package com.bidguard;

import com.aliyun.ocr_api20210707.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OCR 服务工厂类
 * 根据配置选择使用本地 EasyOCR 服务或阿里云 OCR 服务
 */
public class OcrServiceFactory {
    private static final Logger LOGGER = Logger.getLogger(OcrServiceFactory.class.getName());
    
    /**
     * 根据配置选择 OCR 服务识别 PDF（支持缓存）
     * 
     * @param pdfFile PDF 文件
     * @return OCR 识别结果
     * @throws IOException 识别失败
     */
    public static OcrServiceClient.OcrResult recognizePdf(File pdfFile) throws IOException {
        // 1. 检查缓存是否存在且有效
        File cacheFile = getCacheFile(pdfFile);
        
        if (cacheFile.exists()) {
            // 检查缓存是否过期（PDF文件修改时间晚于缓存文件）
            if (pdfFile.lastModified() <= cacheFile.lastModified()) {
                LOGGER.info("找到OCR缓存文件，直接加载：" + cacheFile.getName());
                try {
                    OcrServiceClient.OcrResult cachedResult = loadCacheFromFile(cacheFile);
                    if (cachedResult != null && cachedResult.success) {
                        LOGGER.info(String.format("成功加载缓存：%d页，%d字符", 
                            cachedResult.pageCount, cachedResult.fullText.length()));
                        return cachedResult;
                    }
                } catch (Exception e) {
                    LOGGER.warning("加载缓存失败，将重新识别：" + e.getMessage());
                }
            } else {
                LOGGER.info("缓存文件已过期（PDF已修改），将重新识别");
            }
        }
        
        // 2. 缓存不存在或无效，调用OCR服务
        SimilarityConfig config = SimilarityConfig.getInstance();
        OcrServiceClient.OcrResult result;
        
        if ("aliyun".equalsIgnoreCase(config.ocrType)) {
            LOGGER.info("使用阿里云 OCR 服务进行识别");
            result = recognizePdfWithAliyun(pdfFile);
        } else {
            LOGGER.info("使用本地 EasyOCR 服务进行识别");
            result = OcrServiceClient.recognizePdf(pdfFile);
        }
        
        // 3. 保存识别结果到缓存
        if (result != null && result.success) {
            try {
                saveCacheToFile(result, cacheFile);
                LOGGER.info("OCR结果已缓存到：" + cacheFile.getAbsolutePath());
            } catch (Exception e) {
                LOGGER.warning("保存缓存失败：" + e.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * 获取缓存文件路径
     */
    private static File getCacheFile(File pdfFile) {
        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        String baseName = pdfFile.getName().replaceAll("(?i)\\.pdf$", "");
        String cacheFileName = baseName + "_ocr_cache.json";
        
        return new File(outputDir, cacheFileName);
    }
    
    /**
     * 保存OCR结果到缓存文件
     */
    private static void saveCacheToFile(OcrServiceClient.OcrResult result, File cacheFile) throws IOException {
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
        
        String json = gson.toJson(result);
        
        // 如果文件已存在且为只读，先设置为可写
        if (cacheFile.exists() && !cacheFile.canWrite()) {
            cacheFile.setWritable(true);
        }
        
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(
                    new FileOutputStream(cacheFile), 
                    java.nio.charset.StandardCharsets.UTF_8))) {
            writer.print(json);
        }
        
        // 设置缓存文件为只读，防止误删
        if (cacheFile.exists() && cacheFile.setReadOnly()) {
            LOGGER.fine("缓存文件已设置为只读：" + cacheFile.getName());
        }
    }
    
    /**
     * 从缓存文件加载OCR结果
     */
    private static OcrServiceClient.OcrResult loadCacheFromFile(File cacheFile) throws IOException {
        Gson gson = new Gson();
        
        try (Reader reader = new InputStreamReader(
                new FileInputStream(cacheFile), 
                java.nio.charset.StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, OcrServiceClient.OcrResult.class);
        }
    }
    
    /**
     * 根据配置选择 OCR 服务识别图片
     * 
     * @param image 图片
     * @return OCR 识别结果
     * @throws IOException 识别失败
     */
    public static OcrServiceClient.OcrResult recognizeImage(BufferedImage image) throws IOException {
        SimilarityConfig config = SimilarityConfig.getInstance();
        
        if ("aliyun".equalsIgnoreCase(config.ocrType)) {
            LOGGER.info("使用阿里云 OCR 服务进行识别");
            return recognizeImageWithAliyun(image);
        } else {
            LOGGER.info("使用本地 EasyOCR 服务进行识别");
            return OcrServiceClient.recognizeImage(image);
        }
    }
    
    /**
     * 检查 OCR 服务是否可用
     * 
     * @return 服务是否可用
     */
    public static boolean isServiceAvailable() {
        SimilarityConfig config = SimilarityConfig.getInstance();
        
        if ("aliyun".equalsIgnoreCase(config.ocrType)) {
            // 检查阿里云配置是否完整
            return !config.ocrAliyunAccessKeyId.isEmpty() 
                   && !config.ocrAliyunAccessKeySecret.isEmpty();
        } else {
            // 检查本地服务
            return OcrServiceClient.isServiceAvailable();
        }
    }
    
    /**
     * 使用阿里云 OCR 识别 PDF
     */
    private static OcrServiceClient.OcrResult recognizePdfWithAliyun(File pdfFile) throws IOException {
        // 获取配置实例
        SimilarityConfig config = SimilarityConfig.getInstance();
        
        OcrServiceClient.OcrResult result = new OcrServiceClient.OcrResult();
        result.engine = "aliyun";
        
        // 创建output目录
        File outputDir = new File("output");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        // 生成本次识别的时间戳
        String timestamp = String.valueOf(System.currentTimeMillis());
        String baseName = pdfFile.getName().replaceAll("\\.pdf$", "");
        
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            result.pageCount = pageCount;
            
            StringBuilder fullText = new StringBuilder();
            int totalTextCount = 0;
            
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                LOGGER.info(String.format("正在识别第 %d/%d 页...", pageIndex + 1, pageCount));
                
                // 渲染 PDF 页面为图片（使用配置的DPI）
                int renderDpi = config.ocrRenderDpi;
                BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, renderDpi);
                
                // 可选：去除红章（当前默认关闭，预留给将来的去红章功能）
                if (config.ocrRemoveSealEnabled) {
                    LOGGER.info("执行红章去除...");
                    pageImage = SimpleSealRemover.removeSeal(pageImage);
                }
                
                // 调用阿里云 OCR 识别
                OcrServiceClient.OcrResult pageResult = recognizeImageWithAliyun(pageImage);
                
                if (pageResult.success && pageResult.hasText()) {
                    // 保存每页的识别结果到文件
                    String pageFileName = String.format("%s_page_%03d_%s.txt", 
                        baseName, pageIndex + 1, timestamp);
                    File pageFile = new File(outputDir, pageFileName);
                    
                    try {
                        StringBuilder pageContent = new StringBuilder();
                        pageContent.append("=".repeat(80)).append("\n");
                        pageContent.append("PDF文件: ").append(pdfFile.getName()).append("\n");
                        pageContent.append("页码: ").append(pageIndex + 1).append("/").append(pageCount).append("\n");
                        pageContent.append("识别引擎: 阿里云 OCR\n");
                        pageContent.append("识别时间: ").append(new java.util.Date()).append("\n");
                        pageContent.append("文字块数量: ").append(pageResult.texts.size()).append("\n");
                        
                        // 计算平均置信度
                        double avgConfidence = pageResult.texts.stream()
                            .mapToDouble(t -> t.confidence)
                            .average()
                            .orElse(0.0);
                        pageContent.append(String.format("平均置信度: %.2f%%\n", avgConfidence * 100));
                        pageContent.append("=".repeat(80)).append("\n\n");
                        
                        // 输出每个文字块的详细信息
                        pageContent.append("文字块详细信息：\n");
                        pageContent.append("-".repeat(80)).append("\n");
                        for (int i = 0; i < pageResult.texts.size(); i++) {
                            OcrServiceClient.OcrTextItem item = pageResult.texts.get(i);
                            pageContent.append(String.format("\n[%d] 文字内容: %s\n", i + 1, item.text));
                            pageContent.append(String.format("    置信度: %.2f%%\n", item.confidence * 100));
                            if (item.bbox != null && item.bbox.size() == 4) {
                                pageContent.append("    位置坐标: ");
                                pageContent.append(String.format("[左上(%.0f,%.0f), ", item.bbox.get(0)[0], item.bbox.get(0)[1]));
                                pageContent.append(String.format("右上(%.0f,%.0f), ", item.bbox.get(1)[0], item.bbox.get(1)[1]));
                                pageContent.append(String.format("右下(%.0f,%.0f), ", item.bbox.get(2)[0], item.bbox.get(2)[1]));
                                pageContent.append(String.format("左下(%.0f,%.0f)]\n", item.bbox.get(3)[0], item.bbox.get(3)[1]));
                            }
                        }
                        pageContent.append("\n").append("-".repeat(80)).append("\n");
                        
                        // 输出完整的纯文本内容
                        pageContent.append("\n识别文本内容（纯文本）：\n");
                        pageContent.append("-".repeat(80)).append("\n");
                        pageContent.append(pageResult.fullText);
                        pageContent.append("\n").append("-".repeat(80)).append("\n");
                        pageContent.append("\n总字符数: ").append(pageResult.fullText.length()).append("\n");
                        
                        java.nio.file.Files.writeString(pageFile.toPath(), pageContent.toString(),
                            java.nio.charset.StandardCharsets.UTF_8);
                        
                        LOGGER.info("第 " + (pageIndex + 1) + " 页识别结果已保存: " + pageFile.getName());
                    } catch (Exception e) {
                        LOGGER.warning("保存第 " + (pageIndex + 1) + " 页识别结果失败: " + e.getMessage());
                    }
                    
                    // 添加到总文本
                    fullText.append(pageResult.fullText);
                    if (pageIndex < pageCount - 1) {
                        fullText.append("\n\n");
                    }
                    
                    // 更新文本块信息
                    for (OcrServiceClient.OcrTextItem item : pageResult.texts) {
                        item.page = pageIndex + 1;
                        result.texts.add(item);
                    }
                    totalTextCount += pageResult.textCount;
                } else {
                    LOGGER.warning("第 " + (pageIndex + 1) + " 页识别失败: " + pageResult.error);
                    
                    // 保存失败信息
                    String pageFileName = String.format("%s_page_%03d_%s_FAILED.txt", 
                        baseName, pageIndex + 1, timestamp);
                    File pageFile = new File(outputDir, pageFileName);
                    
                    try {
                        String errorContent = "识别失败\n错误信息: " + pageResult.error;
                        java.nio.file.Files.writeString(pageFile.toPath(), errorContent,
                            java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        // 忽略保存失败
                    }
                }
            }
            
            result.fullText = fullText.toString();
            result.textCount = totalTextCount;
            result.success = totalTextCount > 0;
            
            // 保存汇总文件
            String summaryFileName = String.format("%s_SUMMARY_%s.txt", baseName, timestamp);
            File summaryFile = new File(outputDir, summaryFileName);
            
            try {
                StringBuilder summary = new StringBuilder();
                summary.append("=".repeat(80)).append("\n");
                summary.append("阿里云 OCR 识别结果汇总\n");
                summary.append("=".repeat(80)).append("\n\n");
                summary.append("PDF文件: ").append(pdfFile.getName()).append("\n");
                summary.append("识别时间: ").append(new java.util.Date()).append("\n");
                summary.append("总页数: ").append(pageCount).append("\n");
                summary.append("识别成功页数: ").append(totalTextCount > 0 ? "已识别" : "失败").append("\n");
                summary.append("总字符数: ").append(fullText.length()).append("\n");
                summary.append("=".repeat(80)).append("\n\n");
                
                summary.append("每页识别结果文件列表:\n");
                summary.append("-".repeat(80)).append("\n");
                for (int i = 0; i < pageCount; i++) {
                    String pageFileName = String.format("%s_page_%03d_%s.txt", 
                        baseName, i + 1, timestamp);
                    summary.append(String.format("第 %d 页: %s\n", i + 1, pageFileName));
                }
                summary.append("-".repeat(80)).append("\n\n");
                
                summary.append("全部识别文本内容:\n");
                summary.append("=".repeat(80)).append("\n");
                summary.append(fullText.toString());
                summary.append("\n").append("=".repeat(80)).append("\n");
                
                java.nio.file.Files.writeString(summaryFile.toPath(), summary.toString(),
                    java.nio.charset.StandardCharsets.UTF_8);
                
                LOGGER.info("识别结果汇总已保存: " + summaryFile.getName());
                LOGGER.info("所有识别结果已保存到 output 文件夹");
                
            } catch (Exception e) {
                LOGGER.warning("保存汇总文件失败: " + e.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "阿里云 OCR 识别 PDF 失败", e);
            result.success = false;
            result.error = e.getMessage();
        }
        
        return result;
    }
    
    /**
     * 使用阿里云 OCR 识别图片
     */
    private static OcrServiceClient.OcrResult recognizeImageWithAliyun(BufferedImage image) {
        SimilarityConfig config = SimilarityConfig.getInstance();
        
        // 可选：去除红章（当前默认关闭，预留给将来的去红章功能）
        if (config.ocrRemoveSealEnabled) {
            LOGGER.info("执行红章去除...");
            image = SimpleSealRemover.removeSeal(image);
        }
        
        OcrServiceClient.OcrResult result = new OcrServiceClient.OcrResult();
        result.engine = "aliyun";
        
        try {
            // 创建阿里云客户端
            com.aliyun.ocr_api20210707.Client client = RecognizeCharacter.createClient();
            
            // 将图片转换为字节流
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            InputStream imageStream = new ByteArrayInputStream(imageBytes);
            
            // 调用阿里云 OCR API
            RecognizeAdvancedRequest request = new RecognizeAdvancedRequest();
            request.setBody(imageStream);
            
            // 开启自动方向检测（自动旋转图片到正确方向）
            request.setNeedRotate(true);
            
            // 开启分页排序（对多列文本进行智能排序）
            request.setNeedSortPage(true);
            
            // 输出单字识别结果（启用字符级精确标注）
            request.setOutputCharInfo(true);
            
            // 开启表格识别
            // request.setOutputTable(true);  // 如需要表格识别可取消注释
            
            RuntimeOptions runtime = new RuntimeOptions();
            RecognizeAdvancedResponse response = client.recognizeAdvancedWithOptions(request, runtime);
            
            // ========== 保存阿里云OCR原始响应数据 ==========
            if (response != null) {
                try {
                    // 创建output目录
                    File outputDir = new File("output");
                    if (!outputDir.exists()) {
                        outputDir.mkdirs();
                    }
                    
                    // 生成文件名：aliyun_ocr_raw_{时间戳}.json
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String rawFileName = "aliyun_ocr_raw_" + timestamp + ".json";
                    File rawFile = new File(outputDir, rawFileName);
                    
                    // 使用Gson将整个response对象转换为JSON并保存
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String responseJson = gson.toJson(response);
                    
                    java.nio.file.Files.writeString(rawFile.toPath(), responseJson, 
                        java.nio.charset.StandardCharsets.UTF_8);
                    
                    LOGGER.info("已保存阿里云OCR原始响应到: " + rawFile.getAbsolutePath());
                } catch (Exception e) {
                    LOGGER.warning("保存阿里云OCR原始响应失败: " + e.getMessage());
                }
            }
            // ================================================
            
            // 解析结果：getData()返回的是JSON字符串，不是纯文本！
            if (response != null && response.getBody() != null && response.getBody().getData() != null) {
                String dataJson = response.getBody().getData();
                
                try {
                    // 使用Gson解析JSON字符串
                    Gson gson = new Gson();
                    com.google.gson.JsonObject jsonObj = gson.fromJson(dataJson, com.google.gson.JsonObject.class);
                    
                    // 提取纯文本内容
                    String contentText = "";
                    if (jsonObj.has("content") && !jsonObj.get("content").isJsonNull()) {
                        contentText = jsonObj.get("content").getAsString();
                    }
                    
                    // 提取prism_wordsInfo数组 - 包含每个文字块的位置、置信度等详细信息
                    if (jsonObj.has("prism_wordsInfo") && jsonObj.get("prism_wordsInfo").isJsonArray()) {
                        com.google.gson.JsonArray wordsArray = jsonObj.getAsJsonArray("prism_wordsInfo");
                        
                        result.success = true;
                        result.fullText = contentText;
                        result.textCount = wordsArray.size();
                        result.pageCount = 1;
                        
                        // 解析每个文字块
                        for (int i = 0; i < wordsArray.size(); i++) {
                            com.google.gson.JsonObject wordObj = wordsArray.get(i).getAsJsonObject();
                            
                            OcrServiceClient.OcrTextItem item = new OcrServiceClient.OcrTextItem();
                            
                            // 文字内容
                            if (wordObj.has("word") && !wordObj.get("word").isJsonNull()) {
                                item.text = wordObj.get("word").getAsString();
                            }
                            
                            // 置信度 (prob字段，如99表示99%)
                            if (wordObj.has("prob") && !wordObj.get("prob").isJsonNull()) {
                                item.confidence = wordObj.get("prob").getAsDouble() / 100.0;  // 转换为0-1范围
                            } else {
                                item.confidence = 0.95;  // 默认值
                            }
                            
                            // 位置信息：pos是4个顶点坐标 [{x,y}, {x,y}, {x,y}, {x,y}]
                            if (wordObj.has("pos") && wordObj.get("pos").isJsonArray()) {
                                com.google.gson.JsonArray posArray = wordObj.getAsJsonArray("pos");
                                if (posArray.size() == 4) {
                                    // 使用bbox存储4个顶点：左上、右上、右下、左下
                                    item.bbox = new ArrayList<>();
                                    for (int j = 0; j < 4; j++) {
                                        com.google.gson.JsonObject point = posArray.get(j).getAsJsonObject();
                                        double[] vertex = new double[2];
                                        vertex[0] = point.get("x").getAsDouble();
                                        vertex[1] = point.get("y").getAsDouble();
                                        item.bbox.add(vertex);
                                    }
                                }
                            }
                            
                            // 解析字符级位置信息 (charInfo字段)
                            if (wordObj.has("charInfo") && wordObj.get("charInfo").isJsonArray()) {
                                com.google.gson.JsonArray charInfoArray = wordObj.getAsJsonArray("charInfo");
                                item.charBboxes = new ArrayList<>();
                                
                                for (int k = 0; k < charInfoArray.size(); k++) {
                                    com.google.gson.JsonObject charObj = charInfoArray.get(k).getAsJsonObject();
                                    
                                    // 阿里云charInfo格式: {x, y, w, h, word, prob}
                                    // x,y是左上角坐标，w是宽度，h是高度
                                    if (charObj.has("x") && charObj.has("y") && 
                                        charObj.has("w") && charObj.has("h")) {
                                        
                                        double x = charObj.get("x").getAsDouble();
                                        double y = charObj.get("y").getAsDouble();
                                        double w = charObj.get("w").getAsDouble();
                                        double h = charObj.get("h").getAsDouble();
                                        
                                        // 构造字符的bbox（4个顶点：左上、右上、右下、左下）
                                        List<double[]> charBbox = new ArrayList<>();
                                        charBbox.add(new double[]{x, y});           // 左上
                                        charBbox.add(new double[]{x + w, y});       // 右上
                                        charBbox.add(new double[]{x + w, y + h});   // 右下
                                        charBbox.add(new double[]{x, y + h});       // 左下
                                        
                                        item.charBboxes.add(charBbox);
                                    }
                                }
                                
                                LOGGER.fine(String.format("块#%d: 文字='%s', 字符bbox数=%d",
                                    i, item.text, item.charBboxes.size()));
                            }
                            
                            item.page = 1;
                            result.texts.add(item);
                        }
                        
                        LOGGER.info(String.format("成功解析阿里云OCR结果：%d个文字块，平均置信度：%.2f%%",
                                result.textCount, 
                                result.texts.stream().mapToDouble(t -> t.confidence).average().orElse(0) * 100));
                        
                    } else {
                        // 如果没有prism_wordsInfo，只使用纯文本内容
                        result.success = true;
                        result.fullText = contentText;
                        result.textCount = 1;
                        result.pageCount = 1;
                        
                        OcrServiceClient.OcrTextItem item = new OcrServiceClient.OcrTextItem();
                        item.text = contentText;
                        item.confidence = 0.95;
                        item.page = 1;
                        result.texts.add(item);
                        
                        LOGGER.warning("阿里云OCR返回结果中没有prism_wordsInfo，只使用纯文本");
                    }
                    
                } catch (com.google.gson.JsonSyntaxException e) {
                    LOGGER.log(Level.SEVERE, "解析阿里云OCR返回的JSON失败", e);
                    result.success = false;
                    result.error = "JSON解析失败: " + e.getMessage();
                }
            } else {
                result.success = false;
                result.error = "阿里云 OCR 返回无效响应";
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "阿里云 OCR 识别失败", e);
            result.success = false;
            result.error = e.getMessage();
        }
        
        return result;
    }
}
