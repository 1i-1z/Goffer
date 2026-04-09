package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/8 17:33
 * @Description: 面试标题返回参数
 */
@Data
@Builder
public class InterviewTitleRespDTO {

    /**
     * 会话id
     */
    private Long sessionId;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 创建时间
     */
    private Date createTime;
}
