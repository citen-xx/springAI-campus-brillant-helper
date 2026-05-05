package com.ruoyi.web.controller.system;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.utils.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import com.ruoyi.web.service.RedisChatMemory;
import com.ruoyi.system.service.RagService;

@RestController
@RequestMapping("/api/ai/chat")
public class SseChatController
{
    private static final Logger log = LoggerFactory.getLogger(SseChatController.class);

    @Resource(name = "threadPoolTaskExecutor")
    private Executor taskExecutor;

    private final RedisCache redisCache;
    private final StringRedisTemplate stringRedisTemplate;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ChatMemory chatMemory;
    private final MessageChatMemoryAdvisor chatMemoryAdvisor;
    private final RagService ragService;

    private DefaultRedisScript<Long> limitScript;

    public SseChatController(RedisCache redisCache, StringRedisTemplate stringRedisTemplate,
        ChatClient.Builder chatClientBuilder, RedisChatMemory redisChatMemory, RagService ragService, ObjectMapper objectMapper)
    {
        this.redisCache = redisCache;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.chatMemory = redisChatMemory;
        this.chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.chatClient = chatClientBuilder.build();
        this.ragService = ragService;
    }

    @PostConstruct
    public void init()
    {
        limitScript = new DefaultRedisScript<>();
        limitScript.setResultType(Long.class);
        limitScript.setScriptText(
            "local key = KEYS[1]\n" +
            "local window_size = tonumber(ARGV[1])\n" +
            "local max_requests = tonumber(ARGV[2])\n" +
            "local current_time = tonumber(ARGV[3])\n" +
            "local window_start = current_time - window_size\n" +
            "redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)\n" +
            "local current_requests = redis.call('ZCARD', key)\n" +
            "if current_requests < max_requests then\n" +
            "    redis.call('ZADD', key, current_time, current_time)\n" +
            "    redis.call('PEXPIRE', key, window_size)\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end"
        );
    }

    /**
     * 流式 AI 对话接口
     *
     * 保留：
     * - RedisCache 问答缓存
     * - Redis + Lua 滑动窗口限流（1 分钟 10 次）
     *
     * 升级：
     * - Spring AI ChatClient
     * - MessageChatMemoryAdvisor 会话记忆
     * - QuestionAnswerAdvisor RAG 检索
     * - SSE 推送给前端
     */
    @RateLimiter(time = 60, count = 3, limitType = LimitType.USER_ID, message = "大模型额度已耗尽，请稍后再试")
    @RequestMapping(value = "/stream", method = { RequestMethod.GET, RequestMethod.POST }, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody(required = false) Map<String, Object> body,
        @RequestParam(value = "prompt", required = false) String prompt,
        @RequestParam(value = "message", required = false) String message,
        @RequestParam(value = "conversationId", required = false) String conversationIdParam,
        HttpServletRequest request)
    {
        String userPrompt = resolvePrompt(body, prompt, message);
        String conversationId = resolveConversationId(body, conversationIdParam, request);
        SseEmitter emitter = new SseEmitter(60_000L);

        emitter.onTimeout(() -> {
            log.warn("SSE chat timeout, conversationId={}, prompt={}", conversationId, userPrompt);
            emitter.complete();
        });
        emitter.onCompletion(() -> log.debug("SSE chat completed, conversationId={}, prompt={}", conversationId, userPrompt));
        emitter.onError(throwable -> log.error("SSE chat error, conversationId={}, prompt={}", conversationId, userPrompt, throwable));

        // 1. Redis 问答缓存优先返回
        String cacheKey = "ai:qa:" + userPrompt.trim();
        String cachedAnswer = redisCache.getCacheObject(cacheKey);
        if (StringUtils.isNotEmpty(cachedAnswer))
        {
            sendAsync(emitter, toSse(cachedAnswer));
            emitter.complete();
            return emitter;
        }

        // 2. Redis + Lua 滑动窗口限流（1 分钟 10 次）
        String rateLimitKey = "ai:rate_limit:" + conversationId;
        long windowSizeMs = TimeUnit.SECONDS.toMillis(60);
        long maxRequests = 10L;
        long currentTimeMs = System.currentTimeMillis();
        Long isAllowed = stringRedisTemplate.execute(
            limitScript,
            Collections.singletonList(rateLimitKey),
            String.valueOf(windowSizeMs),
            String.valueOf(maxRequests),
            String.valueOf(currentTimeMs)
        );
        if (isAllowed == null || isAllowed == 0L)
        {
            sendAsync(emitter, toSse("请求过于频繁，请稍后再试"));
            emitter.complete();
            return emitter;
        }

        // 3. 先执行向量检索，再把检索结果组装成系统提示词做 RAG
        List<Document> retrievedDocuments = ragService.retrieveRelevantDocuments(userPrompt);
        log.info("RAG retrieved {} document chunks, conversationId={}, prompt={}", retrievedDocuments.size(), conversationId, userPrompt);
        String systemPrompt = ragService.buildSystemPrompt(retrievedDocuments);

        Flux<ChatResponse> flux = chatClient.prompt(userPrompt)
            .functions("getStudentScore", "getCardBalance")
            .system(systemPrompt)
            .advisors(advisorSpec -> advisorSpec
                .param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId)
                .param(MessageChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, 20)
                .advisors(chatMemoryAdvisor))
            .stream()
            .chatResponse();

