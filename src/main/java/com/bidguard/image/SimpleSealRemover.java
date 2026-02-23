package com.bidguard.image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * 简化但更有效的公章去除器 - 专门针对营业执照等官方文件
 */
public class SimpleSealRemover {
    
    /**
     * 主要的公章去除方法
     */
    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) {
            System.err.println("[ERROR] 输入图像为空");
            return null;
        }
        
        System.out.println("[DEBUG] ============ 开始简化公章去除 ============");
        System.out.println("[DEBUG] 图像尺寸: " + image.getWidth() + "x" + image.getHeight());
        
        // 使用简单但精确的红色像素去除
        BufferedImage result = removeRedPixelsIntelligently(image);
        
        System.out.println("[DEBUG] ============ 公章去除完成 ============");
        return result;
    }
    
    /**
     * 智能红色像素去除
     */
    private static BufferedImage removeRedPixelsIntelligently(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        
        int originalRedPixels = 0;
        int removedPixels = 0;
        
        System.out.println("[DEBUG] 开始逐像素分析...");
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                
                if (isRedPixel(rgb)) {
                    originalRedPixels++;
                    
                    // 更严格的判断：是否真的是印章部分
                    if (shouldRemoveRedPixel(image, x, y)) {
                        // 使用智能替换颜色
                        int replacement = getSmartReplacementColor(image, x, y);
                        result.setRGB(x, y, replacement);
                        removedPixels++;
                    } else {
                        // 保留这个红色像素
                        result.setRGB(x, y, rgb);
                    }
                } else {
                    // 非红色像素直接复制
                    result.setRGB(x, y, rgb);
                }
            }
            
            // 每50行输出一次进度
            if (y % 50 == 0) {
                double progress = (y * 100.0) / height;
                System.out.println("[DEBUG] 处理进度: " + String.format("%.1f%%", progress));
            }
        }
        
        double removalRate = originalRedPixels > 0 ? (removedPixels * 100.0) / originalRedPixels : 0;
        System.out.println("[DEBUG] 原始红色像素: " + originalRedPixels);
        System.out.println("[DEBUG] 移除红色像素: " + removedPixels);
        System.out.println("[DEBUG] 红色像素去除率: " + String.format("%.1f%%", removalRate));
        
        return result;
    }
    
    /**
     * 判断是否为红色像素 - 更宽松的检测
     */
    private static boolean isRedPixel(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        // 多种红色检测条件
        // 1. 标准红色：红色分量高，绿蓝分量相对低
        boolean standardRed = (r > 120) && (r > g * 1.3) && (r > b * 1.3);
        
        // 2. 深红色：总亮度较低但红色占主导
        boolean darkRed = (r > 80) && (r > g * 1.8) && (r > b * 1.8) && (r + g + b < 300);
        
        // 3. 鲜红色：红色分量很高
        boolean brightRed = (r > 180) && (g < 100) && (b < 100);
        
        return standardRed || darkRed || brightRed;
    }
    
    /**
     * 判断红色像素是否应该被移除
     */
    private static boolean shouldRemoveRedPixel(BufferedImage image, int x, int y) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        int redNeighbors = 0;
        int totalNeighbors = 0;
        
        // 检查5x5邻域中的红色像素密度
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    totalNeighbors++;
                    if (isRedPixel(image.getRGB(nx, ny))) {
                        redNeighbors++;
                    }
                }
            }
        }
        
        // 如果邻域中红色像素比例较高，可能是印章，应该移除
        double redRatio = (double) redNeighbors / totalNeighbors;
        
        // 设置一个合理的阈值：如果周围有足够的红色像素，认为是印章的一部分
        return redRatio > 0.15;  // 15%以上的邻域是红色就移除
    }
    
    /**
     * 获取智能替换颜色
     */
    private static int getSmartReplacementColor(BufferedImage image, int x, int y) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        java.util.List<Integer> nearbyColors = new ArrayList<>();
        
        // 在更大范围内收集非红色像素
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    int rgb = image.getRGB(nx, ny);
                    if (!isRedPixel(rgb)) {
                        nearbyColors.add(rgb);
                    }
                }
            }
        }
        
        if (nearbyColors.isEmpty()) {
            // 如果找不到非红色像素，返回浅灰色（纸张色）
            return new Color(248, 248, 248).getRGB();
        }
        
        // 计算平均颜色
        long sumR = 0, sumG = 0, sumB = 0;
        for (int color : nearbyColors) {
            sumR += (color >> 16) & 0xFF;
            sumG += (color >> 8) & 0xFF;
            sumB += color & 0xFF;
        }
        
        int avgR = (int) (sumR / nearbyColors.size());
        int avgG = (int) (sumG / nearbyColors.size());
        int avgB = (int) (sumB / nearbyColors.size());
        
        // 稍微提高亮度，模拟纸张背景
        avgR = Math.min(255, avgR + 10);
        avgG = Math.min(255, avgG + 10);
        avgB = Math.min(255, avgB + 10);
        
        return (avgR << 16) | (avgG << 8) | avgB;
    }
}