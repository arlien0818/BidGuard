package com.bidguard;

import com.google.gson.Gson;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * PDF标注器
 * 
 * 功能：根据查重检测结果，在PDF上标注重复内容的位置
 */
public class PdfAnnotator {
    private static final Logger LOGGER = Logger.getLogger(PdfAnnotator.class.getName());
    
    // 标注颜色
    private static final Color DUPLICATE_COLOR = new Color(255, 0, 0, 100); // 半透明红色
    private static final float STROKE_WIDTH = 2.0f;
    
    // DPI转换（OCR图像渲染DPI vs PDF原生DPI）
    private static final float OCR_RENDER_DPI = 200.0f; // 必须与OcrServiceFactory中的renderImageWithDPI参数一致
    private static final float PDF_NATIVE_DPI = 72.0f;  // PDF默认坐标单位（点）
    private static final float DPI_SCALE = PDF_NATIVE_DPI / OCR_RENDER_DPI; // 0.36
    
    // 坐标网格（调试用）
    private static final boolean DRAW_COORDINATE_GRID = true; // 是否绘制坐标网格
    private static final int GRID_INTERVAL = 100; // 网格间隔（单位）
    private static final int GRID_MARK_LENGTH = 10; // 网格标记长度（单位）
    private static final Color GRID_COLOR = new Color(0, 0, 255); // 蓝色网格线
    
    /**
     * 标注结果
     */
    public static class AnnotationResult {
        public File annotatedFile1;
        public File annotatedFile2;
        public int totalAnnotations1;
        public int totalAnnotations2;
        
        public AnnotationResult(File file1, File file2, int count1, int count2) {
            this.annotatedFile1 = file1;
            this.annotatedFile2 = file2;
            this.totalAnnotations1 = count1;
            this.totalAnnotations2 = count2;
        }
    }
    
    /**
     * 根据查重检测结果标注两个PDF
     * 
     * @param detectionJsonFile 查重检测结果JSON文件
     * @param originalPdf1 原始PDF文件1
     * @param originalPdf2 原始PDF文件2
     * @return 标注结果
     */
    public static AnnotationResult annotatePdfs(
            File detectionJsonFile,
            File originalPdf1,
            File originalPdf2) throws IOException {
        
        LOGGER.info("开始PDF标注:");
        LOGGER.info("  检测结果: " + detectionJsonFile.getName());
        LOGGER.info("  PDF1: " + originalPdf1.getName());
        LOGGER.info("  PDF2: " + originalPdf2.getName());
        
        // 1. 读取查重检测结果
        OcrDuplicateDetector.DuplicateDetectionResult detection = loadDetectionResult(detectionJsonFile);
        LOGGER.info(String.format("加载检测结果: %d 个重复片段", detection.totalMatches));
        
        // 2. 为每个文档收集所有需要标注的位置
        Map<Integer, java.util.List<double[][]>> annotationsDoc1 = collectAnnotations(detection, 1);
        Map<Integer, java.util.List<double[][]>> annotationsDoc2 = collectAnnotations(detection, 2);
        
        LOGGER.info(String.format("文档1需要标注 %d 个页面", annotationsDoc1.size()));
        LOGGER.info(String.format("文档2需要标注 %d 个页面", annotationsDoc2.size()));
        
        // 3. 生成输出文件路径
        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        String timestamp = String.valueOf(System.currentTimeMillis());
        String baseName1 = originalPdf1.getName().replaceAll("(?i)\\.pdf$", "");
        String baseName2 = originalPdf2.getName().replaceAll("(?i)\\.pdf$", "");
        
        File annotatedFile1 = new File(outputDir, baseName1 + "_annotated_" + timestamp + ".pdf");
        File annotatedFile2 = new File(outputDir, baseName2 + "_annotated_" + timestamp + ".pdf");
        
        // 4. 标注PDF1
        int count1 = annotateDocument(originalPdf1, annotatedFile1, annotationsDoc1);
        LOGGER.info(String.format("文档1标注完成: %d 个bbox", count1));
        
        // 5. 标注PDF2
        int count2 = annotateDocument(originalPdf2, annotatedFile2, annotationsDoc2);
        LOGGER.info(String.format("文档2标注完成: %d 个bbox", count2));
        
        return new AnnotationResult(annotatedFile1, annotatedFile2, count1, count2);
    }
    
