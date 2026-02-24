package com.bidguard.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 配置管理类 - 从 config.properties 文件加载参数
 * 配置文件使用 UTF-8 编码
 * 修改配置文件后重启程序即可生效，无需重新编译
 */
public class SimilarityConfig {
    private static final Logger LOGGER = Logger.getLogger(SimilarityConfig.class.getName());
    private static final String CONFIG_FILE = "config.properties";
    private static final String LOCAL_CONFIG_FILE = "local.properties";
    private static SimilarityConfig instance;
    private Properties properties;
    
    // ========== 版本号 ========== 
    public final String version;
    // ========== 段落分割参数 ========== 
    public final int paragraphMinLength;
    
    // ========== 子串匹配参数 ==========
    public final int substringMinLength;
    
    // ========== 段落相似度参数 ==========
    public final double paragraphSimilarityThreshold;
    
    // ========== 综合相似度权重 ==========
    public final double weightLexical;
    public final double weightSemantic;
    public final double weightStructural;
    
    // ========== 相似度等级判定阈值 ==========
    public final double levelHighLarge;
    public final double levelHighMedium;
    public final double levelHighSmall;
    
    // ========== N-gram 参数 ==========
    public final int ngramSize2;
    public final int ngramSize3;
    
    // ========== 文本标准化参数 ==========
    public final double tfidfChineseTextSpaceRatio;
    public final int tfidfShortTextLength;
    public final int tfidfMinWordLength;
    
    // ========== 结构相似度权重分配 ==========
    public final double structuralWeightParagraph;
    public final double structuralWeightLength;
    public final double structuralWeightDensity;
    
    // ========== 文档长度分级阈值 ==========
    public final int docLengthLargeThreshold;
    public final int docLengthMediumThreshold;
    
    // ========== 相似度判定阈值 ==========
    public final double similarityLevelMediumRatio;
    
    // ========== 文档分析阈值 ==========
    public final double analysisLexicalVeryHigh;
    public final double analysisSemanticVeryHigh;
    public final double analysisSemanticVeryLow;
    public final double analysisStructuralAlmostSame;
    public final double analysisStructuralVeryHigh;
    public final double analysisOverallPlagiarism;
    public final double analysisOverallSimilar;
    public final double analysisOverallSomeSimilarity;
    
    // ========== 调试输出参数 ==========
    public final int debugPdfPrintLines;
    public final int debugMatchPrintLimit;
    
    // ========== 其他参数 ==========
    public final int previewMaxChars;
    public final boolean debugPrintPdfContent;
    
    // ========== OCR 图片压缩参数 ==========
    public final int ocrImageMaxDimension;
    public final float ocrJpegQuality;
    
    // ========== OCR 渲染参数 ==========
    public final int ocrRenderDpi;           // PDF渲染为图片的DPI（影响识别精度）
    public final boolean ocrRemoveSealEnabled; // 是否在OCR识别前去除红章（预留）
    
    // ========== OCR 服务配置 ==========
    public final String ocrType;  // local 或 aliyun
    public final String ocrLocalUrl;
    public final String ocrAliyunAccessKeyId;
    public final String ocrAliyunAccessKeySecret;
    public final String ocrAliyunEndpoint;
    
