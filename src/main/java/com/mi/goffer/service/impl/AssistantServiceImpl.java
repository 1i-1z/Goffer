package com.mi.goffer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mi.goffer.common.convention.exception.ClientException;
import com.mi.goffer.common.enums.MessageRoleEnum;
import com.mi.goffer.common.prompt.ChatPrompt;
import com.mi.goffer.common.util.MilvusUtil;
import com.mi.goffer.dao.entity.InterviewKnowledgeDO;
import com.mi.goffer.dao.entity.MessagesDO;
import com.mi.goffer.dao.entity.ScoresDO;
import com.mi.goffer.dao.entity.SessionsDO;
import com.mi.goffer.dao.mapper.InterviewKnowledgeMapper;
import com.mi.goffer.dao.mapper.MessagesMapper;
import com.mi.goffer.dao.mapper.ScoresMapper;
import com.mi.goffer.dao.mapper.SessionsMapper;
import com.mi.goffer.dto.req.ChatReqDTO;
import com.mi.goffer.dto.req.InterviewReqDTO;
import com.mi.goffer.dto.resp.*;
import com.mi.goffer.service.AssistantService;
import com.mi.goffer.service.VoiceService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mi.goffer.common.constant.ChatConstant.*;
import static com.mi.goffer.common.convention.errorcode.BaseErrorCode.SESSION_MODE_NOT_INTERVIEW;
import static com.mi.goffer.common.convention.errorcode.BaseErrorCode.SESSION_NOT_FOUND;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 17:21
 * @Description: 大模型服务层实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AssistantServiceImpl implements AssistantService {

    private final MessagesMapper messagesMapper;
    private final SessionsMapper sessionsMapper;
    private final ScoresMapper scoresMapper;
    private final InterviewKnowledgeMapper knowledgeMapper;
    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final EmbeddingModel embeddingModel;
    private final ConversationContextManager conversationContextManager;
    private final MilvusUtil milvusUtil;
    private final VoiceService voiceService;

    // TTS 音频缓存：key = sessionId，value = 缓存的音频数据（无过期时间，前端请求后即删除）
    private final Map<Long, byte[]> ttsAudioCache = new java.util.concurrent.ConcurrentHashMap<>();
    // 文本流 Sinks 缓存：key = sessionId，value = sink（voiceInterview 写入，interviewTextStream 订阅）
    private final Map<Long, Sinks.Many<String>> textSinkCache = new java.util.concurrent.ConcurrentHashMap<>();

    // json 匹配正则
    private static final Pattern JSON_PATTERN = Pattern.compile(
            "```json\\s*\\n?(.*?)\\n?```|\\{[^}]*totalScore[^}]*\\}",
            Pattern.DOTALL
    );

    /**
     * 普通对话
     *
     * @param userId 用户id
     * @return Flux<ChatRespDTO> 流式响应
     */
    @Override
    public Flux<ChatRespDTO> chat(String userId, ChatReqDTO reqDTO) {
        Long sessionId = reqDTO.getSessionId();
        // 首次请求，sessionId 为空，自动创建会话
        if (sessionId == null) {
            SessionsDO newSession = SessionsDO.builder()
                    .userId(userId)
                    .title(DEFAULT_TITLE)
                    .mode(0)
                    .isDeleted(0)
                    .status(-1) // 设置普通会话状态
                    .build();
            sessionsMapper.insert(newSession);
            sessionId = newSession.getSessionId();
            // 异步生成标题
            generateTitleAsync(sessionId, reqDTO.getMessage());
        } else {
            SessionsDO sessionsDO = sessionsMapper.selectById(sessionId);
            if (sessionsDO == null) {
                throw new ClientException(SESSION_NOT_FOUND);
            }
        }
        final Long finalSessionId = sessionId;
        String message = reqDTO.getMessage();
        SessionsDO sessionsDO = sessionsMapper.selectById(sessionId);
        if (sessionsDO == null) {
            throw new ClientException(SESSION_NOT_FOUND);
        }
        // 持久化用户消息
        messagesMapper.insert(MessagesDO.builder()
                .sessionId(sessionId)
                .role(MessageRoleEnum.USER.getCode())
                .content(message)
                .build());

        List<MessagesDO> uncompressedMessages = conversationContextManager.getUncompressedMessages(sessionId, sessionsDO);
        if (conversationContextManager.needCompress(uncompressedMessages, sessionsDO.getMode())) {
            conversationContextManager.compress(uncompressedMessages, sessionsDO);
            // 压缩后重新加载未压缩部分
            uncompressedMessages = conversationContextManager.getUncompressedMessages(sessionId, sessionsDO);
        }

        // 构建聊天消息列表
        List<ChatMessage> messageList = new ArrayList<>();
        // 添加系统提示词
        messageList.add(SystemMessage.from(buildSystemPrompt(sessionsDO)));
        // 添加截断后的消息
        for (MessagesDO truncatedMessage : uncompressedMessages) {
            messageList.add(toChatMessage(truncatedMessage));
        }
        // 添加本轮用户消息
        messageList.add(UserMessage.from(message));

        // 流式响应
        StringBuffer fullResponse = new StringBuffer();
        return Flux.create(emitter -> streamingChatModel.chat(messageList, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                fullResponse.append(partialResponse);
                emitter.next(ChatRespDTO.builder()
                        .sessionId(finalSessionId)
                        .content(partialResponse)
                        .build());
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                messagesMapper.insert(MessagesDO.builder()
                        .sessionId(finalSessionId)
                        .role(MessageRoleEnum.ASSISTANT.getCode())
                        .content(fullResponse.toString())
                        .build()
                );
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                emitter.error(error);
            }
        }));
    }

    /**
     * 面试对话
     *
     * @param userId 用户id
     * @param reqDTO 请求参数
     * @return Flux<InterviewRespDTO> 流式响应
     */
    @Override
    public Flux<InterviewRespDTO> interview(String userId, InterviewReqDTO reqDTO) {
        Long sessionId = reqDTO.getSessionId();
        SessionsDO sessionsDO = null;
        // 首次请求，sessionId 为空，自动创建会话
        if (sessionId == null) {
            SessionsDO newSession = SessionsDO.builder()
                    .userId(userId)
                    .title(generateInterviewTitle(userId, reqDTO.getMode()))
                    .mode(reqDTO.getMode())
                    .isDeleted(0)
                    .status(0) // 设置面试开始状态
                    .build();
            sessionsMapper.insert(newSession);
            sessionId = newSession.getSessionId();
        } else {
            sessionsDO = sessionsMapper.selectById(sessionId);
            if (sessionsDO == null) {
                throw new ClientException(SESSION_NOT_FOUND);
            }
            if (sessionsDO.getMode() != 1 && sessionsDO.getMode() != 2) {
                throw new ClientException(SESSION_MODE_NOT_INTERVIEW);
            }
        }

        // 重新获取会话（首次请求时获取刚创建的）
        sessionsDO = sessionsMapper.selectById(sessionId);
        final Long finalSessionId = sessionId;
        final Integer currentStatus = sessionsDO.getStatus();
        String message = reqDTO.getMessage();
        // 持久化用户消息
        if (StringUtils.isNotBlank(message)) {
            MessagesDO userMessage = MessagesDO.builder()
                    .sessionId(sessionId)
                    .role(MessageRoleEnum.CANDIDATE.getCode())
                    .content(message)
                    .build();
            messagesMapper.insert(userMessage);
        }
        // 重新获取会话
        sessionsDO = sessionsMapper.selectById(sessionId);
        // 获取未压缩的消息列表
        List<MessagesDO> uncompressedMessages = conversationContextManager.getUncompressedMessages(sessionId, sessionsDO);
        // 检查是否需要压缩上下文
        if (conversationContextManager.needCompress(uncompressedMessages, sessionsDO.getMode())) {
            conversationContextManager.compress(uncompressedMessages, sessionsDO);
            // 重新加载会话和消息
            sessionsDO = sessionsMapper.selectById(sessionId);
            uncompressedMessages = conversationContextManager.getUncompressedMessages(sessionId, sessionsDO);
        }

        // 构建系统提示词
        String systemPrompt = buildSystemPromptForInterview(sessionsDO);
        // 知识检索（仅在用户有输入时执行）
        String knowledgeRef = null;
        if (StringUtils.isNotBlank(message)) {
            knowledgeRef = searchKnowledgeRef(message, sessionsDO.getMode());
        }
        if (StringUtils.isNotBlank(knowledgeRef)) {
            systemPrompt = systemPrompt.replace(TECH_REFERENCE, knowledgeRef);
        } else {
            systemPrompt = systemPrompt.replace(TECH_REFERENCE, NO_KNOWLEDGE_REFERENCE);
        }

        // 构建聊天消息列表
        List<ChatMessage> messageList = new ArrayList<>();
        messageList.add(SystemMessage.from(systemPrompt));
        for (MessagesDO truncatedMessage : uncompressedMessages) {
            messageList.add(toInterviewMessage(truncatedMessage));
        }
        if (StringUtils.isNotBlank(message)) {
            messageList.add(UserMessage.from(message));
        }
        // 流式响应
        StringBuffer fullResponse = new StringBuffer();
        // JSON 块过滤状态机：记录当前是否处于 ```json ... ``` 块内
        final boolean[] inJsonBlock = {false};
        // 缓存已进入 JSON 块时的上一个字符，用于检测 ``` 开头
        final StringBuffer jsonBuffer = new StringBuffer();
        return Flux.create(emitter -> streamingChatModel.chat(messageList, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                fullResponse.append(partialResponse);
                StringBuilder filtered = new StringBuilder();
                for (char c : partialResponse.toCharArray()) {
                    if (inJsonBlock[0]) {
                        // 处于 JSON 块内，累积直到检测到结束标记
                        jsonBuffer.append(c);
                        if (jsonBuffer.length() >= 4) {
                            String tail = jsonBuffer.substring(jsonBuffer.length() - 4);
                            if (tail.equals("```\n") || tail.equals("```")) {
                                inJsonBlock[0] = false;
                                jsonBuffer.setLength(0);
                            }
                        }
                    } else {
                        // 正常内容，检查是否进入 JSON 块
                        jsonBuffer.append(c);
                        if (jsonBuffer.length() >= 7) {
                            String tail = jsonBuffer.substring(jsonBuffer.length() - 7);
                            if (tail.equals("```json\n") || tail.equals("```json")) {
                                // 进入 JSON 块，删除 ```json\n 部分
                                filtered.deleteCharAt(filtered.length() - 1); // 去掉末尾换行符残留
                                jsonBuffer.setLength(0);
                                inJsonBlock[0] = true;
                            } else {
                                // 不是 JSON 开头，保留当前字符
                                filtered.append(c);
                                if (jsonBuffer.length() > 7) {
                                    jsonBuffer.deleteCharAt(0);
                                }
                            }
                        } else {
                            filtered.append(c);
                        }
                    }
                }
                if (filtered.length() > 0) {
                    emitter.next(InterviewRespDTO.builder()
                            .sessionId(finalSessionId)
                            .content(filtered.toString())
                            .build());
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                String aiResponse = fullResponse.toString();
                // 持久化 AI 响应
                MessagesDO aiMessageDO = MessagesDO.builder()
                        .sessionId(finalSessionId)
                        .role(MessageRoleEnum.INTERVIEWER.getCode())
                        .content(aiResponse)
                        .build();
                messagesMapper.insert(aiMessageDO);
                // 检测并保存评分（仅在面试进行中状态才处理）
                if (currentStatus == 0) {
                    parseAndSaveScore(userId, aiMessageDO.getSessionId(), aiResponse);
                }
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                emitter.error(error);
            }
        }));
    }

    /**
     * 语音面试
     *
     * @param userId    用户id
     * @param sessionId 会话ID
     * @param mode      面试模式（1：后端面试、2：前端面试）
     * @return Flux<byte[]> 音频流
     */
    @Override
    public Flux<byte[]> voiceInterview(String userId, Long sessionId, Integer mode, MultipartFile audioFile) {
        SessionsDO sessionsDO = null;
        // 首次请求，sessionId 为空，自动创建会话
        if (sessionId == null) {
            SessionsDO newSession = SessionsDO.builder()
                    .userId(userId)
                    .title(generateInterviewTitle(userId, mode))
                    .mode(mode)
                    .isDeleted(0)
                    .status(0)
                    .build();
            sessionsMapper.insert(newSession);
            sessionId = newSession.getSessionId();
        } else {
            sessionsDO = sessionsMapper.selectById(sessionId);
            if (sessionsDO == null) {
                throw new ClientException(SESSION_NOT_FOUND);
            }
            if (sessionsDO.getMode() != 1 && sessionsDO.getMode() != 2) {
                throw new ClientException(SESSION_MODE_NOT_INTERVIEW);
            }
        }

        final Long finalSessionId = sessionId;
        // ASR: 音频 → 文本
        String transcribedText = voiceService.speechToText(audioFile);
        log.info("voiceInterview - sessionId: {}, ASR转写结果: {}", finalSessionId, transcribedText);

        // 无有效语音输入，返回静音
        if (StringUtils.isBlank(transcribedText)) {
            return Flux.just(voiceService.generateSilence());
        }

        // ASR 有结果，开始 AI → TTS 链路
        StringBuilder aiResponse = new StringBuilder();
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
        textSinkCache.put(finalSessionId, sink);

        return Flux.create(emitter -> {
            // 调用流式响应，收集完整文本后执行 TTS
            streamInterviewResponseWithCallback(
                    userId,
                    finalSessionId,
                    transcribedText,
                    textChunk -> {
                        aiResponse.append(textChunk);
                        // 同步向 SSE 订阅者推送文本片段
                        Sinks.EmitResult result = sink.tryEmitNext(textChunk);
                        if (result.isFailure()) {
                            log.warn("textSink推送失败 sessionId: {}, reason: {}", finalSessionId, result);
                        }
                    },
                    filteredText -> {
                        log.info("voiceInterview - AI完整回复(过滤后): {}, sessionId: {}", filteredText, finalSessionId);

                        // 完成文本流
                        sink.tryEmitComplete();
                        textSinkCache.remove(finalSessionId);

                        // TTS: 文本 → 音频（流式返回）
                        try {
                            Flux<byte[]> ttsFlux = voiceService.textToSpeechStream(filteredText);
                            ttsFlux.subscribe(
                                    emitter::next,
                                    e -> {
                                        log.error("TTS生成失败", e);
                                        emitter.next(voiceService.generateSilence());
                                        emitter.complete();
                                    },
                                    emitter::complete
                            );
                        } catch (Exception e) {
                            log.error("TTS生成异常", e);
                            emitter.next(voiceService.generateSilence());
                            emitter.complete();
                        }
                    },
                    emitter::error
            );
        });
    }

    @Override
    public byte[] voiceInterviewByte(String userId, Long sessionId, Integer mode, MultipartFile audioFile) {
        // 先查 TTS 缓存，有则直接返回（避免重复 LLM 调用）
        byte[] cachedAudio = ttsAudioCache.remove(sessionId);
        if (cachedAudio != null) {
            log.info("voiceInterviewByte - 命中TTS缓存, sessionId: {}, size: {} bytes", sessionId, cachedAudio.length);
            return cachedAudio;
        }

        // 缓存未命中，走原有流程：ASR → LLM → TTS
        Flux<byte[]> audioFlux = voiceInterview(userId, sessionId, mode, audioFile);
        return audioFlux.reduce(new byte[0], (a, b) -> {
            byte[] result = new byte[a.length + b.length];
            System.arraycopy(a, 0, result, 0, a.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return result;
        }).block(Duration.ofSeconds(60));
    }

    /**
     * 语音面试（返回流式数据）
     *
     * @param userId    用户id
     * @param sessionId 会话ID
     * @param mode      面试模式（1：后端面试、2：前端面试）
     * @param audioFile 音频文件
     * @return Flux<InterviewRespDTO> 流式响应
     */
    @Override
    public Flux<InterviewRespDTO> interviewTextStream(String userId, Long sessionId, Integer mode, MultipartFile audioFile) {
        // 查文本流 sink
        Sinks.Many<String> sink = textSinkCache.get(sessionId);
        if (sink == null) {
            log.warn("interviewTextStream - 未找到文本流, sessionId: {}, 请先调用 voiceInterview 接口", sessionId);
            return Flux.error(new ClientException("请先调用语音面试接口"));
        }

        // 订阅 voiceInterview 推送的文本流，转换为 InterviewRespDTO
        Flux<String> textFlux = sink.asFlux()
                .onErrorResume(e -> {
                    log.error("interviewTextStream - 文本流异常, sessionId: {}", sessionId, e);
                    return Flux.empty();
                });

        return textFlux.map(textChunk -> InterviewRespDTO.builder()
                .sessionId(sessionId)
                .content(textChunk)
                .build());
    }

    /**
     * 带回调的面试流式响应（内部使用）
     *
     * @param userId     用户id
     * @param sessionId  会话id
     * @param message    用户消息
     * @param onPartial  部分响应回调
     * @param onComplete 完成回调，参数为过滤后可供 TTS 使用的文本
     * @param onError    错误回调
     */
    private void streamInterviewResponseWithCallback(
            String userId,
            Long sessionId,
            String message,
            java.util.function.Consumer<String> onPartial,
            java.util.function.Consumer<String> onComplete,
            java.util.function.Consumer<Throwable> onError
    ) {
        SessionsDO sessionsDO = sessionsMapper.selectById(sessionId);
        if (sessionsDO == null) {
            onError.accept(new ClientException(SESSION_NOT_FOUND));
            return;
        }

        // 持久化用户消息
        if (StringUtils.isNotBlank(message)) {
            messagesMapper.insert(MessagesDO.builder()
                    .sessionId(sessionId)
                    .role(MessageRoleEnum.CANDIDATE.getCode())
                    .content(message)
                    .build());
        }

        // 上下文压缩检查
        List<MessagesDO> uncompressedMessages = conversationContextManager.getUncompressedMessages(sessionId, sessionsDO);
        if (conversationContextManager.needCompress(uncompressedMessages, sessionsDO.getMode())) {
            conversationContextManager.compress(uncompressedMessages, sessionsDO);
            sessionsDO = sessionsMapper.selectById(sessionId);
            uncompressedMessages = conversationContextManager.getUncompressedMessages(sessionId, sessionsDO);
        }

        // 构建系统提示词
        String systemPrompt = buildSystemPromptForVoiceInterview(sessionsDO);
        String knowledgeRef = null;
        if (StringUtils.isNotBlank(message)) {
            knowledgeRef = searchKnowledgeRef(message, sessionsDO.getMode());
        }
        if (StringUtils.isNotBlank(knowledgeRef)) {
            systemPrompt = systemPrompt.replace(TECH_REFERENCE, knowledgeRef);
        } else {
            systemPrompt = systemPrompt.replace(TECH_REFERENCE, NO_KNOWLEDGE_REFERENCE);
        }

        // 构建聊天消息列表
        List<ChatMessage> messageList = new ArrayList<>();
        messageList.add(SystemMessage.from(systemPrompt));
        for (MessagesDO truncatedMessage : uncompressedMessages) {
            messageList.add(toInterviewMessage(truncatedMessage));
        }
        if (StringUtils.isNotBlank(message)) {
            messageList.add(UserMessage.from(message));
        }

        // 流式响应
        StringBuffer fullResponse = new StringBuffer();
        final boolean[] inJsonBlock = {false};
        final StringBuffer jsonBuffer = new StringBuffer();
        // 行级缓冲：用于流式输出时检测并过滤整行
        final StringBuilder lineBuffer = new StringBuilder();
        final boolean[] isFilteringLine = {false};

        streamingChatModel.chat(messageList, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                fullResponse.append(partialResponse);
                StringBuilder filtered = new StringBuilder();

                for (char c : partialResponse.toCharArray()) {
                    if (inJsonBlock[0]) {
                        // 处于 JSON 块内，累积直到检测到结束标记
                        jsonBuffer.append(c);
                        if (jsonBuffer.length() >= 4) {
                            String tail = jsonBuffer.substring(jsonBuffer.length() - 4);
                            if (tail.equals("```\n") || tail.equals("```")) {
                                inJsonBlock[0] = false;
                                jsonBuffer.setLength(0);
                            }
                        }
                    } else {
                        // 正常内容，检查是否进入 JSON 块
                        jsonBuffer.append(c);
                        if (jsonBuffer.length() >= 7) {
                            String tail = jsonBuffer.substring(jsonBuffer.length() - 7);
                            if (tail.equals("```json\n") || tail.equals("```json")) {
                                // 进入 JSON 块，删除 ```json\n 部分
                                filtered.deleteCharAt(filtered.length() - 1);
                                jsonBuffer.setLength(0);
                                inJsonBlock[0] = true;
                            } else {
                                // 不是 JSON 开头，正常处理字符
                                if (jsonBuffer.length() > 7) {
                                    jsonBuffer.deleteCharAt(0);
                                }

                                // 处理字符
                                if (isFilteringLine[0]) {
                                    // 当前处于过滤状态，跳过该行所有字符直到换行
                                    if (c == '\n') {
                                        isFilteringLine[0] = false;
                                        lineBuffer.setLength(0);
                                    }
                                } else {
                                    // 正常状态，添加到行缓冲区
                                    lineBuffer.append(c);
                                    String currentBuffer = lineBuffer.toString();

                                    // 检查是否需要进入过滤状态（检测缓冲区结尾）
                                    if (shouldStartFiltering(currentBuffer)) {
                                        isFilteringLine[0] = true;
                                        // 删除已添加到过滤结果的该行内容
                                        // 找到最后一个换行符的位置
                                        int lastNewline = filtered.lastIndexOf("\n");
                                        if (lastNewline >= 0) {
                                            filtered.setLength(lastNewline + 1);
                                        } else {
                                            filtered.setLength(0);
                                        }
                                    } else {
                                        // 正常输出
                                        filtered.append(c);
                                    }

                                    // 如果是换行符，重置行缓冲区
                                    if (c == '\n') {
                                        lineBuffer.setLength(0);
                                    }
                                }
                            }
                        } else {
                            // 不到7个字符，正常处理
                            if (!isFilteringLine[0]) {
                                filtered.append(c);
                            }
                            lineBuffer.append(c);
                            if (c == '\n') {
                                lineBuffer.setLength(0);
                            }
                        }
                    }
                }

                if (filtered.length() > 0) {
                    onPartial.accept(filtered.toString());
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                String aiResponse = fullResponse.toString();
                // 最终过滤：移除所有引导性文字和评价（兜底处理）
                String filteredForTts = filterInterviewOutput(aiResponse);
                // 持久化 AI 响应（保留完整内容，包括 JSON）
                messagesMapper.insert(MessagesDO.builder()
                        .sessionId(sessionId)
                        .role(MessageRoleEnum.INTERVIEWER.getCode())
                        .content(aiResponse)
                        .build());
                // 解析并保存评分
                parseAndSaveScore(userId, sessionId, aiResponse);
                // 通知完成，传递过滤后的文本供 TTS 使用
                onComplete.accept(filteredForTts);
            }

            @Override
            public void onError(Throwable error) {
                onError.accept(error);
            }
        });
    }

    /**
     * 判断当前缓冲区是否应该开始过滤
     * 使用贪婪匹配：检测缓冲区结尾是否匹配过滤模式
     *
     * @param currentBuffer 当前行缓冲区内容
     * @return true 表示应该开始过滤该行
     */
    private boolean shouldStartFiltering(String currentBuffer) {
        if (currentBuffer == null || currentBuffer.isEmpty()) {
            return false;
        }
        String trimmed = currentBuffer.trim();

        // 检查各种需要过滤的模式（贪婪匹配）
        // 评价行（直接以评价开头）
        if (trimmed.matches("^评价[：:].*")) {
            return true;
        }
        // 精确追问行
        if (trimmed.matches("^精确追问[：:].*")) {
            return true;
        }
        // 追问引导行
        if (trimmed.matches("^追问点如下.*") || trimmed.matches("^以下是追问.*")) {
            return true;
        }
        // 追问行
        if (trimmed.matches("^追问[：:].*")) {
            return true;
        }
        // 包含分数的行（"xxx分"格式，且行长度合理）
        // 匹配模式：任意内容 + 数字 + 分
        if (trimmed.matches(".*\\d+分.*") && trimmed.length() < 200) {
            return true;
        }
        return false;
    }

    /**
     * 从文本中移除包含 totalScore 的 JSON 对象
     * 通过向前找 {、向后匹配 } 来处理嵌套花括号
     */
    private String removeJsonByBraceMatch(String text) {
        String marker = "totalScore";
        int idx = text.indexOf(marker);
        if (idx == -1) {
            return text;
        }
        int start = idx;
        while (start > 0 && text.charAt(start - 1) != '{') {
            start--;
        }
        if (start == 0) {
            return text;
        }
        int braceCount = 0;
        int end = idx;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    end++;
                    break;
                }
            }
            end++;
        }
        return text.substring(0, start) + text.substring(end);
    }

    /**
     * 过滤面试输出中的引导性文字和评价
     * 只保留题目和追问内容，移除"精确追问："、"评价："、"以下是追问"等前缀
     *
     * @param text 原始 AI 输出
     * @return 过滤后的文本
     */
    private String filterInterviewOutput(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        // 移除所有以"精确追问："开头的行（整行移除）
        text = text.replaceAll("(?m)^精确追问[：:].*$", "");
        // 移除所有以"评价："开头的行（整行移除）
        text = text.replaceAll("(?m)^评价[：:].*$", "");
        // 移除所有以"以下是追问"开头的行
        text = text.replaceAll("(?m)^以下是追问.*$", "");
        // 移除所有以"追问点如下"开头的行
        text = text.replaceAll("(?m)^追问点如下.*$", "");
        // 移除所有以"追问："开头的行
        text = text.replaceAll("(?m)^追问[：:].*$", "");
        // 移除包含分数的行（如"6分"、"7分"等）
        text = text.replaceAll("(?m)^.*\\d+分.*$", "");
        // 移除纯 JSON 块（用于评分的，从 totalScore 向前找 { 向后匹配 }）
        text = removeJsonByBraceMatch(text);
        // 清理多余的空行
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.trim();
    }

    /**
     * 获取所有会话标题
     *
     * @param userId 用户id
     * @return List<TitleRespDTO> 会话标题列表
     */
    @Override
    public List<TitleRespDTO> getAllChatTitle(String userId) {
        return sessionsMapper.selectList(Wrappers.lambdaQuery(SessionsDO.class)
                        .eq(SessionsDO::getUserId, userId)
                        .eq(SessionsDO::getMode, 0)
                        .eq(SessionsDO::getIsDeleted, 0)
                        .eq(SessionsDO::getStatus, -1)
                        .orderByDesc(SessionsDO::getSessionId))
                .stream().map(sessionsDO -> TitleRespDTO.builder()
                        .sessionId(sessionsDO.getSessionId())
                        .title(sessionsDO.getTitle())
                        .createTime(sessionsDO.getCreateTime())
                        .build())
                .toList();
    }

    /**
     * 查询会话历史
     *
     * @param userId  用户id
     * @param keyword 关键词
     * @return List<QueryChatHistoryRespDTO> 会话历史列表
     * <p>
     * 这里先用模糊查询查询数据库中的 Title，后续如果需要重构，
     * 会考虑将内容持久化到 Elasticsearch 中，
     * 并使用 Elasticsearch 的 Full-Text Search 功能进行查询。
     */
    @Override
    public List<QueryChatHistoryRespDTO> queryChatHistory(String userId, String keyword) {
        return sessionsMapper.selectList(Wrappers.lambdaQuery(SessionsDO.class)
                        .eq(SessionsDO::getUserId, userId)
                        .eq(SessionsDO::getMode, 0)
                        .eq(SessionsDO::getIsDeleted, 0)
                        .eq(SessionsDO::getStatus, -1)
                        .like(SessionsDO::getTitle, keyword)
                        .orderByDesc(SessionsDO::getSessionId))
                .stream().map(sessionsDO -> QueryChatHistoryRespDTO.builder()
                        .sessionId(sessionsDO.getSessionId())
                        .title(sessionsDO.getTitle())
                        .createTime(sessionsDO.getCreateTime())
                        .build())
                .toList();
    }

    /**
     * 获取面试历史信息
     *
     * @param userId 用户id
     * @return List<InterviewHistoryInfoRespDTO> 面试历史信息列表
     */
    @Override
    public List<InterviewHistoryInfoRespDTO> getInterviewHistoryInfo(String userId) {
        List<SessionsDO> sessions = sessionsMapper.selectList(Wrappers.lambdaQuery(SessionsDO.class)
                .eq(SessionsDO::getUserId, userId)
                .eq(SessionsDO::getMode, 1)
                .eq(SessionsDO::getIsDeleted, 0)
                .in(SessionsDO::getStatus, 0, 1)
                .orderByDesc(SessionsDO::getCreateTime));
        List<ScoresDO> scores = scoresMapper.selectList(Wrappers.lambdaQuery(ScoresDO.class)
                .eq(ScoresDO::getUserId, userId));
        // sessionId -> ScoresDO
        Map<Long, ScoresDO> scoreMaps = scores.stream()
                .collect(Collectors.toMap(ScoresDO::getSessionId, scoresDO -> scoresDO));

        return sessions.stream()
                .map(session -> {
                    ScoresDO score = scoreMaps.get(session.getSessionId());
                    List<CategoryScoreRespDTO> categoryScores = new ArrayList<>();

                    if (score != null && score.getScore() != null) {
                        categoryScores = score.getScore().entrySet().stream()
                                .map(entry -> CategoryScoreRespDTO.builder()
                                        .category(entry.getKey())
                                        .score(entry.getValue())
                                        .build())
                                .toList();
                    }

                    return InterviewHistoryInfoRespDTO.builder()
                            .sessionId(session.getSessionId())
                            .title(session.getTitle())
                            .createTime(session.getCreateTime())
                            .categoryScoreRespDTOList(categoryScores)
                            .build();
                })
                .toList();
    }

    /**
     * 根据会话id获取消息列表
     *
     * @param sessionId 会话id
     * @return List<ChatMessageRespDTO> 消息列表
     */
    @Override
    public List<ChatMessageRespDTO> getMessageBySessionId(String sessionId) {
        return messagesMapper.selectList(Wrappers.lambdaQuery(MessagesDO.class)
                        .eq(MessagesDO::getSessionId, sessionId)
                        .orderByAsc(MessagesDO::getMessageId))
                .stream().map(messagesDO -> ChatMessageRespDTO.builder()
                        .role(messagesDO.getRole())
                        .content(messagesDO.getContent())
                        .build())
                .toList();
    }

    /**
     * 根据会话id获取面试消息列表
     *
     * @param sessionId 会话id
     * @return List<InterviewMessageRespDTO> 面试消息列表
     */
    @Override
    public List<InterviewMessageRespDTO> getInterviewMessageBySessionId(String sessionId) {
        return messagesMapper.selectList(Wrappers.lambdaQuery(MessagesDO.class)
                        .eq(MessagesDO::getSessionId, sessionId)
                        .orderByAsc(MessagesDO::getMessageId))
                .stream().map(messagesDO -> InterviewMessageRespDTO.builder()
                        .role(messagesDO.getRole())
                        .content(messagesDO.getContent())
                        .createTime(messagesDO.getCreateTime())
                        .build())
                .toList();
    }

    /**
     * 异步生成会话标题
     *
     * @param sessionId   会话id
     * @param userMessage 用户消息
     */
    private void generateTitleAsync(Long sessionId, String userMessage) {
        CompletableFuture.runAsync(() -> {
            try {
                // 构建系统提示词
                String prompt = ChatPrompt.GENERATE_TITLE_PROMPT.replace("{conversation}", userMessage);
                // 调用大模型生成标题
                String title = chatModel.chat(List.of(UserMessage.from(prompt)))
                        .aiMessage()
                        .text()
                        .trim();
                sessionsMapper.update(Wrappers.lambdaUpdate(SessionsDO.class)
                        .eq(SessionsDO::getSessionId, sessionId)
                        .set(SessionsDO::getTitle, title));
            } catch (Exception e) {
                // 吞掉异常，不影响主流程
            }
        });
    }

    /**
     * 生成面试标题
     *
     * @param userId 用户id
     * @param mode   模式：1（后端面试）、2（前端面试）
     * @return 面试标题
     */
    private String generateInterviewTitle(String userId, Integer mode) {
        // 获取今天的日期范围
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        // 查询当天该用户的面试次数
        Long count = sessionsMapper.selectCount(new LambdaQueryWrapper<SessionsDO>()
                .eq(SessionsDO::getUserId, userId)
                .eq(SessionsDO::getMode, mode)
                .ge(SessionsDO::getCreateTime, startOfDay)
                .lt(SessionsDO::getCreateTime, endOfDay));

        // 生成面试标题: yyyy.MM.dd-后端面试-01
        String dateStr = today.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        String modeName = mode == 1 ? BACK_END_INTERVIEW : FRONT_END_INTERVIEW;
        String sequence = String.format("%02d", count.intValue() + 1);

        return dateStr + "-" + modeName + "-" + sequence;
    }

    /**
     * 构建系统提示词（包含摘要上下文）
     *
     * @param sessionsDO 会话信息
     * @return 完整的系统提示词
     */
    private String buildSystemPrompt(SessionsDO sessionsDO) {
        Integer mode = sessionsDO.getMode();
        String systemPrompt = ChatPrompt.getSystemPrompt(mode);
        if (StringUtils.isNotBlank(sessionsDO.getLastCompress())) {
            systemPrompt += COMPRESS_HEADER + sessionsDO.getLastCompress();
        }
        return systemPrompt;
    }

    /**
     * 构建面试系统提示词
     *
     * @param sessionsDO 会话信息
     * @return 系统提示词
     */
    private String buildSystemPromptForInterview(SessionsDO sessionsDO) {
        Integer mode = sessionsDO.getMode();
        String systemPrompt = ChatPrompt.getSystemPrompt(mode);

        // 根据 mode 注入对应分类列表（1=后端，2=前端）
        if (mode == 1) {
            systemPrompt = systemPrompt.replace("{common_categories}",
                    String.join("、", BACK_END_INTERVIEW_CATEGORIES));
        } else if (mode == 2) {
            systemPrompt = systemPrompt.replace("{common_categories}",
                    String.join("、", FRONT_END_INTERVIEW_CATEGORIES));
        }

        if (StringUtils.isNotBlank(sessionsDO.getLastCompress())) {
            systemPrompt += COMPRESS_HEADER + sessionsDO.getLastCompress();
        }
        return systemPrompt;
    }

    /**
     * 构建语音面试系统提示词
     *
     * @param sessionsDO 会话信息
     * @return 系统提示词
     */
    private String buildSystemPromptForVoiceInterview(SessionsDO sessionsDO) {
        Integer mode = sessionsDO.getMode();
        String systemPrompt = ChatPrompt.getVoiceSystemPrompt(mode);

        // 根据 mode 注入对应分类列表（1=后端，2=前端）
        if (mode == 1) {
            systemPrompt = systemPrompt.replace("{common_categories}",
                    String.join("、", BACK_END_INTERVIEW_CATEGORIES));
        } else if (mode == 2) {
            systemPrompt = systemPrompt.replace("{common_categories}",
                    String.join("、", FRONT_END_INTERVIEW_CATEGORIES));
        }

        if (StringUtils.isNotBlank(sessionsDO.getLastCompress())) {
            systemPrompt += COMPRESS_HEADER + sessionsDO.getLastCompress();
        }
        return systemPrompt;
    }

    /**
     * 解析并保存面试评分数据
     *
     * @param userId     用户id
     * @param sessionId  会话id
     * @param aiResponse AI 完整回复
     */
    private void parseAndSaveScore(String userId, Long sessionId, String aiResponse) {
        Matcher matcher = JSON_PATTERN.matcher(aiResponse);

        if (matcher.find()) {
            // 支持两种格式：
            // 1. 代码块格式：```json ... ``` → group(1) 包含 JSON
            // 2. 纯文本格式：{...} → group(0) 包含完整 JSON
            String jsonStr = (matcher.group(1) != null ? matcher.group(1) : matcher.group(0)).trim();
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode jsonNode = objectMapper.readTree(jsonStr);
                // 使用 Jackson 解析 score 字段（Map<String, Integer>）
                Map<String, Integer> scoreMap = objectMapper.convertValue(
                        jsonNode.get("score"),
                        new TypeReference<>() {
                        }
                );

                ScoresDO scoresDO = ScoresDO.builder()
                        .userId(userId)
                        .sessionId(sessionId)
                        .totalScore(jsonNode.get("totalScore").asInt())
                        .score(scoreMap)
                        .evaluation(jsonNode.get("evaluation").asText())
                        .advantages(jsonNode.get("advantages").asText())
                        .disadvantages(jsonNode.get("disadvantages").asText())
                        .suggestion(jsonNode.get("suggestion").asText())
                        .build();
                // 持久化评分数据
                scoresMapper.insert(scoresDO);
                // 更新会话状态为"面试复盘完成"
                sessionsMapper.updateById(SessionsDO.builder()
                        .sessionId(sessionId)
                        .status(1)
                        .build());
            } catch (Exception e) {
                log.error("解析面试评分 JSON 失败, jsonStr: {}", jsonStr, e);
            }
        }
    }

    /**
     * RAG 检索：从 Milvus 查询相关知识，注入 system prompt
     *
     * @param userAnswer 用户本轮回答
     * @param mode       面试模式（1=后端，2=前端）
     * @return 格式化后的【技术背景参考】字符串，查不到返回空字符串
     */
    private String searchKnowledgeRef(String userAnswer, Integer mode) {
        try {
            // 向量化用户回答
            var embedding = embeddingModel.embed(userAnswer).content();
            List<Float> vector = embedding.vectorAsList();
            // 查 Milvus，取 topK=3 条
            List<Long> knowledgeIds = milvusUtil.searchByVector(vector, 3, null, null);
            if (knowledgeIds == null || knowledgeIds.isEmpty()) {
                return "";
            }
            // 回查 MySQL 获取知识详情
            List<InterviewKnowledgeDO> knowledgeList = knowledgeMapper.selectBatchIds(knowledgeIds);
            if (knowledgeList == null || knowledgeList.isEmpty()) {
                return "";
            }
            // 拼接为【技术背景参考】格式
            StringBuilder sb = new StringBuilder();
            for (InterviewKnowledgeDO k : knowledgeList) {
                sb.append("【").append(k.getCategory())
                        .append(" - ").append(k.getChunkTitle()).append("】\n")
                        .append(k.getChunkContent()).append("\n\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.error("[RAG] 知识检索失败，已跳过", e);
            return "";
        }
    }

    /**
     * 将 MessagesDO 转换为 ChatMessage
     *
     * @param messagesDO 消息对象
     * @return ChatMessage 聊天消息对象
     */
    private ChatMessage toChatMessage(MessagesDO messagesDO) {
        return messagesDO.getRole().equals(MessageRoleEnum.USER.getCode()) ?
                UserMessage.from(messagesDO.getContent()) : AiMessage.from(messagesDO.getContent());
    }

    /**
     * 将面试模式下的 MessagesDO 转换为 ChatMessage
     *
     * @param messagesDO 消息对象
     * @return ChatMessage 聊天消息对象
     */
    private ChatMessage toInterviewMessage(MessagesDO messagesDO) {
        return messagesDO.getRole().equals(MessageRoleEnum.CANDIDATE.getCode()) ?
                UserMessage.from(messagesDO.getContent()) : AiMessage.from(messagesDO.getContent());
    }
}
