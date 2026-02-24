package com.bidguard.image;

import com.bidguard.sealremover.DocumentSealRemover;

import java.awt.image.BufferedImage;

/**
 * 简单红色像素替换 - 速度最快
 *
 * <p>逐像素检测红色（复用 {@link DocumentSealRemover#isRedSealColor(int)}），
 * 命中即替换为估算的背景色。不做连通区域分析，不做形态学处理。</p>
 */
public class SimpleSealRemover {

    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) {
            System.err.println("[SimpleSealRemover] 输入图像为空");
            return null;
        }

        int w = image.getWidth();
        int h = image.getHeight();

        System.out.println("===== [SimpleSealRemover] 开始处理 =====");
        System.out.println("  图像尺寸: " + w + "x" + h);

        // 估算背景色（取四角）
        int bgColor = estimateBackground(image);
        System.out.println("  估算背景色: #" + String.format("%06X", bgColor & 0xFFFFFF));

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int removed = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                if (DocumentSealRemover.isRedSealColor(rgb)) {
                    result.setRGB(x, y, bgColor);
                    removed++;
                } else {
                    result.setRGB(x, y, rgb);
                }
            }
        }

        System.out.println("  替换像素数: " + removed);
        System.out.println("===== [SimpleSealRemover] 处理完成 =====");
        return result;
    }

    private static int estimateBackground(BufferedImage image) {
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
                    if (!DocumentSealRemover.isRedSealColor(rgb)) {
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
}
