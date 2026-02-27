# BidGuard 项目介绍

## 一句话概括
BidGuard 是一个智能文档查重与标注工具，支持 PDF、Word、Excel、TXT 多格式，自动两两配对，精准检测重复片段并在 PDF 上高亮标注，适用于招标文件、合同等场景。

## 主要功能
- 多文件批量查重（支持 n 份文件，两两配对 $C(n,2)$）
- 智能文本提取（自动判别扫描件/可提取文本 PDF，支持 OCR）
- N-Gram + Jaccard 相似度算法，定位重复片段
- 连续重复片段检测（动态规划，支持字符级定位）
- 查重结果报告（JSON+TXT，含页码、坐标、置信度等）
- PDF自动标注（高亮重复区域，输出新PDF）
- 可配置的 OCR 流程（支持 EasyOCR/阿里云，DPI/去红章可调）
- GUI 界面（Swing），支持批量操作、进度显示、配置热重载

## 技术架构
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

## 核心流程
1. 文件选择与配对（支持多文件批量查重）
2. 文本提取（自动判别扫描件/Word转PDF/Excel/TXT）
3. OCR识别（扫描件自动走OCR，支持去红章预处理）
4. 相似度计算（N-Gram、Jaccard、动态阈值、段落/全文/连续片段检测）
5. 位置映射（字符范围→文字块→页码+坐标）
6. 查重报告生成（JSON+TXT，含详细统计）
7. PDF标注（自动高亮重复区域，输出新PDF）
8. 配置系统（所有参数可调，支持热重载）

## 特色与改进
- 批量查重与标注：支持多文件自动两两查重并标注，适合大批量文档处理
- 智能判别扫描件：PDF自动判别是否需OCR，流程全自动
- OCR流程可配置：DPI、去红章、引擎类型等均可在 config.properties 中调整
- 查重算法升级：支持连续片段检测、段落级匹配、综合相似度
- GUI界面优化：新增OCR识别选项卡、配置展示、进度条、批量操作
- 报告输出丰富：JSON+TXT报告，PDF标注，统计信息全面

## 配置系统
`SimilarityConfig` 单例从 `config.properties`（UTF-8）加载所有参数，敏感信息（阿里云 AK）从未纳入版本控制的 `local.properties` 加载。支持 `reload()` 热重载。所有算法阈值、权重、N-Gram 大小等均可通过配置文件调整，无需重新编译。

### 关键配置项
- `ocr.render.dpi`：PDF 渲染为图片的 DPI，影响识别精度和速度（默认 200）
- `ocr.remove.seal.enabled`：是否在识别前去除红章（默认 false，预留功能）
- `ocr.type`：OCR 引擎类型（local/aliyun）
- `ocr.image.max.dimension`：图片压缩最大边长（默认 1200）
- `ocr.jpeg.quality`：JPEG 压缩质量（默认 0.85）
- `substring.min.length`：连续重复片段最小长度（默认 30 字符）
- `paragraph.similarity.threshold`：段落相似度阈值（默认 85%）
- `similarity.weight.*`：综合相似度各维度权重配置


软件plantUML:
@startuml
start

:接收输入文件;

if (文件类型?) then (PDF)
    
    if (是否扫描件?) then (是)
        
        if (OCR配置?) then (本地EasyOCR)
            :去除红章【必须用SimpleSealRemover.removeSeal】;
            :EasyOCR识别;
            :查重BatchDuplicateRunner.runBatchDuplicateCheck(List<PdfTask> pdfTasks) ;
            :生成查重报告;
            stop
        
        else (阿里云)
            :去除红章【必须用SimpleSealRemover.removeSeal】;
            :阿里云通用文字识别;
            :查重BatchDuplicateRunner.runBatchDuplicateCheck(List<PdfTask> pdfTasks) ;
            :标注;
            :生成查重报告;
            stop
        endif
    
    else (否，Word转的PDF)
        :查重BatchDuplicateRunner.runBatchDuplicateCheck(List<PdfTask> pdfTasks) ;
        :生成查重报告;
        stop
    endif

else (Word/TXT)
    :查重BatchDuplicateRunner.runBatchDuplicateCheck(List<PdfTask> pdfTasks) ;
    :生成查重报告;
    stop
endif

@enduml
