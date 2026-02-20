package com.bidguard;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class DebugDuplicateDetection {
    public static void main(String[] args) throws IOException {
        // 加载OCR缓存
        Gson gson = new Gson();
        
        OcrServiceClient.OcrResult doc1;
        OcrServiceClient.OcrResult doc2;
        
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
        
        String text1 = doc1.fullText;
        String text2 = doc2.fullText;
        
        System.out.println("=== 文档长度 ===");
        System.out.println("文档A: " + text1.length() + " 字符");
        System.out.println("文档B: " + text2.length() + " 字符");
        
        // 检查目标字符串
        String target = "本项目建设周期为180日历天，分为需求调研阶段、系统设计阶段、设备采购";
        int pos1 = text1.indexOf(target);
        int pos2 = text2.indexOf(target);
        
        System.out.println("\n=== 目标字符串查找 ===");
        System.out.println("目标: " + target);
        System.out.println("文档A位置: " + pos1);
        System.out.println("文档B位置: " + pos2);
        System.out.println("目标长度: " + target.length());
        
        // 查看更长的匹配
        String longerTarget = "本项目建设周期为180日历天，分为需求调研阶段、系统设计阶段、设备采购 阶段、安装调试阶段、系统联调阶段及试运行阶段六个阶段实施。";
        int longerPos1 = text1.indexOf(longerTarget);
        int longerPos2 = text2.indexOf(longerTarget);
        
        System.out.println("\n=== 更长的目标字符串 ===");
        System.out.println("目标: " + longerTarget);
        System.out.println("文档A位置: " + longerPos1);
        System.out.println("文档B位置: " + longerPos2);
        System.out.println("目标长度: " + longerTarget.length());
        
        // 查看位置109周围的内容
        System.out.println("\n=== 位置109周围的内容（文档A） ===");
        int start1 = Math.max(0, 100);
        int end1 = Math.min(text1.length(), 180);
        System.out.println("位置100-180: " + text1.substring(start1, end1));
        
        System.out.println("\n=== 位置128周围的内容（文档B） ===");
        int start2 = Math.max(0, 100);
        int end2 = Math.min(text2.length(), 200);
        System.out.println("位置100-200: " + text2.substring(start2, end2));
        
        // 运行实际的查重检测
        System.out.println("\n=== 运行findCommonSubstrings（minLength=30） ===");
        var matches = BidChecker.findCommonSubstrings(text1, text2, 30);
        System.out.println("找到 " + matches.size() + " 个匹配片段:");
        
        for (int i = 0; i < matches.size(); i++) {
            BidChecker.SubstringMatch m = matches.get(i);
            System.out.println(String.format("\n片段#%d:", i + 1));
            System.out.println("  长度: " + m.length + " 字符");
            System.out.println("  文档A位置: " + m.startPos1);
            System.out.println("  文档B位置: " + m.startPos2);
            System.out.println("  内容预览: " + m.substring.substring(0, Math.min(50, m.substring.length())));
            
            // 检查是否包含目标字符串的起始位置
            if (m.startPos1 <= pos1 && pos1 < m.startPos1 + m.length) {
                System.out.println("  *** 这个片段包含了目标字符串的起始位置(109)！");
            }
        }
        
        // 手动测试DP算法在特定位置的行为
        System.out.println("\n=== 手动测试DP算法 ===");
        testDPAlgorithm(text1, text2, pos1, pos2, target.length());
    }
    
    private static void testDPAlgorithm(String text1, String text2, int targetPos1, int targetPos2, int targetLen) {
        // 测试在目标位置附近的DP行为
        int windowStart1 = Math.max(0, targetPos1 - 10);
        int windowEnd1 = Math.min(text1.length(), targetPos1 + targetLen + 10);
        int windowStart2 = Math.max(0, targetPos2 - 10);
        int windowEnd2 = Math.min(text2.length(), targetPos2 + targetLen + 10);
        
        String window1 = text1.substring(windowStart1, windowEnd1);
        String window2 = text2.substring(windowStart2, windowEnd2);
        
        System.out.println("测试窗口1 (" + windowStart1 + "-" + windowEnd1 + "): " + window1);
        System.out.println("测试窗口2 (" + windowStart2 + "-" + windowEnd2 + "): " + window2);
        
        int len1 = window1.length();
        int len2 = window2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];
        
        int maxLength = 0;
        int maxI = 0, maxJ = 0;
        
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (window1.charAt(i - 1) == window2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    if (dp[i][j] > maxLength) {
                        maxLength = dp[i][j];
                        maxI = i;
                        maxJ = j;
                    }
                } else {
                    dp[i][j] = 0;
                }
            }
        }
        
        System.out.println("窗口内最长匹配: " + maxLength + " 字符");
        if (maxLength > 0) {
            int startI = maxI - maxLength;
            int startJ = maxJ - maxLength;
            String matched = window1.substring(startI, maxI);
            System.out.println("匹配内容: " + matched);
            System.out.println("在原文档中的位置: 文档A=" + (windowStart1 + startI) + ", 文档B=" + (windowStart2 + startJ));
        }
    }
}
