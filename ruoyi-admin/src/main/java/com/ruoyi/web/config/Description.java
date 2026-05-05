package com.ruoyi.web.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 说明注解
 *
 * 说明：
 * Spring AI 1.0.0-M6 官方原生更推荐使用 Tool / ToolParam 描述工具；
 * 这里额外提供一个同名语义注解，方便项目内标记 Function Bean 的用途。
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Description
{
    String value();
}
