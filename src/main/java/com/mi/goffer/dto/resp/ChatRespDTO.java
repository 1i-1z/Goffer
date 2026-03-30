package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/30 11:47
 * @Description: 聊天响应参数
 */
@Data
@Builder
public class ChatRespDTO {

    /**
     * 会话id
     */
    private Long sessionId;

    /**
     * 流式内容片段
     */
    private String content;
}
