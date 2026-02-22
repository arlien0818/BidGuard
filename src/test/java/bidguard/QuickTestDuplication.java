package bidguard;

import com.bidguard.OcrServiceClient;
import com.bidguard.OcrDuplicateDetector;
import com.bidguard.SimilarityConfig;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 快速测试查重功能
 */
public class QuickTestDuplication {
    public static void main(String[] args) {
        try {
            System.out.println("=".repeat(80));
            System.out.println("开始对gpt文档A和gpt文档B进行查重测试");
            System.out.println("=".repeat(80));
            
            // 加载OCR缓存
            Gson gson = new Gson();
            
            OcrServiceClient.OcrResult doc1;
            OcrServiceClient.OcrResult doc2;
            
            System.out.println("\n1. 加载OCR缓存文件...");
            try (Reader reader = new InputStreamReader(
                    new FileInputStream("output/gpt文档A_ocr_cache.json"), 
                    StandardCharsets.UTF_8)) {
                doc1 = gson.fromJson(reader, OcrServiceClient.OcrResult.class);
            }
            
            try (Reader reader = new InputStreamReader(
                    new FileInputStream("output/gpt文档B_ocr_cache.json"), 
                    StandardCharsets.UTF_8)) {
                doc2 = gson.fromJson(reader, OcrServiceClient.OcrResult.class);
            }
            
            System.out.println("   文档A: " + doc1.fullText.length() + " 字符");
            System.out.println("   文档B: " + doc2.fullText.length() + " 字符");
            
            // 执行查重检测
            System.out.println("\n2. 执行查重检测...");
            int minLength = SimilarityConfig.getInstance().substringMinLength;
            System.out.println("   最小片段长度: " + minLength + " 字符");
            
            OcrDuplicateDetector.DuplicateDetectionResult result = 
                OcrDuplicateDetector.detectDuplicates(
                    doc1, doc2,
                    "gpt文档A.pdf",
                    "gpt文档B.pdf",
                    minLength);
            
            System.out.println("   找到 " + result.totalMatches + " 个重复片段");
            
            // 保存结果
            System.out.println("\n3. 保存查重结果...");
            File jsonFile = OcrDuplicateDetector.saveResultToJson(
                result, "gpt文档A.pdf", "gpt文档B.pdf");
            
            System.out.println("   JSON文件: " + jsonFile.getAbsolutePath());
            
            // 显示重复片段摘要
            System.out.println("\n" + "=".repeat(80));
            System.out.println("重复片段摘要");
            System.out.println("=".repeat(80));
            
            for (int i = 0; i < result.matches.size(); i++) {
                OcrDuplicateDetector.DuplicateMatch match = result.matches.get(i);
                System.out.println(String.format("\n【片段#%d】", i + 1));
                System.out.println("  长度: " + match.textLength + " 字符");
                System.out.println("  文档A位置: " + match.doc1Location.startCharPos + 
                                 " (涉及" + match.doc1Location.textBlocks.size() + "个文字块)");
                System.out.println("  文档B位置: " + match.doc2Location.startCharPos + 
                                 " (涉及" + match.doc2Location.textBlocks.size() + "个文字块)");
                
                // 显示内容预览
                String preview = match.duplicateText.trim();
                if (preview.length() > 50) {
                    preview = preview.substring(0, 50) + "...";
                }
                System.out.println("  内容: " + preview);
            }
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("查重完成！请查看output目录下的报告文件。");
            System.out.println("=".repeat(80));
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
