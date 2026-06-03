package com.mi.goffer.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/6/3 17:45
 * @Description: 生理监测实体类（心率、血氧）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("vital_signs")
public class VitalSignsDO {

    /**
     * 记录ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long vitalSignsId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 会话ID（可为NULL，日常监测时无session）
     */
    private Long sessionId;

    /**
     * 心率（单位：bpm）
     */
    private Integer heartRate;

    /**
     * 血氧饱和度（单位：%）
     */
    private Integer bloodOxygen;

    /**
     * 硬件端采集时间
     */
    private Date measurementTime;

    /**
     * 设备ID（MAC地址）
     */
    private String deviceId;

    /**
     * 入库时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
