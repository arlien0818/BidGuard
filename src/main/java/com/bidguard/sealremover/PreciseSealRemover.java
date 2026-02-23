package com.bidguard.sealremover;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * 精确公章去除器 - 先定位印章区域，再精确去除
 */
public class PreciseSealRemover {
    
    /**
     * 主方法：去除图像中的红色圆形印章
     */
    public static BufferedImage removeSeal(BufferedImage image) {
        if (image == null) {
            System.err.println("[ERROR] 输入图像为空");
            return null;
        }
        
        System.out.println("========================================");
        System.out.println("[开始] 精确公章检测和去除");
        System.out.println("图像尺寸: " + image.getWidth() + "x" + image.getHeight());
        System.out.println("========================================");
        
        // 步骤1: 找到所有红色印章区域
        List<Rectangle> sealRegions = findSealRegions(image);
        
        if (sealRegions.isEmpty()) {
            System.out.println("[警告] 未检测到印章区域，返回原图");
            return image;
        }
        
        System.out.println("[发现] 检测到 " + sealRegions.size() + " 个印章区域");
        
        // 步骤2: 只在印章区域内去除红色像素
        BufferedImage result = removeSealsInRegions(image, sealRegions);
        
        System.out.println("========================================");
        System.out.println("[完成] 公章去除完成");
        System.out.println("========================================");
        
        return result;
    }
    
    /**
     * 找到图像中的红色印章区域
     */
    private static List<Rectangle> findSealRegions(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        System.out.println("[步骤1] 扫描红色像素分布...");
        
        // 创建红色像素标记图
        boolean[][] isRed = new boolean[width][height];
        int redCount = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isStrictRedPixel(image.getRGB(x, y))) {
                    isRed[x][y] = true;
                    redCount++;
                }
            }
        }
        
        System.out.println("[步骤1] 发现 " + redCount + " 个红色像素");
        
        if (redCount == 0) {
            return new ArrayList<>();
        }
        
        // 使用连通区域分析找到印章区域
        System.out.println("[步骤2] 分析连通区域...");
        List<Rectangle> regions = findConnectedRegions(isRed, width, height);
        
        System.out.println("[步骤2] 发现 " + regions.size() + " 个连通区域");
        
        // 过滤：只保留可能是圆形印章的区域
        System.out.println("[步骤3] 筛选圆形印章区域...");
        List<Rectangle> sealRegions = new ArrayList<>();
        
        for (Rectangle region : regions) {
            // 印章通常是正方形区域（圆形的外接矩形）
            double aspectRatio = (double) region.width / region.height;
            int area = region.width * region.height;
            
            System.out.println("  区域: " + region.x + "," + region.y + 
                " 大小:" + region.width + "x" + region.height + 
                " 比例:" + String.format("%.2f", aspectRatio) +
                " 面积:" + area);
            
            // 条件：
            // 1. 宽高比接近1:1（圆形）
            // 2. 面积足够大（不是噪点）
            // 3. 面积不要太大（不是整个文档）
            if (aspectRatio > 0.5 && aspectRatio < 2.0 && 
                area > 1000 && area < width * height * 0.3) {
                
                // 扩展区域边界，确保完整包含印章
                Rectangle expanded = new Rectangle(
                    Math.max(0, region.x - 10),
                    Math.max(0, region.y - 10),
                    Math.min(width - region.x + 10, region.width + 20),
                    Math.min(height - region.y + 10, region.height + 20)
                );
                
                sealRegions.add(expanded);
                System.out.println("  -> 确认为印章区域!");
            }
        }
        
        return sealRegions;
    }
    
    /**
     * 严格的红色像素检测 - 只检测正红色
     */
    private static boolean isStrictRedPixel(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        // 严格条件：
        // 1. 红色分量必须高 (>150)
        // 2. 红色必须明显高于绿色和蓝色
        // 3. 绿色和蓝色都要低
        return r > 150 && g < 120 && b < 120 && r > g + 50 && r > b + 50;
    }
    
    /**
     * 找到所有连通的红色区域
     */
    private static List<Rectangle> findConnectedRegions(boolean[][] isRed, int width, int height) {
        boolean[][] visited = new boolean[width][height];
        List<Rectangle> regions = new ArrayList<>();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isRed[x][y] && !visited[x][y]) {
                    // 使用BFS找到连通区域
                    Rectangle region = bfsRegion(isRed, visited, x, y, width, height);
                    if (region != null) {
                        regions.add(region);
                    }
                }
            }
        }
        
        return regions;
    }
    
    /**
     * BFS遍历连通区域
     */
    private static Rectangle bfsRegion(boolean[][] isRed, boolean[][] visited, 
                                        int startX, int startY, int width, int height) {
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startX, startY});
        visited[startX][startY] = true;
        
        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        int pixelCount = 0;
        
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};
        
        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int x = pos[0];
            int y = pos[1];
            
            pixelCount++;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            
            // 检查8个方向的邻居
            for (int i = 0; i < 8; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];
                
                if (nx >= 0 && nx < width && ny >= 0 && ny < height &&
                    !visited[nx][ny] && isRed[nx][ny]) {
                    visited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        
        // 太小的区域忽略（噪点）
        if (pixelCount < 100) {
            return null;
        }
        
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
    
    /**
     * 只在指定区域内去除红色像素
     */
    private static BufferedImage removeSealsInRegions(BufferedImage image, List<Rectangle> regions) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // 复制原图 - 其他区域完全不动
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        
        int totalRemoved = 0;
        
        for (Rectangle region : regions) {
            System.out.println("[处理] 印章区域: (" + region.x + "," + region.y + 
                ") 大小: " + region.width + "x" + region.height);
            
            int removed = 0;
            
            // 只处理这个区域内的像素
            for (int y = region.y; y < region.y + region.height && y < height; y++) {
                for (int x = region.x; x < region.x + region.width && x < width; x++) {
                    int rgb = image.getRGB(x, y);
                    
                    if (isStrictRedPixel(rgb)) {
                        // 用周围非红色像素的颜色替换
                        int replacement = getLocalBackgroundColor(image, x, y, region);
                        result.setRGB(x, y, replacement);
                        removed++;
                    }
                    // 非红色像素保持不变（已经在复制时处理了）
                }
            }
            
            System.out.println("[处理] 该区域去除 " + removed + " 个红色像素");
            totalRemoved += removed;
        }
        
        System.out.println("[统计] 总共去除 " + totalRemoved + " 个红色像素");
        
        return result;
    }
    
    /**
     * 获取局部背景颜色（基于周围非红色像素）
     */
    private static int getLocalBackgroundColor(BufferedImage image, int x, int y, Rectangle region) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        List<Integer> bgColors = new ArrayList<>();
        
        // 在5x5范围内寻找非红色像素
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    int rgb = image.getRGB(nx, ny);
                    if (!isStrictRedPixel(rgb)) {
                        bgColors.add(rgb);
                    }
                }
            }
        }
        
        if (bgColors.isEmpty()) {
            // 如果周围全是红色，使用更大范围或默认纸张色
            return new Color(252, 252, 250).getRGB(); // 接近白色的纸张色
        }
        
        // 计算平均背景色
        long sumR = 0, sumG = 0, sumB = 0;
        for (int color : bgColors) {
            sumR += (color >> 16) & 0xFF;
            sumG += (color >> 8) & 0xFF;
            sumB += color & 0xFF;
        }
        
        int avgR = (int) (sumR / bgColors.size());
        int avgG = (int) (sumG / bgColors.size());
        int avgB = (int) (sumB / bgColors.size());
        
        return (avgR << 16) | (avgG << 8) | avgB;
    }
}
