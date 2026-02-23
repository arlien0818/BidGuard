# BidGuard 项目介绍

## 一句话概括

BidGuard 是一个招标文档相似度检测工具——输入多份 PDF/Word/Excel 文档，自动两两配对，通过 N-Gram + Jaccard 算法计算文本相似度，定位重复片段在原文中的精确位置（页码+坐标），最终在 PDF 上画出标注框并输出检测报告。

## 整体架构

```
Main → BidCheckerGUI (Swing UI)
         │
         ├─ BatchDuplicateRunner          ← 批量查重编排入口
         │     ├─ PairGenerator           ← 文件两两配对 C(n,2)
         │     ├─ PdfTask.ensureOcr()     ← 懒加载 OCR（DCL 双重检查锁）
         │     │    └─ OcrServiceFactory  ← OCR 引擎选择 + 缓存
         │     ├─ OcrDuplicateDetector    ← 查重检测 + 位置映射
         │     │    └─ BidChecker         ← 核心算法（重点）
         │     └─ PdfAnnotator            ← PDF 标注绘制
         │
         ├─ PdfTextExtractor / PdfTextCleaner  ← 可复制 PDF 直接提取
         ├─ PdfPageRenderer                    ← PDF 页面渲染为图片
         └─ SimilarityConfig                   ← 全局可热重载配置
```

## 核心流程（按执行顺序）

### 1. 文件选择与配对

用户通过 `BidCheckerGUI` 选择多份文件。`PairGenerator.generatePairs()` 对 n 个文件做 $C(n,2)$ 两两组合，生成 `FilePair` 对象列表。

### 2. 文本提取

`BidChecker.compareFiles()` 根据文件后缀分发：
- `.docx` → `readWord()` 用 Apache POI 提取段落和表格文本
- `.pdf` → `readPDF()` 先调 `PdfTextExtractor.extract()` 尝试直接提取；如果判定为扫描件（`isScannedPdf()`：每页平均字符数 < 50），则走 OCR 流程
- `.xlsx` → `readExcel()` 逐 Sheet/Row/Cell 拼接
- `.txt` → 直接读取

文本提取后经 `PdfTextCleaner.clean()` 过滤噪声行（十六进制碎片、页码、纯标点等）。

### 3. OCR 识别（扫描件专用）

`PdfTask.ensureOcr()` 以双重检查锁（DCL）懒加载 OCR 结果。实际调用 `OcrServiceFactory.recognizePdf()`:
- 先查文件缓存（按 PDF 修改时间判断有效性），命中则跳过识别
- 根据 `config.properties` 中 `ocr.type` 配置，选择本地 EasyOCR（`OcrServiceClient`，HTTP 调 `localhost:5001`）或阿里云 OCR
- 识别前由 `PdfPageRenderer.renderPage()` 将 PDF 页渲染为图片，**DPI 从配置文件读取**（`ocr.render.dpi`，默认 200）
- **可选去红章**：如果 `ocr.remove.seal.enabled=true`，则在识别前调用 `SimpleSealRemover.removeSeal()` 去除红色公章（当前默认关闭）
- 识别结果包含每个文字块的 text、confidence、bbox（边界框坐标），缓存到 JSON 文件

### 4. 相似度计算（BidChecker 核心算法）

这是整个项目的核心，全部在 `BidChecker.java` 中实现。

#### 4.1 文本归一化

`normalizeForSimilarity()` — 统一小写，替换不可见字符（NBSP、零宽空格等），压缩多余空白。所有后续算法的输入都先经此处理。

#### 4.2 N-Gram 生成

`shingles(text, n)` — 生成文本的 N-Gram 集合。根据空格占比自动区分语言：
- 中文（空格占比 < 5%）：去掉空格后逐字符滑动窗口，生成字符级 N-Gram
- 英文：按空格分词后生成 Token 级 N-Gram

#### 4.3 Jaccard 相似度

`similarityJaccardNGram(a, b, n)` — 基于 N-Gram 集合计算 Jaccard 系数：$|A \cap B| / |A \cup B| \times 100$。当前主要使用 3-Gram（2-Gram 过于宽松易误报）。

#### 4.4 综合相似度

`enhancedSimilarity()` — 按配置权重融合三个维度的得分：
- **词汇相似度**：`similarityJaccardNGram()`，当前权重 1.0（主要指标）
- **语义相似度**：`calculateTFIDFSimilarity()`，基于 TF 词频向量的**余弦相似度**（当前权重 0，预留）
- **结构相似度**：`calculateStructuralSimilarity()`，比较段落数、文档长度、词汇密度的加权差异（当前权重 0，预留）

