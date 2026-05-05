package com.ruoyi.framework.config;

import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 */
@SuppressWarnings("deprecation")
@Configuration
@EnableCaching
public class RedisConfig extends CachingConfigurerSupport
{
    @Bean
    @SuppressWarnings(value = { "unchecked", "rawtypes" })
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory)
    {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        FastJson2JsonRedisSerializer serializer = new FastJson2JsonRedisSerializer(Object.class);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Redis ZSET 滑动窗口限流脚本
     *
     * 参数说明：
     * ARGV[1] = 当前时间戳（毫秒）
     * ARGV[2] = 窗口期（毫秒）
     * ARGV[3] = 唯一请求 ID
     * ARGV[4] = 允许的最大请求数
     *
     * 执行步骤：
     * 1. ZREMRANGEBYSCORE 清理窗口外的旧请求
     * 2. ZCARD 统计当前窗口内的请求数
     * 3. 未超限则 ZADD 新请求
     * 4. PEXPIRE 设置过期时间，避免 key 长期残留
     */
    @Bean
    public DefaultRedisScript<Long> limitScript()
    {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(limitScriptText());
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    private String limitScriptText()
    {
        return ""
            + "local key = KEYS[1]\n"
            + "local currentTime = tonumber(ARGV[1])\n"
            + "local windowSize = tonumber(ARGV[2])\n"
            + "local requestId = ARGV[3]\n"
            + "local maxCount = tonumber(ARGV[4])\n"
            + "local minScore = currentTime - windowSize\n"
            + "redis.call('ZREMRANGEBYSCORE', key, '-inf', minScore)\n"
            + "local currentCount = redis.call('ZCARD', key)\n"
            + "if currentCount >= maxCount then\n"
            + "    return 0\n"
            + "end\n"
            + "redis.call('ZADD', key, currentTime, requestId)\n"
            + "redis.call('PEXPIRE', key, windowSize)\n"
            + "return 1\n";
    }
}
