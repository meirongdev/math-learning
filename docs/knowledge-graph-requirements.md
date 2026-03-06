# 需求文档：数学知识图谱与掌握度评估

## 背景

当前应用仅支持自由输入题目解题，缺乏对学生知识点掌握情况的系统化追踪。本功能在现有解题能力基础上，新增结构化知识图谱、题库测评、掌握度标记、解题历史星级评分等能力，使家长能够全面了解孩子的学习状态。

---

## 1. 知识图谱

### 1.1 结构

新加坡小学数学（P1–P6，PSLE 2026 大纲）知识点按树状层级组织：

```
数学 (root)
├── 数与代数 (Numbers & Algebra)
│   ├── 整数 (Whole Numbers)
│   │   ├── 四则运算 (Four Operations)
│   │   ├── 因数与倍数 (Factors & Multiples)
│   │   └── 整除性 (Divisibility)
│   ├── 分数 (Fractions)
│   │   ├── 分数概念 (Fraction Concepts)
│   │   ├── 分数四则运算 (Fraction Operations)
│   │   └── 分数与除法 (Fractions & Division)
│   ├── 小数 (Decimals)
│   │   ├── 小数概念 (Decimal Concepts)
│   │   └── 小数四则运算 (Decimal Operations)
│   ├── 百分比 (Percentages)
│   │   ├── 百分比概念 (Percentage Concepts)
│   │   └── 百分比应用 (Percentage Applications)
│   ├── 比与比例 (Ratio)
│   │   ├── 比的概念 (Ratio Concepts)
│   │   └── 比的应用 (Ratio Applications)
│   └── 代数 (Algebra) — P5/P6
│       ├── 代数式 (Algebraic Expressions)
│       └── 方程 (Equations)
├── 测量 (Measurement)
│   ├── 长度、质量与体积 (Length, Mass & Volume)
│   ├── 时间 (Time)
│   ├── 金钱 (Money)
│   ├── 面积与周长 (Area & Perimeter)
│   └── 体积与容积应用 (Volume Applications)
├── 图形与几何 (Geometry)
│   ├── 平面图形 (2D Shapes)
│   │   ├── 三角形 (Triangles)
│   │   ├── 四边形 (Quadrilaterals)
│   │   └── 圆 (Circles) — P5/P6
│   ├── 立体图形 (3D Shapes)
│   └── 角度 (Angles)
└── 数据分析 (Statistics)
    ├── 图表 (Data Representation)
    │   ├── 象形图 (Pictographs) — P1/P2
    │   ├── 条形图 (Bar Graphs) — P2/P3
    │   ├── 折线图 (Line Graphs) — P4
    │   └── 饼图 (Pie Charts) — P4+
    └── 平均数 (Average/Mean) — P4+
```

每个叶节点为一个独立**知识点（Knowledge Node）**，附带：
- `code`：唯一标识符（如 `fractions.operations.addition`）
- `name_en` / `name_zh`：英文 / 中文名称
- `grade_start`：该知识点首次出现的年级（P1–P6）
- `prerequisites`：前置知识点 code 列表

### 1.2 掌握度等级

每个学生对每个知识点有独立的掌握度记录：

| 等级 | 英文 | 说明 |
|:-----|:-----|:-----|
| `UNKNOWN` | 未知 | 尚未接触或测评 |
| `FAMILIAR` | 了解 | 有初步概念，仍会出错 |
| `MASTERED` | 掌握 | 能稳定正确解答 |

掌握度来源有两种：
1. **家长手动标记**：在知识图谱页面或解题历史中直接修改
2. **解题后家长评分**：完成解题后，家长给出 1–5 星评分，系统据此建议掌握度（4–5 星 → 掌握，2–3 星 → 了解，1 星 → 未知），家长可接受或覆盖

---

## 2. 题库（Knowledge Assessment Bank）

### 2.1 说明

独立于 RAG 向量库的结构化题库，每道题明确标记知识点映射，用于针对性考核。初始内置 ≥ 60 道覆盖 P1–P6 各年级的测评题。

### 2.2 题目数据结构

```json
{
  "id": "q001",
  "question": "Amy has 3/4 of a pizza. She eats 1/3 of what she has. What fraction of the whole pizza does she eat?",
  "grade": 4,
  "difficulty": "medium",
  "knowledge_tags": ["fractions.operations.multiplication"],
  "answer_hint": "Multiply the fractions: 1/3 × 3/4"
}
```

### 2.3 测评流程

1. 家长进入某学生的**测评模式**，选择知识点范围（单个知识点 / 按年级批量）
2. 系统从题库中按知识点随机抽题，展示给孩子
3. 孩子在 AI 助手帮助下解题（走现有解题流程）
4. 解题完成后，家长给出 1–5 星掌握度评分
5. 系统记录本次测评，更新知识点掌握度

---

## 3. 解题历史增强

### 3.1 星级评分

解题历史记录新增 `rating` 字段（1–5 整数，可为空）：
- 解题完成后，结果页底部显示星级评分控件
- 家长点击评分后立即保存到 `solve_records.rating`
- 评分同时触发关联知识点的掌握度建议更新（家长可接受或修改）

### 3.2 历史列表展示

`GET /api/v1/records/{studentId}` 响应新增字段：

