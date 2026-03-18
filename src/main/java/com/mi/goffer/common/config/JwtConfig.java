package com.mi.goffer.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/12 16:54
 * @Description: JWT 配置类
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
@Configuration
public class JwtConfig {

    /**
     * JWT签名密钥
     */
    private String secret;

    /**
     * Token过期时间（毫秒）
     */
    private Duration expiration;

    /**
     * 请求头名称
     */
    private String header;

    /**
     * Token前缀
     */
    private String prefix;

    /**
     * Token前缀长度
     */
    private Integer prefixLength;
}
