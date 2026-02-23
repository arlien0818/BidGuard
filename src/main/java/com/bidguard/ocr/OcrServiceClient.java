package com.bidguard.ocr;

import com.bidguard.config.SimilarityConfig;
import com.bidguard.pdf.PdfPageRenderer;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * OCR 服务客户端：通过 HTTP 调用 EasyOCR 服务进行文字识别
 * 服务地址：http://localhost:5001/ocr
 */
public class OcrServiceClient {

    private static final String DEFAULT_OCR_URL = "http://localhost:5001/ocr";
    private static final int DEFAULT_TIMEOUT_MS = 300000; // 5分钟超时（扫描件识别较慢）
    private static final int TARGET_FILE_SIZE_KB = 500;  // 目标文件大小（KB，仅作参考）

    /**
     * OCR 识别结果
     */
    public static class OcrResult {
        public boolean success;
        public String fullText;
        public int textCount;
        public List<OcrTextItem> texts;
        public List<OcrPage> pages;
        public int pageCount;
        public String engine;
        public String error;

        public OcrResult() {
            this.texts = new ArrayList<>();
            this.pages = new ArrayList<>();
        }

        public boolean hasText() {
            return fullText != null && !fullText.isBlank();
        }
    }

    /**
     * 单个文字识别项
     */
    public static class OcrTextItem {
        public String text;
        public double confidence;
        public List<double[]> bbox; // [[x1,y1], [x2,y2], [x3,y3], [x4,y4]]
        public int page;
        
        // 字符级位置信息（仅阿里云OCR启用OutputCharInfo时有值）
        // 每个元素对应text中的一个字符，值为该字符的bbox [[x1,y1], [x2,y2], [x3,y3], [x4,y4]]
        public List<List<double[]>> charBboxes;

        @Override
        public String toString() {
            return String.format("text='%s', conf=%.2f, page=%d", text, confidence, page);
        }
    }

    /**
     * 单页识别结果
     */
    public static class OcrPage {
        public int page;
        public int textCount;
        public List<OcrTextItem> texts;
        public String fullText;

        public OcrPage() {
            this.texts = new ArrayList<>();
        }
    }

