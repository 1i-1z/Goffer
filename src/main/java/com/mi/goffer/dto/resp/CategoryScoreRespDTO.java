package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/10 01:28
 * @Description: 分类分数返回参数
 */
@Data
@Builder
public class CategoryScoreRespDTO {

    /**
     * 分类名称
     */
    private String category;

    /**
     * 分类分数
     */
    private Integer score;
}
