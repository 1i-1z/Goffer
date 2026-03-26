package com.mi.goffer.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/10 21:55
 * @Description: 消息实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("messages")
public class MessagesDO {
    /**
     * 消息id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long messageId;

    /**
     * 所属会话id
     */
    private Long sessionId;

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