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

## Phase 4: 代码质量与健壮性

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

## Phase 5: 功能完整性

> **目标**：补齐数据库已建表但尚未接通的功能，让应用形成完整闭环。
>
> 当前状态：`student_profiles`、`solve_records`、`knowledge_progress` 表已存在，但没有任何 API 端点写入或读取这些数据。Auth 的 JWT 已生成但未被任何端点校验。

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

## Phase 6: 本地性能优化

> **目标**：在 Ollama 本地推理环境下把用户体验做到最好，缓存命中率 ≥ 60%。

### Task 6.1: 语义缓存

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.1 | 语义缓存：解题前先用题目 embedding 在 vector_store 中查询相似历史解题结果（cosine ≥ 0.95），命中则直接返回 | 相似题目跳过 LLM 调用，日志显示 "Semantic cache hit" |
| 6.2 | 常见题型预热：应用启动时批量解题并存入 Redis，覆盖 40 道题库中的典型题 | 预热后典型题响应 < 1s |

### Task 6.2: 稳定性加固

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.3 | LLM 调用重试机制（最多 2 次，指数退避 1s/2s），区分可重试错误（超时）与不可重试错误（400） | 单次超时后自动重试，最终仍失败才返回错误 |
| 6.4 | PgVector HNSW 索引有效性验证：`EXPLAIN ANALYZE` 确认走索引，相似度检索 < 100ms | 查询计划显示 Index Scan |

**Phase 6 交付物：** 本地响应快，常见题秒级返回，偶发 LLM 错误自动重试。

---

## Phase 7: 高阶功能（按需）

> **目标**：从解题工具进化为自适应学习平台，按实际使用反馈决定优先级。

### Task 7.1: 薄弱点分析与推荐

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 7.1 | 薄弱点分析：基于 `knowledge_progress` 统计正确率，识别需强化知识点 | `GET /api/v1/knowledge/{studentId}/weaknesses` 返回排序列表 |
| 7.2 | 个性化推荐：基于薄弱点 + RAG 检索推荐下一道题 | 推荐结果偏向薄弱知识点 |

### Task 7.2: 知识图谱

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 7.3 | 知识点前置关系建模（如 `ratio.basic` 依赖 `whole_numbers`） | 知识图谱数据结构设计完成并录入 |
| 7.4 | 自适应学习路径：根据掌握情况规划下一步目标 | 路径推荐 API 返回有序知识点序列 |

### Task 7.3: 多模态输入（可选）

| # | 任务 | 说明 |
|:--|:-----|:-----|
| 7.5 | OCR 图片题目识别 | 调用多模态 LLM（如 Qwen-VL）或集成 Tesseract |
| 7.6 | 手写数学公式识别 | 依赖 OCR 基础能力 |

**Phase 7 交付物：** 具备个性化推荐和学习路径规划的完整学习平台。

---

## Phase 8: 生产部署（本地开发完成后）

> **前提**：Phase 4–6 全部完成，本地应用功能完整、质量达标后再进行。
>
> **部署目标**：homelab k8s 集群，所有基础设施（PostgreSQL、Redis、Ollama）由 homelab 自行提供。

### Task 8.1: 质量验收

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 8.1 | 准备 20 道 PSLE 标准题验收集，端到端验证 AI 输出质量 | 正确率 ≥ 90%，格式合规 |
| 8.2 | 单元测试覆盖率提升至 80%+（重点：Agent 链、缓存、RAG） | `./gradlew jacocoTestReport` 达标 |

### Task 8.2: CI 镜像构建

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 8.3 | CI `publish` job：main 分支 push 后自动构建后端镜像并推送至 `ghcr.io/<owner>/math-learning/backend` | GitHub Actions 绿色，镜像可见（`sha-<hash>` + `latest`） |
| 8.4 | CI 同步构建前端镜像（nginx + Wasm）并推送至 `ghcr.io/<owner>/math-learning/frontend` | 同上 |

### Task 8.3: Homelab k8s 部署

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 8.5 | 创建 k8s Secret：datasource-password、jwt-secret | `kubectl get secret math-learning-secrets` 存在 |
| 8.6 | ArgoCD Application 指向 `deployment/charts/math-learning/`，配置 `ollamaBaseUrl`、`datasourceUrl` 等 values | ArgoCD 状态 Synced + Healthy |
| 8.7 | `kubectl get pods` backend + frontend 均 Running；访问 `math.homelab.local` 完整可用 | 浏览器端到端走通 |

### Task 8.4: 可观测性

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 8.8 | 集成 Micrometer + Prometheus，暴露 `/actuator/prometheus` | homelab Prometheus 成功抓取 |
| 8.9 | LLM 调用耗时、缓存命中率、错误率写入 Prometheus，配置 Grafana dashboard | Grafana 可查看实时指标 |

**Phase 8 交付物：** 运行在 homelab k8s 上的完整应用，前后端内网可访问，监控就绪。

---

## 当前优先级速查

| 优先级 | 任务 |
|:-------|:-----|
| **立即（Phase 4）** | 统一 ObjectMapper、全局异常处理、LLM 响应 JSON 解析、输入验证、测试骨架 |
| **接下来（Phase 5）** | JWT 正式启用、学生档案 API、解题记录持久化、知识点掌握度追踪、前端登录页 |
| **之后（Phase 6）** | 语义缓存、LLM 重试、pgvector 性能验证 |
| **按需（Phase 7）** | 薄弱点推荐、知识图谱、OCR |
| **最后（Phase 8）** | CI 镜像、k8s 部署、可观测性 |
