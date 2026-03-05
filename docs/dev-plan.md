# 开发计划 - 具体可执行版 (MVP)

## Phase 0: 前置准备 ✅

```bash
# 1. 安装工具链
brew install openjdk@25        # Java 25 (Temurin-25+36)
brew install gradle            # Gradle 9.2 (升级说明见下方)
brew install kotlin            # Kotlin 2.1+
brew install docker            # Docker Desktop
brew install ollama            # 本地 LLM

# 2. 启动基础设施
cd infra && docker compose up -d

# 3. 拉取本地模型
ollama pull qwen3.5           # 对话模型 (~6.6GB)
ollama pull nomic-embed-text   # Embedding 模型 (~274MB, 768维向量)

# 4. 验证
ollama run qwen3.5 "1+1=?"   # 确认模型可用
psql postgresql://mathlearning:mathlearning@localhost:5432/mathlearning -c "SELECT 1;"
```

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

### Task 1.1: 后端骨架跑通

| # | 任务 | 验证方式 | 状态 |
|:--|:-----|:---------|:-----|
| 1.1 | `cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'` 启动成功 | 控制台无报错，8080 端口可访问 | ✅ |
| 1.2 | Flyway 自动执行 `V1__init_schema.sql`，建表完成 | `\dt` 查看 5 张应用表 + vector_store + flyway_schema_history = 7 张表已创建 | ✅ |
| 1.3 | `/api/v1/auth/register` 注册用户成功 | `curl` 返回 201 + userId，重复注册返回 409，登录返回 JWT token | ✅ |

### Task 1.2: Agent 链调通

| # | 任务 | 验证方式 | 状态 |
|:--|:-----|:---------|:-----|
| 1.4 | 调通 `MathSolverOrchestrator`，单次调用返回结构化 JSON | 集成测试通过，输入 "5+3=?" 得到 parentGuide + childScript + barModelJson + knowledgeTags | ✅ |
| 1.5 | 编写 Planner Agent 的 System Prompt（新加坡 CPA 教学法、PSLE 评分标准） | System Prompt 包含 CPA 教学法、知识点提取、JSON 结构化输出格式 | ✅ |
| 1.6 | 编写 Persona Agent 的 System Prompt（家长版 vs 儿童版语气） | System Prompt 生成 parentGuide（专业指导语气）+ childScript（趣味冒险语气） | ✅ |

### Task 1.3: SSE 流式端到端

| # | 任务 | 验证方式 | 状态 |
|:--|:-----|:---------|:-----|
| 1.7 | `/api/v1/solve/stream` 返回 SSE 流 | `curl -N` 可见逐字输出；同时新增了非流式 `/api/v1/solve` 端点 | ✅ |

**Phase 1 交付物：** ✅ 可运行的后端 API，Ollama 本地模型驱动的解题 Agent 链。

---

## Phase 2: RAG 知识库 + Prompt 优化 ✅ <small>(已完成)</small>

### Task 2.1: 向量知识库搭建

| # | 任务 | 验证方式 | 状态 |
|:--|:-----|:---------|:-----|
| 2.1 | 编写题目导入脚本：读取 JSON 题库 → 调用 Embedding API → 写入 `vector_store` 表 | `SELECT count(*) FROM vector_store;` 返回 40 条数据 | ✅ |
| 2.2 | 集成 Spring AI VectorStore（PgVectorStore），配置 cosine 相似度 | `vectorStore.similaritySearch(query)` 返回相关题目 | ✅ |
| 2.3 | 收集 40 道新加坡 PSLE 题目（P4-P6 代数、比例、平均数、分数），格式化为 JSON | 题库文件就绪：`src/main/resources/data/sg-math-questions.json` | ✅ |

### Task 2.2: RAG 融入 Agent 链

| # | 任务 | 验证方式 | 状态 |
|:--|:-----|:---------|:-----|
| 2.4 | Planner Agent 先做 RAG 检索（Top-5 相似题），将检索结果作为上下文注入 Prompt | 日志确认 "RAG retrieval returned 5 similar questions" | ✅ |
| 2.5 | 添加 grade 过滤：只检索 ≤ 当前年级的题目 | P5 题目请求只返回 grade ≤ 5 的题 | ✅ |

### Task 2.3: Prompt 微调 + 缓存

