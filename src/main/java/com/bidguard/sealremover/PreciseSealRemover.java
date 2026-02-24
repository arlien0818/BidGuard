package com.bidguard.sealremover;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * 精确公章去除器 - 连通区域筛选 + 零膨胀逐像素替换
 *
 * 相比 DocumentSealRemover 的优势：先用 BFS 找到大面积红色连通区域，
 * 过滤散点噪声（如红色页码、小标记），只去除真正的印章区域内的红色像素。
 * 同样不做膨胀，不动非红色像素。
 */
public class PreciseSealRemover {

    private static final int MIN_SEAL_AREA = 400;

    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) return null;
        int w = image.getWidth(), h = image.getHeight();
        System.out.println("[PreciseSealRemover] " + w + "x" + h);

        // 1. 标记红色像素
        boolean[][] isRed = new boolean[w][h];
        int total = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (DocumentSealRemover.isRedSealColor(image.getRGB(x, y))) { isRed[x][y]=true; total++; }
        System.out.println("  红色像素: " + total);
        if (total == 0) return image;

        // 2. BFS 找大连通区域，标记为"印章掩膜"
        boolean[][] visited = new boolean[w][h];
        boolean[][] sealMask = new boolean[w][h];
        int regions = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isRed[x][y] && !visited[x][y]) {
                    List<int[]> comp = bfs(isRed, visited, x, y, w, h);
                    if (comp.size() >= MIN_SEAL_AREA) {
                        for (int[] p : comp) sealMask[p[0]][p[1]] = true;
                        regions++;
                        System.out.println("  印章连通区域 #" + regions + " 面积=" + comp.size());
                    }
                }
            }
        }
        if (regions == 0) { System.out.println("  无大区域，返回原图"); return image; }

        // 3. 只替换掩膜内且仍为红色的像素（黑字等非红像素完全不动）
        int bgColor = DocumentSealRemover.estimateGlobalBackground(image);
        System.out.printf("  背景色 #%06X%n", bgColor & 0xFFFFFF);
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int replaced = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                if (sealMask[x][y] && DocumentSealRemover.isRedSealColor(rgb)) {
                    result.setRGB(x, y, bgColor); replaced++;
                } else {
                    result.setRGB(x, y, rgb);
                }
            }
        }
        System.out.println("  替换红色像素: " + replaced);
        return result;
    }

    private static List<int[]> bfs(boolean[][] grid, boolean[][] visited,
                                    int sx, int sy, int w, int h) {
        List<int[]> comp = new ArrayList<>();
        Queue<int[]> q = new LinkedList<>();
        q.add(new int[]{sx, sy}); visited[sx][sy] = true;
        int[] ddx={-1,0,1,-1,1,-1,0,1}, ddy={-1,-1,-1,0,0,1,1,1};
        while (!q.isEmpty()) {
            int[] p = q.poll(); comp.add(p);
            for (int i=0; i<8; i++) {
                int nx=p[0]+ddx[i], ny=p[1]+ddy[i];
                if (nx>=0&&nx<w&&ny>=0&&ny<h&&!visited[nx][ny]&&grid[nx][ny]) {
                    visited[nx][ny]=true; q.add(new int[]{nx,ny});
                }
            }
        }
        return comp;
    }
}