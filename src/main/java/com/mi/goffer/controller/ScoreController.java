package com.mi.goffer.controller;

import com.mi.goffer.common.context.UserContext;
import com.mi.goffer.common.convention.result.Result;
import com.mi.goffer.common.convention.result.Results;
import com.mi.goffer.dto.resp.CategoryScoreRespDTO;
import com.mi.goffer.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/8 17:02
 * @Description: 评分控制层
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/score")
public class ScoreController {

    private final ScoreService scoreService;

    /**
     * 获取用户总分
     *
     * @return Integer 总分（向下取整）
     */
    @GetMapping("/total")
    public Result<Integer> getTotalScore() {
        return Results.success(scoreService.getTotalScore(UserContext.getCurrentUserId()));
    }

    /**
     * 获取用户分类得分
     *
     * @return List<CategoryScoreRespDTO> 分类得分列表
     */
    @GetMapping("/category")
    public Result<List<CategoryScoreRespDTO>> getCategoryScore() {
        return Results.success(scoreService.getCategoryScore(UserContext.getCurrentUserId()));
    }
}
