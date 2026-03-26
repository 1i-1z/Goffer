package com.mi.goffer.common.constant;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 16:11
 * @Description: 大模型配置常量类
 */
public class ChatConstant {

    /**
     * 最大保留对话轮次（不含摘要）
     */
    public static final int MAX_TURNS = 10;

    /**
     * 超过多少轮触发总结
     */
    public static final int SUMMARY_TRIGGER_TURNS = 8;

    /**
     * 摘要头部
     */
    public static final String SUMMARY_HEADER = """
            【上下文摘要】
            """;
}
