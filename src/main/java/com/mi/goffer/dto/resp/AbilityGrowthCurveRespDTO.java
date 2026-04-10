package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/10 08:45
 * @Description: 能力成长曲线参数返回参数
 */
@Data
@Builder
public class AbilityGrowthCurveRespDTO {

    /**
     * 分类名称
     */
    private String category;

    /**
     * 分类分数
     */
    private Integer score;

    /**
     * 创建时间
     */
    private Date crateTime;
}
