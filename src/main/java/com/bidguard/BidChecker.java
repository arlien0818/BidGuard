package com.bidguard;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class BidChecker {

    private static final Logger LOGGER = Logger.getLogger(BidChecker.class.getName());

    // ...existing code...

    private static String preview200(String s) {
        final int maxChars = SimilarityConfig.getInstance().previewMaxChars;
        if (s == null) {
            return "<null>";
        }
        String normalized = s.replace("\r", "").replace("\t", " ");
        normalized = normalized.replaceAll("[ ]{2,}", " ");
        normalized = normalized.trim();
        if (normalized.isEmpty()) {
            return "<empty>";
        }
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars) + "...";
    }

    private static void debugPrintPdfContent(File file, String text) {
        System.out.println("=== PDF文本调试输出: " + file.getName() + " ===");
        if (text == null || text.isBlank()) {
            System.out.println("<空文本或提取失败>");
            return;
        }
        String[] lines = text.split("\\R");
        int maxLines = SimilarityConfig.getInstance().debugPdfPrintLines;
        System.out.println("总字符数: " + text.length() + ", 行数: " + lines.length);
        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            System.out.println(String.format(Locale.ROOT, "%02d| %s", i + 1, lines[i]));
        }
        if (lines.length > maxLines) {
            System.out.println("... (共 " + lines.length + " 行)");
        }
        System.out.println("预览200字符: " + preview200(text));
        System.out.println("==============================\n");
    }

    private static boolean isUnreadableOrEmptyOfficeFile(File file) {
        if (file == null) {
            return true;
        }
        if (!file.exists() || !file.isFile()) {
            LOGGER.warning(() -> "File not found: " + file);
            return true;
        }
        if (file.length() == 0L) {
            LOGGER.warning(() -> "File is empty (0 bytes): " + file);
            return true;
        }
        // Office 临时锁文件：~$xxx.docx，不能读
        if (file.getName().startsWith("~$")) {
            LOGGER.warning(() -> "Skip Office temporary lock file: " + file);
            return true;
        }
        return false;
    }

    private static String normalizeForSimilarity(String s) {
        if (s == null) {
            return "";
        }
        // 统一大小写；把各种空白/不可见字符拉平
        String t = s.toLowerCase(Locale.ROOT)
                .replace('\u00A0', ' ') // nbsp
                .replace('\u200B', ' ') // zero width space
                .replace('\r', '\n');
        // 去掉多余空白
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    //这个方法目前没有任何地方用到。
    private static String advancedNormalization(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}&&[^\\u4e00-\\u9fa5]]", "") // 保留中文，去除标点
                .replaceAll("\\d+", "NUM") // 数字标准化
                .replaceAll("\\s+", " ")
                .trim();
    }

    // 常用停用词列表
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这",
            "the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "a", "an", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did"
    );
    //这个方法目前没有任何地方用到。
    private static String removeStopWords(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Arrays.stream(text.split("\\s+"))
                .filter(word -> !STOP_WORDS.contains(word.toLowerCase()))
                .collect(Collectors.joining(" "));
    }

    private static Set<String> shingles(String text, int n) {
        Set<String> set = new HashSet<>();
        String t = normalizeForSimilarity(text);
        if (t.isEmpty()) {
            return set;
        }

        // 如果文本中几乎没有空格（典型中文），用字符 n-gram
        boolean looksLikeNoSpaceLanguage = t.indexOf(' ') < 0;

        if (looksLikeNoSpaceLanguage) {
            String compact = t.replace(" ", "");
            if (compact.length() < n) {
                set.add(compact);
                return set;
            }
            for (int i = 0; i <= compact.length() - n; i++) {
                set.add(compact.substring(i, i + n));
            }
            return set;
        }

        // 有空格：按 token 做 n-gram（更适合英文/有分词的文本）
        String[] tokens = t.split("\\s+");
        if (tokens.length == 0) {
            return set;
        }
        if (tokens.length < n) {
            set.add(String.join(" ", tokens));
            return set;
        }
        for (int i = 0; i <= tokens.length - n; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j++) {
                if (j > 0) sb.append(' ');
                sb.append(tokens[i + j]);
            }
            set.add(sb.toString());
        }
        return set;
    }

    /**
     * Jaccard 相似度：|A∩B| / |A∪B|，结果范围 0..100
     * 对中文更稳：使用字符 3-gram；对英文/有空格文本：使用 token 3-gram
     */
    public static double similarityJaccardNGram(String a, String b, int n) {
        Set<String> sa = shingles(a, n);
        Set<String> sb = shingles(b, n);

        if (sa.isEmpty() && sb.isEmpty()) {
            return 0.0;
        }

        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);

        Set<String> intersection = new HashSet<>(sa);
        intersection.retainAll(sb);

        if (union.isEmpty()) {
            return 0.0;
        }
        return intersection.size() * 100.0 / union.size();
    }

    private static int intersectionSize(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> tmp = new HashSet<>(a);
        tmp.retainAll(b);
        return tmp.size();
    }

    // TF-IDF 相似度计算（改用余弦相似度，适合两文档比较）
    public static double calculateTFIDFSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.trim().isEmpty() || text2.trim().isEmpty()) {
            return 0.0;
        }

        // 使用较轻的标准化，保留更多有效词汇
        String norm1 = normalizeForTFIDF(text1);
        String norm2 = normalizeForTFIDF(text2);

        Map<String, Integer> tf1 = calculateTermFrequency(norm1);
        Map<String, Integer> tf2 = calculateTermFrequency(norm2);

        // 找出共同词汇
        Set<String> commonTerms = new HashSet<>(tf1.keySet());
        commonTerms.retainAll(tf2.keySet());

        if (commonTerms.isEmpty()) {
            return 0.0;
        }

        // 使用余弦相似度（基于词频向量）
        Set<String> allTerms = new HashSet<>(tf1.keySet());
        allTerms.addAll(tf2.keySet());

        double dotProduct = 0.0;
        double norm1Squared = 0.0;
        double norm2Squared = 0.0;

        int totalWords1 = tf1.values().stream().mapToInt(Integer::intValue).sum();
        int totalWords2 = tf2.values().stream().mapToInt(Integer::intValue).sum();

        for (String term : allTerms) {
            // 使用标准化词频作为向量分量
            double freq1 = tf1.getOrDefault(term, 0) / (double) totalWords1;
            double freq2 = tf2.getOrDefault(term, 0) / (double) totalWords2;

            dotProduct += freq1 * freq2;
            norm1Squared += freq1 * freq1;
            norm2Squared += freq2 * freq2;
        }

        double denominator = Math.sqrt(norm1Squared) * Math.sqrt(norm2Squared);

        if (denominator == 0.0) {
            return 0.0;
        }

        double similarity = (dotProduct / denominator) * 100.0;
        return Math.max(0.0, Math.min(100.0, similarity));
    }

    // 专门用于TF-IDF的文本标准化（针对中文优化）
    private static String normalizeForTFIDF(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 基础清理
        String processed = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\r\\n]+", " ") // 换行替换为空格
                .replaceAll("[\\p{Punct}&&[^\\u4e00-\\u9fa5]]", " ") // 去除非中文标点
                .replaceAll("\\s+", " ") // 多个空格合并
                .trim();

        // 判断是否为中文为主的文本（空格很少）
        SimilarityConfig config = SimilarityConfig.getInstance();
        boolean isChineseText = processed.indexOf(' ') < 0 ||
                               (processed.length() - processed.replace(" ", "").length()) < processed.length() * config.tfidfChineseTextSpaceRatio;

        if (isChineseText) {
            // 中文文本：使用字符级处理，生成字符二元组作为“词”
            processed = processed.replace(" ", ""); // 去掉空格
            StringBuilder result = new StringBuilder();

            // 生成字符二元组
            for (int i = 0; i < processed.length() - 1; i++) {
                if (i > 0) result.append(" ");
                result.append(processed.substring(i, i + 2));
            }

            // 如果文本太短，也加入单字符
            if (processed.length() <= config.tfidfShortTextLength) {
                for (int i = 0; i < processed.length(); i++) {
                    result.append(" ").append(processed.charAt(i));
                }
            }

            return result.toString().trim();
        } else {
            // 英文或有空格的文本：按词处理
            Set<String> minimalStopWords = Set.of("the", "and", "or", "of", "to", "a", "an", "is", "are", "in", "on", "at");
            return Arrays.stream(processed.split("\\s+"))
                    .filter(word -> word.length() > config.tfidfMinWordLength)
                    .filter(word -> !minimalStopWords.contains(word))
                    .collect(Collectors.joining(" "));
        }
    }

    private static Map<String, Integer> calculateTermFrequency(String text) {
        Map<String, Integer> tf = new HashMap<>();
        if (text == null || text.trim().isEmpty()) {
            return tf;
        }

        String[] words = text.trim().split("\\s+");
        for (String word : words) {
            if (!word.isEmpty() && word.length() > 0) {
                tf.put(word, tf.getOrDefault(word, 0) + 1);
            }
        }
        return tf;
    }
    //这个方法目前没有任何地方用到。
    private static double calculateTFIDF(String term, Map<String, Integer> tf, List<String> corpus) {
        int termFreq = tf.getOrDefault(term, 0);
        if (termFreq == 0) {
            return 0.0;
        }

        // 计算文档频率（包含该词的文档数）
        long docCount = corpus.stream()
                .mapToLong(doc -> doc.contains(term) ? 1 : 0)
                .sum();

        if (docCount == 0) {
            return 0.0;
        }

        // 计算TF（词频标准化）
        int totalWords = tf.values().stream().mapToInt(Integer::intValue).sum();
        double tfScore = totalWords > 0 ? (double) termFreq / totalWords : 0.0;

        // 计算IDF（逆文档频率）
        double idf = Math.log((double) corpus.size() / docCount);

        return tfScore * idf;
    }


    // 新的TF-IDF计算方法，使用词频映射而不是原始文本
    //这个方法目前没有任何地方用到。
    private static double calculateTFIDFWithTFMaps(String term, Map<String, Integer> tf, List<Map<String, Integer>> tfCorpus) {
        int termFreq = tf.getOrDefault(term, 0);
        if (termFreq == 0) {
            return 0.0;
        }

        // 计算文档频率（有多少个文档包含该词）
        long docCount = tfCorpus.stream()
                .mapToLong(tfMap -> tfMap.containsKey(term) ? 1 : 0)
                .sum();

        if (docCount == 0) {
            return 0.0;
        }

        // 计算TF（词频标准化）
        int totalWords = tf.values().stream().mapToInt(Integer::intValue).sum();
        double tfScore = totalWords > 0 ? (double) termFreq / totalWords : 0.0;

        // 计算IDF（逆文档频率）
        double idf = Math.log((double) tfCorpus.size() / docCount);

        return tfScore * idf;
    }

    // 计算结构相似度（基于段落数量、表格数量等）
    public static double calculateStructuralSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return 0.0;
        }

        // 段落数量相似度
        int paragraphs1 = text1.split("\\n+").length;
        int paragraphs2 = text2.split("\\n+").length;
        double paragraphSim = 1.0 - Math.abs(paragraphs1 - paragraphs2) / (double) Math.max(paragraphs1, paragraphs2);

        // 文档长度相似度
        int len1 = text1.length();
        int len2 = text2.length();
        double lengthSim = 1.0 - Math.abs(len1 - len2) / (double) Math.max(len1, len2);

        // 词汇密度相似度
        double density1 = approxTokenCountForSimilarity(text1) / (double) Math.max(1, len1);
        double density2 = approxTokenCountForSimilarity(text2) / (double) Math.max(1, len2);
        double densitySim = 1.0 - Math.abs(density1 - density2) / Math.max(density1, density2);

        SimilarityConfig config = SimilarityConfig.getInstance();
        return (paragraphSim * config.structuralWeightParagraph + 
                lengthSim * config.structuralWeightLength + 
                densitySim * config.structuralWeightDensity) * 100.0;
    }

    // 增强的综合相似度算法
    public static double enhancedSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.trim().isEmpty() || text2.trim().isEmpty()) {
            return 0.0;
        }

        // 使用配置文件中的权重分配
        SimilarityConfig config = SimilarityConfig.getInstance();
        
        // 1. 词汇重叠相似度
        double lexicalSim = Math.max(
                similarityJaccardNGram(text1, text2, config.ngramSize2),
                similarityJaccardNGram(text1, text2, config.ngramSize3)
        ) * config.weightLexical;

        // 2. TF-IDF语义相似度
        double tfidfSim = calculateTFIDFSimilarity(text1, text2) * config.weightSemantic;

        // 3. 结构相似度
        double structuralSim = calculateStructuralSimilarity(text1, text2) * config.weightStructural;

        double totalSim = lexicalSim + tfidfSim + structuralSim;


        System.out.println(String.format("[DEBUG] 词汇相似度: %.2f%% (权重%.0f%%), TF-IDF相似度: %.2f%% (权重%.0f%%), 结构相似度: %.2f%% (权重%.0f%%)",
                    lexicalSim / config.weightLexical, config.weightLexical * 100,
                    tfidfSim / config.weightSemantic, config.weightSemantic * 100,
                    structuralSim / config.weightStructural, config.weightStructural * 100));


        return Math.min(100.0, totalSim);
    }

    // 动态阈值判定
    public static String getSimilarityLevel(double similarity, int docLength) {
        // 根据文档长度调整判定标准
        SimilarityConfig config = SimilarityConfig.getInstance();
        double highThreshold = docLength > config.docLengthLargeThreshold ? config.levelHighLarge : 
                              docLength > config.docLengthMediumThreshold ? config.levelHighMedium : config.levelHighSmall;
        double mediumThreshold = highThreshold * config.similarityLevelMediumRatio;

        if (similarity >= highThreshold) {
            return "高度相似 (可能存在抄袭)";
        } else if (similarity >= mediumThreshold) {
            return "中等相似 (需要进一步检查)";
        } else {
            return "相似度较低 (正常范围)";
        }
    }

    // ========== 段落匹配相关类 ==========
    
    // 段落信息
    public static class Paragraph {
        public final int index;
        public final String text;
        public final int startPos;
        public final int endPos;

        public Paragraph(int index, String text, int startPos, int endPos) {
            this.index = index;
            this.text = text;
            this.startPos = startPos;
            this.endPos = endPos;
        }

        public int length() {
            return text == null ? 0 : text.length();
        }
    }

    // 子串匹配结果（段落内的连续相似片段）
    public static class SubstringMatch {
        public final String substring;
        public final int startPos1;  // 在段落1中的起始位置
        public final int startPos2;  // 在段落2中的起始位置
        public final int length;

        public SubstringMatch(String substring, int startPos1, int startPos2) {
            this.substring = substring;
            this.startPos1 = startPos1;
            this.startPos2 = startPos2;
            this.length = substring == null ? 0 : substring.length();
        }

        @Override
        public String toString() {
            return String.format("    [子串匹配] 长度: %d字符, 位置1: %d, 位置2: %d\n      内容: %s",
                    length, startPos1, startPos2, preview200(substring));
        }
    }

    // 段落匹配结果
    public static class ParagraphMatch {
        public final Paragraph para1;
        public final Paragraph para2;
        public final double similarity;
        public final List<SubstringMatch> substringMatches;  // 段落内的子串匹配

        public ParagraphMatch(Paragraph para1, Paragraph para2, double similarity) {
            this.para1 = para1;
            this.para2 = para2;
            this.similarity = similarity;
            this.substringMatches = new ArrayList<>();
        }

        public void addSubstringMatch(SubstringMatch match) {
            this.substringMatches.add(match);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("文件1段落#%d ↔ 文件2段落#%d  相似度: %.2f%%\n",
                    para1.index, para2.index, similarity));
            sb.append(String.format("  [文件1] %s\n", preview200(para1.text)));
            sb.append(String.format("  [文件2] %s", preview200(para2.text)));
            
            if (!substringMatches.isEmpty()) {
                sb.append(String.format("\n  发现 %d 处连续相似片段(≥%d字):", 
                    substringMatches.size(), SimilarityConfig.getInstance().substringMinLength));
                for (SubstringMatch sm : substringMatches) {
                    sb.append("\n").append(sm);
                }
            }
            return sb.toString();
        }
    }

    // 段落匹配报告
    public static class ParagraphMatchingReport {
        public final List<ParagraphMatch> matches;
        public final int totalParagraphs1;
        public final int totalParagraphs2;
        public final double threshold;

        public ParagraphMatchingReport(List<ParagraphMatch> matches, int total1, int total2, double threshold) {
            this.matches = matches;
            this.totalParagraphs1 = total1;
            this.totalParagraphs2 = total2;
            this.threshold = threshold;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========== 段落匹配报告 ==========\n");
            sb.append(String.format("文件1段落数: %d, 文件2段落数: %d\n", totalParagraphs1, totalParagraphs2));
            sb.append(String.format("相似度阈值: %.1f%%\n", threshold));
            sb.append(String.format("发现 %d 处高度相似段落:\n", matches.size()));
            
            // 统计子串匹配总数和最长子串
            int totalSubstrings = 0;
            int maxSubstringLength = 0;
            for (ParagraphMatch match : matches) {
                totalSubstrings += match.substringMatches.size();
                for (SubstringMatch sm : match.substringMatches) {
                    maxSubstringLength = Math.max(maxSubstringLength, sm.length);
                }
            }
            if (totalSubstrings > 0) {
                sb.append(String.format("其中包含 %d 处连续相似片段(≥%d字), 最长片段: %d 字符\n", 
                    totalSubstrings, SimilarityConfig.getInstance().substringMinLength, 
                    maxSubstringLength));
            }
            sb.append("\n");

            if (matches.isEmpty()) {
                sb.append("未发现超过阈值的相似段落。\n");
            } else {
                for (int i = 0; i < matches.size(); i++) {
                    sb.append(String.format("[%d] ", i + 1)).append(matches.get(i)).append("\n\n");
                }
            }
            sb.append("=================================\n");
            return sb.toString();
        }
    }

    // ========== 段落匹配相关类结束 ==========

    // 权重化的文档相似度（针对不同部分设置权重）
    public static class DocumentSimilarityResult {
        public final double overallSimilarity;
        public final double lexicalSimilarity;
        public final double semanticSimilarity;
        public final double structuralSimilarity;
        public final String level;
        public final String analysis;

        public DocumentSimilarityResult(double overall, double lexical, double semantic, double structural, String level, String analysis) {
            this.overallSimilarity = overall;
            this.lexicalSimilarity = lexical;
            this.semanticSimilarity = semantic;
            this.structuralSimilarity = structural;
            this.level = level;
            this.analysis = analysis;
        }

        @Override
        public String toString() {
            return String.format("综合相似度: %.2f%% [%s]\n" +
                            "- 词汇相似度: %.2f%%\n" +
                            "- 语义相似度: %.2f%%\n" +
                            "- 结构相似度: %.2f%%\n" +
                            "分析: %s",
                    overallSimilarity, level, lexicalSimilarity, semanticSimilarity, structuralSimilarity, analysis);
        }
    }

    public static DocumentSimilarityResult analyzeDocumentSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.trim().isEmpty() || text2.trim().isEmpty()) {
            return new DocumentSimilarityResult(0.0, 0.0, 0.0, 0.0, "无法比较", "文档为空或无效");
        }

        SimilarityConfig config = SimilarityConfig.getInstance();
        double lexical = Math.max(similarityJaccardNGram(text1, text2, config.ngramSize2), 
                                  similarityJaccardNGram(text1, text2, config.ngramSize3));
        double semantic = calculateTFIDFSimilarity(text1, text2);
        double structural = calculateStructuralSimilarity(text1, text2);
        // 使用配置文件中的权重
        double overall = lexical * config.weightLexical + semantic * config.weightSemantic + structural * config.weightStructural;

        int docLength = Math.max(text1.length(), text2.length());
        String level = getSimilarityLevel(overall, docLength);

        StringBuilder analysis = new StringBuilder();
        if (lexical > config.analysisLexicalVeryHigh) analysis.append("词汇重叠度很高; ");
        if (semantic > config.analysisSemanticVeryHigh) analysis.append("语义相似度很高; ");
        else if (semantic < config.analysisSemanticVeryLow) analysis.append("语义差异较大; ");
        if (structural > config.analysisStructuralAlmostSame) analysis.append("结构几乎相同; ");
        else if (structural > config.analysisStructuralVeryHigh) analysis.append("结构高度相似; ");

        if (overall > config.analysisOverallPlagiarism) analysis.append("疑似抄袭");
        else if (overall > config.analysisOverallSimilar) analysis.append("存在相似内容");
        else if (overall > config.analysisOverallSomeSimilarity) analysis.append("有一定相似性");
        else analysis.append("相似度在正常范围内");

        return new DocumentSimilarityResult(overall, lexical, semantic, structural, level, analysis.toString());
    }

    // 读取 Word (.docx) 文本（段落 + 表格）
    public static String readWord(File file) {
        if (isUnreadableOrEmptyOfficeFile(file)) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {

            // 普通段落
            for (XWPFParagraph para : doc.getParagraphs()) {
                String p = para.getText();
                if (p != null && !p.isBlank()) {
                    text.append(p).append("\n");
                }
            }

            // 表格（很多招标文件内容都在表格里）
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph para : cell.getParagraphs()) {
                            String p = para.getText();
                            if (p != null && !p.isBlank()) {
                                text.append(p).append("\n");
                            }
                        }
                    }
                }
            }

        } catch (org.apache.poi.EmptyFileException e) {
            LOGGER.log(Level.WARNING, "Word file is empty: " + file, e);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read Word document: " + file, e);
        }
        return text.toString();
    }

    // 读取 PDF 文本（自动判断是否为扫描件并调用 OCR）
    public static String readPDF(File file) {
        if (isUnreadableOrEmptyOfficeFile(file)) {
            return "";
        }
        
        try {
            // 1. 尝试直接提取 PDF 文本
            PdfTextExtractor.PdfExtractionResult result = PdfTextExtractor.extract(file.toPath());
            
            // 2. 判断是否为扫描件
            if (result.isScannedPdf()) {
                System.out.println("\n========================================");
                System.out.println("[PDF识别] 检测到扫描件 PDF，启动 OCR 识别流程");
                System.out.println("========================================");
                
                // 3. 检查 OCR 服务是否可用
                if (!OcrServiceFactory.isServiceAvailable()) {
                    System.err.println("[OCR错误] OCR 服务不可用");
                    SimilarityConfig cfg = SimilarityConfig.getInstance();
                    if ("aliyun".equalsIgnoreCase(cfg.ocrType)) {
                        System.err.println("[OCR错误] 阿里云 OCR 配置不完整，请检查 config.properties 中的配置");
                    } else {
                        System.err.println("[OCR错误] 本地 OCR 服务不可用 (http://localhost:5001)");
                        System.err.println("[OCR错误] 请先启动 OCR 服务: python ocr-service/run_easyocr_service.py");
                    }
                    System.err.println("[OCR错误] 返回直接提取的文本（可能为空或不完整）");
                    return result.getText() == null ? "" : result.getText();
                }
                
                try {
                    // 4. 调用 OCR 服务识别（自动根据配置选择本地或阿里云）
                    System.out.println("[OCR识别] 正在发送 PDF 到 OCR 服务...");
                    OcrServiceClient.OcrResult ocrResult = OcrServiceFactory.recognizePdf(file);
                    
                    if (ocrResult.success && ocrResult.hasText()) {
                        System.out.println(String.format(
                            "[OCR识别] ✓ 成功识别 %d 页，共 %d 个文本块，文本长度: %d 字符",
                            ocrResult.pageCount, ocrResult.textCount, ocrResult.fullText.length()));
                        System.out.println("========================================\n");
                        
                        // 返回 OCR 识别的文本
                        return ocrResult.fullText;
                    } else {
                        System.err.println("[OCR错误] OCR 识别失败: " + ocrResult.error);
                        System.err.println("[OCR错误] 返回直接提取的文本（可能为空或不完整）");
                        return result.getText() == null ? "" : result.getText();
                    }
                    
                } catch (IOException e) {
                    System.err.println("[OCR错误] OCR 服务调用失败: " + e.getMessage());
                    LOGGER.log(Level.WARNING, "OCR service call failed, falling back to direct extraction", e);
                    return result.getText() == null ? "" : result.getText();
                }
                
            } else {
                // 不是扫描件，直接使用提取的文本
                System.out.println("[PDF识别] 使用直接提取的文本（非扫描件）");
                String text = result.getText();
                System.out.println(text);
                return text == null ? "" : text;
            }
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read PDF document: " + file, e);
            return "";
        }
    }

    // 读取 Excel (.xlsx) 文本
    public static String readExcel(File file) {
        if (isUnreadableOrEmptyOfficeFile(file)) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        text.append(cell.toString()).append("\t");
                    }
                    text.append("\n");
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read Excel document: " + file, e);
        }
        return text.toString();
    }

    private static int approxTokenCountForSimilarity(String s) {
        String t = normalizeForSimilarity(s);
        if (t.isEmpty()) {
            return 0;
        }
        // 中文/无空格：按字符计
        if (t.indexOf(' ') < 0) {
            return t.length();
        }
        // 有空格：按空格分词计
        return t.split("\\s+").length;
    }

    /**
     * 将文本分割为段落列表
     * 策略：按单换行切分，过滤过短段落（<30字符）
     */
    public static List<Paragraph> splitIntoParagraphs(String text) {
        List<Paragraph> paragraphs = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return paragraphs;
        }

        // 改用单换行切分，因为PDF提取的文本通常只有单个\n
        String[] lines = text.split("\\n");
        int currentPos = 0;
        int index = 0;
        int filteredCount = 0;
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() >= SimilarityConfig.getInstance().paragraphMinLength) {
                int start = currentPos;
                int end = start + trimmed.length();
                paragraphs.add(new Paragraph(index++, trimmed, start, end));
            } else if (!trimmed.isEmpty()) {
                filteredCount++;
            }
            currentPos += line.length() + 1;
        }

        System.out.println("[段落切分] 原始行数: " + lines.length + ", 有效段落: " + paragraphs.size() + ", 过滤掉: " + filteredCount);

        return paragraphs;
    }

    /**
     * 在两个段落内查找连续100字以上的相同子串
     * @param text1 段落1文本
     * @param text2 段落2文本
     * @param minLength 最小匹配长度（默认100）
     * @return 子串匹配列表
     */
    public static List<SubstringMatch> findCommonSubstrings(String text1, String text2, int minLength) {
        List<SubstringMatch> matches = new ArrayList<>();
        if (text1 == null || text2 == null || text1.length() < minLength || text2.length() < minLength) {
            return matches;
        }

        // 使用动态规划找出所有公共子串
        int len1 = text1.length();
        int len2 = text2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];
        
        // 用于存储找到的所有子串（位置和长度）
        List<int[]> foundSubstrings = new ArrayList<>();  // [pos1, pos2, length]
        
        // 动态规划：计算最长公共子串
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    
                    // 如果当前找到的公共子串长度达到minLength
                    if (dp[i][j] >= minLength) {
                        // 检查是否是子串的结束位置（下一个字符不匹配或到达边界）
                        if (i == len1 || j == len2 || text1.charAt(i) != text2.charAt(j)) {
                            int length = dp[i][j];
                            int startPos1 = i - length;
                            int startPos2 = j - length;
                            foundSubstrings.add(new int[]{startPos1, startPos2, length});
                        }
                    }
                } else {
                    dp[i][j] = 0;
                }
            }
        }
        
        // 过滤重叠的子串，保留最长的
        foundSubstrings.sort((a, b) -> Integer.compare(b[2], a[2]));  // 按长度降序
        
        boolean[] used1 = new boolean[len1];
        boolean[] used2 = new boolean[len2];
        
        for (int[] substr : foundSubstrings) {
            int pos1 = substr[0];
            int pos2 = substr[1];
            int length = substr[2];
            
            // 检查是否与已有的匹配重叠
            boolean overlap = false;
            for (int k = 0; k < length; k++) {
                if (used1[pos1 + k] || used2[pos2 + k]) {
                    overlap = true;
                    break;
                }
            }
            
            if (!overlap) {
                String substring = text1.substring(pos1, pos1 + length);
                matches.add(new SubstringMatch(substring, pos1, pos2));
                
                // 标记已使用
                for (int k = 0; k < length; k++) {
                    used1[pos1 + k] = true;
                    used2[pos2 + k] = true;
                }
            }
        }
        
        // 按位置排序
        matches.sort((a, b) -> Integer.compare(a.startPos1, b.startPos1));
        
        return matches;
    }

    /**
     * 将归一化文本中的位置映射回原始文本位置
     * //这个方法目前没有任何地方用到。
     */
    private static int mapNormalizedPosToOriginal(String original, String normalized, int normPos) {
        if (normPos <= 0) return 0;
        if (normPos >= normalized.length()) return original.length();
        
        int origPos = 0;
        int normCount = 0;
        
        for (int i = 0; i < original.length() && normCount < normPos; i++) {
            char c = original.charAt(i);
            // 模拟normalizeForSimilarity的转换规则
            if (!Character.isWhitespace(c) || c == ' ') {
                normCount++;
            }
            origPos = i + 1;
        }
        
        return Math.min(origPos, original.length());
    }

    /**
     * 段落级相似度匹配：找出所有高度相似的段落对
     * @param text1 文档1文本
     * @param text2 文档2文本
     * @param threshold 相似度阈值（0-100），默认建议70
     * @return 段落匹配报告
     */
    public static ParagraphMatchingReport matchParagraphs(String text1, String text2, double threshold) {
        List<Paragraph> paras1 = splitIntoParagraphs(text1);
        List<Paragraph> paras2 = splitIntoParagraphs(text2);
        List<ParagraphMatch> matches = new ArrayList<>();

        System.out.println("[段落匹配] 文件1: " + paras1.size() + " 段, 文件2: " + paras2.size() + " 段");
        System.out.println("[段落匹配] 阈值: " + threshold + "%, 预计比对次数: " + (paras1.size() * paras2.size()));

        int comparedCount = 0;
        int highSimCount = 0;

        for (Paragraph p1 : paras1) {
            for (Paragraph p2 : paras2) {
                comparedCount++;
                double sim = similarityJaccardNGram(p1.text, p2.text, 3);
                if (sim >= threshold) {
                    highSimCount++;
                    ParagraphMatch match = new ParagraphMatch(p1, p2, sim);
                    
                    // 查找段落内的连续相同片段
                    List<SubstringMatch> subMatches = findCommonSubstrings(p1.text, p2.text, 
                        SimilarityConfig.getInstance().substringMinLength);
                    for (SubstringMatch sm : subMatches) {
                        match.addSubstringMatch(sm);
                    }
                    
                    matches.add(match);
                    if (matches.size() <= SimilarityConfig.getInstance().debugMatchPrintLimit) {
                        System.out.println(String.format("  [匹配#%d] 段落%d ↔ 段落%d, 相似度: %.2f%%, 子串匹配: %d处",
                                matches.size(), p1.index, p2.index, sim, subMatches.size()));
                    }
                }
            }
        }

        System.out.println("[段落匹配] 实际比对: " + comparedCount + " 次, 找到: " + highSimCount + " 个高相似段落");

        // 按相似度降序排列
        matches.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        return new ParagraphMatchingReport(matches, paras1.size(), paras2.size(), threshold);
    }
    
    /**
     * 在整个文档中查找跨段落的连续100字以上相同片段
     * 用于发现即使段落不完全匹配，但有长片段重复的情况
     */
    public static List<SubstringMatch> findCrossDocumentSubstrings(String text1, String text2, int minLength) {
        return findCommonSubstrings(text1, text2, minLength);
    }

    /**
     * 对比两个文本内容，返回详细对比结果字符串
     * 
     * @param text1 文本1
     * @param text2 文本2
     * @return 对比结果字符串
     */
    public static String compareTexts(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return "文本内容为空！";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("[文本1长度] ").append(text1.length()).append(" 字符\n");
        sb.append("[文本2长度] ").append(text2.length()).append(" 字符\n\n");

        Set<String> s1_2 = shingles(text1, 2);
        Set<String> s2_2 = shingles(text2, 2);
        Set<String> s1_3 = shingles(text1, 3);
        Set<String> s2_3 = shingles(text2, 3);
        sb.append("shingle(2) size1=").append(s1_2.size()).append(" size2=").append(s2_2.size())
          .append(" intersection=").append(intersectionSize(s1_2, s2_2)).append("\n");
        sb.append("shingle(3) size1=").append(s1_3.size()).append(" size2=").append(s2_3.size())
          .append(" intersection=").append(intersectionSize(s1_3, s2_3)).append("\n\n");

        double sim2 = similarityJaccardNGram(text1, text2, 2);
        double sim3 = similarityJaccardNGram(text1, text2, 3);
        double originalSim = Math.max(sim2, sim3);
        double enhancedSim = enhancedSimilarity(text1, text2);
        DocumentSimilarityResult detailedResult = analyzeDocumentSimilarity(text1, text2);
        double tfidfOnly = calculateTFIDFSimilarity(text1, text2);
        double structuralOnly = calculateStructuralSimilarity(text1, text2);

        sb.append("=== 原始算法结果 ===\n");
        sb.append("相似度(2-gram): ").append(String.format("%.2f", sim2)).append("%\n");
        sb.append("相似度(3-gram): ").append(String.format("%.2f", sim3)).append("%\n");
        sb.append("相似度(原始推荐): ").append(String.format("%.2f", originalSim)).append("%\n\n");

        sb.append("=== 增强算法结果 ===\n");
        sb.append("增强相似度: ").append(String.format("%.2f", enhancedSim)).append("%\n\n");
        sb.append(detailedResult).append("\n\n");

        sb.append("[DEBUG] 单项测试:\n");
        sb.append("TF-IDF相似度: ").append(String.format("%.2f", tfidfOnly)).append("%\n");
        sb.append("结构相似度: ").append(String.format("%.2f", structuralOnly)).append("%\n\n");

        // 段落匹配
        ParagraphMatchingReport paraReport = matchParagraphs(text1, text2, 
            SimilarityConfig.getInstance().paragraphSimilarityThreshold);
        sb.append(paraReport);

        // 全文长片段查重（无论分段如何，只要连续N字及以上相同就报告）
        int minLength = SimilarityConfig.getInstance().substringMinLength;
        sb.append(String.format("\n========== 全文长片段重复检测（≥%d字） ==========\n", minLength));
        List<SubstringMatch> longMatches = findCrossDocumentSubstrings(text1, text2, minLength);
        if (longMatches.isEmpty()) {
            sb.append(String.format("未发现连续%d字及以上的完全重复片段。\n", minLength));
        } else {
            sb.append(String.format("发现 %d 处连续重复片段：\n\n", longMatches.size()));
            for (int i = 0; i < longMatches.size(); i++) {
                SubstringMatch sm = longMatches.get(i);
                sb.append(String.format("[片段#%d] 长度: %d字符\n", i + 1, sm.length));
                sb.append(String.format("  文本1位置: %d, 文本2位置: %d\n", sm.startPos1, sm.startPos2));
                sb.append(String.format("  内容预览: %s\n\n", preview200(sm.substring)));
            }
        }
        sb.append("=================================\n");

        return sb.toString();
    }

    /**
     * 对比两个文件内容，返回详细对比结果字符串（支持 docx、pdf、xlsx）
     */
    public static String compareFiles(File file1, File file2) {
        if (file1 == null || file2 == null) {
            return "文件未选择！";
        }
        String text1 = "", text2 = "";
        String name1 = file1.getName().toLowerCase();
        String name2 = file2.getName().toLowerCase();
        try {
            if (name1.endsWith(".docx")) {
                text1 = readWord(file1);
            } else if (name1.endsWith(".pdf")) {
                text1 = readPDF(file1);
                debugPrintPdfContent(file1, text1);
            } else if (name1.endsWith(".xlsx")) {
                text1 = readExcel(file1);
            } else if (name1.endsWith(".txt")) {
                text1 = new String(java.nio.file.Files.readAllBytes(file1.toPath()));
            } else {
                return "暂不支持的文件类型: " + name1;
            }
            if (name2.endsWith(".docx")) {
                text2 = readWord(file2);
            } else if (name2.endsWith(".pdf")) {
                text2 = readPDF(file2);
                debugPrintPdfContent(file2, text2);
            } else if (name2.endsWith(".xlsx")) {
                text2 = readExcel(file2);
            } else if (name2.endsWith(".txt")) {
                text2 = new String(java.nio.file.Files.readAllBytes(file2.toPath()));
            } else {
                return "暂不支持的文件类型: " + name2;
            }
        } catch (Exception e) {
            return "读取文件出错: " + e.getMessage();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[文件1] ").append(file1.getName()).append("\n");
        sb.append("[文件2] ").append(file2.getName()).append("\n");
        sb.append("[内容长度] ").append(text1.length()).append(" vs ").append(text2.length()).append("\n\n");

        Set<String> s1_2 = shingles(text1, 2);
        Set<String> s2_2 = shingles(text2, 2);
        Set<String> s1_3 = shingles(text1, 3);
        Set<String> s2_3 = shingles(text2, 3);
        sb.append("shingle(2) size1=").append(s1_2.size()).append(" size2=").append(s2_2.size())
          .append(" intersection=").append(intersectionSize(s1_2, s2_2)).append("\n");
        sb.append("shingle(3) size1=").append(s1_3.size()).append(" size2=").append(s2_3.size())
          .append(" intersection=").append(intersectionSize(s1_3, s2_3)).append("\n\n");

        double sim2 = similarityJaccardNGram(text1, text2, 2);
        double sim3 = similarityJaccardNGram(text1, text2, 3);
        double originalSim = Math.max(sim2, sim3);
        double enhancedSim = enhancedSimilarity(text1, text2);
        DocumentSimilarityResult detailedResult = analyzeDocumentSimilarity(text1, text2);
        double tfidfOnly = calculateTFIDFSimilarity(text1, text2);
        double structuralOnly = calculateStructuralSimilarity(text1, text2);

        sb.append("=== 原始算法结果 ===\n");
        sb.append("相似度(2-gram): ").append(String.format("%.2f", sim2)).append("%\n");
        sb.append("相似度(3-gram): ").append(String.format("%.2f", sim3)).append("%\n");
        sb.append("相似度(原始推荐): ").append(String.format("%.2f", originalSim)).append("%\n\n");

        sb.append("=== 增强算法结果 ===\n");
        sb.append("增强相似度: ").append(String.format("%.2f", enhancedSim)).append("%\n\n");
        sb.append(detailedResult).append("\n\n");

        sb.append("[DEBUG] 单项测试:\n");
        sb.append("TF-IDF相似度: ").append(String.format("%.2f", tfidfOnly)).append("%\n");
        sb.append("结构相似度: ").append(String.format("%.2f", structuralOnly)).append("%\n\n");

        // 段落匹配
        ParagraphMatchingReport paraReport = matchParagraphs(text1, text2, 
            SimilarityConfig.getInstance().paragraphSimilarityThreshold);
        sb.append(paraReport);

        // 全文长片段查重（无论分段如何，只要连续N字及以上相同就报告）
        int minLength = SimilarityConfig.getInstance().substringMinLength;
        sb.append(String.format("\n========== 全文长片段重复检测（≥%d字） ==========\n", minLength));
        List<SubstringMatch> longMatches = findCrossDocumentSubstrings(text1, text2, minLength);
        if (longMatches.isEmpty()) {
            sb.append(String.format("未发现连续%d字及以上的完全重复片段。\n", minLength));
        } else {
            sb.append(String.format("发现 %d 处连续重复片段：\n\n", longMatches.size()));
            for (int i = 0; i < longMatches.size(); i++) {
                SubstringMatch sm = longMatches.get(i);
                sb.append(String.format("[片段#%d] 长度: %d字符\n", i + 1, sm.length));
                sb.append(String.format("  文件1位置: %d, 文件2位置: %d\n", sm.startPos1, sm.startPos2));
                sb.append(String.format("  内容预览: %s\n\n", preview200(sm.substring)));
            }
        }
        sb.append("=================================\n");

        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println(">>> 代码版本: 2026-02-03 v5 已重新编译 <<<");
        System.out.println("========================================");

        // 测试文件路径（你改成本地文件）
        File file1 = new File("test1.pdf");
        File file2 = new File("test2.pdf");

        String text1 = readPDF(file1);
        String text2 = readPDF(file2);

        System.out.println("[DEBUG] file1=" + file1.getAbsolutePath() + " size=" + (file1.exists() ? file1.length() : -1));
        System.out.println("[DEBUG] file2=" + file2.getAbsolutePath() + " size=" + (file2.exists() ? file2.length() : -1));
        System.out.println("[DEBUG] text1 chars=" + text1.length() + ", approxTokens=" + approxTokenCountForSimilarity(text1));
        System.out.println("[DEBUG] text2 chars=" + text2.length() + ", approxTokens=" + approxTokenCountForSimilarity(text2));
//        System.out.println("[DEBUG] text1 preview: " + preview200(text1));
//        System.out.println("[DEBUG] text2 preview: " + preview200(text2));


        Set<String> s1_2 = shingles(text1, 2);
        Set<String> s2_2 = shingles(text2, 2);
        Set<String> s1_3 = shingles(text1, 3);
        Set<String> s2_3 = shingles(text2, 3);
        System.out.println("[DEBUG] shingle(2) size1=" + s1_2.size() + " size2=" + s2_2.size() + " intersection=" + intersectionSize(s1_2, s2_2));
        System.out.println("[DEBUG] shingle(3) size1=" + s1_3.size() + " size2=" + s2_3.size() + " intersection=" + intersectionSize(s1_3, s2_3));

        // 原始算法结果
        double sim2 = similarityJaccardNGram(text1, text2, 2);
        double sim3 = similarityJaccardNGram(text1, text2, 3);
        double originalSim = Math.max(sim2, sim3);

        // 增强算法结果
        double enhancedSim = enhancedSimilarity(text1, text2);
        DocumentSimilarityResult detailedResult = analyzeDocumentSimilarity(text1, text2);

        System.out.println("=== 原始算法结果 ===");
        System.out.println("相似度(2-gram): " + String.format("%.2f", sim2) + "%");
        System.out.println("相似度(3-gram): " + String.format("%.2f", sim3) + "%");
        System.out.println("相似度(原始推荐): " + String.format("%.2f", originalSim) + "%");

        System.out.println("\n=== 增强算法结果 ===");
        System.out.println("增强相似度: " + String.format("%.2f", enhancedSim) + "%");
        System.out.println();
        System.out.println(detailedResult);

        // TF-IDF 单独测试
        double tfidfOnly = calculateTFIDFSimilarity(text1, text2);
        double structuralOnly = calculateStructuralSimilarity(text1, text2);
        System.out.println("\n[DEBUG] 单项测试:");
        System.out.println("TF-IDF相似度: " + String.format("%.2f", tfidfOnly) + "%");
        System.out.println("结构相似度: " + String.format("%.2f", structuralOnly) + "%");



        // 让 IDE 知道这两个工具方法是有用的（未来会用到），避免“未使用”波浪线
        if (false) {
            System.out.println(readPDF(new File("dummy.pdf")).length());
            System.out.println(readExcel(new File("dummy.xlsx")).length());
        }
    }
}
