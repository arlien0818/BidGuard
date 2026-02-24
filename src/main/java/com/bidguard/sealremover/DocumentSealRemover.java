package com.bidguard.sealremover;

import java.awt.image.BufferedImage;

/**
 * 扫描文档红章去除器 - 零膨胀逐像素精准替换
 *
 * <p>原则：只动被判定为红色的像素本身，其余像素（包括章下黑字）完全不动。
 * 不做任何膨胀/腐蚀/形态学操作，避免扩展到相邻文字像素。
 *
 * <p>#BF476D 对应 HSV: H341, S0.63, V0.75，需覆盖 [300,360] 段。
 */
public class DocumentSealRemover {

    // 色相范围：0-30 (橙红) 和 300-360 (紫红/粉红，覆盖 #BF476D ~341)
    private static final float HUE_LOW1  = 0f,   HUE_HIGH1 = 30f;
    private static final float HUE_LOW2  = 300f, HUE_HIGH2 = 360f;

    // 扫描件颜色偏淡，饱和度阈值放宽
    private static final float MIN_SAT = 0.12f;
    private static final float MIN_VAL = 0.15f;

    // -----------------------------------------------------------------

    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) return null;
        int w = image.getWidth(), h = image.getHeight();
        System.out.println("[DocumentSealRemover v2.10] " + w + "x" + h);

        int bgColor = estimateGlobalBackground(image);
        System.out.printf("  背景色 #%06X%n", bgColor & 0xFFFFFF);

        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int replaced = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                if (isRedSealColor(rgb)) {
                    result.setRGB(x, y, bgColor);
                    replaced++;
                } else {
                    result.setRGB(x, y, rgb);
                }
            }
        }
        System.out.println("  替换红色像素: " + replaced);
        return result;
    }

    /** 判断单像素是否为红色印章色（公开供其他类复用） */
    public static boolean isRedSealColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8)  & 0xFF;
        int b =  rgb        & 0xFF;
        if (r <= g || r <= b) return false;                    // 必须 R 最大

        float[] hsv = toHsv(r, g, b);
        if (hsv[1] < MIN_SAT || hsv[2] < MIN_VAL) return false;

        float h = hsv[0];
        return (h >= HUE_LOW1 && h <= HUE_HIGH1) || (h >= HUE_LOW2 && h <= HUE_HIGH2);
    }

    /** 取四角 3030 区域非红色像素均值 */
    static int estimateGlobalBackground(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int sz = Math.min(30, Math.min(w, h));
        long sr = 0, sg = 0, sb = 0;
        int cnt = 0;
        int[][] corners = {{0,0},{w-sz,0},{0,h-sz},{w-sz,h-sz}};
        for (int[] c : corners) {
            for (int y = c[1]; y < c[1]+sz && y < h; y++) {
                for (int x = c[0]; x < c[0]+sz && x < w; x++) {
                    int rgb = image.getRGB(x, y);
                    if (!isRedSealColor(rgb)) {
                        sr += (rgb>>16)&0xFF; sg += (rgb>>8)&0xFF; sb += rgb&0xFF; cnt++;
                    }
                }
            }
        }
        if (cnt == 0) return 0xF0F0F0;
        return ((int)(sr/cnt) << 16) | ((int)(sg/cnt) << 8) | (int)(sb/cnt);
    }

    private static float[] toHsv(int r, int g, int b) {
        float rf=r/255f, gf=g/255f, bf=b/255f;
        float mx=Math.max(rf,Math.max(gf,bf)), mn=Math.min(rf,Math.min(gf,bf)), d=mx-mn;
        float h=0, s=(mx==0)?0:d/mx, v=mx;
        if (d!=0) {
            if      (mx==rf) h=60*((gf-bf)/d%6);
            else if (mx==gf) h=60*((bf-rf)/d+2);
            else             h=60*((rf-gf)/d+4);
            if (h<0) h+=360;
        }
        return new float[]{h,s,v};
    }
}