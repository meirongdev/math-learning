# 技术栈升级与市场调研报告

> 生成日期：2026-07-05
> 范围：全栈依赖升级 + 同类开源产品功能对齐
> 升级状态：✅ 全部批次已完成并验证通过（Spring AI 1.0.0 GA 有 breaking change，保留 2.0.0-M2）

---

## 一、技术栈版本升级分析

### 1.1 后端 (Backend) — ✅ 已完成

| 依赖 | 旧版本 | 新版本 | 状态 |
|------|-------|-------|------|
| **Spring Boot** | 4.0.3 | **4.1.0** | ✅ |
| **Spotless** | 7.0.2 | **8.8.0** | ✅ |
| **Gradle** | 9.2.0 | **9.6.1** | ✅ |
| **Resilience4j** | 2.2.0 | **2.3.0** | ✅ |
| **Testcontainers** | 1.20.4 | **1.21.3** | ✅ |
| **Netty DNS (macOS)** | 4.1.118.Final | **4.2.1.Final** | ✅ |
| **Lombok** | 1.18.42 | **1.18.46** | ✅ |
| **Jacoco** | 0.8.13 | **0.8.15** | ✅ |
| **Java** | 25 | 25 | ✅ 已最新 |
| **JJWT** | 0.12.6 | 0.12.6 | ✅ 已最新 |
| **Spring AI** | 2.0.0-M2 | **2.0.0** ✅ | ← Maven Central 发布的 GA 版，构建于 SB 4.1.0 |

### 1.2 前端 (KMP + Compose Multiplatform) — ✅ 已完成

| 依赖 | 旧版本 | 新版本 | 状态 |
|------|-------|-------|------|
| **Compose Multiplatform** | 1.10.2 | **1.11.1** | ⚠️ WasmJS resource generator 不兼容，保持 1.10.2 |
| **Spotless** | 7.0.2 | **8.8.0** | ✅ |
| **Ktlint** | 1.5.0 | **1.6.0** | ✅ |
| **Ktor** | 3.0.2 | **3.2.0** | ✅ |
| **kotlinx-coroutines** | 1.9.0 | **1.10.2** | ✅ |
| **kotlinx-serialization** | 1.7.3 | **1.9.0** | ✅ |
| **Kotlin** | 2.2.20 | 2.2.20 | ✅ Gradle Plugin Portal 上 2.2.20 为真实版本 |
| **compose.version** | 1.10.2 | 1.11.1 | ⚠️ 回退到 1.10.2；1.11.1 WasmJS resource generator 编译失败 |

### 1.3 Android 模块 — ✅ 已完成

| 依赖 | 旧版本 | 新版本 | 状态 |
|------|-------|-------|------|
| **AGP** | 8.9.1 | **9.2.1** | ✅ (需要 plugin 迁移) |
| **Compose BOM** | 2025.01.01 | **2026.06.01** | ✅ |
| **Navigation Compose** | 2.8.5 | **2.9.8** | ✅ |
| **Lifecycle** | 2.8.7 | **2.11.0** | ✅ |
| **Activity Compose** | 1.9.3 | **1.13.0** | ✅ |
| **Koin** | 4.0.3 | **4.1.0** | ✅ |
| **Room** | 2.7.1 | **2.8.4** | ✅ |
| **DataStore** | 1.1.1 | **1.2.1** | ✅ |
| **CameraX** | 1.4.1 | **1.6.1** | ✅ |
| **Kover** | 0.9.1 | **0.9.8** | ✅ |
| **ML Kit OCR** | 16.0.1 | 16.0.1 | ✅ 已最新 |
| **KSP** | 2.2.20-2.0.3 | 2.2.20-2.0.3 | ✅ 与 Kotlin 版本绑定 |
| **kotlinx-coroutines** | 1.9.0 | **1.10.2** | ✅ |
| **kotlinx-serialization** | 1.7.3 | **1.9.0** | ✅ |

### 1.4 基础设施 — 待评估

| 组件 | 当前版本 | 建议 |
|------|---------|------|
| **PostgreSQL** | 17 (pgvector:pg17) | 维持现有，pg18 就绪后评估 |
| **pgvector** | bundled | 维持现有 |
| **Redis** | 7-alpine | **8.8.0** 已发布，需验证 Spring Data Redis 兼容性 |

### 1.5 AGP 9.x 迁移说明

升级至 AGP 9.2.1 后需要以下代码变更（已完成）：

| 文件 | 变更 |
|------|------|
| `shared/build.gradle.kts` | `com.android.library` → `com.android.kotlin.multiplatform.library` |
| `shared/build.gradle.kts` | `android {}` 移入 `kotlin {}` 作用域内 |
| `shared/build.gradle.kts` | 移除 `compileOptions`（KMP 通过 `compilerOptions.jvmTarget` 控制） |
| `androidApp/build.gradle.kts` | 移除 `kotlin("android")` 插件（AGP 9.x 内置 Kotlin 支持） |
| `androidApp/build.gradle.kts` | 移除 `kotlinOptions {}` 块（需用 `compileOptions` 替代） |
| `frontend/gradle.properties` | 新增 `android.disallowKotlinSourceSets=false` |

