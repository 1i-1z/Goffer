package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/11 14:11
 * @Description: 面试消息返回参数
 */
@Data
@Builder
public class InterviewMessageRespDTO {

    /**
     * 角色
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 创建时间
     */
    private Date createTime;
}
