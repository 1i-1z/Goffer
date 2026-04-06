package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/5 11:18
 * @Description: 面试响应参数
 */
@Data
@Builder
public class InterviewRespDTO {

    /**
     * 会话id
     */
    private Long sessionId;

    /**
     * 流式内容片段
     */
    private String content;
}
