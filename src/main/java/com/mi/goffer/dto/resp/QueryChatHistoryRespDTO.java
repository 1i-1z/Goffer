package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/2 14:43
 * @Description: 聊天历史查询响应参数
 */
@Data
@Builder
public class QueryChatHistoryRespDTO {

    /**
     * 会话id
     */
    private Long sessionId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 创建时间
     */
    private Date createTime;
}
