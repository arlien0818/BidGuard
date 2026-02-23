package com.bidguard.sealremover;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 改进的公章去除器 - 支持多种颜色公章、黑白复印件、形状检测
 */
public class SealRemover {
    
    // 公章检测参数
    private static final int MIN_SEAL_SIZE = 40;     // 最小公章尺寸
    private static final int MAX_SEAL_SIZE = 200;    // 最大公章尺寸
    private static final double CIRCULARITY_THRESHOLD = 0.6; // 圆形度阈值
    private static final double DENSITY_THRESHOLD = 0.3;     // 密度阈值
    
    // 颜色检测阈值
    private static final int SATURATION_THRESHOLD = 80;  // 饱和度阈值
    private static final int BRIGHTNESS_DIFF_THRESHOLD = 30; // 亮度差异阈值
    
    /**
     * 智能检测并去除图像中的公章（支持多种情况）
     */
    public static BufferedImage removeSeal(BufferedImage image) {
        System.out.println("[DEBUG] SealRemover: 开始智能公章检测和去除...");
        System.out.println("[DEBUG] 图像类型: " + getImageTypeDescription(image));
        
        if (image == null) {
            System.err.println("[ERROR] 输入图像为null");
            return null;
        }
        
        // 1. 多策略检测公章候选区域
        List<Rectangle> candidates = new ArrayList<>();
        
        // 策略1: 颜色特征检测（彩色图像）
        if (!isGrayscaleImage(image)) {
            System.out.println("[DEBUG] 执行彩色公章检测...");
            candidates.addAll(detectColoredSeals(image));
        }
        
        // 策略2: 形状和密度检测（适用于所有类型）
        System.out.println("[DEBUG] 执行形状密度检测...");
        candidates.addAll(detectShapeBasedSeals(image));
        
        // 策略3: 纹理模式检测
        System.out.println("[DEBUG] 执行纹理模式检测...");
        candidates.addAll(detectTextureBasedSeals(image));
        
        // 2. 合并重叠的候选区域
        List<Rectangle> mergedCandidates = mergeOverlappingRegions(candidates);
        System.out.println("[DEBUG] 合并后找到 " + mergedCandidates.size() + " 个公章候选区域");
        
        // 3. 智能过滤，避免误删文字内容
        List<Rectangle> validSeals = filterValidSeals(image, mergedCandidates);
        System.out.println("[DEBUG] 验证后确认 " + validSeals.size() + " 个有效公章");
        
        // 4. 去除确认的公章
        BufferedImage result = removeConfirmedSeals(image, validSeals);
        
        System.out.println("[SUCCESS] ✓ 智能公章检测和去除完成");
        return result;
    }
    
