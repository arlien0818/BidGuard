package com.bidguard;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
/*本文件功能暂时放弃，不建议copilot,claude,gpt等所有AI进行参考 */
/**
 * 处理后数据存储测试类
 */
public class ProcessedDataStorageTest {
    
    public static void main(String[] args) {
        System.out.println("=== 第四步：数据保存模块测试 ===");
        testDataStorage();
    }
    
    public static void testDataStorage() {
        System.out.println("[DEBUG] 开始测试数据保存功能...");
        
        // 1. 创建测试图像
        BufferedImage testImage = createTestImageWithSeal();
        
        // 2. 设置输出目录和文件前缀
        File outputDir = new File("test_output");
        String fileNamePrefix = "bidguard_test_" + System.currentTimeMillis();
        
        System.out.println("[DEBUG] 输出目录: " + outputDir.getAbsolutePath());
        System.out.println("[DEBUG] 文件前缀: " + fileNamePrefix);
        
        // 3. 执行完整的处理和保存流程
        System.out.println("\n--- 执行完整处理流程 ---");
        ProcessedDataStorage.ProcessedData result = ProcessedDataStorage.processAndSave(
            testImage, outputDir, fileNamePrefix
        );
        
        if (result == null) {
            System.err.println("[ERROR] 数据处理和保存失败");
            return;
        }
        
        // 4. 验证生成的文件
        System.out.println("\n--- 验证生成的文件 ---");
        validateGeneratedFiles(outputDir, fileNamePrefix);
        
        // 5. 显示处理结果统计
        displayProcessingStatistics(result);
        
        // 6. 清理测试文件
        System.out.println("\n--- 清理测试文件 ---");
        cleanupTestFiles(outputDir);
        
        System.out.println("\n=== 数据保存测试完成 ===");
    }
    
    /**
     * 创建带公章的测试图像
     */
    private static BufferedImage createTestImageWithSeal() {
        System.out.println("[DEBUG] 创建带公章的测试图像...");
        
        int width = 600;
        int height = 800;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 白色背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // 添加文档内容
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("宋体", Font.PLAIN, 14));
        
        String[] documentContent = {
            "投 标 文 件",
            "",
            "项目名称: XXX市政工程建设项目",
            "投标单位: XXX建筑工程有限公司",
            "法定代表人: 张三",
            "联系电话: 138-0000-0000",
            "传真号码: 010-12345678",
            "电子邮箱: info@example.com",
            "",
            "一、企业基本情况",
            "我公司成立于2010年，注册资金5000万元，",
            "具有建筑工程施工总承包壹级资质，市政公用",
            "工程施工总承包贰级资质。",
            "",
            "二、投标报价",
            "工程总造价: ￥8,500,000.00",
            "(大写: 捌佰伍拾万元整)",
            "工期: 180日历天",
            "质量标准: 合格",
            "",
            "三、技术方案简述",
            "1. 严格按照设计图纸和技术规范施工",
            "2. 采用先进的施工工艺和设备",  
            "3. 建立完善的质量管理体系",
            "4. 确保工程质量和施工安全",
            "",
            "四、承诺事项",
            "我司承诺严格履行合同义务，按时保质",
            "完成工程建设任务。如有违约，愿承担",
            "相应的法律责任和经济损失。",
            "",
            "此致",
            "XXX市建设工程招标办公室"
        };
        
        int y = 50;
        for (String line : documentContent) {
            g2d.drawString(line, 50, y);
            y += 20;
        }
        
        // 添加红色公章
        g2d.setColor(new Color(220, 50, 50));
        g2d.setStroke(new BasicStroke(3));
        
        // 公章1: 企业公章
        int seal1X = 400;
        int seal1Y = 200;
        int seal1Size = 100;
        g2d.drawOval(seal1X, seal1Y, seal1Size, seal1Size);
        g2d.setColor(new Color(255, 100, 100, 120));
        g2d.fillOval(seal1X + 2, seal1Y + 2, seal1Size - 4, seal1Size - 4);
        
        g2d.setColor(new Color(200, 40, 40));
        g2d.setFont(new Font("宋体", Font.BOLD, 12));
        g2d.drawString("XXX建筑", seal1X + 20, seal1Y + 45);
        g2d.drawString("有限公司", seal1X + 20, seal1Y + 60);
        
