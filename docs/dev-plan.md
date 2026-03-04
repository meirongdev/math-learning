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

## Phase 2: RAG 知识库 + Prompt 优化

### Task 2.1: 向量知识库搭建

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 2.1 | 编写题目导入脚本：读取 JSON/CSV 题库 → 调用 Embedding API → 写入 `sg_math_questions` 表 | `SELECT count(*) FROM sg_math_questions;` 有数据 |
| 2.2 | 集成 Spring AI VectorStore（PgVectorStore），配置 cosine 相似度 | 代码中 `vectorStore.similaritySearch(query)` 返回相关题目 |
| 2.3 | 收集 20-50 道新加坡 PSLE 真题（P4-P6 代数、比例、平均数），格式化为 JSON | 题库文件就绪 |

### Task 2.2: RAG 融入 Agent 链

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 2.4 | Planner Agent 先做 RAG 检索（Top-5 相似题），将检索结果作为上下文注入 Prompt | 解题输出引用了相似题的解法模式 |
| 2.5 | 添加 grade 过滤：只检索 ≤ 当前年级的题目 | P3 题目不会返回 P6 级别的解法 |

### Task 2.3: Prompt 微调 + 缓存

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 2.6 | 微调各 Agent 的 System Prompt，确保输出严格符合 PSLE 评分标准 | 用 10 道标准题对比 AI 输出与标准答案 |
| 2.7 | Redis 缓存：相同题目 + 年级的 AI 响应缓存 24h | 第二次请求命中缓存，响应 < 50ms |

**Phase 2 交付物：** 具备 PSLE 知识库支撑的增强型解题器。

---

## Phase 3: Web 前端 (Compose Wasm)

### Task 3.1: 前端骨架

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 3.1 | `cd frontend && ./gradlew :webApp:wasmJsBrowserDevelopmentRun` 启动成功 | 浏览器打开 localhost:8080 显示 "SG Math Tutor" |
| 3.2 | 解题页面：文字输入框 + 年级选择器 + Solve 按钮 | UI 渲染正常，交互可用 |

### Task 3.2: 前后端联调

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 3.3 | 对接 `/api/v1/solve/stream`，SSE 流式渲染解题结果 | 点击 Solve 后逐字显示解析内容 |
| 3.4 | 分区展示：家长导引区 + 儿童脚本区 + Bar Model 描述区 | 三个区域独立显示 |
| 3.5 | CORS 配置：后端允许前端域名跨域请求 | 前端请求无 CORS 错误 |

### Task 3.3: UI 打磨

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 3.6 | Loading 状态、错误提示、空状态处理 | 各异常路径 UI 友好 |
| 3.7 | 移动端响应式适配（Wasm 在手机浏览器中可用） | 手机浏览器打开无布局错乱 |

**Phase 3 交付物：** 可在线交互的解题工具 Web 端。

---

## Phase 4: 闭环验证与发布

### Task 4.1: 端到端测试

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 4.1 | 编写后端集成测试（Testcontainers + PostgreSQL + Ollama） | `./gradlew test` 全绿 |
| 4.2 | 准备 20 道 PSLE 标准题验收集，逐题验证 AI 输出质量 | 正确率 ≥ 90%，格式合规 |

### Task 4.2: 部署

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 4.3 | 后端 Docker 镜像构建：`docker build -f infra/backend.Dockerfile -t math-learning-backend .` | 镜像构建成功 |
| 4.4 | 后端部署至 Fly.io / AWS ECS（切换 prod profile，接入 DeepSeek API） | 线上 API 可访问 |
| 4.5 | 前端构建 Wasm 产物，部署至 Vercel / Cloudflare Pages | 线上页面可访问 |

### Task 4.3: 上线与反馈

| # | 任务 | 验证方式 |
|:--|:-----|:---------|
| 4.6 | 配置生产环境监控（日志 + 错误告警） | 可在 dashboard 查看请求日志 |
| 4.7 | 发布内测链接，邀请 5-10 位家长试用 | 收到首批反馈 |

**Phase 4 交付物：** 线上可用的 MVP 版本 + 首批用户反馈。
