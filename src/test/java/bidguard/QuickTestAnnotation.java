package com.bidguard;

import java.io.File;

/**
 * 快速测试PDF标注功能
 */
public class QuickTestAnnotation {
    
    public static void main(String[] args) {
        try {
            System.out.println("=".repeat(80));
            System.out.println("快速测试PDF标注 - gpt文档A和gpt文档B");
            System.out.println("=".repeat(80));
            System.out.println();
            
            // 1. 查找最新的查重检测结果JSON文件
            File outputDir = new File("output");
            File[] jsonFiles = outputDir.listFiles((dir, name) -> 
                name.startsWith("duplicate_detection_gpt文档A_vs_gpt文档B_") && 
                name.endsWith(".json"));
            
            if (jsonFiles == null || jsonFiles.length == 0) {
                System.err.println("未找到查重检测结果JSON文件");
                return;
            }
            
            // 找到最新的文件
            File latestJsonFile = jsonFiles[0];
            for (File f : jsonFiles) {
                if (f.lastModified() > latestJsonFile.lastModified()) {
                    latestJsonFile = f;
                }
            }
            
            System.out.println("1. 使用查重结果: " + latestJsonFile.getName());
            
            // 2. 查找原始PDF文件
            File pdf1 = new File("testfiles/gpt文档A.pdf");
            File pdf2 = new File("testfiles/gpt文档B.pdf");
            
            if (!pdf1.exists()) {
                System.err.println("未找到PDF文件: " + pdf1.getPath());
                return;
            }
            if (!pdf2.exists()) {
                System.err.println("未找到PDF文件: " + pdf2.getPath());
                return;
            }
            
            System.out.println("   PDF1: " + pdf1.getName());
            System.out.println("   PDF2: " + pdf2.getName());
            System.out.println();
            
            // 3. 执行标注
            System.out.println("2. 开始标注PDF...");
            PdfAnnotator.AnnotationResult result = PdfAnnotator.annotatePdfs(
                latestJsonFile, pdf1, pdf2);
            
            System.out.println();
            System.out.println("=".repeat(80));
            System.out.println("标注完成！");
            System.out.println("=".repeat(80));
            System.out.println("文档1标注结果: " + result.annotatedFile1.getName());
            System.out.println("  标注区域数: " + result.totalAnnotations1);
            System.out.println();
            System.out.println("文档2标注结果: " + result.annotatedFile2.getName());
            System.out.println("  标注区域数: " + result.totalAnnotations2);
            System.out.println("=".repeat(80));
            
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
