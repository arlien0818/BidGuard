package com.bidguard;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BidCheckerGUI extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(BidCheckerGUI.class.getName());

    // 批量文件对比组件
    private DefaultListModel<String> fileListModel;
    private JList<String> fileList;
    private JButton selectFilesButton;
    private JButton readAllFilesButton;
    private JButton compareButton;
    private JButton generateAnnotationDataButton;
    private JButton annotatePdfButton;
    private JButton testPairGeneratorButton;
    private JButton batchRunButton;
    private JTextArea resultArea;
    private JTextArea previewArea;
    
    // 文件数据
    private List<File> selectedFiles = new ArrayList<>();
    private Map<File, String> fileTexts = new HashMap<>();
    private File latestDetectionJsonFile;

    // 公章去除组件
    // private JTextField imageFileField;
    // private JButton selectImageButton;
    // private JButton removeSealButton;
    // private JButton previewButton;
    // private JTextArea sealResultArea;
    // private File selectedImageFile;

    // OCR 验证组件
    // private JTextField ocrImageField;
    // private JButton selectOcrImageButton;
    // private JButton ocrPreviewButton;
    // private JButton runOcrButton;
    // private JTextArea ocrResultArea;
    // private File selectedOcrFile;

    private JProgressBar progressBar;
    private JTabbedPane tabbedPane;

    // 功能: 初始化主界面组件并设置默认文件
    public BidCheckerGUI() {
        setTitle("BidGuard 智能文档处理工具");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane();

        JPanel comparePanel = createComparePanel();
        tabbedPane.addTab("文件对比", comparePanel);


        // ===================== 文件查重相关UI和逻辑已注释 =====================
        // JPanel duplicatePanel = createDuplicateDetectionPanel();
        // tabbedPane.addTab("文件查重", duplicatePanel);
        // ===============================================================

        add(tabbedPane, BorderLayout.CENTER);

        JPanel statusPanel = createStatusPanel();
        add(statusPanel, BorderLayout.SOUTH);
    }

    // 功能: 构建文件对比选项卡界面布局
    private JPanel createComparePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // === 左侧：文件选择和列表 ===
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(300, 0));
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new GridLayout(6, 1, 5, 5));
        
        selectFilesButton = new JButton("选择标书文件（≥2个）");
        selectFilesButton.addActionListener(e -> selectMultipleFiles());
        
        readAllFilesButton = new JButton("读取所选文件");
        readAllFilesButton.setEnabled(false);
        readAllFilesButton.addActionListener(e -> readAllFiles());
        
        compareButton = new JButton("一键对比");
        compareButton.setEnabled(false);
        compareButton.addActionListener(e -> compareFiles());
        
        generateAnnotationDataButton = new JButton("生成查重标注数据");
        generateAnnotationDataButton.setEnabled(false);
        generateAnnotationDataButton.addActionListener(e -> generateDuplicateAnnotationData());
        
        testPairGeneratorButton = new JButton("测试配对生成器");
        testPairGeneratorButton.setToolTipText("测试从多个文件生成所有可能的配对组合");
        testPairGeneratorButton.addActionListener(e -> testPairGenerator());

        batchRunButton = new JButton("批量执行查重并标注");
        batchRunButton.setToolTipText("对所有生成的配对依次执行 OCR/查重/保存/标注（会缓存 OCR）");
        batchRunButton.setEnabled(false);
        batchRunButton.addActionListener(e -> runBatchDuplicateAndAnnotate());
        
        buttonPanel.add(selectFilesButton);
        buttonPanel.add(readAllFilesButton);
        buttonPanel.add(compareButton);
        buttonPanel.add(generateAnnotationDataButton);
        buttonPanel.add(testPairGeneratorButton);
        buttonPanel.add(batchRunButton);
        
        leftPanel.add(buttonPanel, BorderLayout.NORTH);
        
        // 文件列表
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane fileListScroll = new JScrollPane(fileList);
        fileListScroll.setBorder(BorderFactory.createTitledBorder("已选文件"));
        
        leftPanel.add(fileListScroll, BorderLayout.CENTER);

        // === 右侧：预览区 ===
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        
        previewArea = createPreviewTextArea();
        JScrollPane previewScroll = new JScrollPane(previewArea);
        previewScroll.setBorder(BorderFactory.createTitledBorder("文本预览"));

        
        rightPanel.add(previewScroll, BorderLayout.CENTER);
        
        // === 底部：结果区和标注按钮 ===
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        annotatePdfButton = new JButton("一键标注PDF");
        annotatePdfButton.setEnabled(false);
        annotatePdfButton.addActionListener(e -> annotatePdfs());
        annotatePdfButton.setPreferredSize(new Dimension(150, 30));
        annotatePdfButton.setToolTipText("先生成查重标注数据，检查无误后再执行标注");
        
        JPanel annotatePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        annotatePanel.add(annotatePdfButton);
        
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        JScrollPane resultScrollPane = new JScrollPane(resultArea);
        resultScrollPane.setBorder(BorderFactory.createTitledBorder("对比结果"));
        resultScrollPane.setPreferredSize(new Dimension(0, 200));
        
        bottomPanel.add(annotatePanel, BorderLayout.NORTH);
        bottomPanel.add(resultScrollPane, BorderLayout.CENTER);
        
        rightPanel.add(bottomPanel, BorderLayout.SOUTH);

        // === 整体布局：左右分割 ===
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            leftPanel, rightPanel);
        mainSplitPane.setResizeWeight(0.3);
        mainSplitPane.setContinuousLayout(true);

        panel.add(mainSplitPane, BorderLayout.CENTER);

        return panel;
    }

    // 功能: 创建底部状态栏及进度条
    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createLoweredBevelBorder());

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("就绪");

        JLabel versionLabel = new JLabel("BidGuard v2.07 - 批量文件对比");

        statusPanel.add(progressBar, BorderLayout.CENTER);
        statusPanel.add(versionLabel, BorderLayout.EAST);

        return statusPanel;
    }

    // 功能: 生成只读多行文本预览区域
    private JTextArea createPreviewTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }


    // 功能: 更新检测按钮状态

    /*
    private void updateDetectDuplicateButtonState() {
        boolean enable = duplicateFile1 != null;
        if (crossDocumentRadio.isSelected()) {
            enable = enable && duplicateFile2 != null;
        }
        detectDuplicateButton.setEnabled(enable);
    }
    */


    // 功能: 执行重复内容检测

    /*
    private void detectDuplicates() {
        // ...existing code...
    }
    */

    // 功能: 单文档内部查重

    /*
    private void detectInternalDuplicates() {
        // ...existing code...
    }
    */

    

    // 功能: 判定运行环境返回默认根目录
    private String getDefaultDirectory() {
        try {
            String classPath = BidCheckerGUI.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            File classFile = new File(classPath);
            if (classFile.isFile() && classFile.getName().toLowerCase().endsWith(".jar")) {
                return classFile.getParent();
            }
            return System.getProperty("user.dir");
        } catch (Exception e) {
            return System.getProperty("user.dir");
        }
    }

    // 功能: 获取项目根目录下的 testfiles 目录
    private File getTestFilesDirectory() {
        return new File(getDefaultDirectory(), "testfiles");
    }

    // 功能: 将默认测试文件绑定到输入框（支持PDF/Word/Excel/TXT）
    private void applyDefaultFileSelections() {
        // 不再自动加载默认文件，由用户手动选择
    }

    // 功能: 根据优先和备选文件名解析默认测试文件（支持多个候选）
    private File resolveDefaultTestFile(File baseDir, String... candidateNames) {
        for (String name : candidateNames) {
            File candidate = new File(baseDir, name);
            if (candidate.exists()) {
                LOGGER.info(() -> "找到默认测试文件: " + candidate.getAbsolutePath());
                return candidate;
            }
        }
        // 如果都不存在，返回第一个候选名
        File firstCandidate = new File(baseDir, candidateNames.length > 0 ? candidateNames[0] : "test.pdf");
        LOGGER.warning(() -> "默认测试文件不存在，使用: " + firstCandidate.getAbsolutePath());
        return firstCandidate;
    }

    // 功能: 选择多个文件（至少2个）
    private void selectMultipleFiles() {
        File testFilesDir = getTestFilesDirectory();
        String defaultPath = testFilesDir.exists() ? testFilesDir.getAbsolutePath() : getDefaultDirectory();
        
        JFileChooser fileChooser = new JFileChooser(defaultPath);
        // 仅展示并允许选择 PDF 文件
        FileNameExtensionFilter pdfFilter = new FileNameExtensionFilter("PDF 文件 (*.pdf)", "pdf");
        fileChooser.setFileFilter(pdfFilter);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setDialogTitle("选择标书文件（请选择至少2个PDF文件）");
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] files = fileChooser.getSelectedFiles();
            
            // 检查文件数量
            if (files.length < 2) {
                JOptionPane.showMessageDialog(this,
                    "请至少选择2个PDF文件！\n\n"
                    + "当前只选择了 " + files.length + " 个文件\n\n"
                    + "提示：\n"
                    + "• 选择2个文件可进行对比\n"
                    + "• 选择多个文件可测试配对生成器",
                    "文件数量不足",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // 检查文件格式
            for (File file : files) {
                if (!file.getName().toLowerCase().endsWith(".pdf")) {
                    JOptionPane.showMessageDialog(this,
                        "请选择PDF文件！\n\n"
                        + "文件 '" + file.getName() + "' 不是PDF格式",
                        "文件格式错误",
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            
            // 更新文件列表
            selectedFiles.clear();
            selectedFiles.addAll(Arrays.asList(files));
            fileTexts.clear();
            
            // 更新显示
            fileListModel.clear();
            for (File file : selectedFiles) {
                fileListModel.addElement(file.getName());
            }
            
            // 清空预览区
            previewArea.setText("");
            resultArea.setText("");
            
            // 更新按钮状态
                readAllFilesButton.setEnabled(files.length == 2);
            compareButton.setEnabled(false);
            generateAnnotationDataButton.setEnabled(false);
            annotatePdfButton.setEnabled(false);
                batchRunButton.setEnabled(files.length >= 2);
            
            LOGGER.info("已选择 " + files.length + " 个文件");
            for (File file : files) {
                LOGGER.info("  - " + file.getName());
            }
            
            // 提示用户下一步操作
            if (files.length == 2) {
                resultArea.append("已选择2个文件，可以进行对比操作。\n");
                resultArea.append("请点击\"读取所选文件\"按钮继续。\n");
            } else {
                resultArea.append(String.format("已选择%d个文件。\n", files.length));
                resultArea.append("• 如需对比2个文件，请重新选择2个文件\n");
                resultArea.append("• 可点击\"测试配对生成器\"查看所有可能的配对组合\n");
            }
        }
    }
    
    // 功能: 读取所有选中的文件
    private void readAllFiles() {
        if (selectedFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先选择文件！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (selectedFiles.size() != 2) {
            JOptionPane.showMessageDialog(this, "请选择2个文件！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        readAllFilesButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("正在读取文件...");
        
        // 使用SwingWorker异步读取
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                fileTexts.clear();
                
                for (int i = 0; i < selectedFiles.size(); i++) {
                    File file = selectedFiles.get(i);
                    String displayName = "文件" + (i + 1);
                    
                    publish("正在读取 " + displayName + ": " + file.getName() + "...");
                    
                    String text = extractTextFromFile(file, displayName);
                    fileTexts.put(file, text);
                    
                    publish("✓ " + displayName + " 读取完成: " + text.length() + " 字符");
                }
                
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    resultArea.append(msg + "\n");
                }
                resultArea.setCaretPosition(resultArea.getDocument().getLength());
            }
            
            @Override
            protected void done() {
                try {
                    get();
                    resultArea.append("\n所有文件读取完成！\n");
                    
                    // 显示第一个文件的预览
                    if (!selectedFiles.isEmpty()) {
                        File firstFile = selectedFiles.get(0);
                        String text = fileTexts.get(firstFile);
                        if (text != null) {
                            previewArea.setText("文件1 - " + firstFile.getName() + "\n\n" + text);
                            previewArea.setCaretPosition(0);
                        }
                    }
                    
                    // 更新按钮状态
                    compareButton.setEnabled(true);
                    generateAnnotationDataButton.setEnabled(true);
                    
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "读取文件失败", ex);
                    resultArea.append("\n读取失败: " + ex.getMessage() + "\n");
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "读取失败: " + ex.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    readAllFilesButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("就绪");
                }
            }
        };
        
        worker.execute();
    }

    // 功能: 从文件中提取文本（支持PDF/Word/Excel/TXT）
    private String extractTextFromFile(File file, String displayName) throws Exception {
        String name = file.getName().toLowerCase(Locale.ROOT);

        // Word文件
        if (name.endsWith(".docx")) {
            LOGGER.info(() -> displayName + " 检测到Word文档");
            return BidChecker.readWord(file);
        }

        // Excel文件
        if (name.endsWith(".xlsx")) {
            LOGGER.info(() -> displayName + " 检测到Excel表格");
            return BidChecker.readExcel(file);
        }

        // TXT文件
        if (name.endsWith(".txt")) {
            LOGGER.info(() -> displayName + " 检测到文本文件");
            StringBuilder text = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(file, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line).append("\n");
                }
            }
            return text.toString();
        }

        // PDF文件
        if (name.endsWith(".pdf")) {
            LOGGER.info(() -> displayName + " 检测到PDF文件，开始分析...");

            // 先提取PDF基本信息
            PdfTextExtractor.PdfExtractionResult result = PdfTextExtractor.extract(file.toPath());

            // 检查是否为扫描件
            if (result.isScannedPdf()) {
                LOGGER.info(() -> displayName + " 识别为扫描件PDF，调用OCR识别...");

                // 调用OCR识别
                String ocrText = BidChecker.readPDF(file);

                if (ocrText == null || ocrText.trim().isEmpty()) {
                    throw new IOException("OCR识别未能提取到文本。\n请确保OCR服务已启动: python ocr-service/run_easyocr_service.py");
                }

                LOGGER.info(() -> String.format("%s OCR识别完成: %d 页，%d 字符",
                    displayName, result.pageCount, ocrText.length()));

                return ocrText;
            } else {
                // Word转PDF或普通PDF，直接提取文本
                LOGGER.info(() -> displayName + " 识别为可提取文本的PDF");
                String text = result.getText();

                if (text == null || text.trim().isEmpty()) {
                    throw new IOException("PDF文本提取失败，未能获取到内容");
                }

                return text;
            }
        }

        // 未知格式
        throw new IOException("不支持的文件格式: " + name);
    }

    // 功能: 对比两个已读取的文本
    private void compareFiles() {
        if (fileTexts.size() != 2) {
            JOptionPane.showMessageDialog(this,
                "请先读取两个文件！",
                "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (selectedFiles.size() != 2) {
            JOptionPane.showMessageDialog(this,
                "请选择两个文件！",
                "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        progressBar.setIndeterminate(true);
        progressBar.setString("正在对比...");
        compareButton.setEnabled(false);

        // 使用SwingWorker异步对比
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                LOGGER.info("开始对比文本内容");

                // 获取两个文件的文本
                File file1 = selectedFiles.get(0);
                File file2 = selectedFiles.get(1);
                String text1 = fileTexts.get(file1);
                String text2 = fileTexts.get(file2);
                
                // 直接对比已读取的文本内容
                return BidChecker.compareTexts(text1, text2);
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    resultArea.setText(result);
                    resultArea.setCaretPosition(0);
                    LOGGER.info("对比完成");
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "对比文件失败", ex);
                    resultArea.setText("对比出错：" + ex.getMessage());
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "对比失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                } finally {
                    compareButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("就绪");
                }
            }
        };

        worker.execute();
    }

    // 功能: 清空指定预览区内容
    private void clearPreviewArea(JTextArea area) {
        if (area != null) {
            area.setText("");
        }
    }

    // 功能: 选择用于去除公章的图像文件
    // private void chooseImageFile() {
    //     String defaultPath = getDefaultDirectory();
    //     JFileChooser fileChooser = createImageFileChooser(defaultPath);

    //     int result = fileChooser.showOpenDialog(this);
    //     if (result == JFileChooser.APPROVE_OPTION) {
    //         selectedImageFile = fileChooser.getSelectedFile();
    //         imageFileField.setText(selectedImageFile.getAbsolutePath());

    //         removeSealButton.setEnabled(true);
    //         previewButton.setEnabled(true);

    //         sealResultArea.setText("已选择图像文件: " + selectedImageFile.getName() + "\n");
    //         sealResultArea.append("文件大小: " + (selectedImageFile.length() / 1024) + " KB\n");
    //         sealResultArea.append("点击'智能去除公章'开始处理...\n");
    //     }
    // }

    // 功能: 选择供 OCR 校验的图像文件
    // private void chooseOcrImageFile() {
    //     String defaultPath = getDefaultDirectory();
    //     JFileChooser fileChooser = createImageFileChooser(defaultPath);

    //     int result = fileChooser.showOpenDialog(this);
    //     if (result == JFileChooser.APPROVE_OPTION) {
    //         selectedOcrFile = fileChooser.getSelectedFile();
    //         ocrImageField.setText(selectedOcrFile.getAbsolutePath());
    //         runOcrButton.setEnabled(true);
    //         ocrPreviewButton.setEnabled(true);

    //         ocrResultArea.setText("已选择图像文件: " + selectedOcrFile.getName() + "\n");
    //         ocrResultArea.append("文件大小: " + (selectedOcrFile.length() / 1024) + " KB\n");
    //         ocrResultArea.append("点击'执行OCR'开始识别...\n");
    //     }
    // }

    // 功能: 预览当前选中的公章处理图像
    // private void previewImage() {
    //     if (selectedImageFile == null) {
    //         JOptionPane.showMessageDialog(this, "请先选择图像文件！", "提示", JOptionPane.WARNING_MESSAGE);
    //         return;
    //     }

    //     try {
    //         BufferedImage image = ImageProcessor.readImage(selectedImageFile);
    //         if (image != null) {
    //             showImagePreview(image, "原始图像预览");
    //         } else {
    //             JOptionPane.showMessageDialog(this, "无法读取图像文件，请检查文件格式！", "错误", JOptionPane.ERROR_MESSAGE);
    //         }
    //     } catch (Exception e) {
    //         JOptionPane.showMessageDialog(this, "预览图像时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
    //     }
    // }

    // 功能: 预览当前选中的 OCR 图像
    // private void previewOcrImage() {
    //     if (selectedOcrFile == null) {
    //         JOptionPane.showMessageDialog(this, "请先选择图像文件！", "提示", JOptionPane.WARNING_MESSAGE);
    //         return;
    //     }

    //     try {
    //         BufferedImage image = ImageProcessor.readImage(selectedOcrFile);
    //         if (image != null) {
    //             showImagePreview(image, "OCR图像预览");
    //         } else {
    //             JOptionPane.showMessageDialog(this, "无法读取图像文件，请检查文件格式！", "错误", JOptionPane.ERROR_MESSAGE);
    //         }
    //     } catch (Exception e) {
    //         JOptionPane.showMessageDialog(this, "预览图像时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
    //     }
    // }

    // 功能: 执行 OCR 识别流程并呈现结果
    // private void runOcrAction() {
    //     if (selectedOcrFile == null) {
    //         JOptionPane.showMessageDialog(this, "请先选择图像文件！", "提示", JOptionPane.WARNING_MESSAGE);
    //         return;
    //     }

    //     ocrResultArea.setText("正在准备OCR识别...\n");

    //     SwingWorker<TextExtractor.OCRResult, String> worker = new SwingWorker<>() {
    //         @Override
    //         protected TextExtractor.OCRResult doInBackground() {
    //             publish("开始OCR验证流程...");

    //             SwingUtilities.invokeLater(() -> {
    //                 progressBar.setIndeterminate(true);
    //                 progressBar.setString("OCR处理中...");
    //                 runOcrButton.setEnabled(false);
    //             });

    //             try {
    //                 long startTime = System.currentTimeMillis();

    //                 publish("步骤1: 读取图像文件...");
    //                 BufferedImage originalImage = ImageProcessor.readImage(selectedOcrFile);
    //                 if (originalImage == null) {
    //                     publish("错误: 无法读取图像文件");
    //                     return null;
    //                 }

    //                 publish("步骤2: 图像预处理...");
    //                 BufferedImage preprocessedImage = ImageProcessor.preprocessImage(originalImage);
    //                 if (preprocessedImage == null) {
    //                     publish("错误: 图像预处理失败");
    //                     return null;
    //                 }

    //                 publish("步骤3: 执行OCR识别...");
    //                 TextExtractor.OCRResult ocrResult = TextExtractor.extractText(preprocessedImage);
    //                 if (ocrResult == null) {
    //                     publish("错误: OCR识别失败");
    //                     return null;
    //                 }

    //                 long totalTime = System.currentTimeMillis() - startTime;
    //                 publish(String.format("总耗时: %d ms", totalTime));
    //                 publish(String.format("识别置信度: %.1f%%", ocrResult.confidence * 100));
    //                 publish("文本行数: " + ocrResult.lines.size());

    //                 publish("--- OCR前5行 ---");
    //                 for (int i = 0; i < Math.min(5, ocrResult.lines.size()); i++) {
    //                     publish(String.format("%d: %s", i + 1, ocrResult.lines.get(i)));
    //                 }
    //                 if (ocrResult.lines.size() > 5) {
    //                     publish(String.format("... (共 %d 行)", ocrResult.lines.size()));
    //                 }

    //                 if (!ocrResult.extractedInfo.isEmpty()) {
    //                     publish("\n--- 结构化字段 ---");
    //                     for (String key : ocrResult.extractedInfo.keySet()) {
    //                         publish(key + ": " + ocrResult.extractedInfo.get(key));
    //                     }
    //                 }

    //                 return ocrResult;
    //             } catch (Exception e) {
    //                 publish("错误: " + e.getMessage());
    //                 return null;
    //             }
    //         }

    //         @Override
    //         protected void process(java.util.List<String> chunks) {
    //             for (String message : chunks) {
    //                 ocrResultArea.append(message + "\n");
    //             }
    //             ocrResultArea.setCaretPosition(ocrResultArea.getDocument().getLength());
    //         }

    //         @Override
    //         protected void done() {
    //             SwingUtilities.invokeLater(() -> {
    //                 progressBar.setIndeterminate(false);
    //                 progressBar.setString("就绪");
    //                 runOcrButton.setEnabled(selectedOcrFile != null);
    //             });

    //             try {
    //                 TextExtractor.OCRResult result = get();
    //                 if (result == null) {
    //                     JOptionPane.showMessageDialog(BidCheckerGUI.this, "OCR识别失败或未返回结果", "提示", JOptionPane.WARNING_MESSAGE);
    //                 }
    //             } catch (Exception e) {
    //                 JOptionPane.showMessageDialog(BidCheckerGUI.this, "OCR执行出现异常: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
    //             }
    //         }
    //     };

    //     worker.execute();
    // }

    // 功能: 弹窗展示缩放后的图片预览
    // private void showImagePreview(BufferedImage image, String title) {
    //     JFrame previewFrame = new JFrame(title);
    //     previewFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    //     int maxWidth = 600;
    //     int maxHeight = 400;
    //     int width = image.getWidth();
    //     int height = image.getHeight();

    //     if (width > maxWidth || height > maxHeight) {
    //         double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
    //         width = (int) (width * scale);
    //         height = (int) (height * scale);
    //     }

    //     BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    //     Graphics2D g2d = scaledImage.createGraphics();
    //     g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    //     g2d.drawImage(image, 0, 0, width, height, null);
    //     g2d.dispose();

    //     JLabel imageLabel = new JLabel(new ImageIcon(scaledImage));
    //     JScrollPane scrollPane = new JScrollPane(imageLabel);

    //     previewFrame.add(scrollPane);
    //     previewFrame.setSize(width + 50, height + 80);
    //     previewFrame.setLocationRelativeTo(this);
    //     previewFrame.setVisible(true);
    // }

    // 功能: 调用图像处理流水线去除公章
    // private void removeSealAction() {
    //     if (selectedImageFile == null) {
    //         JOptionPane.showMessageDialog(this, "请先选择图像文件！", "提示", JOptionPane.WARNING_MESSAGE);
    //         return;
    //     }

    //     SwingWorker<Void, String> worker = new SwingWorker<>() {
    //         @Override
    //         protected Void doInBackground() {
    //             publish("开始处理图像...");

    //             try {
    //                 SwingUtilities.invokeLater(() -> {
    //                     progressBar.setIndeterminate(true);
    //                     progressBar.setString("正在处理中...");
    //                     removeSealButton.setEnabled(false);
    //                 });

    //                 long startTime = System.currentTimeMillis();

    //                 publish("步骤1: 读取图像文件...");
    //                 Thread.sleep(200);

    //                 File outputDir = new File("output");
    //                 String fileNamePrefix = "bidguard_" + System.currentTimeMillis();

    //                 publish("输出目录: " + outputDir.getAbsolutePath());
    //                 publish("步骤2: 执行完整处理流程...");

    //                 ProcessedDataStorage.ProcessedData result = ProcessedDataStorage.processAndSave(
    //                         ImageProcessor.readImage(selectedImageFile),
    //                         outputDir,
    //                         fileNamePrefix
    //                 );

    //                 if (result == null) {
    //                     publish("错误: 图像处理失败");
    //                     return null;
    //                 }

    //                 publish("步骤3: 执行OCR文字识别...");

    //                 TextExtractor.OCRResult ocrResult = TextExtractor.extractText(result.binarizedImage);

    //                 long endTime = System.currentTimeMillis();
    //                 long totalTime = endTime - startTime;

    //                 publish("处理完成！");
    //                 publish("总耗时: " + totalTime + " 毫秒");
    //                 publish("公章去除率: " + result.metadata.get("sealRemovalRate"));
    //                 publish("识别文本行数: " + (ocrResult != null ? ocrResult.lines.size() : 0));

    //                 if (ocrResult != null && !ocrResult.extractedInfo.isEmpty()) {
    //                     publish("\n=== 提取的结构化信息 ===");
    //                     for (String key : ocrResult.extractedInfo.keySet()) {
    //                         publish(key + ": " + ocrResult.extractedInfo.get(key));
    //                     }
    //                 }

    //                 publish("\n生成的文件:");
    //                 File[] outputFiles = outputDir.listFiles((dir, name) -> name.startsWith(fileNamePrefix));
    //                 if (outputFiles != null) {
    //                     for (File file : outputFiles) {
    //                         publish("- " + file.getName());
    //                     }
    //                 }

    //                 SwingUtilities.invokeLater(() -> {
    //                     int choice = JOptionPane.showConfirmDialog(
    //                             BidCheckerGUI.this,
    //                             "处理完成！是否查看处理后的图像？",
    //                             "处理完成",
    //                             JOptionPane.YES_NO_OPTION
    //                     );

    //                     if (choice == JOptionPane.YES_OPTION && result.cleanedImage != null) {
    //                         showImagePreview(result.cleanedImage, "公章去除结果");
    //                     }
    //                 });

    //             } catch (Exception e) {
    //                 publish("错误: " + e.getMessage());
    //                 LOGGER.log(Level.SEVERE, "公章去除失败", e);
    //             }

    //             return null;
    //         }

    //         @Override
    //         protected void process(java.util.List<String> chunks) {
    //             for (String message : chunks) {
    //                 sealResultArea.append(message + "\n");
    //             }
    //             sealResultArea.setCaretPosition(sealResultArea.getDocument().getLength());
    //         }

    //         @Override
    //         protected void done() {
    //             SwingUtilities.invokeLater(() -> {
    //                 progressBar.setIndeterminate(false);
    //                 progressBar.setString("就绪");
    //                 removeSealButton.setEnabled(true);
    //             });
    //         }
    //     };

    //     worker.execute();
    // }

    // 功能: 创建限制图像类型的文件选择器
    // private JFileChooser createImageFileChooser(String defaultPath) {
    //     JFileChooser fileChooser = new JFileChooser(defaultPath);
    //     fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
    //         @Override
    //         public boolean accept(File f) {
    //             if (f.isDirectory()) {
    //                 return true;
    //             }
    //             String name = f.getName().toLowerCase();
    //             return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
    //                     name.endsWith(".png") || name.endsWith(".bmp") ||
    //                     name.endsWith(".tiff") || name.endsWith(".gif");
    //         }

    //         @Override
    //         public String getDescription() {
    //             return "图像文件 (*.jpg, *.png, *.bmp, *.tiff, *.gif)";
    //         }
    //     });
    //     return fileChooser;
    // }

    // 功能: 生成查重标注数据（仅支持PDF文件）
    private void generateDuplicateAnnotationData() {
        if (selectedFiles.size() != 2) {
            JOptionPane.showMessageDialog(this,
                "请先选择两个文件！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        File file1 = selectedFiles.get(0);
        File file2 = selectedFiles.get(1);
        
        // 检查是否都是PDF文件
        if (!file1.getName().toLowerCase().endsWith(".pdf") ||
            !file2.getName().toLowerCase().endsWith(".pdf")) {
            JOptionPane.showMessageDialog(this,
                "查重标注数据生成功能仅支持PDF文件！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        progressBar.setIndeterminate(true);
        progressBar.setString("正在生成查重标注数据...");
        generateAnnotationDataButton.setEnabled(false);
        
        // 使用SwingWorker异步处理
        SwingWorker<File, String> worker = new SwingWorker<>() {
            @Override
            protected File doInBackground() throws Exception {
                LOGGER.info("开始生成查重标注数据");
                publish("正在获取OCR识别结果...\n");
                
                // 获取两个PDF的OCR结果
                OcrServiceClient.OcrResult ocrResult1 = OcrServiceFactory.recognizePdf(file1);
                publish("文档1 OCR完成: " + ocrResult1.textCount + " 个文字块\n");
                
                OcrServiceClient.OcrResult ocrResult2 = OcrServiceFactory.recognizePdf(file2);
                publish("文档2 OCR完成: " + ocrResult2.textCount + " 个文字块\n");
                
                publish("\n正在执行查重检测...\n");
                
                // 执行查重检测
                int minLength = SimilarityConfig.getInstance().substringMinLength;
                OcrDuplicateDetector.DuplicateDetectionResult detection = 
                    OcrDuplicateDetector.detectDuplicates(
                        ocrResult1,
                        ocrResult2,
                        file1.getName(),
                        file2.getName(),
                        minLength
                    );
                
                publish("找到 " + detection.totalMatches + " 个重复片段\n");
                
                publish("\n正在保存结果文件...\n");
                
                // 保存结果到JSON文件
                File jsonFile = OcrDuplicateDetector.saveResultToJson(
                    detection,
                    file1.getName(),
                    file2.getName()
                );
                
                publish("查重标注数据已保存！\n");
                
                return jsonFile;
            }
            
            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    resultArea.append(message);
                }
                resultArea.setCaretPosition(resultArea.getDocument().getLength());
            }
            
            @Override
            protected void done() {
                try {
                    File jsonFile = get();
                    
                    // 保存JSON文件引用，供标注功能使用
                    latestDetectionJsonFile = jsonFile;
                    
                    resultArea.append("\n" + "=".repeat(60) + "\n");
                    resultArea.append("✓ 查重标注数据生成成功！\n");
                    resultArea.append("=".repeat(60) + "\n\n");
                    resultArea.append("JSON文件: " + jsonFile.getName() + "\n");
                    resultArea.append("文本报告: " + jsonFile.getName().replace(".json", ".txt").replace("duplicate_detection_", "duplicate_report_") + "\n");
                    resultArea.append("保存位置: " + jsonFile.getParent() + "\n\n");
                    resultArea.append("请检查上述文件，确认无误后，点击'执行PDF标注'按钮。\n");
                    
                    LOGGER.info("查重标注数据生成完成");
                    
                    // 启用标注按钮
                    annotatePdfButton.setEnabled(true);
                    
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "查重标注数据已生成！\n\n" +
                        "文件保存在 output/ 目录下：\n" +
                        "- " + jsonFile.getName() + " (JSON数据)\n" +
                        "- " + jsonFile.getName().replace(".json", ".txt").replace("duplicate_detection_", "duplicate_report_") + " (可读报告)\n\n" +
                        "请人工检查文件内容，确认无误后点击'执行PDF标注'按钮。",
                        "成功", JOptionPane.INFORMATION_MESSAGE);
                        
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "生成查重标注数据失败", ex);
                    resultArea.append("\n✗ 生成失败: " + ex.getMessage() + "\n");
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "生成查重标注数据失败:\n" + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                } finally {
                    generateAnnotationDataButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("就绪");
                }
            }
        };
        
        worker.execute();
    }

    // 功能: 执行PDF标注
    private void annotatePdfs() {
        if (latestDetectionJsonFile == null || !latestDetectionJsonFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "未找到查重检测结果！\n请先点击'生成查重标注数据'按钮。",
                "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (selectedFiles.size() != 2) {
            JOptionPane.showMessageDialog(this,
                "请先选择两个文件！",
                "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        File file1 = selectedFiles.get(0);
        File file2 = selectedFiles.get(1);
        
        // 确认执行标注
        int confirm = JOptionPane.showConfirmDialog(this,
            "确认要在PDF上标注重复内容吗？\n\n" +
            "将会生成两个新的PDF文件：\n" +
            "- " + file1.getName().replace(".pdf", "") + "_annotated_*.pdf\n" +
            "- " + file2.getName().replace(".pdf", "") + "_annotated_*.pdf\n\n" +
            "原始文件不会被修改。",
            "确认标注",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        progressBar.setIndeterminate(true);
        progressBar.setString("正在标注PDF...");
        annotatePdfButton.setEnabled(false);
        
        // 使用SwingWorker异步处理
        SwingWorker<PdfAnnotator.AnnotationResult, String> worker = new SwingWorker<>() {
            @Override
            protected PdfAnnotator.AnnotationResult doInBackground() throws Exception {
                LOGGER.info("开始PDF标注");
                publish("读取查重检测结果...\n");
                publish("检测文件: " + latestDetectionJsonFile.getName() + "\n\n");
                
                publish("正在标注PDF文件...\n");
                publish("文档1: " + file1.getName() + "\n");
                publish("文档2: " + file2.getName() + "\n\n");
                
                // 执行标注
                PdfAnnotator.AnnotationResult result = PdfAnnotator.annotatePdfs(
                    latestDetectionJsonFile,
                    file1,
                    file2
                );
                
                publish("标注完成！\n");
                
                return result;
            }
            
            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    resultArea.append(message);
                }
                resultArea.setCaretPosition(resultArea.getDocument().getLength());
            }
            
            @Override
            protected void done() {
                try {
                    PdfAnnotator.AnnotationResult result = get();
                    
                    resultArea.append("\n" + "=".repeat(60) + "\n");
                    resultArea.append("✓ PDF标注完成！\n");
                    resultArea.append("=".repeat(60) + "\n\n");
                    resultArea.append("文档1标注文件:\n");
                    resultArea.append("  " + result.annotatedFile1.getName() + "\n");
                    resultArea.append("  标注区域: " + result.totalAnnotations1 + " 个\n\n");
                    resultArea.append("文档2标注文件:\n");
                    resultArea.append("  " + result.annotatedFile2.getName() + "\n");
                    resultArea.append("  标注区域: " + result.totalAnnotations2 + " 个\n\n");
                    resultArea.append("文件位置: " + result.annotatedFile1.getParent() + "\n");
                    resultArea.append("=".repeat(60) + "\n");
                    
                    LOGGER.info("PDF标注完成");
                    
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "PDF标注完成！\n\n" +
                        "标注后的文件已保存到 output/ 目录：\n\n" +
                        "文档1:\n" +
                        "  " + result.annotatedFile1.getName() + "\n" +
                        "  标注区域: " + result.totalAnnotations1 + " 个\n\n" +
                        "文档2:\n" +
                        "  " + result.annotatedFile2.getName() + "\n" +
                        "  标注区域: " + result.totalAnnotations2 + " 个\n\n" +
                        "重复内容已用红色矩形框标注。",
                        "标注完成", JOptionPane.INFORMATION_MESSAGE);
                        
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "PDF标注失败", ex);
                    resultArea.append("\n✗ 标注失败: " + ex.getMessage() + "\n");
                    
                    // 提供更详细的错误信息
                    String errorDetail = ex.getMessage();
                    if (ex.getCause() != null) {
                        errorDetail += "\n原因: " + ex.getCause().getMessage();
                    }
                    
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "PDF标注失败:\n\n" + errorDetail + "\n\n" +
                        "请检查:\n" +
                        "1. 原始PDF文件是否存在\n" +
                        "2. 查重检测结果JSON是否完整\n" +
                        "3. output目录是否有写入权限",
                        "错误", JOptionPane.ERROR_MESSAGE);
                } finally {
                    annotatePdfButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("就绪");
                }
            }
        };
        
        worker.execute();
    }

    // 功能: 测试配对生成器
    private void testPairGenerator() {
        LOGGER.info("开始测试配对生成器");
        
        // 清空结果区
        resultArea.setText("");
        
        if (selectedFiles.isEmpty()) {
            resultArea.append("❌ 请先选择文件！\n\n");
            resultArea.append("点击\"选择标书文件\"按钮，选择多个PDF文件（建议3-5个）。\n");
            JOptionPane.showMessageDialog(this,
                "请先选择文件！\n\n请点击\"选择标书文件\"按钮选择多个PDF文件。",
                "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        if (selectedFiles.size() < 2) {
            resultArea.append("❌ 至少需要2个文件才能生成配对！\n\n");
            resultArea.append("当前选择: " + selectedFiles.size() + " 个文件\n");
            JOptionPane.showMessageDialog(this,
                "至少需要2个文件才能生成配对！\n\n当前只选择了 " + selectedFiles.size() + " 个文件。",
                "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        resultArea.append("=".repeat(60) + "\n");
        resultArea.append("配对生成器测试\n");
        resultArea.append("=".repeat(60) + "\n\n");
        
        // 显示输入文件
        resultArea.append("📁 输入文件列表 (" + selectedFiles.size() + " 个):\n");
        for (int i = 0; i < selectedFiles.size(); i++) {
            File file = selectedFiles.get(i);
            resultArea.append(String.format("  [%d] %s\n", i + 1, file.getName()));
        }
        resultArea.append("\n");
        
        // 计算预期配对数量
        int expectedPairCount = PairGenerator.calculatePairCount(selectedFiles.size());
        resultArea.append(String.format("📊 预期生成配对数: C(%d,2) = %d\n\n", 
            selectedFiles.size(), expectedPairCount));
        
        // 生成配对
        LOGGER.info("调用 PairGenerator.generatePairs() 生成配对...");
        List<FilePair> pairs = PairGenerator.generatePairs(selectedFiles);
        
        // 显示生成结果
        resultArea.append("=".repeat(60) + "\n");
        resultArea.append("✅ 配对生成结果\n");
        resultArea.append("=".repeat(60) + "\n\n");
        resultArea.append(String.format("实际生成配对数: %d\n\n", pairs.size()));
        
        // 显示所有配对
        resultArea.append("配对详情:\n");
        resultArea.append("-".repeat(60) + "\n");
        for (int i = 0; i < pairs.size(); i++) {
            FilePair pair = pairs.get(i);
            resultArea.append(String.format("配对 #%-2d: %s\n", 
                i + 1, pair.toString()));
            resultArea.append(String.format("          ↳ [%s]\n", 
                pair.getFileA().getName()));
            resultArea.append(String.format("          ↳ [%s]\n", 
                pair.getFileB().getName()));
            if (i < pairs.size() - 1) {
                resultArea.append("\n");
            }
        }
        resultArea.append("-".repeat(60) + "\n\n");
        
        // 验证结果
        boolean testPassed = (pairs.size() == expectedPairCount);
        if (testPassed) {
            resultArea.append("✅ 测试通过！配对数量正确。\n\n");
        } else {
            resultArea.append(String.format("❌ 测试失败！预期 %d 个配对，实际生成 %d 个。\n\n", 
                expectedPairCount, pairs.size()));
        }
        
        // 算法说明
        resultArea.append("=".repeat(60) + "\n");
        resultArea.append("算法说明\n");
        resultArea.append("=".repeat(60) + "\n");
        resultArea.append("核心逻辑：双层循环\n\n");
        resultArea.append("for (int i = 0; i < files.size(); i++) {\n");
        resultArea.append("    for (int j = i + 1; j < files.size(); j++) {\n");
        resultArea.append("        pairs.add(new FilePair(files.get(i), files.get(j)));\n");
        resultArea.append("    }\n");
        resultArea.append("}\n\n");
        resultArea.append("特点：\n");
        resultArea.append("  • 避免重复（不会生成 A vs B 又生成 B vs A）\n");
        resultArea.append("  • 避免自我配对（不会生成 A vs A）\n");
        resultArea.append("  • 保证顺序（i < j）\n\n");
        
        // 滚动到顶部
        resultArea.setCaretPosition(0);
        
        LOGGER.info("配对生成器测试完成，生成 " + pairs.size() + " 个配对");
        
        // 显示弹窗
        JOptionPane.showMessageDialog(this,
            String.format("配对生成器测试完成！\n\n" +
                "从 %d 个文件生成了 %d 个配对组合\n\n" +
                "请查看结果区域了解详细信息。",
                selectedFiles.size(), pairs.size()),
            "测试完成", JOptionPane.INFORMATION_MESSAGE);
    }

    // 功能: 批量执行查重并标注（生成 pairs -> OCR(缓存) -> detect -> save JSON -> annotate）
    private void runBatchDuplicateAndAnnotate() {
        if (selectedFiles.size() < 2) {
            JOptionPane.showMessageDialog(this, "请至少选择2个PDF文件！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 构造 PdfTask 列表
        List<PdfTask> tasks = new ArrayList<>();
        for (File f : selectedFiles) tasks.add(new PdfTask(f));

        // 清空结果区并开始任务
        resultArea.setText("");
        progressBar.setIndeterminate(true);
        progressBar.setString("正在批量执行查重并标注...");

        // 禁用相关按钮
        batchRunButton.setEnabled(false);
        selectFilesButton.setEnabled(false);
        readAllFilesButton.setEnabled(false);

        SwingWorker<Map<FilePair, File>, String> worker = new SwingWorker<>() {
            @Override
            protected Map<FilePair, File> doInBackground() {
                publish("开始批量处理...\n");
                Map<FilePair, File> results = BatchDuplicateRunner.runBatchDuplicateCheck(tasks);
                publish(String.format("批量处理完成，生成 %d 个 JSON 结果。\n", results.size()));
                return results;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String m : chunks) {
                    resultArea.append(m);
                }
                resultArea.setCaretPosition(resultArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    Map<FilePair, File> map = get();
                    resultArea.append("\n处理汇总:\n");
                    resultArea.append("-".repeat(60) + "\n");
                    for (Map.Entry<FilePair, File> e : map.entrySet()) {
                        resultArea.append(String.format("%s -> %s\n", e.getKey().toString(), e.getValue().getName()));
                    }
                    resultArea.append("-".repeat(60) + "\n");
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        String.format("批量处理完成：共生成 %d 个结果文件", map.size()),
                        "完成", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "批量处理失败", ex);
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "批量处理失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                } finally {
                    progressBar.setIndeterminate(false);
                    progressBar.setString("就绪");
                    batchRunButton.setEnabled(true);
                    selectFilesButton.setEnabled(true);
                    readAllFilesButton.setEnabled(selectedFiles.size() == 2);
                }
            }
        };

        worker.execute();
    }

    // 功能: 启动 Swing 应用入口
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BidCheckerGUI gui = new BidCheckerGUI();
            gui.setVisible(true);
        });
    }
}
