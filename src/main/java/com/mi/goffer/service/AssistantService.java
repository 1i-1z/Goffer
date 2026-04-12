package com.mi.goffer.service;

import com.mi.goffer.dto.req.ChatReqDTO;
import com.mi.goffer.dto.req.InterviewReqDTO;
import com.mi.goffer.dto.resp.*;
import org.springframework.web.multipart.MultipartFile;
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
     *
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
     * 语音面试
     *
     * @param userId    用户id
     * @param sessionId 会话ID
     * @param mode      面试模式（1：后端面试、2：前端面试）
     * @return Flux<byte[]> 音频流
     */
    Flux<byte[]> voiceInterview(String userId, Long sessionId, Integer mode, MultipartFile audioFile);

    /**
     * 语音面试（返回完整音频数据）
     *
     * @param userId    用户id
     * @param sessionId 会话ID
     * @param mode      面试模式（1：后端面试、2：前端面试）
     * @param audioFile 音频文件
     * @return byte[] 完整音频数据
     */
    byte[] voiceInterviewByte(String userId, Long sessionId, Integer mode, MultipartFile audioFile);

    /**
     * 语音面试（返回流式数据）
     *
     * @param userId    用户id
     * @param sessionId 会话ID
     * @param mode      面试模式（1：后端面试、2：前端面试）
     * @param audioFile 音频文件
     * @return Flux<InterviewRespDTO> 流式响应
     */
    Flux<InterviewRespDTO> interviewTextStream(String userId, Long sessionId, Integer mode, MultipartFile audioFile);

    /**
     * 获取所有会话标题
     *
     * @param userId 用户id
     * @return List<TitleRespDTO> 会话标题列表
     */
    List<TitleRespDTO> getAllChatTitle(String userId);

    /**
     * 查询会话历史
     *
     * @param userId  用户id
     * @param keyword 关键词
     * @return List<QueryChatHistoryRespDTO> 会话历史列表
     */
    List<QueryChatHistoryRespDTO> queryChatHistory(String userId, String keyword);

    /**
     * 获取面试历史信息
     *
     * @param userId 用户id
     * @return List<InterviewHistoryInfoRespDTO> 面试历史信息列表
     */
    List<InterviewHistoryInfoRespDTO> getInterviewHistoryInfo(String userId);

    /**
     * 根据会话id获取消息列表
     *
     * @param sessionId 会话id
     * @return List<ChatMessageRespDTO> 会话消息列表
     */
    List<ChatMessageRespDTO> getMessageBySessionId(String sessionId);

    /**
     * 根据会话id获取面试消息列表
     *
     * @param sessionId 会话id
     * @return List<InterviewMessageRespDTO> 面试消息列表
     */
    List<InterviewMessageRespDTO> getInterviewMessageBySessionId(String sessionId);
}
