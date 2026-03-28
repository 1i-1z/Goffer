package com.mi.goffer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mi.goffer.common.convention.exception.ClientException;
import com.mi.goffer.common.enums.MessageRoleEnum;
import com.mi.goffer.dao.entity.MessagesDO;
import com.mi.goffer.dao.entity.SessionsDO;
import com.mi.goffer.dao.mapper.MessagesMapper;
import com.mi.goffer.dao.mapper.SessionsMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

import static com.mi.goffer.common.constant.ChatConstant.SUMMARY_TRIGGER_TURNS;
import static com.mi.goffer.common.convention.errorcode.BaseErrorCode.SESSION_SUMMARY_FAILED;
import static com.mi.goffer.common.prompt.ChatPrompt.SUMMARIZE_SYSTEM_PROMPT;

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
                .orderByAsc(MessagesDO::getSessionId);
        return messagesMapper.selectList(queryWrapper);
    }

    /**
     * 判断是否需要生成摘要
     *
     * @param sessionId 会话id
     * @return 判断是否需要生成摘要
     */
    public boolean needSummarize(Long sessionId) {
        SessionsDO sessionsDO = sessionsMapper.selectById(sessionId);
        // 会话不存在
        if (sessionsDO == null) return false;

        List<MessagesDO> uncompressedMessages = getUncompressedMessages(sessionId, sessionsDO);
        // 如果此时用户输入的消息数量达到阈值，则需要生成摘要
        long count = uncompressedMessages.stream()
                .filter(message -> message.getRole().equals(MessageRoleEnum.USER.getCode()))
                .count();
        return count >= SUMMARY_TRIGGER_TURNS;
    }

    /**
     * 获取未压缩的消息列表
     *
     * @param sessionId 会话id
     * @return 未压缩的消息列表
     */
    public List<MessagesDO> getUncompressedMessages(Long sessionId, SessionsDO sessionsDO) {
        List<Long> lastCompressedMessageId = sessionsDO.getLastCompressedMessageId();
        // 如果没有压缩消息，则返回所有消息
        if (CollectionUtils.isEmpty(lastCompressedMessageId)) {
            return loadHistory(sessionId);
        }
        // 获取最大的压缩消息id
        long maxCompressedMessageId = lastCompressedMessageId.getLast();
        // 获取未压缩的消息列表
        return messagesMapper.selectList(
                Wrappers.lambdaQuery(MessagesDO.class)
                        .eq(MessagesDO::getSessionId, sessionId)
                        .gt(MessagesDO::getMessageId, maxCompressedMessageId)
                        .orderByAsc(MessagesDO::getSessionId)
        );
    }

    /**
     * 压缩会话历史
     *
     * @param sessionId 会话id
     */
    public void compress(Long sessionId) {
        SessionsDO sessionsDO = sessionsMapper.selectById(sessionId);
        List<MessagesDO> uncompressedMessages = getUncompressedMessages(sessionId, sessionsDO);
        if (CollectionUtils.isEmpty(uncompressedMessages)) return;

        String summary;
        // 根据会话模式选择对应的摘要生成方式
        if (sessionsDO.getMode() == 0) {
            // 普通会话
            summary = callLlmForSummary(formatChatMessagesForSummary(uncompressedMessages));
        } else {
            // 面试会话
            summary = callLlmForInterviewSummary(formatInterviewMessagesForSummary(uncompressedMessages));
        }
        // 将摘要持久化进数据库
        LambdaUpdateWrapper<SessionsDO> updateWrapper = Wrappers.lambdaUpdate(SessionsDO.class)
                .eq(SessionsDO::getSessionId, sessionId)
                .set(SessionsDO::getLastSummary, summary)
                .set(SessionsDO::getLastCompressedMessageId, uncompressedMessages.stream()
                        .map(MessagesDO::getMessageId)
                        .toList());
        sessionsMapper.update(null, updateWrapper);
    }

    /**
     * 将普通对话消息列表格式化为摘要提示词
     *
     * @param messages 消息列表
     * @return 格式化的对话历史
     */
    private String formatChatMessagesForSummary(List<MessagesDO> messages) {
        StringBuilder sb = new StringBuilder();
        for (MessagesDO message : messages) {
            sb.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 将面试对话消息列表格式化为摘要提示词
     * TODO: 实现面试对话的格式化逻辑
     *
     * @param messages 消息列表
     * @return 格式化的对话历史
     */
    private String formatInterviewMessagesForSummary(List<MessagesDO> messages) {
        // TODO: 面试对话的摘要格式化逻辑
        // 预期行为：根据面试阶段（技术基础/项目/场景题）分组格式化，
        // 保留面试官的问题模式、候选人的回答要点、追问链路等结构化信息
        return null;
    }

    /**
     * 调用LLM生成摘要
     *
     * @param conversationHistory 对话历史
     * @return 摘要
     */
    private String callLlmForSummary(String conversationHistory) {
        String prompt = SUMMARIZE_SYSTEM_PROMPT.replace("{conversation_history}", conversationHistory);
        String summary = chatModel.chat(prompt);
        if (StringUtils.isBlank(summary)) {
            throw new ClientException(SESSION_SUMMARY_FAILED);
        }
        return summary;
    }

    /**
     * 调用 LLM 生成面试对话摘要
     * TODO: 实现面试对话的摘要生成逻辑
     *
     * @param conversationHistory 格式化的对话历史
     * @return 摘要内容
     */
    private String callLlmForInterviewSummary(String conversationHistory) {
        // TODO: 面试对话的摘要生成
        // 预期行为：调用专用的面试摘要 prompt，生成包含
        // "面试领域分布、候选人表现画像、待复盘考点" 等结构化摘要
        return null;
    }
}
