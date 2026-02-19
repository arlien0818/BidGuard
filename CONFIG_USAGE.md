# BidGuard 配置文件使用说明

## 📋 概述

BidGuard 现在支持通过配置文件调整相似度检测参数，**修改配置后无需重新编译**，只需重启程序即可生效。

**重要提示**：配置文件必须使用 **UTF-8 编码**保存，以确保中文注释正确显示。

## 📁 配置文件位置

配置文件名：`config.properties`  
**编码格式**：UTF-8（无 BOM）

程序会按以下顺序查找配置文件：
1. 当前工作目录
2. 程序（.jar）所在目录

建议将配置文件放在项目根目录或 jar 包同级目录。

## ⚙️ 配置项说明

### 1. 段落分割参数

```properties
# 段落最小长度（字符数）
# 说明：低于此长度的文本行会被过滤，不参与段落匹配
# 默认值：30
# 建议范围：20-50
paragraph.min.length=30
```

**调整建议**：
- 如果文档中有很多短句，可以降低到 20-25
- 如果只关注长段落，可以提高到 40-50

---

### 2. 子串匹配参数

```properties
# 子串最小匹配长度（字符数）
# 说明：检测段落内连续相同片段的最小长度
# 默认值：100
# 建议范围：50-200
substring.min.length=100
```

**调整建议**：
- **更严格检测**：降低到 50-80，可以发现更多短片段抄袭
- **只关注大段抄袭**：提高到 150-200

---

### 3. 段落相似度阈值

```properties
# 段落相似度阈值（百分比，0-100）
# 说明：超过此阈值的段落对会被标记为相似
# 默认值：70.0
# 建议范围：60.0-85.0
paragraph.similarity.threshold=70.0
```

**调整建议**：
- **宽松模式**（减少误报）：提高到 75-80
- **严格模式**（发现更多相似）：降低到 60-65

---

### 4. 综合相似度权重

```properties
# 三个权重之和必须等于 1.0
# 词汇相似度权重（基于 N-gram 重叠）
similarity.weight.lexical=0.4

# 语义相似度权重（基于 TF-IDF）
similarity.weight.semantic=0.45

# 结构相似度权重（文档长度、段落数等）
similarity.weight.structural=0.15
```

**调整建议**：

| 场景 | 词汇 | 语义 | 结构 | 说明 |
|------|------|------|------|------|
| 默认（推荐） | 0.40 | 0.45 | 0.15 | 平衡各项指标 |
| 重视逐字抄袭 | 0.50 | 0.35 | 0.15 | 提高词汇权重 |
| 重视语义相似 | 0.30 | 0.55 | 0.15 | 关注改写抄袭 |
| 忽略结构差异 | 0.45 | 0.50 | 0.05 | 降低结构影响 |

---

### 5. 相似度等级判定阈值

```properties
# 高度相似阈值（大文档 >10000字符）
similarity.level.high.large=75.0

# 高度相似阈值（中文档 >5000字符）
similarity.level.high.medium=70.0

# 高度相似阈值（小文档）
similarity.level.high.small=65.0
```

**说明**：
- 大文档更容易出现偶然重复，所以阈值更高
- 小文档即使较低的相似度也值得注意

---

### 6. 其他参数

```properties
# 预览文本最大字符数
preview.max.chars=200

# 调试模式：是否输出详细的PDF内容
debug.print.pdf.content=false
```

## 🎯 典型配置场景

### 场景 1: 严格抄袭检测（毕业论文）

```properties
paragraph.min.length=25
substring.min.length=80
paragraph.similarity.threshold=65.0
similarity.weight.lexical=0.45
similarity.weight.semantic=0.45
similarity.weight.structural=0.10
```

### 场景 2: 标书对比（关注关键段落）

```properties
paragraph.min.length=40
substring.min.length=120
paragraph.similarity.threshold=70.0
similarity.weight.lexical=0.40
similarity.weight.semantic=0.45
similarity.weight.structural=0.15
```

### 场景 3: 快速筛查（减少误报）

```properties
paragraph.min.length=30
substring.min.length=150
paragraph.similarity.threshold=75.0
similarity.weight.lexical=0.35
similarity.weight.semantic=0.50
similarity.weight.structural=0.15
```

## 🔧 调试技巧

1. **观察对比结果**
   - 程序会在控制台输出当前使用的配置
   - 根据实际对比效果调整参数

2. **逐步调整**
   - 每次只调整 1-2 个参数
   - 观察对比结果的变化

3. **保存多个配置**
   - 可以创建 `config.strict.properties`、`config.loose.properties`
   - 使用时改名为 `config.properties`

## ⚠️ 注意事项

1. **配置文件编码**
   - **必须使用 UTF-8 编码保存**
   - 推荐使用 UTF-8 无 BOM 格式
   - 使用记事本编辑时，另存为时选择"UTF-8"编码
   - 使用 VS Code/Notepad++ 等编辑器会自动使用 UTF-8

2. **权重之和必须为 1.0**
   - 如果不等于 1.0，程序会发出警告
   - 系统会自动归一化，但建议手动修正

3. **参数验证**
   - 阈值应在 0-100 之间
   - 长度参数应为正整数
   - 如果配置无效，程序会使用默认值

4. **当前不支持热重载**
   - 修改配置后需要重启程序
   - （未来版本可能支持实时重载）

## 📝 配置文件模板

完整的配置文件已在项目根目录生成为 `config.properties`，您可以直接编辑使用。

---

如有问题，请查看程序日志输出，其中会显示当前生效的配置参数。
