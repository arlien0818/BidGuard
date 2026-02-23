package com.bidguard.ui;

import com.bidguard.core.*;
import com.bidguard.config.SimilarityConfig;
import com.bidguard.core.OcrDuplicateDetector;
import com.bidguard.ocr.OcrServiceClient;
import com.bidguard.ocr.OcrServiceFactory;
import com.bidguard.pdf.PdfAnnotator;
import com.bidguard.pdf.PdfTask;
import com.bidguard.pdf.PdfTextExtractor;
import com.bidguard.sealremover.SealRemovalService;

import javax.swing.*;
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
    private JTextArea compareResultArea;  // 文本对比结果区域
    private JTextArea annotationLogArea;  // 查重标注日志区域
    private JTextArea previewArea;

    // 文件类型选择
    private JRadioButton txtRadio;
    private JRadioButton pdfRadio;
    private ButtonGroup fileTypeGroup;

    // 文件数据
    private List<File> selectedFiles = new ArrayList<>();
    private Map<File, String> fileTexts = new HashMap<>();
    private File latestDetectionJsonFile;



    private JProgressBar progressBar;
    private JTabbedPane tabbedPane;

    // OCR识别选项卡组件
    private JLabel ocrFileLabel;
    private JButton selectOcrFileButton;
    private JButton executeOcrButton;
    private JButton saveOcrResultButton;
    private JTextArea ocrResultArea;
    private JTextArea ocrConfigArea;
    private File selectedOcrFile;

    // 去红章选项卡组件
    private JLabel sealPdfFileLabel;
    private JComboBox<SealRemovalService.Algorithm> sealAlgorithmCombo;
    private JSpinner sealDpiSpinner;
    private JButton sealSelectFileButton;
    private JButton sealProcessButton;
    private JButton sealRunOcrButton;
    private JButton sealOpenReportButton;
    private JTextArea sealLogArea;
    private JTextArea sealOcrResultArea;
    private File sealSelectedFile;
    private SealRemovalService.RemovalResult sealCurrentResult;

    // 功能: 初始化主界面组件并设置默认文件
    public BidCheckerGUI() {
        setTitle("BidGuard 智能文档处理工具");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 750);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane();

        JPanel comparePanel = createComparePanel();
        tabbedPane.addTab("文件对比", comparePanel);

        JPanel ocrPanel = createOcrPanel();
        tabbedPane.addTab("OCR识别", ocrPanel);

        JPanel sealPanel = createSealRemovalPanel();
        tabbedPane.addTab("去红章", sealPanel);

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

        // 文件类型选择面板
        JPanel fileTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileTypePanel.setBorder(BorderFactory.createTitledBorder("输入文件类型"));

        txtRadio = new JRadioButton("TXT");
        pdfRadio = new JRadioButton("PDF", true);  // 默认选中PDF
        fileTypeGroup = new ButtonGroup();
        fileTypeGroup.add(txtRadio);
        fileTypeGroup.add(pdfRadio);

        // 当切换文件类型时，清空已选文件
        txtRadio.addActionListener(e -> onFileTypeChanged());
        pdfRadio.addActionListener(e -> onFileTypeChanged());

        fileTypePanel.add(txtRadio);
        fileTypePanel.add(pdfRadio);

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

        generateAnnotationDataButton = new JButton("生成查重报告");
        generateAnnotationDataButton.setEnabled(false);
        generateAnnotationDataButton.setToolTipText("TXT/PDF均可：生成详细的查重检测报告（JSON+TXT格式）");
        generateAnnotationDataButton.addActionListener(e -> generateDuplicateReport());

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

        // 组合文件类型选择和按钮面板
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(fileTypePanel, BorderLayout.NORTH);
        topPanel.add(buttonPanel, BorderLayout.CENTER);

        leftPanel.add(topPanel, BorderLayout.NORTH);

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

        // === 底部：分离的结果显示区域 ===
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        // 标注按钮
        annotatePdfButton = new JButton("生成PDF标注");
        annotatePdfButton.setEnabled(false);
        annotatePdfButton.addActionListener(e -> annotatePdfs());
        annotatePdfButton.setPreferredSize(new Dimension(150, 30));
        annotatePdfButton.setToolTipText("仅PDF：根据查重报告在PDF上标注重复位置（需先生成查重报告）");

        JPanel annotatePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        annotatePanel.add(annotatePdfButton);

        // 文本对比结果区域
        compareResultArea = new JTextArea();
        compareResultArea.setEditable(false);
        JScrollPane compareResultScroll = new JScrollPane(compareResultArea);
        compareResultScroll.setBorder(BorderFactory.createTitledBorder("文本对比结果"));

        // 查重标注日志区域
        annotationLogArea = new JTextArea();
        annotationLogArea.setEditable(false);
        JScrollPane annotationLogScroll = new JScrollPane(annotationLogArea);
        annotationLogScroll.setBorder(BorderFactory.createTitledBorder("查重标注日志"));

        // 使用分割面板将两个区域上下分割
        JSplitPane resultSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            compareResultScroll, annotationLogScroll);
        resultSplitPane.setResizeWeight(0.5);
        resultSplitPane.setPreferredSize(new Dimension(0, 200));

        bottomPanel.add(annotatePanel, BorderLayout.NORTH);
        bottomPanel.add(resultSplitPane, BorderLayout.CENTER);

        rightPanel.add(bottomPanel, BorderLayout.SOUTH);

        // === 整体布局：左右分割 ===
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            leftPanel, rightPanel);
        mainSplitPane.setResizeWeight(0.3);
        mainSplitPane.setContinuousLayout(true);

        panel.add(mainSplitPane, BorderLayout.CENTER);

        return panel;
    }

    // 功能: 创建OCR识别选项卡界面布局
    private JPanel createOcrPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // === 顶部：文件选择和配置信息 ===
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));

        // 文件选择面板
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        filePanel.setBorder(BorderFactory.createTitledBorder("PDF文件选择"));

        ocrFileLabel = new JLabel("未选择文件");
        ocrFileLabel.setPreferredSize(new Dimension(400, 25));

        selectOcrFileButton = new JButton("选择PDF文件");
        selectOcrFileButton.addActionListener(e -> selectOcrFile());

        filePanel.add(selectOcrFileButton);
        filePanel.add(ocrFileLabel);

        // 配置信息显示
        ocrConfigArea = new JTextArea(6, 50);
        ocrConfigArea.setEditable(false);
        ocrConfigArea.setBackground(new Color(245, 245, 245));
        JScrollPane configScroll = new JScrollPane(ocrConfigArea);
        configScroll.setBorder(BorderFactory.createTitledBorder("当前OCR配置"));

        // 显示当前配置
        updateOcrConfigDisplay();

        topPanel.add(filePanel, BorderLayout.NORTH);
        topPanel.add(configScroll, BorderLayout.CENTER);

        // === 中间：操作按钮 ===
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));

        executeOcrButton = new JButton("执行OCR识别");
        executeOcrButton.setEnabled(false);
        executeOcrButton.setPreferredSize(new Dimension(150, 35));
        executeOcrButton.addActionListener(e -> executeOcr());

        saveOcrResultButton = new JButton("保存识别结果");
        saveOcrResultButton.setEnabled(false);
        saveOcrResultButton.setPreferredSize(new Dimension(150, 35));
        saveOcrResultButton.addActionListener(e -> saveOcrResult());

        JButton reloadConfigButton = new JButton("重新加载配置");
        reloadConfigButton.setPreferredSize(new Dimension(150, 35));
        reloadConfigButton.addActionListener(e -> {
            SimilarityConfig.reload();
            updateOcrConfigDisplay();
            ocrResultArea.append("\n========================================\n");
            ocrResultArea.append("配置已重新加载\n");
            ocrResultArea.append("========================================\n\n");
        });

        buttonPanel.add(executeOcrButton);
        buttonPanel.add(saveOcrResultButton);
        buttonPanel.add(reloadConfigButton);

        // === 底部：识别结果显示 ===
        ocrResultArea = new JTextArea();
        ocrResultArea.setEditable(false);
        ocrResultArea.setLineWrap(true);
        ocrResultArea.setWrapStyleWord(true);
        JScrollPane resultScroll = new JScrollPane(ocrResultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("OCR识别结果"));
        resultScroll.setPreferredSize(new Dimension(0, 250)); // 设置首选高度，防止挤压按钮区域

        // 初始提示
        ocrResultArea.setText("请选择一个PDF文件，然后执行OCR识别。\n\n" +
                "功能说明：\n" +
                "1. 选择PDF文件后，点击\"执行OCR识别\"开始识别\n" +
                "2. 识别完成后会显示文字内容、字符数、识别块数等信息\n" +
                "3. 可点击\"保存识别结果\"将结果保存到文件\n" +
                "4. 修改config.properties后，可点击\"重新加载配置\"使新配置生效\n\n" +
                "提示：\n" +
                "- 可通过修改config.properties中的ocr.render.dpi调整识别精度（默认200）\n" +
                "- 将ocr.remove.seal.enabled设为true可在识别前去除红章（实验性功能）\n");

        // 组合按钮和结果区到中间面板
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(buttonPanel, BorderLayout.NORTH);
        centerPanel.add(resultScroll, BorderLayout.CENTER);

        // 整体布局
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);

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

    // 功能: 文件类型切换时的处理
    private void onFileTypeChanged() {
        // 清空已选文件
        selectedFiles.clear();
        fileListModel.clear();
        fileTexts.clear();
        latestDetectionJsonFile = null;

        // 清空显示区域
        previewArea.setText("");
        compareResultArea.setText("");
        annotationLogArea.setText("");

        // 重置按钮状态
        readAllFilesButton.setEnabled(false);
        compareButton.setEnabled(false);
        generateAnnotationDataButton.setEnabled(false);
        annotatePdfButton.setEnabled(false);
        batchRunButton.setEnabled(false);

        // 根据模式更新批量按钮文字和提示
        boolean isTxtMode = txtRadio.isSelected();
        if (isTxtMode) {
            batchRunButton.setText("批量执行查重");
            batchRunButton.setToolTipText("对所有生成的配对依次执行查重并保存报告（TXT模式，无标注功能）");
        } else {
            batchRunButton.setText("批量执行查重并标注");
            batchRunButton.setToolTipText("对所有生成的配对依次执行 OCR/查重/保存/标注（会缓存 OCR）");
        }

        // 提示用户
        if (isTxtMode) {
            annotationLogArea.append("已切换到 TXT 模式，请选择文本文件。\n");
            annotationLogArea.append("TXT 模式支持：文本对比、两两查重报告生成、批量查重。\n");
            annotationLogArea.append("不支持：PDF标注功能（TXT文件无位置坐标信息）。\n");
        } else {
            annotationLogArea.append("已切换到 PDF 模式，请选择 PDF 文件。\n");
            annotationLogArea.append("PDF 模式支持全部功能：文本对比、两两查重报告、PDF标注、批量处理。\n");
        }
    }

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

        boolean isTxtMode = txtRadio.isSelected();
        String fileType = isTxtMode ? "TXT" : "PDF";
        String fileExt = isTxtMode ? "txt" : "pdf";

        JFileChooser fileChooser = new JFileChooser(defaultPath);
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
            fileType + " 文件 (*." + fileExt + ")", fileExt);
        fileChooser.setFileFilter(filter);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setDialogTitle("选择标书文件（请选择至少2个" + fileType + "文件）");

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] files = fileChooser.getSelectedFiles();

            // 检查文件数量
            if (files.length < 2) {
                JOptionPane.showMessageDialog(this,
                    "请至少选择2个" + fileType + "文件！\n\n"
                    + "当前只选择了 " + files.length + " 个文件\n\n"
                    + "提示：\n"
                    + (isTxtMode ?
                        "• 选择2个文件可进行对比\n" :
                        "• 选择2个文件可进行对比\n" +
                        "• 选择多个文件可测试配对生成器"),
                    "文件数量不足",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 检查文件格式
            for (File file : files) {
                if (!file.getName().toLowerCase().endsWith("." + fileExt)) {
                    JOptionPane.showMessageDialog(this,
                        "请选择" + fileType + "文件！\n\n"
                        + "文件 '" + file.getName() + "' 不是" + fileType + "格式",
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
            compareResultArea.setText("");
            annotationLogArea.setText("");

            // 更新按钮状态（TXT模式下仅禁用PDF标注功能）
            readAllFilesButton.setEnabled(files.length == 2);
            compareButton.setEnabled(false);
            generateAnnotationDataButton.setEnabled(false);
            annotatePdfButton.setEnabled(false);  // TXT模式下始终禁用
            batchRunButton.setEnabled(files.length >= 2);  // TXT和PDF都支持批量

            LOGGER.info("已选择 " + files.length + " 个" + fileType + "文件");
            for (File file : files) {
                LOGGER.info("  - " + file.getName());
            }

            // 提示用户下一步操作
            if (files.length == 2) {
                annotationLogArea.append("已选择2个文件，可以进行对比操作。\n");
                annotationLogArea.append("请点击\"读取所选文件\"按钮继续。\n");
                if (isTxtMode) {
                    annotationLogArea.append("\n注意：TXT模式支持文本对比和查重报告，不支持PDF标注。\n");
                }
            } else {
                annotationLogArea.append(String.format("已选择%d个文件。\n", files.length));
                annotationLogArea.append("• 可点击\"测试配对生成器\"查看所有可能的配对组合\n");
                annotationLogArea.append("• 可点击\"" + batchRunButton.getText() + "\"进行两两对比\n");
                if (isTxtMode) {
                    annotationLogArea.append("• TXT模式支持批量查重，但不支持PDF标注\n");
                } else {
                    annotationLogArea.append("• 如需单独对比2个文件，请重新选择2个文件\n");
                }
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
                    annotationLogArea.append(msg + "\n");
                }
                annotationLogArea.setCaretPosition(annotationLogArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    get();
                    annotationLogArea.append("\n所有文件读取完成！\n");

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
                    generateAnnotationDataButton.setEnabled(true);  // TXT和PDF都支持查重报告

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "读取文件失败", ex);
                    annotationLogArea.append("\n读取失败: " + ex.getMessage() + "\n");
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
                    compareResultArea.setText(result);
                    compareResultArea.setCaretPosition(0);
                    LOGGER.info("对比完成");
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "对比文件失败", ex);
                    compareResultArea.setText("对比出错：" + ex.getMessage());
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



    // 功能: 生成查重报告（支持TXT和PDF文件）
    private void generateDuplicateReport() {
        if (selectedFiles.size() != 2) {
            JOptionPane.showMessageDialog(this,
                "请先选择两个文件！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File file1 = selectedFiles.get(0);
        File file2 = selectedFiles.get(1);

        // 判断文件类型
        boolean isTxtMode = txtRadio.isSelected();
        String fileExt = isTxtMode ? ".txt" : ".pdf";

        // 检查文件扩展名是否匹配
        if (!file1.getName().toLowerCase().endsWith(fileExt) ||
            !file2.getName().toLowerCase().endsWith(fileExt)) {
            JOptionPane.showMessageDialog(this,
                String.format("请选择两个%s文件！", fileExt.toUpperCase()),
                "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 清空日志区域
        annotationLogArea.setText("");

        progressBar.setIndeterminate(true);
        progressBar.setString("正在生成查重报告...");
        generateAnnotationDataButton.setEnabled(false);

        if (isTxtMode) {
            // TXT模式：生成简化版查重报告
            generateTextDuplicateReport(file1, file2);
        } else {
            // PDF模式：生成完整版查重报告（含bbox信息）
            generatePdfDuplicateReport(file1, file2);
        }
    }

    // 功能: 生成TXT文件的查重报告
    private void generateTextDuplicateReport(File file1, File file2) {
        SwingWorker<File, String> worker = new SwingWorker<>() {
            @Override
            protected File doInBackground() throws Exception {
                LOGGER.info("开始生成TXT查重报告");
                publish("正在读取文本文件...\n");

                // 获取已读取的文本内容
                String text1 = fileTexts.get(file1);
                String text2 = fileTexts.get(file2);

                if (text1 == null || text2 == null) {
                    throw new IOException("文本内容未读取，请先点击'读取所选文件'按钮");
                }

                publish("文档1: " + text1.length() + " 字符\n");
                publish("文档2: " + text2.length() + " 字符\n\n");

                publish("正在执行查重检测...\n");

                // 使用纯文本查重方法（和PDF使用相同的核心算法）
                int minLength = SimilarityConfig.getInstance().substringMinLength;
                OcrDuplicateDetector.DuplicateDetectionResult detection =
                    OcrDuplicateDetector.detectDuplicatesFromText(
                        text1,
                        text2,
                        file1.getName(),
                        file2.getName(),
                        minLength
                    );

                publish("找到 " + detection.totalMatches + " 个重复片段\n");
                publish("Jaccard相似度: " + String.format("%.2f%%", detection.jaccardScore) + "\n");
                publish("增强相似度: " + String.format("%.2f%%", detection.enhancedSimilarityScore) + "\n\n");

                publish("正在保存结果文件...\n");

                // 保存简化版的查重报告
                File jsonFile = OcrDuplicateDetector.saveTextDuplicateResult(
                    detection,
                    file1.getName(),
                    file2.getName()
                );

                publish("查重报告已保存！\n");

                return jsonFile;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    annotationLogArea.append(message);
                }
                annotationLogArea.setCaretPosition(annotationLogArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    File jsonFile = get();

                    annotationLogArea.append("\n" + "=".repeat(60) + "\n");
                    annotationLogArea.append("✓ TXT查重报告生成成功！\n");
                    annotationLogArea.append("=".repeat(60) + "\n\n");
                    annotationLogArea.append("JSON文件: " + jsonFile.getName() + "\n");
                    annotationLogArea.append("文本报告: " + jsonFile.getName().replace(".json", ".txt").replace("duplicate_detection_", "duplicate_report_") + "\n");
                    annotationLogArea.append("保存位置: " + jsonFile.getParent() + "\n\n");
                    annotationLogArea.append("注意：TXT文件查重完成，无法进行PDF标注。\n");

                    LOGGER.info("TXT查重报告生成完成");

                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "TXT查重报告已生成！\n\n" +
                        "文件保存在 output/ 目录下：\n" +
                        "- " + jsonFile.getName() + " (JSON数据)\n" +
                        "- " + jsonFile.getName().replace(".json", ".txt").replace("duplicate_detection_", "duplicate_report_") + " (可读报告)\n\n" +
                        "请查看报告文件了解详细的查重结果。",
                        "成功", JOptionPane.INFORMATION_MESSAGE);

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "生成TXT查重报告失败", ex);
                    annotationLogArea.append("\n✗ 生成失败: " + ex.getMessage() + "\n");
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "生成查重报告失败:\n" + ex.getMessage(),
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

    // 功能: 生成PDF文件的查重报告
    private void generatePdfDuplicateReport(File file1, File file2) {
        SwingWorker<File, String> worker = new SwingWorker<>() {
            @Override
            protected File doInBackground() throws Exception {
                LOGGER.info("开始生成PDF查重报告");
                publish("正在获取OCR识别结果...\n");

                // 获取两个PDF的OCR结果
                OcrServiceClient.OcrResult ocrResult1 = OcrServiceFactory.recognizePdf(file1);
                publish("文档1 OCR完成: " + ocrResult1.textCount + " 个文字块\n");

                OcrServiceClient.OcrResult ocrResult2 = OcrServiceFactory.recognizePdf(file2);
                publish("文档2 OCR完成: " + ocrResult2.textCount + " 个文字块\n");

                publish("\n正在执行查重检测...\n");

                // 执行查重检测（和TXT使用相同的核心算法）
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
                publish("Jaccard相似度: " + String.format("%.2f%%", detection.jaccardScore) + "\n");
                publish("增强相似度: " + String.format("%.2f%%", detection.enhancedSimilarityScore) + "\n\n");

                publish("正在保存结果文件...\n");

                // 保存结果到JSON文件（含bbox信息）
                File jsonFile = OcrDuplicateDetector.saveResultToJson(
                    detection,
                    file1.getName(),
                    file2.getName()
                );

                publish("查重报告已保存！\n");

                return jsonFile;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    annotationLogArea.append(message);
                }
                annotationLogArea.setCaretPosition(annotationLogArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    File jsonFile = get();

                    // 保存JSON文件引用，供标注功能使用
                    latestDetectionJsonFile = jsonFile;

                    annotationLogArea.append("\n" + "=".repeat(60) + "\n");
                    annotationLogArea.append("✓ PDF查重报告生成成功！\n");
                    annotationLogArea.append("=".repeat(60) + "\n\n");
                    annotationLogArea.append("JSON文件: " + jsonFile.getName() + "\n");
                    annotationLogArea.append("文本报告: " + jsonFile.getName().replace(".json", ".txt").replace("duplicate_detection_", "duplicate_report_") + "\n");
                    annotationLogArea.append("保存位置: " + jsonFile.getParent() + "\n\n");
                    annotationLogArea.append("下一步：如需在PDF上标注，请点击'生成PDF标注'按钮。\n");

                    LOGGER.info("PDF查重报告生成完成");

                    // 启用标注按钮
                    annotatePdfButton.setEnabled(true);

                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "PDF查重报告已生成！\n\n" +
                        "文件保存在 output/ 目录下：\n" +
                        "- " + jsonFile.getName() + " (JSON数据)\n" +
                        "- " + jsonFile.getName().replace(".json", ".txt").replace("duplicate_detection_", "duplicate_report_") + " (可读报告)\n\n" +
                        "如需在PDF上标注重复位置，请点击'生成PDF标注'按钮。",
                        "成功", JOptionPane.INFORMATION_MESSAGE);

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "生成PDF查重报告失败", ex);
                    annotationLogArea.append("\n✗ 生成失败: " + ex.getMessage() + "\n");
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "生成查重报告失败:\n" + ex.getMessage(),
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
                "未找到查重检测结果！\n请先点击'生成查重报告'按钮生成PDF查重报告。",
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
                    annotationLogArea.append(message);
                }
                annotationLogArea.setCaretPosition(annotationLogArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    PdfAnnotator.AnnotationResult result = get();

                    annotationLogArea.append("\n" + "=".repeat(60) + "\n");
                    annotationLogArea.append("✓ PDF标注完成！\n");
                    annotationLogArea.append("=".repeat(60) + "\n\n");
                    annotationLogArea.append("文档1标注文件:\n");
                    annotationLogArea.append("  " + result.annotatedFile1.getName() + "\n");
                    annotationLogArea.append("  标注区域: " + result.totalAnnotations1 + " 个\n\n");
                    annotationLogArea.append("文档2标注文件:\n");
                    annotationLogArea.append("  " + result.annotatedFile2.getName() + "\n");
                    annotationLogArea.append("  标注区域: " + result.totalAnnotations2 + " 个\n\n");
                    annotationLogArea.append("文件位置: " + result.annotatedFile1.getParent() + "\n");
                    annotationLogArea.append("=".repeat(60) + "\n");

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
                    annotationLogArea.append("\n✗ 标注失败: " + ex.getMessage() + "\n");

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
        annotationLogArea.setText("");

        if (selectedFiles.isEmpty()) {
            annotationLogArea.append("❌ 请先选择文件！\n\n");
            annotationLogArea.append("点击\"选择标书文件\"按钮，选择多个PDF文件（建议3-5个）。\n");
            JOptionPane.showMessageDialog(this,
                "请先选择文件！\n\n请点击\"选择标书文件\"按钮选择多个PDF文件。",
                "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (selectedFiles.size() < 2) {
            annotationLogArea.append("❌ 至少需要2个文件才能生成配对！\n\n");
            annotationLogArea.append("当前选择: " + selectedFiles.size() + " 个文件\n");
            JOptionPane.showMessageDialog(this,
                "至少需要2个文件才能生成配对！\n\n当前只选择了 " + selectedFiles.size() + " 个文件。",
                "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        annotationLogArea.append("=".repeat(60) + "\n");
        annotationLogArea.append("配对生成器测试\n");
        annotationLogArea.append("=".repeat(60) + "\n\n");

        // 显示输入文件
        annotationLogArea.append("📁 输入文件列表 (" + selectedFiles.size() + " 个):\n");
        for (int i = 0; i < selectedFiles.size(); i++) {
            File file = selectedFiles.get(i);
            annotationLogArea.append(String.format("  [%d] %s\n", i + 1, file.getName()));
        }
        annotationLogArea.append("\n");

        // 计算预期配对数量
        int expectedPairCount = PairGenerator.calculatePairCount(selectedFiles.size());
        annotationLogArea.append(String.format("📊 预期生成配对数: C(%d,2) = %d\n\n",
            selectedFiles.size(), expectedPairCount));

        // 生成配对
        LOGGER.info("调用 PairGenerator.generatePairs() 生成配对...");
        List<FilePair> pairs = PairGenerator.generatePairs(selectedFiles);

        // 显示生成结果
        annotationLogArea.append("=".repeat(60) + "\n");
        annotationLogArea.append("✅ 配对生成结果\n");
        annotationLogArea.append("=".repeat(60) + "\n\n");
        annotationLogArea.append(String.format("实际生成配对数: %d\n\n", pairs.size()));

        // 显示所有配对
        annotationLogArea.append("配对详情:\n");
        annotationLogArea.append("-".repeat(60) + "\n");
        for (int i = 0; i < pairs.size(); i++) {
            FilePair pair = pairs.get(i);
            annotationLogArea.append(String.format("配对 #%-2d: %s\n",
                i + 1, pair.toString()));
            annotationLogArea.append(String.format("          ↳ [%s]\n",
                pair.getFileA().getName()));
            annotationLogArea.append(String.format("          ↳ [%s]\n",
                pair.getFileB().getName()));
            if (i < pairs.size() - 1) {
                annotationLogArea.append("\n");
            }
        }
        annotationLogArea.append("-".repeat(60) + "\n\n");

        // 验证结果
        boolean testPassed = (pairs.size() == expectedPairCount);
        if (testPassed) {
            annotationLogArea.append("✅ 测试通过！配对数量正确。\n\n");
        } else {
            annotationLogArea.append(String.format("❌ 测试失败！预期 %d 个配对，实际生成 %d 个。\n\n",
                expectedPairCount, pairs.size()));
        }

        // 算法说明
        annotationLogArea.append("=".repeat(60) + "\n");
        annotationLogArea.append("算法说明\n");
        annotationLogArea.append("=".repeat(60) + "\n");
        annotationLogArea.append("核心逻辑：双层循环\n\n");
        annotationLogArea.append("for (int i = 0; i < files.size(); i++) {\n");
        annotationLogArea.append("    for (int j = i + 1; j < files.size(); j++) {\n");
        annotationLogArea.append("        pairs.add(new FilePair(files.get(i), files.get(j)));\n");
        annotationLogArea.append("    }\n");
        annotationLogArea.append("}\n\n");
        annotationLogArea.append("特点：\n");
        annotationLogArea.append("  • 避免重复（不会生成 A vs B 又生成 B vs A）\n");
        annotationLogArea.append("  • 避免自我配对（不会生成 A vs A）\n");
        annotationLogArea.append("  • 保证顺序（i < j）\n\n");

        // 滚动到顶部
        annotationLogArea.setCaretPosition(0);

        LOGGER.info("配对生成器测试完成，生成 " + pairs.size() + " 个配对");

        // 显示弹窗
        JOptionPane.showMessageDialog(this,
            String.format("配对生成器测试完成！\n\n" +
                "从 %d 个文件生成了 %d 个配对组合\n\n" +
                "请查看结果区域了解详细信息。",
                selectedFiles.size(), pairs.size()),
            "测试完成", JOptionPane.INFORMATION_MESSAGE);
    }

    // 功能: 批量执行查重并标注（支持TXT和PDF）
    private void runBatchDuplicateAndAnnotate() {
        if (selectedFiles.size() < 2) {
            String fileType = txtRadio.isSelected() ? "TXT" : "PDF";
            JOptionPane.showMessageDialog(this, "请至少选择2个" + fileType + "文件！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean isTxtMode = txtRadio.isSelected();

        // 清空结果区并开始任务
        annotationLogArea.setText("");
        progressBar.setIndeterminate(true);
        progressBar.setString("正在批量执行查重" + (isTxtMode ? "..." : "并标注..."));

        // 禁用相关按钮
        batchRunButton.setEnabled(false);
        selectFilesButton.setEnabled(false);
        readAllFilesButton.setEnabled(false);

        if (isTxtMode) {
            // TXT模式：批量查重（不涉及OCR和PDF标注）
            runBatchTxtDuplicateCheck();
        } else {
            // PDF模式：批量查重并标注
            runBatchPdfDuplicateAndAnnotate();
        }
    }

    // 功能: 批量执行TXT文件查重
    private void runBatchTxtDuplicateCheck() {
        SwingWorker<Map<FilePair, File>, String> worker = new SwingWorker<>() {
            @Override
            protected Map<FilePair, File> doInBackground() {
                publish("开始批量TXT查重处理...\n\n");

                // 生成所有配对
                List<FilePair> pairs = PairGenerator.generatePairs(selectedFiles);
                publish(String.format("生成 %d 个文件配对\n\n", pairs.size()));

                Map<FilePair, File> resultMap = new LinkedHashMap<>();
                int minLength = SimilarityConfig.getInstance().substringMinLength;

                for (int i = 0; i < pairs.size(); i++) {
                    FilePair pair = pairs.get(i);
                    File fileA = pair.getFileA();
                    File fileB = pair.getFileB();

                    publish(String.format("=".repeat(60) + "\n"));
                    publish(String.format("处理配对 %d/%d: %s\n", i + 1, pairs.size(), pair.toString()));
                    publish(String.format("=".repeat(60) + "\n"));

                    try {
                        // 读取TXT文件内容
                        publish("读取文件 A: " + fileA.getName() + "...\n");
                        String textA = readTextFile(fileA);
                        publish(String.format("  文件 A: %d 字符\n", textA.length()));

                        publish("读取文件 B: " + fileB.getName() + "...\n");
                        String textB = readTextFile(fileB);
                        publish(String.format("  文件 B: %d 字符\n\n", textB.length()));

                        // 执行查重检测
                        publish("执行查重检测...\n");
                        OcrDuplicateDetector.DuplicateDetectionResult detection =
                            OcrDuplicateDetector.detectDuplicatesFromText(
                                textA, textB,
                                fileA.getName(), fileB.getName(),
                                minLength
                            );

                        publish(String.format("  重复片段: %d 个\n", detection.totalMatches));
                        publish(String.format("  Jaccard相似度: %.2f%%\n", detection.jaccardScore));
                        publish(String.format("  增强相似度: %.2f%%\n\n", detection.enhancedSimilarityScore));

                        // 保存结果
                        publish("保存查重报告...\n");
                        File jsonFile = OcrDuplicateDetector.saveTextDuplicateResult(
                            detection,
                            fileA.getName(),
                            fileB.getName()
                        );

                        publish("  JSON文件: " + jsonFile.getName() + "\n");
                        publish("  TXT报告: " + jsonFile.getName().replace(".json", ".txt").replace("duplicate_detection_", "duplicate_report_") + "\n\n");

                        resultMap.put(pair, jsonFile);

                    } catch (Exception ex) {
                        LOGGER.log(Level.SEVERE, "处理配对失败: " + pair.toString(), ex);
                        publish("  ✗ 失败: " + ex.getMessage() + "\n\n");
                    }
                }

                publish(String.format("\n批量处理完成！共生成 %d 个查重报告\n", resultMap.size()));
                return resultMap;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String m : chunks) {
                    annotationLogArea.append(m);
                }
                annotationLogArea.setCaretPosition(annotationLogArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    Map<FilePair, File> map = get();
                    annotationLogArea.append("\n" + "=".repeat(60) + "\n");
                    annotationLogArea.append("处理汇总\n");
                    annotationLogArea.append("=".repeat(60) + "\n");
                    for (Map.Entry<FilePair, File> e : map.entrySet()) {
                        annotationLogArea.append(String.format("%s\n  -> %s\n",
                            e.getKey().toString(), e.getValue().getName()));
                    }
                    annotationLogArea.append("=".repeat(60) + "\n");

                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        String.format("批量TXT查重完成！\n\n共生成 %d 个查重报告\n\n报告保存在 output/ 目录", map.size()),
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

    // 功能: 批量执行PDF文件查重并标注
    private void runBatchPdfDuplicateAndAnnotate() {
        // 构造 PdfTask 列表
        List<PdfTask> tasks = new ArrayList<>();
        for (File f : selectedFiles) tasks.add(new PdfTask(f));

        SwingWorker<Map<FilePair, File>, String> worker = new SwingWorker<>() {
            @Override
            protected Map<FilePair, File> doInBackground() {
                publish("开始批量PDF处理...\n");
                Map<FilePair, File> results = BatchDuplicateRunner.runBatchDuplicateCheck(tasks);
                publish(String.format("批量处理完成，生成 %d 个 JSON 结果。\n", results.size()));
                return results;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String m : chunks) {
                    annotationLogArea.append(m);
                }
                annotationLogArea.setCaretPosition(annotationLogArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    Map<FilePair, File> map = get();
                    annotationLogArea.append("\n处理汇总:\n");
                    annotationLogArea.append("-".repeat(60) + "\n");
                    for (Map.Entry<FilePair, File> e : map.entrySet()) {
                        annotationLogArea.append(String.format("%s -> %s\n", e.getKey().toString(), e.getValue().getName()));
                    }
                    annotationLogArea.append("-".repeat(60) + "\n");
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

    // 功能: 读取TXT文件内容
    private String readTextFile(File file) throws IOException {
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

    // ========== OCR识别相关方法 ==========

    // 功能: 更新OCR配置显示
    private void updateOcrConfigDisplay() {
        SimilarityConfig config = SimilarityConfig.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("OCR引擎类型: ").append(config.ocrType.toUpperCase()).append("\n");
        sb.append("PDF渲染DPI: ").append(config.ocrRenderDpi).append(" (影响识别精度，推荐150-300)\n");
        sb.append("去红章功能: ").append(config.ocrRemoveSealEnabled ? "开启" : "关闭").append("\n");
        sb.append("图片压缩最大边长: ").append(config.ocrImageMaxDimension).append("px\n");
        sb.append("JPEG压缩质量: ").append(String.format("%.2f", config.ocrJpegQuality)).append("\n");

        if ("aliyun".equalsIgnoreCase(config.ocrType)) {
            sb.append("\n阿里云OCR配置:\n");
            sb.append("  - API端点: ").append(config.ocrAliyunEndpoint).append("\n");
            sb.append("  - AccessKey: ").append(config.ocrAliyunAccessKeyId.isEmpty() ? "未配置" : "已配置").append("\n");
        } else {
            sb.append("\n本地EasyOCR配置:\n");
            sb.append("  - 服务地址: ").append(config.ocrLocalUrl).append("\n");
        }

        ocrConfigArea.setText(sb.toString());
    }

    // 功能: 选择OCR识别的PDF文件
    private void selectOcrFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(getTestFilesDirectory());
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF文件", "pdf"));
        fileChooser.setDialogTitle("选择要进行OCR识别的PDF文件");

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedOcrFile = fileChooser.getSelectedFile();
            ocrFileLabel.setText(selectedOcrFile.getName());
            executeOcrButton.setEnabled(true);

            ocrResultArea.append("\n========================================\n");
            ocrResultArea.append("已选择文件: " + selectedOcrFile.getName() + "\n");
            ocrResultArea.append("文件大小: " + String.format("%.2f MB", selectedOcrFile.length() / (1024.0 * 1024.0)) + "\n");
            ocrResultArea.append("========================================\n\n");
        }
    }

    // 功能: 执行OCR识别
    private void executeOcr() {
        if (selectedOcrFile == null || !selectedOcrFile.exists()) {
            JOptionPane.showMessageDialog(this, "请先选择一个PDF文件", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        executeOcrButton.setEnabled(false);
        saveOcrResultButton.setEnabled(false);
        progressBar.setString("正在执行OCR识别...");
        progressBar.setIndeterminate(true);

        SwingWorker<OcrServiceClient.OcrResult, String> worker = new SwingWorker<>() {
            @Override
            protected OcrServiceClient.OcrResult doInBackground() throws Exception {
                publish("开始OCR识别: " + selectedOcrFile.getName() + "\n");
                publish("识别配置: DPI=" + SimilarityConfig.getInstance().ocrRenderDpi +
                       ", 去红章=" + (SimilarityConfig.getInstance().ocrRemoveSealEnabled ? "是" : "否") + "\n");
                publish("请稍候，正在处理...\n\n");

                long startTime = System.currentTimeMillis();
                OcrServiceClient.OcrResult result = OcrServiceFactory.recognizePdf(selectedOcrFile);
                long elapsed = System.currentTimeMillis() - startTime;

                publish("\n识别完成！耗时: " + (elapsed / 1000.0) + " 秒\n");

                return result;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    ocrResultArea.append(msg);
                }
                ocrResultArea.setCaretPosition(ocrResultArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    OcrServiceClient.OcrResult result = get();

                    // 计算平均置信度（在外层作用域定义）
                    double avgConfidence = result.texts.stream()
                        .mapToDouble(t -> t.confidence)
                        .average()
                        .orElse(0.0);

                    ocrResultArea.append("\n" + "=".repeat(60) + "\n");
                    ocrResultArea.append("识别结果统计\n");
                    ocrResultArea.append("=".repeat(60) + "\n");
                    ocrResultArea.append("识别引擎: " + result.engine + "\n");
                    ocrResultArea.append("识别状态: " + (result.success ? "成功" : "失败") + "\n");
                    ocrResultArea.append("总页数: " + result.pageCount + "\n");
                    ocrResultArea.append("文字块数量: " + result.texts.size() + "\n");
                    ocrResultArea.append("总字符数: " + result.fullText.length() + "\n");

                    if (!result.success) {
                        ocrResultArea.append("错误信息: " + result.error + "\n");
                    } else {
                        ocrResultArea.append("平均置信度: " + String.format("%.2f%%", avgConfidence * 100) + "\n");
                    }
                    ocrResultArea.append("=".repeat(60) + "\n\n");

                    if (result.success && result.hasText()) {
                        ocrResultArea.append("识别文本内容（前500字符预览）:\n");
                        ocrResultArea.append("-".repeat(60) + "\n");
                        String preview = result.fullText.length() > 500 ?
                            result.fullText.substring(0, 500) + "..." : result.fullText;
                        ocrResultArea.append(preview + "\n");
                        ocrResultArea.append("-".repeat(60) + "\n\n");

                        saveOcrResultButton.setEnabled(true);

                        JOptionPane.showMessageDialog(BidCheckerGUI.this,
                            String.format("OCR识别成功！\n\n总字符数: %d\n文字块数: %d\n平均置信度: %.2f%%",
                                result.fullText.length(), result.texts.size(), avgConfidence * 100),
                            "识别完成", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(BidCheckerGUI.this,
                            "OCR识别失败: " + result.error,
                            "错误", JOptionPane.ERROR_MESSAGE);
                    }

                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "OCR识别异常", ex);
                    ocrResultArea.append("\n[错误] OCR识别异常: " + ex.getMessage() + "\n");
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "OCR识别异常: " + ex.getMessage(),
                        "错误", JOptionPane.ERROR_MESSAGE);
                } finally {
                    executeOcrButton.setEnabled(true);
                    progressBar.setString("就绪");
                    progressBar.setIndeterminate(false);
                }
            }
        };

        worker.execute();
    }

    // 功能: 保存OCR识别结果
    private void saveOcrResult() {
        if (selectedOcrFile == null) {
            return;
        }

        try {
            // 查找缓存的OCR结果文件
            File outputDir = new File("output");
            String baseName = selectedOcrFile.getName().replaceAll("(?i)\\.pdf$", "");
            File cacheFile = new File(outputDir, baseName + "_ocr_cache.json");

            if (cacheFile.exists()) {
                // 打开文件所在目录
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();
                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                        desktop.open(outputDir);
                    }
                }

                JOptionPane.showMessageDialog(this,
                    "OCR识别结果已缓存到:\n" + cacheFile.getAbsolutePath() + "\n\n已打开output目录",
                    "保存成功", JOptionPane.INFORMATION_MESSAGE);

                ocrResultArea.append("\n[提示] 识别结果已保存到: " + cacheFile.getName() + "\n");
            } else {
                JOptionPane.showMessageDialog(this,
                    "未找到OCR缓存文件，请先执行OCR识别",
                    "提示", JOptionPane.WARNING_MESSAGE);
            }

        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "打开输出目录失败", ex);
            JOptionPane.showMessageDialog(this,
                "无法打开输出目录: " + ex.getMessage(),
                "警告", JOptionPane.WARNING_MESSAGE);
        }
    }

    // =========================================================================
    // 去红章标签页
    // =========================================================================

    // 功能: 构建去红章选项卡界面布局
    private JPanel createSealRemovalPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // === 左侧控制面板 ===
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(280, 0));

        // 文件选择
        JPanel filePanel = new JPanel(new BorderLayout(5, 5));
        filePanel.setBorder(BorderFactory.createTitledBorder("输入PDF（扫描件）"));

        sealPdfFileLabel = new JLabel("未选择文件");
        sealPdfFileLabel.setFont(sealPdfFileLabel.getFont().deriveFont(Font.PLAIN, 12f));
        sealSelectFileButton = new JButton("选择 PDF");
        sealSelectFileButton.addActionListener(e -> sealSelectFile());

        JPanel fileLabelPanel = new JPanel(new BorderLayout(3, 0));
        fileLabelPanel.add(sealSelectFileButton, BorderLayout.WEST);
        fileLabelPanel.add(sealPdfFileLabel, BorderLayout.CENTER);
        filePanel.add(fileLabelPanel, BorderLayout.NORTH);

        // 算法选择
        JPanel algoPanel = new JPanel(new BorderLayout(5, 5));
        algoPanel.setBorder(BorderFactory.createTitledBorder("去章算法"));
        sealAlgorithmCombo = new JComboBox<>(SealRemovalService.Algorithm.values());
        sealAlgorithmCombo.setSelectedItem(SealRemovalService.Algorithm.DOCUMENT);
        algoPanel.add(sealAlgorithmCombo, BorderLayout.CENTER);

        JTextArea algoDesc = new JTextArea(
            "DOCUMENT：HSV色彩空间+形态学，最佳综合效果\n" +
            "PRECISE：先定位印章区域再去除，精度最高\n" +
            "SIMPLE：直接替换红色像素，速度最快");
        algoDesc.setEditable(false);
        algoDesc.setLineWrap(true);
        algoDesc.setWrapStyleWord(true);
        algoDesc.setFont(algoDesc.getFont().deriveFont(Font.PLAIN, 11f));
        algoDesc.setBackground(new Color(248, 248, 248));
        algoDesc.setBorder(null);
        algoPanel.add(algoDesc, BorderLayout.SOUTH);

        // DPI 设置
        JPanel dpiPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        dpiPanel.setBorder(BorderFactory.createTitledBorder("渲染DPI（越高越慢越清晰）"));
        sealDpiSpinner = new JSpinner(new SpinnerNumberModel(200, 72, 400, 50));
        sealDpiSpinner.setPreferredSize(new Dimension(80, 28));
        dpiPanel.add(new JLabel("DPI:"));
        dpiPanel.add(sealDpiSpinner);
        dpiPanel.add(new JLabel("  推荐 200（OCR前送去200即可）"));

        // 操作按钮
        JPanel btnPanel = new JPanel(new GridLayout(3, 1, 5, 8));
        btnPanel.setBorder(BorderFactory.createTitledBorder("操作"));

        sealProcessButton = new JButton("① 执行去章 + 保存PNG");
        sealProcessButton.setEnabled(false);
        sealProcessButton.setToolTipText("渲染PDF每页 → 去除红章 → 保存原图/去章PNG → 生成HTML对比报告");
        sealProcessButton.addActionListener(e -> sealProcess());

        sealRunOcrButton = new JButton("② 送阿里云OCR识别");
        sealRunOcrButton.setEnabled(false);
        sealRunOcrButton.setToolTipText("将去章后的图像送阿里云OCR，识别文字内容");
        sealRunOcrButton.addActionListener(e -> sealRunOcr());

        sealOpenReportButton = new JButton("打开HTML对比报告");
        sealOpenReportButton.setEnabled(false);
        sealOpenReportButton.setToolTipText("在浏览器中打开原图/去章图并排对比的HTML报告");
        sealOpenReportButton.addActionListener(e -> sealOpenReport());

        btnPanel.add(sealProcessButton);
        btnPanel.add(sealRunOcrButton);
        btnPanel.add(sealOpenReportButton);

        // 拼装左侧
        JPanel leftTop = new JPanel(new BorderLayout(5, 5));
        leftTop.add(filePanel, BorderLayout.NORTH);
        leftTop.add(algoPanel, BorderLayout.CENTER);

        JPanel leftBottom = new JPanel(new BorderLayout(5, 8));
        leftBottom.add(dpiPanel, BorderLayout.NORTH);
        leftBottom.add(btnPanel, BorderLayout.CENTER);

        leftPanel.add(leftTop, BorderLayout.NORTH);
        leftPanel.add(leftBottom, BorderLayout.SOUTH);

        // === 右侧：日志区 + OCR结果区 ===
        sealLogArea = new JTextArea();
        sealLogArea.setEditable(false);
        sealLogArea.setLineWrap(true);
        sealLogArea.setWrapStyleWord(true);
        sealLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(sealLogArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("处理日志"));

        sealOcrResultArea = new JTextArea();
        sealOcrResultArea.setEditable(false);
        sealOcrResultArea.setLineWrap(true);
        sealOcrResultArea.setWrapStyleWord(true);
        JScrollPane ocrScroll = new JScrollPane(sealOcrResultArea);
        ocrScroll.setBorder(BorderFactory.createTitledBorder("OCR识别结果（去章后）"));

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, logScroll, ocrScroll);
        rightSplit.setResizeWeight(0.55);
        rightSplit.setContinuousLayout(true);

        // 整体
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setContinuousLayout(true);

        panel.add(mainSplit, BorderLayout.CENTER);

        // 初始提示
        sealLogArea.setText(
            "使用说明：\n" +
            "1. 选择一个扫描件 PDF（含红章）\n" +
            "2. 选择去章算法（推荐 DOCUMENT）\n" +
            "3. 点击「① 执行去章 + 保存PNG」\n" +
            "   → 每页生成 page_XXX_original.png 和 page_XXX_no_seal.png\n" +
            "   → 生成 HTML 对比报告（原图/去章图并排）\n" +
            "4. 人工查看 HTML 报告确认效果\n" +
            "5. 点击「② 送阿里云OCR识别」获取文字内容\n\n" +
            "中间文件输出目录：output/seal_removal_<文件名>_<时间戳>/\n"
        );

        return panel;
    }

    // 功能: 去章标签页 - 选择PDF文件
    private void sealSelectFile() {
        JFileChooser fc = new JFileChooser(getTestFilesDirectory());
        fc.setFileFilter(new FileNameExtensionFilter("PDF 文件", "pdf"));
        fc.setDialogTitle("选择扫描件 PDF");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            sealSelectedFile = fc.getSelectedFile();
            sealPdfFileLabel.setText(sealSelectedFile.getName());
            sealPdfFileLabel.setToolTipText(sealSelectedFile.getAbsolutePath());
            sealProcessButton.setEnabled(true);
            sealRunOcrButton.setEnabled(false);
            sealOpenReportButton.setEnabled(false);
            sealCurrentResult = null;
            sealLogArea.setText("已选择: " + sealSelectedFile.getAbsolutePath() + "\n\n点击「① 执行去章 + 保存PNG」开始处理。\n");
            sealOcrResultArea.setText("");
        }
    }

    // 功能: 去章标签页 - 执行去章主流程
    private void sealProcess() {
        if (sealSelectedFile == null || !sealSelectedFile.exists()) {
            JOptionPane.showMessageDialog(this, "请先选择PDF文件", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SealRemovalService.Algorithm algo =
            (SealRemovalService.Algorithm) sealAlgorithmCombo.getSelectedItem();
        int dpi = (Integer) sealDpiSpinner.getValue();

        sealProcessButton.setEnabled(false);
        sealRunOcrButton.setEnabled(false);
        sealOpenReportButton.setEnabled(false);
        sealLogArea.setText("");
        sealOcrResultArea.setText("");
        progressBar.setIndeterminate(true);
        progressBar.setString("去章处理中...");

        File pdfFile = sealSelectedFile;

        SwingWorker<SealRemovalService.RemovalResult, String> worker = new SwingWorker<>() {
            @Override
            protected SealRemovalService.RemovalResult doInBackground() throws Exception {
                return SealRemovalService.processPages(pdfFile, algo, dpi, msg -> publish(msg + "\n"));
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String s : chunks) sealLogArea.append(s);
                sealLogArea.setCaretPosition(sealLogArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    sealCurrentResult = get();
                    sealLogArea.append("\n✅ 处理完成！\n");
                    sealLogArea.append("共 " + sealCurrentResult.pages.size() + " 页\n");
                    sealLogArea.append("输出目录: " + sealCurrentResult.outputDir.getAbsolutePath() + "\n");
                    sealLogArea.append("HTML报告: " + sealCurrentResult.htmlReport.getName() + "\n\n");
                    sealLogArea.append("查看 HTML 报告确认效果后，可点击「② 送阿里云OCR识别」。\n");

                    sealRunOcrButton.setEnabled(true);
                    sealOpenReportButton.setEnabled(true);

                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        String.format("去章完成！共 %d 页\n\n中间文件保存在：\n%s",
                            sealCurrentResult.pages.size(),
                            sealCurrentResult.outputDir.getAbsolutePath()),
                        "完成", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "去章处理失败", ex);
                    sealLogArea.append("\n❌ 处理失败: " + ex.getMessage() + "\n");
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "去章处理失败:\n" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                } finally {
                    sealProcessButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("就绪");
                }
            }
        };
        worker.execute();
    }

    // 功能: 去章标签页 - 送 OCR
    private void sealRunOcr() {
        if (sealCurrentResult == null) {
            JOptionPane.showMessageDialog(this, "请先执行去章处理", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        sealRunOcrButton.setEnabled(false);
        sealOcrResultArea.setText("");
        progressBar.setIndeterminate(true);
        progressBar.setString("OCR识别中...");

        SealRemovalService.RemovalResult result = sealCurrentResult;

        SwingWorker<String, String> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return SealRemovalService.runOcr(result, msg -> publish(msg + "\n"));
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String s : chunks) sealLogArea.append(s);
                sealLogArea.setCaretPosition(sealLogArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    String text = get();
                    sealOcrResultArea.setText(text);
                    sealOcrResultArea.setCaretPosition(0);

                    // 保存 OCR 结果到文件
                    File ocrOut = new File(sealCurrentResult.outputDir,
                        sealSelectedFile.getName().replaceAll("(?i)\\.pdf$", "") + "_ocr_result.txt");
                    try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(ocrOut),
                            java.nio.charset.StandardCharsets.UTF_8)) {
                        w.write(text);
                    }
                    sealLogArea.append("\nOCR文本已保存: " + ocrOut.getName() + "\n");

                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "OCR识别完成！\n共 " + sealCurrentResult.pages.size() + " 页\n" +
                        "识别文本已保存至:\n" + ocrOut.getAbsolutePath(),
                        "OCR完成", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "OCR失败", ex);
                    sealLogArea.append("\n❌ OCR失败: " + ex.getMessage() + "\n");
                    JOptionPane.showMessageDialog(BidCheckerGUI.this,
                        "OCR失败:\n" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                } finally {
                    sealRunOcrButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("就绪");
                }
            }
        };
        worker.execute();
    }

    // 功能: 去章标签页 - 在浏览器打开HTML对比报告
    private void sealOpenReport() {
        if (sealCurrentResult == null || sealCurrentResult.htmlReport == null
                || !sealCurrentResult.htmlReport.exists()) {
            JOptionPane.showMessageDialog(this, "HTML报告不存在，请先执行去章处理", "提示",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(sealCurrentResult.htmlReport.toURI());
            } else {
                JOptionPane.showMessageDialog(this,
                    "请手动用浏览器打开:\n" + sealCurrentResult.htmlReport.getAbsolutePath(),
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "打开失败:\n" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    // 功能: 启动 Swing 应用入口
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BidCheckerGUI gui = new BidCheckerGUI();
            gui.setVisible(true);
        });
    }
}
