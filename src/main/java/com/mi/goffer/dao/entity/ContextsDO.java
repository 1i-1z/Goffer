package com.mi.goffer.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.util.Date;

/**
 * @Author: 1i-1z
 * @Date: 2026/3/10 21:44
 * @Description: 封装数据库表contexts的实体类
 */
@Data
public class ContextsDO {
    /**
     * 上下文id
     */
    private String contextsId;

    /**
     * 所属会话id
     */
    private String sessionsId;

    /**
     * 上下文总结
     */
    private String summary;

    /**
     * 未总结消息的id集合
     */
    private String activeMessages;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}