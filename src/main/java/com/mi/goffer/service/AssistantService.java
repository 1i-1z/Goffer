package com.mi.goffer.service;

import reactor.core.publisher.Flux;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 15:46
 * @Description: 大模型服务层接口
 */
public interface AssistantService {

    /**
     * 普通对话
     * @param userId 用户id
     * @param sessionId 会话id
     * @param message 用户输入
     * @return Flux<String> 流式响应
     */
    Flux<String> chat(String userId, Long sessionId, String message);

    /**
     * 面试对话
     * @param userId 用户id
     * @param sessionId 会话id
     * @param message 用户输入
     * @return Flux<String> 流式响应
     */
    Flux<String> interview(String userId, String sessionId, String message);
}
