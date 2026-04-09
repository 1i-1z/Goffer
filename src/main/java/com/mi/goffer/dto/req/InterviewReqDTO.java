package com.mi.goffer.dto.req;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

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
     * 消息内容（文本模式使用）
     */
    private String message;

    /**
     * 音频文件（语音模式使用）
     */
    private MultipartFile audioFile;

    /**
     * 交互模式：text-纯文本, voice-语音交互
     */
    private String interactionMode = "text";
}
