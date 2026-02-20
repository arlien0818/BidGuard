# AI Contract – BidGuard 项目

## 项目目标
本项目用于对「标书（PDF的扫描件）」进行识别与分析，暂时放弃word转的PDF及其它格式的标书。因此不要再去看python及EASYOCR相关的代码了。
为后续条款抽取、相似度比对、评分项分析提供基础数据。

---

## 强制架构原则（不可违反）

1. OCR 能力 **不得运行在 Java 进程内**
2. OCR 必须作为 **独立服务**
3. Java 仅通过 HTTP 调用 OCR 服务
4. OCR 输出必须是 **结构化 JSON**

---

## 技术选型（固定，不得随意更换）

- OCR：EASYOCR（Python），暂时放弃，不要再去看它相关的代码了。
- OCR 服务形态：REST API
- 主系统：Java（Spring Boot）
- 通信方式：HTTP / multipart
- 当前阶段不引入大模型

---

## OCR 服务要求

- 支持：中文 + 英文混排
- 支持：扫描 PDF / 图片
- 提供 HTTP 接口：
  - 输入：文件
  - 输出：JSON
- 返回字段至少包含：
  - text
  - confidence

---

## Java 侧职责边界

Java 只负责：
- 文件转发
- OCR 结果接收
- JSON 解析

Java 不得：
- 参与 OCR 识别逻辑
- 依赖 OCR 算法库

---

## 当前阶段范围（禁止超前设计）

✔ 跑通 OCR 服务 → Java 调用 → JSON 返回  
✘ 表格结构化  
✘ 条款层级分析  
✘ 大模型调用


## 其他开发规范
✔每次修改代码请把BidCheckerGUI.java中的： JLabel versionLabel = new JLabel("BidGuard v1.0");一句中的V加0.01，按十进抽进位。
  配置文件就是指config.properties

AI不要自己创建新的标签页。除非是聊天对话中程序员明确指出的。
把生成的测试类文件以及需要生成其他相关的测试文件，放入test文件夹，不要在项目根目录下。
把生成的调试类以及需要生成的其他相关的调试文件，放入debug文件夹，不要放在项目根目录下。


##参考文档
阿里能通用文字识别的返回结果请参考《阿里云 OCR 返回结构分析文档.md》来解析
阿里云通用文字识别的一个返回的实际例子在《aliyun_ocr_response_structure.json》中。

# 测试与调试代码生成规范

1. 所有临时测试程序、调试程序、验证类，必须生成在：
   src/test/java/com/bidguard/

2. 严禁在 src/main/java 下生成：
   - 带有 main 方法的临时测试类
   - QuickTest、Debug、Temp、Trial 等实验性文件
   - 用于一次性验证的工具程序

3. src/main/java 仅允许放置：
   - 生产逻辑代码
   - 核心业务类
   - 系统架构相关类
   - 真正参与构建发布的代码

4. 若需要生成调试辅助程序，应放入：
   src/test/java/com/bidguard/debug/

5. 任何临时验证代码不得混入生产目录。


