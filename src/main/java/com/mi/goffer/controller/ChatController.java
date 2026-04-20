package com.mi.goffer.controller;

import com.mi.goffer.common.context.UserContext;
import com.mi.goffer.common.convention.result.Result;
import com.mi.goffer.common.convention.result.Results;
import com.mi.goffer.dto.req.ChatReqDTO;
import com.mi.goffer.dto.req.InterviewReqDTO;
import com.mi.goffer.dto.resp.*;
import com.mi.goffer.service.AssistantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 15:34
 * @Description: 大模型对话控制层
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/goffer")
public class ChatController {

    private final AssistantService assistantService;

    /**
     * 获取所有会话标题
     *
     * @return List<TitleRespDTO> 会话标题列表
     */
    @GetMapping("/get-title")
    public Result<List<TitleRespDTO>> getTitle() {
        return Results.success(assistantService.getAllChatTitle(UserContext.getCurrentUserId()));
    }

    /**
     * 聊天历史查询
     *
     * @param keyword 关键词
     * @return List<QueryChatHistoryRespDTO> 聊天历史列表
     */
    @GetMapping("/get-history")
    public Result<List<QueryChatHistoryRespDTO>> getChatHistory(@RequestParam String keyword) {
        return Results.success(assistantService.queryChatHistory(UserContext.getCurrentUserId(), keyword));
    }

    /**
     * 获取面试历史信息
     *
     * @return List<InterviewHistoryInfoRespDTO> 面试历史信息列表
     */
    @GetMapping("/get-interview-history")
    public Result<List<InterviewHistoryInfoRespDTO>> getInterviewHistory() {
        return Results.success(assistantService.getInterviewHistoryInfo(UserContext.getCurrentUserId()));
    }

    /**
     * 获取聊天消息
     *
     * @param sessionId 会话ID
     * @return List<ChatMessageRespDTO> 聊天消息列表
     */
    @GetMapping("/get-chat-message")
    public Result<List<ChatMessageRespDTO>> getChatMessage(@RequestParam String sessionId) {
        return Results.success(assistantService.getMessageBySessionId(sessionId));
    }

    /**
     * 获取面试消息
     *
     * @param sessionId 会话ID
     * @return List<InterviewMessageRespDTO> 面试消息列表
     */
    @GetMapping("/get-interview-message")
    public Result<List<InterviewMessageRespDTO>> getInterviewMessage(@RequestParam String sessionId) {
        return Results.success(assistantService.getInterviewMessageBySessionId(sessionId));
    }

    /**
     * 普通对话
     *
     * @param reqDTO 请求参数
     * @return Flux<String> 流式响应
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatRespDTO> chat(@RequestBody @Validated ChatReqDTO reqDTO) {
        return assistantService.chat(UserContext.getCurrentUserId(), reqDTO);
    }

    /**
     * 面试对话
     *
     * @param reqDTO 面试参数
     * @return Flux<InterviewRespDTO> 流式响应
     */
    @PostMapping(value = "/interview", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<InterviewRespDTO> interview(@RequestBody @Validated InterviewReqDTO reqDTO) {
        return assistantService.interview(UserContext.getCurrentUserId(), reqDTO);
    }

    /**
     * 预创建面试会话
     * <p>
     * 前端点击语音按钮时调用此接口，预先创建会话并返回sessionId
     *
     * @param mode 面试模式（1：后端面试、2：前端面试）
     * @return Long sessionId
     */
    @PostMapping("/interview/create-session")
    public Result<Long> createInterviewSession(@RequestParam Integer mode) {
        return Results.success(assistantService.createInterviewSession(UserContext.getCurrentUserId(), mode));
    }

    /**
     * 语音面试接口（合并：音频+文本流式输出）
     * <p>
     * 前端每 1.5 秒发送一个音频片段
     * 后端：ASR转写 → AI对话 → TTS合成 → 同时返回音频流和文本流
     * <p>
     * 响应说明：
     * - content 不为 null：文本片段，用于UI显示
     * - audioData 不为 null：音频片段，用于播放
     *
     * @param sessionId 会话ID
     * @param mode      面试模式（1：后端面试、2：前端面试）
     * @param audioFile 音频文件
     * @return Flux<VoiceInterviewRespDTO> 流式响应（包含音频和文本）
     */
    @PostMapping(value = "/interview/voice-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<VoiceInterviewRespDTO> voiceInterviewStream(
            @RequestParam Long sessionId,
            @RequestParam Integer mode,
            @RequestParam MultipartFile audioFile
    ) {
        return assistantService.voiceInterviewWithStream(UserContext.getCurrentUserId(), sessionId, mode, audioFile);
    }
}