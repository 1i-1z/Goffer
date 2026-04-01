package com.mi.goffer.common.constant;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/1 11:09
 * @Description: Milvus 常量类
 */
public class MilvusConstant {

    /**
     * 索引集合
     */
    public static final String COLLECTION_NAME = "interview_knowledge";

    /**
     * 向量维度
     */
    public static final int VECTOR_DIMENSION = 1024;

    /**
     * 索引字段
     */
    public static final String FIELD_KNOWLEDGE_ID = "primary_key";

    /**
     * 向量字段
     */
    public static final String FIELD_VECTOR = "vector";

    /**
     * 所属分类：如 Java基础、Java并发、JVM、MySQL 等
     */
    public static final String FIELD_CATEGORY = "category";

    /**
     * 子分类长度
     */
    public static final int MAX_SUB_CATEGORY_LENGTH = 128;

    /**
     * 子分类字段
     */
    public static final String FIELD_SUB_CATEGORY = "sub_category";
}
