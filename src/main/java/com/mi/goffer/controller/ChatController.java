package com.mi.goffer.controller;

import com.mi.goffer.common.context.UserContext;
import com.mi.goffer.common.convention.result.Result;
import com.mi.goffer.common.convention.result.Results;
import com.mi.goffer.dto.req.ChatReqDTO;
import com.mi.goffer.dto.resp.ChatRespDTO;
import com.mi.goffer.dto.resp.TitleRespDTO;
import com.mi.goffer.service.AssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 15:34
 * @Description: 大模型对话控制层
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/goffer")
public class ChatController {

    private final AssistantService assistantService;

    /**
     * 获取所有会话标题
     *
     * @return List<TitleRespDTO> 会话标题列表
     */
    @GetMapping("/get-title")
    public Result<List<TitleRespDTO>> getTitle() {
        return Results.success(assistantService.getAllChatTitle(UserContext.getCurrentUserId()));
    }

    /**
     * 普通对话
     *
     * @param reqDTO 请求参数
     * @return Flux<String> 流式响应
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatRespDTO> chat(@RequestBody @Validated ChatReqDTO reqDTO) {
        return assistantService.chat(UserContext.getCurrentUserId(), reqDTO);
    }
}
