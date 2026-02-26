package com.bidguard.image;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
// import java.util.logging.Logger;

/**
 * 图像处理器 - 负责A4页面图像读取、预处理和格式转换
 */
public class ImageProcessor {
    
    // private static final Logger LOGGER = Logger.getLogger(ImageProcessor.class.getName());
    
    // A4页面标准尺寸 (像素，300DPI)
    public static final int A4_WIDTH_300DPI = 2480;
    public static final int A4_HEIGHT_300DPI = 3508;
    
    /**
     * 读取图像文件
     * @param imageFile 图像文件
     * @return BufferedImage 或 null
     */
    public static BufferedImage readImage(File imageFile) {
        System.out.println("[DEBUG] ImageProcessor: 开始读取图像文件: " + imageFile.getAbsolutePath());
        
        if (imageFile == null || !imageFile.exists()) {
            System.err.println("[ERROR] 图像文件不存在: " + imageFile);
            return null;
        }
        
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                System.err.println("[ERROR] 无法读取图像文件，可能格式不支持: " + imageFile.getName());
                return null;
            }
            
            System.out.println("[SUCCESS] ✓ 图像读取成功");
            System.out.println("[DEBUG] 图像尺寸: " + image.getWidth() + "x" + image.getHeight());
            System.out.println("[DEBUG] 图像类型: " + getImageTypeString(image.getType()));
            System.out.println("[DEBUG] 文件大小: " + (imageFile.length() / 1024) + " KB");
            
            return image;
            
        } catch (IOException e) {
            System.err.println("[ERROR] 读取图像文件失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 预处理图像：调整尺寸、对比度、去噪等
     * @param originalImage 原始图像
     * @return 预处理后的图像
     */
    public static BufferedImage preprocessImage(BufferedImage originalImage) {
        System.out.println("[DEBUG] ImageProcessor: 开始图像预处理...");
        
        if (originalImage == null) {
            System.err.println("[ERROR] 原始图像为null，无法预处理");
            return null;
        }
        
        BufferedImage processedImage = originalImage;
        
        // 1. 尺寸标准化（如果需要）
        processedImage = resizeIfNeeded(processedImage);
        
        // 2. 对比度增强
        processedImage = enhanceContrast(processedImage);
        
        // 3. 颜色空间转换（确保RGB格式）
        processedImage = ensureRGBFormat(processedImage);
        
        System.out.println("[SUCCESS] ✓ 图像预处理完成");
        System.out.println("[DEBUG] 处理后图像尺寸: " + processedImage.getWidth() + "x" + processedImage.getHeight());
        
        return processedImage;
    }
    
    /**
     * 如需要，调整图像尺寸到合适大小
     */
    private static BufferedImage resizeIfNeeded(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        System.out.println("[DEBUG] 检查是否需要调整尺寸: " + width + "x" + height);
        
        // 如果图像太大（超过A4 300DPI），按比例缩小
        if (width > A4_WIDTH_300DPI * 1.2 || height > A4_HEIGHT_300DPI * 1.2) {
            double scale = Math.min(
                (double) A4_WIDTH_300DPI / width,
                (double) A4_HEIGHT_300DPI / height
            );
            
            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);
            
            System.out.println("[DEBUG] 需要缩放，比例: " + String.format("%.2f", scale));
            System.out.println("[DEBUG] 新尺寸: " + newWidth + "x" + newHeight);
            
            BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = resizedImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
            g2d.dispose();
            
            System.out.println("[SUCCESS] ✓ 图像尺寸调整完成");
            return resizedImage;
        }
        
        System.out.println("[DEBUG] 图像尺寸合适，无需调整");
        return image;
    }
    
    /**
     * 增强对比度
     */
    private static BufferedImage enhanceContrast(BufferedImage image) {
        System.out.println("[DEBUG] 开始增强对比度...");
        
        BufferedImage enhanced = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        
        // 简单的对比度增强算法
        float contrastFactor = 1.2f; // 对比度因子
        int brightness = 10;         // 亮度调整
        
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color originalColor = new Color(image.getRGB(x, y));
                
                int r = Math.min(255, Math.max(0, (int) (originalColor.getRed() * contrastFactor) + brightness));
                int g = Math.min(255, Math.max(0, (int) (originalColor.getGreen() * contrastFactor) + brightness));
                int b = Math.min(255, Math.max(0, (int) (originalColor.getBlue() * contrastFactor) + brightness));
                
                Color enhancedColor = new Color(r, g, b);
                enhanced.setRGB(x, y, enhancedColor.getRGB());
            }
        }
        
        System.out.println("[SUCCESS] ✓ 对比度增强完成");
        return enhanced;
    }
    
    /**
     * 确保图像为RGB格式
     */
    private static BufferedImage ensureRGBFormat(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            System.out.println("[DEBUG] 图像已是RGB格式");
            return image;
        }
        
        System.out.println("[DEBUG] 转换图像为RGB格式...");
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgbImage.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        
        System.out.println("[SUCCESS] ✓ RGB格式转换完成");
        return rgbImage;
    }
    
    /**
     * 获取图像类型字符串描述
     */
    private static String getImageTypeString(int type) {
        switch (type) {
            case BufferedImage.TYPE_INT_RGB: return "INT_RGB";
            case BufferedImage.TYPE_INT_ARGB: return "INT_ARGB";
            case BufferedImage.TYPE_INT_ARGB_PRE: return "INT_ARGB_PRE";
            case BufferedImage.TYPE_INT_BGR: return "INT_BGR";
            case BufferedImage.TYPE_3BYTE_BGR: return "3BYTE_BGR";
            case BufferedImage.TYPE_4BYTE_ABGR: return "4BYTE_ABGR";
            case BufferedImage.TYPE_BYTE_GRAY: return "BYTE_GRAY";
            case BufferedImage.TYPE_USHORT_GRAY: return "USHORT_GRAY";
            default: return "UNKNOWN(" + type + ")";
        }
    }
    
    /**
     * 保存图像到文件（用于调试）
     */
    public static boolean saveImage(BufferedImage image, File outputFile, String format) {
        try {
            boolean result = ImageIO.write(image, format, outputFile);
            if (result) {
                System.out.println("[SUCCESS] ✓ 图像保存成功: " + outputFile.getAbsolutePath());
            } else {
                System.err.println("[ERROR] 图像保存失败: " + outputFile.getAbsolutePath());
            }
            return result;
        } catch (IOException e) {
            System.err.println("[ERROR] 保存图像时发生异常: " + e.getMessage());
            return false;
        }
    }
}