package com.mi.goffer.dto.req;

import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/2 17:25
 * @Description: 面试请求参数
 */
@Data
public class InterviewReqDTO {

    /**
     * 会话id
     */
    private Long sessionId;

    /**
     * 会话模式（1：后端面试、2：前端面试）
     */
    private Integer mode;

    /**
     * 消息内容
     */
    private String message;
}
