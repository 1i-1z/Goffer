package com.mi.goffer.service;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/1 14:52
 * @Description: 面试知识点导入（只在第一次初始化数据库使用，后续不再使用）
 */
@Deprecated
public interface InterviewKnowledgeImporter {

    /**
     * 从 Markdown 文件夹导入所有知识到 MySQL + Milvus
     */
    void importAll();
}
