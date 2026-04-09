package com.mi.goffer.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mi.goffer.dao.entity.ScoresDO;
import com.mi.goffer.dao.mapper.ScoresMapper;
import com.mi.goffer.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/8 17:03
 * @Description: 评分服务层接口实现类
 */
@Service
@RequiredArgsConstructor
public class ScoreServiceImpl implements ScoreService {

    private final ScoresMapper scoresMapper;

    /**
     * 获取用户总分
     *
     * @param userId 用户id
     * @return Integer 总分（向下取整）
     */
    @Override
    public Integer getTotalScore(String userId) {
        return (int) scoresMapper.selectList(Wrappers.lambdaQuery(ScoresDO.class)
                        .eq(ScoresDO::getUserId, userId))
                .stream()
                .mapToInt(ScoresDO::getTotalScore)
                .average()
                .orElse(0.0);
    }
}
