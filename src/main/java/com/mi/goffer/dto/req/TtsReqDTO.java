package com.mi.goffer.dto.req;

import lombok.Builder;
import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/11 18:03
 * @Description: 语音转文字请求参数
 */
@Data
@Builder
public class TtsReqDTO {

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 输入内容
     */
    private String input;

    /**
     * 音色
     */
    private String voice;

    /**
     * 音频格式
     */
    private String format;

    /**
     * 采样率
     */
    private Integer sampleRate;

    /**
     * 是否流式返回
     */
    private Boolean stream;
}
