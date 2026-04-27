package com.ruoyi.common.enums;

/**
 * 限流类型
 *
 * @author ruoyi
 */
public enum LimitType
{
    /**
     * 默认策略，全局限流
     */
    DEFAULT,

    /**
     * 基于请求 IP 限流
     */
    IP,

    /**
     * 基于当前登录用户 ID 限流，取不到用户时回退到 IP
     */
    USER_ID
}
