package com.mi.goffer.common.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/4/1 14:39
 * @Description: Markdown 块级解析工具类
 */
@Slf4j
@Component
public class MarkdownChunkParser {

    /**
     * 解析目录下所有 .md 文件，返回 Chunk 列表
     *
     * @param dirPath 八股文件夹路径（如 "八股/"）
     * @return 所有解析出的 Chunk
     */
    public List<Chunk> parseDirectory(String dirPath) {
        List<Chunk> allChunks = new ArrayList<>();
        Path dir = Paths.get(dirPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目录不存在: " + dirPath);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.md")) {
            for (Path file : stream) {
                allChunks.addAll(parseFile(file));
            }
        } catch (IOException e) {
            throw new RuntimeException("读取目录失败: " + dirPath, e);
        }
        log.info("[Parser] 共解析 {} 个 Chunk", allChunks.size());
        return allChunks;
    }

    /**
     * 解析单个 Markdown 文件
     * 文件格式约定：
     * # 大标题 → category（一级分类）
     * ## <font>子标题 → chunkTitle + chunkContent
     *
     * @param filePath .md 文件路径
     * @return 该文件的 Chunk 列表
     */
    public List<Chunk> parseFile(Path filePath) {
        List<Chunk> chunks = new ArrayList<>();
        String content;
        try {
            content = Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + filePath, e);
        }
        // category 直接取文件名（去掉 .md 后缀），subCategory 在 parseBlock 里提取
        String category = filePath.getFileName().toString().replaceAll("\\.md$", "");
        // 按 ## 二级标题拆分，每个 ## 块作为一个 Chunk
        Pattern sectionPattern = Pattern.compile("(?m)^##\\s+.*$");
        Matcher matcher = sectionPattern.matcher(content);
        List<int[]> ranges = new ArrayList<>();
        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
        }
        for (int i = 0; i < ranges.size(); i++) {
            // 当前 ## 到下一个 ## 之间的内容（或者到文件末尾）
            int start = ranges.get(i)[0];
            int end = (i + 1 < ranges.size()) ? ranges.get(i + 1)[0] : content.length();
            String block = content.substring(start, end).trim();
            if (block.isBlank()) continue;
            Chunk chunk = parseBlock(block, category, content, start);
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    /**
     * 提取当前 ## 块前面的所有内容中最后一个有效的 H1 标题（# xxx）作为 subCategory。
     * 规则：
     * - 只匹配 # xxx（H1 标题），排除 ##、### 等其他级别；
     * - 排除代码块内的 # 伪标题（如 # 编译前、# 添加/更新玩家得分 等）；
     * - 若无 H1 则返回 null。
     */
    private String extractSubCategory(String fullContent, int blockStart) {
        String beforeBlock = fullContent.substring(0, blockStart);
        // 先移除代码块（```...```），避免块内 # 行干扰
        String withoutCodeBlocks = beforeBlock.replaceAll("```[\\s\\S]*?```", "");
        Matcher m = Pattern.compile("(?m)^#\\s+(.+)$").matcher(withoutCodeBlocks);
        String subCategory = null;
        while (m.find()) {
            subCategory = m.group(1).replaceAll("<[^>]+>", "").trim();
        }
        return subCategory;
    }

    /**
     * 解析单个 ## 块
     * ## <font>子标题 → chunkTitle
     * 正文内容 → chunkContent
     * 过滤掉 --- 分隔线和图片，只保留文本
     *
     * @param block       ## 标题 + 正文
     * @param category    该块所属的一级分类（文件名）
     * @param fullContent 原始文件全文（用于向上查找 # 一级标题）
     * @param blockStart  当前 ## 在全文中的起始位置
     * @return Chunk 或 null（内容过短则跳过）
     */
    private Chunk parseBlock(String block, String category, String fullContent, int blockStart) {
        // 去掉开头的 ## 前缀
        String body = block.replaceFirst("(?m)^##\\s+", "").trim();
        if (body.isBlank()) return null;
        String chunkTitle;
        // 优先取 <font> 中的文字作为标题
        Matcher fontMatcher = Pattern.compile("<font[^>]*>(.+?)</font>").matcher(body);
        if (fontMatcher.find()) {
            chunkTitle = fontMatcher.group(1).trim();
        } else {
            // 没有 <font>，取第一行非空行作标题
            String[] lines = body.split("\n");
            chunkTitle = lines[0].replaceAll("<[^>]+>", "").trim();
        }
        // 清理正文：去掉 --- 分隔线、图片、HTML 标签、多余空格
        String chunkContent = body
                .replaceAll("---", "")
                .replaceAll("!\\[.*?\\]\\(.*?\\)", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();
        // 内容太短跳过
        if (chunkContent.length() < 10) return null;
        String subCategory = extractSubCategory(fullContent, blockStart);
        return new Chunk(category, subCategory, chunkTitle, chunkContent);
    }

    @Data
    @AllArgsConstructor
    public static class Chunk {

        /**
         * 所属分类：如 Java 基础、Java 并发、JVM、MySQL 等
         */
        private String category;
        /**
         * 子分类
         */
        private String subCategory;

        /**
         * 知识点题目
         */
        private String chunkTitle;

        /**
         * 知识点内容
         */
        private String chunkContent;
    }
}
