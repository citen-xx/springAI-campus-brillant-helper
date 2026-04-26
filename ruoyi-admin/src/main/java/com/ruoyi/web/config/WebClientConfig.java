package com.ruoyi.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 配置类
 */
@Configuration
public class WebClientConfig {

    /**
     * 手动注入 WebClient.Builder Bean
     * 解决 AiController 构造函数中找不到该 Bean 的问题
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}