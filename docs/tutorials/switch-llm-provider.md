# Tutorial: Switch LLM Provider

The app uses Spring AI's provider abstraction, so switching between Ollama (local) and DeepSeek-R1 (cloud) only requires a configuration change — no code changes.

---

## Default: Ollama (local, dev profile)

The `dev` profile is pre-configured for Ollama. This is the recommended setup for local development.

```bash
# Prerequisites: Ollama installed and models pulled
ollama pull qwen3.5:2b        # chat model (~3.8 GB)
ollama pull nomic-embed-text  # embedding model (~274 MB)

# Start Ollama server
ollama serve

# Start the backend with dev profile
make backend-run
```

### Change the Ollama chat model

Edit `backend/src/main/resources/application-dev.yml`:

```yaml
spring:
  ai:
    ollama:
      chat:
        model: qwen3.5   # change from qwen3.5:2b to full model
```

Then pull the model if not already downloaded:

```bash
ollama pull qwen3.5
```

Available tested models:

| Model | Size | Speed (local) | Notes |
|-------|------|---------------|-------|
| `qwen3.5:2b` | 3.8 GB | ~16s | Default, good balance |
| `qwen3.5` | 6.6 GB | ~25s | Better quality |

> Thinking mode is always disabled regardless of model. See [troubleshooting](../reference/troubleshooting.md) for why.

---

## Switch to DeepSeek-R1 (cloud)

DeepSeek-R1 is configured via `application-prod.yml` using the OpenAI-compatible API.

### 1. Get a DeepSeek API key

Sign up at [platform.deepseek.com](https://platform.deepseek.com) and create an API key.

### 2. Set environment variables

```bash
export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL=jdbc:postgresql://localhost:5432/mathlearning
export DATABASE_USERNAME=mathlearning
export DATABASE_PASSWORD=mathlearning
export REDIS_HOST=localhost
export DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxx
```

### 3. Start the backend

```bash
cd backend && ./gradlew bootRun
```

Or with explicit profile:

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=prod'
```

The embedding model still uses `nomic-embed-text` via Ollama (configured in base `application.yml`). Ollama must still be running for RAG retrieval.

### Adjust the DeepSeek model

Edit `backend/src/main/resources/application-prod.yml`:

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          model: deepseek-chat   # lighter/cheaper option
          # model: deepseek-reasoner  # reasoning model (default)
```

---

## Add a New Provider

Spring AI supports any OpenAI-compatible API endpoint. To add another provider (e.g. Gemini via Vertex AI):

1. Add the relevant Spring AI starter to `backend/build.gradle.kts`
2. Create a new profile yml (e.g. `application-gemini.yml`) with the provider config
3. No code changes needed — `ChatClient` and `OllamaChatOptions` are provider-specific; for non-Ollama providers, `OllamaChatOptions` is not used, so the `OllamaConfig` interceptor is not needed

> The `OllamaConfig.ollamaThinkFieldFixCustomizer()` interceptor only affects the `RestClient` configured for the Ollama model. It does not intercept OpenAI-compatible API calls.

---

## Performance Comparison

| Provider | Model | Latency | Cost | Notes |
|----------|-------|---------|------|-------|
| Ollama | qwen3.5:2b | ~16s | Free | Local, no internet required |
| Ollama | qwen3.5 | ~25s | Free | Better output quality |
| DeepSeek | deepseek-reasoner | ~5–10s | ~$0.01/solve | Production recommended |
| DeepSeek | deepseek-chat | ~3–5s | <$0.01/solve | Faster, less reasoning depth |
