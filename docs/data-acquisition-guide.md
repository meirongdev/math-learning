# 新加坡数学题库获取指南 (Data Acquisition Guide)

本文档旨在指导如何持续获取、清洗并集成高质量的新加坡小学数学（P1-P6）题目数据，以支撑 RAG 知识库和测评系统的扩展。

## 1. 核心数据源 (Data Sources)

### 1.1 免费公开渠道 (Public & Free)
*   **SGTestPaper / Testpaper.biz**: 
    *   **资源类型**: 提供新加坡顶级名校（如 Nanyang, Raffles, Rosyth）的历年 CA1/SA1/CA2/SA2 模拟卷 PDF。
    *   **获取策略**: 定期下载最新学年的 PDF 试卷。
*   **SEAB (Singapore Examinations and Assessment Board)**:
    *   **资源类型**: 官方 PSLE 样题和往届试题大纲。
    *   **获取策略**: 关注每年 10 月更新的考试报告。
*   **Kaggle / Hugging Face (GSM8K)**:
    *   **资源类型**: 全球性的小学数学应用题数据集。
    *   **获取策略**: 筛选逻辑与新加坡数学（CPA Approach）相似的题目。

### 1.2 AI 合成数据 (Synthetic Data Generation) - **推荐方案**
利用现有的 `sg-math-questions.json` 作为 Seed 数据，通过 LLM（如 GPT-4o 或 DeepSeek-V3）批量生成衍生题。
*   **指令模板**: 
    > "基于以下示例题目，生成 5 道逻辑结构相同但数值、场景（Context）不同的新题目。确保难度等级为 Primary 6，涉及知识点为 Ratio 和 Model Method。输出格式需严格符合项目的 JSON Schema。"

### 1.3 商业/半商业渠道 (Professional)
*   **KooBits / EPH / Marshall Cavendish**: 这些是新加坡主流的教辅出版商。
*   **获取策略**: 购买电子版教辅书，利用 OCR 技术提取。

---

## 2. 数据处理流水线 (Data Pipeline)

为了将原始数据（PDF/图片/网页文本）转化为系统可用的结构化 JSON，需遵循以下流程：

### 第一步：原始提取 (Raw Extraction)
*   **工具**: `qwen2.5-vl` (本地) 或 `Google Document AI` (云端)。
*   **目标**: 将 PDF/图片中的文本与数学符号（LaTeX）完整提取。

### 第二步：LLM 清洗与格式化 (Cleaning & Structuring)
使用 `Content Agent` 对原始文本进行二次加工：
1.  **标准化**: 统一单位符号（如 $、kg、cm）。
2.  **结构化**: 识别 `question`、`grade`、`knowledgeTags`。
3.  **解析答案**: 自动生成符合 `Model Method` 的解题步骤（JSON 格式）。

### 第三步：向量化与去重 (Embedding & Deduplication)
*   **工具**: `nomic-embed-text` (Ollama)。
*   **去重逻辑**: 计算新题目与数据库中已有题目的余弦相似度。若相似度 > 0.95，则视为重复，予以剔除。

---

## 3. 集成与导入 (Integration)

### 3.1 批量导入脚本
将新生成的题目保存为 `backend/src/main/resources/data/new-questions.json`，然后利用 `QuestionImportService` 进行导入：

```bash
# 触发重新导入（开发环境）
curl -X POST http://localhost:8080/api/v1/admin/import-questions
```

### 3.2 标签体系 (Tagging System)
确保每道题至少包含以下一个标签，以便 RAG 检索：
*   **Heuristics**: `Model Method`, `Unitary Method`, `Working Backwards`, `Guess and Check`.
*   **Topics**: `Whole Numbers`, `Fractions`, `Decimals`, `Percentage`, `Ratio`, `Algebra`.

---

## 4. 维护与质量保证 (QA)

*   **人工校验**: 每批次导入的新题需随机抽取 5% 进行人工审核，验证 AI 生成的解题步骤是否符合 CPA 教学法。
*   **反馈循环**: 在应用中，如果家长对某道题的 AI 评价为 1-2 星，该题目应自动标记为“待审核”，并从 RAG 检索池中暂时移除。
