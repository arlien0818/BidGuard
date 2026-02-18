package com.bidguard;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/*本文件功能暂时放弃，不建议copilot,claude,gpt等所有AI进行参考 */

/**
 * 文本提取器 - 对去除公章后的图像进行OCR识别和文本提取
 */
public class TextExtractor {
    
    /**
     * OCR识别结果数据结构
     */
    public static class OCRResult {
        public String rawText;                    // 原始识别文本
        public String cleanedText;               // 清理后的文本
        public List<String> lines;               // 按行分割的文本
        public Map<String, String> extractedInfo; // 提取的结构化信息
        public double confidence;                // 识别置信度
        public long processingTime;              // 处理时间
        
        public OCRResult() {
            this.lines = new ArrayList<>();
            this.extractedInfo = new HashMap<>();
        }
    }
    
    /**
     * 对图像进行OCR识别（使用Java内置方法模拟）
     * @param image 输入图像（最好是二值化的OCR优化图像）
     * @return OCR识别结果
     */
    public static OCRResult extractText(BufferedImage image) {
        System.out.println("[DEBUG] TextExtractor: 开始OCR文本识别...");
        System.out.println("[DEBUG] 输入图像尺寸: " + image.getWidth() + "x" + image.getHeight());
        
        long startTime = System.currentTimeMillis();
        OCRResult result = new OCRResult();
        
        try {
            // 由于没有真实的Tesseract，我们使用模拟的OCR结果
            // 在实际项目中，这里会调用真实的OCR引擎
            result = simulateOCRRecognition(image);
            
            // 清理和处理识别结果
            result.cleanedText = cleanAndNormalizeText(result.rawText);
            result.lines = splitIntoLines(result.cleanedText);
            
            // 提取结构化信息
            extractStructuredInfo(result);
            
            result.processingTime = System.currentTimeMillis() - startTime;
            
            System.out.println("[SUCCESS] ✓ OCR识别完成");
            System.out.println("[DEBUG] 识别耗时: " + result.processingTime + " ms");
            System.out.println("[DEBUG] 识别文本长度: " + result.cleanedText.length() + " 字符");
            System.out.println("[DEBUG] 识别行数: " + result.lines.size());
            System.out.println("[DEBUG] 识别置信度: " + String.format("%.1f%%", result.confidence * 100));
            
            return result;
            
        } catch (Exception e) {
            System.err.println("[ERROR] OCR识别过程中发生异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 模拟OCR识别过程（在没有真实OCR引擎时使用）
     * 实际项目中会替换为真实的Tesseract调用
     */
    private static OCRResult simulateOCRRecognition(BufferedImage image) {
        System.out.println("[DEBUG] 模拟OCR识别过程...");
        
        OCRResult result = new OCRResult();
        
        // 分析图像特征来模拟OCR结果
        int blackPixels = countBlackPixels(image);
        double textDensity = (double) blackPixels / (image.getWidth() * image.getHeight());
        
        System.out.println("[DEBUG] 图像黑色像素: " + blackPixels);
        System.out.println("[DEBUG] 文本密度: " + String.format("%.3f", textDensity));
        
        // 模拟标书内容的OCR识别结果
        StringBuilder ocrText = new StringBuilder();
        ocrText.append("投 标 文 件\n\n");
        ocrText.append("项目名称: XXX市政工程建设项目\n");
        ocrText.append("投标单位: XXX建筑工程有限公司\n");
        ocrText.append("法定代表人: 张三\n");
        ocrText.append("联系电话: 138-0000-0000\n");
        ocrText.append("传真号码: 010-12345678\n");
        ocrText.append("电子邮箱: info@example.com\n\n");
        
        ocrText.append("一、企业基本情况\n");
        ocrText.append("我公司成立于2010年，注册资金5000万元，\n");
        ocrText.append("具有建筑工程施工总承包壹级资质，市政公用\n");
        ocrText.append("工程施工总承包贰级资质。\n\n");
        
        ocrText.append("二、投标报价\n");
        ocrText.append("工程总造价: ￥8,500,000.00\n");
        ocrText.append("(大写: 捌佰伍拾万元整)\n");
        ocrText.append("工期: 180日历天\n");
        ocrText.append("质量标准: 合格\n\n");
        
        ocrText.append("三、技术方案简述\n");
        ocrText.append("1. 严格按照设计图纸和技术规范施工\n");
        ocrText.append("2. 采用先进的施工工艺和设备\n");
        ocrText.append("3. 建立完善的质量管理体系\n");
        ocrText.append("4. 确保工程质量和施工安全\n\n");
        
        ocrText.append("四、承诺事项\n");
        ocrText.append("我司承诺严格履行合同义务，按时保质\n");
        ocrText.append("完成工程建设任务。如有违约，愿承担\n");
        ocrText.append("相应的法律责任和经济损失。\n\n");
        
        ocrText.append("此致\n");
        ocrText.append("XXX市建设工程招标办公室\n");
        
        result.rawText = ocrText.toString();
        
        // 基于图像质量模拟置信度
        if (textDensity > 0.05) {
            result.confidence = 0.95; // 高质量
        } else if (textDensity > 0.03) {
            result.confidence = 0.85; // 中等质量
        } else {
            result.confidence = 0.75; // 较低质量
        }
        
        System.out.println("[SUCCESS] ✓ 模拟OCR识别完成");
        return result;
    }
    
    /**
     * 清理和标准化识别出的文本
     */
    private static String cleanAndNormalizeText(String rawText) {
        System.out.println("[DEBUG] 清理和标准化文本...");
        
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }
        
        String cleaned = rawText;
        
        // 1. 移除多余的空白字符
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        
        // 2. 标准化换行符
        cleaned = cleaned.replaceAll("\\r\\n", "\n");
        cleaned = cleaned.replaceAll("\\r", "\n");
        
        // 3. 移除多余的空行
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        
        // 4. 修正常见的OCR识别错误
        cleaned = fixCommonOCRErrors(cleaned);
        
        // 5. 去除首尾空白
        cleaned = cleaned.trim();
        
        System.out.println("[SUCCESS] ✓ 文本清理完成");
        System.out.println("[DEBUG] 原始长度: " + rawText.length() + " -> 清理后长度: " + cleaned.length());
        
        return cleaned;
    }
    
    /**
     * 修正常见的OCR识别错误
     */
    private static String fixCommonOCRErrors(String text) {
        Map<String, String> corrections = new HashMap<>();
        
        // 常见数字识别错误
        corrections.put("O", "0");  // 字母O -> 数字0
        corrections.put("l", "1");  // 小写l -> 数字1
        corrections.put("I", "1");  // 大写I -> 数字1
        
        // 常见标点符号错误
        corrections.put("，", ",");   // 中文逗号 -> 英文逗号
        corrections.put("。", ".");   // 中文句号 -> 英文句号
        corrections.put("：", ":");   // 中文冒号 -> 英文冒号
        
        // 应用修正
        String corrected = text;
        for (Map.Entry<String, String> entry : corrections.entrySet()) {
            // 只在特定上下文中修正，避免误改
            if (shouldApplyCorrection(corrected, entry.getKey())) {
                corrected = corrected.replace(entry.getKey(), entry.getValue());
            }
        }
        
        return corrected;
    }
    
    /**
     * 判断是否应该应用OCR错误修正
     */
    private static boolean shouldApplyCorrection(String text, String errorChar) {
        // 简单的上下文判断逻辑
        // 实际项目中可以使用更复杂的NLP技术
        
        if (errorChar.equals("O") && text.contains("电话") && text.contains("-")) {
            return true; // 电话号码中的O可能是0
        }
        
        if (errorChar.equals("l") && Pattern.compile("\\d+l\\d+").matcher(text).find()) {
            return true; // 数字中的l可能是1
        }
        
        return false;
    }
    
    /**
     * 将文本按行分割
     */
    private static List<String> splitIntoLines(String text) {
        List<String> lines = new ArrayList<>();
        
        String[] rawLines = text.split("\n");
        for (String line : rawLines) {
            line = line.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        
        return lines;
    }
    
    /**
     * 从文本中提取结构化信息
     */
    private static void extractStructuredInfo(OCRResult result) {
        System.out.println("[DEBUG] 提取结构化信息...");
        
        Map<String, String> info = result.extractedInfo;
        String text = result.cleanedText;
        
        // 提取项目名称
        String projectName = extractWithPattern(text, "项目名称[:\\s]*([^\\n]+)");
        if (projectName != null) {
            info.put("项目名称", projectName.trim());
        }
        
        // 提取投标单位
        String bidder = extractWithPattern(text, "投标单位[:\\s]*([^\\n]+)");
        if (bidder != null) {
            info.put("投标单位", bidder.trim());
        }
        
        // 提取法定代表人
        String legalRep = extractWithPattern(text, "法定代表人[:\\s]*([^\\n]+)");
        if (legalRep != null) {
            info.put("法定代表人", legalRep.trim());
        }
        
        // 提取联系电话
        String phone = extractWithPattern(text, "联系电话[:\\s]*([^\\n]+)");
        if (phone != null) {
            info.put("联系电话", phone.trim());
        }
        
        // 提取工程造价
        String cost = extractWithPattern(text, "工程总造价[:\\s]*([^\\n]+)");
        if (cost != null) {
            info.put("工程总造价", cost.trim());
        }
        
        // 提取工期
        String duration = extractWithPattern(text, "工期[:\\s]*([^\\n]+)");
        if (duration != null) {
            info.put("工期", duration.trim());
        }
        
        // 提取质量标准
        String quality = extractWithPattern(text, "质量标准[:\\s]*([^\\n]+)");
        if (quality != null) {
            info.put("质量标准", quality.trim());
        }
        
        System.out.println("[SUCCESS] ✓ 结构化信息提取完成，共提取 " + info.size() + " 个字段");
        
        // 显示提取的信息
        for (Map.Entry<String, String> entry : info.entrySet()) {
            System.out.println("[DEBUG] " + entry.getKey() + ": " + entry.getValue());
        }
    }
    
    /**
     * 使用正则表达式提取信息
     */
    private static String extractWithPattern(String text, String pattern) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(text);
        
        if (m.find()) {
            return m.group(1);
        }
        
        return null;
    }
    
    /**
     * 保存OCR结果到文件
     */
    public static void saveOCRResult(OCRResult result, File outputFile) throws IOException {
        System.out.println("[DEBUG] 保存OCR结果到文件: " + outputFile.getName());
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("=== OCR文本识别结果 ===");
            writer.println("识别时间: " + new Date());
            writer.println("处理耗时: " + result.processingTime + " ms");
            writer.println("识别置信度: " + String.format("%.1f%%", result.confidence * 100));
            writer.println("文本行数: " + result.lines.size());
            writer.println();
            
            writer.println("--- 提取的结构化信息 ---");
            for (Map.Entry<String, String> entry : result.extractedInfo.entrySet()) {
                writer.println(entry.getKey() + ": " + entry.getValue());
            }
            writer.println();
            
            writer.println("--- 完整识别文本 ---");
            writer.println(result.cleanedText);
        }
        
        System.out.println("[SUCCESS] ✓ OCR结果已保存");
    }
    
    // 工具方法
    private static int countBlackPixels(BufferedImage image) {
        int blackPixels = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF; // 取红色分量作为灰度值
                if (gray < 128) { // 暗色像素认为是文本
                    blackPixels++;
                }
            }
        }
        
        return blackPixels;
    }
}