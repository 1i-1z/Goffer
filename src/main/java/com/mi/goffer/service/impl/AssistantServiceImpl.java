package com.mi.goffer.service.impl;

import com.mi.goffer.service.AssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 17:21
 * @Description: 大模型服务层实现类
 */
@Service
@RequiredArgsConstructor
public class AssistantServiceImpl implements AssistantService {

    /**
     * 普通对话
     * @param usersId 用户id
     * @param sessionsId 会话id
     * @param message 用户输入
     * @return Flux<String> 流式响应
     */
    @Override
    public Flux<String> chat(String usersId, String sessionsId, String message) {
        return null;
    }

    /**
     * 面试对话
     * @param usersId 用户id
     * @param sessionsId 会话id
     * @param message 用户输入
     * @return Flux<String> 流式响应
     */
    @Override
    public Flux<String> interview(String usersId, String sessionsId, String message) {
        return null;
    }
}
