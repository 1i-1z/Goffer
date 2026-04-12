package com.mi.goffer.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/11 19:42
 * @Description: 语音配置类
 */
@Getter
@Setter
@Component
public class VoiceConfig {

    /**
     * ASR 接口 API Key
     */
    @Value("${voice.asr.api-key}")
    private String asrApiKey;

    /**
     * ASR 模型名称
     */
    @Value("${voice.asr.model-name}")
    private String asrModelName;

    /**
     * ASR 服务地址
     */
    @Value("${voice.asr.base-url}")
    private String asrBaseUrl;

    /**
     * 单次录音最大时长（秒），超过此值不做处理（用于兜底，无实际作用）
     */
    @Value("${voice.asr.max-duration-seconds}")
    private Integer asrMaxDurationSeconds;

    /**
     * TTS 接口 API Key
     */
    @Value("${voice.tts.api-key}")
    private String ttsApiKey;

    /**
     * TTS 模型名称
     */
    @Value("${voice.tts.model-name}")
    private String ttsModelName;

    /**
     * TTS 服务地址
     */
    @Value("${voice.tts.base-url}")
    private String ttsBaseUrl;

    /**
     * TTS 音色（模型:音色名）
     */
    @Value("${voice.tts.voice}")
    private String ttsVoice;

    /**
     * TTS 采样率（Hz）
     */
    @Value("${voice.tts.sample-rate}")
    private Integer ttsSampleRate;

    /**
     * TTS 音频格式
     */
    @Value("${voice.tts.format}")
    private String ttsFormat;
}
