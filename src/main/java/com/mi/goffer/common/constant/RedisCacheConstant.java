package com.mi.goffer.common.constant;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/18 16:00
 * @Description: Redis 缓存常量类
 */
public class RedisCacheConstant {

    /**
     * 邮件验证码缓存前缀
     */
    public static final String EMAIL_CODE_KEY_PREFIX = "email:code:";

    /**
     * 邮件发送锁缓存前缀
     */
    public static final String EMAIL_SEND_LOCK_KEY_PREFIX = "email:send:lock:";

    /**
     * 邮件验证码有效期（分钟）
     */
    public static final long EMAIL_CODE_EXPIRE_MINUTES = 5;

    /**
     * 用户 Token 缓存 key
     */
    public static final String USER_TOKEN_KEY = "user:token:";
}
