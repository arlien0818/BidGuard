package com.bidguard.sealremover;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * 精确公章去除器 - 连通区域分析 + 面积筛选 + 邻域填充
 *
 * <p>思路：
 * <ol>
 *   <li>逐像素标记红色（复用 {@link DocumentSealRemover#isRedSealColor(int)}）</li>
 *   <li>BFS 找到连通红色区域</li>
 *   <li>仅去除面积超过阈值的连通区域（避免误删小红色标记/数字）</li>
 *   <li>膨胀掩膜 + 邻域均值填充</li>
 * </ol>
 */
public class PreciseSealRemover {

    /** 最小印章面积（像素），低于此的连通区域忽略 */
    private static final int MIN_SEAL_AREA = 500;

    /** 膨胀半径 */
    private static final int DILATE_RADIUS = 3;

    /** 邻域搜索半径 */
    private static final int FILL_RADIUS = 5;

    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) {
            System.err.println("[PreciseSealRemover] 输入图像为空");
            return null;
        }

        int w = image.getWidth();
        int h = image.getHeight();

        System.out.println("===== [PreciseSealRemover] 开始处理 =====");
        System.out.println("  图像尺寸: " + w + "x" + h);

        // 1. 标记红色像素
        boolean[][] isRed = new boolean[w][h];
        int redCount = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (DocumentSealRemover.isRedSealColor(image.getRGB(x, y))) {
                    isRed[x][y] = true;
                    redCount++;
                }
            }
        }
        System.out.println("  红色像素: " + redCount);
        if (redCount == 0) {
            System.out.println("  未检测到红色，返回原图");
            return image;
        }

        // 2. BFS 连通区域分析
        boolean[][] visited = new boolean[w][h];
        boolean[][] sealMask = new boolean[w][h]; // 最终保留的大区域掩膜
        int keptRegions = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isRed[x][y] && !visited[x][y]) {
                    List<int[]> component = bfs(isRed, visited, x, y, w, h);
                    if (component.size() >= MIN_SEAL_AREA) {
                        for (int[] p : component) sealMask[p[0]][p[1]] = true;
                        keptRegions++;
                        System.out.println("  保留连通区域 #" + keptRegions + "  面积=" + component.size());
                    }
                }
            }
        }

        if (keptRegions == 0) {
            System.out.println("  无足够大的红色区域，返回原图");
            return image;
        }

        // 3. 膨胀
        boolean[][] dilated = dilate(sealMask, w, h, DILATE_RADIUS);

        // 4. 邻域均值填充
        BufferedImage result = fillNeighborAvg(image, dilated, w, h);

        System.out.println("===== [PreciseSealRemover] 处理完成 =====");
        return result;
    }

    // ---------- BFS ----------

    private static List<int[]> bfs(boolean[][] isRed, boolean[][] visited,
                                    int sx, int sy, int w, int h) {
        List<int[]> component = new ArrayList<>();
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{sx, sy});
        visited[sx][sy] = true;
        int[] ddx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] ddy = {-1, -1, -1, 0, 0, 1, 1, 1};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            component.add(p);
            for (int i = 0; i < 8; i++) {
                int nx = p[0] + ddx[i], ny = p[1] + ddy[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h
                        && !visited[nx][ny] && isRed[nx][ny]) {
                    visited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return component;
    }

    // ---------- 膨胀 ----------

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

    // ---------- 邻域均值填充 ----------

    private static BufferedImage fillNeighborAvg(BufferedImage image, boolean[][] mask, int w, int h) {
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        // 先全图复制
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                result.setRGB(x, y, image.getRGB(x, y));

        int processed = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask[x][y]) continue;
                long sr = 0, sg = 0, sb = 0;
                int cnt = 0;
                for (int dy = -FILL_RADIUS; dy <= FILL_RADIUS; dy++) {
                    for (int dx = -FILL_RADIUS; dx <= FILL_RADIUS; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h && !mask[nx][ny]) {
                            int c = image.getRGB(nx, ny);
                            sr += (c >> 16) & 0xFF;
                            sg += (c >> 8) & 0xFF;
                            sb += c & 0xFF;
                            cnt++;
                        }
                    }
                }
                if (cnt > 0) {
                    int fill = ((int) (sr / cnt) << 16) | ((int) (sg / cnt) << 8) | (int) (sb / cnt);
                    result.setRGB(x, y, fill);
                } else {
                    result.setRGB(x, y, 0xF0F0F0); // fallback
                }
                processed++;
            }
        }
        System.out.println("  替换像素数: " + processed);
        return result;
    }
}
