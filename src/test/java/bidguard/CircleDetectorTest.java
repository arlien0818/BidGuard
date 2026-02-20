package bidguard;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
/*本文件功能暂时放弃，不建议copilot,claude,gpt等所有AI进行参考 */
/**
 * 圆形检测测试 - 只做一件事：找到图像中的圆形印章位置
 */
public class CircleDetectorTest {

    public static void main(String[] args) {
        // 直接指定测试图片路径，请修改为您的营业执照图片路径
        String imagePath = "D:\\work\\Project\\BidGuard\\output\\公章深.png";

        try {
            System.out.println("========================================");
            System.out.println("圆形印章检测测试");
            System.out.println("========================================");

            // 读取图片
            BufferedImage image = ImageIO.read(new File(imagePath));
            if (image == null) {
                System.out.println("无法读取图片: " + imagePath);
                return;
            }

            System.out.println("图片尺寸: " + image.getWidth() + "x" + image.getHeight());

            // 检测圆形
            Circle[] circles = detectCircles(image);

            if (circles.length == 0) {
                System.out.println("未检测到圆形");
            } else {
                System.out.println("检测到 " + circles.length + " 个圆形:");
                for (int i = 0; i < circles.length; i++) {
                    Circle c = circles[i];
                    System.out.println("  圆" + (i+1) + ": 中心(" + c.x + "," + c.y + ") 半径=" + c.radius);
                }

                // 在原图上画出检测到的圆形，保存结果
                BufferedImage result = drawCircles(image, circles);
                File outputFile = new File("D:\\work\\Project\\BidGuard\\output\\circle_detection_result.png");
                outputFile.getParentFile().mkdirs();
                ImageIO.write(result, "PNG", outputFile);
                System.out.println("结果已保存到: " + outputFile.getAbsolutePath());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 检测图像中的圆形 - 使用霍夫圆变换的简化版本
     */
    public static Circle[] detectCircles(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();

        boolean[][] red = new boolean[w][h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                red[x][y] = isRedPixelHSV(image.getRGB(x, y));
            }
        }

        // 1. 提取红色边缘点
        java.util.List<Point> edgePoints = new java.util.ArrayList<>();
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                if (!red[x][y]) continue;

                if (!red[x - 1][y] || !red[x + 1][y]
                        || !red[x][y - 1] || !red[x][y + 1]) {
                    edgePoints.add(new Point(x, y));
                }
            }
        }

        if (edgePoints.size() < 100) return new Circle[0];

        // 2. 圆心投票（网格聚合）
        java.util.Map<Point, Integer> votes = new java.util.HashMap<>();
        java.util.Random rand = new java.util.Random();
        int grid = 6;                       // ★ 新增：圆心量化网格
        int samples = Math.min(3000, edgePoints.size() * 5);

        for (int i = 0; i < samples; i++) {
            Point p1 = edgePoints.get(rand.nextInt(edgePoints.size()));
            Point p2 = edgePoints.get(rand.nextInt(edgePoints.size()));
            Point p3 = edgePoints.get(rand.nextInt(edgePoints.size()));

            // ★ 新增：过滤几何质量差的三点
            if (p1.distance(p2) < 25 || p1.distance(p3) < 25 || p2.distance(p3) < 25)
                continue;

            Point c = computeCircleCenter(p1, p2, p3);
            if (c == null) continue;
            if (c.x < 0 || c.x >= w || c.y < 0 || c.y >= h) continue;

            // ★ 新增：圆心网格化，防抖
            int gx = (c.x / grid) * grid;
            int gy = (c.y / grid) * grid;
            Point key = new Point(gx, gy);

            votes.put(key, votes.getOrDefault(key, 0) + 1);
        }

        if (votes.isEmpty()) return new Circle[0];

        // 3. 取票数最多的圆心
        Point best = null;
        int max = 0;
        for (var e : votes.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }

        int cx = best.x;
        int cy = best.y;

        // ★ 新增：圆心“反偏置修正”（针对下半圆缺失）
        double vx = 0, vy = 0;
        for (Point p : edgePoints) {
            vx += p.x - cx;
            vy += p.y - cy;
        }
        vx /= edgePoints.size();
        vy /= edgePoints.size();
        // 加大修正系数，更强力地补偿下半圆缺失
        cx -= (int) (vx * 0.4);
        cy -= (int) (vy * 0.4);

        // 4. 半径估算（对称补全 + 上分位）
        java.util.List<Integer> radii = new java.util.ArrayList<>();
        for (Point p : edgePoints) {
            int r1 = (int) Math.hypot(p.x - cx, p.y - cy);
            radii.add(r1);

            // ★ 新增：对称补点（补下半圆）
            int sx = 2 * cx - p.x;
            int sy = 2 * cy - p.y;
            int r2 = (int) Math.hypot(sx - cx, sy - cy);
            radii.add(r2);
        }

        radii.sort(Integer::compareTo);

        // 取 92% 分位，避免半径偏小
        int idx = (int) (radii.size() * 0.92);
        int radius = radii.get(idx);

        // 额外补偿5%，针对印章边缘被遮挡
        radius = (int) (radius * 1.05);

        // ★ 新增：基于掩膜分布对圆心做二次微调
        Point refinedCenter = refineCenterByMask(red, cx, cy, radius, w, h);
        cx = refinedCenter.x;
        cy = refinedCenter.y;

        return new Circle[]{new Circle(cx, cy, radius)};
    }