    /**
     * 从JSON文件加载查重检测结果
     */
    private static OcrDuplicateDetector.DuplicateDetectionResult loadDetectionResult(File jsonFile) 
            throws IOException {
        Gson gson = new Gson();
        try (Reader reader = new InputStreamReader(
                new FileInputStream(jsonFile),
                StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, OcrDuplicateDetector.DuplicateDetectionResult.class);
        }
    }
    
    /**
     * 收集需要标注的位置
     * 
     * @param detection 查重检测结果
     * @param docNumber 文档编号 (1 或 2)
     * @return Map<页码, List<bbox坐标>>
     */
    private static Map<Integer, java.util.List<double[][]>> collectAnnotations(
            OcrDuplicateDetector.DuplicateDetectionResult detection,
            int docNumber) {
        
        Map<Integer, java.util.List<double[][]>> pageAnnotations = new HashMap<>();
        
        for (OcrDuplicateDetector.DuplicateMatch match : detection.matches) {
            OcrDuplicateDetector.DocumentLocation location = 
                (docNumber == 1) ? match.doc1Location : match.doc2Location;
            
            for (OcrDuplicateDetector.TextBlockRef block : location.textBlocks) {
                if (block.bbox != null && block.bbox.size() == 4) {
                    // 计算重复内容在该块中的覆盖率
                    int blockLength = block.text.length();
                    int overlapLength = block.endCharInBlock - block.startCharInBlock;
                    double coverageRatio = (double) overlapLength / blockLength;
                    
                    // 策略：只标注覆盖率≥55%的块
                    // 这样既能标注跨行的真实重复内容，又能过滤掉低覆盖率的误标
                    if (coverageRatio < 0.55) {
                        LOGGER.info(String.format("跳过低覆盖率块 #%d (覆盖率: %.1f%%, 重叠: %d/%d)",
                            block.blockIndex, coverageRatio * 100, overlapLength, blockLength));
                        continue;
                    }
                    
                    LOGGER.info(String.format("标注块 #%d (覆盖率: %.1f%%, 重叠: %d/%d)",
                        block.blockIndex, coverageRatio * 100, overlapLength, blockLength));
                    
                    int pageNum = block.pageNumber;
                    
                    // 转换为二维数组格式
                    double[][] bbox = new double[4][2];
                    for (int i = 0; i < 4; i++) {
                        bbox[i][0] = block.bbox.get(i)[0];
                        bbox[i][1] = block.bbox.get(i)[1];
                    }
                    
                    pageAnnotations.computeIfAbsent(pageNum, k -> new java.util.ArrayList<>()).add(bbox);
                }
            }
        }
        
        return pageAnnotations;
    }
    
    /**
     * 标注单个PDF文档
     * 
     * @param originalPdf 原始PDF文件
     * @param outputPdf 输出PDF文件
     * @param pageAnnotations 页面标注Map<页码, List<bbox>>
     * @return 标注的bbox总数
     */
    private static int annotateDocument(
            File originalPdf,
            File outputPdf,
            Map<Integer, java.util.List<double[][]>> pageAnnotations) throws IOException {
        
        int totalAnnotations = 0;
        
        try (PDDocument document = PDDocument.load(originalPdf)) {
            // 遍历所有需要标注的页面
            for (Map.Entry<Integer, java.util.List<double[][]>> entry : pageAnnotations.entrySet()) {
                int pageNum = entry.getKey();
                List<double[][]> bboxes = entry.getValue();
                
                // 页码从1开始，PDFBox索引从0开始
                int pageIndex = pageNum - 1;
                
                if (pageIndex >= 0 && pageIndex < document.getNumberOfPages()) {
                    PDPage page = document.getPage(pageIndex);
                    
                    // 在这个页面上绘制所有bbox
                    annotatePage(document, page, bboxes);
                    totalAnnotations += bboxes.size();
                    
                    LOGGER.info(String.format("  第%d页: 标注 %d 个区域", pageNum, bboxes.size()));
                } else {
                    LOGGER.warning(String.format("  页码 %d 超出范围，跳过", pageNum));
                }
            }
            
            // 保存标注后的PDF
            document.save(outputPdf);
            LOGGER.info("已保存标注PDF: " + outputPdf.getName());
        }
        
        return totalAnnotations;
    }
    
