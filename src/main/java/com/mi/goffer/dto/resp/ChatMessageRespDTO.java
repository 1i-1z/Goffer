package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/11 14:03
 * @Description: 聊天消息返回参数
 */
@Data
@Builder
public class ChatMessageRespDTO {

    /**
     * 角色
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;
}