        // 公章2: 法人章
        int seal2X = 450;
        int seal2Y = 650;
        int seal2Size = 60;
        g2d.setColor(new Color(200, 60, 60));
        g2d.drawOval(seal2X, seal2Y, seal2Size, seal2Size);
        g2d.setColor(new Color(250, 120, 120, 100));
        g2d.fillOval(seal2X + 2, seal2Y + 2, seal2Size - 4, seal2Size - 4);
        
        g2d.setColor(new Color(180, 50, 50));
        g2d.setFont(new Font("宋体", Font.BOLD, 10));
        g2d.drawString("张三", seal2X + 18, seal2Y + 35);
        
        g2d.dispose();
        
        System.out.println("[SUCCESS] ✓ 测试图像创建完成: " + width + "x" + height);
        return image;
    }
    
    /**
     * 验证生成的文件是否存在和完整
     */
    private static void validateGeneratedFiles(File outputDir, String fileNamePrefix) {
        System.out.println("[DEBUG] 验证生成的文件...");
        
        String[] expectedFiles = {
            fileNamePrefix + "_cleaned.png",
            fileNamePrefix + "_binary_ocr.png", 
            fileNamePrefix + "_metadata.txt",
            fileNamePrefix + "_processing_report.txt"
        };
        
        boolean allFilesExist = true;
        
        for (String fileName : expectedFiles) {
            File file = new File(outputDir, fileName);
            if (file.exists() && file.length() > 0) {
                System.out.println("[SUCCESS] ✓ " + fileName + " (大小: " + file.length() + " 字节)");
            } else {
                System.err.println("[ERROR] ✗ " + fileName + " 文件缺失或为空");
                allFilesExist = false;
            }
        }
        
        if (allFilesExist) {
            System.out.println("[SUCCESS] ✓ 所有预期文件都已成功生成");
        } else {
            System.err.println("[ERROR] 部分文件生成失败");
        }
    }
    
    /**
     * 显示处理结果统计信息
     */
    private static void displayProcessingStatistics(ProcessedDataStorage.ProcessedData data) {
        System.out.println("\n--- 处理结果统计 ---");
        
        System.out.println("[DEBUG] 处理耗时: " + data.processingTime + " ms");
        System.out.println("[DEBUG] 原图尺寸: " + data.metadata.get("originalWidth") + "x" + data.metadata.get("originalHeight"));
        System.out.println("[DEBUG] 清理后尺寸: " + data.metadata.get("cleanedWidth") + "x" + data.metadata.get("cleanedHeight"));
        
        System.out.println("[DEBUG] 原图红色像素: " + data.metadata.get("originalRedPixels"));
        System.out.println("[DEBUG] 清理后红色像素: " + data.metadata.get("cleanedRedPixels"));
        System.out.println("[SUCCESS] ✓ 公章去除率: " + data.metadata.get("sealRemovalRate"));
        
        System.out.println("[DEBUG] 平均亮度: " + String.format("%.1f", (Double)data.metadata.get("averageBrightness")));
        System.out.println("[DEBUG] 图像对比度: " + String.format("%.1f", (Double)data.metadata.get("contrast")));
        
        // 验证OCR优化图像质量
        if (data.binarizedImage != null) {
            System.out.println("[SUCCESS] ✓ OCR优化二值化图像生成成功");
            System.out.println("[DEBUG] 二值化图像尺寸: " + data.binarizedImage.getWidth() + "x" + data.binarizedImage.getHeight());
        } else {
            System.err.println("[ERROR] OCR优化图像生成失败");
        }
    }
    
    /**
     * 清理测试文件
     */
    private static void cleanupTestFiles(File outputDir) {
        System.out.println("[DEBUG] 清理测试文件...");
        
        if (outputDir.exists() && outputDir.isDirectory()) {
            File[] files = outputDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        file.delete();
                        System.out.println("[DEBUG] 已删除: " + file.getName());
                    }
                }
            }
            outputDir.delete();
            System.out.println("[DEBUG] 已删除目录: " + outputDir.getName());
        }
        
        System.out.println("[SUCCESS] ✓ 测试文件清理完成");
    }
}