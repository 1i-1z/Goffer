package com.mi.goffer.dto.resp;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/10 01:03
 * @Description: 总分返回参数
 */
@Data
@RequiredArgsConstructor
public class TotalScoreRespDTO {

    /**
     * 总分
     */
    private Integer totalScore;
}