| # | 任务 | 验证方式 | 状态 |
|:--|:-----|:---------|:-----|
| 2.6 | 微调各 Agent 的 System Prompt，加入 PSLE 2026 评分标准、知识点编码体系、CPA 教学法详细指导 | System Prompt 包含 PSLE 评分标准、标准知识点代码、Bar Model 设计规则 | ✅ |
| 2.7 | Redis 缓存：相同题目 + 年级的 AI 响应缓存 24h | `@Cacheable` 注解生效，SolveService 日志显示 "Cache miss" 仅首次触发 | ✅ |

> **⚠️ 实施中的技术发现：**
>
> | 问题 | 解决方案 |
> |:-----|:---------|
> | Spring Boot 4.0 不自动注册 `ObjectMapper` Bean | `QuestionImportService` 内部自行创建 `new ObjectMapper()` |
> | PgVectorStore 使用 `vector_store` 表而非自定义表 | 使用 Spring AI 默认的 `vector_store` 表，将 metadata 字段存储 grade/topic/difficulty |
> | `GenericJackson2JsonRedisSerializer` 已标记 `@Deprecated(forRemoval)` | 改用 `Jackson2JsonRedisSerializer` |
> | 本地 Ollama 多 Agent 管线处理较慢（~2min/请求） | Redis 缓存确保相同题目秒级返回，生产环境切换 DeepSeek-R1 可大幅加速 |

**Phase 2 交付物：** ✅ 具备 PSLE 知识库支撑的增强型解题器。

**Phase 2 新增文件：**
- `backend/src/main/resources/data/sg-math-questions.json` — 40 道 PSLE 题库（P4-P6）
- `backend/src/main/java/com/mathlearning/service/QuestionImportService.java` — 应用启动时自动导入题库
- `backend/src/main/java/com/mathlearning/service/RagRetrievalService.java` — RAG 检索服务（grade 过滤 + Top-5）
- `backend/src/main/java/com/mathlearning/service/SolveService.java` — 缓存层包装
- `backend/src/main/java/com/mathlearning/config/CacheConfig.java` — Redis 缓存配置（24h TTL）

---

## Phase 3: Web 前端 (Compose Wasm) ✅ <small>(已完成)</small>

### Task 3.1: 前端骨架

| # | 任务 | 验证方式 | 状态 |
|:--|:-----|:---------|:-----|
| 3.1 | `cd frontend && ./gradlew :webApp:wasmJsBrowserDevelopmentRun` 启动成功 | 浏览器打开 localhost:8081 显示 "SG Math Tutor"（端口改为 8081 避免与后端冲突） | ✅ |
| 3.2 | 解题页面：文字输入框 + 年级选择器 + Solve 按钮 | UI 渲染正常，交互可用 | ✅ |

### Task 3.2: 前后端联调

| # | 任务 | 验证方式 | 状态 |
|:--|:-----|:---------|:-----|
| 3.3 | 对接 `/api/v1/solve/stream`，SSE 流式渲染解题结果 | 前端通过 Ktor 发送 POST 请求，解析 SSE data 行为结构化 SolveEvent | ✅ |
| 3.4 | 分区展示：家长导引区 + 儿童脚本区 + Bar Model 描述区 + 知识点标签 | 四个区域使用不同 Material3 颜色容器独立显示 | ✅ |
| 3.5 | CORS 配置：后端允许前端域名跨域请求 | SecurityConfig 添加 CorsConfigurationSource，CORS preflight 返回 200 | ✅ |

### Task 3.3: UI 打磨

| # | 任务 | 验证方式 | 状态 |
|:--|:-----|:---------|:-----|
| 3.6 | Loading 状态、错误提示、空状态处理 | Loading 卡片含进度指示器，错误显示在 errorContainer 中，空状态显示引导文本 | ✅ |
| 3.7 | 移动端响应式适配（Wasm 在手机浏览器中可用） | viewport meta 标签 + widthIn(max=600.dp) 实现桌面居中 + 移动端自适应 | ✅ |

> **⚠️ 实施中的技术发现：**
>
> | 问题 | 解决方案 |
> |:-----|:---------|
> | 前端默认端口 8080 与后端冲突 | 通过 `webpack.config.d/devServer.js` 配置 dev server 端口为 8081 |
> | 前端发送 grade 为 String "P3"，后端期望 int 3 | 前端 `SolveRequest.grade` 改为 `Int`，UI 层 `removePrefix("P").toInt()` 转换 |
> | 前端 API 路径 `/api/solve` 与后端 `/api/v1/solve/stream` 不匹配 | 修复 MathApi.kt 中的请求路径 |
> | 流式端点返回原始文本，前端期望结构化事件 | 重写 SolveController 流式端点，使用多 Agent 管线结果 + ObjectMapper 序列化为 `{type, content}` JSON 事件 |
> | `Modifier.menuAnchor()` 已弃用 | 改用 `Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)` |
> | Spring Boot 4.0 无自动注册 ObjectMapper Bean | SolveController 内部自行创建 `new ObjectMapper()` |