    private SimilarityConfig() {
        properties = new Properties();
        loadConfig();
        version = getStringProperty("version", "2.12");
        // 先读取 ocr.type，以决定是否需要加载本地未提交的密钥文件
        String ocrTypeTemp = getStringProperty("ocr.type", "local");
        if ("aliyun".equalsIgnoreCase(ocrTypeTemp)) {
            loadLocalConfig();
        }

        // 加载配置，如果失败则使用默认值
        paragraphMinLength = getIntProperty("paragraph.min.length", 30);
        substringMinLength = getIntProperty("substring.min.length", 100);
        paragraphSimilarityThreshold = getDoubleProperty("paragraph.similarity.threshold", 70.0);
        
        weightLexical = getDoubleProperty("similarity.weight.lexical", 0.4);
        weightSemantic = getDoubleProperty("similarity.weight.semantic", 0.45);
        weightStructural = getDoubleProperty("similarity.weight.structural", 0.15);
        
        levelHighLarge = getDoubleProperty("similarity.level.high.large", 75.0);
        levelHighMedium = getDoubleProperty("similarity.level.high.medium", 70.0);
        levelHighSmall = getDoubleProperty("similarity.level.high.small", 65.0);
        
        ngramSize2 = getIntProperty("ngram.size.2", 2);
        ngramSize3 = getIntProperty("ngram.size.3", 3);
        
        // 文本标准化参数
        tfidfChineseTextSpaceRatio = getDoubleProperty("tfidf.chinese.text.space.ratio", 0.1);
        tfidfShortTextLength = getIntProperty("tfidf.short.text.length", 3);
        tfidfMinWordLength = getIntProperty("tfidf.min.word.length", 1);
        
        // 结构相似度权重分配
        structuralWeightParagraph = getDoubleProperty("structural.weight.paragraph", 0.4);
        structuralWeightLength = getDoubleProperty("structural.weight.length", 0.3);
        structuralWeightDensity = getDoubleProperty("structural.weight.density", 0.3);
        
        // 文档长度分级阈值
        docLengthLargeThreshold = getIntProperty("doc.length.large.threshold", 10000);
        docLengthMediumThreshold = getIntProperty("doc.length.medium.threshold", 5000);
        
        // 相似度判定阈值
        similarityLevelMediumRatio = getDoubleProperty("similarity.level.medium.ratio", 0.6);
        
        // 文档分析阈值
        analysisLexicalVeryHigh = getDoubleProperty("analysis.lexical.very.high", 80.0);
        analysisSemanticVeryHigh = getDoubleProperty("analysis.semantic.very.high", 70.0);
        analysisSemanticVeryLow = getDoubleProperty("analysis.semantic.very.low", 10.0);
        analysisStructuralAlmostSame = getDoubleProperty("analysis.structural.almost.same", 95.0);
        analysisStructuralVeryHigh = getDoubleProperty("analysis.structural.very.high", 90.0);
        analysisOverallPlagiarism = getDoubleProperty("analysis.overall.plagiarism", 85.0);
        analysisOverallSimilar = getDoubleProperty("analysis.overall.similar", 60.0);
        analysisOverallSomeSimilarity = getDoubleProperty("analysis.overall.some.similarity", 30.0);
        
        // 调试输出参数
        debugPdfPrintLines = getIntProperty("debug.pdf.print.lines", 20);
        debugMatchPrintLimit = getIntProperty("debug.match.print.limit", 5);
        
        previewMaxChars = getIntProperty("preview.max.chars", 200);
        debugPrintPdfContent = getBooleanProperty("debug.print.pdf.content", false);
        
        ocrImageMaxDimension = getIntProperty("ocr.image.max.dimension", 800);
        ocrJpegQuality = (float) getDoubleProperty("ocr.jpeg.quality", 0.85);
        
        // OCR 渲染参数
        ocrRenderDpi = getIntProperty("ocr.render.dpi", 200);
        ocrRemoveSealEnabled = getBooleanProperty("ocr.remove.seal.enabled", false);
        
        // OCR 服务配置
        ocrType = getStringProperty("ocr.type", "local");
        ocrLocalUrl = getStringProperty("ocr.local.url", "http://localhost:5001/ocr");
        ocrAliyunAccessKeyId = getStringProperty("ocr.aliyun.access.key.id", "");
        ocrAliyunAccessKeySecret = getStringProperty("ocr.aliyun.access.key.secret", "");
        ocrAliyunEndpoint = getStringProperty("ocr.aliyun.endpoint", "ocr-api.cn-hangzhou.aliyuncs.com");
        
        // 验证权重之和
        double weightSum = weightLexical + weightSemantic + weightStructural;
        if (Math.abs(weightSum - 1.0) > 0.01) {
            LOGGER.warning(String.format(
                "权重之和不等于1.0 (当前: %.3f)，请检查配置文件！将自动归一化。", weightSum));
        }
        
        logConfig();
    }
    
