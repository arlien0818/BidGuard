package com.bidguard.sealremover;

import java.awt.image.BufferedImage;

/**
 * 红色通道扣除法去章器（方案一，最实用）
 *
 */
public class RedChannelSealRemover {

    /**
     * 处理图像，去除红色印章同时保留红章下的文字。
     *
     * @param image 输入图像（彩色扫描件）
     * @return 处理后图像（TYPE_INT_RGB）
     */
    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) return null;
        int w = image.getWidth(), h = image.getHeight();
        System.out.println("[RedChannelSealRemover6] " + w + "x" + h);

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int processed = 0;
        // redness分布统计,用于分析印章颜色特征,先不要删除，后续版本可根据分布调整算法参数
        // java.util.Map<Integer, Integer> rednessCount = new java.util.HashMap<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);

                if (DocumentSealRemover.isRedSealColor(rgb)) {
                //把红色压回基准
                //降低颜色饱和度（防止变暗色块）
                //再整体提亮
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8)  & 0xFF;
                    int b =  rgb        & 0xFF;

                    // ===== 1. 计算红色强度 =====
                    int base = Math.max(g, b);
                    int redness = r - base;

                    if (redness > 0) {

                        // ===== 2. 强力削弱红通道 =====
                        r = base;

                        // ===== 3. 去饱和（往灰色方向压）=====
                        int avg = (r + g + b) / 3;
                        g = (g + avg) / 2;
                        b = (b + avg) / 2;

                        // ===== 4. 整体提亮（核心）=====
                        int lift = redness * 2;   // 可以改成 *3 如果还不够
                        r = Math.min(255, r + lift);
                        g = Math.min(255, g + lift);
                        b = Math.min(255, b + lift);

                        // ===== 5. 轻微压暗低亮度像素 =====
                        int gray = (r + g + b) / 3;
                        if (gray < 120) {
                            r = (int)(r * 0.9);
                            g = (int)(g * 0.9);
                            b = (int)(b * 0.9);
                        }
                    }

                    int newRGB = (r << 16) | (g << 8) | b;
                    result.setRGB(x, y, newRGB);
                    processed++;

                } else {
                    result.setRGB(x, y, rgb);
                }
            }
        }

        System.out.println("[RedChannelSealRemover4] 处理红色像素: " + processed
                + " (" + String.format("%.2f%%", processed * 100.0 / (w * h)) + ")");
        // 打印redness分布
        // System.out.println("[RedChannelSealRemover] redness分布：");
        // rednessCount.entrySet().stream()
        // .sorted(java.util.Map.Entry.comparingByKey())
        // .forEach(e -> System.out.println(" redness=" + e.getKey() + " : " +
        // e.getValue() + "个"));
        return result;
    }
}