**Phase 3 交付物：** ✅ 可在线交互的解题工具 Web 端（前端 localhost:8081 + 后端 localhost:8080）。

**Phase 3 新增/修改文件：**
- `frontend/webApp/src/wasmJsMain/kotlin/com/mathlearning/web/App.kt` — 完整解题 UI（输入卡片 + 四区域结果展示 + 错误/空状态处理）
- `frontend/webApp/src/wasmJsMain/kotlin/com/mathlearning/web/theme/Theme.kt` — Material3 主题（新增 tertiary、error 配色）
- `frontend/shared/src/commonMain/kotlin/com/mathlearning/shared/api/MathApi.kt` — SSE 流式 API 客户端（修复路径 + 解析逻辑）
- `frontend/shared/src/commonMain/kotlin/com/mathlearning/shared/model/Models.kt` — SolveRequest grade 改为 Int
- `frontend/webApp/webpack.config.d/devServer.js` — dev server 端口配置（8081）
- `backend/src/main/java/com/mathlearning/controller/SolveController.java` — 流式端点重写为结构化 SSE 事件
- `backend/src/main/java/com/mathlearning/config/SecurityConfig.java` — CORS 跨域配置

---

## Phase 4: 闭环验证与 Homelab 部署

> **部署目标**：用户拥有 k8s homelab，所有基础设施（PostgreSQL、Redis、Ollama）由 homelab 自行提供，无需云服务。本阶段目标为在 homelab 上成功部署并端到端验证。

### Task 4.1: 端到端测试

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 4.1 | 编写后端集成测试（Testcontainers + PostgreSQL + Ollama） | `./gradlew test` 全绿 |
| 4.2 | 准备 20 道 PSLE 标准题验收集，逐题验证 AI 输出质量 | 正确率 ≥ 90%，格式合规 |

### Task 4.2: 容器镜像构建

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 4.3 | 后端镜像构建：`docker build -f infra/backend.Dockerfile -t math-learning-backend .` | 镜像构建成功，`docker run` 本地启动 8080 正常 |
| 4.4 | 前端构建 Wasm 产物并打包进 Nginx 镜像（或直接 static files） | `docker run` 本地浏览器可访问前端 |
| 4.5 | 镜像推送至 homelab 私有镜像仓库（或直接 `docker save` / `kind load`） | homelab 节点可拉取镜像 |

### Task 4.3: Homelab k8s 部署

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 4.6 | 编写 k8s Deployment + Service YAML（backend + frontend），挂载 homelab 提供的 PostgreSQL / Redis / Ollama 连接配置（ConfigMap + Secret） | `kubectl get pods` 全部 Running |
| 4.7 | 配置 Ingress（或 NodePort）对外暴露前后端服务 | 浏览器通过 homelab 域名 / IP 访问完整应用 |
| 4.8 | prod profile 验证：切换至 DeepSeek-R1（或保留 Ollama），验证 Agent 链全流程 | `/api/v1/solve` 返回正确结构化解题结果 |

### Task 4.4: 可观测性

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 4.9 | 集成 Micrometer + Prometheus，暴露 `/actuator/prometheus` 端点 | homelab Prometheus 成功抓取指标 |
| 4.10 | 配置基础告警（JVM 内存、LLM 调用耗时、错误率） | Grafana dashboard 可查看实时指标 |

**Phase 4 交付物：** 运行在 homelab k8s 上的完整 MVP，前后端可通过内网访问，监控就绪。

---

## Phase 5: 代码质量提升

> **目标**：消除技术债，提升系统健壮性与可维护性。

### Task 5.1: JSON 解析与异常处理重构

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 5.1 | 统一 `ObjectMapper` Bean（`@Primary` 单例），替换所有 `new ObjectMapper()` | 全局 Bean 注入正常，无重复实例 |
| 5.2 | 创建 `JsonParserUtil` 工具类，封装类型安全解析，失败抛 `InvalidJsonFormatException` | 格式错误的 LLM 响应抛异常而非崩溃 |
| 5.3 | 添加 `@ControllerAdvice` 全局异常 Handler，统一 `ErrorResponse` 格式 | 所有 4xx/5xx 返回 `{code, message}` 结构 |