    /**
     * 在单个页面上绘制标注
     * 
     * @param document PDF文档
     * @param page 页面
     * @param bboxes bbox列表（图像坐标系）
     */
    private static void annotatePage(
            PDDocument document,
            PDPage page,
            List<double[][]> bboxes) throws IOException {
        
        PDRectangle mediaBox = page.getMediaBox();
        float pageWidth = mediaBox.getWidth();
        float pageHeight = mediaBox.getHeight();
        
        // 计算OCR渲染图像的尺寸（基于OCR_RENDER_DPI）
        // 无论PDF原始尺寸是多少，我们都按固定DPI重新渲染
        // 例如：A4纸PDF(595×842点) → 按200 DPI渲染 → 图像(1653×2339像素)
        float renderImageWidth = pageWidth * (OCR_RENDER_DPI / PDF_NATIVE_DPI);
        float renderImageHeight = pageHeight * (OCR_RENDER_DPI / PDF_NATIVE_DPI);
        
        // 计算缩放比例：OCR图像坐标 → PDF坐标
        // 这个比例对任意尺寸的PDF都适用（只要长宽比一致）
        float scaleX = pageWidth / renderImageWidth;   // = 72/200 = 0.36
        float scaleY = pageHeight / renderImageHeight; // = 72/200 = 0.36
        
        // 调试信息：首次标注时输出页面信息
        if (bboxes.size() > 0 && LOGGER.isLoggable(java.util.logging.Level.FINE)) {
            LOGGER.fine(String.format(
                "[页面尺寸] PDF: %.1f×%.1f点, 渲染图像: %.1f×%.1f像素, 缩放比例: %.4f",
                pageWidth, pageHeight, renderImageWidth, renderImageHeight, scaleX));
        }
        
        // 验证：对于标准矩形（A4长宽比），scaleX应该等于scaleY
        if (Math.abs(scaleX - scaleY) > 0.001) {
            LOGGER.warning(String.format(
                "[坐标转换警告] 缩放比例不一致: scaleX=%.4f, scaleY=%.4f, 页面可能有旋转或非标准长宽比",
                scaleX, scaleY));
        }
        
        // 创建内容流，追加模式（不覆盖原内容）
        try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            
            // 绘制坐标网格（调试用）
            if (DRAW_COORDINATE_GRID) {
                drawCoordinateGrid(contentStream, mediaBox, pageHeight);
            }
            
            // 设置绘制参数
            contentStream.setStrokingColor(DUPLICATE_COLOR);
            contentStream.setLineWidth(STROKE_WIDTH);
            
            // 绘制每个bbox
            for (double[][] bbox : bboxes) {
                // bbox格式: [[x1,y1], [x2,y2], [x3,y3], [x4,y4]]
                // 顺序: 左上、右上、右下、左下
                
                // 坐标转换：OCR图像坐标 -> PDF坐标
                // OCR: 左上角为原点，Y向下（像素坐标）
                // PDF: 左下角为原点，Y向上（点坐标，72 DPI）
                
                double[][] pdfBbox = new double[4][2];
                for (int i = 0; i < 4; i++) {
                    pdfBbox[i][0] = (float) bbox[i][0] * scaleX;  // X坐标缩放
                    pdfBbox[i][1] = pageHeight - (float) bbox[i][1] * scaleY;  // Y坐标缩放并翻转
                }
                
                // 绘制矩形（使用四个顶点绘制多边形）
                contentStream.moveTo((float) pdfBbox[0][0], (float) pdfBbox[0][1]); // 左上
                contentStream.lineTo((float) pdfBbox[1][0], (float) pdfBbox[1][1]); // 右上
                contentStream.lineTo((float) pdfBbox[2][0], (float) pdfBbox[2][1]); // 右下
                contentStream.lineTo((float) pdfBbox[3][0], (float) pdfBbox[3][1]); // 左下
                contentStream.closePath();
                contentStream.stroke();
                
                // 可选：添加半透明填充
                // contentStream.setNonStrokingColor(DUPLICATE_COLOR);
                // contentStream.fill();
            }
        }
    }
    
    /**
     * 绘制坐标网格（调试用）
     * 在PDF坐标系中绘制网格线和标记
     * 
     * @param contentStream 内容流
     * @param mediaBox 页面尺寸
     * @param pageHeight 页面高度
     */
    private static void drawCoordinateGrid(
            PDPageContentStream contentStream,
            PDRectangle mediaBox,
            float pageHeight) throws IOException {
        
        float pageWidth = mediaBox.getWidth();
        
        contentStream.setStrokingColor(GRID_COLOR);
        contentStream.setLineWidth(1.5f); // 加粗一点便于观察
        
        // 绘制垂直网格线（X轴方向，每100单位）
        for (int x = 0; x <= pageWidth; x += GRID_INTERVAL) {
            // 在底部画一个小标记（PDF坐标系：Y=0是底部）
            contentStream.moveTo(x, 0);
            contentStream.lineTo(x, GRID_MARK_LENGTH);
            contentStream.stroke();
            
            // 在顶部画一个小标记
            contentStream.moveTo(x, pageHeight - GRID_MARK_LENGTH);
            contentStream.lineTo(x, pageHeight);
            contentStream.stroke();
        }
        
        // 绘制水平网格线（Y轴方向，每100单位）
        for (int y = 0; y <= pageHeight; y += GRID_INTERVAL) {
            // 在左侧画一个小标记（PDF坐标系：X=0是左边）
            contentStream.moveTo(0, y);
            contentStream.lineTo(GRID_MARK_LENGTH, y);
            contentStream.stroke();
            
            // 在右侧画一个小标记
            contentStream.moveTo(pageWidth - GRID_MARK_LENGTH, y);
            contentStream.lineTo(pageWidth, y);
            contentStream.stroke();
        }
    }
    
    /**
     * 测试方法：标注示例PDF
     */
    public static void main(String[] args) {
        try {
            // 查找最新的查重检测结果JSON
            File outputDir = new File("output");
            File[] jsonFiles = outputDir.listFiles((dir, name) -> 
                name.startsWith("duplicate_detection_") && name.endsWith(".json"));
            
            if (jsonFiles == null || jsonFiles.length == 0) {
                System.err.println("未找到查重检测结果JSON文件");
                System.err.println("请先运行查重检测生成JSON文件");
                return;
            }
            
            // 选择最新的JSON文件
            File latestJson = null;
            long latestTime = 0;
            for (File f : jsonFiles) {
                if (f.lastModified() > latestTime) {
                    latestTime = f.lastModified();
                    latestJson = f;
                }
            }
            
            System.out.println("使用检测结果: " + latestJson.getName());
            
            // 从JSON文件名推断原始PDF文件名
            // 格式: duplicate_detection_文件1_vs_文件2_时间戳.json
            String jsonName = latestJson.getName();
            String[] parts = jsonName.replace("duplicate_detection_", "")
                                     .replace(".json", "")
                                     .split("_vs_");
            
            if (parts.length != 2) {
                System.err.println("无法从JSON文件名解析PDF文件名");
                return;
            }
            
            String pdfName1 = parts[0].replaceAll("_\\d+$", "") + ".pdf";
            String pdfName2 = parts[1].replaceAll("_\\d+$", "") + ".pdf";
            
            // 在testfiles目录查找PDF
            File testFilesDir = new File("testfiles");
            File pdf1 = new File(testFilesDir, pdfName1);
            File pdf2 = new File(testFilesDir, pdfName2);
            
            if (!pdf1.exists()) {
                pdf1 = new File(pdfName1);
            }
            if (!pdf2.exists()) {
                pdf2 = new File(pdfName2);
            }
            
            if (!pdf1.exists() || !pdf2.exists()) {
                System.err.println("找不到原始PDF文件:");
                System.err.println("  PDF1: " + pdf1.getAbsolutePath() + " (存在:" + pdf1.exists() + ")");
                System.err.println("  PDF2: " + pdf2.getAbsolutePath() + " (存在:" + pdf2.exists() + ")");
                return;
            }
            
            System.out.println("\n开始标注PDF...");
            
            // 执行标注
            AnnotationResult result = annotatePdfs(latestJson, pdf1, pdf2);
            
            System.out.println("\n标注完成！");
            System.out.println("=".repeat(60));
            System.out.println("文档1: " + result.annotatedFile1.getName());
            System.out.println("  标注区域: " + result.totalAnnotations1 + " 个");
            System.out.println();
            System.out.println("文档2: " + result.annotatedFile2.getName());
            System.out.println("  标注区域: " + result.totalAnnotations2 + " 个");
            System.out.println("=".repeat(60));
            System.out.println("文件位置: " + result.annotatedFile1.getParent());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
