package com.mi.goffer.service;

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
}
