package com.mi.goffer.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/10 17:11
 * @Description: 面试历史信息返回参数
 */
@Data
@Builder
public class InterviewHistoryInfoRespDTO {

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

    /**
     * 分类分数列表
     */
    private List<CategoryScoreRespDTO> categoryScoreRespDTOList;
}
