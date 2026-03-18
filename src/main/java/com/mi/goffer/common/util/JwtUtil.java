package com.mi.goffer.common.util;

import com.mi.goffer.common.config.JwtConfig;
import com.mi.goffer.common.constant.RedisCacheConstant;
import com.mi.goffer.common.convention.exception.ClientException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/18 15:55
 * @Description: JWT 工具类
 */
@Slf4j
@Component
public class JwtUtil {

    private final JwtConfig jwtConfig;
    private final SecretKey secretKey;
    private final StringRedisTemplate stringRedisTemplate;

    public JwtUtil(JwtConfig jwtConfig, StringRedisTemplate stringRedisTemplate) {
        this.jwtConfig = jwtConfig;
        this.stringRedisTemplate = stringRedisTemplate;
        this.secretKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token
     */
    public String generateUserToken(String userId) {
        return generateToken(userId);
    }

    private String generateToken(String subject) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtConfig.getExpiration().toMillis());
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    private JwtParser createParser() {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        try {
            return createParser()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            throw new ClientException("Token已过期，请重新登录");
        } catch (SignatureException e) {
            log.warn("Token签名验证失败: {}", e.getMessage());
            throw new ClientException("Token无效");
        } catch (MalformedJwtException e) {
            log.warn("Token格式错误: {}", e.getMessage());
            throw new ClientException("Token格式错误");
        } catch (IllegalArgumentException e) {
            log.warn("Token为空: {}", e.getMessage());
            throw new ClientException("Token不能为空");
        } catch (Exception e) {
            log.error("Token解析失败: {}", e.getMessage());
            throw new ClientException("Token解析失败");
        }
    }

    /**
     * 解析用户 ID
     */
    public String parseUserId(String token) {
        Claims claims = parseToken(token);
        try {
            return claims.getSubject();
        } catch (NumberFormatException e) {
            throw new ClientException("Token用户ID格式错误");
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从请求头提取 Token
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(jwtConfig.getPrefix())) {
            throw new ClientException("Authorization请求头格式错误，应为: " + jwtConfig.getPrefix() + " xxx");
        }
        return authHeader.substring(jwtConfig.getPrefixLength());
    }

    /**
     * 退出登录 - 删除 Redis 中的用户 Token
     *
     * @param userId 用户ID
     */
    public void logout(String userId) {
        String key = RedisCacheConstant.USER_TOKEN_KEY + userId;
        stringRedisTemplate.delete(key);
        log.info("用户 {} 已退出登录，Token 已从 Redis 中删除", userId);
    }
}
