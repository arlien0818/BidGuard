package com.bidguard;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 页面渲染器：将 PDF 页面转换为 BufferedImage 图片
 * 用于扫描件 PDF 的 OCR 识别流程
 */
public class PdfPageRenderer {

    /**
     * 页面图片信息
     */
    public static class PageImage {
        public final int pageNumber;
        public final BufferedImage image;
        public final int width;
        public final int height;

        public PageImage(int pageNumber, BufferedImage image) {
            this.pageNumber = pageNumber;
            this.image = image;
            this.width = image.getWidth();
            this.height = image.getHeight();
        }
    }

    /**
     * 将 PDF 的指定页面渲染为图片
     * 
     * @param pdfPath PDF 文件路径
     * @param pageNumber 页码（从 1 开始）
     * @param dpi 分辨率，推荐 300 用于 OCR
     * @return 页面图片
     * @throws IOException 文件读取或渲染失败
     */
    public static BufferedImage renderPage(Path pdfPath, int pageNumber, int dpi) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
                throw new IllegalArgumentException(
                    String.format("页码超出范围: %d (PDF总页数: %d)", pageNumber, document.getNumberOfPages())
                );
            }

            PDFRenderer renderer = new PDFRenderer(document);
            int pageIndex = pageNumber - 1; // PDFBox 使用 0-based index
            
            System.out.println(String.format("[PDF渲染] 第 %d 页, DPI=%d", pageNumber, dpi));
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
            System.out.println(String.format("[PDF渲染] 完成，图片尺寸: %dx%d", image.getWidth(), image.getHeight()));
            
            return image;
        }
    }

    /**
     * 将 PDF 文档渲染为图片
     * 
     * @param document 已加载的 PDDocument
     * @param pageNumber 页码（从 1 开始）
     * @param dpi 分辨率
     * @return 页面图片
     * @throws IOException 渲染失败
     */
    public static BufferedImage renderPage(PDDocument document, int pageNumber, int dpi) throws IOException {
        if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
            throw new IllegalArgumentException(
                String.format("页码超出范围: %d (PDF总页数: %d)", pageNumber, document.getNumberOfPages())
            );
        }

        PDFRenderer renderer = new PDFRenderer(document);
        int pageIndex = pageNumber - 1;
        
        return renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
    }

    /**
     * 批量渲染 PDF 的所有页面
     * 
     * @param pdfPath PDF 文件路径
     * @param dpi 分辨率，推荐 300 用于 OCR
     * @return 所有页面的图片列表
     * @throws IOException 文件读取或渲染失败
     */
    public static List<PageImage> renderAllPages(Path pdfPath, int dpi) throws IOException {
        List<PageImage> pages = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            
            System.out.println(String.format("[PDF渲染] 开始渲染 %d 页，DPI=%d", totalPages, dpi));
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < totalPages; i++) {
                int pageNumber = i + 1;
                BufferedImage image = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                pages.add(new PageImage(pageNumber, image));
                
                if (pageNumber % 10 == 0 || pageNumber == totalPages) {
                    System.out.println(String.format("[PDF渲染] 进度: %d/%d 页", pageNumber, totalPages));
                }
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println(String.format("[PDF渲染] 完成，耗时 %d ms (平均 %.1f ms/页)", 
                elapsed, elapsed / (double) totalPages));
        }
        
        return pages;
    }

    /**
     * 批量渲染 PDF 的指定页面范围
     * 
     * @param pdfPath PDF 文件路径
     * @param startPage 起始页码（从 1 开始，包含）
     * @param endPage 结束页码（从 1 开始，包含）
     * @param dpi 分辨率
     * @return 指定范围的页面图片列表
     * @throws IOException 文件读取或渲染失败
     */
    public static List<PageImage> renderPageRange(Path pdfPath, int startPage, int endPage, int dpi) 
            throws IOException {
        List<PageImage> pages = new ArrayList<>();
        
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            int totalPages = document.getNumberOfPages();
            
            if (startPage < 1 || startPage > totalPages) {
                throw new IllegalArgumentException("起始页码超出范围: " + startPage);
            }
            if (endPage < startPage || endPage > totalPages) {
                throw new IllegalArgumentException("结束页码无效: " + endPage);
            }
            
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = endPage - startPage + 1;
            
            System.out.println(String.format("[PDF渲染] 渲染第 %d-%d 页（共 %d 页），DPI=%d", 
                startPage, endPage, pageCount, dpi));
            
            for (int pageNum = startPage; pageNum <= endPage; pageNum++) {
                int pageIndex = pageNum - 1;
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
                pages.add(new PageImage(pageNum, image));
            }
            
            System.out.println(String.format("[PDF渲染] 完成 %d 页", pageCount));
        }
        
        return pages;
    }
}
