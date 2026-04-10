package com.mi.goffer.service;

import com.mi.goffer.dto.resp.AbilityGrowthCurveRespDTO;
import com.mi.goffer.dto.resp.CategoryScoreRespDTO;

import java.util.List;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/8 17:03
 * @Description: 评分服务层接口
 */
public interface ScoreService {

    /**
     * 获取用户总分
     *
     * @param userId 用户id
     * @return Integer 总分
     */
    Integer getTotalScore(String userId);

    /**
     * 获取用户分类分数
     *
     * @param userId 用户id
     * @return List<CategoryScoreRespDTO> 分类分数列表
     */
    List<CategoryScoreRespDTO> getCategoryScore(String userId);

    /**
     * 获取用户能力成长曲线参数（最后八次）
     *
     * @param userId 用户id
     * @return List<AbilityGrowthCurveRespDTO> 能力成长曲线参数列表
     */
    List<AbilityGrowthCurveRespDTO> getAbilityGrowthCurve(String userId);
}
