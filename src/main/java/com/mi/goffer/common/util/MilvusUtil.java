package com.mi.goffer.common.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mi.goffer.common.config.MilvusConfig;
import com.mi.goffer.common.constant.MilvusConstant;
import com.mi.goffer.dao.entity.InterviewKnowledgeDO;
import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryIteratorParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

    /**
     * 使用 Iterator 导出 Milvus 数据到本地 JSON 文件
     *
     * @param batchSize  每批返回的数据量
     * @param outputPath 输出文件路径（传入 null 则使用默认文件名）
     * @return 导出的记录数量
     */
    public int exportToJsonFile(long batchSize, String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            outputPath = "interview_knowledge_VDB.json";
        }

        // 初始化/清空文件
        try {
            Files.write(Path.of(outputPath), "[]".getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("[Milvus] 初始化导出文件失败: {}", outputPath, e);
            return 0;
        }

        QueryIteratorParam queryIteratorParam = QueryIteratorParam.newBuilder()
                .withCollectionName(MilvusConstant.COLLECTION_NAME)
                .withExpr("")
                .withBatchSize(batchSize)
                .addOutField(MilvusConstant.FIELD_KNOWLEDGE_ID)
                .addOutField(MilvusConstant.FIELD_VECTOR)
                .addOutField(MilvusConstant.FIELD_CATEGORY)
                .addOutField(MilvusConstant.FIELD_SUB_CATEGORY)
                .build();

        R<QueryIterator> queryIteratorRes = milvusClient.queryIterator(queryIteratorParam);
        if (queryIteratorRes.getStatus() != R.Status.Success.getCode()) {
            log.error("Iterator 查询初始化失败: {}", queryIteratorRes.getMessage());
            return 0;
        }

        QueryIterator queryIterator = queryIteratorRes.getData();
        int totalCount = 0;

        try {
            while (true) {
                List<QueryResultsWrapper.RowRecord> batchResults = queryIterator.next();
                if (batchResults.isEmpty()) {
                    break;
                }

                // 读取现有数据
                String jsonString;
                List<JSONObject> jsonObjectList;
                try {
                    jsonString = Files.readString(Path.of(outputPath));
                    jsonObjectList = JSON.parseArray(jsonString).toJavaList(JSONObject.class);
                } catch (IOException e) {
                    log.error("[Milvus] 读取现有文件失败", e);
                    break;
                }

                // 追加新数据
                for (QueryResultsWrapper.RowRecord record : batchResults) {
                    JSONObject row = new JSONObject();
                    row.put("knowledgeId", record.get(MilvusConstant.FIELD_KNOWLEDGE_ID));
                    row.put("vector", record.get(MilvusConstant.FIELD_VECTOR));
                    row.put("category", record.get(MilvusConstant.FIELD_CATEGORY));
                    row.put("subCategory", record.get(MilvusConstant.FIELD_SUB_CATEGORY));
                    jsonObjectList.add(row);
                    totalCount++;
                }

                // 写回文件
                try {
                    Files.write(Path.of(outputPath),
                            JSON.toJSONString(jsonObjectList).getBytes(),
                            StandardOpenOption.WRITE);
                } catch (IOException e) {
                    log.error("[Milvus] 写入文件失败", e);
                    break;
                }
            }
        } finally {
            queryIterator.close();
        }

        log.info("[Milvus] 数据导出完成，共 {} 条，文件: {}", totalCount, outputPath);
        return totalCount;
    }
}
