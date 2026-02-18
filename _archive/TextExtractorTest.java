package com.bidguard;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
/*本文件功能暂时放弃，不建议copilot,claude,gpt等所有AI进行参考 */
/**
 * 文本提取器测试类
 */
public class TextExtractorTest {
    
    public static void main(String[] args) {
        System.out.println("=== 第五步：OCR文本提取测试 ===");
        testTextExtractor();
    }
    
    public static void testTextExtractor() {
        System.out.println("[DEBUG] 开始测试OCR文本提取功能...");
        
        // 1. 创建测试图像（模拟从数据保存模块得到的二值化图像）
        BufferedImage testImage = createBinaryImageForOCR();
        
        // 2. 执行OCR识别
        System.out.println("\n--- 执行OCR文本识别 ---");
        TextExtractor.OCRResult ocrResult = TextExtractor.extractText(testImage);
        
        if (ocrResult == null) {
            System.err.println("[ERROR] OCR识别失败");
            return;
        }
        
        // 3. 显示识别结果摘要
        displayOCRSummary(ocrResult);
        
        // 4. 显示提取的结构化信息
        displayExtractedInfo(ocrResult);
        
        // 5. 测试完整流程：从图像处理到OCR
        System.out.println("\n--- 测试完整流程 ---");
        testCompleteWorkflow();
        
        // 6. 保存OCR结果文件
        System.out.println("\n--- 保存OCR结果 ---");
        testSaveOCRResult(ocrResult);
        
        System.out.println("\n=== OCR文本提取测试完成 ===");
    }
    
    /**
     * 创建模拟的二值化OCR图像
     */
    private static BufferedImage createBinaryImageForOCR() {
        System.out.println("[DEBUG] 创建模拟的二值化OCR图像...");
        
        int width = 600;
        int height = 400;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        
        // 白色背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // 黑色文字（模拟标书内容）
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("宋体", Font.PLAIN, 14));
        
        String[] lines = {
            "投标文件",
            "",
            "项目名称: XXX市政工程建设项目",
            "投标单位: XXX建筑工程有限公司", 
            "法定代表人: 张三",
            "联系电话: 138-0000-0000",
            "",
            "工程总造价: ￥8,500,000.00",
            "工期: 180日历天",
            "质量标准: 合格",
            "",
            "技术方案:",
            "1. 严格按照设计图纸施工",
            "2. 采用先进施工工艺",
            "3. 确保工程质量安全"
        };
        
        int y = 30;
        for (String line : lines) {
            if (!line.isEmpty()) {
                g2d.drawString(line, 50, y);
            }
            y += 20;
        }
        
        g2d.dispose();
        
