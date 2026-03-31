package com.mi.goffer.common.constant;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 16:11
 * @Description: 大模型配置常量类
 */
public class ChatConstant {

    /**
     * 超过多少轮触发总结
     */
    public static final int COMPRESS_TRIGGER_TURNS = 8;

    /**
     * 摘要头部
     */
    public static final String COMPRESS_HEADER = """
            【上下文摘要】
            """;

    /**
     * 默认标题
     */
    public static final String DEFAULT_TITLE = "新会话";
}
