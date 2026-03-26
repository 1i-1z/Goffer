package com.mi.goffer.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 16:03
 * @Description: 聊天请求参数
 */
@Data
public class ChatReqDTO {

    /**
     * 会话id
     */
    @NotBlank(message = "会话id不可为空")
    private Long sessionId;

    /**
     * 消息内容
     */
    @NotBlank(message = "消息内容不可为空")
    private String message;
}
