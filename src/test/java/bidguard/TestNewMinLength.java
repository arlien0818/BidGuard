package bidguard;

import com.bidguard.OcrServiceClient;
import com.bidguard.OcrDuplicateDetector;
import com.bidguard.SimilarityConfig;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 测试新的minLength配置是否能检测到更多重复片段
 */
public class TestNewMinLength {
    
    public static void main(String[] args) {
        try {
            System.out.println("========================================");
            System.out.println("测试新的minLength配置");
            System.out.println("========================================\n");
            
            // 读取配置
            int minLength = SimilarityConfig.getInstance().substringMinLength;
            System.out.println("当前配置的最小片段长度: " + minLength + " 字符\n");
            
            // 读取OCR缓存文件
            File cache1 = new File("output/gpt文档A_ocr_cache.json");
            File cache2 = new File("output/gpt文档B_ocr_cache.json");
            
            if (!cache1.exists() || !cache2.exists()) {
                System.err.println("错误: OCR缓存文件不存在");
                System.err.println("请先运行OCR识别");
                return;
            }
            
            System.out.println("读取OCR缓存...");
            Gson gson = new Gson();
            
            OcrServiceClient.OcrResult result1 = gson.fromJson(
                new InputStreamReader(new FileInputStream(cache1), StandardCharsets.UTF_8),
                OcrServiceClient.OcrResult.class
            );
            
            OcrServiceClient.OcrResult result2 = gson.fromJson(
                new InputStreamReader(new FileInputStream(cache2), StandardCharsets.UTF_8),
                OcrServiceClient.OcrResult.class
            );
            
            System.out.println("文档A: " + result1.textCount + " 个文字块, " + result1.fullText.length() + " 字符");
            System.out.println("文档B: " + result2.textCount + " 个文字块, " + result2.fullText.length() + " 字符\n");
            
            // 执行查重检测
            System.out.println("开始查重检测 (minLength=" + minLength + ")...\n");
            
            OcrDuplicateDetector.DuplicateDetectionResult detection = 
                OcrDuplicateDetector.detectDuplicates(
                    result1,
                    result2,
                    "gpt文档A.pdf",
                    "gpt文档B.pdf",
                    minLength
                );
            
            // 显示结果
            System.out.println("========================================");
            System.out.println("检测结果");
            System.out.println("========================================");
            System.out.println("找到 " + detection.totalMatches + " 个重复片段:\n");
            
            for (int i = 0; i < detection.matches.size(); i++) {
                OcrDuplicateDetector.DuplicateMatch match = detection.matches.get(i);
                System.out.println(String.format("[片段 #%d] 长度: %d 字符", 
                    match.matchId, match.textLength));
                System.out.println("  文档A位置: [" + match.doc1Location.startCharPos + 
                    " - " + match.doc1Location.endCharPos + "]");
                System.out.println("  文档B位置: [" + match.doc2Location.startCharPos + 
                    " - " + match.doc2Location.endCharPos + "]");
                
                // 显示前50个字符作为预览
                String preview = match.duplicateText.length() <= 50 ? 
                    match.duplicateText : 
                    match.duplicateText.substring(0, 50) + "...";
                System.out.println("  内容预览: " + preview.replace("\n", " "));
                System.out.println();
            }
            
            // 保存结果
            System.out.println("保存结果文件...");
            File jsonFile = OcrDuplicateDetector.saveResultToJson(
                detection, 
                "gpt文档A.pdf", 
                "gpt文档B.pdf"
            );
            System.out.println("\n结果已保存到: " + jsonFile.getAbsolutePath());
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
