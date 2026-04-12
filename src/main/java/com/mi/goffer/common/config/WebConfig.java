package com.mi.goffer.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/11 19:58
 * @Description: Web 配置类
 */
@Configuration
public class WebConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
