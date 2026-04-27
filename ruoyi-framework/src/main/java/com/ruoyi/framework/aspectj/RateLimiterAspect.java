package com.ruoyi.framework.aspectj;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
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
 * 限流处理
 *
 * @author ruoyi
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
    public void doBefore(JoinPoint point, RateLimiter rateLimiter) throws Throwable
    {
        int time = rateLimiter.time();
        int count = rateLimiter.count();

        String combineKey = getCombineKey(rateLimiter, point);
        List<Object> keys = Collections.singletonList(combineKey);
        try
        {
            // 这里通过 Redis + Lua 脚本一次性完成“读取当前计数、计数自增、首次访问设置过期时间”三步操作，
            // 避免高并发场景下多次 Redis 往返带来的竞态问题，保证整个限流判断具备原子性。
            // 当前项目这段脚本实现的是“固定窗口计数限流”：
            // 在一个 time 秒的窗口内，某个 key 最多允许访问 count 次。
            // 它不是严格意义上的“滑动窗口”或“令牌桶”，但滑动窗口/令牌桶在生产中也经常借助
            // Redis + Lua 来保证原子更新，所以复习时可以把这类实现都归到“Redis 原子限流”这一类理解。
            Long number = redisTemplate.execute(limitScript, keys, count, time);
            if (StringUtils.isNull(number) || number.intValue() > count)
            {
                throw new ServiceException(rateLimiter.message());
            }
            log.info("限制请求'{}',当前请求'{}',缓存key'{}'", count, number.intValue(), combineKey);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException("服务器限流异常，请稍后再试");
        }
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
            // Ignore and fallback to IP below.
        }
        return "ip:" + IpUtils.getIpAddr();
    }
}
