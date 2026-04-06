package com.mi.goffer.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/10 21:55
 * @Description: 分数实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName(value = "scores", autoResultMap = true)
public class ScoresDO {
    /**
     * 分数id
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String scoreId;

    /**
     * 所属用户id
     */
    private String userId;

    /**
     * 所属消息id
     */
    private Long messageId;

    /**
     * 总分
     */
    private Integer totalScore;

    /**
     * 分数
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> score;

    /**
     * 总体评价
     */
    private String evaluation;

    /**
     * 技术优势
     */
    private String advantages;

    /**
     * 技术短板
     */
    private String disadvantages;

    /**
     * 学习建议
     */
    private String suggestion;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

}