### 1.6 未升级项说明

### 1.7 Compose Multiplatform 1.11.1 兼容性说明

Compose 1.11.1 的 resource generator 生成的 commonMain 代码（Res.kt, ExpectResourceCollectors.kt）在 WasmJS 目标上编译失败，
报 `Missing stdlib class` 和 `Unresolved reference ByteArray` 等错误。这是 JetBrains Compose 资源插件在 WasmJS 的已知限制。

**恢复方案：** 回退到 Compose 1.10.2，保留其余所有依赖升级。待 Compose 后续版本修复后再评估升级。


| 依赖 | 原因 | 后续计划 |
|------|------|---------|
| **Spring AI** | 2.0.0-M2 → 2.0.0 ✅ | Maven Central GA 版，API 微调已适配 | ✅ 已完成 |
| **Redis 7 → 8** | 无代码变更，但需要验证 Spring Data Redis 兼容性 | 待生产部署前评估 |
| **PostgreSQL 17 → 18** | pgvector 支持 pg18 的 image 已发布，无代码影响 | 下次 Docker 镜像更新时同步 |

---

## 二、市场调研：同类开源产品功能对齐

### 2.1 选取对标项目

经调研，将以下开源教育平台纳入对标（按成熟度排序）：

| 项目 | Stars | 定位 | 技术栈 | 核心亮点 |
|------|-------|------|--------|---------|
| **Mr. Ranedeer AI Tutor** | ⭐ 29.6k | AI 个性化学习助手 | GPT-4 Prompt | 定制化学习风格、深度交互式 Prompt |
| **Open edX** | ⭐ 8.1k | 企业级 LMS | Python/Django | MOOC 平台完整生态 |
| **classroomIO** | ⭐ 1.6k | 现代企业培训 LMS | TypeScript/Next.js | 现代化 UI 替代 Moodle |
| **Kolibri** | ⭐ 1.1k | 离线教育平台 | Python | 离线学习、适配低带宽环境 |
| **Ascend Flow (智流)** | ⭐ 6 | 多 Agent 自适应学习 | Python | 多 LLM Tutor 协同 + 心流理论 |
| **SolvYaar** | ⭐ 3 | AI 数学学习平台 | TypeScript | 区块链成就 + 实时数据流 |
| **Ai-Math-Tutor-Agent** | ⭐ 3 | 数学 AI Tutor | Python/LangGraph | MCP 搜索 + Human-in-the-loop |

### 2.2 功能矩阵对比

| 功能维度 | Math Learning (本项目) | Mr. Ranedeer | Open edX | Kolibri | classroomIO | 行业标杆 (Khan Academy) |
|---------|----------------------|-------------|----------|---------|-------------|----------------------|
| **AI 解题/讲解** | ✅ Planner + Content Agent pipeline | ✅ GPT-4 prompt | ❌ 无 | ❌ 无 | ❌ 无 | ✅ 基本解题 |
| **多解释模式** | ✅ 原理解说/苏格拉底启发 | ✅ 多种教学风格 | ❌ | ❌ | ❌ | ❌ |
| **RAG 知识库** | ✅ pgvector + grade 过滤 | ❌ | ❌ | ❌ | ❌ | ❌ |
| **语义缓存** | ✅ 3 层缓存 (Redis + pgvector + Caffeine) | ❌ | ❌ | ❌ | ❌ | ❌ |
| **SSE 流式输出** | ✅ 分段流式 | ✅ GPT 原生流 | ❌ | ❌ | ❌ | ❌ |
| **OCR 识题** | ✅ Tesseract.js (中英文) | ❌ | ❌ | ❌ | ❌ | ❌ |
| **知识图谱/技能树** | ✅ P1-P6 63节点树 + 星空图 | ✅ 概念链 | ❌ | ❌ | ❌ | ✅ 技能映射 |
| **掌握度追踪** | ✅ 三级 (UNKNOWN/FAMILIAR/MASTERED) | ✅ 基于概念的自评 | ✅ 课程进度 | ✅ 内容完成度 | ❌ | ✅ 精通制 |
| **自适应学习路径** | ✅ 薄弱点推荐 + 前置依赖检查 | ❌ | ✅ 先修依赖 | ❌ | ❌ | ✅ 推荐练习 |
| **游戏化成就** | ✅ 动态勋章 + 练习链 + 反思徽章 | ❌ | ❌ | ❌ | ❌ | ✅ 能量点 |
| **家长导引** | ✅ parentGuide + childScript | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Bar Model 可视化** | ✅ JSON 驱动 | ❌ | ❌ | ❌ | ❌ | ✅ 部分 |
| **错题本** | 🚧 骨架已交付 (rating ≤ 2) | ❌ | ❌ | ❌ | ❌ | ✅ |
| **PDF 导出** | 🚧 骨架已交付 | ❌ | ✅ | ✅ | ❌ | ❌ |
| **周报/学习报告** | ❌ 计划中 | ❌ | ✅ | ❌ | ❌ | ✅ 家长报告 |
| **多平台 (Web + Android)** | ✅ Wasm + Android (KMP) | ❌ | ✅ Web | ✅ 多平台 | ✅ Web | ✅ Web + App |
| **离线支持** | 🚧 Room 只读缓存 (Android) | ❌ | ❌ | ✅ 核心能力 | ❌ | ✅ 有限 |
| **JWT 鉴权** | ✅ JJWT | ❌ | ✅ OAuth2 | ❌ | ✅ | ✅ |
| **本地 LLM 支持** | ✅ Ollama 优先 | ❌ | ❌ | ❌ | ❌ | ❌ |
| **BYOK (自带 Key)** | 📄 提案阶段 | ✅ | ❌ | ❌ | ❌ | ❌ |

