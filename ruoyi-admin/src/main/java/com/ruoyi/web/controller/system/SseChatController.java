package com.ruoyi.web.controller.system;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.web.config.CardBalanceRequest;
import com.ruoyi.web.config.StudentScoreRequest;
import com.ruoyi.system.domain.Student;
import com.ruoyi.system.service.CurrentStudentService;
import com.ruoyi.system.service.RagService;
import com.ruoyi.web.service.RedisChatMemory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai/chat")
public class SseChatController
{
    private static final Logger log = LoggerFactory.getLogger(SseChatController.class);

    @Resource(name = "threadPoolTaskExecutor")
    private Executor taskExecutor;

    @Resource(name = "getStudentScore")
    private BiFunction<StudentScoreRequest, ToolContext, Map<String, Object>> getStudentScoreTool;

    @Resource(name = "getCardBalance")
    private BiFunction<CardBalanceRequest, ToolContext, Map<String, Object>> getCardBalanceTool;

    private final RedisCache redisCache;
    private final StringRedisTemplate stringRedisTemplate;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ChatMemory chatMemory;
    private final MessageChatMemoryAdvisor chatMemoryAdvisor;
    private final RagService ragService;
    private final CurrentStudentService currentStudentService;

    private DefaultRedisScript<Long> limitScript;

