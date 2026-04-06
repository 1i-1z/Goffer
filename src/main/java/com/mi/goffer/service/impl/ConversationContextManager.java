package com.mi.goffer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mi.goffer.common.convention.exception.ClientException;
import com.mi.goffer.common.enums.MessageRoleEnum;
import com.mi.goffer.dao.entity.MessagesDO;
import com.mi.goffer.dao.entity.SessionsDO;
import com.mi.goffer.dao.mapper.MessagesMapper;
import com.mi.goffer.dao.mapper.SessionsMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

import static com.mi.goffer.common.constant.ChatConstant.CHAT_COMPRESS_TRIGGER_TURNS;
import static com.mi.goffer.common.constant.ChatConstant.INTERVIEW_COMPRESS_TRIGGER_TURNS;
import static com.mi.goffer.common.convention.errorcode.BaseErrorCode.SESSION_COMPRESS_FAILED;
import static com.mi.goffer.common.prompt.ChatPrompt.COMPRESS_SYSTEM_PROMPT;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/26 23:23
 * @Description: 会话上下文管理器
 */
@Service
@RequiredArgsConstructor
public class ConversationContextManager {

    private final MessagesMapper messagesMapper;
    private final SessionsMapper sessionsMapper;
    private final ChatModel chatModel;

    /**
     * 加载会话历史
     *
     * @param sessionId 会话id
     * @return 会话历史
     */
    public List<MessagesDO> loadHistory(Long sessionId) {
        LambdaQueryWrapper<MessagesDO> queryWrapper = Wrappers.lambdaQuery(MessagesDO.class)
                .eq(MessagesDO::getSessionId, sessionId)
                .orderByAsc(MessagesDO::getMessageId);
        return messagesMapper.selectList(queryWrapper);
    }

    /**
     * 判断是否需要生成摘要
     *
     * @param messages 待判断的消息列表
     * @param mode     会话模式：0（普通对话）、1（后端面试）、2（前端面试）
     * @return 是否需要生成摘要
     */
    public boolean needCompress(List<MessagesDO> messages, Integer mode) {
        if (CollectionUtils.isEmpty(messages)) return false;
        long userMessageCount = messages.stream()
                .filter(message -> message.getRole().equals(MessageRoleEnum.USER.getCode()))
                .count();
        return mode == 0
                ? userMessageCount >= CHAT_COMPRESS_TRIGGER_TURNS
                : userMessageCount >= INTERVIEW_COMPRESS_TRIGGER_TURNS;
    }

    /**
     * 获取未压缩的消息列表
     *
     * @param sessionId  会话id
     * @param sessionsDO 会话信息
     * @return 未压缩的消息列表
     */
    public List<MessagesDO> getUncompressedMessages(Long sessionId, SessionsDO sessionsDO) {
        Long lastCompressedMessageId = sessionsDO.getLastCompressedMessageId();
        // 如果没有压缩消息，则返回所有消息
        if (lastCompressedMessageId == null) {
            return loadHistory(sessionId);
        }
        // 获取未压缩的消息
        return messagesMapper.selectList(
                Wrappers.lambdaQuery(MessagesDO.class)
                        .eq(MessagesDO::getSessionId, sessionId)
                        .gt(MessagesDO::getMessageId, lastCompressedMessageId)
                        .orderByAsc(MessagesDO::getMessageId)
        );
    }

    /**
     * 压缩消息列表
     *
     * @param uncompressedMessages 未压缩的消息列表
     * @param sessionsDO           会话信息
     */
    public void compress(List<MessagesDO> uncompressedMessages, SessionsDO sessionsDO) {
        if (CollectionUtils.isEmpty(uncompressedMessages)) return;

        // 带上上一次摘要，一起传给 LLM 重新压缩
        String previousCompress = sessionsDO.getLastCompress();
        String compressResult;
        // 根据会话模式选择对应的压缩生成方式
        if (sessionsDO.getMode() == 0) {
            // 普通会话
            compressResult = callLlmForCompress(formatChatMessagesForCompress(uncompressedMessages), previousCompress);
        } else {
            // 面试会话
            compressResult = callLlmForInterviewCompress(formatInterviewMessagesForCompress(uncompressedMessages), previousCompress);
        }

        // 只记录最大 messageId
        long maxMessageId = uncompressedMessages.stream()
                .mapToLong(MessagesDO::getMessageId)
                .max()
                .orElse(0L);
        // 将压缩持久化进数据库
        sessionsMapper.updateById(SessionsDO.builder()
                .sessionId(sessionsDO.getSessionId())
                .lastCompress(compressResult)
                .lastCompressedMessageId(maxMessageId)
                .build());
    }

    /**
     * 将普通对话消息列表格式化为压缩提示词
     *
     * @param messages 消息列表
     * @return 格式化的对话历史
     */
    private String formatChatMessagesForCompress(List<MessagesDO> messages) {
        StringBuilder sb = new StringBuilder();
        for (MessagesDO message : messages) {
            sb.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 将面试对话消息列表格式化为压缩提示词
     * TODO: 实现面试对话的格式化逻辑
     *
     * @param messages 消息列表
     * @return 格式化的对话历史
     */
    private String formatInterviewMessagesForCompress(List<MessagesDO> messages) {
        // TODO: 面试对话的摘要格式化逻辑
        // 预期行为：根据面试阶段（技术基础/项目/场景题）分组格式化，
        // 保留面试官的问题模式、候选人的回答要点、追问链路等结构化信息
        return null;
    }

    /**
     * 调用LLM生成压缩
     *
     * @param conversationHistory 对话历史
     * @param previousCompress    上一次压缩内容
     * @return 压缩
     */
    private String callLlmForCompress(String conversationHistory, String previousCompress) {
        String prompt;
        if (StringUtils.isNotBlank(previousCompress)) {
            prompt = COMPRESS_SYSTEM_PROMPT
                    .replace("{conversation_history}", conversationHistory)
                    .replace("{previous_compress}", previousCompress);
        } else {
            prompt = COMPRESS_SYSTEM_PROMPT
                    .replace("{conversation_history}", conversationHistory)
                    .replace("{previous_compress}", ""); // 首次压缩时为空
        }
        String compressResult = chatModel.chat(List.of(new UserMessage(prompt)))
                .aiMessage()
                .text()
                .trim();
        if (StringUtils.isBlank(compressResult)) {
            throw new ClientException(SESSION_COMPRESS_FAILED);
        }
        return compressResult;
    }

    /**
     * 调用 LLM 生成面试对话压缩
     * TODO: 实现面试对话的压缩生成逻辑
     *
     * @param conversationHistory 格式化的对话历史
     * @return 压缩内容
     */
    private String callLlmForInterviewCompress(String conversationHistory, String previousCompress) {
        // TODO: 面试对话的摘要生成
        // 预期行为：调用专用的面试摘要 prompt，生成包含
        // "面试领域分布、候选人表现画像、待复盘考点" 等结构化摘要
        return null;
    }
}
