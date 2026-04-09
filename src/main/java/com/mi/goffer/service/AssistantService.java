package com.mi.goffer.service;

import com.mi.goffer.dto.req.ChatReqDTO;
import com.mi.goffer.dto.req.InterviewReqDTO;
import com.mi.goffer.dto.resp.*;
import reactor.core.publisher.Flux;

import java.util.List;

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
    Flux<InterviewRespDTO> interview(String userId, InterviewReqDTO reqDTO);

    /**
     * 获取所有会话标题
     *
     * @param userId 用户id
     * @return List<TitleRespDTO> 会话标题列表
     */
    List<TitleRespDTO> getAllChatTitle(String userId);

    /**
     * 获取所有面试标题
     *
     * @param userId 用户id
     * @return List<InterviewTitleRespDTO> 面试历史列表
     */
    List<InterviewTitleRespDTO> getAllInterviewTitle(String userId);

    /**
     * 查询会话历史
     *
     * @param userId 用户id
     * @param keyword 关键词
     * @return List<QueryChatHistoryRespDTO> 会话历史列表
     */
    List<QueryChatHistoryRespDTO> queryChatHistory(String userId, String keyword);
}
