package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/2 10:48
 * @Description: 会话标题返回参数
 */
@Data
@Builder
public class TitleRespDTO {

    /**
     * 会话标题
     */
    private String title;
}
