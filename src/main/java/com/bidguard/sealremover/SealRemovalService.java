package com.bidguard.sealremover;

import com.bidguard.image.SimpleSealRemover;
import com.bidguard.ocr.OcrServiceFactory;
import com.bidguard.ocr.OcrServiceClient;
import com.bidguard.pdf.PdfPageRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 去红章服务类 - 核心入口，串联"PDF渲染 → 去章算法 → 存PNG → 生成HTML报告 → 送OCR"全流程。
 *
 * <p>设计原则：
 * <ul>
 *   <li>此类本身不依赖 UI，所有状态通过回调 {@code logger} 上报，便于将来合并到主流程。</li>
 *   <li>{@link #processPages} 返回 {@link RemovalResult}，其中包含每页原图/去章图的
 *       {@link BufferedImage}，主流程可直接替换 PDF 渲染步骤后紧接调用 OCR。</li>
 *   <li>中间文件：每页保存原始 PNG 和去章后 PNG（可人眼对比），并生成一份 HTML 报告
 *       (原图 / 去章图并排展示)，方便调试和人工审核。</li>
 * </ul>
 *
 * <p>将来合并到主流程只需：
 * <pre>
 *   RemovalResult r = SealRemovalService.processPages(pdfFile, algorithm, 200, log);
 *   List&lt;BufferedImage&gt; cleaned = r.getProcessedImages();
 *   // 送 OCR...
 * </pre>
 */
public class SealRemovalService {

    private static final Logger LOGGER = Logger.getLogger(SealRemovalService.class.getName());

    // -------------------------------------------------------------------------
    // 算法选项
    // -------------------------------------------------------------------------

    /**
     * 去章算法选项。
     * <ul>
     *   <li>{@link #DOCUMENT}  - {@link DocumentSealRemover}：HSV色彩空间 + 形态学处理，推荐扫描文件</li>
     *   <li>{@link #PRECISE}   - {@link PreciseSealRemover}：先定位印章区域再去除，精确度高</li>
     *   <li>{@link #SIMPLE}    - {@link SimpleSealRemover}：轻量级红色像素直接替换，速度最快</li>
     * </ul>
     */
    public enum Algorithm {
        RED_CHANNEL("红色通道扣除（★推荐，保留章下文字）"),
        LAB("LAB色度压制（实验性）"),
        DOCUMENT("HSV逐像素去红"),
        PRECISE("精确定位去除"),
        SIMPLE("简单红色像素替换（最快）");

        public final String displayName;

        Algorithm(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // -------------------------------------------------------------------------
    // 结果数据结构
    // -------------------------------------------------------------------------

    /** 单页处理结果 */
    public static class PageResult {
        /** 页码（1-based） */
        public final int pageNumber;
        /** 原始渲染图（未去章） */
        public final BufferedImage originalImage;
        /** 去章后图像 */
        public final BufferedImage processedImage;
        /** 保存的原始 PNG 文件（可能为 null，若 saveIntermediatePng=false） */
        public File savedOriginalPng;
        /** 保存的去章 PNG 文件（可能为 null） */
        public File savedProcessedPng;
        /** OCR 识别文本（送 OCR 后填充） */
        public String ocrText = "";
        /** 本页去章耗时（ms） */
        public long processingMs;
        /** 算法实际使用 */
        public Algorithm algorithmUsed;

        public PageResult(int pageNumber, BufferedImage originalImage,
                         BufferedImage processedImage, Algorithm algo, long ms) {
            this.pageNumber = pageNumber;
            this.originalImage = originalImage;
            this.processedImage = processedImage;
            this.algorithmUsed = algo;
            this.processingMs = ms;
        }
    }

    /** 整体处理结果 */
    public static class RemovalResult {
        /** 所有页的处理结果 */
        public final List<PageResult> pages;
        /** 输出目录 */
        public final File outputDir;
        /** 生成的 HTML 预览报告 */
        public File htmlReport;
        /** 合并的 OCR 全文（调用 runOcr 后填充） */
        public String mergedOcrText = "";
        /** 总耗时（ms） */
        public long totalMs;

        public RemovalResult(List<PageResult> pages, File outputDir) {
            this.pages = pages;
            this.outputDir = outputDir;
        }

        /** 便捷方法：取出所有去章后的图像，方便主流程直接送 OCR */
        public List<BufferedImage> getProcessedImages() {
            List<BufferedImage> list = new ArrayList<>();
            for (PageResult p : pages) {
                list.add(p.processedImage);
            }
            return list;
        }
    }

    // -------------------------------------------------------------------------
    // 主流程
    // -------------------------------------------------------------------------

    /**
     * 主入口：渲染 PDF 每页 → 去章 → 保存 PNG → 生成 HTML 报告。
     *
     * @param pdfFile    输入 PDF（扫描件）
     * @param algorithm  去章算法
     * @param dpi        渲染分辨率（推荐 200~300）
     * @param logger     进度回调（在调用线程中执行，GUI 请用 SwingWorker#publish 包装）
     * @return           处理结果，包含每页图像及中间文件路径
     */
    public static RemovalResult processPages(File pdfFile, Algorithm algorithm,
                                             int dpi, Consumer<String> logger) throws IOException {
        long start = System.currentTimeMillis();
        log(logger, "===== 开始去红章处理 =====");
        log(logger, "文件: " + pdfFile.getName());
        log(logger, "算法: " + algorithm.displayName);
        log(logger, "渲染DPI: " + dpi);

        // 1. 渲染全部页
        log(logger, "\n[1/4] 渲染PDF页面...");
        Path pdfPath = pdfFile.toPath();
        List<PdfPageRenderer.PageImage> rawPages = PdfPageRenderer.renderAllPages(pdfPath, dpi);
        log(logger, "  共 " + rawPages.size() + " 页");

        // 2. 创建输出目录
        File outputDir = prepareOutputDir(pdfFile);
        log(logger, "\n[2/4] 输出目录: " + outputDir.getAbsolutePath());

        // 3. 逐页去章
        log(logger, "\n[3/4] 逐页去章...");
        List<PageResult> results = new ArrayList<>();
        for (PdfPageRenderer.PageImage pi : rawPages) {
            log(logger, "  处理第 " + pi.pageNumber + "/" + rawPages.size() + " 页...");
            long t0 = System.currentTimeMillis();

            BufferedImage processed = applyAlgorithm(pi.image, algorithm);
            long elapsed = System.currentTimeMillis() - t0;

            PageResult pr = new PageResult(pi.pageNumber, pi.image, processed, algorithm, elapsed);

            // 保存 PNG 中间文件
            pr.savedOriginalPng = savePng(pi.image,
                new File(outputDir, String.format("page_%03d_original.png", pi.pageNumber)));
            pr.savedProcessedPng = savePng(processed,
                new File(outputDir, String.format("page_%03d_no_seal.png", pi.pageNumber)));

            results.add(pr);
            log(logger, String.format("    ✓ 第%d页完成 (%dms)，已保存 %s",
                pi.pageNumber, elapsed, pr.savedProcessedPng.getName()));
        }

        // 4. 生成 HTML 报告
        log(logger, "\n[4/4] 生成HTML预览报告...");
        RemovalResult result = new RemovalResult(results, outputDir);
        result.htmlReport = generateHtmlReport(result, pdfFile, algorithm);
        result.totalMs = System.currentTimeMillis() - start;

        log(logger, "\n===== 去章完成 =====");
        log(logger, "耗时: " + result.totalMs + " ms");
        log(logger, "HTML报告: " + result.htmlReport.getName());
        log(logger, "输出目录: " + outputDir.getAbsolutePath());

        return result;
    }

    /**
     * 在 {@link #processPages} 的结果上执行阿里云 OCR（逐页识别去章后的图像）。
     *
     * @param result  {@link #processPages} 的返回值
     * @param logger  进度回调
     * @return        所有页的合并识别文本
     */
    public static String runOcr(RemovalResult result, Consumer<String> logger) throws IOException {
        log(logger, "\n===== 开始OCR识别（去章后）=====");
        StringBuilder sb = new StringBuilder();

        for (PageResult pr : result.pages) {
            log(logger, "  OCR 第 " + pr.pageNumber + "/" + result.pages.size() + " 页...");
            try {
                OcrServiceClient.OcrResult ocrResult =
                    OcrServiceFactory.recognizeImage(pr.processedImage);
                pr.ocrText = ocrResult != null ? ocrResult.fullText : "";
                sb.append("===== 第").append(pr.pageNumber).append("页 =====\n");
                sb.append(pr.ocrText).append("\n\n");
                log(logger, "    ✓ 第" + pr.pageNumber + "页 OCR完成，"
                    + (ocrResult != null ? ocrResult.textCount : 0) + " 个字块");
            } catch (Exception e) {
                log(logger, "    ✗ 第" + pr.pageNumber + "页 OCR失败: " + e.getMessage());
                pr.ocrText = "[OCR失败: " + e.getMessage() + "]";
                sb.append("===== 第").append(pr.pageNumber).append("页 =====\n")
                  .append(pr.ocrText).append("\n\n");
            }
        }

        result.mergedOcrText = sb.toString();
        log(logger, "===== OCR完成，共 " + result.pages.size() + " 页 =====");
        return result.mergedOcrText;
    }

    // -------------------------------------------------------------------------
    // 内部：算法分发
    // -------------------------------------------------------------------------

    private static BufferedImage applyAlgorithm(BufferedImage image, Algorithm algo) {
        switch (algo) {
            case RED_CHANNEL:
                return RedChannelSealRemover.removeSeal(image);
            case LAB:
                return LabSealRemover.removeSeal(image);
            case DOCUMENT:
                return DocumentSealRemover.removeSeal(image);
            case PRECISE:
                return PreciseSealRemover.removeSeal(image);
            case SIMPLE:
            default:
                return SimpleSealRemover.removeSeal(image);
        }
    }

    // -------------------------------------------------------------------------
    // 内部：文件操作
    // -------------------------------------------------------------------------

    private static File prepareOutputDir(File pdfFile) {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String baseName = pdfFile.getName().replaceAll("\\.pdf$", "").replaceAll("[^\\w\\-]", "_");
        File dir = new File("output", "seal_removal_" + baseName + "_" + ts);
        dir.mkdirs();
        return dir;
    }

    private static File savePng(BufferedImage img, File dest) {
        try {
            ImageIO.write(img, "PNG", dest);
        } catch (IOException e) {
            LOGGER.warning("保存PNG失败: " + dest.getPath() + " - " + e.getMessage());
        }
        return dest;
    }

    // -------------------------------------------------------------------------
    // 内部：HTML 报告生成（原图 / 去章图并排展示）
    // -------------------------------------------------------------------------

    private static File generateHtmlReport(RemovalResult result, File pdfFile,
                                           Algorithm algorithm) throws IOException {
        File htmlFile = new File(result.outputDir,
            pdfFile.getName().replaceAll("\\.pdf$", "") + "_seal_removal_report.html");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
            .append("<title>去红章报告 - ").append(pdfFile.getName()).append("</title>")
            .append("<style>")
            .append("body{font-family:sans-serif;background:#f5f5f5;margin:0;padding:20px}")
            .append("h1{color:#333;font-size:20px}")
            .append(".meta{background:#fff;border-radius:6px;padding:12px 16px;")
            .append("margin-bottom:20px;border:1px solid #ddd;font-size:13px;color:#555}")
            .append(".meta span{font-weight:bold;color:#222}")
            .append(".page-block{background:#fff;border-radius:6px;padding:12px;")
            .append("margin-bottom:24px;border:1px solid #ddd}")
            .append(".page-title{font-size:15px;font-weight:bold;color:#444;margin-bottom:10px}")
            .append(".img-row{display:flex;gap:16px;flex-wrap:wrap}")
            .append(".img-col{flex:1;min-width:300px;text-align:center}")
            .append(".img-col img{max-width:100%;border:1px solid #ccc;border-radius:4px}")
            .append(".label{font-size:12px;color:#888;margin-top:6px}")
            .append(".orig-label{color:#c0392b}.proc-label{color:#27ae60}")
            .append(".stat{font-size:12px;color:#999;margin-top:4px}")
            .append("</style></head><body>")
            .append("<h1>📄 去红章处理报告</h1>")
            .append("<div class=\"meta\">")
            .append("<div>源文件：<span>").append(escapeHtml(pdfFile.getName())).append("</span></div>")
            .append("<div>算法：<span>").append(escapeHtml(algorithm.displayName)).append("</span></div>");
        // 插入主要参数说明
        if (algorithm == Algorithm.DOCUMENT) {
            html.append("<div style='font-size:12px;color:#888;margin:2px 0 6px 0'>主要参数：色相[0-30,300-360]°，饱和度≥0.12，明度≥0.15，仅R>G且R>B像素判为红章</div>");
        } else if (algorithm == Algorithm.PRECISE) {
            html.append("<div style='font-size:12px;color:#888;margin:2px 0 6px 0'>主要参数：区域连通面积≥400像素，红色判定同DOCUMENT</div>");
        } else if (algorithm == Algorithm.SIMPLE) {
            html.append("<div style='font-size:12px;color:#888;margin:2px 0 6px 0'>主要参数：红色像素直接替换，红色判定同DOCUMENT</div>");
        } else if (algorithm == Algorithm.LAB) {
            html.append("<div style='font-size:12px;color:#888;margin:2px 0 6px 0'>主要参数：LAB色度压制，A通道动态阈值，L>30有效，输出灰度</div>");
        }
        html.append("<div>总页数：<span>").append(result.pages.size()).append("</span></div>")
            .append("<div>处理时间：<span>")
            .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
            .append("</span></div>")
            .append("<div>耗时：<span>").append(result.totalMs).append(" ms</span></div>")
            .append("</div>");

        for (PageResult pr : result.pages) {
            html.append("<div class=\"page-block\">")
                .append("<div class=\"page-title\">第 ").append(pr.pageNumber).append(" 页")
                .append("<span class=\"stat\"> — 去章耗时 ").append(pr.processingMs).append("ms</span>")
                .append("</div>")
                .append("<div class=\"img-row\">");

            // 原图
            html.append("<div class=\"img-col\">")
                .append("<img src=\"").append(pr.savedOriginalPng.getName()).append("\" ")
                .append("alt=\"原图 第").append(pr.pageNumber).append("页\">")
                .append("<div class=\"label orig-label\">🔴 原图（含红章）</div>")
                .append("</div>");

            // 去章后
            html.append("<div class=\"img-col\">")
                .append("<img src=\"").append(pr.savedProcessedPng.getName()).append("\" ")
                .append("alt=\"去章后 第").append(pr.pageNumber).append("页\">")
                .append("<div class=\"label proc-label\">✅ 去章后</div>")
                .append("</div>");

            html.append("</div></div>"); // img-row / page-block
        }

        html.append("</body></html>");

        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(htmlFile), StandardCharsets.UTF_8)) {
            w.write(html.toString());
        }

        return htmlFile;
    }

    // -------------------------------------------------------------------------
    // 工具
    // -------------------------------------------------------------------------

    private static void log(Consumer<String> logger, String msg) {
        if (logger != null) logger.accept(msg);
        LOGGER.info(msg);
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 将 BufferedImage 编码为 base64 data URI（小图预览可内联，大图用文件路径更稳妥） */
    @SuppressWarnings("unused")
    static String toBase64DataUri(BufferedImage img) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }
}