`analyzeDocumentSimilarity()` 是完整版，额外输出 `DocumentSimilarityResult`，包含各维度得分和文字分析结论。

#### 4.5 TF-IDF 语义相似度（预留）

`calculateTFIDFSimilarity()` — 先通过 `normalizeForTFIDF()` 做语言感知的文本预处理（中文生成字符二元组作为"词"，英文过滤停用词），再由 `calculateTermFrequency()` 统计词频，最终计算两个 TF 向量的**余弦相似度**。

#### 4.6 动态阈值判定

`getSimilarityLevel()` — 根据文档长度动态选择阈值（大文档 75%、中文档 70%、小文档 65%），输出"高度相似/中等相似/相似度较低"判定。

#### 4.7 段落级匹配

`splitIntoParagraphs()` — 按换行切分，过滤短于 30 字符的行，输出带位置信息的 `Paragraph` 列表。

`matchParagraphs()` — 对两篇文档的段落做笛卡尔积（$M \times N$），逐对计算 3-Gram Jaccard 相似度，超过阈值（默认 85%）的计入 `ParagraphMatchingReport`。

#### 4.8 连续重复片段检测（关键功能）

`findCommonSubstrings(text1, text2, minLength)` — 用**动态规划（DP）**构建最长公共子串矩阵，找出所有长度 ≥ minLength（默认 30 字符）的连续相同片段。通过 `used[]` 数组去除重叠，按位置排序输出 `SubstringMatch` 列表。

`findCrossDocumentSubstrings()` 是其全文版本，忽略段落边界，在整个文档级别查找重复。这是 `OcrDuplicateDetector` 的核心依赖。

### 5. 位置映射与查重报告

`OcrDuplicateDetector.detectDuplicates()` 调用 `BidChecker.findCrossDocumentSubstrings()` 拿到重复片段后，通过 `mapCharRangeToBlocks()` 将字符范围映射回 OCR 文字块的页码和 bbox 坐标，生成包含精确位置的 `DuplicateDetectionResult`，序列化为 JSON。

### 6. PDF 标注

`PdfAnnotator.annotatePdfs()` 读取查重 JSON，将 OCR 坐标动态换算到 PDF 坐标（根据配置的 `ocr.render.dpi` 计算缩放比例），用 PDFBox 在原 PDF 上绘制半透明红色矩形框标记重复区域，输出标注后的 PDF。

### 7. 批量编排

`BatchDuplicateRunner.runBatchDuplicateCheck()` 串联以上全部步骤：生成配对 → OCR → 查重 → JSON → 标注。标注失败不中断批处理。

## 配置系统

`SimilarityConfig` 单例从 `config.properties`（UTF-8）加载所有参数，敏感信息（阿里云 AK）从未纳入版本控制的 `local.properties` 加载。支持 `reload()` 热重载。所有算法阈值、权重、N-Gram 大小等均可通过配置文件调整，无需重新编译。

### 关键配置项

**OCR 渲染参数**：
- `ocr.render.dpi`：PDF 渲染为图片的 DPI，影响识别精度和速度（默认 200）
- `ocr.remove.seal.enabled`：是否在识别前去除红章（默认 false，预留功能）

**OCR 服务配置**：
- `ocr.type`：OCR 引擎类型（local/aliyun）
- `ocr.image.max.dimension`：图片压缩最大边长（默认 1200）
- `ocr.jpeg.quality`：JPEG 压缩质量（默认 0.85）

**相似度算法参数**：
- `substring.min.length`：连续重复片段最小长度（默认 30 字符）
- `paragraph.similarity.threshold`：段落相似度阈值（默认 85%）
- `similarity.weight.*`：综合相似度各维度权重配置

## 最近改进（2026-02-23）

### OCR 流程优化与模块化
- **DPI 配置化**：将硬编码的渲染 DPI 提取为配置项 `ocr.render.dpi`，支持用户根据文档质量动态调整（150-300 DPI）
- **去红章预留接口**：在 `OcrServiceFactory` 中添加可选的去红章步骤，通过 `ocr.remove.seal.enabled` 控制，为将来的去红章功能模块做好准备
- **坐标转换动态化**：`PdfAnnotator` 的 DPI 缩放比例从配置动态计算，消除硬编码，确保标注坐标始终与识别 DPI 一致
- **代码解耦**：确认 `PdfPageRenderer`、`OcrServiceFactory`、`SimpleSealRemover` 等模块完全独立，为独立的去红章功能模块奠定基础

