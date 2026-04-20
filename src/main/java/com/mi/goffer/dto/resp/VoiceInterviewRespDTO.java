package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * @Author: 1i-1z
 * @Date: 2026/4/19 13:49
 * @Description: 语音面试响应参数（包含音频和文本）
 */
@Data
@Builder
public class VoiceInterviewRespDTO {

    /**
     * 会话id
     */
    private Long sessionId;

    /**
     * 流式文本内容片段
     */
    private String content;

    /**
     * 音频数据片段（base64编码或二进制）
     */
    private byte[] audioData;
}
