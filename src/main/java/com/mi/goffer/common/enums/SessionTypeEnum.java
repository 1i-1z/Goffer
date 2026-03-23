package com.mi.goffer.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 16:20
 * @Description: 会话类型枚举类
 */
@Getter
@AllArgsConstructor
public enum SessionTypeEnum {

    CHAT("CHAT", "普通聊天"),
    INTERVIEW("INTERVIEW", "模拟面试"),
    RESUME_REVIEW("RESUME_REVIEW", "简历点评");

    private final String code;
    private final String desc;
}
