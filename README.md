# SG Math Tutor

AI-powered math tutoring app targeting the **2026 PSLE syllabus**. Built for parents of P1–P6 students — provides step-by-step problem solving using Singapore's CPA pedagogy and Model Method, with special focus on the 2026 new topics: **Algebra** and **Ratio & Average**.

## Tech Stack

| Layer | Technology | Version |
|:------|:-----------|:--------|
| Backend | Java + Spring Boot + Spring AI | Java 25, Spring Boot 4.0.3, Spring AI 2.0.0-M2 |
| Build | Gradle | 9.2 |
| Frontend | Kotlin Multiplatform + Compose for Web (Wasm) | Kotlin 2.1+ |
| Database | PostgreSQL + pgvector | PostgreSQL 17, 768-dim HNSW index |
| Cache | Redis | 7.x |
| LLM (dev) | Ollama + qwen3.5:2b | local, zero API cost |
| LLM (prod) | DeepSeek-R1 | via OpenAI-compatible API |

## Quick Start

```bash
make setup      # start PostgreSQL + Redis, pull Ollama models
ollama serve    # keep running in a separate terminal
make dev        # start backend (then make frontend-run in another terminal)
```

Backend: `http://localhost:8080` — Frontend: `http://localhost:8081`

See [docs/quickstart.md](docs/quickstart.md) for full setup instructions.

## Documentation

| Section | Description |
|---------|-------------|
| [Quickstart](docs/quickstart.md) | Prerequisites, setup, first API call |
| [Architecture](docs/architecture.md) | Agent pipeline, data model, design decisions |
| [Roadmap](docs/roadmap.md) | Phase status and upcoming work |
| [API Reference](docs/reference/api.md) | All REST endpoints with examples |
| [Configuration](docs/reference/configuration.md) | application.yml, profiles, env vars |
| [Make Targets](docs/reference/make-targets.md) | All `make` commands |
| [Troubleshooting](docs/reference/troubleshooting.md) | Known issues and workarounds |
| [Tutorial: Add Questions](docs/tutorials/add-psle-questions.md) | Add PSLE questions to the knowledge base |
| [Tutorial: Switch LLM](docs/tutorials/switch-llm-provider.md) | Switch between Ollama and DeepSeek-R1 |

## Project Structure

```
math-learning/
├── backend/          # Spring Boot application (Java 25)
│   └── src/main/
│       ├── java/com/mathlearning/
│       │   ├── agent/         # MathSolverOrchestrator
│       │   ├── config/        # OllamaConfig, CacheConfig, SecurityConfig
│       │   ├── controller/    # REST endpoints
│       │   ├── model/         # JPA entities & DTOs (Java records)
│       │   ├── repository/    # Spring Data JPA repositories
│       │   └── service/       # Business logic, RAG retrieval
│       └── resources/
│           ├── application.yml / application-dev.yml / application-prod.yml
│           ├── data/          # sg-math-questions.json (PSLE question bank)
│           └── db/migration/  # Flyway migrations
├── frontend/         # KMP + Compose Wasm frontend
│   ├── shared/       # Shared Kotlin module (API client, models)
│   └── webApp/       # Web application (Wasm target)
├── infra/            # Docker Compose, Dockerfiles, DB init scripts
├── deployment/
│   └── charts/       # Helm chart for homelab k8s deployment
├── docs/             # Documentation
│   ├── quickstart.md
│   ├── architecture.md
│   ├── roadmap.md
│   ├── dev-plan.md           # Detailed task tracking
│   ├── requirements.md       # Product requirements
│   ├── reference/            # API, config, make targets, troubleshooting
│   └── tutorials/            # Step-by-step guides
└── .github/
    └── workflows/ci.yml
```

## Roadmap

- [x] **Phase 1** — Core Agent chain (Planner + Content Agent) + Auth + SSE
- [x] **Phase 2** — RAG knowledge base (pgvector) + Redis cache + Prompt tuning
- [x] **Phase 3** — Web frontend (Compose Wasm)
- [ ] **Phase 4** — Code quality: unified error handling, input validation, test baseline
- [ ] **Phase 5** — Feature completeness: JWT enforcement, student profiles, solve history
- [ ] **Phase 6** — Local performance: semantic cache, LLM retry
- [ ] **Phase 7** — Advanced features: weakness analysis, recommendations, OCR (optional)
- [ ] **Phase 8** — Production deployment: CI images, homelab k8s, observability

See [docs/roadmap.md](docs/roadmap.md) for details.

## License

Private — All rights reserved.
