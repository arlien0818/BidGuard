package bidguard;

import com.bidguard.OcrDuplicateDetector;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 调试标注覆盖率
 */
public class DebugAnnotationCoverage {
    
    public static void main(String[] args) throws Exception {
        // 加载最新的检测结果
        File outputDir = new File("output");
        File[] jsonFiles = outputDir.listFiles((dir, name) -> 
            name.startsWith("duplicate_detection_gpt文档A_vs_gpt文档B_") && 
            name.endsWith(".json"));
        
        if (jsonFiles == null || jsonFiles.length == 0) {
            System.err.println("未找到查重检测结果JSON文件");
            return;
        }
        
        File latestJsonFile = jsonFiles[0];
        for (File f : jsonFiles) {
            if (f.lastModified() > latestJsonFile.lastModified()) {
                latestJsonFile = f;
            }
        }
        
        System.out.println("分析文件: " + latestJsonFile.getName());
        System.out.println("=".repeat(80));
        
        // 读取检测结果
        Gson gson = new Gson();
        OcrDuplicateDetector.DuplicateDetectionResult detection;
        try (Reader reader = new InputStreamReader(
                new FileInputStream(latestJsonFile), StandardCharsets.UTF_8)) {
            detection = gson.fromJson(reader, OcrDuplicateDetector.DuplicateDetectionResult.class);
        }
        
        // 分析每个片段的块覆盖率
        for (OcrDuplicateDetector.DuplicateMatch match : detection.matches) {
            System.out.println();
            System.out.println(String.format("【片段 #%d】 长度: %d 字符", 
                match.matchId, match.textLength));
            System.out.println("内容: " + match.duplicateText.substring(0, Math.min(50, match.duplicateText.length())) + "...");
            System.out.println();
            
            // 文档1
            System.out.println(">> 文档1:");
            for (OcrDuplicateDetector.TextBlockRef block : match.doc1Location.textBlocks) {
                int blockLength = block.text.length();
                int overlapLength = block.endCharInBlock - block.startCharInBlock;
                double coverageRatio = (double) overlapLength / blockLength;
                
                String status = coverageRatio >= 0.80 ? "✓ 标注" : "✗ 跳过";
                System.out.println(String.format("   块#%d: 覆盖率=%.1f%% (%d/%d字符) %s",
                    block.blockIndex, coverageRatio * 100, overlapLength, blockLength, status));
                System.out.println(String.format("         块文本: \"%s\"", 
                    block.text.length() > 40 ? block.text.substring(0, 40) + "..." : block.text));
                System.out.println(String.format("         重叠部分: [%d-%d]",
                    block.startCharInBlock, block.endCharInBlock));
            }
            
            // 文档2
            System.out.println();
            System.out.println(">> 文档2:");
            for (OcrDuplicateDetector.TextBlockRef block : match.doc2Location.textBlocks) {
                int blockLength = block.text.length();
                int overlapLength = block.endCharInBlock - block.startCharInBlock;
                double coverageRatio = (double) overlapLength / blockLength;
                
                String status = coverageRatio >= 0.80 ? "✓ 标注" : "✗ 跳过";
                System.out.println(String.format("   块#%d: 覆盖率=%.1f%% (%d/%d字符) %s",
                    block.blockIndex, coverageRatio * 100, overlapLength, blockLength, status));
                System.out.println(String.format("         块文本: \"%s\"", 
                    block.text.length() > 40 ? block.text.substring(0, 40) + "..." : block.text));
                System.out.println(String.format("         重叠部分: [%d-%d]",
                    block.startCharInBlock, block.endCharInBlock));
            }
            
            System.out.println("-".repeat(80));
        }
    }
}