    /**
     * 专门处理营业执照的红色印章
     */
    private static BufferedImage removeBusinessLicenseSeals(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        
        int removedPixels = 0;
        int totalPixels = image.getWidth() * image.getHeight();
        
        System.out.println("[DEBUG] 开始精确红色像素检测...");
        
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                
                if (isRedSealPixel(rgb)) {
                    // 检查周围像素，确认这是印章的一部分
                    if (isPartOfSeal(image, x, y)) {
                        // 使用智能填充替换红色像素
                        int replacementColor = getIntelligentReplacement(image, x, y);
                        result.setRGB(x, y, replacementColor);
                        removedPixels++;
                    }
                }
            }
        }
        
        double removalRate = (removedPixels * 100.0) / totalPixels;
        System.out.println("[DEBUG] 移除像素数: " + removedPixels + ", 去除率: " + String.format("%.1f%%", removalRate));
        
        return result;
    }
    
    /**
     * 判断像素是否为红色印章像素
     */
    private static boolean isRedSealPixel(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        // 针对营业执照红色印章的精确颜色检测
        // 条件1: 红色分量明显高于绿色和蓝色
        // 条件2: 排除粉红色和橙红色
        // 条件3: 包括深红色
        
        return (r > 150 && r > g * 1.5 && r > b * 1.5) ||  // 标准红色
               (r > 100 && r > g * 2.0 && r > b * 2.0 && r + g + b < 400); // 深红色
    }
    
    /**
     * 检查像素是否是印章的一部分（通过周围像素分析）
     */
    private static boolean isPartOfSeal(BufferedImage image, int x, int y) {
        int width = image.getWidth();
        int height = image.getHeight();
        int redCount = 0;
        int totalCount = 0;
        
        // 检查3x3邻域
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    totalCount++;
                    if (isRedSealPixel(image.getRGB(nx, ny))) {
                        redCount++;
                    }
                }
            }
        }
        
        // 如果邻域中有足够的红色像素，认为是印章的一部分
        return redCount >= 2; // 至少有2个邻近的红色像素
    }
    
    /**
     * 智能获取替换颜色（基于周围非红色像素）
     */
    private static int getIntelligentReplacement(BufferedImage image, int x, int y) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        List<Integer> nonRedColors = new ArrayList<>();
        
        // 在更大范围内寻找非红色像素
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    int rgb = image.getRGB(nx, ny);
                    if (!isRedSealPixel(rgb)) {
                        nonRedColors.add(rgb);
                    }
                }
            }
        }
        
        if (nonRedColors.isEmpty()) {
            return Color.WHITE.getRGB(); // 默认白色
        }
        
        // 计算平均颜色
        long totalR = 0, totalG = 0, totalB = 0;
        for (int color : nonRedColors) {
            totalR += (color >> 16) & 0xFF;
            totalG += (color >> 8) & 0xFF;
            totalB += color & 0xFF;
        }
        
        int avgR = (int) (totalR / nonRedColors.size());
        int avgG = (int) (totalG / nonRedColors.size());
        int avgB = (int) (totalB / nonRedColors.size());
        
        return (avgR << 16) | (avgG << 8) | avgB;
    }
    
    /**
     * 检测彩色公章（红色、蓝色、紫色等）- 保留原方法作为备用
     */
    private static List<Rectangle> detectColoredSeals(BufferedImage image) {
        System.out.println("[DEBUG] 检测彩色公章...");
        
        List<Rectangle> colorSeals = new ArrayList<>();
        
        // 检测红色公章
        colorSeals.addAll(detectColorSeals(image, "红色", 
            new int[]{0, 10, 350, 360}, new int[]{100, 255}, new int[]{100, 255}));
        
        // 检测蓝色公章
        colorSeals.addAll(detectColorSeals(image, "蓝色",
            new int[]{200, 250}, new int[]{100, 255}, new int[]{100, 255}));
        
        // 检测紫色公章
        colorSeals.addAll(detectColorSeals(image, "紫色",
            new int[]{280, 320}, new int[]{100, 255}, new int[]{100, 255}));
        
        System.out.println("[DEBUG] 彩色检测找到 " + colorSeals.size() + " 个候选区域");
        return colorSeals;
    }
    
    /**
     * 基于特定颜色检测公章
     */
    private static List<Rectangle> detectColorSeals(BufferedImage image, String colorName, 
            int[] hueRange, int[] satRange, int[] valRange) {
        
        System.out.println("[DEBUG] 检测" + colorName + "公章...");
        
        BufferedImage mask = createColorMask(image, hueRange, satRange, valRange);
        List<Rectangle> regions = findConnectedRegions(mask);
        
        List<Rectangle> validRegions = new ArrayList<>();
        for (Rectangle region : regions) {
            if (isValidSealSize(region) && hasCircularShape(mask, region)) {
                validRegions.add(region);
                System.out.println("[DEBUG] 找到" + colorName + "公章候选: " + 
                    region.width + "x" + region.height + " at (" + region.x + "," + region.y + ")");
            }
        }
        
        return validRegions;
    }
    
    /**
     * 基于形状和密度检测公章（适用于黑白复印件）
     */
    private static List<Rectangle> detectShapeBasedSeals(BufferedImage image) {
        System.out.println("[DEBUG] 基于形状密度检测公章...");
        
        List<Rectangle> shapeSeals = new ArrayList<>();
        
        // 1. 创建边缘检测图像
        BufferedImage edgeImage = detectEdges(image);
        
        // 2. 寻找圆形和方形区域
        List<Rectangle> circularRegions = findCircularRegions(edgeImage);
        List<Rectangle> squareRegions = findSquareRegions(edgeImage);
        
        shapeSeals.addAll(circularRegions);
        shapeSeals.addAll(squareRegions);
        
        // 3. 验证密度特征
        List<Rectangle> densityValidated = new ArrayList<>();
        for (Rectangle region : shapeSeals) {
            if (hasHighDensity(image, region)) {
                densityValidated.add(region);
                System.out.println("[DEBUG] 形状密度检测找到公章: " + 
                    region.width + "x" + region.height + " at (" + region.x + "," + region.y + ")");
            }
        }
        
        System.out.println("[DEBUG] 形状密度检测找到 " + densityValidated.size() + " 个候选区域");
        return densityValidated;
    }
    
    /**
     * 基于纹理模式检测公章
     */
    private static List<Rectangle> detectTextureBasedSeals(BufferedImage image) {
        System.out.println("[DEBUG] 基于纹理模式检测公章...");
        
        List<Rectangle> textureSeals = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();
        
        // 滑动窗口检测
        int windowSize = 80;
        int step = 20;
        
        for (int y = 0; y <= height - windowSize; y += step) {
            for (int x = 0; x <= width - windowSize; x += step) {
                Rectangle window = new Rectangle(x, y, windowSize, windowSize);
                
                if (hasSealTexture(image, window)) {
                    textureSeals.add(window);
                    System.out.println("[DEBUG] 纹理检测找到公章候选: " + 
                        window.width + "x" + window.height + " at (" + window.x + "," + window.y + ")");
                }
            }
        }
        
        System.out.println("[DEBUG] 纹理检测找到 " + textureSeals.size() + " 个候选区域");
        return textureSeals;
    }
    
    /**
     * 智能过滤，区分公章和正常文字内容
     */
    private static List<Rectangle> filterValidSeals(BufferedImage image, List<Rectangle> candidates) {
        System.out.println("[DEBUG] 智能过滤公章候选区域...");
        
        List<Rectangle> validSeals = new ArrayList<>();
        
        for (Rectangle candidate : candidates) {
            boolean isValid = true;
            String reason = "";
            
            // 检查1: 位置合理性（避免删除标题区域的红头文件）
            if (isInHeaderRegion(image, candidate)) {
                // 如果在页面顶部，需要额外验证是否真的是公章而不是红头文字
                if (!isCircularEnough(image, candidate)) {
                    isValid = false;
                    reason = "位于页头且非圆形，疑似红头文字";
                }
            }
            
            // 检查2: 内容分析（公章内部应该有文字或图案）
            if (isValid && !hasInternalContent(image, candidate)) {
                isValid = false;
                reason = "内部无内容，疑似装饰性图形";
            }
            
            // 检查3: 周围上下文（公章通常出现在文档末尾或特定位置）
            if (isValid && isInMainTextFlow(image, candidate)) {
                // 如果在正文流中，需要更严格的验证
                if (!hasHighCircularity(image, candidate)) {
                    isValid = false;
                    reason = "位于正文中且圆形度不足";
                }
            }
            
            if (isValid) {
                validSeals.add(candidate);
                System.out.println("[SUCCESS] ✓ 确认有效公章: " + 
                    candidate.width + "x" + candidate.height + " at (" + candidate.x + "," + candidate.y + ")");
            } else {
                System.out.println("[DEBUG] 过滤候选区域: " + reason);
            }
        }
        
        return validSeals;
    }
    
    /**
     * 检测红色区域，生成红色像素掩码
     */
    private static BufferedImage detectRedRegions(BufferedImage image) {
        System.out.println("[DEBUG] 检测红色区域...");
        
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        int redPixelCount = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixel = new Color(image.getRGB(x, y));
                
                if (isRedPixel(pixel)) {
                    mask.setRGB(x, y, Color.WHITE.getRGB()); // 白色表示红色区域
                    redPixelCount++;
                } else {
                    mask.setRGB(x, y, Color.BLACK.getRGB()); // 黑色表示非红色区域
                }
            }
        }
        
        System.out.println("[DEBUG] 检测到红色像素数量: " + redPixelCount);
        System.out.println("[DEBUG] 红色像素比例: " + String.format("%.2f%%", (redPixelCount * 100.0) / (width * height)));
        
        return mask;
    }
    
    /**
     * 判断像素是否为红色
     */
    private static boolean isRedPixel(Color pixel) {
        int r = pixel.getRed();
        int g = pixel.getGreen();
        int b = pixel.getBlue();
        
        // 转换为HSV色彩空间
        float[] hsv = Color.RGBtoHSB(r, g, b, null);
        int hue = (int) (hsv[0] * 360);
        int saturation = (int) (hsv[1] * 255);
        int value = (int) (hsv[2] * 255);
        
        // 检查是否在红色范围内（红色在0度和360度附近）
        boolean isRedHue = (hue >= 0 && hue <= 10) || (hue >= 350 && hue <= 360);
        boolean hasSufficientSaturation = saturation >= 100;
        boolean hasSufficientValue = value >= 100;
        
        return isRedHue && hasSufficientSaturation && hasSufficientValue;
    }
    
    // ========== 辅助检测方法 ==========
    
    /**
     * 判断图像是否为灰度图像
     */
    private static boolean isGrayscaleImage(BufferedImage image) {
        int sampleSize = Math.min(1000, image.getWidth() * image.getHeight());
        int colorPixels = 0;
        
        for (int i = 0; i < sampleSize; i++) {
            int x = (i * image.getWidth()) / sampleSize;
            int y = (i * image.getHeight()) / sampleSize;
            
            Color pixel = new Color(image.getRGB(x % image.getWidth(), y % image.getHeight()));
            if (Math.abs(pixel.getRed() - pixel.getGreen()) > 10 || 
                Math.abs(pixel.getGreen() - pixel.getBlue()) > 10) {
                colorPixels++;
            }
        }
        
        double colorRatio = (double) colorPixels / sampleSize;
        boolean isGray = colorRatio < 0.1; // 少于10%的像素有明显颜色差异
        
        System.out.println("[DEBUG] 图像颜色分析: " + String.format("%.1f%%", colorRatio * 100) + 
            " 彩色像素，判断为" + (isGray ? "灰度" : "彩色") + "图像");
        return isGray;
    }
    
    /**
     * 创建颜色掩码
     */
    private static BufferedImage createColorMask(BufferedImage image, int[] hueRange, int[] satRange, int[] valRange) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixel = new Color(image.getRGB(x, y));
                
                if (isColorInRange(pixel, hueRange, satRange, valRange)) {
                    mask.setRGB(x, y, Color.WHITE.getRGB());
                } else {
                    mask.setRGB(x, y, Color.BLACK.getRGB());
                }
            }
        }
        
        return mask;
    }
    
    /**
     * 判断颜色是否在指定范围内
     */
    private static boolean isColorInRange(Color color, int[] hueRange, int[] satRange, int[] valRange) {
        float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        int hue = (int) (hsv[0] * 360);
        int sat = (int) (hsv[1] * 255);
        int val = (int) (hsv[2] * 255);
        
        boolean hueMatch = false;
        if (hueRange.length == 4) {
            // 处理跨越0度的红色
            hueMatch = (hue >= hueRange[0] && hue <= hueRange[1]) || 
                      (hue >= hueRange[2] && hue <= hueRange[3]);
        } else {
            hueMatch = hue >= hueRange[0] && hue <= hueRange[1];
        }
        
        boolean satMatch = sat >= satRange[0] && sat <= satRange[1];
        boolean valMatch = val >= valRange[0] && val <= valRange[1];
        
        return hueMatch && satMatch && valMatch;
    }
    
    /**
     * 边缘检测（简化的Sobel算子）
     */
    private static BufferedImage detectEdges(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage edges = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        // Sobel算子
        int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};
        
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int gx = 0, gy = 0;
                
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        Color pixel = new Color(image.getRGB(x + dx, y + dy));
                        int gray = (int) (0.299 * pixel.getRed() + 0.587 * pixel.getGreen() + 0.114 * pixel.getBlue());
                        
                        gx += gray * sobelX[dy + 1][dx + 1];
                        gy += gray * sobelY[dy + 1][dx + 1];
                    }
                }
                
                int magnitude = (int) Math.sqrt(gx * gx + gy * gy);
                magnitude = Math.min(255, magnitude);
                
                Color edgeColor = magnitude > 50 ? Color.WHITE : Color.BLACK;
                edges.setRGB(x, y, edgeColor.getRGB());
            }
        }
        
        return edges;
    }
    
    /**
     * 寻找圆形区域
     */
    private static List<Rectangle> findCircularRegions(BufferedImage edgeImage) {
        List<Rectangle> circles = new ArrayList<>();
        // 简化的圆形检测，实际中可以使用霍夫变换
        // 这里使用基本的形状分析
        
        List<Rectangle> allRegions = findConnectedRegions(edgeImage);
        for (Rectangle region : allRegions) {
            if (isValidSealSize(region) && hasCircularShape(edgeImage, region)) {
                circles.add(region);
            }
        }
        
        return circles;
    }
    
    /**
     * 寻找方形区域
     */
    private static List<Rectangle> findSquareRegions(BufferedImage edgeImage) {
        List<Rectangle> squares = new ArrayList<>();
        
        List<Rectangle> allRegions = findConnectedRegions(edgeImage);
        for (Rectangle region : allRegions) {
            if (isValidSealSize(region) && hasSquareShape(edgeImage, region)) {
                squares.add(region);
            }
        }
        
        return squares;
    }
    
    /**
     * 检查区域是否有高密度特征（公章通常很密集）
     */
    private static boolean hasHighDensity(BufferedImage image, Rectangle region) {
        int darkPixels = 0;
        int totalPixels = region.width * region.height;
        
        for (int y = region.y; y < region.y + region.height; y++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
                    Color pixel = new Color(image.getRGB(x, y));
                    int gray = (int) (0.299 * pixel.getRed() + 0.587 * pixel.getGreen() + 0.114 * pixel.getBlue());
                    
                    if (gray < 180) { // 较暗的像素
                        darkPixels++;
                    }
                }
            }
        }
        
        double density = (double) darkPixels / totalPixels;
        return density >= DENSITY_THRESHOLD;
    }
    
    /**
     * 检查是否有公章纹理特征
     */
    private static boolean hasSealTexture(BufferedImage image, Rectangle region) {
        // 公章通常有同心圆或规则的文字排列
        int centerX = region.x + region.width / 2;
        int centerY = region.y + region.height / 2;
        int radius = Math.min(region.width, region.height) / 2;
        
        // 检查同心圆模式
        int[] radialProfile = new int[radius];
        for (int r = 0; r < radius; r++) {
            int pixelCount = 0;
            int darkCount = 0;
            
            // 沿圆周采样
            for (int angle = 0; angle < 360; angle += 10) {
                double rad = Math.toRadians(angle);
                int x = centerX + (int) (r * Math.cos(rad));
                int y = centerY + (int) (r * Math.sin(rad));
                
                if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
                    Color pixel = new Color(image.getRGB(x, y));
                    int gray = (int) (0.299 * pixel.getRed() + 0.587 * pixel.getGreen() + 0.114 * pixel.getBlue());
                    
                    pixelCount++;
                    if (gray < 180) {
                        darkCount++;
                    }
                }
            }
            
            if (pixelCount > 0) {
                radialProfile[r] = (darkCount * 100) / pixelCount;
            }
        }
        
        // 分析径向分布，公章通常在边缘有高密度
        boolean hasEdgePattern = radialProfile[radius - 1] > 30; // 边缘有内容
        boolean hasInternalPattern = false;
        
        for (int r = radius / 4; r < radius * 3 / 4; r++) {
            if (radialProfile[r] > 20) {
                hasInternalPattern = true;
                break;
            }
        }
        
        return hasEdgePattern && hasInternalPattern;
    }
    
    // ========== 验证和过滤方法 ==========
    
    /**
     * 检查是否在页面头部区域（红头文件通常在此）
     */
    private static boolean isInHeaderRegion(BufferedImage image, Rectangle region) {
        double headerRatio = 0.2; // 页面上部20%视为头部区域
        int headerHeight = (int) (image.getHeight() * headerRatio);
        
        return region.y < headerHeight;
    }
    
    /**
     * 检查圆形度是否足够（区分圆章和文字）
     */
    private static boolean isCircularEnough(BufferedImage image, Rectangle region) {
        return calculateCircularity(region, image) >= 0.8; // 更严格的圆形度要求
    }
    
    /**
     * 检查是否有内部内容
     */
    private static boolean hasInternalContent(BufferedImage image, Rectangle region) {
        int centerX = region.x + region.width / 2;
        int centerY = region.y + region.height / 2;
        int innerRadius = Math.min(region.width, region.height) / 4;
        
        int darkPixels = 0;
        int totalPixels = 0;
        
        for (int y = centerY - innerRadius; y <= centerY + innerRadius; y++) {
            for (int x = centerX - innerRadius; x <= centerX + innerRadius; x++) {
                if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
                    double distance = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));
                    if (distance <= innerRadius) {
                        Color pixel = new Color(image.getRGB(x, y));
                        int gray = (int) (0.299 * pixel.getRed() + 0.587 * pixel.getGreen() + 0.114 * pixel.getBlue());
                        
                        totalPixels++;
                        if (gray < 200) {
                            darkPixels++;
                        }
                    }
                }
            }
        }
        
        double internalDensity = totalPixels > 0 ? (double) darkPixels / totalPixels : 0;
        return internalDensity > 0.1; // 内部至少10%的像素是内容
    }
    
    /**
     * 检查是否在正文流中
     */
    private static boolean isInMainTextFlow(BufferedImage image, Rectangle region) {
        // 简化判断：页面中部区域视为正文流
        int topMargin = image.getHeight() / 4;
        int bottomMargin = image.getHeight() * 3 / 4;
        
        return region.y > topMargin && region.y < bottomMargin;
    }
    
    /**
     * 检查高圆形度
     */
    private static boolean hasHighCircularity(BufferedImage image, Rectangle region) {
        return calculateCircularity(region, image) >= 0.85;
    }
    
    // ========== 通用工具方法 ==========
    
    /**
     * 合并重叠的候选区域
     */
    private static List<Rectangle> mergeOverlappingRegions(List<Rectangle> candidates) {
        List<Rectangle> merged = new ArrayList<>();
        boolean[] used = new boolean[candidates.size()];
        
        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) continue;
            
            Rectangle current = new Rectangle(candidates.get(i));
            used[i] = true;
            
            // 查找所有与当前区域重叠的区域
            boolean foundOverlap = true;
            while (foundOverlap) {
                foundOverlap = false;
                for (int j = i + 1; j < candidates.size(); j++) {
                    if (used[j]) continue;
                    
                    if (current.intersects(candidates.get(j))) {
                        current = current.union(candidates.get(j));
                        used[j] = true;
                        foundOverlap = true;
                    }
                }
            }
            
            merged.add(current);
        }
        
        return merged;
    }
    
    /**
     * 去除确认的公章区域
     */
    private static BufferedImage removeConfirmedSeals(BufferedImage image, List<Rectangle> sealRegions) {
        System.out.println("[DEBUG] 去除确认的公章区域...");
        
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        
        int removedCount = 0;
        
        for (Rectangle sealRegion : sealRegions) {
            System.out.println("[DEBUG] 去除公章: " + sealRegion.width + "x" + sealRegion.height + 
                             " at (" + sealRegion.x + "," + sealRegion.y + ")");
            
            // 智能填充去除公章
            intelligentSealRemoval(result, sealRegion);
            removedCount++;
        }
        
        System.out.println("[SUCCESS] ✓ 成功去除 " + removedCount + " 个公章");
        return result;
    }
    
    /**
     * 智能公章去除（保留背景纹理）
     */
    private static void intelligentSealRemoval(BufferedImage image, Rectangle sealRegion) {
        // 1. 分析周围背景
        Color avgBgColor = getAverageBackgroundColor(image, sealRegion);
        
        // 2. 检测背景纹理
        boolean hasTexture = hasBackgroundTexture(image, sealRegion);
        
        if (hasTexture) {
            // 纹理填充
            fillWithTexture(image, sealRegion);
        } else {
            // 平滑颜色填充
            fillWithSmoothColor(image, sealRegion, avgBgColor);
        }
    }
    
    /**
     * 检测背景是否有纹理
     */
    private static boolean hasBackgroundTexture(BufferedImage image, Rectangle region) {
        int margin = 15;
        int sampleSize = 100;
        List<Integer> bgGrayValues = new ArrayList<>();
        
        // 采样周围背景区域
        for (int i = 0; i < sampleSize; i++) {
            int x = region.x - margin + (int) (Math.random() * (region.width + 2 * margin));
            int y = region.y - margin + (int) (Math.random() * (region.height + 2 * margin));
            
            // 跳过公章内部区域
            if (x >= region.x && x < region.x + region.width &&
                y >= region.y && y < region.y + region.height) {
                continue;
            }
            
            if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
                Color pixel = new Color(image.getRGB(x, y));
                int gray = (int) (0.299 * pixel.getRed() + 0.587 * pixel.getGreen() + 0.114 * pixel.getBlue());
                bgGrayValues.add(gray);
            }
        }
        
        if (bgGrayValues.size() < 20) return false;
        
        // 计算方差
        double mean = bgGrayValues.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = bgGrayValues.stream()
            .mapToDouble(val -> (val - mean) * (val - mean))
            .average().orElse(0);
        
        return Math.sqrt(variance) > 15; // 标准差大于15认为有纹理
    }
    
    /**
     * 纹理填充
     */
    private static void fillWithTexture(BufferedImage image, Rectangle region) {
        int margin = 20;
        
        for (int y = region.y; y < region.y + region.height; y++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                // 从周围区域随机采样一个像素
                int sampleX, sampleY;
                int attempts = 0;
                do {
                    sampleX = region.x - margin + (int) (Math.random() * (region.width + 2 * margin));
                    sampleY = region.y - margin + (int) (Math.random() * (region.height + 2 * margin));
                    attempts++;
                } while ((sampleX >= region.x && sampleX < region.x + region.width &&
                         sampleY >= region.y && sampleY < region.y + region.height) && attempts < 10);
                
                if (sampleX >= 0 && sampleX < image.getWidth() && 
                    sampleY >= 0 && sampleY < image.getHeight()) {
                    image.setRGB(x, y, image.getRGB(sampleX, sampleY));
                }
            }
        }
    }
    
    /**
     * 平滑颜色填充
     */
    private static void fillWithSmoothColor(BufferedImage image, Rectangle region, Color baseColor) {
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 创建渐变效果，边缘更自然
        int centerX = region.x + region.width / 2;
        int centerY = region.y + region.height / 2;
        int radius = Math.min(region.width, region.height) / 2;
        
        for (int y = region.y; y < region.y + region.height; y++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                double distance = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));
                double ratio = Math.min(1.0, distance / radius);
                
                // 边缘混合
                if (ratio > 0.8) {
                    Color originalPixel = new Color(image.getRGB(x, y));
                    double blendFactor = (ratio - 0.8) / 0.2; // 0.8-1.0 映射到 0-1
                    
                    int r = (int) (baseColor.getRed() * (1 - blendFactor) + originalPixel.getRed() * blendFactor);
                    int g = (int) (baseColor.getGreen() * (1 - blendFactor) + originalPixel.getGreen() * blendFactor);
                    int b = (int) (baseColor.getBlue() * (1 - blendFactor) + originalPixel.getBlue() * blendFactor);
                    
                    image.setRGB(x, y, new Color(r, g, b).getRGB());
                } else {
                    image.setRGB(x, y, baseColor.getRGB());
                }
            }
        }
        
        g2d.dispose();
    }
    
    // ========== 基础工具方法 ==========
    
    private static String getImageTypeDescription(BufferedImage image) {
        switch (image.getType()) {
            case BufferedImage.TYPE_INT_RGB: return "RGB彩色";
            case BufferedImage.TYPE_BYTE_GRAY: return "灰度";
            case BufferedImage.TYPE_BYTE_BINARY: return "二值化";
            default: return "其他(" + image.getType() + ")";
        }
    }
    
    private static List<Rectangle> findConnectedRegions(BufferedImage mask) {
        List<Rectangle> regions = new ArrayList<>();
        boolean[][] visited = new boolean[mask.getHeight()][mask.getWidth()];
        
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if (!visited[y][x] && isWhitePixel(mask, x, y)) {
                    Rectangle region = floodFill(mask, visited, x, y);
                    if (region != null) {
                        regions.add(region);
                    }
                }
            }
        }
        
        return regions;
    }
    
    private static boolean isValidSealSize(Rectangle region) {
        return region.width >= MIN_SEAL_SIZE && region.height >= MIN_SEAL_SIZE &&
               region.width <= MAX_SEAL_SIZE && region.height <= MAX_SEAL_SIZE;
    }
    
    private static boolean hasCircularShape(BufferedImage mask, Rectangle region) {
        return calculateCircularity(region, mask) >= CIRCULARITY_THRESHOLD;
    }
    
    private static boolean hasSquareShape(BufferedImage mask, Rectangle region) {
        double aspectRatio = (double) region.width / region.height;
        return aspectRatio >= 0.8 && aspectRatio <= 1.25; // 接近正方形
    }
    
    private static Rectangle floodFill(BufferedImage mask, boolean[][] visited, int startX, int startY) {
        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        
        List<Point> stack = new ArrayList<>();
        stack.add(new Point(startX, startY));
        visited[startY][startX] = true;
        
        while (!stack.isEmpty()) {
            Point current = stack.remove(stack.size() - 1);
            int x = current.x;
            int y = current.y;
            
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            
            int[][] directions = {{0,1}, {0,-1}, {1,0}, {-1,0}};
            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                
                if (nx >= 0 && nx < mask.getWidth() && ny >= 0 && ny < mask.getHeight() 
                    && !visited[ny][nx] && isWhitePixel(mask, nx, ny)) {
                    visited[ny][nx] = true;
                    stack.add(new Point(nx, ny));
                }
            }
        }
        
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
    
    private static boolean isWhitePixel(BufferedImage mask, int x, int y) {
        Color pixel = new Color(mask.getRGB(x, y));
        return pixel.getRed() > 128;
    }
    
    private static double calculateCircularity(Rectangle region, BufferedImage mask) {
        int centerX = region.x + region.width / 2;
        int centerY = region.y + region.height / 2;
        int radius = Math.min(region.width, region.height) / 2;
        
        int pixelsInCircle = 0;
        int whitePixelsInCircle = 0;
        
        for (int y = region.y; y < region.y + region.height; y++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                double distance = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));
                if (distance <= radius) {
                    pixelsInCircle++;
                    if (isWhitePixel(mask, x, y)) {
                        whitePixelsInCircle++;
                    }
                }
            }
        }
        
        return pixelsInCircle > 0 ? (double) whitePixelsInCircle / pixelsInCircle : 0;
    }
    
    private static Color getAverageBackgroundColor(BufferedImage image, Rectangle sealRegion) {
        int margin = 10;
        int startX = Math.max(0, sealRegion.x - margin);
        int endX = Math.min(image.getWidth(), sealRegion.x + sealRegion.width + margin);
        int startY = Math.max(0, sealRegion.y - margin);
        int endY = Math.min(image.getHeight(), sealRegion.y + sealRegion.height + margin);
        
        long totalR = 0, totalG = 0, totalB = 0;
        int pixelCount = 0;
        
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                if (x >= sealRegion.x && x < sealRegion.x + sealRegion.width &&
                    y >= sealRegion.y && y < sealRegion.y + sealRegion.height) {
                    continue;
                }
                
                Color pixel = new Color(image.getRGB(x, y));
                totalR += pixel.getRed();
                totalG += pixel.getGreen();
                totalB += pixel.getBlue();
                pixelCount++;
            }
        }
        
        if (pixelCount == 0) {
            return Color.WHITE;
        }
        
        int avgR = (int) (totalR / pixelCount);
        int avgG = (int) (totalG / pixelCount);
        int avgB = (int) (totalB / pixelCount);
        
        return new Color(avgR, avgG, avgB);
    }
}