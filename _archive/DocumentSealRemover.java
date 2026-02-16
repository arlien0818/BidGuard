package com.bidguard;

import java.awt.image.BufferedImage;

/**
 * 扫描文档红章去除器 - 基于HSV色彩空间和形态学处理
 *
 * 核心思路：
 * 1. RGB转HSV，用色相范围+饱和度提取红色掩膜（允许颜色漂移）
 * 2. 形态学膨胀+闭运算，连接断裂的圆环和文字
 * 3. 基于掩膜保持亮度结构，仅削弱红色通道
 */
public class DocumentSealRemover {

    // 红色色相范围（HSV中红色在0°和360°附近）
    private static final float HUE_RED_LOW1 = 0f;
    private static final float HUE_RED_HIGH1 = 25f;   // 0-25度
    private static final float HUE_RED_LOW2 = 335f;   // 335-360度
    private static final float HUE_RED_HIGH2 = 360f;

    // 饱和度和亮度阈值
    private static final float MIN_SATURATION = 0.25f;  // 最小饱和度（允许较淡的红色）
    private static final float MIN_VALUE = 0.20f;       // 最小亮度

    // 形态学操作参数
    private static final int DILATE_RADIUS = 2;    // 膨胀半径
    private static final int CLOSE_RADIUS = 5;     // 闭运算半径