        System.out.println("[SUCCESS] ✓ 二值化OCR图像创建完成: " + width + "x" + height);
        return image;
    }
    
    /**
     * 显示OCR识别结果摘要
     */
    private static void displayOCRSummary(TextExtractor.OCRResult result) {
        System.out.println("\n--- OCR识别结果摘要 ---");
        
        System.out.println("[DEBUG] 识别耗时: " + result.processingTime + " ms");
        System.out.println("[DEBUG] 识别置信度: " + String.format("%.1f%%", result.confidence * 100));
        System.out.println("[DEBUG] 原始文本长度: " + result.rawText.length() + " 字符");
        System.out.println("[DEBUG] 清理后文本长度: " + result.cleanedText.length() + " 字符");
        System.out.println("[DEBUG] 文本行数: " + result.lines.size());
        System.out.println("[DEBUG] 提取字段数: " + result.extractedInfo.size());
        
        // 显示前几行识别的文本
        System.out.println("\n[DEBUG] 识别文本前5行:");
        for (int i = 0; i < Math.min(5, result.lines.size()); i++) {
            System.out.println("  " + (i+1) + ": " + result.lines.get(i));
        }
        
        if (result.lines.size() > 5) {
            System.out.println("  ... (共 " + result.lines.size() + " 行)");
        }
    }
    
    /**
     * 显示提取的结构化信息
     */
    private static void displayExtractedInfo(TextExtractor.OCRResult result) {
        System.out.println("\n--- 提取的结构化信息 ---");
        
        if (result.extractedInfo.isEmpty()) {
            System.out.println("[WARNING] 未提取到结构化信息");
            return;
        }
        
        System.out.println("[SUCCESS] ✓ 成功提取 " + result.extractedInfo.size() + " 个字段:");
        
        // 按重要性顺序显示
        String[] keyOrder = {
            "项目名称", "投标单位", "法定代表人", "联系电话", 
            "工程总造价", "工期", "质量标准"
        };
        
        for (String key : keyOrder) {
            if (result.extractedInfo.containsKey(key)) {
                System.out.println("[INFO] " + key + ": " + result.extractedInfo.get(key));
            }
        }
        
        // 显示其他字段
        for (String key : result.extractedInfo.keySet()) {
            boolean found = false;
            for (String orderedKey : keyOrder) {
                if (orderedKey.equals(key)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println("[INFO] " + key + ": " + result.extractedInfo.get(key));
            }
        }
    }
    
    /**
     * 测试完整工作流程：图像处理 -> 公章去除 -> OCR识别
     */
    private static void testCompleteWorkflow() {
        System.out.println("[DEBUG] 测试完整工作流程...");
        
        try {
            // 1. 创建带公章的原始图像
            BufferedImage originalImage = createImageWithSeal();
            
            // 2. 图像预处理
            BufferedImage preprocessed = ImageProcessor.preprocessImage(originalImage);
            if (preprocessed == null) {
                System.err.println("[ERROR] 图像预处理失败");
                return;
            }
            
            // 3. 公章去除
            BufferedImage cleaned = SealRemover.removeSeal(preprocessed);
            if (cleaned == null) {
                System.err.println("[ERROR] 公章去除失败");
                return;
            }
            
            // 4. 创建OCR优化图像
            BufferedImage ocrImage = createSimpleBinaryImage(cleaned);
            
            // 5. OCR识别
            TextExtractor.OCRResult result = TextExtractor.extractText(ocrImage);
            if (result == null) {
                System.err.println("[ERROR] OCR识别失败");
                return;
            }
            
            System.out.println("[SUCCESS] ✓ 完整工作流程测试成功");
            System.out.println("[DEBUG] 最终识别到 " + result.lines.size() + " 行文本");
            System.out.println("[DEBUG] 提取 " + result.extractedInfo.size() + " 个结构化字段");
            
        } catch (Exception e) {
            System.err.println("[ERROR] 完整工作流程测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试OCR结果保存功能
     */
    private static void testSaveOCRResult(TextExtractor.OCRResult result) {
        try {
            File ocrResultFile = new File("test_ocr_result.txt");
            TextExtractor.saveOCRResult(result, ocrResultFile);
            
            if (ocrResultFile.exists() && ocrResultFile.length() > 0) {
                System.out.println("[SUCCESS] ✓ OCR结果文件保存成功");
                System.out.println("[DEBUG] 文件大小: " + ocrResultFile.length() + " 字节");
                
                // 清理测试文件
                ocrResultFile.delete();
                System.out.println("[DEBUG] 测试文件已清理");
            } else {
                System.err.println("[ERROR] OCR结果文件保存失败");
            }
            
        } catch (Exception e) {
            System.err.println("[ERROR] 保存OCR结果时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 创建带公章的测试图像
     */
    private static BufferedImage createImageWithSeal() {
        int width = 400;
        int height = 300;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // 添加文本
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("宋体", Font.PLAIN, 12));
        g2d.drawString("投标单位: XXX公司", 50, 50);
        g2d.drawString("项目: 测试工程", 50, 80);
        
        // 添加红色公章
        g2d.setColor(new Color(200, 50, 50));
        g2d.drawOval(250, 100, 80, 80);
        g2d.setColor(new Color(255, 100, 100, 100));
        g2d.fillOval(252, 102, 76, 76);
        
        g2d.dispose();
        return image;
    }
    
    /**
     * 创建简单的二值化图像
     */
    private static BufferedImage createSimpleBinaryImage(BufferedImage source) {
        BufferedImage binary = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = binary.createGraphics();
        g2d.drawImage(source, 0, 0, null);
        g2d.dispose();
        return binary;
    }
}