package com.bidguard.sealremover;

import java.awt.image.BufferedImage;

/**
 * 扫描文档红章去除器 - 基于HSV色彩空间检测 + 邻域均值填充
 *
 * <p>核心思路：
 * <ol>
 *   <li>RGB->HSV，用宽泛的色相+饱和度范围提取红/粉红色掩膜（覆盖 #BF476D 等偏粉红的印章色）</li>
 *   <li>形态学膨胀，将掩膜边缘扩大，确保边缘残留也被处理</li>
 *   <li>对掩膜区域逐像素取周围非红色邻居的均值颜色进行填充</li>
 * </ol>
 *
 * <p>可被 {@link PreciseSealRemover} 和 SimpleSealRemover 复用的关键方法：
 * {@link #isRedSealColor(int)}  判断单个像素是否属于红色印章色。
 */
public class DocumentSealRemover {

    /* ====== 红色检测参数（HSV） ====== */

    /** 色相范围1: 0-30 度（橙红 -> 红） */
    private static final float HUE_LOW1 = 0f;
    private static final float HUE_HIGH1 = 30f;

    /** 色相范围2: 300-360 度（紫红 -> 红，覆盖 #BF476D ~= 341 度） */
    private static final float HUE_LOW2 = 300f;
    private static final float HUE_HIGH2 = 360f;

    /** 最低饱和度（扫描件背景偏灰，需要放宽） */
    private static final float MIN_SATURATION = 0.15f;

    /** 最低亮度 */
    private static final float MIN_VALUE = 0.18f;

    /* ====== 形态学 / 填充参数 ====== */

    /** 膨胀半径（像素），用于扩大掩膜边缘 */
    private static final int DILATE_RADIUS = 2;

    /** 邻域填充搜索半径 */
    private static final int FILL_SEARCH_RADIUS = 5;

    // ------------------------------------------------------------------
    //  公共 API
    // ------------------------------------------------------------------

    /**
     * 去除图像中的红色印章。
     *
     * @param image 输入图像（扫描件渲染的 BufferedImage）
     * @return 去章后的图像；如果未检测到红色区域则返回原图
     */
    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) {
            System.err.println("[DocumentSealRemover] 输入图像为空");
            return null;
        }

        int w = image.getWidth();
        int h = image.getHeight();
        System.out.println("===== [DocumentSealRemover] 开始处理 =====");
        System.out.println("  图像尺寸: " + w + "x" + h);

        // 1. 创建红色掩膜
        boolean[][] mask = createRedMask(image);
        int initial = countTrue(mask, w, h);
        System.out.println("  初始红色像素: " + initial);
        if (initial == 0) {
            System.out.println("  未检测到红色区域，返回原图");
            return image;
        }

        // 2. 膨胀
        boolean[][] dilated = dilate(mask, w, h, DILATE_RADIUS);
        System.out.println("  膨胀后像素: " + countTrue(dilated, w, h));

        // 3. 邻域均值填充
        BufferedImage result = fillWithNeighborAverage(image, dilated);
        System.out.println("===== [DocumentSealRemover] 处理完成 =====");
        return result;
    }

    /**
     * 判断一个像素是否属于"红色印章色"。
     * <p>公开供 {@link PreciseSealRemover} 和 SimpleSealRemover 复用。
     *
     * @param rgb 32-bit ARGB / RGB 值（仅低 24 位有效）
     * @return true = 该像素被视为红色印章像素
     */
    public static boolean isRedSealColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        float[] hsv = rgbToHsv(r, g, b);
        float hue = hsv[0]; // 0-360
        float sat = hsv[1]; // 0-1
        float val = hsv[2]; // 0-1

        if (sat < MIN_SATURATION || val < MIN_VALUE) return false;

        // 红色需要 R > G 且 R > B（排除绿色/蓝色区域被错误捕获）
        if (r <= g || r <= b) return false;

        return (hue >= HUE_LOW1 && hue <= HUE_HIGH1)
            || (hue >= HUE_LOW2 && hue <= HUE_HIGH2);
    }

    // ------------------------------------------------------------------
    //  内部方法
    // ------------------------------------------------------------------

    private static boolean[][] createRedMask(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[][] mask = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mask[x][y] = isRedSealColor(image.getRGB(x, y));
            }
        }
        return mask;
    }

    /** 形态学膨胀（圆形结构元素） */
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
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                            result[nx][ny] = true;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * 对掩膜内的像素用邻域非红色像素的均值颜色替换。
     * 如果周围全是红色，则使用更大范围或回退到估算的背景色。
     */
    private static BufferedImage fillWithNeighborAverage(BufferedImage image, boolean[][] mask) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        // 先估算全局背景色（取四角非红色像素均值）
        int fallbackBg = estimateGlobalBackground(image);
        int processed = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask[x][y]) {
                    result.setRGB(x, y, image.getRGB(x, y));
                    continue;
                }
                // 搜索周围非红色像素的平均色
                long sumR = 0, sumG = 0, sumB = 0;
                int cnt = 0;
                int sr = FILL_SEARCH_RADIUS;
                for (int dy = -sr; dy <= sr; dy++) {
                    for (int dx = -sr; dx <= sr; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h && !mask[nx][ny]) {
                            int c = image.getRGB(nx, ny);
                            sumR += (c >> 16) & 0xFF;
                            sumG += (c >> 8) & 0xFF;
                            sumB += c & 0xFF;
                            cnt++;
                        }
                    }
                }
                int fill;
                if (cnt > 0) {
                    int avgR = (int) (sumR / cnt);
                    int avgG = (int) (sumG / cnt);
                    int avgB = (int) (sumB / cnt);
                    fill = (avgR << 16) | (avgG << 8) | avgB;
                } else {
                    fill = fallbackBg;
                }
                result.setRGB(x, y, fill);
                processed++;
            }
        }
        System.out.println("  替换像素数: " + processed);
        return result;
    }

    /** 估算全局背景色：取四角各 20x20 区域的非红色像素均值 */
    private static int estimateGlobalBackground(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int size = Math.min(20, Math.min(w, h));
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
        return ((int) (sumR / cnt) << 16) | ((int) (sumG / cnt) << 8) | (int) (sumB / cnt);
    }

    /** RGB -> HSV (H: 0-360, S: 0-1, V: 0-1) */
    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float hue = 0, sat, val = max;
        sat = (max == 0) ? 0 : delta / max;
        if (delta != 0) {
            if (max == rf) hue = 60 * (((gf - bf) / delta) % 6);
            else if (max == gf) hue = 60 * (((bf - rf) / delta) + 2);
            else hue = 60 * (((rf - gf) / delta) + 4);
            if (hue < 0) hue += 360;
        }
        return new float[]{hue, sat, val};
    }

    private static int countTrue(boolean[][] mask, int w, int h) {
        int count = 0;
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                if (mask[x][y]) count++;
        return count;
    }
}
