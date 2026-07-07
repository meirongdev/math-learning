# BYOK 改进方案：用户自配 AI API（最小功能集）

> 状态：提案（2026-07-05）。目标是让用户在 App 内配置自己的 AI API（Bring Your Own Key），
> 使解题、苏格拉底引导等 AI 功能跑在用户自己的模型上，而不依赖服务器部署时写死的 provider。

---

## 1. 现状 Review

### 1.1 LLM 接入是部署期决定的，用户无法干预

- `OllamaConfig#chatClient` 把 `ChatClient` 硬编码为 `OllamaChatModel` 的单例 Bean
  （`backend/src/main/java/com/mathlearning/config/OllamaConfig.java:94`）。
- `MathSolverOrchestrator` 直接注入这个单例，全部 LLM 调用走同一个 provider。
- provider 切换靠 Spring profile：dev → Ollama（qwen3.5），prod → DeepSeek（`spring.ai.openai.*`）。

### 1.2 发现的潜在缺陷（本方案顺带修复）

`application-prod.yml` 配置了 DeepSeek（`spring.ai.openai.base-url` + `deepseek-reasoner`），
但 `chatClient` Bean 无条件绑定 `OllamaChatModel`，且 prod profile 并未排除 Ollama 自动装配。
**prod 环境下 ChatClient 实际仍指向默认的 `localhost:11434` Ollama，DeepSeek 配置是死配置。**
M2 引入的动态 ChatClient 工厂会替换这个硬编码 Bean，一并解决。

### 1.3 Embedding / RAG / 语义缓存的耦合

- pgvector 固定 768 维（nomic-embed-text），HNSW 索引；`RagRetrievalService`、
  `SemanticCacheService`、`QuestionImportService` 共用同一 `VectorStore`。
- 若允许用户自配 embedding 模型，维度不一致会击穿整个向量库 → **embedding 必须保持服务端管理**。

### 1.4 前端没有 AI 设置入口

- Web（Wasm）无设置页；Android 已有 DataStore 存后端 URL 的先例，可复用同一设置页模式。
- `MathApi` 是所有平台共享的 HTTP 层，新增设置接口只需在 shared 模块加方法。

---

## 2. 目标与非目标

### 目标（最小可用）

1. 用户登录后可配置一个 **OpenAI 兼容** 的 AI API：`base URL + API key + model 名`。
2. 配置后，该用户的 solve 全链路（planner、讲解、苏格拉底模式）使用其自配模型。
3. 提供"测试连接"能力，保存前可验证配置可用。
4. 未配置的用户回退到服务器默认 provider，现有行为完全不变。

### 非目标（刻意砍掉，控制最小集）

- ❌ 多 provider SDK 适配（Anthropic/Gemini 原生协议等）。**只支持 OpenAI 兼容协议**——
  它一种协议即可覆盖 DeepSeek、Kimi/Moonshot、Qwen（DashScope 兼容模式）、OpenRouter、
  OpenAI、vLLM/LM Studio，甚至用户本地 Ollama 的 `/v1` 端点。
- ❌ 用户自配 embedding 模型（见 1.3，维度锁死 768）。
- ❌ 用量统计、计费、配额管理。
- ❌ 多套配置切换（每用户只存一份，可编辑）。

---

## 3. 最小功能集（4 个模块）

### M1 — 数据模型与设置 API（后端）

**新 Flyway 迁移 `V5__user_ai_settings.sql`：**

```sql
CREATE TABLE user_ai_settings (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    base_url      VARCHAR(500)  NOT NULL,
    api_key_enc   VARCHAR(1000) NOT NULL,   -- AES-GCM 加密存储
    model_name    VARCHAR(100)  NOT NULL,
    enabled       BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**REST 端点（DTO 用 Java record，遵循项目惯例）：**

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/settings/ai` | 返回当前用户配置；API key 只返回掩码（如 `sk-***f3a`） |
| `PUT` | `/api/v1/settings/ai` | 创建/更新配置；key 字段为空表示保留原 key |
| `DELETE` | `/api/v1/settings/ai` | 删除配置，回退服务器默认 |
| `POST` | `/api/v1/settings/ai/test` | 用提交的配置发一次最小 chat 调用，返回 `{ok, latencyMs, error?}` |

**API key 加密：** AES-256-GCM，密钥来自环境变量 `APP_SETTINGS_ENCRYPTION_KEY`
（dev 给默认值，模式同现有 `app.jwt.secret`）。任何响应/日志不得出现明文 key。

### M2 — 动态 ChatClient 解析（后端核心）

**新增 `ChatClientProvider` 服务，替换 `OllamaConfig#chatClient` 硬编码 Bean：**

```java
public interface ChatClientProvider {
    ChatClient forCurrentUser();   // 从 SecurityContext 取 userId
    ChatClient serverDefault();    // 启动时按 profile 构建（Ollama 或 OpenAI 兼容）
}
```

- 用户有 enabled 配置 → 用 Spring AI 的 `OpenAiChatModel` **编程式构建**
  （`OpenAiApi.builder().baseUrl(...).apiKey(...)` + `OpenAiChatOptions.model(...)`），
  不依赖自动装配，因此每个用户可以有不同实例。