### Task 5.2: 输入验证与安全加固

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 5.4 | 题目内容长度限制（≤500字）、年级范围校验（1-6）、数学表达式基础过滤 | 非法输入返回 400 + 明确错误消息 |
| 5.5 | JWT 增强：添加刷新 Token 机制、Token 撤销（Redis 黑名单） | 过期 Token 无法访问，刷新流程正常 |
| 5.6 | 审计日志：记录关键操作（登录、解题请求、异常） | 日志可追踪用户行为 |

### Task 5.3: 测试覆盖

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 5.7 | 单元测试覆盖率提升至 80%+（重点覆盖 Agent 链、缓存、RAG） | `./gradlew jacocoTestReport` 覆盖率报告达标 |
| 5.8 | 边界条件测试：空题目、超长输入、无效年级、LLM 超时 | 全部边界 case 有对应测试用例 |

**Phase 5 交付物：** 健壮的后端，统一异常处理，80%+ 测试覆盖率。

---

## Phase 6: 性能优化

> **目标**：将响应时间从 2 分钟降至 10-30 秒，缓存命中率达 60-80%。

### Task 6.1: 智能缓存策略

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.1 | 语义缓存：基于题目 embedding 相似度（cosine ≥ 0.95）复用缓存结果 | 相似题目命中缓存，日志显示 "Semantic cache hit" |
| 6.2 | 常见题型预生成解题模板（离线批处理，结果存 Redis） | 预热后常见题型响应 < 1s |
| 6.3 | 缓存命中率监控指标：`cache.hit.rate` 暴露至 Prometheus | Grafana 可观测命中率趋势 |

### Task 6.2: LLM 与并发优化

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.4 | 生产环境切换 DeepSeek-R1（Spring AI 切换 provider），本地保留 Ollama dev profile | prod profile 响应时间 < 30s |
| 6.5 | LLM 调用超时控制（30s）+ 重试机制（最多 2 次，指数退避） | 超时后返回友好错误，不阻塞线程 |
| 6.6 | LLM 调用性能监控：`llm.call.duration`、`llm.call.count`（按模型/成功/失败） | Prometheus 可查询，Grafana 告警阈值配置 |

### Task 6.3: 数据库与连接池调优

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 6.7 | PgVector 查询性能：验证 HNSW 索引有效，分析 `EXPLAIN ANALYZE` 输出 | 相似度检索 < 100ms |
| 6.8 | HikariCP 连接池参数调优（`maximumPoolSize`、`connectionTimeout`） | 压测下无连接池耗尽错误 |

**Phase 6 交付物：** 响应速度显著提升，性能监控完善，缓存策略生效。

---

## Phase 7: 功能扩展

> **目标**：从解题工具进化为完整的自适应学习平台。

### Task 7.1: 学习进度与个性化推荐

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 7.1 | 答题历史记录：`solve_history` 表记录学生每次请求、知识点、是否理解 | 历史查询 API 返回正确数据 |
| 7.2 | 薄弱点分析：统计各知识点错误率，识别需强化的领域 | `/api/v1/student/{id}/weaknesses` 返回排序知识点列表 |
| 7.3 | 个性化题目推荐：基于薄弱点 + RAG 检索推荐下一道题 | 推荐结果偏向薄弱知识点 |

### Task 7.2: 知识图谱增强

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 7.4 | 知识点关联关系建模（前置知识、相关知识点） | 知识图谱数据结构设计完成并录入 |
| 7.5 | 自适应学习路径：根据当前掌握情况规划下一步学习目标 | 路径推荐 API 返回有序知识点序列 |

### Task 7.3: 多模态输入（可选）

| # | 任务 | 说明 |
|:--|:-----|:-----|
| 7.6 | OCR 图片题目识别 | 集成 Tesseract 或调用多模态 LLM（如 Qwen-VL） |
| 7.7 | 手写数学公式识别 | 依赖 OCR 基础能力 |

**Phase 7 交付物：** 具备个性化推荐和学习路径规划的完整学习平台。

---

## 技术债务优先级速查

| 优先级 | 任务 |
|:-------|:-----|
| **高**（立即处理） | JSON 解析重构（5.1-5.2）、统一异常处理（5.3）、输入验证（5.4） |
| **中**（Phase 5-6） | JWT 增强（5.5）、测试覆盖（5.7-5.8）、LLM 监控（6.6）、缓存优化（6.1-6.3） |
| **低**（Phase 7） | 学习路径、个性化推荐、OCR |
