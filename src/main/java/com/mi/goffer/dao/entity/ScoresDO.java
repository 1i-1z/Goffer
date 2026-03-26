package com.mi.goffer.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/10 21:55
 * @Description: 分数实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("scores")
public class ScoresDO {
    /**
     * 分数id
     */
    private String scoreId;

    /**
     * 所属用户id
     */
    private String userId;

    /**
     * 所属消息id
     */
    private String messageId;

    /**
     * 分数
     */
    private String score;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

}