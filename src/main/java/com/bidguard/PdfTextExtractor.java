package com.bidguard;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * PDF 文本提取工具：面向 Word 导出的 PDF，可直接提取可复制文本，无需再次 OCR。
 */
public class PdfTextExtractor {

    /**
     * 单页文本信息
     */
    public static class PdfPage {
        public final int pageNumber;
        public final String text;
        public final int charCount;

        public PdfPage(int pageNumber, String text) {
            this.pageNumber = pageNumber;
            this.text = text == null ? "" : text;
            this.charCount = this.text.length();
        }
    }

    /**
     * PDF 提取结果
     */
    public static class PdfExtractionResult {
        public Path sourcePath;
        public int pageCount;
        public String rawText;
        public String cleanedText;
        public final List<PdfPage> pages = new ArrayList<>();
        public final Map<String, String> metadata = new HashMap<>();
        public long processingTimeMillis;

        public boolean hasText() {
            String text = getText();
            return text != null && !text.isBlank();
        }

        public String getText() {
            return cleanedText != null ? cleanedText : rawText;
        }

        /**
         * 判断是否为扫描件 PDF（文本内容很少或没有）
         * 判断标准：
         * 1. 完全没有文本
         * 2. 每页平均字符数 < 50
         * 3. 文本密度（字符数/页数）过低
         */
        public boolean isScannedPdf() {
            String text = getText();
            
            // 完全没有文本
            if (text == null || text.isBlank()) {
                return true;
            }
            
            // 计算每页平均字符数
            int totalChars = text.length();
            if (pageCount > 0) {
                double avgCharsPerPage = totalChars / (double) pageCount;
                
                // 每页平均少于 50 个字符，很可能是扫描件
                if (avgCharsPerPage < 50) {
                    System.out.println(String.format(
                        "[PDF判断] 疑似扫描件：每页平均 %.1f 字符（< 50）", avgCharsPerPage));
                    return true;
                }
                
                // 每页平均少于 200 个字符，可能是扫描件或文本很少的 PDF
                if (avgCharsPerPage < 200) {
                    System.out.println(String.format(
                        "[PDF判断] 文本较少：每页平均 %.1f 字符", avgCharsPerPage));
                    
                    // 进一步检查：如果大部分是空白、符号或数字，也认为是扫描件
                    long letterCount = text.chars()
                        .filter(c -> Character.isLetter(c))
                        .count();
                    double letterRatio = letterCount / (double) totalChars;
                    
                    if (letterRatio < 0.3) {
                        System.out.println(String.format(
                            "[PDF判断] 疑似扫描件：字母占比仅 %.1f%% ", letterRatio * 100));
                        return true;
                    }
                }
            }
            
            System.out.println(String.format(
                "[PDF判断] 可能是 Word 转换的 PDF：总字符 %d，共 %d 页", totalChars, pageCount));
            return false;
        }
    }

    /**
     * 从 PDF 文件提取文字（按页聚合）。
     */
    public static PdfExtractionResult extract(Path pdfPath) throws IOException {
        Objects.requireNonNull(pdfPath, "pdfPath must not be null");

        if (!Files.exists(pdfPath)) {
            throw new IOException("PDF 文件不存在: " + pdfPath);
        }
        if (Files.isDirectory(pdfPath)) {
            throw new IOException("路径指向的是目录而非文件: " + pdfPath);
        }

        System.out.println("[PDF] 开始解析: " + pdfPath.toAbsolutePath());
        long start = System.currentTimeMillis();

        PdfExtractionResult result = new PdfExtractionResult();
        result.sourcePath = pdfPath;

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            result.pageCount = document.getNumberOfPages();
            captureMetadata(document.getDocumentInformation(), result.metadata);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            // 以下三项是追加的
            stripper.setAddMoreFormatting(false);
            stripper.setLineSeparator("\n");
            stripper.setWordSeparator(" ");

            StringBuilder aggregated = new StringBuilder();
            for (int page = 1; page <= result.pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = normalizeLineEndings(stripper.getText(document));
                result.pages.add(new PdfPage(page, pageText));

                if (!pageText.isBlank()) {
                    if (aggregated.length() > 0) {
                        aggregated.append("\n\n");
                    }
                    aggregated.append(pageText.trim());
                }
            }

            result.rawText = aggregated.toString();
            result.cleanedText = PdfTextCleaner.clean(result.rawText);
            result.processingTimeMillis = System.currentTimeMillis() - start;

            System.out.println("[PDF] 页面数: " + result.pageCount + ", 文本长度: " + result.getText().length());
            System.out.println("[PDF] 处理耗时: " + result.processingTimeMillis + " ms");
        }