```json
{
  "id": "...",
  "question": "...",
  "knowledgeTags": ["fractions.operations.multiplication"],
  "rating": 4,
  "createdAt": "2026-03-06T09:00:00Z"
}
```

---

## 4. API 设计

> 本节为 Phase 6 目标接口设计。
> 当前已上线接口请以 `docs/reference/api.md` 为准。

### 知识图谱

| Method | Path | 说明 |
|:-------|:-----|:-----|
| `GET` | `/api/v1/knowledge/graph` | 返回完整知识图谱树（公开，无需鉴权） |
| `GET` | `/api/v1/knowledge/{studentId}/progress` | 返回学生各知识点掌握度 |
| `PUT` | `/api/v1/knowledge/{studentId}/progress/{nodeCode}` | 家长手动更新掌握度（`UNKNOWN`/`FAMILIAR`/`MASTERED`） |

### 题库

| Method | Path | 说明 |
|:-------|:-----|:-----|
| `GET` | `/api/v1/questions?tag={code}&grade={n}&limit={n}` | 按知识点/年级随机抽题 |

### 解题记录

| Method | Path | 说明 |
|:-------|:-----|:-----|
| `PATCH` | `/api/v1/records/{recordId}/rating` | 保存家长星级评分（1–5） |
| `GET` | `/api/v1/records/{studentId}` | 历史列表（已有，后续扩展 `rating` 字段） |

### 学生管理（补充）

| Method | Path | 说明 |
|:-------|:-----|:-----|
| `DELETE` | `/api/v1/students/{id}` | 删除学生档案（删除策略由实现决定，默认按最小可用方案先实现） |

---

## 5. 数据库变更

### 5.1 新增表 `knowledge_nodes`

```sql
CREATE TABLE knowledge_nodes (
    code        VARCHAR(100) PRIMARY KEY,
    name_en     VARCHAR(200) NOT NULL,
    name_zh     VARCHAR(200) NOT NULL,
    parent_code VARCHAR(100) REFERENCES knowledge_nodes(code),
    grade_start SMALLINT NOT NULL CHECK (grade_start BETWEEN 1 AND 6),
    sort_order  SMALLINT NOT NULL DEFAULT 0
);
```

### 5.2 修改表 `knowledge_progress`

新增 `mastery_level` 列，在保留统计字段基础上补充等级视图：

```sql
ALTER TABLE knowledge_progress
    ADD COLUMN mastery_level VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (mastery_level IN ('UNKNOWN', 'FAMILIAR', 'MASTERED'));
```

### 5.3 修改表 `solve_records`

新增 `rating` 列：

```sql
ALTER TABLE solve_records
    ADD COLUMN rating SMALLINT CHECK (rating BETWEEN 1 AND 5);
```

### 5.4 新增表 `assessment_questions`

```sql
CREATE TABLE assessment_questions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_text  TEXT NOT NULL,
    grade          SMALLINT NOT NULL CHECK (grade BETWEEN 1 AND 6),
    difficulty     VARCHAR(10) NOT NULL CHECK (difficulty IN ('easy', 'medium', 'hard')),
    answer_hint    TEXT
);

CREATE TABLE assessment_question_tags (
    question_id UUID REFERENCES assessment_questions(id) ON DELETE CASCADE,
    node_code   VARCHAR(100) REFERENCES knowledge_nodes(code),
    PRIMARY KEY (question_id, node_code)
);
```

---

## 6. 前端页面规划

### 6.1 知识图谱页（新页面）

- 树形展开列表，每个知识点显示掌握度徽章（未知灰 / 了解黄 / 掌握绿）
- 点击知识点 → 弹出详情：查看描述、手动修改掌握度、入口跳转测评
- 顶部按年级过滤

### 6.2 测评模式（在解题页扩展）

- 学生选择器选中学生后，增加"开始测评"按钮
- 测评流程：选择知识点范围 → 显示题目 → 进入解题 → 星级评分

### 6.3 解题结果页增强

- 现有结果区底部新增"掌握度评分"控件（1–5 颗星）
- 评分后显示"已记录"确认；同时展示当前知识点掌握度建议

### 6.4 解题历史页（新页面）

- 按学生筛选的解题历史列表
- 每条记录：题目摘要 + 知识点标签 + 星级评分 + 时间
- 点击可展开查看完整解题内容

---

## 7. 范围说明

**本期包含：**
- 知识图谱数据模型与种子数据（P1–P6 全覆盖）
- 掌握度等级（三级）+ 家长手动标记
- 题库（≥ 60 道）与随机抽题 API
- 解题历史星级评分
- 前端：知识图谱页、解题历史页、星级评分组件

**本期不包含（后续考虑）：**
- 知识点依赖关系的自动学习路径规划
- 基于掌握度的 AI 个性化推题（Phase 8 / 原 Phase 7）
- 学生自主答题（无 AI 辅助的纯测试模式）

---

## 8. 与当前系统的一致性说明

- 当前知识点追踪接口为 `GET /api/v1/knowledge/{studentId}`，返回 attempt/correct/masteryScore 聚合信息。
- 本文中的 `GET /api/v1/knowledge/graph`、`/progress` 路径属于 Phase 6 目标，不代表当前已上线。
- `rating` 字段与 `PATCH /api/v1/records/{recordId}/rating` 需先完成数据库迁移再对外承诺。
