package bidguard;

import java.io.File;
import com.bidguard.OcrServiceClient;
import com.bidguard.OcrServiceFactory;

/**
 * 快速测试：验证新的阿里云OCR解析逻辑
 */
public class QuickOcrTest {
    public static void main(String[] args) {
        try {
            System.out.println("======== 阿里云OCR新解析逻辑测试 ========\n");
            
            File testPdf = new File("testfiles/test1.pdf");
            if (!testPdf.exists()) {
                System.err.println("测试文件不存在: " + testPdf.getAbsolutePath());
                return;
            }
            
            System.out.println("测试文件: " + testPdf.getName());
            System.out.println("开始识别...\n");
            
            // 调用OCR识别
            OcrServiceClient.OcrResult result = OcrServiceFactory.recognizePdf(testPdf);
            
            if (result.success) {
                System.out.println("\n✓ 识别成功！");
                System.out.println("==========================================");
                System.out.println("识别引擎: " + result.engine);
                System.out.println("总页数: " + result.pageCount);
                System.out.println("文字块总数: " + result.textCount);
                System.out.println("总字符数: " + result.fullText.length());
                
                // 计算平均置信度
                double avgConfidence = result.texts.stream()
                    .mapToDouble(t -> t.confidence)
                    .average()
                    .orElse(0.0);
                System.out.printf("平均置信度: %.2f%%\n", avgConfidence * 100);
                
                // 显示前3个文字块的详细信息
                System.out.println("\n前3个文字块示例:");
                System.out.println("------------------------------------------");
                int displayCount = Math.min(3, result.texts.size());
                for (int i = 0; i < displayCount; i++) {
                    OcrServiceClient.OcrTextItem item = result.texts.get(i);
                    System.out.printf("\n[%d] %s\n", i + 1, item.text);
                    System.out.printf("    置信度: %.2f%%\n", item.confidence * 100);
                    if (item.bbox != null && item.bbox.size() == 4) {
                        System.out.printf("    左上角: (%.0f, %.0f)\n", 
                            item.bbox.get(0)[0], item.bbox.get(0)[1]);
                    }
                }
                
                System.out.println("\n==========================================");
                System.out.println("详细结果已保存到 output 文件夹");
                System.out.println("请查看对应的 *_page_*.txt 和 *_SUMMARY_*.txt 文件");
                
            } else {
                System.err.println("\n✗ 识别失败！");
                System.err.println("错误信息: " + result.error);
            }
            
        } catch (Exception e) {
            System.err.println("\n测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
