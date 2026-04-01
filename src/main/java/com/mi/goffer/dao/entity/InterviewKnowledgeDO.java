package com.mi.goffer.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/1 10:00
 * @Description: 面试知识点实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("interview_knowledge")
public class InterviewKnowledgeDO {

    /**
     * 知识点id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long knowledgeId;

    /**
     * 所属分类：如 Java 基础、Java 并发、JVM、MySQL 等
     */
    private String category;

    /**
     * 子分类
     */
    private String subCategory;

    /**
     * 知识点题目
     */
    private String chunkTitle;

    /**
     * 知识点内容
     */
    private String chunkContent;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
