package com.mi.goffer.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mi.goffer.common.convention.exception.ClientException;
import com.mi.goffer.common.enums.MessageRoleEnum;
import com.mi.goffer.common.prompt.ChatPrompt;
import com.mi.goffer.dao.entity.MessagesDO;
import com.mi.goffer.dao.entity.SessionsDO;
import com.mi.goffer.dao.mapper.MessagesMapper;
import com.mi.goffer.dao.mapper.SessionsMapper;
import com.mi.goffer.dto.req.ChatReqDTO;
import com.mi.goffer.dto.resp.ChatRespDTO;
import com.mi.goffer.service.AssistantService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.mi.goffer.common.constant.ChatConstant.*;
import static com.mi.goffer.common.convention.errorcode.BaseErrorCode.SESSION_NOT_FOUND;

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
    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final ConversationContextManager conversationContextManager;

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
                    .status(-1)
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
        if (conversationContextManager.needCompress(uncompressedMessages)) {
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
     * @return Flux<String> 流式响应
     */
    @Override
    public Flux<String> interview(String userId, ChatReqDTO reqDTO) {
        return null;
    }

    /**
     * 异步生成会话标题
     *
     * @param sessionId   会话id
     * @param userMessage 用户消息
     */
    public void generateTitleAsync(Long sessionId, String userMessage) {
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
     * 构建系统提示词（包含摘要上下文）
     *
     * @param sessionsDO 会话信息
     * @return 完整的系统提示词
     */
    private String buildSystemPrompt(SessionsDO sessionsDO) {
        Integer mode = sessionsDO.getMode();
        // 根据会话模式选择对应的系统提示词
        String systemPrompt = ChatPrompt.getSystemPrompt(mode);
        if (StringUtils.isNotBlank(sessionsDO.getLastCompress())) {
            systemPrompt += COMPRESS_HEADER + sessionsDO.getLastCompress();
        }
        return systemPrompt;
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
}
