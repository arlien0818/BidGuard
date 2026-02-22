package bidguard;

import com.bidguard.OcrServiceClient;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class DebugFragment4 {
    public static void main(String[] args) throws IOException {
        Gson gson = new Gson();
        
        OcrServiceClient.OcrResult doc1;
        try (Reader reader = new InputStreamReader(
                new FileInputStream("output/gpt文档A_ocr_cache.json"), 
                StandardCharsets.UTF_8)) {
            doc1 = gson.fromJson(reader, OcrServiceClient.OcrResult.class);
        }
        
        String fullText = doc1.fullText;
        int startChar = 264;
        int endChar = 295;
        
        System.out.println("=== 片段#4 调试信息 ===");
        System.out.println("fullText字符范围: [" + startChar + " - " + endChar + "]");
        System.out.println("重复文本内容: [" + fullText.substring(startChar, endChar) + "]");
        
        String duplicateText = fullText.substring(startChar, endChar);
        System.out.println("重复文本长度: " + duplicateText.length());
        System.out.println("重复文本(去空格): [" + duplicateText.replaceAll("\\s+", "") + "]");
        
        // 遍历文字块，找出与这个范围重叠的
        System.out.println("\n遍历文字块:");
        int currentPos = 0;
        
        for (int i = 0; i < doc1.texts.size(); i++) {
            OcrServiceClient.OcrTextItem item = doc1.texts.get(i);
            String itemText = item.text != null ? item.text : "";
            
            int itemStart = currentPos;
            int itemEnd = currentPos + itemText.length();
            
            // 检查是否与目标范围有重叠
            if (itemEnd > startChar && itemStart < endChar) {
                System.out.println(String.format("\n块#%d 有重叠:", i));
                System.out.println("  位置: [" + itemStart + " - " + itemEnd + "]");
                System.out.println("  文本: " + itemText);
                
                int overlapStart = Math.max(0, startChar - itemStart);
                int overlapEnd = Math.min(itemText.length(), endChar - itemStart);
                String overlapText = itemText.substring(overlapStart, overlapEnd);
                
                System.out.println("  重叠部分: [" + overlapText + "]");
                System.out.println("  重叠部分(去空格): [" + overlapText.replaceAll("\\s+", "") + "]");
                
                String overlapNormalized = overlapText.replaceAll("\\s+", "");
                String duplicateNormalized = duplicateText.replaceAll("\\s+", "");
                
                boolean isValid = overlapNormalized.length() > 2 && duplicateNormalized.contains(overlapNormalized);
                System.out.println("  验证结果: " + isValid);
                System.out.println("    overlapNormalized.length() = " + overlapNormalized.length());
                System.out.println("    duplicateNormalized.contains(overlapNormalized) = " + duplicateNormalized.contains(overlapNormalized));
            }
            
            currentPos = itemEnd;
            
            if (currentPos >= endChar) {
                break;
            }
        }
    }
}
