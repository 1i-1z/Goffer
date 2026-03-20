package com.mi.goffer.common.interceptor;

import com.mi.goffer.common.context.UserContext;
import com.mi.goffer.common.convention.exception.ClientException;
import com.mi.goffer.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/20 20:35
 * @Description: 认证拦截器
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从请求头提取 Token
        String authHeader = request.getHeader("Authorization");

        // 如果没有 Authorizatoin 头，放行
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return true;
        }

        String token = jwtUtil.extractTokenFromHeader(authHeader);
        // 解析用户信息并存入上下文
        String userId = jwtUtil.parseUserId(token);

        // 检查用户 Token 是否存在于 Redis（已退出登录则不在）
        if (!jwtUtil.isTokenExists(userId)) {
            throw new ClientException("Token已失效，请重新登录");
        }

        UserContext.setCurrentUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理上下文
        UserContext.clear();
    }
}

