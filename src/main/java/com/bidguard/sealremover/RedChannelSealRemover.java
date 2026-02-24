package com.bidguard.sealremover;

import java.awt.image.BufferedImage;

/**
 * 红色通道扣除法去章器（方案一，最实用）
 *
 * <p>原理：扫描件中红色印章的红墨水叠加在纸面上，但不影响 G/B 通道的原始内容。
 * <ul>
 *   <li>纯红章区域（无文字）：R 高，G/B 偏高 → min(G,B) 较大 → 还原为浅色/白色</li>
 *   <li>红章下的黑色文字：R 受红墨影响偏高，G/B 依然很低 → min(G,B) 很小 → 还原为深色，文字保留</li>
 * </ul>
 * 对非红色像素（黑字、灰度内容）完全不做任何处理，最大程度保护文字。
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
        System.out.println("[RedChannelSealRemover] " + w + "x" + h);

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int processed = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                if (DocumentSealRemover.isRedSealColor(rgb)) {
                    // 红章像素：用 min(G, B) 还原底层亮度（保留文字，去除纯红）
                    int g = (rgb >> 8) & 0xFF;
                    int b =  rgb       & 0xFF;
                    int underlying = Math.min(g, b);
                    // 向白色方向略微拉伸，使章痕更淡（可选，系数 1.15）
                    underlying = Math.min(255, (int)(underlying * 1.15));
                    int gray = (underlying << 16) | (underlying << 8) | underlying;
                    result.setRGB(x, y, gray);
                    processed++;
                } else {
                    result.setRGB(x, y, rgb);
                }
            }
        }

        System.out.println("[RedChannelSealRemover] 处理红色像素: " + processed
                + " (" + String.format("%.2f%%", processed * 100.0 / (w * h)) + ")");
        return result;
    }
}
