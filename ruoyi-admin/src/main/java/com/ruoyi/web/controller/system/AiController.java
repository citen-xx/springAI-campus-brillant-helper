package com.ruoyi.web.controller.system;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.StringUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@Anonymous
@RequestMapping("/system/ai")
public class AiController
{
    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final RedisCache redisCache;
    private final StringRedisTemplate stringRedisTemplate;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    private DefaultRedisScript<Long> limitScript;

    @Autowired
    public AiController(RedisCache redisCache, StringRedisTemplate stringRedisTemplate, ChatModel chatModel,
        VectorStore vectorStore, ObjectMapper objectMapper)
    {
        this.redisCache = redisCache;
        this.stringRedisTemplate = stringRedisTemplate;
        this.chatClient = ChatClient.create(chatModel);
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
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
     * AI 问答接口
     *
     * 1. 保留 Redis 缓存查询
     * 2. 保留 Lua 滑动窗口限流
     * 3. 使用 Spring AI ChatClient + QuestionAnswerAdvisor 做 RAG
     * 4. 返回前端可直接消费的 SSE 格式
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody Map<String, String> request)
    {
        String query = request.get("query");
        String user = request.getOrDefault("user", "campus-student");

        if (StringUtils.isEmpty(query))
        {
            return Flux.just(toSse("query 不能为空"));
        }

        // =========================
        // 1. Redis 缓存优先返回
        // =========================
        String cacheKey = "ai:qa:" + query.trim();
        String cachedAnswer = redisCache.getCacheObject(cacheKey);
        if (StringUtils.isNotEmpty(cachedAnswer))
        {
            log.info("Cache hit, query={}", query);
            return Flux.just(toSse(cachedAnswer));
        }

        // =========================
        // 2. Lua 滑动窗口限流
        // =========================
        String rateLimitKey = "ai:rate_limit:" + user;
        long windowSizeMs = 60000L;
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
            log.warn("Rate limited, user={}, query={}", user, query);
            return Flux.just(toSse("请求过于频繁，请稍后再试"));
        }

        // =========================
        // 3. Spring AI + RAG 检索
        // =========================
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(SearchRequest.builder()
                .query(query)
                .topK(4)
                .similarityThreshold(0.7d)
                .build())
            .build();

        return chatClient.prompt()
            .user(query)
            .advisors(advisor)
            .stream()
            .content()
            .map(this::toSse)
            .onErrorResume(ex -> {
                log.error("AI stream error, query={}", query, ex);
                return Flux.just(toSse("系统繁忙，请稍后再试"));
            });
    }

    private String toSse(String answer)
    {
        try
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "message");
            payload.put("answer", answer);
            return "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException("SSE 消息序列化失败", e);
        }
    }
}
