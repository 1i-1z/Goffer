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
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // json 匹配正则，用于匹配面试模式下所返回的总结内容
    private static final Pattern JSON_PATTERN = Pattern.compile("```json\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);

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
                    parseAndSaveScore(userId, aiMessageDO.getMessageId(), aiResponse, finalSessionId);
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
     * 解析并保存面试评分数据
     *
     * @param userId     用户id
     * @param messageId  AI 消息id
     * @param aiResponse AI 完整回复
     * @param sessionId  会话id
     */
    private void parseAndSaveScore(String userId, Long messageId, String aiResponse, Long sessionId) {
        Matcher matcher = JSON_PATTERN.matcher(aiResponse);

        if (matcher.find()) {
            String jsonStr = matcher.group(1).trim();
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
                        .messageId(messageId)
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
