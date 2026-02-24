package com.bidguard.sealremover;

import java.awt.image.BufferedImage;

/**
 * LAB色彩空间印章去除器 - 压制色度、保留亮度（黑字完整保留）
 *
 * <p>核心思路：不"删除红色"，而是"压制红色的色度影响，保留亮度结构"。
 *
 * <p>流程：
 * <ol>
 *   <li>RGB  LAB（L=亮度，A=红绿轴，B=蓝黄轴）</li>
 *   <li>动态识别红色：A > (均值 + K标准差)，且 L > 暗色阈值（排除黑字）</li>
 *   <li>对红色区域将 A/B 通道向中性（0）回归（alpha=0.85）</li>
 *   <li>将 L 通道直接映射为灰度输出（彻底消除残余红色，OCR友好）</li>
 *   <li>对灰度图做全局对比度拉伸（恢复章下被压暗的文字边缘）</li>
 * </ol>
 *
 * <p>为什么黑字不会丢失：
 * <ul>
 *   <li>黑字像素 L 通道极低（接近0），不满足 L > MIN_L_FOR_RED，跳过压制</li>
 *   <li>输出的灰度值直接来自 L 通道，黑字边缘的 L 值与原图完全一致</li>
 * </ul>
 */
public class LabSealRemover {

    /** A通道压制强度（0=不处理，1=完全去色度） */
    private static final float ALPHA = 0.85f;

    /** 动态红色阈值系数：threshold = meanA + K * stdA */
    private static final float K = 0.8f;

    /**
     * L通道最低亮度（0-100），低于此视为深色像素（黑字/暗纹），跳过压制。
     * 值越小，越多暗像素被跳过（更安全）；值越大，章下文字越难被误处理。
     */
    private static final float MIN_L_FOR_RED = 30.0f;

    // D65 参考白点
    private static final float Xn = 0.95047f;
    private static final float Yn = 1.00000f;
    private static final float Zn = 1.08883f;

    // -----------------------------------------------------------------------

    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) { System.err.println("[LabSealRemover] 输入为空"); return null; }
        int w = image.getWidth(), h = image.getHeight();
        System.out.println("===== [LabSealRemover] 开始处理 =====");
        System.out.println("  图像尺寸: " + w + "x" + h);

        // ---- Step 1: 全图 RGB  LAB ----
        float[][] Lch = new float[w][h];
        float[][] Ach = new float[w][h];
        float[][] Bch = new float[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float[] lab = rgbToLab(image.getRGB(x, y));
                Lch[x][y] = lab[0];
                Ach[x][y] = lab[1];
                Bch[x][y] = lab[2];
            }
        }

        // ---- Step 2: 动态计算 A 通道均值 + 标准差 ----
        double sumA = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) sumA += Ach[x][y];
        double meanA = sumA / ((long) w * h);

        double sumSq = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            double d = Ach[x][y] - meanA; sumSq += d * d;
        }
        double stdA = Math.sqrt(sumSq / ((long) w * h));
        float threshold = (float) (meanA + K * stdA);
        System.out.printf("  A通道统计: 均值=%.2f 标准差=%.2f 印章阈值=%.2f%n",
            meanA, stdA, threshold);

        // ---- Step 3: 对红色区域压制 A/B 通道 ----
        int suppressed = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (Ach[x][y] > threshold && Lch[x][y] > MIN_L_FOR_RED) {
                    Ach[x][y] *= (1f - ALPHA);        // A 向 0 回归
                    Bch[x][y] *= (1f - ALPHA * 0.5f); // B 轻度压制
                    suppressed++;
                }
            }
        }
        System.out.println("  色度压制像素: " + suppressed);

        // ---- Step 4: 输出灰度（直接用 L 通道，彻底消除残余红色） ----
        // L 范围 0-100  gray 0-255
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int g = clamp(Math.round(Lch[x][y] * 2.55f));
                gray.setRGB(x, y, (g << 16) | (g << 8) | g);
            }
        }

        // ---- Step 5: 对比度拉伸（恢复章下被压暗的文字边缘） ----
        BufferedImage result = contrastStretch(gray, w, h);
        System.out.println("===== [LabSealRemover] 处理完成 =====");
        return result;
    }

    // -----------------------------------------------------------------------
    // 对比度拉伸（1% - 99% 分位线性拉伸）
    // -----------------------------------------------------------------------

    private static BufferedImage contrastStretch(BufferedImage img, int w, int h) {
        int[] hist = new int[256];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            hist[(img.getRGB(x, y) >> 16) & 0xFF]++;

        int total = w * h;
        int low = 0, high = 255;
        int cum = 0;
        for (int i = 0; i < 256; i++) { cum += hist[i]; if (cum < total * 0.01) low = i; }
        cum = 0;
        for (int i = 255; i >= 0; i--) { cum += hist[i]; if (cum < total * 0.01) high = i; }

        if (high <= low) return img;
        float scale = 255f / (high - low);
        System.out.printf("  对比增强: [%d,%d]  [0,255]%n", low, high);

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int g = (img.getRGB(x, y) >> 16) & 0xFF;
                int ng = clamp(Math.round((g - low) * scale));
                result.setRGB(x, y, (ng << 16) | (ng << 8) | ng);
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // RGB  LAB 转换（sRGB, D65）
    // -----------------------------------------------------------------------

    private static float[] rgbToLab(int rgb) {
        float r = srgbLinear(((rgb >> 16) & 0xFF) / 255f);
        float g = srgbLinear(((rgb >> 8) & 0xFF) / 255f);
        float b = srgbLinear((rgb & 0xFF) / 255f);

        // Linear sRGB  XYZ (D65)
        float X = 0.4124564f*r + 0.3575761f*g + 0.1804375f*b;
        float Y = 0.2126729f*r + 0.7151522f*g + 0.0721750f*b;
        float Z = 0.0193339f*r + 0.1191920f*g + 0.9503041f*b;

        // XYZ  LAB
        float fx = labF(X / Xn), fy = labF(Y / Yn), fz = labF(Z / Zn);
        return new float[]{116*fy - 16, 500*(fx - fy), 200*(fy - fz)};
    }

    private static float srgbLinear(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    private static float labF(float t) {
        return t > 0.008856f ? (float) Math.cbrt(t) : 7.787f * t + 16f / 116f;
    }

    private static int clamp(long v) { return (int) Math.min(255, Math.max(0, v)); }
}