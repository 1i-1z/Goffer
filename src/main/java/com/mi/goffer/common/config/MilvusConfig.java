package com.mi.goffer.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/1 09:51
 * @Description: Milvus 配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "milvus")
public class MilvusConfig {

    /**
     * Milvus 地址
     */
    private String uri;

    /**
     * 令牌认证
     */
    private String token;
}
