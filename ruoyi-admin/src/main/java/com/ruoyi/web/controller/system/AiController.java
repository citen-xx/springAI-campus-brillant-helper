package com.ruoyi.web.controller.system;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.redis.RedisCache; // 若依自带的 Redis 工具！
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct; // ✅ Spring Boot 3.x 正确版本
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@RestController
@Anonymous
@RequestMapping("/system/ai")
public class AiController {

    // 1. 注入若依自带的 Redis 工具
    @Autowired
    private RedisCache redisCache;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> limitScript;

    @PostConstruct
    public void init() {
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

    @Value("${dify.api-url}")
    private String apiUrl;

    @Value("${dify.api-key}")
    private String apiKey;

    private final WebClient webClient;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiController.class);

    public AiController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        String user = request.getOrDefault("user", "campus-student");

        // ==========================================
        // 🛡️ 动态滑动窗口限流器 (防止刷单耗尽 Token)
        // ==========================================
        String rateLimitKey = "ai:rate_limit:" + user;
        long windowSizeMs = 60000; // 窗口大小 1 分钟
        long maxRequests = 10;     // 每分钟最多请求 10 次
        long currentTimeMs = System.currentTimeMillis();

        Long isAllowed = stringRedisTemplate.execute(
                limitScript,
                Collections.singletonList(rateLimitKey),
                String.valueOf(windowSizeMs),
                String.valueOf(maxRequests),
                String.valueOf(currentTimeMs)
        );

        if (isAllowed == null || isAllowed == 0L) {
            log.warn("🚨 用户 {} 请求频繁被限流", user);
            return Flux.just("data: {\"event\": \"message\", \"answer\": \"请求过于频繁，请稍后再试。您的提问配额已用完。\"}\n\n");
        }

        // ==========================================
        // 🚀 核心优化：防大模型 Token 消耗的精准拦截层
        // ==========================================
        String cacheKey = "ai:qa:" + query.trim();

        // 第一步：先查 Redis 缓存
        String cachedAnswer = redisCache.getCacheObject(cacheKey);
        if (StringUtils.isNotEmpty(cachedAnswer)) {
            log.info("🎯 命中 Redis 缓存，直接返回！问题: {}", query);
            // 组装成流式格式返回给前端，前端毫无察觉
            String json = "{\"event\": \"message\", \"answer\": \"" + cachedAnswer + "\"}";
            return Flux.just("data: " + json + "\n\n");
        }

        /* // 第二步：缓存没有，查 MySQL 数据库 (标准问题库)
        // 注意：把这段代码的注释解开，换成你真实的方法名
        AiCommonQa qa = aiCommonQaService.selectByQuestion(query);
        if (qa != null) {
            log.info("📚 命中 MySQL 标准库，写入缓存并返回！");
            // 存入 Redis，设置 24 小时过期
            redisCache.setCacheObject(cacheKey, qa.getAnswer(), 24, TimeUnit.HOURS);
            String json = "{\"event\": \"message\", \"answer\": \"" + qa.getAnswer() + "\"}";
            return Flux.just("data: " + json + "\n\n");
        }
        */

        // 第三步：数据库也没有，只能去求助 Dify 大模型了！
        log.info("☁️ 缓存未命中，开始向 Dify 大模型请求: {}", query);

        Map<String, Object> difyPayload = new HashMap<>();
        difyPayload.put("inputs", new HashMap<>());
        difyPayload.put("query", query);
        difyPayload.put("response_mode", "streaming");
        difyPayload.put("user", user);

        String safeApiKey = apiKey.trim().startsWith("Bearer ") ? apiKey.trim() : "Bearer " + apiKey.trim();

        return webClient.post()
                .uri(apiUrl)
                .header(HttpHeaders.AUTHORIZATION, safeApiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(difyPayload)
                .retrieve()
                .bodyToFlux(String.class)
                .onErrorResume(e -> {
                    log.error("💥 大模型连接异常: ", e);
                    return Flux.just("data: {\"event\": \"message\", \"answer\": \"系统网络繁忙，请稍后再试\"}\n\n");
                });
    }
}