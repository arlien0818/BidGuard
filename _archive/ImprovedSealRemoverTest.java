package com.bidguard;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 改进的公章去除器测试类 - 测试多种情况
 */
public class ImprovedSealRemoverTest {
    
    public static void main(String[] args) {
        System.out.println("=== 改进公章去除器综合测试 ===");
        
        // 测试1: 彩色文档（红色公章 + 红头文件）
        testColoredDocument();
        
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // 测试2: 黑白复印件
        testBlackWhiteCopy();
        
        System.out.println("\n" + "=".repeat(50) + "\n");
        
        // 测试3: 多色公章文档
        testMultiColorSeals();
        
        System.out.println("\n=== 改进公章去除器测试完成 ===");
    }
    
    /**
     * 测试1: 彩色文档（包含红头文件和红色公章）
     */
    private static void testColoredDocument() {
        System.out.println("=== 测试1: 彩色文档（红头文件 + 红色公章）===");
        
        BufferedImage testImage = createColoredDocumentWithRedHeader();
        
        System.out.println("[DEBUG] 创建的测试图像包含:");
        System.out.println("  - 红色页面标题（红头文件）");
        System.out.println("  - 正文内容");
        System.out.println("  - 红色圆形公章");
        System.out.println("  - 红色方形章");
        
        // 执行改进的公章去除
        BufferedImage result = SealRemover.removeSeal(testImage);
        
        if (result != null) {
            // 分析结果
            analyzeRemovalResult(testImage, result, "彩色文档");
            
            // 保存结果供查看
            saveTestResult(testImage, result, "colored_document");
        } else {
            System.err.println("[ERROR] 彩色文档测试失败");
        }
    }
    
    /**
     * 测试2: 黑白复印件
     */
    private static void testBlackWhiteCopy() {
        System.out.println("=== 测试2: 黑白复印件 ===");
        
        BufferedImage testImage = createBlackWhiteCopy();
        
        System.out.println("[DEBUG] 创建的黑白复印件包含:");
        System.out.println("  - 黑色文字内容");
        System.out.println("  - 灰色圆形公章（原红章复印后）");
        System.out.println("  - 灰色方形章");
        
        // 执行改进的公章去除
        BufferedImage result = SealRemover.removeSeal(testImage);
        
        if (result != null) {
            analyzeRemovalResult(testImage, result, "黑白复印件");
            saveTestResult(testImage, result, "bw_copy");
        } else {
            System.err.println("[ERROR] 黑白复印件测试失败");
        }
    }
    
    /**
     * 测试3: 多色公章文档
     */
    private static void testMultiColorSeals() {
        System.out.println("=== 测试3: 多色公章文档 ===");
        
        BufferedImage testImage = createMultiColorSealsDocument();
        
        System.out.println("[DEBUG] 创建的多色公章文档包含:");
        System.out.println("  - 红色公章");
        System.out.println("  - 蓝色公章");
        System.out.println("  - 紫色公章");
        System.out.println("  - 黑色正文");
        
        // 执行改进的公章去除
        BufferedImage result = SealRemover.removeSeal(testImage);
        
        if (result != null) {
            analyzeRemovalResult(testImage, result, "多色公章");
            saveTestResult(testImage, result, "multi_color");
        } else {
            System.err.println("[ERROR] 多色公章测试失败");
        }
    }
    
    /**
     * 创建包含红头文件的彩色文档
     */
    private static BufferedImage createColoredDocumentWithRedHeader() {
        int width = 600;
        int height = 800;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 白色背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // 红头文件（页面顶部的红色标题）
        g2d.setColor(new Color(180, 50, 50));
        g2d.setFont(new Font("宋体", Font.BOLD, 24));
        g2d.drawString("中华人民共和国国家发展和改革委员会", 50, 60);
        
        g2d.setFont(new Font("宋体", Font.BOLD, 18));
        g2d.drawString("发改投资〔2026〕123号", 50, 90);
        
        // 分隔线
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(50, 110, width - 50, 110);
        
        // 黑色正文内容
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("宋体", Font.PLAIN, 14));
        
        String[] content = {
            "关于XXX工程项目投资计划的批复",
            "",
            "XX省人民政府：",
            "",
            "你省关于XXX工程项目投资计划的请示收悉。经研究，",
            "现批复如下：",
            "",
            "一、同意实施XXX工程项目，项目总投资85000万元。",
            "二、项目建设要严格执行国家有关规定和标准。",
            "三、请你省加强项目建设管理，确保工程质量。",
            "",
            "特此批复。",
            "",
            "",
            "                    国家发展和改革委员会",
            "                      2026年2月3日"
        };
        
