package com.bidguard.core;

import com.bidguard.config.SimilarityConfig;
import com.bidguard.ocr.OcrServiceClient;
import com.bidguard.pdf.PdfAnnotator;
import com.bidguard.pdf.PdfTask;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 批量查重运行器：串联生成 pairs、OCR（缓存）、detectDuplicates、保存 JSON、标注 PDF 的完整链路。
 *
 * 该类不依赖 UI，可在后台任务或命令行中调用。
 */
public class BatchDuplicateRunner {
    private static final Logger LOGGER = Logger.getLogger(BatchDuplicateRunner.class.getName());

    /**
     * 运行批量查重并标注的唯一入口。
     * @param pdfTasks 要处理的 PDF 任务列表（不得为 null）
     * @return map: FilePair -> 生成的 JSON 结果文件
     */
    public static Map<FilePair, File> runBatchDuplicateCheck(List<PdfTask> pdfTasks) {
        Objects.requireNonNull(pdfTasks, "pdfTasks");

        Map<FilePair, File> resultMap = new LinkedHashMap<>();

        if (pdfTasks.size() < 2) {
            LOGGER.warning("至少需要 2 个 PDF 才能执行批量查重");
            return resultMap;
        }

        // 先生成 pairs（利用现有的 PairGenerator）
        List<File> files = new ArrayList<>();
        for (PdfTask t : pdfTasks) files.add(t.getFile());

        List<FilePair> pairs = PairGenerator.generatePairs(files);

        LOGGER.info(() -> String.format("开始处理 %d 个配对", pairs.size()));

        // 做一个局部缓存，以便快速从 File -> PdfTask
        Map<File, PdfTask> taskByFile = new HashMap<>();
        for (PdfTask t : pdfTasks) taskByFile.put(t.getFile(), t);

        for (FilePair pair : pairs) {
            File a = pair.getFileA();
            File b = pair.getFileB();

            PdfTask taskA = taskByFile.get(a);
            PdfTask taskB = taskByFile.get(b);

            if (taskA == null || taskB == null) {
                LOGGER.warning(() -> "跳过未知文件配对: " + pair.toString());
                continue;
            }

            try {
                // OCR（确保缓存）——两次 ensureOcr 调用对同一文件只执行一次
                LOGGER.info(() -> "确保 OCR: " + a.getName());
                OcrServiceClient.OcrResult ocrA = taskA.ensureOcr();

                LOGGER.info(() -> "确保 OCR: " + b.getName());
                OcrServiceClient.OcrResult ocrB = taskB.ensureOcr();

                // 调用 detectDuplicates（只负责算法）
                LOGGER.info(() -> "执行 detectDuplicates: " + pair.toString());
                OcrDuplicateDetector.DuplicateDetectionResult detection =
                    OcrDuplicateDetector.detectDuplicates(
                        ocrA, ocrB, a.getName(), b.getName(), SimilarityConfig.getInstance().substringMinLength
                    );

                // 将结果保存为 JSON（中间产物，可复用）
                LOGGER.info(() -> "保存检测结果为 JSON: " + pair.toString());
                File json = OcrDuplicateDetector.saveResultToJson(detection, a.getName(), b.getName());

                // 标注（渲染）——解耦通过 JSON 文件
                LOGGER.info(() -> "执行 PDF 标注: " + pair.toString());
                try {
                    PdfAnnotator.annotatePdfs(json, a, b);
                } catch (Exception annEx) {
                    // 标注失败不应中断整个批处理，记录并继续
                    LOGGER.log(Level.SEVERE, "标注失败: " + pair.toString(), annEx);
                }

                resultMap.put(pair, json);

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "处理配对失败: " + pair.toString(), ex);
            }
        }

        LOGGER.info(() -> String.format("批量处理完成，生成 JSON %d 个", resultMap.size()));
        return resultMap;
    }
}
