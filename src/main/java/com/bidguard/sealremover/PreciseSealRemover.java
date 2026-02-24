package com.bidguard.sealremover;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * 精确公章去除器 - 连通区域分析 + 面积筛选 + 直接背景色替换
 *
 * 思路：
 * 1. 复用 isRedSealColor() 标记红色像素
 * 2. BFS 找连通区域，只保留面积 >= MIN_SEAL_AREA 的（过滤散点/小红字）
 * 3. 小范围膨胀（1px），直接填充全局背景色（不做邻域均值，避免文字被模糊）
 */
public class PreciseSealRemover {

    private static final int MIN_SEAL_AREA = 500;
    private static final int DILATE_RADIUS = 1;

    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) {
            System.err.println("[PreciseSealRemover] 输入图像为空");
            return null;
        }

        int w = image.getWidth(), h = image.getHeight();
        System.out.println("===== [PreciseSealRemover] 开始处理 =====");
        System.out.println("  图像尺寸: " + w + "x" + h);

        // 1. 标记红色
        boolean[][] isRed = new boolean[w][h];
        int redCount = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (DocumentSealRemover.isRedSealColor(image.getRGB(x, y))) {
                    isRed[x][y] = true; redCount++;
                }
        System.out.println("  红色像素: " + redCount);
        if (redCount == 0) { System.out.println("  未检测到红色，返回原图"); return image; }

        // 2. BFS 连通区域，仅保留大区域
        boolean[][] visited  = new boolean[w][h];
        boolean[][] sealMask = new boolean[w][h];
        int keptRegions = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isRed[x][y] && !visited[x][y]) {
                    List<int[]> comp = bfs(isRed, visited, x, y, w, h);
                    if (comp.size() >= MIN_SEAL_AREA) {
                        for (int[] p : comp) sealMask[p[0]][p[1]] = true;
                        keptRegions++;
                        System.out.println("  保留印章连通区域 #" + keptRegions + "  面积=" + comp.size());
                    }
                }
            }
        }
        if (keptRegions == 0) { System.out.println("  无足够大的红色区域，返回原图"); return image; }

        // 3. 膨胀 1px
        boolean[][] finalMask = dilate(sealMask, w, h, DILATE_RADIUS);

        // 4. 直接用全局背景色替换，但只替换掩膜内仍为红色的像素，黑字保留
        int bgColor = DocumentSealRemover.estimateGlobalBackground(image);
        System.out.printf("  全局背景色: #%06X%n", bgColor & 0xFFFFFF);
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int processed = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int orig = image.getRGB(x, y);
                if (finalMask[x][y] && DocumentSealRemover.isRedSealColor(orig)) {
                    result.setRGB(x, y, bgColor); processed++;
                } else {
                    result.setRGB(x, y, orig);
                }
            }
        }
        System.out.println("  替换像素数: " + processed);
        System.out.println("===== [PreciseSealRemover] 处理完成 =====");
        return result;
    }

    private static List<int[]> bfs(boolean[][] isRed, boolean[][] visited,
                                    int sx, int sy, int w, int h) {
        List<int[]> comp = new ArrayList<>();
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{sx, sy}); visited[sx][sy] = true;
        int[] ddx = {-1,0,1,-1,1,-1,0,1};
        int[] ddy = {-1,-1,-1,0,0,1,1,1};
        while (!queue.isEmpty()) {
            int[] p = queue.poll(); comp.add(p);
            for (int i = 0; i < 8; i++) {
                int nx = p[0]+ddx[i], ny = p[1]+ddy[i];
                if (nx>=0&&nx<w&&ny>=0&&ny<h&&!visited[nx][ny]&&isRed[nx][ny]) {
                    visited[nx][ny] = true; queue.add(new int[]{nx,ny});
                }
            }
        }
        return comp;
    }

    private static boolean[][] dilate(boolean[][] mask, int w, int h, int radius) {
        boolean[][] result = new boolean[w][h];
        int r2 = radius * radius;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask[x][y]) continue;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (dx*dx+dy*dy > r2) continue;
                        int nx = x+dx, ny = y+dy;
                        if (nx>=0&&nx<w&&ny>=0&&ny<h) result[nx][ny] = true;
                    }
                }
            }
        }
        return result;
    }
}