        int y = 150;
        for (String line : content) {
            g2d.drawString(line, 80, y);
            y += 25;
        }
        
        // 红色圆形公章（右下角）
        addColoredSeal(g2d, 450, 600, 90, "国家发改委", new Color(220, 50, 50), true);
        
        // 红色方形章（批示章）
        addColoredSeal(g2d, 350, 650, 60, "同意", new Color(200, 40, 40), false);
        
        g2d.dispose();
        return image;
    }
    
    /**
     * 创建黑白复印件
     */
    private static BufferedImage createBlackWhiteCopy() {
        int width = 600;
        int height = 600;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 稍微泛黄的背景（模拟复印纸）
        g2d.setColor(new Color(248, 248, 240));
        g2d.fillRect(0, 0, width, height);
        
        // 黑色标题和内容
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("宋体", Font.BOLD, 20));
        g2d.drawString("合同书（复印件）", 200, 50);
        
        g2d.setFont(new Font("宋体", Font.PLAIN, 14));
        String[] content = {
            "甲方：XXX建设有限公司",
            "乙方：XXX施工企业",
            "",
            "经双方友好协商，就XXX工程项目达成如下协议：",
            "",
            "第一条 工程概况",
            "工程名称：XXX市政道路建设工程",
            "工程地点：XXX市XXX区",
            "工程总造价：人民币850万元",
            "",
            "第二条 工期要求", 
            "开工日期：2026年3月1日",
            "竣工日期：2026年8月31日",
            "工期：180日历天",
            "",
            "第三条 质量标准",
            "工程质量应符合国家现行标准。"
        };
        
        int y = 100;
        for (String line : content) {
            g2d.drawString(line, 50, y);
            y += 22;
        }
        
        // 灰色圆形公章（模拟红章复印后的效果）
        addGraySeal(g2d, 400, 450, 80, "建设公司", Color.DARK_GRAY, true);
        
        // 灰色方形章
        addGraySeal(g2d, 480, 500, 50, "合同", new Color(100, 100, 100), false);
        
        g2d.dispose();
        return image;
    }
    
    /**
     * 创建多色公章文档
     */
    private static BufferedImage createMultiColorSealsDocument() {
        int width = 700;
        int height = 500;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 白色背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        
        // 标题
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("宋体", Font.BOLD, 18));
        g2d.drawString("多部门联合审批文件", 250, 40);
        
        // 内容
        g2d.setFont(new Font("宋体", Font.PLAIN, 14));
        String[] content = {
            "项目名称：XXX综合体建设项目",
            "申请单位：XXX开发有限公司",
            "",
            "经多部门联合审查，该项目符合相关规定，",
            "同意按计划实施。各部门意见如下：",
            "",
            "规划部门：符合城市总体规划要求    [盖章]",
            "环保部门：环评审查通过          [盖章]", 
            "消防部门：消防设计合格          [盖章]",
            "",
            "请申请单位按批准的方案实施。"
        };
        
        int y = 80;
        for (String line : content) {
            g2d.drawString(line, 50, y);
            y += 25;
        }
        
        // 不同颜色的公章
        // 红色公章（规划部门）
        addColoredSeal(g2d, 350, 170, 70, "规划局", new Color(220, 50, 50), true);
        
        // 蓝色公章（环保部门）
        addColoredSeal(g2d, 350, 220, 70, "环保局", new Color(50, 100, 200), true);
        
        // 紫色公章（消防部门）
        addColoredSeal(g2d, 350, 270, 70, "消防局", new Color(150, 50, 180), true);
        
        g2d.dispose();
        return image;
    }
    
    /**
     * 添加彩色公章
     */
    private static void addColoredSeal(Graphics2D g2d, int x, int y, int size, String text, Color color, boolean circular) {
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(2));
        
        if (circular) {
            // 圆形章
            g2d.drawOval(x, y, size, size);
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
            g2d.fillOval(x + 2, y + 2, size - 4, size - 4);
        } else {
            // 方形章
            g2d.drawRect(x, y, size, size);
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 100));
            g2d.fillRect(x + 2, y + 2, size - 4, size - 4);
        }
        
        // 章内文字
        g2d.setColor(color);
        g2d.setFont(new Font("宋体", Font.BOLD, circular ? 12 : 14));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, x + (size - textWidth) / 2, y + size / 2 + 5);
    }
    
    /**
     * 添加灰色章（黑白复印效果）
     */
    private static void addGraySeal(Graphics2D g2d, int x, int y, int size, String text, Color grayColor, boolean circular) {
        g2d.setColor(grayColor);
        g2d.setStroke(new BasicStroke(2));
        
        if (circular) {
            g2d.drawOval(x, y, size, size);
            g2d.setColor(new Color(grayColor.getRed(), grayColor.getGreen(), grayColor.getBlue(), 120));
            g2d.fillOval(x + 2, y + 2, size - 4, size - 4);
        } else {
            g2d.drawRect(x, y, size, size);
            g2d.setColor(new Color(grayColor.getRed(), grayColor.getGreen(), grayColor.getBlue(), 120));
            g2d.fillRect(x + 2, y + 2, size - 4, size - 4);
        }
        
        g2d.setColor(grayColor);
        g2d.setFont(new Font("宋体", Font.BOLD, 10));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g2d.drawString(text, x + (size - textWidth) / 2, y + size / 2 + 3);
    }
    
    /**
     * 分析去除结果
     */
    private static void analyzeRemovalResult(BufferedImage original, BufferedImage result, String testType) {
        System.out.println("\n--- " + testType + "处理结果分析 ---");
        
        int originalColorPixels = countColoredPixels(original);
        int resultColorPixels = countColoredPixels(result);
        
        System.out.println("[DEBUG] 原图彩色像素: " + originalColorPixels);
        System.out.println("[DEBUG] 处理后彩色像素: " + resultColorPixels);
        
        if (resultColorPixels < originalColorPixels) {
            int removed = originalColorPixels - resultColorPixels;
            double removeRate = (double) removed / originalColorPixels * 100;
            System.out.println("[SUCCESS] ✓ 成功去除 " + removed + " 个彩色像素");
            System.out.println("[SUCCESS] ✓ 去除率: " + String.format("%.1f%%", removeRate));
        } else {
            System.out.println("[INFO] 彩色像素数量未明显减少，可能为灰度图像或无彩色印章");
        }
        
        // 检查是否保留了文字区域
        boolean textPreserved = checkTextPreservation(result);
        if (textPreserved) {
            System.out.println("[SUCCESS] ✓ 文字内容得到保留");
        } else {
            System.out.println("[WARNING] 可能误删了部分文字内容");
        }
    }
    
    /**
     * 统计彩色像素数量
     */
    private static int countColoredPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y));
                // 检查是否为非灰度像素
                if (Math.abs(pixel.getRed() - pixel.getGreen()) > 20 || 
                    Math.abs(pixel.getGreen() - pixel.getBlue()) > 20) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * 检查文字是否被保留
     */
    private static boolean checkTextPreservation(BufferedImage image) {
        // 简单检查：统计黑色像素（文字）的数量
        int blackPixels = 0;
        int totalPixels = image.getWidth() * image.getHeight();
        
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y));
                int gray = (int) (0.299 * pixel.getRed() + 0.587 * pixel.getGreen() + 0.114 * pixel.getBlue());
                if (gray < 50) { // 很暗的像素认为是文字
                    blackPixels++;
                }
            }
        }
        
        double textRatio = (double) blackPixels / totalPixels;
        return textRatio > 0.02; // 至少2%的像素是文字内容
    }
    
    /**
     * 保存测试结果
     */
    private static void saveTestResult(BufferedImage original, BufferedImage result, String testName) {
        try {
            File originalFile = new File("test_" + testName + "_original.png");
            File resultFile = new File("test_" + testName + "_result.png");
            
            boolean saved1 = ImageProcessor.saveImage(original, originalFile, "PNG");
            boolean saved2 = ImageProcessor.saveImage(result, resultFile, "PNG");
            
            if (saved1 && saved2) {
                System.out.println("[INFO] 测试结果已保存:");
                System.out.println("  - 原图: " + originalFile.getName());
                System.out.println("  - 结果: " + resultFile.getName());
                
                // 清理文件
                originalFile.delete();
                resultFile.delete();
                System.out.println("[DEBUG] 测试文件已清理");
            }
        } catch (Exception e) {
            System.err.println("[WARNING] 保存测试结果失败: " + e.getMessage());
        }
    }
}