- **实例缓存：** Caffeine，key = 配置内容 hash，TTL 30 分钟 + 设置更新时主动逐出，
  避免每次请求重建 HTTP client。
- **错误语义（重要决策）：** 用户自配模型调用失败时**不静默回退**服务器默认，
  而是返回明确错误（"你配置的 AI 服务不可用，请检查设置"）。静默回退会违背用户对
  隐私和费用的预期。可复用现有 `GlobalExceptionHandler` + `LlmTimeoutException` 体系。
- **调用点改造：** `MathSolverOrchestrator` 的构造注入 `ChatClient` 改为注入
  `ChatClientProvider`，`chatClient.prompt()` 调用点（`MathSolverOrchestrator.java:254`）
  改为 `provider.forCurrentUser().prompt()`。

**缓存策略配套调整：**

- L1 精确匹配缓存：cache key 追加模型指纹（`provider+model` hash），
  避免 A 用户的 qwen 结果命中 B 用户的 deepseek 请求。
- L2 语义缓存：**仅对服务器默认模型读写**（最小改动：BYOK 请求跳过语义缓存）。
  Embedding 本身始终用服务端模型，不受用户配置影响。
- RAG 检索不变（embedding 服务端管理）。

### M3 — 前端设置页（shared + Web + Android）

**shared 模块：**

- `Models.kt` 新增 `AiSettingsRequest` / `AiSettingsResponse` / `AiTestResult`。
- `MathApi` 新增 `getAiSettings()` / `updateAiSettings()` / `deleteAiSettings()` / `testAiSettings()`。

**Web（Wasm）：** 顶部导航加"设置"入口（或用户菜单弹窗），表单包含：

- 预设下拉：DeepSeek / OpenAI / Kimi / Qwen / OpenRouter / 自定义
  （预设只是自动填 base URL 和推荐 model，落库结构完全相同）；
- base URL、API key（密码框，回显掩码）、model 名；
- "测试连接"按钮（调 `/test`，显示延迟或错误）＋ 保存 ＋ 恢复默认。

**Android：** 在现有设置界面（后端 URL 配置旁）加同一套表单，复用 shared 的 API 方法。

### M4 — 安全与质量（最小但不可省）

1. **SSRF 防护：** base_url 是用户输入、由服务器发起出站调用。校验：仅允许 `https`
   （localhost/局域网地址在 dev profile 放行以支持本地 Ollama）；解析后拒绝
   link-local（169.254.0.0/16，含云 metadata 端点）。
2. **日志脱敏：** 现 `application.yml` 里 `org.springframework.ai: DEBUG` 会打印请求体/头，
   BYOK 后可能泄漏用户 key —— prod 必须降为 `INFO`，dev 保留但文档标注风险。
3. **限流：** `/api/v1/settings/ai/test` 纳入现有 `RateLimitFilter`（如 5 次/分钟/用户），
   防止被当作免费代理探测器。
4. **测试：**
   - 加密 round-trip 单测；
   - `SettingsController` 端点测试（掩码返回、key 留空保留旧值）；
   - `ChatClientProvider` 单测（有/无配置的路由、缓存逐出）；
   - `MathApiTest` 补 shared 新方法。

---

## 4. 实施顺序与工作量估算

| 步骤 | 内容 | 依赖 | 估算 |
|------|------|------|------|
| 1 | M1：迁移 + 实体 + 加密 + 设置 CRUD 端点 | 无 | 1 天 |
| 2 | M2：`ChatClientProvider` + 编排器改造 + 缓存键调整 | 1 | 1.5 天 |
| 3 | M1 补充：`/test` 连接测试端点 | 2 | 0.5 天 |
| 4 | M3：shared DTO/API + Web 设置页 | 1 | 1 天 |
| 5 | M3：Android 设置页 | 4 | 0.5 天 |
| 6 | M4：SSRF/日志/限流/测试收尾 | 2,3 | 1 天 |

合计约 **5.5 人天**。步骤 1-3 完成后后端即可用 curl 端到端验证，前端可并行。

---

## 5. 验收标准

1. 未配置用户：所有现有功能行为不变（回归 47 个既有测试全绿）。
2. 配置 DeepSeek key 的用户：`/api/v1/solve` 由 DeepSeek 出答案
   （后端日志可见出站请求指向用户 base_url；掩码显示，无明文 key）。
3. 配错 key：solve 返回明确错误提示，不静默回退、不崩溃。
4. "测试连接"能区分：URL 不通 / key 无效 / model 不存在三类错误。
5. `GET /api/v1/settings/ai` 任何时候不返回明文 key。
6. prod profile 下服务器默认 provider 正确指向 DeepSeek（顺带修复 1.2 的死配置问题）。

---

## 6. 后续可选增强（明确不在本期）

- 用户自选多套配置 + 快速切换；
- 按模型能力自动调 prompt（如 reasoner 类模型跳过 CoT 指令）;
- 用量/费用展示（token 统计已在 Spring AI 响应元数据里，展示层工作）；
- Anthropic / Gemini 原生协议支持。
