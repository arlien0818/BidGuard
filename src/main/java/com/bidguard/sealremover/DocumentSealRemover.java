package com.bidguard.sealremover;

import java.awt.image.BufferedImage;

/**
 * 扫描文档红章去除器 - HSV检测 + 直接背景色替换
 *
 * 核心思路：
 * 1. RGB->HSV 宽范围检测红/粉红色（覆盖 #BF476D ~341度）
 * 2. 不做膨胀，避免扩展到相邻文字像素
 * 3. 仅对确认为红色的像素用全局背景色替换
 */
public class DocumentSealRemover {

    /* HSV 色相范围 */
    private static final float HUE_LOW1  = 0f;
    private static final float HUE_HIGH1 = 30f;   // 0-30 度（橙红->红）
    private static final float HUE_LOW2  = 300f;
    private static final float HUE_HIGH2 = 360f;  // 300-360 度（紫红->红，覆盖 #BF476D ~341度）

    private static final float MIN_SATURATION = 0.15f;
    private static final float MIN_VALUE      = 0.18f;

    /** 最小连续红色区域像素数（低于此视为散点噪声，不处理） */
    private static final int MIN_REGION_SIZE = 30;

    /** 形态学膨胀半径（设为0可彻底不膨胀，设为1仅扩1像素边缘） */
    private static final int DILATE_RADIUS = 1;

    // ------------------------------------------------------------------
    // 公共 API
    // ------------------------------------------------------------------

    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) {
            System.err.println("[DocumentSealRemover] 输入图像为空");
            return null;
        }

        int w = image.getWidth();
        int h = image.getHeight();
        System.out.println("===== [DocumentSealRemover] 开始处理 =====");
        System.out.println("  图像尺寸: " + w + "x" + h);

        // 1. 红色掩膜
        boolean[][] mask = createRedMask(image);
        int initial = countTrue(mask, w, h);
        System.out.println("  初始红色像素: " + initial);
        if (initial == 0) {
            System.out.println("  未检测到红色区域，返回原图");
            return image;
        }

        // 2. 小范围膨胀（仅1像素，确保边缘过渡自然）
        boolean[][] finalMask = (DILATE_RADIUS > 0)
            ? dilate(mask, w, h, DILATE_RADIUS) : mask;
        System.out.println("  掩膜像素（膨胀后）: " + countTrue(finalMask, w, h));

        // 3. 估算全局背景色
        int bgColor = estimateGlobalBackground(image);
        System.out.printf("  全局背景色: #%06X%n", bgColor & 0xFFFFFF);

        // 4. 替换
        BufferedImage result = replaceWithBg(image, finalMask, bgColor);
        System.out.println("===== [DocumentSealRemover] 处理完成 =====");
        return result;
    }

    /**
     * 判断像素是否为红色印章色（供其他类复用）。
     */
    public static boolean isRedSealColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // 必须 R > G 且 R > B，排除绿/蓝色误判
        if (r <= g || r <= b) return false;

        float[] hsv = rgbToHsv(r, g, b);
        float hue = hsv[0], sat = hsv[1], val = hsv[2];

        if (sat < MIN_SATURATION || val < MIN_VALUE) return false;

        return (hue >= HUE_LOW1 && hue <= HUE_HIGH1)
            || (hue >= HUE_LOW2 && hue <= HUE_HIGH2);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private static boolean[][] createRedMask(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        boolean[][] mask = new boolean[w][h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                mask[x][y] = isRedSealColor(image.getRGB(x, y));
        return mask;
    }

    private static boolean[][] dilate(boolean[][] mask, int w, int h, int radius) {
        boolean[][] result = new boolean[w][h];
        int r2 = radius * radius;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask[x][y]) continue;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (dx * dx + dy * dy > r2) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h)
                            result[nx][ny] = true;
                    }
                }
            }
        }
        return result;
    }

    /**
     * 对掩膜内【且仍为红色】的像素填充背景色，其余像素（含印章下方的黑字）保持原值。
     * 二次红色判断是关键：膨胀/掩膜可能覆盖了紧邻印章的深色文字像素，不能无脑替换。
     */
    private static BufferedImage replaceWithBg(BufferedImage image,
                                               boolean[][] mask, int bgColor) {
        int w = image.getWidth(), h = image.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int processed = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int orig = image.getRGB(x, y);
                // 只替换掩膜内且像素本身仍是红色的，黑色文字像素原样保留
                if (mask[x][y] && isRedSealColor(orig)) {
                    result.setRGB(x, y, bgColor);
                    processed++;
                } else {
                    result.setRGB(x, y, orig);
                }
            }
        }
        System.out.println("  替换像素数: " + processed);
        return result;
    }

    /** 取四角各 30x30 区域的非红色像素均值作为背景色 */
    static int estimateGlobalBackground(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int size = Math.min(30, Math.min(w, h));
        long sumR = 0, sumG = 0, sumB = 0;
        int cnt = 0;
        int[][] corners = {{0, 0}, {w - size, 0}, {0, h - size}, {w - size, h - size}};
        for (int[] c : corners) {
            for (int y = c[1]; y < c[1] + size && y < h; y++) {
                for (int x = c[0]; x < c[0] + size && x < w; x++) {
                    int rgb = image.getRGB(x, y);
                    if (!isRedSealColor(rgb)) {
                        sumR += (rgb >> 16) & 0xFF;
                        sumG += (rgb >> 8) & 0xFF;
                        sumB += rgb & 0xFF;
                        cnt++;
                    }
                }
            }
        }
        if (cnt == 0) return 0xF0F0F0;
        return ((int)(sumR/cnt) << 16) | ((int)(sumG/cnt) << 8) | (int)(sumB/cnt);
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r/255f, gf = g/255f, bf = b/255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float hue = 0, sat = (max == 0) ? 0 : delta / max, val = max;
        if (delta != 0) {
            if      (max == rf) hue = 60 * (((gf - bf) / delta) % 6);
            else if (max == gf) hue = 60 * (((bf - rf) / delta) + 2);
            else                hue = 60 * (((rf - gf) / delta) + 4);
            if (hue < 0) hue += 360;
        }
        return new float[]{hue, sat, val};
    }

    private static int countTrue(boolean[][] mask, int w, int h) {
        int n = 0;
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                if (mask[x][y]) n++;
        return n;
    }
}