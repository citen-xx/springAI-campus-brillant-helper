package com.ruoyi.web.config;

/**
 * 一卡通余额查询工具入参
 *
 * Spring AI 在工具调用时会要求存在非空输入，
 * 因此这里保留一个可忽略的占位字段，后端不会把它作为身份依据。
 */
public record CardBalanceRequest(String request)
{
}
