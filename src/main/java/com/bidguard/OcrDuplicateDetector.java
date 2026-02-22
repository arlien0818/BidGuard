package com.bidguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * OCR文档查重检测器
 * 
 * 功能：
 * 1. 对两个PDF的OCR结果进行全文查重
 * 2. 将重复片段映射回原始文字块（包含页码和bbox）
 * 3. 生成JSON格式的中间结果，供后续标注使用
 */
public class OcrDuplicateDetector {
    private static final Logger LOGGER = Logger.getLogger(OcrDuplicateDetector.class.getName());
    
    /**
     * 查重结果 - 顶层对象
     */
    public static class DuplicateDetectionResult {
        public String file1Name;              // 文档1文件名
        public String file2Name;              // 文档2文件名
        public String detectionTime;          // 检测时间
        public int totalMatches;              // 总共找到的重复片段数
        public List<DuplicateMatch> matches;  // 重复片段列表
        
        // 统计信息
        public int doc1Length;                // 文档1长度（字符数）
        public int doc2Length;                // 文档2长度（字符数）
        public int minLengthParam;            // 最小片段长度参数
        public int longestMatchLength;        // 最长连续匹配长度
        public int nGramSize;                 // nGram值（n的大小，如2或3）
        public int nGramTheoryCountDoc1;      // 文档1理论nGram数量
        public int nGramTheoryCountDoc2;      // 文档2理论nGram数量
        public int jaccardIntersection;       // Jaccard交集大小
        public int jaccardUnion;              // Jaccard并集大小
        public double jaccardScore;           // Jaccard相似度分数 (0-100)
        public double enhancedSimilarityScore; // 增强相似度分数 (0-100)
        
        public DuplicateDetectionResult() {
            this.matches = new ArrayList<>();
            this.detectionTime = new java.util.Date().toString();
        }
    }
    
    /**
     * 单个重复片段的匹配信息
     */
    public static class DuplicateMatch {
        public int matchId;                   // 匹配编号
        public String duplicateText;          // 重复的文本内容
        public int textLength;                // 文本长度
        public DocumentLocation doc1Location; // 文档1中的位置
        public DocumentLocation doc2Location; // 文档2中的位置
        
        public DuplicateMatch(int matchId, String text) {
            this.matchId = matchId;
            this.duplicateText = text;
            this.textLength = text.length();
        }
    }
    
    /**
     * 文档中的位置信息
     */
    public static class DocumentLocation {
        public int startCharPos;              // 在fullText中的起始字符位置
        public int endCharPos;                // 在fullText中的结束字符位置
        public List<TextBlockRef> textBlocks; // 涉及的文字块列表
        
        public DocumentLocation() {
            this.textBlocks = new ArrayList<>();
        }
    }
    
    /**
     * 文字块引用（包含位置和bbox信息）
     */
    public static class TextBlockRef {
        public int blockIndex;                // 在texts数组中的索引
        public int pageNumber;                // 页码
        public String text;                   // 文字块内容
        public double confidence;             // 识别置信度
        public List<double[]> bbox;           // 4个顶点坐标 [[x1,y1], [x2,y2], [x3,y3], [x4,y4]]
        public int startCharInBlock;          // 该文字块在重复片段中的起始字符（相对于文字块内）
        public int endCharInBlock;            // 该文字块在重复片段中的结束字符（相对于文字块内）
        
        // 精确计算的子串bbox（仅当有字符级信息时）
        // 如果为null，则使用上面的bbox；如果非 null，标注时优先使用这个
        public List<double[]> preciseCharBbox;
        
        public TextBlockRef(int blockIndex, int pageNumber, String text, 
                           double confidence, List<double[]> bbox) {
            this.blockIndex = blockIndex;
            this.pageNumber = pageNumber;
            this.text = text;
            this.confidence = confidence;
            this.bbox = bbox;
        }
    }
    
