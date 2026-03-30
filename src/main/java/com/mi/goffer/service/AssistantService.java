package com.mi.goffer.service;

import com.mi.goffer.dto.req.ChatReqDTO;
import com.mi.goffer.dto.resp.ChatRespDTO;
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
     * @param reqDTO 请求参数
     * @return Flux<ChatRespDTO> 流式响应
     */
    Flux<ChatRespDTO> chat(String userId, ChatReqDTO reqDTO);

    /**
     * 面试对话
     *
     * @param userId 用户id
     * @param reqDTO 请求参数
     * @return Flux<String> 流式响应
     */
    Flux<String> interview(String userId, ChatReqDTO reqDTO);
}
