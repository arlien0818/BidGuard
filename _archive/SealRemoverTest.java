package com.bidguard;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 公章去除器测试类
 */
public class SealRemoverTest {
    
    public static void main(String[] args) {
        System.out.println("=== 第三步：公章检测和去除测试 ===");
        testSealRemover();
    }
    
    public static void testSealRemover() {
        System.out.println("[DEBUG] 开始测试公章去除功能...");
        
        // 1. 创建带公章的测试图像
        BufferedImage testImage = createImageWithSeal();
        
        // 2. 保存原始图像
        File originalFile = new File("original_with_seal.png");
        boolean saved = ImageProcessor.saveImage(testImage, originalFile, "PNG");
        if (!saved) {
            System.err.println("[ERROR] 无法保存原始测试图像");
            return;
        }
        
        // 3. 预处理图像
        System.out.println("\n--- 图像预处理 ---");
        BufferedImage preprocessed = ImageProcessor.preprocessImage(testImage);
        if (preprocessed == null) {
            System.err.println("[ERROR] 图像预处理失败");
            return;
        }
        
        // 4. 去除公章
        System.out.println("\n--- 公章检测和去除 ---");
        BufferedImage result = SealRemover.removeSeal(preprocessed);
        if (result == null) {
            System.err.println("[ERROR] 公章去除失败");
            return;
        }
        
        // 5. 保存结果图像
        File resultFile = new File("result_seal_removed.png");
        boolean resultSaved = ImageProcessor.saveImage(result, resultFile, "PNG");
        
        if (resultSaved) {
            System.out.println("\n[SUCCESS] ✓ 公章去除测试完成！");
            System.out.println("[INFO] 生成的文件:");
            System.out.println("  - 原始图像: " + originalFile.getAbsolutePath());
            System.out.println("  - 处理结果: " + resultFile.getAbsolutePath());
            System.out.println("[INFO] 请查看生成的图像文件，对比去除效果");
        }
        
        // 6. 统计分析
        analyzeResult(testImage, result);
        
        // 7. 清理测试文件
        System.out.println("[DEBUG] 清理测试文件...");
        if (originalFile.exists()) originalFile.delete();
        if (resultFile.exists()) resultFile.delete();
        
        System.out.println("\n=== 公章去除测试完成 ===");
    }
    
    /**
     * 创建包含多个公章的测试图像
     */
    private static BufferedImage createImageWithSeal() {
        System.out.println("[DEBUG] 创建包含公章的测试图像...");
        
        int width = 800;
        int height = 600;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 白色背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // 添加文档内容
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("宋体", Font.PLAIN, 16));
        
        String[] lines = {
            "标书文件内容示例",
            "",
            "项目名称：XXX建设工程",
            "投标单位：XXX建设有限公司", 
            "联系电话：010-12345678",
            "",
            "技术方案：",
            "1. 采用先进的施工技术和管理方法",
            "2. 严格按照国家标准和规范执行",
            "3. 确保工程质量和安全生产",
            "",
            "投标承诺：",
            "我司承诺严格履行合同条款，按时完成工程建设。"
        };
        
        int y = 50;
        for (String line : lines) {
            g2d.drawString(line, 50, y);
            y += 25;
        }
        
        // 添加第一个圆形公章
        addCircularSeal(g2d, 600, 150, 80, "建设公司", new Color(220, 50, 50));
        
        // 添加第二个方形公章
        addSquareSeal(g2d, 650, 350, 70, "质量专用", new Color(200, 30, 30));
        
        // 添加第三个小圆章
        addCircularSeal(g2d, 500, 450, 50, "审核", new Color(180, 60, 60));
        
        g2d.dispose();
        
        System.out.println("[SUCCESS] ✓ 测试图像创建完成: " + width + "x" + height);
        System.out.println("[DEBUG] 包含3个公章：2个圆章，1个方章");
        
        return image;
    }
    
    /**
     * 添加圆形公章
     */
    private static void addCircularSeal(Graphics2D g2d, int centerX, int centerY, int size, String text, Color color) {
        System.out.println("[DEBUG] 添加圆形公章: " + text + " at (" + centerX + "," + centerY + ")");
        
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2));
        
        // 画外圈
        g2d.drawOval(centerX - size/2, centerY - size/2, size, size);
        
        // 半透明填充
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
        g2d.fillOval(centerX - size/2 + 2, centerY - size/2 + 2, size - 4, size - 4);
        
        // 文字
        g2d.setColor(color);
        g2d.setFont(new Font("宋体", Font.BOLD, 12));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, centerX - textWidth/2, centerY + 5);
    }
    
    /**
     * 添加方形公章
     */
    private static void addSquareSeal(Graphics2D g2d, int centerX, int centerY, int size, String text, Color color) {
        System.out.println("[DEBUG] 添加方形公章: " + text + " at (" + centerX + "," + centerY + ")");
        
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2));
        
        // 画方框
        g2d.drawRect(centerX - size/2, centerY - size/2, size, size);
        
        // 半透明填充
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
        g2d.fillRect(centerX - size/2 + 2, centerY - size/2 + 2, size - 4, size - 4);
        
        // 文字
        g2d.setColor(color);
        g2d.setFont(new Font("宋体", Font.BOLD, 10));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, centerX - textWidth/2, centerY + 3);
    }
    
    /**
     * 分析处理结果
     */
    private static void analyzeResult(BufferedImage original, BufferedImage result) {
        System.out.println("\n--- 结果分析 ---");
        
        int originalRedPixels = countRedPixels(original);
        int resultRedPixels = countRedPixels(result);
        
        System.out.println("[DEBUG] 原图红色像素数量: " + originalRedPixels);
        System.out.println("[DEBUG] 结果图红色像素数量: " + resultRedPixels);
        
        if (resultRedPixels < originalRedPixels) {
            int removed = originalRedPixels - resultRedPixels;
            double removeRate = (double) removed / originalRedPixels * 100;
            System.out.println("[SUCCESS] ✓ 成功去除 " + removed + " 个红色像素");
            System.out.println("[SUCCESS] ✓ 去除率: " + String.format("%.1f%%", removeRate));
        } else {
            System.out.println("[WARNING] 红色像素数量未减少，可能检测失败");
        }
    }
    
    /**
     * 统计图像中红色像素的数量
     */
    private static int countRedPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y));
                if (isRedPixel(pixel)) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * 简单的红色像素判断（与SealRemover中的逻辑一致）
     */
    private static boolean isRedPixel(Color pixel) {
        int r = pixel.getRed();
        int g = pixel.getGreen();
        int b = pixel.getBlue();
        
        // 简单的红色判断：红色分量高，且比其他分量大
        return r > 150 && r > g + 50 && r > b + 50;
    }
}