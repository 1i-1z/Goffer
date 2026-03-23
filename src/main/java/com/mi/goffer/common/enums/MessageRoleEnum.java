package com.mi.goffer.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 16:31
 * @Description: 消息角色枚举类
 */
@Getter
@AllArgsConstructor
public enum MessageRoleEnum {

    SYSTEM("system", "系统消息"),
    USER("user", "用户"),
    ASSISTANT("assistant", "AI 助手"),
    INTERVIEWER("interviewer", "面试官"),
    CANDIDATE("candidate", "候选人");

    private final String code;
    private final String desc;
}
