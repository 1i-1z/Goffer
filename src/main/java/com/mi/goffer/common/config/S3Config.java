package com.mi.goffer.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/12 21:17
 * @Description: S3 配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "aws.s3")
public class S3Config {

    /**
     * endpoint
     */
    private String endpoint;

    /**
     * 区域
     */
    private String region;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 访问id
     */
    private String accessKeyId;

    /**
     * 访问密钥
     */
    private String secretAccessKey;

    /**
     * 访问方式
     */
    private boolean pathStyleAccess;
}
