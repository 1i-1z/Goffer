package com.mi.goffer.common.constant;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/18 16:00
 * @Description: Redis 缓存常量类
 */
public class RedisCacheConstant {

    /**
     * 用户 Token 缓存 key
     */
    public static final String USER_TOKEN_KEY = "user:token:";

    /**
     * Token 过期时间（秒）- 15天
     */
    public static final long TOKEN_EXPIRE_SECONDS = 1296000L;
}
