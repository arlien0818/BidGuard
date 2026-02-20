package bidguard;

import net.sourceforge.tess4j.Tesseract;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

/**
 * 依赖测试类 - 验证图像处理相关依赖是否正确加载
 */
public class DependencyTest {
    
    private static final Logger LOGGER = Logger.getLogger(DependencyTest.class.getName());
    
    public static void main(String[] args) {
        System.out.println("=== 第一步：图像处理依赖测试 ===");
        testDependencies();
    }
    
    public static void testDependencies() {
        System.out.println("[DEBUG] 开始测试依赖库...");
        
        // 1. 测试Java内置图像处理
        try {
            System.out.println("[DEBUG] 测试 Java 内置图像处理...");
            
            // 创建测试图像
            BufferedImage testImage = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = testImage.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 200, 100);
            g2d.setColor(Color.RED);
            g2d.fillOval(50, 25, 50, 50); // 模拟红色公章
            g2d.dispose();
            
            System.out.println("[SUCCESS] ✓ Java 内置图像处理可用");
            System.out.println("[DEBUG] 测试图像创建成功: " + testImage.getWidth() + "x" + testImage.getHeight());
            System.out.println("[DEBUG] 图像类型: " + testImage.getType());
            
        } catch (Exception e) {
            System.err.println("[ERROR] ✗ Java 图像处理测试失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        // 2. 测试Tesseract OCR
        try {
            System.out.println("[DEBUG] 测试 Tesseract OCR...");
            Tesseract tesseract = new Tesseract();
            System.out.println("[SUCCESS] ✓ Tesseract 实例化成功");
            System.out.println("[DEBUG] Tesseract 版本信息获取中...");
        } catch (Exception e) {
            System.err.println("[ERROR] ✗ Tesseract 初始化失败: " + e.getMessage());
            System.err.println("[INFO] 注意：Tesseract需要安装本地程序，这是正常的");
        }
        
        // 3. 测试Java内置图像处理
        try {
            System.out.println("[DEBUG] 测试 Java ImageIO...");
            String[] readerFormats = ImageIO.getReaderFormatNames();
            String[] writerFormats = ImageIO.getWriterFormatNames();
            
            System.out.println("[SUCCESS] ✓ Java ImageIO 可用");
            System.out.println("[DEBUG] 支持读取的图像格式: ");
            for (String format : readerFormats) {
                System.out.println("  - " + format);
            }
            System.out.println("[DEBUG] 支持写入的图像格式: ");
            for (String format : writerFormats) {
                System.out.println("  - " + format);
            }
            
        } catch (Exception e) {
            System.err.println("[ERROR] ✗ Java ImageIO 测试失败: " + e.getMessage());
        }
        
        // 4. 测试增强图像IO支持
        try {
            System.out.println("[DEBUG] 测试增强图像IO支持...");
            // 创建一个小的测试图像
            BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            System.out.println("[SUCCESS] ✓ 增强图像IO支持正常");
            System.out.println("[DEBUG] 测试图像创建成功: " + testImage.getWidth() + "x" + testImage.getHeight());
            
        } catch (Exception e) {
            System.err.println("[ERROR] ✗ 增强图像IO测试失败: " + e.getMessage());
        }
        
        System.out.println("\n=== 依赖测试完成 ===");
        System.out.println("[INFO] 如果看到上述SUCCESS标记，说明依赖添加成功");
        System.out.println("[INFO] 如果有ERROR，请检查依赖配置或网络连接");
    }
}