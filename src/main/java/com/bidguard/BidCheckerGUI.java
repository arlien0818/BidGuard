package com.bidguard;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BidCheckerGUI extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(BidCheckerGUI.class.getName());

    // 文件对比组件
    private JTextField file1Field;
    private JTextField file2Field;
    private JButton selectFile1Button;
    private JButton selectFile2Button;
    private JButton readFile1Button;  // 新增：读取文件1按钮
    private JButton readFile2Button;  // 新增：读取文件2按钮
    private JButton compareButton;
    private JButton generateAnnotationDataButton;  // 新增：生成查重标注数据按钮
    private JButton annotatePdfButton;  // 新增：执行PDF标注按钮
    private JTextArea resultArea;
    private File file1;
    private File file2;
    private String file1Text;  // 新增：存储文件1已读取的文本
    private String file2Text;  // 新增：存储文件2已读取的文本
    private File latestDetectionJsonFile;  // 新增：最新生成的查重JSON文件
    private JTextArea file1PreviewArea;
    private JTextArea file2PreviewArea;

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
        JPanel panel = new JPanel(new BorderLayout());

        // === 文件1组 ===
        JPanel file1Panel = new JPanel(new BorderLayout(5, 5));
        file1Panel.setBorder(BorderFactory.createTitledBorder("文件1"));

        file1Field = new JTextField();
        file1Field.setEditable(false);

        JPanel file1ButtonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        selectFile1Button = new JButton("选择文件");
        selectFile1Button.addActionListener(e -> chooseFile(1));
        readFile1Button = new JButton("读取");
        readFile1Button.setEnabled(false);
        readFile1Button.addActionListener(e -> readFile(1));
        file1ButtonPanel.add(selectFile1Button);
        file1ButtonPanel.add(readFile1Button);

        JPanel file1TopPanel = new JPanel(new BorderLayout(5, 0));
        file1TopPanel.add(file1Field, BorderLayout.CENTER);
        file1TopPanel.add(file1ButtonPanel, BorderLayout.EAST);

        file1PreviewArea = createPreviewTextArea();
        JScrollPane file1PreviewScroll = new JScrollPane(file1PreviewArea);
        file1PreviewScroll.setBorder(BorderFactory.createTitledBorder("文本预览"));

        file1Panel.add(file1TopPanel, BorderLayout.NORTH);
        file1Panel.add(file1PreviewScroll, BorderLayout.CENTER);

        // === 文件2组 ===
        JPanel file2Panel = new JPanel(new BorderLayout(5, 5));
        file2Panel.setBorder(BorderFactory.createTitledBorder("文件2"));

        file2Field = new JTextField();
        file2Field.setEditable(false);

        JPanel file2ButtonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        selectFile2Button = new JButton("选择文件");
        selectFile2Button.addActionListener(e -> chooseFile(2));
        readFile2Button = new JButton("读取");
        readFile2Button.setEnabled(false);
        readFile2Button.addActionListener(e -> readFile(2));
        file2ButtonPanel.add(selectFile2Button);
        file2ButtonPanel.add(readFile2Button);

        JPanel file2TopPanel = new JPanel(new BorderLayout(5, 0));
        file2TopPanel.add(file2Field, BorderLayout.CENTER);
        file2TopPanel.add(file2ButtonPanel, BorderLayout.EAST);

        file2PreviewArea = createPreviewTextArea();
        JScrollPane file2PreviewScroll = new JScrollPane(file2PreviewArea);
        file2PreviewScroll.setBorder(BorderFactory.createTitledBorder("文本预览"));

        file2Panel.add(file2TopPanel, BorderLayout.NORTH);
        file2Panel.add(file2PreviewScroll, BorderLayout.CENTER);

        // === 预览区分割 ===
        JSplitPane previewSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            file1Panel, file2Panel);
        previewSplitPane.setResizeWeight(0.5);
        previewSplitPane.setContinuousLayout(true);

        // === 对比按钮和结果区 ===
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        compareButton = new JButton("开始对比");
        compareButton.setEnabled(false);
        compareButton.addActionListener(e -> compareFiles());
        compareButton.setPreferredSize(new Dimension(120, 30));
        
        // 新增：生成查重标注数据按钮
        JButton generateAnnotationDataButton = new JButton("生成查重标注数据");
        generateAnnotationDataButton.setEnabled(false);
        generateAnnotationDataButton.addActionListener(e -> generateDuplicateAnnotationData());
        generateAnnotationDataButton.setPreferredSize(new Dimension(160, 30));
        
        // 新增：执行PDF标注按钮
        JButton annotatePdfButton = new JButton("执行PDF标注");
        annotatePdfButton.setEnabled(false);
        annotatePdfButton.addActionListener(e -> annotatePdfs());
        annotatePdfButton.setPreferredSize(new Dimension(120, 30));
        annotatePdfButton.setToolTipText("先生成查重标注数据，检查无误后再执行标注");
        
        // 将按钮引用保存为成员变量，以便后续更新状态
        this.generateAnnotationDataButton = generateAnnotationDataButton;
        this.annotatePdfButton = annotatePdfButton;

        JPanel compareButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        compareButtonPanel.add(compareButton);
        compareButtonPanel.add(generateAnnotationDataButton);
        compareButtonPanel.add(annotatePdfButton);

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        JScrollPane resultScrollPane = new JScrollPane(resultArea);
        resultScrollPane.setBorder(BorderFactory.createTitledBorder("对比结果"));
        resultScrollPane.setPreferredSize(new Dimension(0, 150));

        bottomPanel.add(compareButtonPanel, BorderLayout.NORTH);
        bottomPanel.add(resultScrollPane, BorderLayout.CENTER);

        // === 整体布局 ===
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            previewSplitPane, bottomPanel);
        mainSplitPane.setResizeWeight(0.7);
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

        JLabel versionLabel = new JLabel("BidGuard v2.06 - 两文档查重算法优化");

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
        if (file1Field == null || file2Field == null) {
            return;
        }
        File baseDir = getTestFilesDirectory();
        // 优先选择PDF，其次Word，再次TXT
        File defaultFile1 = resolveDefaultTestFile(baseDir, "text1.pdf", "test1.pdf", "test1.docx", "test1.txt");
        File defaultFile2 = resolveDefaultTestFile(baseDir, "text2.pdf", "test2.pdf", "test2.docx", "test2.txt");

        // 只设置文件路径，不自动读取
        if (defaultFile1.exists()) {
            file1 = defaultFile1;
            file1Field.setText(defaultFile1.getAbsolutePath());
            readFile1Button.setEnabled(true);
        }

        if (defaultFile2.exists()) {
            file2 = defaultFile2;
            file2Field.setText(defaultFile2.getAbsolutePath());
            readFile2Button.setEnabled(true);
        }
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

    // 功能: 打开文件选择器并记录用户选中的对比文件
    private void chooseFile(int fileNumber) {
        File testFilesDir = getTestFilesDirectory();
        String defaultPath = testFilesDir.exists() ? testFilesDir.getAbsolutePath() : getDefaultDirectory();
        JFileChooser fileChooser = new JFileChooser(defaultPath);
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            if (fileNumber == 1) {
                file1 = fileChooser.getSelectedFile();
                file1Field.setText(file1.getAbsolutePath());
                file1Text = null;  // 清空已读取的文本
                clearPreviewArea(file1PreviewArea);
                readFile1Button.setEnabled(true);  // 启用读取按钮
                LOGGER.info(() -> "已选择文件1: " + file1.getName());
            } else {
                file2 = fileChooser.getSelectedFile();
                file2Field.setText(file2.getAbsolutePath());
                file2Text = null;  // 清空已读取的文本
                clearPreviewArea(file2PreviewArea);
                readFile2Button.setEnabled(true);  // 启用读取按钮
                LOGGER.info(() -> "已选择文件2: " + file2.getName());
            }
            updateCompareButtonState();  // 更新对比按钮状态
        }
    }

    // 功能: 根据两个文件是否都已读取来更新对比按钮状态
    private void updateCompareButtonState() {
        boolean bothFilesRead = file1Text != null && file2Text != null;
        compareButton.setEnabled(bothFilesRead);
        
        // 只有两个文件都是PDF才能生成查重标注数据
        boolean bothPdf = file1 != null && file2 != null &&
                          file1.getName().toLowerCase().endsWith(".pdf") &&
                          file2.getName().toLowerCase().endsWith(".pdf");
        generateAnnotationDataButton.setEnabled(bothFilesRead && bothPdf);
    }

    // 功能: 读取文件内容（支持PDF/Word/Excel/TXT，PDF自动识别扫描件并调用OCR）
    private void readFile(int fileNumber) {
        File file = (fileNumber == 1) ? file1 : file2;
        JTextArea previewArea = (fileNumber == 1) ? file1PreviewArea : file2PreviewArea;
        JButton readButton = (fileNumber == 1) ? readFile1Button : readFile2Button;
        String displayName = "文件" + fileNumber;

        if (file == null || !file.exists()) {
            JOptionPane.showMessageDialog(this,
                displayName + " 不存在: " + (file != null ? file.getAbsolutePath() : ""),
                "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 禁用读取按钮，防止重复点击
        readButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("正在读取 " + displayName + "...");

        // 使用SwingWorker异步读取文件
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return extractTextFromFile(file, displayName);
            }

            @Override
            protected void done() {
                try {
                    String text = get();

                    if (fileNumber == 1) {
                        file1Text = text;
                    } else {
                        file2Text = text;
                    }

                    // 更新预览区
                    if (text == null || text.trim().isEmpty()) {
                        previewArea.setText(displayName + " - 未能提取到文本内容");
                    } else {
                        previewArea.setText(text);
                        previewArea.setCaretPosition(0);
                    }

                    LOGGER.info(() -> String.format("%s 读取完成，文本长度: %d 字符",
                        displayName, text != null ? text.length() : 0));

                    // 更新对比按钮状态
                    updateCompareButtonState();

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "读取" + displayName + "失败", ex);
                    previewArea.setText(displayName + " 读取失败：" + ex.getMessage());
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        displayName + " 读取失败: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                } finally {
                    readButton.setEnabled(true);
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
        if (file1Text == null || file2Text == null) {
            JOptionPane.showMessageDialog(this,
                "请先读取两个文件！",
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

                // 直接对比已读取的文本内容
                return BidChecker.compareTexts(file1Text, file2Text);
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
        if (file1 == null || file2 == null) {
            JOptionPane.showMessageDialog(this,
                "请先选择并读取两个文件！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
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
        
        if (file1 == null || file2 == null) {
            JOptionPane.showMessageDialog(this,
                "原始PDF文件引用丢失！\n请重新选择文件并生成查重数据。",
                "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
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

    // 功能: 启动 Swing 应用入口
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BidCheckerGUI gui = new BidCheckerGUI();
            gui.setVisible(true);
        });
    }
}
