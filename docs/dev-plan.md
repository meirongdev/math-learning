# 开发计划 - 具体可执行版 (MVP)

## Phase 0: 前置准备 ✅

> 安装和启动步骤见 [docs/quickstart.md](quickstart.md)。
>
> **⚠️ 实际版本变更说明 (实施中发现)：**
>
> | 计划版本 | 实际版本 | 原因 |
> |:---------|:---------|:-----|
> | Gradle 8.x | **Gradle 9.2** | Gradle 8.13 不支持 Java 25 运行时 |
> | Spring Boot 3.5 | **Spring Boot 4.0.3** (Spring Framework 7.0.5) | 插件 3.5.0 拉取了 spring-boot:4.0.1 导致版本冲突，统一升级至 4.0.3 |
> | Spring AI 2.0.0 | **Spring AI 2.0.0-M2** | 2.0.0 正式版尚未发布 |
> | Lombok 1.18.36 | **Lombok 1.18.42** | 1.18.36 不兼容 Java 25 |
> | `io.spring.dependency-management` 插件 | **移除，改用原生 `platform()` BOM** | 该插件不兼容 Gradle 9 |
> | Hibernate 自动配置在核心模块 | **需要独立 starter 模块** | Spring Boot 4.0 将 JPA/Flyway 自动配置拆分到独立模块 |
> | Vector 维度 1536 | **768** | 匹配 nomic-embed-text 模型的实际输出维度 |
> | IVFFlat 索引 | **HNSW 索引** | IVFFlat 无法在空表上创建 |

---

## Phase 1: 核心 Agent 搭建 ✅ <small>(已完成)</small>

**交付物：** 可运行的后端 API，Ollama 本地模型驱动的解题 Agent 链。

- Spring Boot 后端骨架，Flyway 建表（5 张业务表 + `vector_store` + `flyway_schema_history`）
- `MathSolverOrchestrator`：Planner Agent → Content Agent 串行管线
- Auth：`/api/v1/auth/register` + `/api/v1/auth/login`（JWT 生成，暂未强制校验）
- SSE 端点：`/api/v1/solve/stream`；非流式端点：`/api/v1/solve`

---

## Phase 2: RAG 知识库 + Prompt 优化 ✅ <small>(已完成)</small>

**交付物：** 具备 PSLE 知识库支撑的增强型解题器。