    /**
     * 检测两个OCR结果之间的重复内容
     * 
     * @param ocrResult1 文档1的OCR结果
     * @param ocrResult2 文档2的OCR结果
     * @param file1Name 文档1文件名
     * @param file2Name 文档2文件名
     * @param minLength 最小重复片段长度（字符数）
     * @return 查重结果
     */
    public static DuplicateDetectionResult detectDuplicates(
            OcrServiceClient.OcrResult ocrResult1,
            OcrServiceClient.OcrResult ocrResult2,
            String file1Name,
            String file2Name,
            int minLength) {
        
        DuplicateDetectionResult result = new DuplicateDetectionResult();
        result.file1Name = file1Name;
        result.file2Name = file2Name;
        
        LOGGER.info("开始查重检测:");
        LOGGER.info(String.format("  文档1: %s (%d字符)", file1Name, ocrResult1.fullText.length()));
        LOGGER.info(String.format("  文档2: %s (%d字符)", file2Name, ocrResult2.fullText.length()));
        LOGGER.info(String.format("  最小片段长度: %d字符", minLength));
        
        // 1. 使用现有的算法查找重复片段
        List<BidChecker.SubstringMatch> substringMatches = 
            BidChecker.findCrossDocumentSubstrings(
                ocrResult1.fullText, 
                ocrResult2.fullText, 
                minLength);
        
        LOGGER.info(String.format("找到 %d 个重复片段", substringMatches.size()));
        
        // 2. 为每个重复片段建立字符位置到文字块的映射
        for (int i = 0; i < substringMatches.size(); i++) {
            BidChecker.SubstringMatch sm = substringMatches.get(i);
            
            DuplicateMatch match = new DuplicateMatch(i + 1, sm.substring);
            
            // 映射文档1中的位置
            match.doc1Location = mapCharRangeToBlocks(
                ocrResult1,
                sm.startPos1,
                sm.startPos1 + sm.length,
                sm.substring
            );
            
            // 映射文档2中的位置
            match.doc2Location = mapCharRangeToBlocks(
                ocrResult2,
                sm.startPos2,
                sm.startPos2 + sm.length,
                sm.substring
            );
            
            result.matches.add(match);
            
            LOGGER.info(String.format("  片段#%d: %d字符, 文档1[%d个块], 文档2[%d个块]",
                i + 1, sm.length,
                match.doc1Location.textBlocks.size(),
                match.doc2Location.textBlocks.size()));
        }
        
        result.totalMatches = result.matches.size();
        
        // 3. 计算统计信息
        result.doc1Length = ocrResult1.fullText.length();
        result.doc2Length = ocrResult2.fullText.length();
        result.minLengthParam = minLength;
        
        // 计算最长匹配长度
        result.longestMatchLength = 0;
        for (DuplicateMatch match : result.matches) {
            if (match.textLength > result.longestMatchLength) {
                result.longestMatchLength = match.textLength;
            }
        }
        
        // 计算Jaccard相似度（使用3-gram）
        JaccardStats jaccard = calculateJaccardStats(ocrResult1.fullText, ocrResult2.fullText, 3);
        result.jaccardIntersection = jaccard.intersection;
        result.jaccardUnion = jaccard.union;
        result.jaccardScore = jaccard.score;
        result.nGramSize = jaccard.nGramSize;
        result.nGramTheoryCountDoc1 = jaccard.theoryCountDoc1;
        result.nGramTheoryCountDoc2 = jaccard.theoryCountDoc2;
        
        // 计算增强相似度
        result.enhancedSimilarityScore = BidChecker.enhancedSimilarity(
            ocrResult1.fullText, ocrResult2.fullText);
        
        LOGGER.info(String.format("统计信息: 最长匹配=%d, Jaccard=%.2f, Enhanced=%.2f",
            result.longestMatchLength, result.jaccardScore, result.enhancedSimilarityScore));
        
        return result;
    }
    
