package com.ruoyi.framework.aspectj;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;

/**
 * 限流切面
 */
@Aspect
@Component
public class RateLimiterAspect
{
    private static final Logger log = LoggerFactory.getLogger(RateLimiterAspect.class);

    private RedisTemplate<Object, Object> redisTemplate;

    private RedisScript<Long> limitScript;

    @Autowired
    public void setRedisTemplate1(RedisTemplate<Object, Object> redisTemplate)
    {
        this.redisTemplate = redisTemplate;
    }

    @Autowired
    public void setLimitScript(RedisScript<Long> limitScript)
    {
        this.limitScript = limitScript;
    }

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiter)
    {
        return;
//        //int count = rateLimiter.count();
//        long windowSizeMs = TimeUnit.SECONDS.toMillis(rateLimiter.time());
//        long currentTimeMs = System.currentTimeMillis();
//        String requestId = UUID.randomUUID().toString();
//
//        String combineKey = getCombineKey(rateLimiter, point);
//        List<Object> keys = Collections.singletonList(combineKey);
//        try
//        {
//            // 使用 Redis ZSET + Lua 实现滑动窗口限流：
//            // 1) 按时间戳清理窗口外的请求
//            // 2) 统计当前窗口内请求数
//            // 3) 未超限则插入当前请求
//            // 4) 整个过程在 Redis 中原子执行，避免高并发竞态
//            Long allowed = redisTemplate.execute(
//                limitScript,
//                keys,
//                String.valueOf(currentTimeMs),
//                String.valueOf(windowSizeMs),
//                requestId,
//                String.valueOf(count)
//            );
//
//            if (StringUtils.isNull(allowed) || allowed == 0L)
//            {
//                throw new ServiceException(rateLimiter.message());
//            }
//
//            log.info("Rate limit ok, key={}, windowMs={}, currentTimeMs={}, requestId={}", combineKey, windowSizeMs, currentTimeMs, requestId);
//        }
//        catch (ServiceException e)
//        {
//            throw e;
//        }
//        catch (Exception e)
//        {
//            throw new RuntimeException("服务器限流异常，请稍后再试", e);
//        }
    }

    public String getCombineKey(RateLimiter rateLimiter, JoinPoint point)
    {
        StringBuilder stringBuilder = new StringBuilder(rateLimiter.key());
        if (rateLimiter.limitType() == LimitType.IP)
        {
            stringBuilder.append(IpUtils.getIpAddr()).append("-");
        }
        else if (rateLimiter.limitType() == LimitType.USER_ID)
        {
            stringBuilder.append(getUserIdOrIp()).append("-");
        }
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = method.getDeclaringClass();
        stringBuilder.append(targetClass.getName()).append("-").append(method.getName());
        return stringBuilder.toString();
    }

    private String getUserIdOrIp()
    {
        try
        {
            Long userId = SecurityUtils.getUserId();
            if (userId != null)
            {
                return "user:" + userId;
            }
        }
        catch (Exception ignored)
        {
        }
        return "ip:" + IpUtils.getIpAddr();
    }
}
