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


    /**
     * Base64 编码的音频数据（仅语音模式最终响应返回）
     */
    private String audioData;

    /**
     * 是否为最终完整响应
     * true: 流式结束，语音模式下此时 audioData 有值
     * false: 流式中间片段
     */
    private Boolean isFinal = false;
}
