package com.mi.goffer.service;

import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/11 19:37
 * @Description: 语音接口层
 */
public interface VoiceService {

    /**
     * ASR: 语音转文本
     *
     * @param audioFile 音频文件
     * @return 识别后的文本
     */
    String speechToText(MultipartFile audioFile);

    /**
     * TTS: 文本转语音（流式）
     *
     * @param text 待转换文本
     * @return PCM 音频数据流
     */
    Flux<byte[]> textToSpeechStream(String text);

    /**
     * TTS: 文本转语音（同步）
     *
     * @param text 待转换文本
     * @return 完整音频数据
     */
    byte[] textToSpeech(String text);

    /**
     * 生成静音音频数据
     *
     * @return 静音PCM数据
     */
    byte[] generateSilence();
}
