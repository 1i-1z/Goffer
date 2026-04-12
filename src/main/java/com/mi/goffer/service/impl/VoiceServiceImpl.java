package com.mi.goffer.service.impl;

import com.mi.goffer.common.config.VoiceConfig;
import com.mi.goffer.common.constant.VoiceConstant;
import com.mi.goffer.dto.resp.AsrRespDTO;
import com.mi.goffer.service.VoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/11 19:37
 * @Description: 语音接口实现层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceServiceImpl implements VoiceService {

    private final VoiceConfig voiceConfig;
    private final RestTemplate restTemplate;
    private static final String BOUNDARY = "----GofferBoundary" + System.currentTimeMillis();

    /**
     * ASR: 语音转文本
     *
     * @param audioFile 音频文件
     * @return 识别后的文本
     */
    @Override
    public String speechToText(MultipartFile audioFile) {
        String url = voiceConfig.getAsrBaseUrl() + VoiceConstant.ASR_ENDPOINT;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("multipart/form-data; boundary=" + BOUNDARY));
        headers.set("Authorization", "Bearer " + voiceConfig.getAsrApiKey());

        try {
            byte[] audioBytes = audioFile.getBytes();
            String filename = audioFile.getOriginalFilename() != null ? audioFile.getOriginalFilename() : "audio.mp3";

            // 手动构建 multipart/form-data 请求体
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // 文件部分
            baos.write(("--" + BOUNDARY + "\r\n").getBytes());
            baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes());
            baos.write("Content-Type: audio/mpeg\r\n\r\n".getBytes());
            baos.write(audioBytes);
            baos.write("\r\n".getBytes());

            // model 部分
            baos.write(("--" + BOUNDARY + "\r\n").getBytes());
            baos.write("Content-Disposition: form-data; name=\"model\"\r\n\r\n".getBytes());
            baos.write(voiceConfig.getAsrModelName().getBytes());
            baos.write("\r\n".getBytes());

            // 结束边界
            baos.write(("--" + BOUNDARY + "--\r\n").getBytes());

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(baos.toByteArray(), headers);

            ResponseEntity<AsrRespDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    AsrRespDTO.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().getText();
            }
        } catch (IOException e) {
            log.error("读取音频文件失败", e);
        } catch (Exception e) {
            log.error("ASR调用失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * TTS: 文本转语音（流式）
     *
     * @param text 待转换文本
     * @return MP3 音频数据流
     */
    @Override
    public Flux<byte[]> textToSpeechStream(String text) {
        String url = voiceConfig.getTtsBaseUrl() + VoiceConstant.TTS_ENDPOINT;

        String requestBody = String.format("""
                {
                    "model": "%s",
                    "input": "%s",
                    "voice": "%s",
                    "response_format": "%s",
                    "sample_rate": %d,
                    "stream": true
                }
                """,
                voiceConfig.getTtsModelName(),
                escapeJson(text),
                voiceConfig.getTtsVoice(),
                voiceConfig.getTtsFormat(),
                voiceConfig.getTtsSampleRate()
        );

        return WebClient.builder()
                .defaultHeader("Authorization", "Bearer " + voiceConfig.getTtsApiKey())
                .build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(byte[].class);
    }

    /**
     * JSON 转义
     */
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * TTS: 文本转语音（同步）
     *
     * @param text 待转换文本
     * @return 完整音频数据
     */
    @Override
    public byte[] textToSpeech(String text) {
        return textToSpeechStream(text)
                .reduce(new byte[0], (a, b) -> {
                    byte[] result = new byte[a.length + b.length];
                    System.arraycopy(a, 0, result, 0, a.length);
                    System.arraycopy(b, 0, result, a.length, b.length);
                    return result;
                })
                .block(Duration.ofSeconds(30));
    }

    /**
     * 生成静音音频数据
     *
     * @return 静音 MP3 数据
     */
    @Override
    public byte[] generateSilence() {
        return new byte[]{0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    }
}
