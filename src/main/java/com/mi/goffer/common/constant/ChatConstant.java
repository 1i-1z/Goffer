package com.mi.goffer.common.constant;

import java.util.List;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/3/23 16:11
 * @Description: 大模型配置常量类
 */
public class ChatConstant {

    /**
     * 普通对话压缩触发阈值
     */
    public static final int CHAT_COMPRESS_TRIGGER_TURNS = 8;

    /**
     * 面试对话压缩触发阈值
     */
    public static final int INTERVIEW_COMPRESS_TRIGGER_TURNS = 12;

    /**
     * 摘要头部
     */
    public static final String COMPRESS_HEADER = """
            【上下文摘要】
            """;

    /**
     * 技术背景参考
     */
    public static final String TECH_REFERENCE = """
                【技术背景参考】
            """;

    /**
     * 暂无相关参考知识
     */
    public static final String NO_KNOWLEDGE_REFERENCE = """
                        （暂无相关参考知识）
            """;

    /**
     * 默认标题
     */
    public static final String DEFAULT_TITLE = "新会话";

    /**
     * 前端面试
     */
    public static final String FRONT_END_INTERVIEW = "前端面试";

    /**
     * 后端面试
     */
    public static final String BACK_END_INTERVIEW = "后端面试";

    /**
     * 前端面试常用分类（按面试频率排序）
     * AI 优先从以下分类中随机选题，避免从 HTML 、CSS 开始
     */
    public static final List<String> FRONT_END_INTERVIEW_CATEGORIES = List.of(
        "HTML",
        "CSS",
        "JS",
        "Vue"
    );

    /**
     * 后端面试常用分类（按面试频率排序）
     * AI 优先从以下分类中随机选题，避免从 Java 基础开始
     */
    public static final List<String> BACK_END_INTERVIEW_CATEGORIES = List.of(
            "Java 集合",
            "Java 并发",
            "MySQL",
            "Redis",
            "Java 基础",
            "JVM",
            "Spring 全家桶",
            "MyBatis",
            "消息队列 MQ"
    );

}