    public static synchronized SimilarityConfig getInstance() {
        if (instance == null) {
            instance = new SimilarityConfig();
        }
        return instance;
    }
    
    /**
     * 重新加载配置文件（热重载）
     */
    public static synchronized void reload() {
        LOGGER.info("重新加载配置文件...");
        instance = null;
        getInstance();
    }
    
    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        
        // 尝试从当前目录加载
        if (!configFile.exists()) {
            // 尝试从jar同级目录加载
            try {
                String jarPath = SimilarityConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
                File jarFile = new File(jarPath);
                if (jarFile.isFile()) {
                    configFile = new File(jarFile.getParent(), CONFIG_FILE);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "无法确定jar路径", e);
            }
        }
        
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile);
                 InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                properties.load(isr);
                LOGGER.info("成功加载配置文件 (UTF-8): " + configFile.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "读取配置文件失败，将使用默认值: " + e.getMessage(), e);
            }
        } else {
            LOGGER.warning("配置文件不存在: " + configFile.getAbsolutePath() + "，将使用默认值");
        }
    }

    /**
     * 从本地未提交的 local.properties 加载敏感配置（例如阿里云 AccessKey）
     * 当 ocr.type=aliyun 时，local.properties 必须存在且包含 ocr.aliyun.access.key.id 与 ocr.aliyun.access.key.secret
     */
    private void loadLocalConfig() {
        File localFile = new File(LOCAL_CONFIG_FILE);

        if (!localFile.exists()) {
            // 尝试从jar同级目录加载
            try {
                String jarPath = SimilarityConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
                File jarFile = new File(jarPath);
                if (jarFile.isFile()) {
                    localFile = new File(jarFile.getParent(), LOCAL_CONFIG_FILE);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "无法确定jar路径", e);
            }
        }

        if (!localFile.exists()) {
            throw new IllegalStateException("本地配置文件 local.properties 未找到；请在本地创建该文件并添加 ocr.aliyun.access.key.id 和 ocr.aliyun.access.key.secret，然后重启程序。");
        }

        try (FileInputStream fis = new FileInputStream(localFile);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            Properties localProps = new Properties();
            localProps.load(isr);
            // 仅覆盖阿里云相关密钥
            String id = localProps.getProperty("ocr.aliyun.access.key.id");
            String secret = localProps.getProperty("ocr.aliyun.access.key.secret");
            if (id != null && !id.trim().isEmpty()) {
                properties.setProperty("ocr.aliyun.access.key.id", id.trim());
            }
            if (secret != null && !secret.trim().isEmpty()) {
                properties.setProperty("ocr.aliyun.access.key.secret", secret.trim());
            }
            LOGGER.info("成功加载本地密钥配置: " + localFile.getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("读取 local.properties 失败: " + e.getMessage(), e);
        }
    }
    
    private int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning(String.format("配置项 %s 值无效: %s，使用默认值: %d", key, value, defaultValue));
            return defaultValue;
        }
    }
    
    private double getDoubleProperty(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning(String.format("配置项 %s 值无效: %s，使用默认值: %.2f", key, value, defaultValue));
            return defaultValue;
        }
    }
    
    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
    
    private String getStringProperty(String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
    
    private void logConfig() {
        LOGGER.info("========== 当前配置 ==========");
        LOGGER.info(String.format("段落最小长度: %d 字符", paragraphMinLength));
        LOGGER.info(String.format("子串最小长度: %d 字符", substringMinLength));
        LOGGER.info(String.format("段落相似度阈值: %.1f%%", paragraphSimilarityThreshold));
        LOGGER.info(String.format("权重 - 词汇: %.2f, 语义: %.2f, 结构: %.2f", 
            weightLexical, weightSemantic, weightStructural));
        LOGGER.info(String.format("相似度等级阈值 - 大: %.1f%%, 中: %.1f%%, 小: %.1f%%",
            levelHighLarge, levelHighMedium, levelHighSmall));
        LOGGER.info("============================");
    }
}
