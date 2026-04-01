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
 * @Description: 会话实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("sessions")
public class SessionsDO {

    /**
     * 会话id（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long sessionId;

    /**
     * 所属用户id
     */
    private String userId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 模式：0（普通对话）、1（后端面试）、2（前端面试）
     */
    private Integer mode;

    /**
     * 是否软删除：1（是）、0（否）
     */
    private Integer isDeleted;

    /**
     * 状态：会话状态：-1（普通会话）、0（面试进行中）、1（面试已结束）、2（面试复盘完成）
     */
    private Integer status;

    /**
     * 该会话最新的压缩摘要内容
     */
    private String lastCompress;

    /**
     * 参与本次压缩的消息id
     */
    private Long lastCompressedMessageId;

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