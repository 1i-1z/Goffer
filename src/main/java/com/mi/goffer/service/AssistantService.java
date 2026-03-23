package com.mi.goffer.service;

import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 15:46
 * @Description: 大模型服务层接口
 */
@AiService
public interface AssistantService {

    /**
     * 普通对话
     * @param usersId 用户id
     * @param sessionsId 会话id
     * @param message 用户输入
     * @return Flux<String> 流式响应
     */
    Flux<String> chat(String usersId, String sessionsId, String message);

    /**
     * 面试对话
     * @param usersId 用户id
     * @param sessionsId 会话id
     * @param message 用户输入
     * @return Flux<String> 流式响应
     */
    Flux<String> interview(String usersId, String sessionsId, String message);
}