    public SseChatController(RedisCache redisCache, StringRedisTemplate stringRedisTemplate,
        ChatClient.Builder chatClientBuilder, RedisChatMemory redisChatMemory, RagService ragService,
        ObjectMapper objectMapper, CurrentStudentService currentStudentService)
    {
        this.redisCache = redisCache;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.chatMemory = redisChatMemory;
        this.chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.chatClient = chatClientBuilder.build();
        this.ragService = ragService;
        this.currentStudentService = currentStudentService;
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

    @Anonymous
    @RateLimiter(time = 60, count = 9999999, limitType = LimitType.USER_ID, message = "大模型额度已耗尽，请稍后再试")
    @RequestMapping(value = { "/stream", "/public/stream" }, method = { RequestMethod.GET, RequestMethod.POST },
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter publicStreamChat(@RequestBody(required = false) Map<String, Object> body,
        @RequestParam(value = "prompt", required = false) String prompt,
        @RequestParam(value = "message", required = false) String message,
        @RequestParam(value = "conversationId", required = false) String conversationIdParam,
        HttpServletRequest request)
    {
        return streamChatInternal(body, prompt, message, conversationIdParam, request, false, Collections.emptyMap());
    }

    @Anonymous
    @RateLimiter(time = 60, count = 9999999, limitType = LimitType.USER_ID, message = "大模型额度已耗尽，请稍后再试")
    @RequestMapping(value = "/student/stream", method = { RequestMethod.GET, RequestMethod.POST },
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter studentStreamChat(@RequestBody(required = false) Map<String, Object> body,
        @RequestParam(value = "prompt", required = false) String prompt,
        @RequestParam(value = "message", required = false) String message,
        @RequestParam(value = "conversationId", required = false) String conversationIdParam,
        HttpServletRequest request)
    {
        ensureStudentAccess();
        Student currentStudent = currentStudentService.requireCurrentStudent();
        Map<String, Object> toolContext = currentStudentService.buildToolContext();
        log.debug("Student chat authorized, studentId={}", currentStudent.getStudentId());
        return streamChatInternal(body, prompt, message, conversationIdParam, request, true, toolContext);
    }

    private SseEmitter streamChatInternal(Map<String, Object> body, String prompt, String message,
        String conversationIdParam, HttpServletRequest request, boolean enableStudentTools,
        Map<String, Object> toolContext)
    {
        String userPrompt = resolvePrompt(body, prompt, message);
        String conversationId = resolveConversationId(body, conversationIdParam, request);
        SseEmitter emitter = new SseEmitter(60_000L);
        StreamLifecycle lifecycle = new StreamLifecycle(conversationId, emitter);

        emitter.onTimeout(() -> {
            log.warn("SSE chat timeout, conversationId={}, prompt={}", conversationId, userPrompt);
            lifecycle.close("timeout");
            lifecycle.safeComplete();
        });
        emitter.onCompletion(() -> {
            log.debug("SSE chat completed callback, conversationId={}, prompt={}", conversationId, userPrompt);
            lifecycle.close("completion callback");
        });
        emitter.onError(throwable -> {
            log.error("SSE chat error callback, conversationId={}, prompt={}", conversationId, userPrompt, throwable);
            lifecycle.close("error callback: " + throwable.getClass().getSimpleName());
        });

        String cacheKey = "ai:qa:" + userPrompt.trim();
        String cachedAnswer = redisCache.getCacheObject(cacheKey);
        if (StringUtils.isNotEmpty(cachedAnswer))
        {
            lifecycle.safeSend(toSse(cachedAnswer));
            lifecycle.safeComplete();
            return emitter;
        }

        if (enableStudentTools && handleDirectStudentToolCall(lifecycle, conversationId, userPrompt, toolContext))
        {
            return emitter;
        }

        List<Document> retrievedDocuments = ragService.retrieveRelevantDocuments(userPrompt);
        log.info("RAG retrieved {} document chunks, conversationId={}, prompt={}, studentTools={}",
            retrievedDocuments.size(), conversationId, userPrompt, enableStudentTools);
        String systemPrompt = appendStudentToolInstructions(ragService.buildSystemPrompt(retrievedDocuments),
            enableStudentTools);

        ChatClient.ChatClientRequestSpec chatRequest = chatClient.prompt(userPrompt)
            .system(systemPrompt)
            .advisors(advisorSpec -> advisorSpec
                .param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId)
                .param(MessageChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, 20)
                .advisors(chatMemoryAdvisor));

        if (enableStudentTools)
        {
            log.info("Student tool chat enabled, conversationId={}, toolContext={}", conversationId, toolContext);
            chatRequest = chatRequest
                .toolContext(toolContext)
                .functions("getStudentScore", "getCardBalance");
            ChatClient.ChatClientRequestSpec finalChatRequest = chatRequest;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try
                {
                    ChatResponse chatResponse = finalChatRequest.call().chatResponse();
                    if (lifecycle.isClosed())
                    {
                        log.info("AI tool call response ignored because lifecycle already closed, conversationId={}",
                            conversationId);
                        return;
                    }
                    if (lifecycle.safeSend(toSse(extractContent(chatResponse))))
                    {
                        lifecycle.safeComplete();
                    }
                }
                catch (Throwable throwable)
                {
                    if (lifecycle.isClosed())
                    {
                        log.info("AI tool call finished after lifecycle closed, conversationId={}", conversationId);
                        return;
                    }
                    log.error("AI tool call failed, conversationId={}, prompt={}", conversationId, userPrompt, throwable);
                    lifecycle.safeCompleteWithError(throwable);
                }
            }, taskExecutor);
            lifecycle.setFuture(future);
            return emitter;
        }

        Flux<ChatResponse> flux = chatRequest.stream().chatResponse();

        AtomicReference<String> previousText = new AtomicReference<>("");
        Disposable disposable = flux
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
                lifecycle::safeSend,
                throwable -> {
                    if (lifecycle.isClosed())
                    {
                        log.info("AI stream terminated after lifecycle closed, conversationId={}", conversationId);
                        return;
                    }
                    log.error("AI stream failed, conversationId={}, prompt={}", conversationId, userPrompt, throwable);
                    lifecycle.safeCompleteWithError(throwable);
                },
                lifecycle::safeComplete
            );
        lifecycle.setDisposable(disposable);

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

    private String appendStudentToolInstructions(String systemPrompt, boolean enableStudentTools)
    {
        if (!enableStudentTools)
        {
            return systemPrompt;
        }
        return systemPrompt + System.lineSeparator() + System.lineSeparator() + """
            你还可以调用学生个人业务工具。
            1. 当用户询问成绩、分数、课程成绩、高数、高等数学、英语或 Java 成绩时，必须调用 getStudentScore 工具，不能直接按知识库兜底回答。
            2. 当用户询问一卡通余额、校园卡余额或饭卡余额时，必须调用 getCardBalance 工具。
            3. 工具只能查询当前登录学生本人的数据，不能根据用户自然语言里的学号、studentId、姓名查询其他学生。
            4. 如果用户要求查询其他同学的数据，仍然只按当前登录学生身份执行，并明确说明只能查询本人数据。
            5. 不要因为知识库没有相关片段就放弃调用工具；成绩和一卡通问题优先使用工具。
            """;
    }

