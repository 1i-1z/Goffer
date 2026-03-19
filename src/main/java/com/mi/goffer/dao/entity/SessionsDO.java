package com.mi.goffer.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/10 21:55
 * @Description: 会话实体类
 */
@Data
@TableName("sessions")
public class SessionsDO{

    /**
     * 会话id
     */
    private String sessionsId;

    /**
     * 所属用户id
     */
    private String usersId;

    /**
     * 会话标题
     */
    private String tittle;

    /**
     * 模式：1（面试）、0（普通对话）
     */
    private Integer mode;

    /**
     * 是否软删除：1（是）、0（否）
     */
    private String isDeleted;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}