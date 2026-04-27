package com.ruoyi.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.enums.LimitType;

/**
 * 限流注解
 *
 * @author ruoyi
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter
{
    /**
     * 限流 key 前缀
     */
    public String key() default CacheConstants.RATE_LIMIT_KEY;

    /**
     * 限流时间，单位秒
     */
    public int time() default 60;

    /**
     * 允许访问次数
     */
    public int count() default 100;

    /**
     * 限流类型
     */
    public LimitType limitType() default LimitType.DEFAULT;

    /**
     * 触发限流后的提示文案
     */
    public String message() default "访问过于频繁，请稍后再试";
}
