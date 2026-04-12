package com.mi.goffer.dto.req;

import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/11 21:14
 * @Description: 语音面试参数
 */
@Data
public class VoiceInterviewRepDTO {

    /**
     * 会话id
     */
    private Long sessionId;

    /**
     * 会话模式（1：后端面试、2：前端面试）
     */
    private Integer mode;
}