    private static Point computeCircleCenter(Point p1, Point p2, Point p3) {
    double a = p2.x - p1.x;
    double b = p2.y - p1.y;
    double c = p3.x - p1.x;
    double d = p3.y - p1.y;

    double e = a * (p1.x + p2.x) + b * (p1.y + p2.y);
    double f = c * (p1.x + p3.x) + d * (p1.y + p3.y);
    double g = 2.0 * (a * (p3.y - p2.y) - b * (p3.x - p2.x));

    if (Math.abs(g) < 1e-6) return null;

    int cx = (int) ((d * e - b * f) / g);
    int cy = (int) ((a * f - c * e) / g);
    return new Point(cx, cy);
}



    /**
     * 基于HSV判断是否为红色像素
     */
    private static boolean isRedPixelHSV(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // RGB转HSV
        float[] hsv = new float[3];
        Color.RGBtoHSB(r, g, b, hsv);

        float hue = hsv[0] * 360;        // 0-360
        float saturation = hsv[1];       // 0-1
        float brightness = hsv[2];       // 0-1

        // 红色在HSV中：色相在0°附近或360°附近
        boolean isRedHue = (hue <= 30 || hue >= 330);
        boolean hasSaturation = saturation >= 0.3;   // 饱和度要足够
        boolean hasBrightness = brightness >= 0.2;   // 亮度不能太暗

        return isRedHue && hasSaturation && hasBrightness;
    }

    /**
     * 计算圆形度 - 检查红色像素在圆环附近的分布
     */
    private static double calculateCircularity(boolean[][] mask, int cx, int cy, int radius,
                                               int width, int height) {
        int onCircle = 0;
        int totalRed = 0;

        // 统计红色像素中有多少在圆环附近
        int tolerance = Math.max(radius / 5, 10); // 容差

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[x][y]) {
                    totalRed++;

                    // 计算到圆心的距离
                    double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));

                    // 检查是否在圆环附近（印章是圆环，不是实心圆）
                    if (Math.abs(dist - radius) <= tolerance || dist <= radius) {
                        onCircle++;
                    }
                }
            }
        }

        if (totalRed == 0) return 0;
        return (double) onCircle / totalRed;
    }

    /**
     * 在图像上画出检测到的圆形
     */
    private static BufferedImage drawCircles(BufferedImage original, Circle[] circles) {
        BufferedImage result = new BufferedImage(
            original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(original, 0, 0, null);

        // 画圆
        g2d.setColor(Color.GREEN);
        g2d.setStroke(new BasicStroke(3));

        for (Circle c : circles) {
            // 画圆
            g2d.drawOval(c.x - c.radius, c.y - c.radius, c.radius * 2, c.radius * 2);

            // 画圆心
            g2d.fillOval(c.x - 5, c.y - 5, 10, 10);

            // 标注
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            g2d.drawString("R=" + c.radius, c.x + c.radius + 10, c.y);
        }

        g2d.dispose();
        return result;
    }

    /**
     * 圆形数据结构
     */
    static class Circle {
        int x, y, radius;

        Circle(int x, int y, int radius) {
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    /**
     * 基于掩膜分布对圆心做二次微调
     */
    private static Point refineCenterByMask(boolean[][] mask, int cx, int cy, int radius,
                                            int width, int height) {
        int ringInner = (int) (radius * 0.6);
        int ringOuter = (int) (radius * 1.25);

        long left = 0, right = 0, top = 0, bottom = 0;

        int minX = Math.max(0, cx - ringOuter);
        int maxX = Math.min(width - 1, cx + ringOuter);
        int minY = Math.max(0, cy - ringOuter);
        int maxY = Math.min(height - 1, cy + ringOuter);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!mask[x][y]) {
                    continue;
                }
                double dist = Math.hypot(x - cx, y - cy);
                if (dist < ringInner || dist > ringOuter) {
                    continue;
                }

                if (x < cx) {
                    left++;
                } else {
                    right++;
                }

                if (y < cy) {
                    top++;
                } else {
                    bottom++;
                }
            }
        }

        double horizontalBalance = balanceRatio(left, right);
        double verticalBalance = balanceRatio(top, bottom);

        // 根据实际偏移情况，水平微调适度，垂直补偿更强
        int dx = (int) Math.round(horizontalBalance * 5); // 稍微加大向右修正能力
        int dy = (int) Math.round(verticalBalance * 8);   // 垂直方向加强补偿

        return new Point(cx + dx, cy + dy);
    }

    private static double balanceRatio(long negativeSide, long positiveSide) {
        long total = negativeSide + positiveSide;
        if (total == 0) {
            return 0;
        }
        return (positiveSide - negativeSide) / (double) total;
    }
}
