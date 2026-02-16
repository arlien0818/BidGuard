package com.bidguard;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 图像处理器测试类
 */
public class ImageProcessorTest {
    
    public static void main(String[] args) {
        System.out.println("=== 第二步：图像读取和预处理测试 ===");
        testImageProcessor();
    }
    
    public static void testImageProcessor() {
        System.out.println("[DEBUG] 开始测试图像处理器...");
        
        // 1. 创建模拟的A4页面图像（包含红色公章）
        BufferedImage testImage = createTestA4Image();
        
        // 2. 保存测试图像
        File testFile = new File("test_a4_page.png");
        boolean saved = ImageProcessor.saveImage(testImage, testFile, "PNG");
        
        if (!saved) {
            System.err.println("[ERROR] 无法保存测试图像");
            return;
        }
        
        // 3. 测试图像读取
        System.out.println("\n--- 测试图像读取功能 ---");
        BufferedImage readImage = ImageProcessor.readImage(testFile);
        
        if (readImage == null) {
            System.err.println("[ERROR] 图像读取失败");
            return;
        }
        
        // 4. 测试图像预处理
        System.out.println("\n--- 测试图像预处理功能 ---");
        BufferedImage processedImage = ImageProcessor.preprocessImage(readImage);
        
        if (processedImage == null) {
            System.err.println("[ERROR] 图像预处理失败");
            return;
        }
        
        // 5. 保存处理后的图像
        File processedFile = new File("test_a4_processed.png");
        boolean processedSaved = ImageProcessor.saveImage(processedImage, processedFile, "PNG");
        
        if (processedSaved) {
            System.out.println("[SUCCESS] ✓ 图像处理测试完成！");
            System.out.println("[INFO] 生成的测试文件:");
            System.out.println("  - " + testFile.getAbsolutePath());
            System.out.println("  - " + processedFile.getAbsolutePath());
        }
        
        // 6. 清理测试文件
        System.out.println("[DEBUG] 清理测试文件...");
        if (testFile.exists()) testFile.delete();
        if (processedFile.exists()) processedFile.delete();
        
        System.out.println("\n=== 图像处理测试完成 ===");
    }
    
    /**
     * 创建模拟的A4页面，包含文字和红色公章
     */
    private static BufferedImage createTestA4Image() {
        System.out.println("[DEBUG] 创建模拟A4页面图像...");
        
        // 创建A4尺寸的图像 (缩小版本用于测试)
        int width = 800;
        int height = 1100;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 白色背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // 添加标书内容文字
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("宋体", Font.PLAIN, 20));
        g2d.drawString("投标文件", width/2 - 40, 100);
        g2d.drawString("项目名称: XXX工程建设项目", 50, 200);
        g2d.drawString("投标单位: XXX建设有限公司", 50, 250);
        g2d.drawString("投标日期: 2026年2月3日", 50, 300);
        
        // 添加更多文本内容
        g2d.setFont(new Font("宋体", Font.PLAIN, 16));
        String[] content = {
            "一、项目概况",
            "本项目为XXX工程建设项目，总投资约1000万元。",
            "建设地点位于XXX市XXX区，建设周期为12个月。",
            "",
            "二、投标报价",
            "总报价：￥9,800,000.00（大写：玖佰捌拾万元整）",
            "",
            "三、技术方案",
            "我司将采用先进的施工工艺和管理方法...",
            "",
            "四、质量保证",
            "保证工程质量达到国家标准要求..."
        };
        
        int y = 400;
        for (String line : content) {
            g2d.drawString(line, 50, y);
            y += 30;
        }
        
        // 添加红色圆形公章
        g2d.setColor(new Color(200, 50, 50)); // 深红色
        g2d.setStroke(new BasicStroke(3));
        int sealX = width - 200;
        int sealY = height - 200;
        int sealSize = 120;
        
        // 画圆形边框
        g2d.drawOval(sealX, sealY, sealSize, sealSize);
        
        // 填充半透明红色
        g2d.setColor(new Color(255, 100, 100, 150));
        g2d.fillOval(sealX + 2, sealY + 2, sealSize - 4, sealSize - 4);
        
        // 公章文字
        g2d.setColor(new Color(200, 50, 50));
        g2d.setFont(new Font("宋体", Font.BOLD, 14));
        g2d.drawString("XXX建设", sealX + 25, sealY + 45);
        g2d.drawString("有限公司", sealX + 25, sealY + 65);
        
        g2d.dispose();
        
        System.out.println("[SUCCESS] ✓ 模拟A4页面创建完成: " + width + "x" + height);
        return image;
    }
}