        return result;
    }

    /**
     * 将提取结果保存为 .txt 文件
     */
    public static void saveAsText(PdfExtractionResult result, Path outputPath) throws IOException {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            String textToWrite = result.getText();
            writer.write(textToWrite == null ? "" : textToWrite);
        }
        System.out.println("[PDF] 文本已保存: " + outputPath.toAbsolutePath());
    }

    private static void captureMetadata(PDDocumentInformation info, Map<String, String> metadata) {
        if (info == null) {
            return;
        }
        putIfPresent(metadata, "title", info.getTitle());
        putIfPresent(metadata, "author", info.getAuthor());
        putIfPresent(metadata, "subject", info.getSubject());
        putIfPresent(metadata, "keywords", info.getKeywords());
        putIfPresent(metadata, "producer", info.getProducer());
        putIfPresent(metadata, "creator", info.getCreator());

        if (info.getCreationDate() != null) {
            metadata.put("created", formatDate(info.getCreationDate().getTime()));
        }
        if (info.getModificationDate() != null) {
            metadata.put("modified", formatDate(info.getModificationDate().getTime()));
        }

        if (metadata.getOrDefault("producer", "").toLowerCase(Locale.ROOT).contains("microsoft")) {
            metadata.put("sourceHint", "检测到 Word/Office 导出痕迹");
        }
    }

    private static void putIfPresent(Map<String, String> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value.trim());
        }
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(date);
    }

    private static String normalizeLineEndings(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * 弹出窗口，显示原始文本和清洗后文本的预览。
     */
    public static void showPreviewUI(PdfExtractionResult result) {
        JFrame frame = new JFrame("PDF 文本预览");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(900, 700);
        frame.setLayout(new GridLayout(1, 2));

        JTextArea rawTextArea = new JTextArea(result.rawText == null ? "" : result.rawText);
        rawTextArea.setLineWrap(true);
        rawTextArea.setWrapStyleWord(true);
        rawTextArea.setEditable(false);
        JScrollPane rawScroll = new JScrollPane(rawTextArea);
        rawScroll.setBorder(BorderFactory.createTitledBorder("原始文本 (rawText)"));

        JTextArea cleanedTextArea = new JTextArea(result.cleanedText == null ? "" : result.cleanedText);
        cleanedTextArea.setLineWrap(true);
        cleanedTextArea.setWrapStyleWord(true);
        cleanedTextArea.setEditable(false);
        JScrollPane cleanedScroll = new JScrollPane(cleanedTextArea);
        cleanedScroll.setBorder(BorderFactory.createTitledBorder("清洗后文本 (cleanedText)"));

        frame.add(rawScroll);
        frame.add(cleanedScroll);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // 简单的命令行演示： java com.bidguard.PdfTextExtractor sample.pdf output.txt
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("用法: java com.bidguard.PdfTextExtractor <PDF路径> [导出TXT路径] [--preview]");
            return;
        }

        Path pdfPath = Path.of(args[0]);
        PdfExtractionResult result = extract(pdfPath);
        System.out.println("=== PDF 文本提取完成 ===");
        System.out.println("页面数: " + result.pageCount);
        System.out.println("是否含文本: " + result.hasText());
        // System.out.println("前200字符: " + preview(result.getText(), 200));

        if (args.length > 1 && !args[1].equalsIgnoreCase("--preview")) {
            saveAsText(result, Path.of(args[1]));
        }
        // 如果参数中包含--preview，则弹出UI
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--preview")) {
                SwingUtilities.invokeLater(() -> showPreviewUI(result));
                break;
            }
        }
    }

    private static String preview(String text, int maxLen) {
        if (text == null) {
            return "<null>";
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return "<empty>";
        }
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen) + "...";
    }
}