    /**
     * 将fullText中的字符范围映射到文字块列表
     * 
     * 策略：
     * 1. 遍历所有文字块，累计字符位置
     * 2. 找出与指定范围有重叠的所有文字块
     * 3. 对每个重叠块，如果有字符级bbox，则计算精确子串bbox
     * 4. 验证：确保重叠部分真的在重复文本中
     * 
     * @param ocrResult OCR识别结果
     * @param startChar fullText中的起始字符位置
     * @param endChar fullText中的结束字符位置
     * @param duplicateText 重复的文本内容（用于验证）
     * @return 文档位置信息
     */
    private static DocumentLocation mapCharRangeToBlocks(
            OcrServiceClient.OcrResult ocrResult,
            int startChar,
            int endChar,
            String duplicateText) {
        
        DocumentLocation location = new DocumentLocation();
        location.startCharPos = startChar;
        location.endCharPos = endChar;
        
        // 遍历所有文字块，找出重叠的
        int currentPos = 0;
        
        for (int i = 0; i < ocrResult.texts.size(); i++) {
            OcrServiceClient.OcrTextItem item = ocrResult.texts.get(i);
            String itemText = item.text != null ? item.text : "";
            
            int itemStart = currentPos;
            int itemEnd = currentPos + itemText.length();
            
            // 检查是否与目标范围有重叠
            if (itemEnd > startChar && itemStart < endChar) {
                // 有重叠，计算重叠部分
                int overlapStart = Math.max(0, startChar - itemStart);
                int overlapEnd = Math.min(itemText.length(), endChar - itemStart);
                
                // 提取文字块中的重叠部分文本
                String overlapText = itemText.substring(overlapStart, overlapEnd);
                
                LOGGER.info(String.format("块#%d重叠检测: itemStart=%d, itemEnd=%d, 目标范围[%d,%d), 重叠[%d,%d), 重叠文本='%s'",
                    i, itemStart, itemEnd, startChar, endChar, overlapStart, overlapEnd, overlapText));
                
                // 验证：重叠部分是否在重复文本中出现
                // 去除所有空白字符后比较，这样可以容忍空格、换行等格式差异
                String overlapNormalized = overlapText.replaceAll("\\s+", "");
                String duplicateNormalized = duplicateText.replaceAll("\\s+", "");
                
                // 使用部分匹配：寻找最长的公共子串
                // 如果重叠部分与重复文本有足够长的公共部分，就认为有效
                boolean isValid = false;
                if (overlapNormalized.length() > 2) {
                    // 方法1：重叠部分完全在重复文本中
                    if (duplicateNormalized.contains(overlapNormalized)) {
                        isValid = true;
                    } else {
                        // 方法2：寻找最长公共子串，如果足够长，也认为有效
                        int maxCommonLength = findLongestCommonSubstring(overlapNormalized, duplicateNormalized);
                        // 要求：至少15个字符的公共子串，且公共部分占重叠部分的80%以上
                        isValid = maxCommonLength >= 15 && 
                                 (maxCommonLength * 1.0 / overlapNormalized.length() >= 0.80);
                    }
                }
                
                if (isValid) {
                    TextBlockRef blockRef = new TextBlockRef(
                        i,
                        item.page,
                        itemText,
                        item.confidence,
                        item.bbox
                    );
                    
                    blockRef.startCharInBlock = overlapStart;
                    blockRef.endCharInBlock = overlapEnd;
                    
                    // 如果有字符级bbox，计算精确的子串bbox
                    if (item.charBboxes != null && !item.charBboxes.isEmpty()) {
                        LOGGER.info(String.format("块#%d: 尝试计算精确bbox, 文本='%s', 字符数=%d, charBboxes数=%d, 范围[%d,%d)",
                            i, itemText, itemText.length(), item.charBboxes.size(), overlapStart, overlapEnd));
                        
                        blockRef.preciseCharBbox = calculatePreciseSubstringBbox(
                            item.charBboxes, overlapStart, overlapEnd, itemText);
                        
                        if (blockRef.preciseCharBbox != null) {
                            LOGGER.info(String.format("✓ 块#%d: 成功计算精确子串bbox [%d,%d) / %d字符，子串='%s'",
                                i, overlapStart, overlapEnd, itemText.length(), 
                                itemText.substring(overlapStart, overlapEnd)));
                        } else {
                            LOGGER.warning(String.format("✗ 块#%d: 精确bbox计算失败，将使用整块bbox", i));
                        }
                    } else {
                        LOGGER.warning(String.format("块#%d: 无字符级bbox数据 (charBboxes %s), 文本='%s'",
                            i, (item.charBboxes == null ? "null" : "empty"), itemText));
                    }
                    
                    location.textBlocks.add(blockRef);
                }
            }
            
            // 更新位置：文字块长度 + 分隔符长度
            // 阿里云OCR的content字段中，各word之间用\n连接（除最后一个）
            currentPos = itemEnd;
            if (i < ocrResult.texts.size() - 1) {
                currentPos += 1;  // 阿里云OCR用\n连接word
            }
            
            // 如果已经超过结束位置，可以提前退出
            if (currentPos >= endChar) {
                break;
            }
        }
        
        return location;
    }
    
