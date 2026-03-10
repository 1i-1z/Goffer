package com.mi.goffer.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.util.Date;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/10 21:55
 * @Description: 封装数据库表messages的实体类
 */
@Data
public class MessagesDO{
    /**
     * 消息id
     */
    private String messagesId;

    /**
     * 所属会话id
     */
    private String sessionsId;

    /**
     * 角色：user、assistant
     */
    private String role;

    /**
     * 消息正文
     */
    private String content;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

}