- 40 道 PSLE 题目（`sg-math-questions.json`）启动时自动 embed 并导入 `vector_store`
- `RagRetrievalService`：cosine 相似度检索，grade 过滤，Top-5 注入 Planner prompt
- `SolveService`：`@Cacheable("solveResults")`，question + grade 为 key，TTL 24h
- `OllamaConfig`：`RestClientCustomizer` interceptor，修复 Spring AI 2.0.0-M2 `think` 字段泄漏 bug（见 [reference/troubleshooting.md](reference/troubleshooting.md)，上游 [spring-ai#5435](https://github.com/spring-projects/spring-ai/pull/5435)）

> **关键发现：** Spring Boot 4.0 不自动注册 `ObjectMapper` Bean（`QuestionImportService` 和 `SolveController` 目前各自 `new ObjectMapper()`，待 Phase 4 统一）；thinking 模式开启时 ~52s/请求，关闭后 ~16s。

---

## Phase 3: Web 前端 (Compose Wasm) ✅ <small>(已完成)</small>

**交付物：** 可在线交互的解题工具 Web 端（前端 localhost:8081 + 后端 localhost:8080）。

- Compose for Web (Wasm) SPA：解题输入 + 年级选择 + 结果四区展示（家长导引、儿童脚本、Bar Model、知识点标签）
- SSE 流式对接，Ktor HTTP client 解析结构化 `{type, content}` 事件
- Loading / 错误 / 空状态处理；移动端响应式适配
- CORS 跨域配置（`SecurityConfig`）

---

## Phase 4: 代码质量与健壮性 ✅ <small>(已完成)</small>

> **目标**：消除当前技术债，让本地应用稳定可靠。这是后续所有功能建设的基础。

### Task 4.1: 统一基础设施

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 4.1 | 统一 `ObjectMapper` Bean（`@Primary` 单例），替换 `SolveController` 和 `QuestionImportService` 中的 `new ObjectMapper()` | 全局 Bean 注入正常，无重复实例 |
| 4.2 | 添加 `@ControllerAdvice` 全局异常 Handler，统一 `ErrorResponse{code, message}` 格式 | 所有 4xx/5xx 返回统一结构，前端可统一处理错误 |
| 4.3 | LLM 响应 JSON 解析替换为 Jackson（现在是手写字符串解析），解析失败抛明确异常 | 格式错误的 LLM 响应抛异常而非返回空字段 |

### Task 4.2: 输入验证

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 4.4 | 题目内容长度限制（≤ 500 字）、年级范围校验（1–6），用 Bean Validation 注解 | 非法输入返回 400 + 明确错误消息 |
| 4.5 | LLM 调用超时控制（60s）+ 失败时返回友好错误，不让请求永久挂起 | 模拟 LLM 超时，前端收到 `{code: "LLM_TIMEOUT", message: "..."}` |

### Task 4.3: 测试基础

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 4.6 | 后端集成测试骨架（Testcontainers + PostgreSQL），覆盖 `/api/v1/solve` 主流程 | `./gradlew test` 全绿 |
| 4.7 | 边界条件测试：空题目、超长输入、无效年级 | 全部边界 case 有对应测试用例 |

**Phase 4 交付物：** 稳定的后端基础，统一错误处理，输入验证就绪，测试骨架建立。

---

## Phase 5: 功能完整性 ✅ <small>(已完成)</small>

> **目标**：补齐数据库已建表但尚未接通的功能，让应用形成完整闭环。
>
> **实施说明：**
> - JWT 鉴权使用 JJWT 0.12.6 实现，`JwtAuthenticationFilter` + `HttpStatusEntryPoint(401)` 确保未认证请求返回 401
> - `StudentController` 基于 `@AuthenticationPrincipal` 获取当前用户 ID
> - `SolveService` 解题成功后自动写入 `solve_records` 并更新 `knowledge_progress`（upsert）
> - 前端新增登录/注册页面、学生选择器，`MathApi.kt` 注入 Bearer Token
> - 测试使用 Testcontainers 单例容器模式，避免跨测试类容器生命周期问题
>
> **UX 遗留问题已在 Phase 6 解决：** Token 持久化、学生管理重设计、历史页与知识点页。

### Task 5.1: JWT 正式启用

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 5.1 | `SecurityConfig` 对需要鉴权的端点启用 JWT 校验（`/api/v1/solve`、学生档案相关端点） | 未带 Token 请求返回 401；带有效 Token 正常通过 |
| 5.2 | 登录响应补充 `expiresAt` 字段；前端 `MathApi.kt` 在请求头注入 Bearer Token | 前端完整登录 → 解题流程可用 |

### Task 5.2: 学生档案 API

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 5.3 | `POST /api/v1/students` — 在当前登录用户下创建学生档案（姓名、年级） | 返回 201 + studentId；`student_profiles` 表写入 |
| 5.4 | `GET /api/v1/students` — 返回当前用户名下所有学生 | 返回学生列表，与数据库一致 |

### Task 5.3: 解题记录持久化

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 5.5 | `SolveService` 解题成功后写入 `solve_records` 表（关联 studentId，若请求携带） | 解题后 `SELECT * FROM solve_records` 可见记录 |
| 5.6 | `GET /api/v1/records/{studentId}` — 分页返回解题历史 | API 返回历史列表，按时间倒序 |

### Task 5.4: 知识点掌握度追踪

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 5.7 | 解题完成后，将 `knowledgeTags` 写入 `knowledge_progress`（upsert，累加 `attempt_count`） | 同一知识点多次解题后 `attempt_count` 递增 |
| 5.8 | `GET /api/v1/knowledge/{studentId}` — 返回各知识点的掌握度统计 | 返回按 `attempt_count` 排序的知识点列表 |

### Task 5.5: 前端配套更新

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 5.9 | 前端增加登录/注册页面（当前直接进入解题页） | 未登录跳转登录页，登录成功后进入解题页 |
| 5.10 | 前端解题时允许选择"当前学生"（从 `/api/v1/students` 获取），关联解题记录 | 选择学生后解题，历史页面可查看记录 |

**Phase 5 交付物：** 完整的用户 → 学生 → 解题 → 历史记录闭环，本地可作为真实产品使用。

---

## Phase 6: UX 完善 + 知识图谱与掌握度评估 ✅ <small>(已完成)</small>

> **目标**：修复 Phase 5 遗留 UX 问题，建立完整的知识图谱体系与测评闭环。
>
> **参考文档：**
> - [session-persistence.md](session-persistence.md) — Token 持久化方案
> - [student-management-redesign.md](student-management-redesign.md) — 学生管理重设计
> - [knowledge-graph-requirements.md](knowledge-graph-requirements.md) — 知识图谱与评估需求
>
> **实施说明：**
> - Flyway V2 迁移：`knowledge_nodes`（63 节点 P1–P6）、`assessment_questions`（68 题）+ `assessment_question_tags`、`solve_records.rating`、`knowledge_progress.mastery_level`
> - Flyway V3 迁移：种子数据（知识图谱节点 + 测评题库）
> - 后端：`KnowledgeController` 重写（graph/progress/mastery APIs）、`RecordController` 增加 rating、`StudentController` 增加 DELETE、新增 `AssessmentController`
> - 前端：`TokenStore` expect/actual（localStorage）、401 拦截器、三 Tab 导航（Solve/Knowledge/History）、`StudentManagementDialog`、知识图谱树形页、历史记录页、星级评分组件
> - `kotlinx-datetime:0.6.1` 用于 Token 过期校验

### Task 6.1: Session 持久化（Phase 5 遗留）

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.1 | `TokenStore` expect/actual：`wasmJsMain` 实现基于 `localStorage`，`jvmMain` no-op | 编译通过，无运行时错误 |
| 6.2 | `MathApi.login()` 调用 `saveToken(token, expiresAt)` 持久化 Token | 登录后刷新页面不弹出登录框 |
| 6.3 | `App()` 首次 composition 从 `localStorage` 恢复 Token，校验 `expiresAt` 未过期则免登录 | Token 有效期内刷新直接进入解题页 |
| 6.4 | Ktor 客户端安装 401 拦截器，Token 过期时清除并跳回登录页 | 使用过期 Token 调用 API 后自动退出 |
| 6.5 | 退出登录时调用 `clearToken()` 清除 `localStorage` | 退出后刷新跳回登录页 |

### Task 6.2: 学生管理重设计（Phase 5 遗留）

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.6 | 后端 `DELETE /api/v1/students/{id}`，需校验归属，级联软删除关联记录 | 204 删除成功；非归属学生返回 404 |
| 6.7 | 前端提取 `StudentManagementDialog` 组件（列表 + 年级选择器 + 删除按钮） | 新增学生可选 P1–P6，删除后列表刷新 |
| 6.8 | 移除主页面内嵌 Add Student 表单，改为"Manage"按钮入口 | 主页面学生卡片简洁，管理入口明确 |
| 6.9 | 对话框内联错误提示，不污染主页面全局错误状态 | 添加/删除失败仅在 Dialog 内显示错误 |

### Task 6.3: 知识图谱数据模型

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.10 | Flyway 迁移：新增 `knowledge_nodes` 表（code、name_en、name_zh、parent_code、grade_start） | 表结构创建成功 |
| 6.11 | 种子数据：P1–P6 全覆盖知识点树（≥ 60 个叶节点），以 Flyway 迁移脚本录入 | `SELECT COUNT(*) FROM knowledge_nodes` ≥ 60 |
| 6.12 | `knowledge_progress` 新增 `mastery_level` 列（`UNKNOWN`/`FAMILIAR`/`MASTERED`，默认 `UNKNOWN`） | 迁移成功，旧数据默认填充 `UNKNOWN` |
| 6.13 | `GET /api/v1/knowledge/graph` — 返回完整知识图谱树（公开端点） | 返回嵌套 JSON 树结构 |
| 6.14 | `GET /api/v1/knowledge/{studentId}/progress` — 返回学生各知识点掌握度 | 返回知识点列表附 mastery_level |
| 6.15 | `PUT /api/v1/knowledge/{studentId}/progress/{nodeCode}` — 家长手动更新掌握度 | 更新后 GET 接口返回新值 |

### Task 6.4: 题库与测评

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.16 | Flyway 迁移：新增 `assessment_questions` + `assessment_question_tags` 表 | 表结构创建成功 |
| 6.17 | 种子数据：≥ 60 道按知识点标注的测评题（P1–P6，覆盖主要叶节点） | 每个年级 ≥ 8 道 |
| 6.18 | `GET /api/v1/questions?tag={code}&grade={n}&limit={n}` — 按知识点随机抽题 | 返回指定数量题目，题目属于目标知识点 |

### Task 6.5: 解题历史星级评分

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.19 | `solve_records` 新增 `rating` 列（1–5，可为空） | 迁移成功 |
| 6.20 | `PATCH /api/v1/records/{recordId}/rating` — 保存家长星级评分 | 评分写入 DB，历史列表返回 rating 字段 |
| 6.21 | 评分后系统建议掌握度（4–5星→掌握，2–3星→了解，1星→未知），家长可接受或覆盖 | 接受建议后 `knowledge_progress.mastery_level` 更新 |

### Task 6.6: 前端新页面

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.22 | 知识图谱页：树形列表，掌握度徽章（未知灰/了解黄/掌握绿），支持手动修改掌握度 | 修改后徽章颜色实时更新 |
| 6.23 | 解题历史页：按学生筛选，显示题目摘要 + 知识点标签 + 星级 + 时间 | 列表按时间倒序，星级显示正确 |
| 6.24 | 解题结果页底部新增星级评分组件，提交后显示确认 | 评分成功写入，刷新历史页可见 |
| 6.25 | 主导航：解题 / 知识图谱 / 历史记录 三个入口 | 页面间切换流畅，无需刷新 |

**Phase 6 交付物：** 登录态持久化、学生管理规范化、完整知识图谱与掌握度追踪、题库测评、历史星级评分。

---

## Phase 7: 本地性能优化

> **目标**：在 Ollama 本地推理环境下把用户体验做到最好，缓存命中率 ≥ 60%。
>
> *(原 Phase 6)*

### Task 7.1: 语义缓存

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 7.1 | 语义缓存：解题前先用题目 embedding 在 vector_store 中查询相似历史解题结果（cosine ≥ 0.95），命中则直接返回 | 相似题目跳过 LLM 调用，日志显示 "Semantic cache hit" |
| 7.2 | 常见题型预热：应用启动时批量解题并存入 Redis，覆盖 40 道题库中的典型题 | 预热后典型题响应 < 1s |

### Task 7.2: 稳定性加固

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 7.3 | LLM 调用重试机制（最多 2 次，指数退避 1s/2s），区分可重试错误（超时）与不可重试错误（400） | 单次超时后自动重试，最终仍失败才返回错误 |
| 7.4 | PgVector HNSW 索引有效性验证：`EXPLAIN ANALYZE` 确认走索引，相似度检索 < 100ms | 查询计划显示 Index Scan |

**Phase 7 交付物：** 本地响应快，常见题秒级返回，偶发 LLM 错误自动重试。

---

## Phase 8: 高阶功能（按需）

> **目标**：从解题工具进化为自适应学习平台，按实际使用反馈决定优先级。
>
> *(原 Phase 7)*

### Task 8.1: 薄弱点分析与推荐

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 8.1 | 薄弱点分析：基于 `knowledge_progress` 和星级评分，识别需强化知识点 | `GET /api/v1/knowledge/{studentId}/weaknesses` 返回排序列表 |
| 8.2 | 个性化推荐：基于薄弱知识点从题库 + RAG 推荐下一道题 | 推荐结果偏向薄弱知识点 |

### Task 8.2: 自适应学习路径

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 8.3 | 知识点前置关系建模（如 `ratio.basic` 依赖 `whole_numbers`），录入 `knowledge_nodes.prerequisites` | 前置关系数据完整 |
| 8.4 | 自适应学习路径：根据掌握情况规划下一步目标 | 路径推荐 API 返回有序知识点序列 |

### Task 8.3: 多模态输入（可选）

| # | 任务 | 说明 |
|:--|:-----|:-----|
| 8.5 | OCR 图片题目识别 | 调用多模态 LLM（如 Qwen-VL）或集成 Tesseract |
| 8.6 | 手写数学公式识别 | 依赖 OCR 基础能力 |

**Phase 8 交付物：** 具备个性化推荐和学习路径规划的完整学习平台。

---

## Phase 9: 生产部署（本地开发完成后）

> **前提**：Phase 6–7 全部完成，本地应用功能完整、质量达标后再进行。
>
> **部署目标**：homelab k8s 集群，所有基础设施（PostgreSQL、Redis、Ollama）由 homelab 自行提供。
>
> *(原 Phase 8)*

### Task 9.1: 质量验收

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 9.1 | 准备 20 道 PSLE 标准题验收集，端到端验证 AI 输出质量 | 正确率 ≥ 90%，格式合规 |
| 9.2 | 单元测试覆盖率提升至 80%+（重点：Agent 链、缓存、RAG） | `./gradlew jacocoTestReport` 达标 |

### Task 9.2: CI 镜像构建

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 9.3 | CI `publish` job：main 分支 push 后自动构建后端镜像并推送至 `ghcr.io/<owner>/math-learning/backend` | GitHub Actions 绿色，镜像可见（`sha-<hash>` + `latest`） |
| 9.4 | CI 同步构建前端镜像（nginx + Wasm）并推送至 `ghcr.io/<owner>/math-learning/frontend` | 同上 |

### Task 9.3: Homelab k8s 部署

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 9.5 | 创建 k8s Secret：datasource-password、jwt-secret | `kubectl get secret math-learning-secrets` 存在 |
| 9.6 | ArgoCD Application 指向 `deployment/charts/math-learning/`，配置 `ollamaBaseUrl`、`datasourceUrl` 等 values | ArgoCD 状态 Synced + Healthy |
| 9.7 | `kubectl get pods` backend + frontend 均 Running；访问 `math.homelab.local` 完整可用 | 浏览器端到端走通 |

### Task 9.4: 可观测性

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 9.8 | 集成 Micrometer + Prometheus，暴露 `/actuator/prometheus` | homelab Prometheus 成功抓取 |
| 9.9 | LLM 调用耗时、缓存命中率、错误率写入 Prometheus，配置 Grafana dashboard | Grafana 可查看实时指标 |

**Phase 9 交付物：** 运行在 homelab k8s 上的完整应用，前后端内网可访问，监控就绪。

---

## 当前优先级速查

| 优先级 | 任务 |
|:-------|:-----|
| **立即（Phase 7）** | 语义缓存、LLM 重试、pgvector 性能验证 |
| **按需（Phase 8）** | 薄弱点推荐、自适应学习路径、OCR |
| **最后（Phase 9）** | CI 镜像、k8s 部署、可观测性 |