        AtomicReference<String> previousText = new AtomicReference<>("");
        CompletableFuture.runAsync(() -> flux
            .map(chatResponse -> {
                String currentText = extractContent(chatResponse);
                String previous = previousText.getAndSet(currentText);
                String delta = currentText;
                if (StringUtils.isNotEmpty(previous) && currentText.startsWith(previous))
                {
                    delta = currentText.substring(previous.length());
                }
                return toSse(delta);
            })
            .subscribe(
                sseChunk -> sendAsync(emitter, sseChunk),
                throwable -> {
                    log.error("AI stream failed, conversationId={}, prompt={}", conversationId, userPrompt, throwable);
                    emitter.completeWithError(throwable);
                },
                emitter::complete
            ), taskExecutor);

        return emitter;
    }

    private String resolvePrompt(Map<String, Object> body, String prompt, String message)
    {
        if (StringUtils.isNotEmpty(prompt))
        {
            return prompt;
        }
        if (StringUtils.isNotEmpty(message))
        {
            return message;
        }
        if (body != null)
        {
            Object bodyPrompt = body.get("prompt");
            if (bodyPrompt != null && StringUtils.isNotEmpty(bodyPrompt.toString()))
            {
                return bodyPrompt.toString();
            }

            Object bodyMessage = body.get("message");
            if (bodyMessage != null && StringUtils.isNotEmpty(bodyMessage.toString()))
            {
                return bodyMessage.toString();
            }

            Object bodyQuery = body.get("query");
            if (bodyQuery != null && StringUtils.isNotEmpty(bodyQuery.toString()))
            {
                return bodyQuery.toString();
            }
        }
        return "";
    }

    private String resolveConversationId(Map<String, Object> body, String conversationIdParam, HttpServletRequest request)
    {
        if (StringUtils.isNotEmpty(conversationIdParam))
        {
            return conversationIdParam;
        }
        if (body != null)
        {
            Object conversationId = body.get("conversationId");
            if (conversationId != null && StringUtils.isNotEmpty(conversationId.toString()))
            {
                return conversationId.toString();
            }
        }

        String sessionId = request.getSession(true).getId();
        return StringUtils.isNotEmpty(sessionId) ? sessionId : UUID.randomUUID().toString();
    }

    private String extractContent(ChatResponse chatResponse)
    {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null)
        {
            return "";
        }
        String text = chatResponse.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private String toSse(String answer)
    {
        try
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "message");
            payload.put("answer", answer);
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException("SSE 消息序列化失败", e);
        }
    }

    private void sendAsync(SseEmitter emitter, String data)
    {
        try
        {
            emitter.send(SseEmitter.event().data(data));
        }
        catch (IOException e)
        {
            log.warn("SSE send failed, emitter closed or network interrupted", e);
            emitter.completeWithError(e);
        }
    }
}
