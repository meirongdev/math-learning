# 简化方案：让 App 更容易部署和使用（聚焦核心功能）

> 状态：提案（2026-07-05）。目标：`git clone` + 填一个 API key + `docker compose up -d` 即可完整使用；
> 保留核心学习闭环，砍掉只为"将来可能需要"服务的运行时依赖。

---

## 1. 现状盘点

### 1.1 跑起来需要 5 个运行组件

| 组件 | 实际用途 | 必要性 |
|------|---------|--------|
| PostgreSQL 17 **+ pgvector** | 业务表（7 张）+ 向量库 | 业务表必须；向量库只服务 RAG（**68 道种子题**）和语义缓存 |
| Redis 7 | 唯一用途：solve 结果 24h 精确缓存（`CacheConfig`） | 单实例下可用进程内缓存等价替代 |
| Ollama + 2 个模型 | dev 的 chat（qwen3.5）+ **所有环境**的 embedding（nomic-embed-text） | 见 1.3：prod 实际也甩不掉它 |
| 后端容器（Java 25） | 应用本体 | 必须 |
| 前端 nginx 容器 | 只为托管 Compose Wasm 静态文件 | 可合并进后端 |

### 1.2 首次使用门槛（quickstart 现状）

装 Java 25 + Docker + Ollama → 拉 2 个模型（数 GB）→ `make infra-up` → 两个终端分别跑前后端
→ curl 注册/登录拿 JWT → 才能解第一道题。冷启动一次 solve 本地 qwen3.5 约 **16 秒**。

### 1.3 放大部署难度的既有问题

- **prod ChatClient 死配置**（BYOK 提案 §1.2 已发现）：`OllamaConfig#chatClient` 无条件绑定
  `OllamaChatModel`，prod 的 DeepSeek 配置不生效，实际仍打 `localhost:11434`；且 prod 未配置
  embedding 替代来源，RAG/语义缓存也依赖本地 Ollama。**"云端部署"目前并不真正可用。**
- Spring AI 2.0.0-M2 milestone 依赖 + `OllamaConfig` 的 think-field workaround，构建脆弱。
- 三套部署路径并存（本地 make / docker compose / Helm chart），文档版本号互相打架
  （architecture.md: Boot 4.0.3 / Gradle 9.2 vs CLAUDE.md: 4.1.0 / 9.6.1）。
- `make docker-up` 声称"including backend container"，实际 compose 里根本没有 backend 服务。

---

## 2. 目标与原则

**目标**：部署 = 任意 Postgres + 一个 app 容器 + 一个 OpenAI 兼容 API key；使用 = 打开页面直接解题。

**原则**：

1. **核心闭环一行不砍**：题目输入（拍照 OCR / 文字）→ AI 讲解（家长引导 + 孩子话术 + 条形图，
   ORIGINAL / SOCRATIC 双模式）→ 家长评分 → 错题本 / 知识点进度 → 自适应推荐。
2. 每砍一个依赖都写明**等价替代**和**什么时候需要加回来**，不做单向门决策。
3. 第一优先场景是**单实例家庭 / 自部署**；横向扩展是将来时，不为它预付成本。

---

## 3. 简化措施

### S1 — 去掉 Redis，改用 Caffeine（半天，零功能损失）

Redis 在本项目只做一件事：`solveResults` 的 24h 精确匹配缓存。而 Caffeine 已经是项目依赖
（`RateLimitFilter`、语义缓存 L2 都在用）。单实例下进程内缓存完全等价。

- 改动：`CacheConfig` 换 `CaffeineCacheManager`（保留 24h TTL + 上限条目数）；删
  `spring-boot-starter-data-redis` 依赖、compose 的 redis 服务、prod 的 `REDIS_*` 环境变量。
- 代价：重启丢缓存（有算术快速路径和云 API 低延迟兜底，可接受）。
- 加回条件：部署多实例、需要跨实例共享缓存时。

### S2 — 前端合并进后端 jar（1 天）

Wasm 产物是纯静态文件。把 `webApp/build/dist/wasmJs/productionExecutable` 在打包时注入
backend 的 `static/`（Gradle task 串联），加一个 wasm MIME 映射即可。

- 收益：删 `frontend.Dockerfile` 和 nginx 容器；单端口、CORS 配置消失；部署产物 = 1 个 jar。
- 开发模式不变：本地照旧 `frontend-run` 热更新 + 后端 8080。

### S3 — 默认 LLM 改为 OpenAI 兼容云 API，Ollama 降为可选（1–2 天）

这一步同时修掉 1.3 的真 bug：

- `ChatClient` 按配置构建：配了 `spring.ai.openai.*` 就走 OpenAI 兼容 provider（DeepSeek /
  Kimi / OpenRouter / 本地 vLLM 均可），否则走 Ollama。这正是 BYOK 提案 M2 的
  `serverDefault()` 那一半，**可以先只做这一半**，per-user BYOK 以后无缝叠加。
- 默认部署只需要 3 个 env：`LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL`。
- 想完全离线的用户保留 `ollama` profile，行为同现状。
- 附带收益：云 API 首答 2–5 秒（对比本地 16 秒），"使用体验"层面的最大单项提升。