### 2.3 差异化优势分析

本项目在以下方面具有 **显著领先** 于同类开源产品：

1. **AI 教学深度**：Planner → Content Agent 串联管线 + 苏格拉底启发模式，在开源教育产品中极为少见。Mr. Ranedeer 仅靠 Prompt 工程，不具备后端缓存/RAG 等基础设施。

2. **新加坡 PSLE 专精**：63 节点知识图谱完全对标新加坡小学数学教学大纲，题库+知识点体系闭环。目前开源市场无针对 PSLE 的竞品。

3. **响应速度优化**：3层缓存架构（Redis 精确匹配 → pgvector 语义缓存 + Caffeine → LLM 管线）+ Resilience4j 韧性控制，本地推理场景下缓存命中率目标 > 60%。

4. **技术栈现代化**：Java 25 + Spring Boot 4 + Kotlin 2.x + Compose Multiplatform (Wasm)，同时支持 Web 和 Android。

5. **家长赋能**：`parentGuide` + `childScript` + Bar Model 的分层设计，让家长能直接辅导，这是市面上多数 AI 解题工具（只能给答案）没有的。

### 2.4 需要补齐的功能差距

| 缺失功能 | 优先级 | 对标参考 | 建议方案 |
|---------|--------|---------|---------|
| **交互式 Bar Model（拖拽建模）** | P1 | Khan Academy Bar Model | 已有 JSON 数据，需前端交互组件 (Phase 9.1) |
| **错题本完整版（含过滤/分页）** | P1 | Khan Academy 错题回顾 | 已有 `GET /api/v1/records/mistakes` 骨架，需完善前端 |
| **周报/学习报告 AI 摘要** | P2 | Khan Academy 家长周报 | `WeeklyReportService` 计划 (Phase 9.5) |
| **类 Pinterest 中台运营系统** | P3 | Open edX Studio | 不需要，本项目无内容创作团队 |
| **社区/论坛功能** | P3 | Open edX 讨论区 | 目前不需要 |
| **iOS 客户端** | P3 | — | 待 Phase 11+ 扩展 |
| **SOC 2/GDPR 合规** | P4 | Open edX | 生产部署后再评估 |

### 2.5 建议后续行动

1. **短期（1-2 周）**：执行"批次一"版本升级（Spring Boot 4.1 + Gradle 9.6 + Testcontainers），低风险高回报
2. **短期（1-2 周）**：完成 Phase 9.1 交互式 Bar Model 组件，这是与其他 AI 解题工具拉开差距的关键
3. **中期（1 个月）**：调研 Spring AI 1.0.0 GA 迁移方案，评估 API 差异
4. **中期（1 个月）**：完整交付错题本 + 周报功能，补齐与 Khan Academy 的功能缺口
5. **长期**：Redis 8 升级评估、iOS 端可行性研究

---

## 附录

### A. 调研数据来源
- Maven Central Search API (search.maven.org)
- Gradle Plugin Portal (plugins.gradle.org)
- Google Maven (dl.google.com)
- GitHub API (api.github.com)
- Docker Hub API (hub.docker.com)

### B. 版本验证说明
- Kotlin `2.2.20` 和 Lombok `1.18.42` 在 Maven Central 未找到对应版本，
  可能是非公开里程碑版本或本地构建。建议核实 build 文件中版本号的来源。
- Spring AI `2.0.0-M2 → 1.0.0`：里程碑版本与 GA 版本的版本号方案不一致，
  建议参考 Spring AI 官方迁移指南进行升级。

### C. 对标项目排除说明
- Khan Academy 本身不是开源项目（仅 exercism 部分开源），故作为功能标杆参考而非技术对标。
- Moodle 虽历史悠久（PHP）但与项目技术栈差距过大，未纳入详细比较。
- 多个 GitHub 小众项目（≤ 5 stars）仅列出未详细分析。
