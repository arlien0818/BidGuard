package com.bidguard;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * 表示一个 PDF 任务，包含文件和可缓存的 OCR 结果。
 */
public class PdfTask {
    private static final Logger LOGGER = Logger.getLogger(PdfTask.class.getName());

    private final File file;
    // 懒加载并缓存 OCR 结果（线程安全）
    private volatile OcrServiceClient.OcrResult cachedOcrResult;

    public PdfTask(File file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public File getFile() {
        return file;
    }

    /**
     * 获取已缓存的 OCR 结果；若为空则执行 OCR 并缓存。
     */
    public OcrServiceClient.OcrResult ensureOcr() throws Exception {
        if (cachedOcrResult == null) {
            synchronized (this) {
                if (cachedOcrResult == null) {
                    LOGGER.info(() -> "执行 OCR: " + file.getName());
                    cachedOcrResult = OcrServiceFactory.recognizePdf(file);
                    if (cachedOcrResult == null) {
                        throw new IllegalStateException("OCR 返回空结果: " + file.getAbsolutePath());
                    }
                }
            }
        }
        return cachedOcrResult;
    }

    @Override
    public String toString() {
        return file != null ? file.getName() : "null";
    }
}
