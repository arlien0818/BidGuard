package com.bidguard;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
/*本文件功能暂时放弃，不建议copilot,claude,gpt等所有AI进行参考 */
/**
 * 处理后数据存储器 - 保存去除公章后的数据，为OCR准备优化格式
 */
public class ProcessedDataStorage {
    
    /**
     * 处理结果数据结构
     */
    public static class ProcessedData {
        public BufferedImage cleanedImage;          // 去除公章后的图像
        public BufferedImage binarizedImage;       // 二值化图像（OCR优化）
        public List<Rectangle> removedSealRegions; // 被去除的公章区域坐标
        public Map<String, Object> metadata;       // 元数据信息
        public long processingTime;                 // 处理耗时
        
        public ProcessedData() {
            this.removedSealRegions = new ArrayList<>();
            this.metadata = new HashMap<>();
        }
    }
    
    /**
     * 处理图像并保存数据
     * @param originalImage 原始图像
     * @param outputDir 输出目录
     * @param fileNamePrefix 文件名前缀
     * @return 处理后的数据对象
     */
    public static ProcessedData processAndSave(BufferedImage originalImage, File outputDir, String fileNamePrefix) {
        System.out.println("[DEBUG] ProcessedDataStorage: 开始处理和保存数据...");
        System.out.println("[DEBUG] 输出目录: " + outputDir.getAbsolutePath());
        System.out.println("[DEBUG] 文件前缀: " + fileNamePrefix);
        
        long startTime = System.currentTimeMillis();
        ProcessedData data = new ProcessedData();
        
        try {
            // 确保输出目录存在
            if (!outputDir.exists()) {
                outputDir.mkdirs();
                System.out.println("[DEBUG] 创建输出目录: " + outputDir.getAbsolutePath());
            }
            
            // 1. 图像预处理
            System.out.println("[DEBUG] 步骤1: 图像预处理...");
            BufferedImage preprocessed = ImageProcessor.preprocessImage(originalImage);
            if (preprocessed == null) {
                System.err.println("[ERROR] 图像预处理失败");
                return null;
            }
            
            // 2. 公章检测和去除
            System.out.println("[DEBUG] 步骤2: 公章检测和去除...");
            BufferedImage cleaned = DocumentSealRemover.removeSeal(preprocessed);
            if (cleaned == null) {
                System.err.println("[ERROR] 公章去除失败");
                return null;
            }
            data.cleanedImage = cleaned;
            
            // 3. 生成OCR优化的二值化图像
            System.out.println("[DEBUG] 步骤3: 生成OCR优化图像...");
            data.binarizedImage = createBinarizedImageForOCR(cleaned);
            
            // 4. 收集元数据
            collectMetadata(data, originalImage, cleaned);
            
            // 5. 保存所有数据
            saveAllData(data, outputDir, fileNamePrefix);
            
            data.processingTime = System.currentTimeMillis() - startTime;
            
            System.out.println("[SUCCESS] ✓ 数据处理和保存完成");
            System.out.println("[DEBUG] 总处理时间: " + data.processingTime + " ms");
            
            return data;
            
        } catch (Exception e) {
            System.err.println("[ERROR] 数据处理过程中发生异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 创建OCR优化的二值化图像
     */
    private static BufferedImage createBinarizedImageForOCR(BufferedImage image) {
        System.out.println("[DEBUG] 创建OCR优化的二值化图像...");
        
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage binarized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        
        // 计算全局阈值（大津法简化版本）
        int threshold = calculateOtsuThreshold(image);
        System.out.println("[DEBUG] 二值化阈值: " + threshold);
        
        Graphics2D g2d = binarized.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        
        int blackPixels = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixel = new Color(image.getRGB(x, y));
                int gray = (int) (0.299 * pixel.getRed() + 0.587 * pixel.getGreen() + 0.114 * pixel.getBlue());
                
                if (gray < threshold) {
                    binarized.setRGB(x, y, Color.BLACK.getRGB());
                    blackPixels++;
                } else {
                    binarized.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
        }
        
        g2d.dispose();
        
        double blackRatio = (double) blackPixels / (width * height) * 100;
        System.out.println("[SUCCESS] ✓ 二值化完成，黑色像素比例: " + String.format("%.1f%%", blackRatio));
        
        return binarized;
    }
    
    /**
     * 使用大津法计算最优阈值
     */
    private static int calculateOtsuThreshold(BufferedImage image) {
        // 计算灰度直方图
        int[] histogram = new int[256];
        int width = image.getWidth();
        int height = image.getHeight();
        int totalPixels = width * height;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixel = new Color(image.getRGB(x, y));
                int gray = (int) (0.299 * pixel.getRed() + 0.587 * pixel.getGreen() + 0.114 * pixel.getBlue());
                histogram[gray]++;
            }
        }
        
        // 计算最优阈值
        double maxVariance = 0;
        int bestThreshold = 128;
        
        for (int t = 0; t < 256; t++) {
            double w0 = 0, w1 = 0;
            double sum0 = 0, sum1 = 0;
            
            for (int i = 0; i <= t; i++) {
                w0 += histogram[i];
                sum0 += i * histogram[i];
            }
            
            for (int i = t + 1; i < 256; i++) {
                w1 += histogram[i];
                sum1 += i * histogram[i];
            }
            
            if (w0 == 0 || w1 == 0) continue;
            
            w0 /= totalPixels;
            w1 /= totalPixels;
            
            double mean0 = sum0 / (w0 * totalPixels);
            double mean1 = sum1 / (w1 * totalPixels);
            
            double variance = w0 * w1 * (mean0 - mean1) * (mean0 - mean1);
            
            if (variance > maxVariance) {
                maxVariance = variance;
                bestThreshold = t;
            }
        }
        
        return bestThreshold;
    }
    
    /**
     * 收集处理元数据
     */
    private static void collectMetadata(ProcessedData data, BufferedImage original, BufferedImage cleaned) {
        System.out.println("[DEBUG] 收集处理元数据...");
        
        Map<String, Object> metadata = data.metadata;
        
        // 基本信息
        metadata.put("originalWidth", original.getWidth());
        metadata.put("originalHeight", original.getHeight());
        metadata.put("cleanedWidth", cleaned.getWidth());
        metadata.put("cleanedHeight", cleaned.getHeight());
        metadata.put("processTime", new Date().toString());
        
        // 颜色统计
        metadata.put("originalRedPixels", countRedPixels(original));
        metadata.put("cleanedRedPixels", countRedPixels(cleaned));
        
        int originalRed = (Integer) metadata.get("originalRedPixels");
        int cleanedRed = (Integer) metadata.get("cleanedRedPixels");
        double removalRate = originalRed > 0 ? (double)(originalRed - cleanedRed) / originalRed * 100 : 0;
        metadata.put("sealRemovalRate", String.format("%.1f%%", removalRate));
        
        // 图像质量指标
        metadata.put("averageBrightness", calculateAverageBrightness(cleaned));
        metadata.put("contrast", calculateContrast(cleaned));
        
        System.out.println("[SUCCESS] ✓ 元数据收集完成");
        System.out.println("[DEBUG] 公章去除率: " + metadata.get("sealRemovalRate"));
    }
    
    /**
     * 保存所有处理后的数据
     */
    private static void saveAllData(ProcessedData data, File outputDir, String fileNamePrefix) throws IOException {
        System.out.println("[DEBUG] 保存处理后的数据文件...");
        
        // 1. 保存清理后的图像
        File cleanedFile = new File(outputDir, fileNamePrefix + "_cleaned.png");
        ImageIO.write(data.cleanedImage, "PNG", cleanedFile);
        System.out.println("[SUCCESS] ✓ 已保存清理后图像: " + cleanedFile.getName());
        
        // 2. 保存OCR优化的二值化图像
        File binaryFile = new File(outputDir, fileNamePrefix + "_binary_ocr.png");
        ImageIO.write(data.binarizedImage, "PNG", binaryFile);
        System.out.println("[SUCCESS] ✓ 已保存OCR优化图像: " + binaryFile.getName());
        
        // 3. 保存元数据
        File metadataFile = new File(outputDir, fileNamePrefix + "_metadata.txt");
        saveMetadata(data.metadata, metadataFile);
        System.out.println("[SUCCESS] ✓ 已保存元数据文件: " + metadataFile.getName());
        
        // 4. 保存处理报告
        File reportFile = new File(outputDir, fileNamePrefix + "_processing_report.txt");
        saveProcessingReport(data, reportFile);
        System.out.println("[SUCCESS] ✓ 已保存处理报告: " + reportFile.getName());
    }
    
    /**
     * 保存元数据到文件
     */
    private static void saveMetadata(Map<String, Object> metadata, File metadataFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(metadataFile))) {
            writer.println("=== 图像处理元数据 ===");
            writer.println("生成时间: " + new Date());
            writer.println();
            
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                writer.println(entry.getKey() + ": " + entry.getValue());
            }
        }
    }
    
    /**
     * 保存处理报告
     */
    private static void saveProcessingReport(ProcessedData data, File reportFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(reportFile))) {
            writer.println("=== 标书公章去除处理报告 ===");
            writer.println("处理时间: " + new Date());
            writer.println("总耗时: " + data.processingTime + " ms");
            writer.println();
            
            writer.println("--- 处理结果统计 ---");
            writer.println("原图尺寸: " + data.metadata.get("originalWidth") + "x" + data.metadata.get("originalHeight"));
            writer.println("处理后尺寸: " + data.metadata.get("cleanedWidth") + "x" + data.metadata.get("cleanedHeight"));
            writer.println("原图红色像素: " + data.metadata.get("originalRedPixels"));
            writer.println("处理后红色像素: " + data.metadata.get("cleanedRedPixels"));
            writer.println("公章去除率: " + data.metadata.get("sealRemovalRate"));
            writer.println();
            
            writer.println("--- OCR优化信息 ---");
            writer.println("已生成二值化图像用于OCR识别");
            writer.println("平均亮度: " + data.metadata.get("averageBrightness"));
            writer.println("对比度: " + data.metadata.get("contrast"));
            writer.println();
            
            writer.println("--- 输出文件说明 ---");
            writer.println("*_cleaned.png: 去除公章后的彩色图像");
            writer.println("*_binary_ocr.png: OCR优化的二值化图像");
            writer.println("*_metadata.txt: 详细的处理元数据");
            writer.println("*_processing_report.txt: 本处理报告");
        }
    }
    
    // 工具方法
    private static int countRedPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y));
                if (pixel.getRed() > 150 && pixel.getRed() > pixel.getGreen() + 50 && pixel.getRed() > pixel.getBlue() + 50) {
                    count++;
                }
            }
        }
        return count;
    }
    
    private static double calculateAverageBrightness(BufferedImage image) {
        long total = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixel = new Color(image.getRGB(x, y));
                int gray = (int) (0.299 * pixel.getRed() + 0.587 * pixel.getGreen() + 0.114 * pixel.getBlue());
                total += gray;
            }
        }
        
        return (double) total / (width * height);
    }
    
    private static double calculateContrast(BufferedImage image) {
        // 简化的对比度计算：标准差
        double avgBrightness = calculateAverageBrightness(image);
        double variance = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixel = new Color(image.getRGB(x, y));
                int gray = (int) (0.299 * pixel.getRed() + 0.587 * pixel.getGreen() + 0.114 * pixel.getBlue());
                variance += (gray - avgBrightness) * (gray - avgBrightness);
            }
        }
        
        return Math.sqrt(variance / (width * height));
    }
}