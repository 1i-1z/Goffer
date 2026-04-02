package com.mi.goffer.dto.req;

import lombok.Data;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/2 14:51
 * @Description: 聊天历史查询请求参数
 */
@Data
public class QueryChatHistoryReqDTO {

    /**
     * 关键字
     */
    String keyword;
}
