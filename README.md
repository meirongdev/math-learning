# SG Math Tutor - Singapore Primary Math Tutoring App

AI-powered math tutoring application targeting the **2026 PSLE syllabus**. Built for parents of P1-P6 students, it provides step-by-step problem solving using Singapore's CPA pedagogy and Model Method, with special focus on the 2026 new topics: **Algebra** and **Ratio & Average**.

## Features

- **Intelligent Agent Problem Solver** — Multi-agent chain (Planner → CPA Designer → Persona) decomposes problems into structured solutions with Bar Model visualizations
- **Dual-audience Output** — Generates a professional parent guide and a fun child-friendly script for each problem
- **SSE Streaming** — Real-time streamed responses with < 500ms first-token latency
- **RAG-enhanced** — Vector similarity search against a PSLE question bank for context-aware solutions
- **2026 PSLE Knowledge Tree** — Covers the updated syllabus including new Algebra topics and restructured Nets & Pie Charts

## Tech Stack

| Layer | Technology | Version |
|:------|:-----------|:--------|
| Backend | Java + Spring Boot + Spring AI | Java 25, Spring Boot 4.0.3, Spring AI 2.0.0-M2 |
| Build | Gradle | 9.2 |
| Frontend | Kotlin Multiplatform + Compose for Web (Wasm) | Kotlin 2.1+ |
| Database | PostgreSQL + pgvector | PostgreSQL 17, 768-dim HNSW index |
| Cache | Redis | 7.x |
| LLM (dev) | Ollama + qwen3.5 | local, zero API cost |
| LLM (prod) | DeepSeek-R1 / Gemini 2.0 Pro | via OpenAI-compatible API |

## Project Structure

```
math-learning/
├── backend/          # Spring Boot application (Java 25)
│   └── src/main/
│       ├── java/com/mathlearning/
│       │   ├── controller/    # REST endpoints
│       │   ├── service/       # Business logic
│       │   ├── agent/         # MathSolverOrchestrator, Agent chain
│       │   ├── model/         # JPA entities & DTOs (Java records)
│       │   └── repository/    # Spring Data JPA repositories
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── application-prod.yml
│           └── db/migration/  # Flyway migrations
├── frontend/         # KMP + Compose Wasm frontend
│   ├── shared/       # Shared Kotlin module
│   └── webApp/       # Web application (Wasm target)
├── infra/            # Infrastructure
│   ├── docker-compose.yml    # PostgreSQL + pgvector, Redis, Ollama
│   ├── init-db/              # DB initialization scripts
│   └── backend.Dockerfile    # Production Docker image
├── docs/             # Design documents
│   ├── requirements.md       # Product requirements (Chinese)
│   ├── system-design.md      # System architecture (Chinese)
│   └── dev-plan.md           # Development plan & task tracking
└── .github/
    └── workflows/ci.yml      # CI pipeline
```

## Prerequisites

- **Java 25** (Temurin)
- **Gradle 9.2** (or use the included wrapper)
- **Docker** (for PostgreSQL, Redis)
- **Ollama** (for local LLM inference)
- **Kotlin 2.1+** (for frontend)

## Getting Started

### 1. Start infrastructure services

```bash
cd infra && docker compose up -d
```

This starts PostgreSQL 17 (with pgvector) and Redis 7.

### 2. Pull local LLM models

```bash
ollama pull qwen3.5            # Chat model (~6.6GB)
ollama pull nomic-embed-text   # Embedding model (~274MB, 768-dim)
```

### 3. Run the backend

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The backend starts on `http://localhost:8080`. Flyway automatically runs database migrations on first startup.

### 4. Run the frontend (optional)

```bash
cd frontend
./gradlew :webApp:wasmJsBrowserDevelopmentRun
```

## API Endpoints

| Method | Path | Description | Auth |
|:-------|:-----|:------------|:-----|
| POST | `/api/v1/auth/register` | Parent registration | None |
| POST | `/api/v1/auth/login` | Login, returns JWT | None |
| POST | `/api/v1/students` | Create student profile | JWT |
| GET | `/api/v1/students` | List students under account | JWT |
| POST | `/api/v1/solve/stream` | Solve problem (SSE stream) | JWT |
| POST | `/api/v1/solve` | Solve problem (non-streaming) | JWT |
| GET | `/api/v1/records/{studentId}` | Problem-solving history | JWT |
| GET | `/api/v1/knowledge/{studentId}` | Knowledge mastery scores | JWT |

### Example: Solve a problem

```bash
curl -N -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{"question":"小明有 x 个苹果，妈妈又给了他 3 个。如果 x = 5，他现在有多少个苹果？","grade":6,"studentId":"uuid-xxx"}' \
     http://localhost:8080/api/v1/solve/stream
```

Response (SSE):
```
data: {"type":"parent_guide","content":"本题考查 P6 代数代换..."}
data: {"type":"child_script","content":"宝贝，x 是一个神奇的魔法盒子..."}
data: {"type":"bar_model","content":{"bars":[...]}}
data: [DONE]
```

## Development Notes

- **Java preview features** are enabled (`--enable-preview`) for Structured Concurrency and Scoped Values
- **Virtual Threads** are enabled by default for high-concurrency Agent request handling
- **Flyway** manages database schema migrations (`backend/src/main/resources/db/migration/`)
- **Spring AI `ChatClient`** is used for all LLM interactions
- **pgvector** with HNSW indexing (768 dimensions, matching `nomic-embed-text` output) provides RAG retrieval
- Production profile uses DeepSeek-R1 via OpenAI-compatible API (`application-prod.yml`)

## Roadmap

- [x] **Phase 1** — Core Agent chain + SSE streaming + Auth
- [ ] **Phase 2** — RAG knowledge base + Prompt tuning + Redis caching
- [ ] **Phase 3** — Web frontend (Compose Wasm) + SSE rendering
- [ ] **Phase 4** — E2E testing + Production deployment + User feedback

## License

Private — All rights reserved.
