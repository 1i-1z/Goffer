package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/11 18:04
 * @Description: 语音转文字返回参数
 */
@Data
@Builder
public class AsrRespDTO {

    /**
     * 文本内容
     */
    private String text;
}
