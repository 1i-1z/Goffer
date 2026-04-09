package com.mi.goffer.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mi.goffer.dao.entity.ScoresDO;
import com.mi.goffer.dao.mapper.ScoresMapper;
import com.mi.goffer.dto.resp.CategoryScoreRespDTO;
import com.mi.goffer.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * 获取用户分类分数
     *
     * @param userId 用户id
     * @return List<CategoryScoreRespDTO> 分类分数列表
     */
    @Override
    public List<CategoryScoreRespDTO> getCategoryScore(String userId) {
        List<ScoresDO> scores = scoresMapper.selectList(Wrappers.lambdaQuery(ScoresDO.class)
                .eq(ScoresDO::getUserId, userId));
        return scores.stream()
                .flatMap(score -> score.getScore().entrySet().stream())
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.averagingInt(Map.Entry::getValue))
                )
                .entrySet().stream()
                .map(entry -> CategoryScoreRespDTO.builder()
                        .category(entry.getKey())
                        .score(entry.getValue().intValue())
                        .build())
                .toList();
    }
}
