package com.mi.goffer.service.impl;

import com.mi.goffer.common.util.MarkdownChunkParser;
import com.mi.goffer.common.util.MarkdownChunkParser.Chunk;
import com.mi.goffer.common.util.MilvusUtil;
import com.mi.goffer.dao.entity.InterviewKnowledgeDO;
import com.mi.goffer.dao.mapper.InterviewKnowledgeMapper;
import com.mi.goffer.service.InterviewKnowledgeImporter;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/1 14:53
 * @Description: 面试知识点导入实现类（只在第一次初始化数据库使用，后续不再使用）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Deprecated
public class InterviewKnowledgeImporterImpl implements InterviewKnowledgeImporter {

    private final InterviewKnowledgeMapper knowledgeMapper;
    private final MarkdownChunkParser markdownChunkParser;
    private final EmbeddingModel embeddingModel;
    private final MilvusUtil milvusUtil;

    // 源目录
    @Value("${knowledge.import.source-dir}")
    private String sourceDir;
    // 批量大小，默认 50（Milvus 单次插入不要太大）
    @Value("${knowledge.import.batch-size}")
    private int batchSize;

    /**
     * 批量导入面试知识点
     */
    @Override
    public void importAll() {
        // 解析目录下所有 .md 文件
        List<Chunk> chunks = markdownChunkParser.parseDirectory(sourceDir);
        log.info("[Importer] 待导入 {} 个 Chunk", chunks.size());
        // 3. 分批处理，避免内存占用过大
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<Chunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            processBatch(batch);
            log.info("[Importer] 进度 {}/{}", Math.min(i + batchSize, chunks.size()), chunks.size());
        }
        log.info("[Importer] 全部导入完成");
    }

    /**
     * 处理一批 Chunk：向量化 → 写 MySQL → 写 Milvus
     * @param batch 待处理的 Chunk
     */
    private void processBatch(List<Chunk> batch) {
        List<InterviewKnowledgeDO> entities = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        for (Chunk chunk : batch) {
            // 先构建实体
            InterviewKnowledgeDO entity = InterviewKnowledgeDO.builder()
                    .category(chunk.getCategory())
                    .subCategory(chunk.getSubCategory())
                    .chunkTitle(chunk.getChunkTitle())
                    .chunkContent(chunk.getChunkContent())
                    .build();
            knowledgeMapper.insert(entity);
            // 调用 Qwen3-Embedding-0.6B 生成向量
            float[] vector;
            try {
                var emb = embeddingModel.embed(chunk.getChunkContent()).content();
                vector = new float[emb.vectorAsList().size()];
                for (int j = 0; j < vector.length; j++) {
                    vector[j] = emb.vectorAsList().get(j);
                }
            } catch (Exception e) {
                log.error("向量化失败，已跳过: {}", chunk.getChunkTitle(), e);
                continue;
            }
            vectors.add(vector);
            entities.add(entity);
        }
        // 整批向量写入 Milvus
        if (!entities.isEmpty()) {
            milvusUtil.insert(entities, vectors);
        }
    }
}