    private String resolveForcedToolName(String userPrompt)
    {
        if (StringUtils.isEmpty(userPrompt))
        {
            return null;
        }
        String prompt = userPrompt.toLowerCase();
        if (prompt.contains("成绩") || prompt.contains("分数") || prompt.contains("高数")
            || prompt.contains("高等数学") || prompt.contains("英语") || prompt.contains("java"))
        {
            return "getStudentScore";
        }
        if (prompt.contains("一卡通") || prompt.contains("校园卡") || prompt.contains("饭卡") || prompt.contains("余额"))
        {
            return "getCardBalance";
        }
        return null;
    }

    private boolean handleDirectStudentToolCall(StreamLifecycle lifecycle, String conversationId, String userPrompt,
        Map<String, Object> toolContext)
    {
        String toolName = resolveForcedToolName(userPrompt);
        if (StringUtils.isEmpty(toolName))
        {
            return false;
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try
            {
                Map<String, Object> result;
                if ("getStudentScore".equals(toolName))
                {
                    String subject = resolveScoreSubject(userPrompt);
                    if (StringUtils.isEmpty(subject))
                    {
                        lifecycle.safeCompleteWithError(new ServiceException("未识别到成绩科目"));
                        return;
                    }
                    result = getStudentScoreTool.apply(new StudentScoreRequest(subject), new ToolContext(toolContext));
                }
                else
                {
                    result = getCardBalanceTool.apply(new CardBalanceRequest("student-chat"), new ToolContext(toolContext));
                }
                log.info("Student chat direct tool dispatch, conversationId={}, tool={}, result={}", conversationId,
                    toolName, result);
                if (lifecycle.isClosed())
                {
                    log.info("Student chat direct tool result dropped because lifecycle already closed, conversationId={}, tool={}",
                        conversationId, toolName);
                    return;
                }
                if (lifecycle.safeSend(toSse(renderStudentToolAnswer(toolName, result))))
                {
                    lifecycle.safeComplete();
                }
            }
            catch (Throwable throwable)
            {
                if (lifecycle.isClosed())
                {
                    log.info("Student chat direct tool task finished after lifecycle closed, conversationId={}, tool={}",
                        conversationId, toolName);
                    return;
                }
                log.error("Student chat direct tool dispatch failed, conversationId={}, prompt={}", conversationId,
                    userPrompt, throwable);
                lifecycle.safeCompleteWithError(throwable);
            }
        }, taskExecutor);
        lifecycle.setFuture(future);
        return true;
    }

    private String resolveScoreSubject(String userPrompt)
    {
        if (StringUtils.isEmpty(userPrompt))
        {
            return null;
        }
        if (userPrompt.contains("高等数学"))
        {
            return "高等数学";
        }
        if (userPrompt.contains("高数"))
        {
            return "高数";
        }
        if (userPrompt.contains("大学英语"))
        {
            return "大学英语";
        }
        if (userPrompt.contains("英语"))
        {
            return "英语";
        }
        if (userPrompt.contains("Java程序设计"))
        {
            return "Java程序设计";
        }
        if (userPrompt.toLowerCase().contains("java"))
        {
            return "java";
        }
        return null;
    }

    private String renderStudentToolAnswer(String toolName, Map<String, Object> result)
    {
        if (result == null || result.isEmpty())
        {
            return "个人数据查询失败，请稍后重试";
        }
        String status = String.valueOf(result.getOrDefault("status", ""));
        String message = String.valueOf(result.getOrDefault("message", "个人数据查询失败"));
        Object dataObject = result.get("data");
        if (!"SUCCESS".equals(status) || !(dataObject instanceof Map<?, ?> data))
        {
            return message;
        }
        if ("getStudentScore".equals(toolName))
        {
            Object subject = data.get("subject");
            Object score = data.get("score");
            return String.format("当前登录学生的%s成绩为%s分。", subject, score);
        }
        if ("getCardBalance".equals(toolName))
        {
            Object balance = data.get("balance");
            return String.format("当前登录学生的一卡通余额为%s元。", balance);
        }
        return message;
    }

    private void ensureStudentAccess()
    {
        try
        {
            if (!SecurityUtils.hasRole("student"))
            {
                throw new ServiceException("当前账号没有学生角色权限", HttpStatus.FORBIDDEN);
            }
        }
        catch (ServiceException ex)
        {
            if (ex.getCode() != null && ex.getCode() == HttpStatus.UNAUTHORIZED)
            {
                throw new ServiceException("当前未登录，无法访问学生聊天", HttpStatus.UNAUTHORIZED);
            }
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("当前未登录，无法访问学生聊天", HttpStatus.UNAUTHORIZED);
        }
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

    private final class StreamLifecycle
    {
        private final String conversationId;
        private final SseEmitter emitter;
        private final AtomicReference<Disposable> disposableRef = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<?>> futureRef = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean terminalSignalled = new AtomicBoolean(false);

        private StreamLifecycle(String conversationId, SseEmitter emitter)
        {
            this.conversationId = conversationId;
            this.emitter = emitter;
        }

        private void setDisposable(Disposable disposable)
        {
            disposableRef.set(disposable);
            if (closed.get() && disposable != null && !disposable.isDisposed())
            {
                disposable.dispose();
                log.info("SSE disposable disposed after late registration, conversationId={}", conversationId);
            }
        }

        private void setFuture(CompletableFuture<?> future)
        {
            futureRef.set(future);
            if (closed.get() && future != null && !future.isDone())
            {
                boolean cancelled = future.cancel(true);
                log.info("SSE future cancelled after late registration, conversationId={}, cancelled={}",
                    conversationId, cancelled);
            }
        }

        private boolean isClosed()
        {
            return closed.get();
        }

        private void close(String reason)
        {
            if (!closed.compareAndSet(false, true))
            {
                log.debug("SSE lifecycle already closed, conversationId={}, reason={}", conversationId, reason);
                return;
            }
            CancellationState state = cancelUpstream(reason);
            log.info(
                "SSE lifecycle closed, conversationId={}, reason={}, disposableDisposed={}, futureCancelled={}",
                conversationId, reason, state.disposableDisposed(), state.futureCancelled());
        }

        private CancellationState cancelUpstream(String reason)
        {
            Disposable disposable = disposableRef.get();
            boolean disposableDisposed = false;
            if (disposable != null && !disposable.isDisposed())
            {
                disposable.dispose();
                disposableDisposed = true;
            }

            CompletableFuture<?> future = futureRef.get();
            boolean futureCancelled = false;
            if (future != null && !future.isDone())
            {
                futureCancelled = future.cancel(true);
            }

            log.debug("SSE upstream cancel evaluated, conversationId={}, reason={}, disposablePresent={}, futurePresent={}",
                conversationId, reason, disposable != null, future != null);
            return new CancellationState(disposableDisposed, futureCancelled);
        }

        private boolean safeSend(String data)
        {
            if (closed.get())
            {
                log.debug("Skip SSE send because lifecycle already closed, conversationId={}", conversationId);
                return false;
            }
            try
            {
                emitter.send(SseEmitter.event().data(data));
                return true;
            }
            catch (IOException | IllegalStateException ex)
            {
                log.warn("SSE send failed, conversationId={}, reason=client disconnected", conversationId, ex);
                close("client disconnected");
                safeComplete();
                return false;
            }
        }

        private void safeComplete()
        {
            if (terminalSignalled.compareAndSet(false, true))
            {
                emitter.complete();
            }
        }

        private void safeCompleteWithError(Throwable throwable)
        {
            if (terminalSignalled.compareAndSet(false, true))
            {
                emitter.completeWithError(throwable);
            }
        }
    }

    private record CancellationState(boolean disposableDisposed, boolean futureCancelled)
    {
    }
}
