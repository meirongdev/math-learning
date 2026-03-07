# Quickstart

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 25 (Temurin) | `brew install --cask temurin@25` |
| Docker | any recent | [docker.com](https://www.docker.com/products/docker-desktop/) |
| Ollama | latest | `brew install ollama` |
| Kotlin | bundled | managed by the frontend Gradle wrapper |

Gradle 9.2 and Kotlin are managed by the project's Gradle wrappers — no manual installation needed.

---

## First-time Setup

```bash
# 1. Start PostgreSQL + Redis (pgvector included)
make infra-up

# 2. Pull the two Ollama models used by the app
make ollama-pull
#   qwen3.5        — chat/reasoning model
#   nomic-embed-text — embedding model     (~274 MB, 768-dim)

# 3. Start Ollama server (keep this running in a separate terminal)
ollama serve
```

Or run everything in one step:

```bash
make setup   # infra-up + ollama-pull
```

---

## Run the App

### Option A — foreground (recommended for first run)

```bash
# Terminal 1: backend
make backend-run

# Terminal 2: frontend
make frontend-run
```

### Option B — background

```bash
make dev-full     # starts infra + backend + frontend, logs → .logs/
make logs         # follow logs
make stop         # stop background services
```

| Service | URL |
|---------|-----|
| Backend API | http://localhost:8080 |
| Frontend | http://localhost:8081 |
| Backend health | http://localhost:8080/actuator/health |

---

## Verify Everything Works

```bash
make check        # health-checks backend, PostgreSQL, Ollama
```

> `make smoke-test` 当前脚本仍使用旧请求体字段，暂不作为权威验证方式。
> 建议使用下方 `curl` 手工验证登录与解题链路。

### Manual API test

```bash
# Register a user
curl -X POST http://localhost:8080/api/v1/auth/register \
     -H "Content-Type: application/json" \
     -d '{"email":"parent@example.com","password":"Test1234!"}'

# Login and capture the JWT token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"parent@example.com","password":"Test1234!"}' | jq -r .token)

# Solve a problem (requires JWT)
curl -X POST http://localhost:8080/api/v1/solve \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $TOKEN" \
     -d '{"question":"Amy has 24 sweets. She gives 1/3 to Bob. How many does Amy have left?","grade":3}'
```

Expected response (cold requests may take a while on local Ollama, cached/fast-path requests return much faster):

```json
{
  "parentGuide": "This question covers P3 fractions...",
  "childScript": "Let's share some sweets...",
  "barModelJson": "{\"title\":\"Sweets\",\"bars\":[...]}",
     "knowledgeTags": ["frac.add_sub", "whole_numbers"]
}
```

## Phase 10 Features To Try

1. Log in and create a student profile.
2. Solve a few questions and rate the explanations.
3. Open the `Knowledge` tab to see star map / tree view and update mastery.
4. Open the `Growth` tab to see badge wall, snapshot, and adaptive challenge recommendations.
5. Tap `Start This Challenge` to send a recommended problem straight into the solve form.
6. Open the `Mistakes` tab to inspect low-rated attempts and preview export payloads.

---

## Common Issues

See [reference/troubleshooting.md](reference/troubleshooting.md).

---

## Next Steps

- [Architecture](architecture.md) — how the agent pipeline and data model work
- [Docs Guide](README.md) — where each maintained document now lives
- [API Reference](reference/api.md) — all endpoints with request/response schemas
- [Configuration](reference/configuration.md) — how to switch models or change env settings
- [Tutorials](tutorials/switch-llm-provider.md) — switch from Ollama to DeepSeek-R1