    /**
     * 识别图片中的文字
     * 
     * @param image 输入图片
     * @return OCR 识别结果
     * @throws IOException 网络请求失败或服务不可用
     */
    public static OcrResult recognizeImage(BufferedImage image) throws IOException {
        return recognizeImage(image, DEFAULT_OCR_URL, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 识别图片中的文字
     * 
     * @param image 输入图片
     * @param ocrUrl OCR 服务地址
     * @param timeoutMs 超时时间（毫秒）
     * @return OCR 识别结果
     * @throws IOException 网络请求失败或服务不可用
     */
    public static OcrResult recognizeImage(BufferedImage image, String ocrUrl, int timeoutMs) 
            throws IOException {
        
        System.out.println("[OCR客户端] 准备发送图片到: " + ocrUrl);
        System.out.println(String.format("[OCR压缩] 原始图片尺寸: %dx%d", image.getWidth(), image.getHeight()));
        
        // 读取配置参数
        SimilarityConfig config = SimilarityConfig.getInstance();
        int maxDimension = config.ocrImageMaxDimension;
        float jpegQuality = config.ocrJpegQuality;
        
        System.out.println(String.format("[OCR压缩] 配置参数 - 最大边长: %dpx, JPEG质量: %.2f", 
            maxDimension, jpegQuality));
        
        // 1. 压缩图片以加速传输和识别
        BufferedImage compressedImage = compressImageForOcr(image, maxDimension);
        
        // 2. 将压缩后的图片保存为临时 JPEG 文件（比 PNG 小很多）
        File tempFile = File.createTempFile("ocr_", ".jpg");
        tempFile.deleteOnExit();
        
        try {
            // 使用高质量 JPEG 压缩
            saveCompressedJpeg(compressedImage, tempFile, jpegQuality);
            
            long fileSizeKB = tempFile.length() / 1024;
            System.out.println(String.format("[OCR压缩] 压缩后文件: %s (%.2f KB)", 
                tempFile.getName(), fileSizeKB / 1024.0 * 1024));
            System.out.println(String.format("[OCR压缩] 压缩完成，文件大小: %d KB，目标: %d KB %s", 
                fileSizeKB, TARGET_FILE_SIZE_KB, 
                fileSizeKB <= TARGET_FILE_SIZE_KB ? "✓" : "(超出但可接受)"));
            
            // 3. 发送 HTTP multipart/form-data 请求
            return sendOcrRequest(tempFile, ocrUrl, timeoutMs);
            
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 识别 PDF 文件（客户端压缩后逐页发送）
     * 
     * @param pdfFile PDF 文件
     * @return OCR 识别结果
     * @throws IOException 网络请求失败或服务不可用
     */
    public static OcrResult recognizePdf(File pdfFile) throws IOException {
        return recognizePdf(pdfFile, DEFAULT_OCR_URL, DEFAULT_TIMEOUT_MS);
    }

    /**
     * 识别 PDF 文件（客户端压缩后逐页发送）
     * 策略：
     * 1. 在客户端将 PDF 每页渲染为图片
     * 2. 压缩每张图片到配置的最大尺寸
     * 3. 逐页发送压缩后的图片到 OCR 服务
     * 4. 合并所有页面的识别结果
     * 
     * @param pdfFile PDF 文件
     * @param ocrUrl OCR 服务地址
     * @param timeoutMs 超时时间（毫秒）
     * @return OCR 识别结果
     * @throws IOException 网络请求失败或服务不可用
     */
    public static OcrResult recognizePdf(File pdfFile, String ocrUrl, int timeoutMs) 
            throws IOException {
        
        if (!pdfFile.exists()) {
            throw new FileNotFoundException("PDF 文件不存在: " + pdfFile);
        }
        
        System.out.println("=".repeat(80));
        System.out.println(String.format("[OCR客户端] 开始处理 PDF: %s (%.2f MB)", 
            pdfFile.getName(), pdfFile.length() / (1024.0 * 1024.0)));
        System.out.println("[OCR策略] 客户端压缩模式 - 渲染每页→压缩→逐页发送");
        System.out.println("=".repeat(80));
        
        long pdfStartTime = System.currentTimeMillis();
        
        // 从配置读取DPI
        SimilarityConfig config = SimilarityConfig.getInstance();
        int renderDpi = config.ocrRenderDpi;
        
        // 1. 将 PDF 每页渲染为图片
        List<PdfPageRenderer.PageImage> pages = PdfPageRenderer.renderAllPages(
            pdfFile.toPath(), renderDpi);
        
        long renderTime = System.currentTimeMillis() - pdfStartTime;
        System.out.println(String.format("[OCR客户端] ✓ PDF渲染完成: %d 页，耗时 %d ms (DPI=%d) (DPI=%d)", 
            pages.size(), renderTime, renderDpi));
        
        // 2. 准备合并结果
        OcrResult mergedResult = new OcrResult();
        mergedResult.success = true;
        mergedResult.pageCount = pages.size();
        StringBuilder fullTextBuilder = new StringBuilder();
        int totalTextCount = 0;
        
        // 3. 逐页压缩并发送到 OCR 服务
        for (int i = 0; i < pages.size(); i++) {
            PdfPageRenderer.PageImage pageImage = pages.get(i);
            int pageNum = i + 1;
            
            System.out.println(String.format("\n[第 %d/%d 页] 开始处理...", 
                pageNum, pages.size()));
            
            try {
                // 调用 recognizeImage 方法（会自动压缩）
                OcrResult pageResult = recognizeImage(pageImage.image, ocrUrl, timeoutMs);
                
                if (pageResult.success && pageResult.hasText()) {
                    fullTextBuilder.append(pageResult.fullText);
                    if (i < pages.size() - 1) {
                        fullTextBuilder.append("\n\n--- 第 ").append(pageNum)
                            .append(" 页结束 ---\n\n");
                    }
                    totalTextCount += pageResult.textCount;
                    
                    System.out.println(String.format("[第 %d/%d 页] ✓ 识别成功: %d 个文本块，%d 字符", 
                        pageNum, pages.size(), pageResult.textCount, 
                        pageResult.fullText != null ? pageResult.fullText.length() : 0));
                } else {
                    System.out.println(String.format("[第 %d/%d 页] ⚠ 未识别到文本", 
                        pageNum, pages.size()));
                }
                
            } catch (Exception e) {
                System.err.println(String.format("[第 %d/%d 页] ✗ 识别失败: %s", 
                    pageNum, pages.size(), e.getMessage()));
                mergedResult.success = false;
                mergedResult.error = "第 " + pageNum + " 页识别失败: " + e.getMessage();
            }
        }
        
        // 4. 合并所有结果
        mergedResult.fullText = fullTextBuilder.toString();
        mergedResult.textCount = totalTextCount;
        mergedResult.engine = "easyocr";
        
        long totalTime = System.currentTimeMillis() - pdfStartTime;
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println(String.format("[OCR客户端] ✓ PDF 识别完成！总耗时: %d ms (%.1f 秒)", 
            totalTime, totalTime / 1000.0));
        System.out.println(String.format("[OCR客户端] 统计: %d 页，%d 个文本块，%d 字符", 
            mergedResult.pageCount, mergedResult.textCount, 
            mergedResult.fullText != null ? mergedResult.fullText.length() : 0));
        System.out.println("=".repeat(80));
        
        return mergedResult;
    }

    /**
     * 发送 OCR 请求（支持图片和 PDF）
     */
    private static OcrResult sendOcrRequest(File file, String ocrUrl, int timeoutMs) 
            throws IOException {
        
        String boundary = "----BidGuardBoundary" + UUID.randomUUID().toString().replace("-", "");
        
        URL url = new URL(ocrUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setConnectTimeout(10000); // 连接超时 10 秒
            conn.setReadTimeout(timeoutMs); // 读取超时（默认 5 分钟）
            
            long startTime = System.currentTimeMillis();
            
            // 构建 multipart/form-data 请求体
            try (OutputStream out = conn.getOutputStream();
                 BufferedOutputStream bout = new BufferedOutputStream(out)) {
                
                // 文件部分
                bout.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                bout.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + 
                    file.getName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                bout.write(("Content-Type: application/octet-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
                
                // 写入文件内容
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        bout.write(buffer, 0, bytesRead);
                    }
                }
                
                bout.write("\r\n".getBytes(StandardCharsets.UTF_8));
                bout.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                bout.flush();
            }
            
            System.out.println("[OCR客户端] 请求已发送，等待响应...");
            
            // 读取响应
            int responseCode = conn.getResponseCode();
            long uploadTime = System.currentTimeMillis() - startTime;
            
            System.out.println(String.format("[OCR客户端] HTTP 响应码: %d (上传耗时: %d ms)", 
                responseCode, uploadTime));
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String jsonResponse = readResponse(conn.getInputStream());
                long totalTime = System.currentTimeMillis() - startTime;
                
                System.out.println(String.format("[OCR客户端] 识别完成，总耗时: %d ms", totalTime));
                
                return parseOcrResponse(jsonResponse);
            } else {
                String errorMsg = readResponse(conn.getErrorStream());
                throw new IOException(String.format("OCR 服务返回错误 %d: %s", 
                    responseCode, errorMsg));
            }
            
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 读取 HTTP 响应内容
     */
    private static String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    /**
     * 解析 OCR 服务返回的 JSON 响应（简单解析）
     */
    private static OcrResult parseOcrResponse(String json) {
        OcrResult result = new OcrResult();
        
        try {
            // 🔍 DEBUG: 打印原始JSON前500字符
            int previewLen = Math.min(500, json.length());
            String jsonPreview = json.substring(0, previewLen);
            System.out.println("[OCR客户端] JSON响应预览 (前" + previewLen + "字符):");
            System.out.println(jsonPreview);
            if (json.length() > 500) {
                System.out.println("... (总长度: " + json.length() + " 字符)");
            }
            System.out.println();
            
            // 简单的 JSON 解析（生产环境建议使用 Gson 或 Jackson）
            result.success = json.contains("\"success\":true") || json.contains("\"success\": true");
            
            if (!result.success) {
                // 提取错误信息
                int errorStart = json.indexOf("\"error\":");
                if (errorStart > 0) {
                    int valueStart = json.indexOf("\"", errorStart + 8) + 1;
                    int valueEnd = json.indexOf("\"", valueStart);
                    if (valueEnd > valueStart) {
                        result.error = decodeJsonString(json.substring(valueStart, valueEnd));
                    }
                }
                return result;
            }
            
            // 提取 full_text
            int fullTextStart = json.indexOf("\"full_text\":");
            if (fullTextStart > 0) {
                int valueStart = json.indexOf("\"", fullTextStart + 12) + 1;
                int valueEnd = findJsonStringEnd(json, valueStart);
                if (valueEnd > valueStart) {
                    result.fullText = decodeJsonString(json.substring(valueStart, valueEnd));
                }
            }
            
            // 提取 text_count
            int textCountStart = json.indexOf("\"text_count\":");
            if (textCountStart > 0) {
                int valueStart = textCountStart + 13;
                int valueEnd = json.indexOf(",", valueStart);
                if (valueEnd < 0) valueEnd = json.indexOf("}", valueStart);
                if (valueEnd > valueStart) {
                    String countStr = json.substring(valueStart, valueEnd).trim();
                    result.textCount = Integer.parseInt(countStr);
                }
            }
            
            // 提取 page_count
            int pageCountStart = json.indexOf("\"page_count\":");
            if (pageCountStart > 0) {
                int valueStart = pageCountStart + 13;
                int valueEnd = json.indexOf(",", valueStart);
                if (valueEnd < 0) valueEnd = json.indexOf("}", valueStart);
                if (valueEnd > valueStart) {
                    String countStr = json.substring(valueStart, valueEnd).trim();
                    result.pageCount = Integer.parseInt(countStr);
                }
            }
            
            // 提取 engine
            int engineStart = json.indexOf("\"engine\":");
            if (engineStart > 0) {
                int valueStart = json.indexOf("\"", engineStart + 9) + 1;
                int valueEnd = json.indexOf("\"", valueStart);
                if (valueEnd > valueStart) {
                    result.engine = json.substring(valueStart, valueEnd);
                }
            }
            
            System.out.println(String.format("[OCR客户端] 解析结果: success=%s, textCount=%d, pageCount=%d, engine=%s", 
                result.success, result.textCount, result.pageCount, result.engine));
            System.out.println(String.format("[OCR客户端] 识别文本长度: %d 字符", 
                result.fullText != null ? result.fullText.length() : 0));
            
        } catch (Exception e) {
            System.err.println("[OCR客户端] JSON 解析失败: " + e.getMessage());
            result.success = false;
            result.error = "JSON 解析失败: " + e.getMessage();
        }
        
        return result;
    }

    /**
     * 查找 JSON 字符串值的结束位置（处理转义字符）
     */
    private static int findJsonStringEnd(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == start || json.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return json.length();
    }

    /**
     * 解码 JSON 字符串中的转义序列（包括 Unicode）
     * 处理：换行符(\n)、回车符(\r)、制表符(\t)、引号(\")、反斜杠(\\)、Unicode转义
     * 
     * @param str JSON 字符串值（已去除两端引号）
     * @return 解码后的字符串
     */
    private static String decodeJsonString(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        StringBuilder result = new StringBuilder(str.length());
        int i = 0;
        
        while (i < str.length()) {
            char c = str.charAt(i);
            
            if (c == '\\' && i + 1 < str.length()) {
                char next = str.charAt(i + 1);
                
                switch (next) {
                    case 'n':
                        result.append('\n');
                        i += 2;
                        break;
                    case 'r':
                        result.append('\r');
                        i += 2;
                        break;
                    case 't':
                        result.append('\t');
                        i += 2;
                        break;
                    case '"':
                        result.append('"');
                        i += 2;
                        break;
                    case '\\':
                        result.append('\\');
                        i += 2;
                        break;
                    case 'u':
                        // Unicode 转义: 反斜杠u加4位十六进制数字
                        if (i + 5 < str.length()) {
                            try {
                                String hex = str.substring(i + 2, i + 6);
                                int codePoint = Integer.parseInt(hex, 16);
                                result.append((char) codePoint);
                                i += 6;
                            } catch (NumberFormatException e) {
                                // 如果解析失败，保留原样
                                result.append(c);
                                i++;
                            }
                        } else {
                            // Unicode 转义不完整，保留原样
                            result.append(c);
                            i++;
                        }
                        break;
                    default:
                        // 未知转义序列，保留反斜杠
                        result.append(c);
                        i++;
                        break;
                }
            } else {
                result.append(c);
                i++;
            }
        }
        
        return result.toString();
    }

    /**
     * 压缩图片以优化 OCR 识别速度
     * 策略：
     * 1. 限制最大尺寸（避免过大图片导致超时）
     * 2. 保持宽高比
     * 3. 使用高质量插值算法保证文字清晰度
     * 
     * @param image 原始图片
     * @param maxDimension 最大边长限制（像素）
     * @return 压缩后的图片
     */
    private static BufferedImage compressImageForOcr(BufferedImage image, int maxDimension) {
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();
        
        // 计算是否需要缩放
        int maxSize = Math.max(originalWidth, originalHeight);
        
        if (maxSize <= maxDimension) {
            System.out.println(String.format(
                "[OCR压缩] 图片尺寸适中，无需缩放 (%dx%d <= %d)", 
                originalWidth, originalHeight, maxDimension));
            return image;
        }
        
        // 计算缩放比例（保持宽高比）
        double scale = (double) maxDimension / maxSize;
        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);
        
        System.out.println(String.format(
            "[OCR压缩] 压缩目标: %dx%d -> %dx%d (缩放比例: %.2f%%, 最大边长: %dpx)", 
            originalWidth, originalHeight, newWidth, newHeight, 
            scale * 100, maxDimension));
        
        // 使用高质量插值算法缩放（保证 OCR 识别效果）
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        
        // 设置高质量渲染参数
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.drawImage(image, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        
        System.out.println("[OCR压缩] ✓ 图片缩放完成");
        
        return resized;
    }

    /**
     * 保存为高质量 JPEG 格式（比 PNG 小很多，但保持文字清晰度）
     * 
     * @param image 图片
     * @param outputFile 输出文件
     * @param quality 质量参数（0.0-1.0）
     * @throws IOException 保存失败
     */
    private static void saveCompressedJpeg(BufferedImage image, File outputFile, float quality) 
            throws IOException {
        
        // 获取 JPEG 编码器
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("未找到 JPEG 编码器");
        }
        
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        
        // 设置压缩参数
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        
        // 写入文件
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /**
     * 测试 OCR 服务是否可用
     * 
     * @return 服务是否在线
     */
    public static boolean isServiceAvailable() {
        return isServiceAvailable(DEFAULT_OCR_URL);
    }

    /**
     * 测试 OCR 服务是否可用
     * 
     * @param ocrUrl OCR 服务地址（根路径，如 http://localhost:5001）
     * @return 服务是否在线
     */
    public static boolean isServiceAvailable(String ocrUrl) {
        try {
            String rootUrl = ocrUrl.replace("/ocr", "");
            URL url = new URL(rootUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            
            int code = conn.getResponseCode();
            conn.disconnect();
            
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