    /**
     * 主方法：去除扫描文档中的红色印章
     */
    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) {
            System.err.println("[ERROR] 输入图像为空");
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        System.out.println("========================================");
        System.out.println("[DocumentSealRemover] 开始处理");
        System.out.println("图像尺寸: " + width + "x" + height);
        System.out.println("========================================");

        // 步骤1: 创建红色掩膜（基于HSV色彩空间）
        System.out.println("[步骤1] 基于HSV创建红色掩膜...");
        boolean[][] redMask = createRedMaskHSV(image);
        int initialRedPixels = countTrue(redMask);
        System.out.println("  初始红色像素: " + initialRedPixels);

        if (initialRedPixels == 0) {
            System.out.println("[警告] 未检测到红色区域，返回原图");
            return image;
        }

        // 步骤2: 形态学处理 - 膨胀+闭运算
        System.out.println("[步骤2] 形态学处理（膨胀+闭运算）...");
        boolean[][] dilatedMask = dilate(redMask, width, height, DILATE_RADIUS);
        boolean[][] closedMask = morphologicalClose(dilatedMask, width, height, CLOSE_RADIUS);
        int finalMaskPixels = countTrue(closedMask);
        System.out.println("  处理后掩膜像素: " + finalMaskPixels);

        // 步骤3: 基于掩膜去除印章（保持亮度结构）
        System.out.println("[步骤3] 去除印章（保持亮度结构）...");
        BufferedImage result = removeSealPreservingStructure(image, closedMask);

        System.out.println("========================================");
        System.out.println("[完成] 印章去除完成");
        System.out.println("========================================");

        return result;
    }

    /**
     * 基于HSV色彩空间创建红色掩膜
     */
    private static boolean[][] createRedMaskHSV(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] mask = new boolean[width][height];

        float[] hsv = new float[3];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // RGB转HSV
                rgbToHsv(r, g, b, hsv);
                float hue = hsv[0];         // 0-360
                float saturation = hsv[1];  // 0-1
                float value = hsv[2];       // 0-1

                // 判断是否为红色区域
                boolean isRedHue = (hue >= HUE_RED_LOW1 && hue <= HUE_RED_HIGH1) ||
                                   (hue >= HUE_RED_LOW2 && hue <= HUE_RED_HIGH2);
                boolean hasSaturation = saturation >= MIN_SATURATION;
                boolean hasValue = value >= MIN_VALUE;

                mask[x][y] = isRedHue && hasSaturation && hasValue;
            }
        }

        return mask;
    }

    /**
     * RGB转HSV
     */
    private static void rgbToHsv(int r, int g, int b, float[] hsv) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        // Value
        hsv[2] = max;

        // Saturation
        if (max == 0) {
            hsv[1] = 0;
        } else {
            hsv[1] = delta / max;
        }

        // Hue
        if (delta == 0) {
            hsv[0] = 0;
        } else if (max == rf) {
            hsv[0] = 60 * (((gf - bf) / delta) % 6);
        } else if (max == gf) {
            hsv[0] = 60 * (((bf - rf) / delta) + 2);
        } else {
            hsv[0] = 60 * (((rf - gf) / delta) + 4);
        }

        if (hsv[0] < 0) {
            hsv[0] += 360;
        }
    }

    /**
     * 形态学膨胀
     */
    private static boolean[][] dilate(boolean[][] mask, int width, int height, int radius) {
        boolean[][] result = new boolean[width][height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[x][y]) {
                    // 膨胀：将周围像素也标记为true
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                // 使用圆形结构元素
                                if (dx * dx + dy * dy <= radius * radius) {
                                    result[nx][ny] = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 形态学腐蚀
     */
    private static boolean[][] erode(boolean[][] mask, int width, int height, int radius) {
        boolean[][] result = new boolean[width][height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[x][y]) {
                    boolean allNeighbors = true;

                    // 检查圆形邻域内是否全为true
                    outer:
                    for (int dy = -radius; dy <= radius && allNeighbors; dy++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            if (dx * dx + dy * dy <= radius * radius) {
                                int nx = x + dx;
                                int ny = y + dy;
                                if (nx < 0 || nx >= width || ny < 0 || ny >= height || !mask[nx][ny]) {
                                    allNeighbors = false;
                                    break outer;
                                }
                            }
                        }
                    }

                    result[x][y] = allNeighbors;
                }
            }
        }

        return result;
    }

    /**
     * 形态学闭运算（先膨胀后腐蚀）- 连接断裂区域
     */
    private static boolean[][] morphologicalClose(boolean[][] mask, int width, int height, int radius) {
        boolean[][] dilated = dilate(mask, width, height, radius);
        return erode(dilated, width, height, radius);
    }

    /**
     * 去除印章同时保持亮度结构
     *
     * 方法：在掩膜区域内，保持原始亮度，但将颜色转为灰度
     * 这样可以保留文字的边缘和结构，同时去除红色
     */
    private static BufferedImage removeSealPreservingStructure(BufferedImage image, boolean[][] mask) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // 先计算掩膜区域周围的背景亮度
        int bgBrightness = estimateBackgroundBrightness(image, mask);
        System.out.println("  估算背景亮度: " + bgBrightness);

        int processedPixels = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);

                if (mask[x][y]) {
                    // 在掩膜区域内：去除红色，保持亮度结构
                    int newRgb = removeRedPreserveLuminance(rgb, bgBrightness);
                    result.setRGB(x, y, newRgb);
                    processedPixels++;
                } else {
                    // 掩膜外：保持原样
                    result.setRGB(x, y, rgb);
                }
            }
        }

        System.out.println("  处理像素数: " + processedPixels);

        return result;
    }

    /**
     * 估算掩膜区域周围的背景亮度
     */
    private static int estimateBackgroundBrightness(BufferedImage image, boolean[][] mask) {
        int width = image.getWidth();
        int height = image.getHeight();

        long totalBrightness = 0;
        int count = 0;

        // 找掩膜边缘附近的非红色像素
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[x][y]) {
                    // 检查是否在掩膜边缘附近
                    boolean nearMask = false;
                    for (int dy = -3; dy <= 3 && !nearMask; dy++) {
                        for (int dx = -3; dx <= 3; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height && mask[nx][ny]) {
                                nearMask = true;
                                break;
                            }
                        }
                    }

                    if (nearMask) {
                        int rgb = image.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        int brightness = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                        totalBrightness += brightness;
                        count++;
                    }
                }
            }
        }

        if (count == 0) {
            return 250; // 默认接近白色
        }

        return (int) (totalBrightness / count);
    }

    /**
     * 去除红色但保持亮度结构
     *
     * 策略：
     * 1. 计算原始像素的亮度
     * 2. 将红色通道的影响降低，向背景亮度靠拢
     * 3. 保持亮度差异以保留结构（如文字边缘）
     */
    private static int removeRedPreserveLuminance(int rgb, int bgBrightness) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // 计算原始亮度
        float originalLuminance = 0.299f * r + 0.587f * g + 0.114f * b;

        // 计算红色对亮度的额外贡献
        // 如果去掉红色，亮度会下降多少
        float nonRedLuminance = 0.587f * g + 0.114f * b;

        // 计算亮度比例（保持结构）
        float luminanceRatio = originalLuminance / 255f;

        // 新的亮度：向背景亮度靠拢，但保持一定的结构
        // 亮度越低（如文字笔画处）保持越暗，亮度越高越接近背景
        float targetLuminance;

        if (originalLuminance < bgBrightness * 0.7f) {
            // 较暗区域（可能是文字）：保持较暗但去除红色
            targetLuminance = originalLuminance * 0.9f + nonRedLuminance * 0.1f;
        } else {
            // 较亮区域（印章的非文字部分）：直接用背景色
            targetLuminance = bgBrightness * 0.95f + originalLuminance * 0.05f;
        }

        // 限制范围
        int newGray = Math.min(255, Math.max(0, (int) targetLuminance));

        return (newGray << 16) | (newGray << 8) | newGray;
    }

    /**
     * 统计掩膜中true的数量
     */
    private static int countTrue(boolean[][] mask) {
        int count = 0;
        for (boolean[] row : mask) {
            for (boolean val : row) {
                if (val) count++;
            }
        }
        return count;
    }
}
