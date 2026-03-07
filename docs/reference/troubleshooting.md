# Troubleshooting Notes

## 1. qwen3.5 Thinking Mode 导致 content 为空

**现象**: 前端提交问题后返回空 JSON `{"parentGuide":"","childScript":"","barModelJson":"{}","knowledgeTags":[]}`

**根因**: qwen3.5 属于 thinking-capable 模型，Ollama 0.12+（当前环境 0.17.6） 默认开启 thinking 模式。开启后模型会先在 `thinking` 字段输出推理过程，再在 `content` 字段输出最终答案。Spring AI 2.0.0-M2 的 `ChatClient.call().content()` 返回的 `content` 为空，因为：

1. thinking tokens 消耗了大部分 `num_predict` 预算（如果设了上限），导致 `content` 被截断（`finishReason=length`）
2. 即使不设 `num_predict` 上限，Spring AI 的 `OllamaChatModel` 在处理 thinking 响应时 `content()` 也可能返回空字符串

**验证方法**: 直接 curl Ollama 确认模型正常返回内容：
```bash
curl -s http://localhost:11434/api/chat -d '{
    "model": "qwen3.5",
  "messages": [{"role": "user", "content": "1+1=?"}],
  "stream": false, "think": false
}'
```

对比 `think: false` (content 有内容, ~500ms) vs 不设 think (content 可能为空, thinking 字段消耗大量 tokens, ~15s)。

## 2. Spring AI 2.0.0-M2 的 disableThinking() Bug

**现象**: 使用 `OllamaChatOptions.builder().disableThinking().build()` 后，Ollama 返回 HTTP 400:
```
think must be a boolean or string ("high", "medium", "low", true, or false)
```

**根因**: Spring AI 2.0.0-M2 存在 bug — `OllamaChatOptions` 的 `think` 字段同时出现在两个位置：
1. `ChatRequest` 顶层 `"think": false`（正确）
2. `ChatRequest.options` map 中 `"think": <ThinkOption object>`（错误）

Ollama 的 `options` 字段只接受模型参数（temperature, num_predict 等），不识别 `think`。

**代码路径**: `OllamaChatModel.ollamaChatRequest()` 方法中：
```java
.options(requestOptions)          // 调用 requestOptions.toMap()，think 泄漏进 options map
.think(requestOptions.getThinkOption())  // 正确设到顶层
```

`OllamaChatOptions.filterNonSupportedFields()` 的 `NON_SUPPORTED_FIELDS` 列表缺少 `"think"`。

**尝试过的无效方案**:
- `ChatClient.prompt().options(OllamaChatOptions.disableThinking())` — 同样触发 bug
- `ChatModel.call(new Prompt(..., OllamaChatOptions.disableThinking()))` — 同样触发 bug
- 子类化 `OllamaChatOptions` 重写 `toMap()` — `ModelOptionsUtils.merge()` 创建新的 `OllamaChatOptions` 实例，子类方法被丢弃

**方案对比**:

| | 方案 A: 裸 RestClient | 方案 B: ClientHttpRequestInterceptor |
|---|---|---|
| 做法 | 绕过 `OllamaChatModel`，直接构造 HTTP 请求 | 保留 `ChatClient`，在 HTTP 层拦截并修复请求体 |
| Spring AI 功能 | 全部丢失（prompt template、output parser、advisor chain、observability） | 全部保留 |
| 代码量 | 需自行管理 RestClient、超时、响应解析 | 一个 `RestClientCustomizer` bean (~20 行) |
| 切换 LLM provider | 需重写调用层 | 改 application.yml 即可 |
| 升级移除成本 | 需重写回 ChatClient | 删掉 interceptor bean 即可 |
| 风险 | 无——完全控制请求体 | 拦截器作用于该 RestClient 的所有请求（非 Ollama 请求也会被解析） |

**最终方案**: 使用方案 B（`RestClientCustomizer` + `ClientHttpRequestInterceptor`），在 HTTP 层从 `options` map 中移除泄漏的 `think` 字段，保留 Spring AI 全部功能。等 [spring-ai#5435](https://github.com/spring-projects/spring-ai/pull/5435) 合入正式版后删除该 interceptor。

```java
// OllamaConfig.java
@Bean
RestClientCustomizer ollamaThinkFieldFixCustomizer() {
    return restClientBuilder -> restClientBuilder.requestInterceptor((request, body, execution) -> {
        var tree = objectMapper.readTree(body);
        if (tree.has("options") && tree.get("options").has("think")) {
            ((ObjectNode) tree.get("options")).remove("think");
            body = objectMapper.writeValueAsBytes(tree);
        }
        return execution.execute(request, body);
    });
}
```

## 3. RestClient 默认使用 Reactor Netty 导致 ReadTimeoutException

**现象**: `RestClient` 调用 Ollama 时抛出 `io.netty.handler.timeout.ReadTimeoutException`

**根因**: 当 Spring WebFlux 在 classpath 上时，`RestClient` 默认使用 Reactor Netty HTTP client，其 read timeout 较短。Ollama 推理时间可能超过默认超时。

**解决**: 显式使用 JDK HttpClient 并配置足够长的 read timeout：
```java
var jdkClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();
var requestFactory = new JdkClientHttpRequestFactory(jdkClient);
requestFactory.setReadTimeout(Duration.ofMinutes(5));
RestClient.builder().baseUrl(ollamaBaseUrl).requestFactory(requestFactory).build();
```

## 4. 前端 withTimeout 过短

**现象**: 前端页面卡死后显示超时错误

**根因**: 前端 Compose `withTimeout(120_000L)` (2分钟) 对于本地 Ollama 推理可能不够（特别是 thinking 模式开启时，单次调用可能 30-50s，两次串行调用总计 60-100s+）。

**解决**: 将 timeout 从 120s 增加到 300s (5分钟)。关闭 thinking 后实际耗时 ~16s，留足余量。

## 5. 性能对比

| 场景 | Planner | Content | 总计 |
|------|---------|---------|------|
| thinking 开启 (默认) | ~26s | ~26s | ~52s |
| thinking 关闭 (`think: false`) | ~6s | ~10s | ~16s |

关闭 thinking 后速度提升 **3x**，对于 PSLE 数学题目不需要复杂推理，关闭 thinking 是合理的。
