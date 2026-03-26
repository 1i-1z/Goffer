package com.mi.goffer.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mi.goffer.common.prompt.ChatPrompt;
import com.mi.goffer.dao.entity.MessagesDO;
import com.mi.goffer.dao.entity.SessionsDO;
import com.mi.goffer.dao.mapper.MessagesMapper;
import com.mi.goffer.dao.mapper.SessionsMapper;
import com.mi.goffer.service.AssistantService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

import static com.mi.goffer.common.constant.ChatConstant.SUMMARY_HEADER;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 17:21
 * @Description: 大模型服务层实现类
 */
@Service
@RequiredArgsConstructor
public class AssistantServiceImpl implements AssistantService {

    private final MessagesMapper messagesMapper;
    private final SessionsMapper sessionsMapper;
    private final StreamingChatModel streamingChatModel;
    private final ConversationContextManager conversationContextManager;

    /**
     * 普通对话
     *
     * @param userId    用户id
     * @param sessionId 会话id
     * @param message    用户输入
     * @return Flux<String> 流式响应
     */
    @Override
    public Flux<String> chat(String userId, Long sessionId, String message) {
        return null;
    }

    /**
     * 面试对话
     *
     * @param userId    用户id
     * @param sessionId 会话id
     * @param message    用户输入
     * @return Flux<String> 流式响应
     */
    @Override
    public Flux<String> interview(String userId, String sessionId, String message) {
        return null;
    }

    /**
     * 构建系统提示词（包含摘要上下文）
     *
     * @param sessionsDO 会话信息
     * @return 完整的系统提示词
     */
    private String buildSystemPrompt(SessionsDO sessionsDO) {
        Integer mode = sessionsDO.getMode();
        // 根据会话模式选择对应的系统提示词
        String systemPrompt = ChatPrompt.getSystemPrompt(mode);
        if (StringUtils.isNotBlank(sessionsDO.getLastSummary())) {
            systemPrompt += SUMMARY_HEADER + sessionsDO.getLastSummary();
        }
        return systemPrompt;
    }

    /**
     * 截断会话，只保留最近N轮对话
     * 10 轮 = 20 条消息（10 user + 10 assistant）
     *
     * @return 截断后的消息列表
     */
    private List<MessagesDO> truncateToMaxTurns(List<MessagesDO> messages, int maxTurns) {
        // 如果消息列表为空或者长度小于等于最大轮数，则返回原列表
        if (messages == null || messages.size() <= maxTurns * 2) {
            return messages;
        }
        // 截断消息列表
        return messages.subList(messages.size() - maxTurns * 2, messages.size());
    }

    /**
     * 将 MessagesDO 转换为 ChatMessage
     * @param messagesDO   消息对象
     * @return ChatMessage 聊天消息对象
     */
    private ChatMessage toChatMessage(MessagesDO messagesDO) {
        return switch (messagesDO.getRole()) {
            case "user" -> UserMessage.from(messagesDO.getContent());
            case "assistant" -> AiMessage.from(messagesDO.getContent());
            default -> UserMessage.from(messagesDO.getContent()); // fallback
        };
    }
}
