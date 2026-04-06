package com.mi.goffer.common.util;

import com.mi.goffer.common.config.MilvusConfig;
import com.mi.goffer.common.constant.MilvusConstant;
import com.mi.goffer.dao.entity.InterviewKnowledgeDO;
import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/1 09:52
 * @Description: Milvus 兼容存储工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusUtil {

    private final MilvusConfig milvusConfig;
    private MilvusClient milvusClient;

    /**
     * 应用启动时连接 Zilliz
     */
    @PostConstruct
    public void init() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withUri(milvusConfig.getUri())
                .withToken(milvusConfig.getToken())
                .build();
        milvusClient = new MilvusServiceClient(connectParam);
        log.info("[Milvus] 连接成功");
    }

    /**
     * 批量插入数据到 Milvus
     *
     * @param list    知识实体列表（与 vectors 一一对应）
     * @param vectors 向量列表
     */
    public void insert(List<InterviewKnowledgeDO> list, List<float[]> vectors) {
        if (list.isEmpty() || vectors.isEmpty() || list.size() != vectors.size()) {
            throw new IllegalArgumentException("list 和 vectors 大小不一致或为空");
        }
        // 将 float[] 转换为 List<Float>（Milvus SDK 要求 Float 而非 float 原始类型）
        List<List<Float>> floatVectors = vectors.stream()
                .map(arr -> {
                    List<Float> boxed = new ArrayList<>(arr.length);
                    for (float v : arr) boxed.add(v);
                    return boxed;
                })
                .toList();
        // 按字段列组织数据，每一列对应一个字段的所有值（索引对齐）
        List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field(MilvusConstant.FIELD_KNOWLEDGE_ID,
                        list.stream().map(InterviewKnowledgeDO::getKnowledgeId).toList()),
                new InsertParam.Field(MilvusConstant.FIELD_CATEGORY,
                        list.stream().map(InterviewKnowledgeDO::getCategory).toList()),
                new InsertParam.Field(MilvusConstant.FIELD_SUB_CATEGORY,
                        list.stream().map(InterviewKnowledgeDO::getSubCategory).toList()),
                // 向量字段：List<List<Float>>，每个 List<Float> 必须为 1024 维
                new InsertParam.Field(MilvusConstant.FIELD_VECTOR, floatVectors)
        );
        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(MilvusConstant.COLLECTION_NAME)
                .withFields(fields)
                .build();
        milvusClient.insert(insertParam);
        log.info("[Milvus] 批量插入 {} 条数据完成", list.size());
    }

    /**
     * 根据向量搜索最相似的 topK 条面试知识
     *
     * @param vector      查询向量（1024维）
     * @param topK        返回条数
     * @param category    可选：限定分类过滤
     * @param subCategory 可选：限定子分类过滤
     * @return 命中的 knowledgeId 列表（按相似度从高到低）
     */
    public List<Long> searchByVector(List<Float> vector, int topK, String category, String subCategory) {
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(MilvusConstant.COLLECTION_NAME) // 指定集合名称
                .withVectorFieldName(MilvusConstant.FIELD_VECTOR) // 指定向量字段名称
                .withVectors(Collections.singletonList(vector)) // 指定查询向量
                .withTopK(topK) // 指定返回条数
                .withMetricType(MetricType.COSINE) // 指定距离度量方式
                .withOutFields(Arrays.asList(
                        MilvusConstant.FIELD_KNOWLEDGE_ID,
                        MilvusConstant.FIELD_CATEGORY,
                        MilvusConstant.FIELD_SUB_CATEGORY))
                .withExpr(buildFilterExpr(category, subCategory) != null 
                ? buildFilterExpr(category, subCategory) 
                : "") // 指定过滤条件
                .build();

        // 执行搜索并用 SearchResultsWrapper 封装结果
        SearchResultsWrapper wrapper = new SearchResultsWrapper(
                milvusClient.search(searchParam).getData().getResults());
        // getIDScore(0)：获取第 0 个目标向量的搜索结果（ID + distance + outputFields）
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        List<Long> knowledgeIds = new ArrayList<>();
        for (SearchResultsWrapper.IDScore score : scores) {
            Long knowledgeId = score.getLongID();
            knowledgeIds.add(knowledgeId);
        }
        return knowledgeIds;
    }

    /**
     * 构建 Milvus 过滤表达式
     *
     * @param category    分类过滤条件（可选）
     * @param subCategory 子分类过滤条件（可选）
     * @return 表达式字符串，为空则返回 null
     */
    private String buildFilterExpr(String category, String subCategory) {
        List<String> conditions = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            conditions.add(MilvusConstant.FIELD_CATEGORY + " == \"" + category + "\"");
        }
        if (subCategory != null && !subCategory.isBlank()) {
            conditions.add(MilvusConstant.FIELD_SUB_CATEGORY + " == \"" + subCategory + "\"");
        }
        // 无任何过滤条件时返回 null，避免空表达式导致搜索报错
        if (conditions.isEmpty()) {
            return null;
        }
        return String.join(" && ", conditions);
    }
}