    /**
     * 查找两个字符串的最长公共子串长度
     * 用于验证文字块是否包含重复内容
     */
    private static int findLongestCommonSubstring(String str1, String str2) {
        if (str1 == null || str2 == null || str1.isEmpty() || str2.isEmpty()) {
            return 0;
        }
        
        int maxLength = 0;
        int len1 = str1.length();
        int len2 = str2.length();
        
        // 使用动态规划查找最长公共子串
        int[][] dp = new int[len1 + 1][len2 + 1];
        
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    maxLength = Math.max(maxLength, dp[i][j]);
                } else {
                    dp[i][j] = 0;
                }
            }
        }
        
        return maxLength;
    }
    
    /**
     * 根据字符级bbox计算精确的子串bbox
     * 
     * @param charBboxes 字符级bbox列表（每个字符一个bbox）
     * @param startIdx 子串起始字符索引（相对于文字块）
     * @param endIdx 子串结束字符索引（相对于文字块，不含）
     * @param blockText 文字块完整文本（用于验证）
     * @return 子串的bbox [[x1,y1], [x2,y2], [x3,y3], [x4,y4]]，如果计算失败返回null
     */
    private static List<double[]> calculatePreciseSubstringBbox(
            List<List<double[]>> charBboxes,
            int startIdx,
            int endIdx,
            String blockText) {
        
        // 边界检查
        if (charBboxes == null || charBboxes.isEmpty()) {
            return null;
        }
        
        if (startIdx < 0 || endIdx > charBboxes.size() || startIdx >= endIdx) {
            LOGGER.warning(String.format(
                "子串索引越界: [%d,%d), charBboxes.size=%d, blockText.length=%d",
                startIdx, endIdx, charBboxes.size(), blockText.length()));
            return null;
        }
        
        // 收集子串范围内所有字符的bbox
        List<List<double[]>> substringCharBboxes = new ArrayList<>();
        for (int i = startIdx; i < endIdx; i++) {
            if (i < charBboxes.size()) {
                substringCharBboxes.add(charBboxes.get(i));
            }
        }
        
        if (substringCharBboxes.isEmpty()) {
            return null;
        }
        
        // 计算包围所有字符的最小外接矩形
        // bbox格式: [[左上x,左上y], [右上x,右上y], [右下x,右下y], [左下x,左下y]]
        
        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (List<double[]> charBbox : substringCharBboxes) {
            if (charBbox != null && charBbox.size() == 4) {
                for (double[] vertex : charBbox) {
                    if (vertex.length == 2) {
                        minX = Math.min(minX, vertex[0]);
                        maxX = Math.max(maxX, vertex[0]);
                        minY = Math.min(minY, vertex[1]);
                        maxY = Math.max(maxY, vertex[1]);
                    }
                }
            }
        }
        
        // 构造最小外接矩形的4个顶点
        List<double[]> result = new ArrayList<>();
        result.add(new double[]{minX, minY}); // 左上
        result.add(new double[]{maxX, minY}); // 右上
        result.add(new double[]{maxX, maxY}); // 右下
        result.add(new double[]{minX, maxY}); // 左下
        
        return result;
    }
    
    /**
     * 保存查重结果到JSON文件
     * 
     * @param result 查重结果
     * @param file1Name 文档1文件名（用于生成输出文件名）
     * @param file2Name 文档2文件名（用于生成输出文件名）
     * @return 保存的文件对象
     */
    public static File saveResultToJson(
            DuplicateDetectionResult result,
            String file1Name,
            String file2Name) throws IOException {
        
        // 创建output目录
        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        // 生成文件名
        String baseName1 = file1Name.replaceAll("(?i)\\.(pdf|txt)$", "");
        String baseName2 = file2Name.replaceAll("(?i)\\.(pdf|txt)$", "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        String jsonFileName = String.format("duplicate_detection_%s_vs_%s_%s.json",
            sanitizeFileName(baseName1),
            sanitizeFileName(baseName2),
            timestamp);
        
        File jsonFile = new File(outputDir, jsonFileName);
        
        // 序列化为JSON
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()  // 不转义中文
            .create();
        
        String json = gson.toJson(result);
        
        // 写入文件
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(jsonFile),
                StandardCharsets.UTF_8)) {
            writer.write(json);
        }
        
        LOGGER.info("查重结果已保存到: " + jsonFile.getAbsolutePath());
        
        // 同时保存一个人类可读的文本报告
        saveHumanReadableReport(result, outputDir, baseName1, baseName2, timestamp);
        
        return jsonFile;
    }
    
    /**
     * 保存人类可读的文本报告
     */
    private static void saveHumanReadableReport(
            DuplicateDetectionResult result,
            File outputDir,
            String baseName1,
            String baseName2,
            String timestamp) {
        
        String txtFileName = String.format("duplicate_report_%s_vs_%s_%s.txt",
            sanitizeFileName(baseName1),
            sanitizeFileName(baseName2),
            timestamp);
        
        File txtFile = new File(outputDir, txtFileName);
        
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(
                    new FileOutputStream(txtFile),
                    StandardCharsets.UTF_8))) {
            
            writer.println("=".repeat(80));
            writer.println("OCR文档查重检测报告");
            writer.println("=".repeat(80));
            writer.println();
            writer.println("文档A: " + result.file1Name);
            writer.println("文档B: " + result.file2Name);
            writer.println("检测时间: " + result.detectionTime);
            writer.println();
            writer.println("-".repeat(80));
            writer.println("【基本信息】");
            writer.println("-".repeat(80));
            writer.println(String.format("文档A长度: %d 字符", result.doc1Length));
            writer.println(String.format("文档B长度: %d 字符", result.doc2Length));
            writer.println(String.format("minLength值: %d", result.minLengthParam));
            writer.println();
            writer.println("-".repeat(80));
            writer.println("【连续子串匹配结果】");
            writer.println("-".repeat(80));
            writer.println(String.format("匹配段总数: %d", result.totalMatches));
            writer.println(String.format("最长连续匹配: %d 字符", result.longestMatchLength));
            writer.println();
            writer.println("=".repeat(80));
            writer.println();
            
            for (DuplicateMatch match : result.matches) {
                writer.println(String.format("【匹配段 #%d】", match.matchId));
                writer.println("-".repeat(80));
                writer.println(String.format("匹配长度: %d 字符", match.textLength));
                writer.println(String.format("文档A起始位置: %d", match.doc1Location.startCharPos));
                writer.println(String.format("文档B起始位置: %d", match.doc2Location.startCharPos));
                writer.println();
                
                // 文档1详细位置
                writer.println(">> 文档A位置信息:");
                writer.println(String.format("   fullText字符范围: [%d - %d]",
                    match.doc1Location.startCharPos,
                    match.doc1Location.endCharPos));
                writer.println(String.format("   涉及文字块数: %d",
                    match.doc1Location.textBlocks.size()));
                
                for (TextBlockRef block : match.doc1Location.textBlocks) {
                    writer.println(String.format("   - 块#%d (第%d页, 置信度%.2f%%)",
                        block.blockIndex,
                        block.pageNumber,
                        block.confidence * 100));
                    if (block.bbox != null && block.bbox.size() == 4) {
                        writer.println(String.format("     坐标: [左上(%.0f,%.0f), 右上(%.0f,%.0f), 右下(%.0f,%.0f), 左下(%.0f,%.0f)]",
                            block.bbox.get(0)[0], block.bbox.get(0)[1],
                            block.bbox.get(1)[0], block.bbox.get(1)[1],
                            block.bbox.get(2)[0], block.bbox.get(2)[1],
                            block.bbox.get(3)[0], block.bbox.get(3)[1]));
                    }
                }
                writer.println();
                
                // 文档2详细位置
                writer.println(">> 文档B位置信息:");
                writer.println(String.format("   fullText字符范围: [%d - %d]",
                    match.doc2Location.startCharPos,
                    match.doc2Location.endCharPos));
                writer.println(String.format("   涉及文字块数: %d",
                    match.doc2Location.textBlocks.size()));
                
                for (TextBlockRef block : match.doc2Location.textBlocks) {
                    writer.println(String.format("   - 块#%d (第%d页, 置信度%.2f%%)",
                        block.blockIndex,
                        block.pageNumber,
                        block.confidence * 100));
                    if (block.bbox != null && block.bbox.size() == 4) {
                        writer.println(String.format("     坐标: [左上(%.0f,%.0f), 右上(%.0f,%.0f), 右下(%.0f,%.0f), 左下(%.0f,%.0f)]",
                            block.bbox.get(0)[0], block.bbox.get(0)[1],
                            block.bbox.get(1)[0], block.bbox.get(1)[1],
                            block.bbox.get(2)[0], block.bbox.get(2)[1],
                            block.bbox.get(3)[0], block.bbox.get(3)[1]));
                    }
                }
                writer.println();
                
                // 重复文本内容
                writer.println(">> 重复文本内容:");
                writer.println("-".repeat(80));
                writer.println(match.duplicateText);
                writer.println("-".repeat(80));
                writer.println();
                writer.println();
            }
            
            writer.println("=".repeat(80));
            writer.println("【统计汇总】");
            writer.println("=".repeat(80));
            writer.println();
            writer.println(String.format("Jaccard相似度 (%d-gram):", result.nGramSize));
            writer.println(String.format("  nGram值: %d", result.nGramSize));
            writer.println(String.format("  文档A理论nGram数量: %d", result.nGramTheoryCountDoc1));
            writer.println(String.format("  文档B理论nGram数量: %d", result.nGramTheoryCountDoc2));
            writer.println(String.format("  交集大小: %d", result.jaccardIntersection));
            writer.println(String.format("  并集大小: %d", result.jaccardUnion));
            writer.println(String.format("  Jaccard分数: %.2f%%", result.jaccardScore));
            writer.println();
            writer.println("增强相似度:");
            writer.println(String.format("  Enhanced Similarity: %.2f%%", result.enhancedSimilarityScore));
            writer.println();
            writer.println("=".repeat(80));
            writer.println("报告结束");
            writer.println("=".repeat(80));
            
            LOGGER.info("可读报告已保存到: " + txtFile.getAbsolutePath());
            
        } catch (IOException e) {
            LOGGER.warning("保存可读报告失败: " + e.getMessage());
        }
    }
    
    /**
     * Jaccard统计信息
     */
    private static class JaccardStats {
        int intersection;
        int union;
        double score;
        int nGramSize;
        int theoryCountDoc1;
        int theoryCountDoc2;
        
        JaccardStats(int intersection, int union, double score, int nGramSize, int theoryCountDoc1, int theoryCountDoc2) {
            this.intersection = intersection;
            this.union = union;
            this.score = score;
            this.nGramSize = nGramSize;
            this.theoryCountDoc1 = theoryCountDoc1;
            this.theoryCountDoc2 = theoryCountDoc2;
        }
    }
    
    /**
     * 计算Jaccard统计信息（返回交集、并集、分数及理论nGram数量）
     */
    private static JaccardStats calculateJaccardStats(String text1, String text2, int n) {
        Set<String> s1 = BidChecker.shingles(text1, n);
        Set<String> s2 = BidChecker.shingles(text2, n);
        
        Set<String> union = new HashSet<>(s1);
        union.addAll(s2);
        
        Set<String> intersection = new HashSet<>(s1);
        intersection.retainAll(s2);
        
        double score = union.isEmpty() ? 0.0 : (intersection.size() * 100.0 / union.size());
        
        // 计算理论nGram数量（归一化后去除空格的字符数 - n + 1）
        String norm1 = BidChecker.normalizeForSimilarity(text1).replace(" ", "");
        String norm2 = BidChecker.normalizeForSimilarity(text2).replace(" ", "");
        int theoryCount1 = Math.max(0, norm1.length() - n + 1);
        int theoryCount2 = Math.max(0, norm2.length() - n + 1);
        
        return new JaccardStats(intersection.size(), union.size(), score, n, theoryCount1, theoryCount2);
    }
    
    /**
     * 清理文件名中的非法字符
     */
    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
    
    /**
     * 截断文本用于预览
     */
    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
    
    /**
     * 纯文本查重检测（用于TXT文件）
     * 使用和PDF相同的查重算法，但不包含页码、bbox等OCR特有信息
     * 
     * @param text1 文本1内容
     * @param text2 文本2内容
     * @param file1Name 文件1名称
     * @param file2Name 文件2名称
     * @param minLength 最小重复片段长度（字符数）
     * @return 查重结果（简化版，无bbox信息）
     */
    public static DuplicateDetectionResult detectDuplicatesFromText(
            String text1,
            String text2,
            String file1Name,
            String file2Name,
            int minLength) {
        
        DuplicateDetectionResult result = new DuplicateDetectionResult();
        result.file1Name = file1Name;
        result.file2Name = file2Name;
        result.doc1Length = text1.length();
        result.doc2Length = text2.length();
        result.minLengthParam = minLength;
        
        LOGGER.info("开始纯文本查重检测:");
        LOGGER.info(String.format("  文档1: %s (%d字符)", file1Name, text1.length()));
        LOGGER.info(String.format("  文档2: %s (%d字符)", file2Name, text2.length()));
        LOGGER.info(String.format("  最小片段长度: %d字符", minLength));
        
        // 使用和PDF相同的核心查重算法
        List<BidChecker.SubstringMatch> substringMatches = 
            BidChecker.findCrossDocumentSubstrings(text1, text2, minLength);
        
        result.totalMatches = substringMatches.size();
        LOGGER.info(String.format("找到 %d 个重复片段", substringMatches.size()));
        
        // 转换为DuplicateMatch对象（但不包含textBlocks信息）
        int maxLength = 0;
        for (int i = 0; i < substringMatches.size(); i++) {
            BidChecker.SubstringMatch sm = substringMatches.get(i);
            
            DuplicateMatch match = new DuplicateMatch(i + 1, sm.substring);
            
            // 只设置字符位置，不设置textBlocks
            match.doc1Location = new DocumentLocation();
            match.doc1Location.startCharPos = sm.startPos1;
            match.doc1Location.endCharPos = sm.startPos1 + sm.length;
            
            match.doc2Location = new DocumentLocation();
            match.doc2Location.startCharPos = sm.startPos2;
            match.doc2Location.endCharPos = sm.startPos2 + sm.length;
            
            result.matches.add(match);
            
            if (sm.length > maxLength) {
                maxLength = sm.length;
            }
        }
        
        result.longestMatchLength = maxLength;
        
        // 计算Jaccard相似度
        JaccardStats jaccardStats = calculateJaccardStats(text1, text2, 3);
        result.jaccardIntersection = jaccardStats.intersection;
        result.jaccardUnion = jaccardStats.union;
        result.jaccardScore = jaccardStats.score;
        result.nGramSize = jaccardStats.nGramSize;
        result.nGramTheoryCountDoc1 = jaccardStats.theoryCountDoc1;
        result.nGramTheoryCountDoc2 = jaccardStats.theoryCountDoc2;
        
        // 计算增强相似度
        result.enhancedSimilarityScore = BidChecker.enhancedSimilarity(text1, text2);
        
        LOGGER.info(String.format("查重完成: Jaccard=%.2f%%, Enhanced=%.2f%%",
            result.jaccardScore, result.enhancedSimilarityScore));
        
        return result;
    }
    
    /**
     * 保存纯文本查重结果（用于TXT文件）
     * 生成简化版的JSON和TXT报告，不包含bbox、页码等信息
     * 
     * @param result 查重结果
     * @param file1Name 文件1名称
     * @param file2Name 文件2名称
     * @return JSON文件
     */
    public static File saveTextDuplicateResult(
            DuplicateDetectionResult result,
            String file1Name,
            String file2Name) throws IOException {
        
        // 创建output目录
        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        // 生成文件名
        String baseName1 = file1Name.replaceAll("(?i)\\.(pdf|txt)$", "");
        String baseName2 = file2Name.replaceAll("(?i)\\.(pdf|txt)$", "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        String jsonFileName = String.format("duplicate_detection_%s_vs_%s_%s.json",
            sanitizeFileName(baseName1),
            sanitizeFileName(baseName2),
            timestamp);
        
        File jsonFile = new File(outputDir, jsonFileName);
        
        // 序列化为JSON
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
        
        String json = gson.toJson(result);
        
        // 写入文件
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(jsonFile),
                StandardCharsets.UTF_8)) {
            writer.write(json);
        }
        
        LOGGER.info("查重结果已保存到: " + jsonFile.getAbsolutePath());
        
        // 保存简化版的文本报告
        saveSimplifiedTextReport(result, outputDir, baseName1, baseName2, timestamp);
        
        return jsonFile;
    }
    
    /**
     * 保存简化版的文本报告（不包含bbox、页码等OCR特有信息）
     */
    private static void saveSimplifiedTextReport(
            DuplicateDetectionResult result,
            File outputDir,
            String baseName1,
            String baseName2,
            String timestamp) {
        
        String txtFileName = String.format("duplicate_report_%s_vs_%s_%s.txt",
            sanitizeFileName(baseName1),
            sanitizeFileName(baseName2),
            timestamp);
        
        File txtFile = new File(outputDir, txtFileName);
        
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(
                    new FileOutputStream(txtFile),
                    StandardCharsets.UTF_8))) {
            
            writer.println("=".repeat(80));
            writer.println("文本查重检测报告");
            writer.println("=".repeat(80));
            writer.println();
            writer.println("文档A: " + result.file1Name);
            writer.println("文档B: " + result.file2Name);
            writer.println("检测时间: " + result.detectionTime);
            writer.println();
            writer.println("-".repeat(80));
            writer.println("【基本信息】");
            writer.println("-".repeat(80));
            writer.println(String.format("文档A长度: %d 字符", result.doc1Length));
            writer.println(String.format("文档B长度: %d 字符", result.doc2Length));
            writer.println(String.format("minLength值: %d", result.minLengthParam));
            writer.println();
            writer.println("-".repeat(80));
            writer.println("【连续子串匹配结果】");
            writer.println("-".repeat(80));
            writer.println(String.format("匹配段总数: %d", result.totalMatches));
            writer.println(String.format("最长连续匹配: %d 字符", result.longestMatchLength));
            writer.println();
            writer.println("=".repeat(80));
            writer.println();
            
            for (DuplicateMatch match : result.matches) {
                writer.println(String.format("【匹配段 #%d】", match.matchId));
                writer.println("-".repeat(80));
                writer.println(String.format("匹配长度: %d 字符", match.textLength));
                writer.println(String.format("文档A起始位置: %d", match.doc1Location.startCharPos));
                writer.println(String.format("文档B起始位置: %d", match.doc2Location.startCharPos));
                writer.println();
                
                // 文档A位置信息（简化版，只有字符范围）
                writer.println(">> 文档A位置信息:");
                writer.println(String.format("   字符范围: [%d - %d]",
                    match.doc1Location.startCharPos,
                    match.doc1Location.endCharPos));
                writer.println();
                
                // 文档B位置信息（简化版，只有字符范围）
                writer.println(">> 文档B位置信息:");
                writer.println(String.format("   字符范围: [%d - %d]",
                    match.doc2Location.startCharPos,
                    match.doc2Location.endCharPos));
                writer.println();
                
                // 重复文本内容
                writer.println(">> 重复文本内容:");
                writer.println("-".repeat(80));
                writer.println(match.duplicateText);
                writer.println("-".repeat(80));
                writer.println();
                writer.println();
            }
            
            writer.println("=".repeat(80));
            writer.println("【统计汇总】");
            writer.println("=".repeat(80));
            writer.println();
            writer.println(String.format("Jaccard相似度 (%d-gram):", result.nGramSize));
            writer.println(String.format("  nGram值: %d", result.nGramSize));
            writer.println(String.format("  文档A理论nGram数量: %d", result.nGramTheoryCountDoc1));
            writer.println(String.format("  文档B理论nGram数量: %d", result.nGramTheoryCountDoc2));
            writer.println(String.format("  交集大小: %d", result.jaccardIntersection));
            writer.println(String.format("  并集大小: %d", result.jaccardUnion));
            writer.println(String.format("  Jaccard分数: %.2f%%", result.jaccardScore));
            writer.println();
            writer.println("增强相似度:");
            writer.println(String.format("  Enhanced Similarity: %.2f%%", result.enhancedSimilarityScore));
            writer.println();
            writer.println("=".repeat(80));
            writer.println("报告结束");
            writer.println("=".repeat(80));
            
            LOGGER.info("文本查重报告已保存到: " + txtFile.getAbsolutePath());
            
        } catch (IOException e) {
            LOGGER.warning("保存文本报告失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试方法：对比两个PDF文件
     */
    public static void main(String[] args) {
        try {
            // 示例：对比两个PDF
            File pdf1 = new File("testfiles/test1.pdf");
            File pdf2 = new File("testfiles/test2.pdf");
            
            if (!pdf1.exists() || !pdf2.exists()) {
                System.err.println("测试文件不存在");
                return;
            }
            
            System.out.println("正在进行OCR识别...");
            
            // 获取OCR结果
            OcrServiceClient.OcrResult result1 = OcrServiceFactory.recognizePdf(pdf1);
            OcrServiceClient.OcrResult result2 = OcrServiceFactory.recognizePdf(pdf2);
            
            System.out.println("\n开始查重检测...");
            
            // 执行查重检测
            DuplicateDetectionResult detection = detectDuplicates(
                result1,
                result2,
                pdf1.getName(),
                pdf2.getName(),
                100  // 最小100字符
            );
            
            // 保存结果
            File jsonFile = saveResultToJson(detection, pdf1.getName(), pdf2.getName());
            
            System.out.println("\n查重完成！");
            System.out.println("结果文件: " + jsonFile.getAbsolutePath());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