### S4 — RAG 降级为标签/年级检索，去掉 pgvector 硬依赖（1–2 天，最大决策点）

**现状**：向量库全部内容 = 68 道已按知识点打标签的种子题；检索结果的用途 = 给 planner prompt
塞 3 道相似题做 few-shot。为这个效果付出的代价：pgvector 扩展 + 768 维锁定 + HNSW 索引 +
embedding 模型（Ollama 的最后一个硬依赖）+ 三个服务类。

**替代**：`assessment_questions` 本来就有 grade 字段和知识点标签（`assessment_question_tags`）。
用"年级过滤 + 标签/关键词匹配"选 3 道题，对 68 道题的库效果差异可忽略。

- 改动：`RagRetrievalService` / `SemanticCacheService` / `QuestionImportService` 三个类
  收敛为一个简单的 `QuestionLookupService`（纯 SQL）；语义缓存 L2 一并去掉（有 L1 精确
  缓存 + 算术快速路径兜底，个人部署下语义缓存命中率本来就低）。
- 收益：**embedding 模型依赖归零**（S3 之后 Ollama 就只剩这一个用途）；Postgres 不再需要
  pgvector 扩展 → 任意托管 PG（RDS / Neon / Supabase）可直接用。
- 加回条件：题库到千级规模、或引入用户自由文本内容检索时再上向量库——届时 embedding
  应走云 API 而不是本地模型。种子题随时可重新导入，**没有单向门**。
- 保守替代：只砍语义缓存、保留 RAG。但那样 embedding 依赖还在，部署简化收益少一半，不推荐。

### S5 — 单用户模式，省掉注册流程（半天）

家庭自部署 90% 是单账号。加 `app.auth.mode=single-user`：从 env 读邮箱密码，启动时 seed
账号，前端登录页预填。JWT 结构与安全模型不动，只是省掉注册步骤；多用户模式仍是默认可选。

### S6 — 部署路径收敛为一条（半天）

- `docker-compose.yml` 升级为**完整应用**：`postgres` + `app` 两个服务 + `.env.example`
  （`JWT_SECRET`、LLM 三项、单用户账号），`docker compose up -d` 一步起全部。
- Helm chart 归档（homelab 场景 compose 足够；需要 K8s 时从 git 历史恢复）。
- Makefile 收敛到高频 target（setup / dev / test / stop），修复 `docker-up` 的假语义。

### S7 —（可选）工具链降险

- Spring Boot / Spring AI 跟进 GA 版本，删掉 `OllamaConfig` 的 think-field workaround。
- 审计 `--enable-preview`：没实际用到 preview 特性就关掉，少一个 JVM 兼容变量。
- 统一各文档的版本号声明（以 gradle catalog 为准）。

---

## 4. 简化前后对比

| 维度 | 现在 | 简化后 |
|------|------|--------|
| 运行组件 | 5（PG+pgvector / Redis / Ollama / 后端 / 前端 nginx） | **2**（任意 PG / app） |
| 必装本地模型 | 2 个（数 GB） | **0**（离线场景可选装） |
| 首次启动 | 约 6 步 + 拉模型 | clone → 填 key → `compose up` |
| 首答延迟（非缓存） | 本地 ~16s | 云 API 2–5s |
| 缓存层 | Redis L1 + 语义 L2 + 管线 | Caffeine L1 + 管线 |
| service 类 | 8 个 | ~6 个 |
| 部署路径 | make / compose / Helm 三套 | compose 一套（make 包装） |

## 5. 功能取舍清单

| 处置 | 内容 |
|------|------|
| **保留**（核心闭环） | solve 管线全部、算术快速路径、双讲解模式、OCR（浏览器/Android 端执行，不占服务端）、学生档案、记录+评分、错题本、知识图谱种子+进度、成就墙（动态计算无新表，维护成本≈0）、自适应推荐 |
| **冻结**（保码不投入） | 导出/PDF（Phase 9 骨架）、掌握树手动编辑 UI |
| **砍掉** | Redis、语义缓存、向量 RAG（降级标签检索）、独立前端容器、Helm chart |
| **平台策略** | Android 为主（拍照解题的主场景）+ Web 作家长桌面入口；shared 模块保持薄 API 层，不加新平台 |

## 6. 实施顺序（合计约 4–6 人天，每步独立可合并）

| 步 | 内容 | 依赖 |
|----|------|------|
| 1 | S1 Redis → Caffeine | 无 |
| 2 | S3 ChatClient 修复 + 云 API 默认（修真 bug，优先） | 无 |
| 3 | S4 RAG 降级 + 去 pgvector | 2（embedding 决策落定） |
| 4 | S2 前端合并 + S6 compose/Makefile/Helm 收敛 | 1–3 |
| 5 | S5 单用户模式 + 文档统一（S7 可穿插） | 4 |

## 7. 与 BYOK 提案的关系

本方案与 `byok-ai-provider-plan.md` 互补：S3 实现的就是 BYOK M2 里的 `serverDefault()`
（并顺带修复其 §1.2 发现的死配置缺陷）；S4 砍掉语义缓存后，BYOK 的"L2 仅对默认模型读写"
调整自动消解。先做本方案，BYOK 的 per-user 配置（M1/M3/M4）随后叠加，互不冲突。
