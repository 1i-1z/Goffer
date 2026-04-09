package com.mi.goffer.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Author: 1i-1z
 * @Date: 2026/4/9 16:06
 * @Description: 语音处理服务
 */
@Service
@Slf4j
// TODO
public class VoiceManager {
    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String API_KEY;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    //如果text中有\换行，可能导致json解析错误，所以使用jackson
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ASR_URL = "https://api.siliconflow.cn/v1/audio/transcriptions";
    private static final String TTS_URL = "https://api.siliconflow.cn/v1/audio/speech";


    /**
     * 语音转文字
     *
     * @param audioFile 音频文件
     * @return string 文本
     */
    public String speechToText(MultipartFile audioFile) {
        try {
            if (audioFile == null || audioFile.isEmpty()) {
                throw new IllegalArgumentException("音频文件不能为空");
            }
            //把音频文件转成 RequestBody
            RequestBody fileBody = RequestBody.create(
                    audioFile.getBytes(),
                    MediaType.parse("audio/wav")
            );

            //ASR API 要求：构造 multipart 表单
            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", "FunAudioLLM/SenseVoiceSmall")
                    .addFormDataPart("audioFile",
                            audioFile.getOriginalFilename() != null ? audioFile.getOriginalFilename() : "audio.wav",
                            fileBody)
                    .build();

            //创建HTTP请求
            Request request = new Request.Builder()
                    .url(ASR_URL)
                    .header("Authorization", "Bearer " + API_KEY)
                    .post(requestBody)
                    .build();

            //发送请求
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                throw new RuntimeException("ASR 调用失败: " + response.code());
            }

            String jsonStr = response.body().string();
            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            String resultText = jsonNode.get("text").asText();

            log.info("[ASR] 识别成功: {}", resultText);
            return resultText;
        } catch (Exception e) {
            log.error("[ASR] 语音转文字失败", e);
            throw new RuntimeException("语音识别失败: " + e.getMessage());
        }
    }



    /**
     * 文字转语音
     *
     * @param text 文本
     * @return string 文本
     */
    public String textToSpeech(String text) {
        try {

            Map<String, Object> request = new HashMap<>();
            request.put("model", "FunAudioLLM/CosyVoice2-0.5B");
            request.put("input", text);
            request.put("voice", "FunAudioLLM/CosyVoice2-0.5B:alex");
            request.put("response_format", "mp3");

            String json = objectMapper.writeValueAsString(request);

            RequestBody body = RequestBody.create(
                    json,
                    MediaType.parse("application/json")
            );

            Request requestHttp = new Request.Builder()
                    .url(TTS_URL)
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            Response response = client.newCall(requestHttp).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("TTS 调用失败: " + response.code());
            }

            byte[] audioBytes = response.body().bytes();
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

            log.info("[TTS] 合成成功, 音频大小: {} bytes", audioBytes.length);
            return base64Audio;
        } catch (Exception e) {
            log.error("[TTS] 文字转语音失败", e);
            throw new RuntimeException("语音合成失败: " + e.getMessage());
        }
    }

}
