# Copilot Instructions for this Repository
This file equips Copilot-style assistants with concise, actionable repo-specific guidance: build/test/lint commands (with single-test examples), a high-level architecture summary, and repository conventions worth enforcing.

---

## Build, test, and lint (how to run)
The repo exposes a top-level Makefile that wraps common tasks. Preferred entrypoints are the gradle wrappers inside each subproject.

Common shortcuts (from repo root):
- Start infra: `make infra-up` (or `cd infra && docker compose up -d`)
- Start dev (backend): `make dev`  # runs infra + backend in dev profile
- Start full dev (background): `make dev-full`  # starts backend and frontend in background
- Build all: `make build`
- Run all tests: `make test`
- Format: `make format`

Backend (Java / Spring Boot)
- Build: `make backend-build` or `cd backend && ./gradlew build`
- Run (dev): `make backend-run` or `cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'`
- Run tests (all): `make backend-test` or `cd backend && ./gradlew test`
- Run a single test class: `cd backend && ./gradlew test --tests "com.mathlearning.package.MyTestClass"`
- Run a single test method: `cd backend && ./gradlew test --tests "com.mathlearning.package.MyTestClass.myMethod"`
- Format / lint: `cd backend && ./gradlew spotlessApply` (check with `spotlessCheck`)

Frontend (Kotlin Multiplatform + Wasm)
- Build: `make frontend-build` or `cd frontend && ./gradlew build`
- Run dev server (hot-reload): `make frontend-run` or `cd frontend && ./gradlew :webApp:wasmJsBrowserDevelopmentRun -t`
- Run frontend shared tests (all): `make frontend-test` or `cd frontend && ./gradlew :shared:wasmJsBrowserTest`
- Run a single frontend test (example): `cd frontend && ./gradlew :shared:wasmJsBrowserTest --tests "com.example.YourTestKt"`
- Format: `cd frontend && ./gradlew spotlessApply`

E2E / Playwright
- Install: `make e2e-install` or `cd e2e && npm install && npx playwright install chromium`
- Run tests: `make e2e-test` or `cd e2e && npx playwright test`
- Run a single spec: `cd e2e && npx playwright test tests/my.spec.ts`

Docker / images
- Build backend image: `make docker-build`
- Bring up full stack (incl. backend container): `make docker-up`

Health checks / smoke
- Quick health check: `make check` (includes backend, db, Ollama checks)
- Quick API smoke: `make smoke-test`

Notes
- Use the gradle wrapper found in `backend/gradlew` and `frontend/gradlew` to ensure consistent Gradle versions.

---

## High-level architecture (big picture)
- Backend: Spring Boot application (Java 25) orchestrates an AI agent pipeline. Key components:
  - Planner / Orchestrator (e.g., MathSolverOrchestrator) that decomposes problems into agent tasks
  - AI integration layer using Spring AI `ChatClient` + `VectorStore` for RAG
  - Persistence: PostgreSQL (with pgvector) for embeddings and application data; Flyway for migrations
  - Cache: Redis for ephemeral cache of RAG results / prompts
  - Security & config: profile-driven `application-*.yml` (dev/prod)

- Frontend: Kotlin Multiplatform (shared module + webApp) compiled to Wasm for the web UI. The frontend renders Bar Model visuals and communicates with backend REST/SSE endpoints.

- Infra: Docker Compose config (infra/) provides PostgreSQL (+pgvector), Redis, and dev LLMs (Ollama). The Makefile has targets to start infra, pull Ollama models, and run dev workflows.

- RAG & embeddings: Repository uses nomic-embed-text (or similar) for embeddings; pgvector holds 768-dim vectors with an HNSW index for fast similarity search.

- CI: GitHub Actions workflows live in `.github/workflows/ci.yml` and run build/test steps with Gradle wrappers.

Files to inspect for deeper context: `docs/architecture.md`, `backend/src/main/java/com/mathlearning/agent/*`, `infra/docker-compose.yml`, `docs/quickstart.md`.

---

## Key conventions and repo-specific patterns
- Java Records: DTOs and many value objects use Java Records (check `backend/src/main/java/.../model`). Keep using records for new DTOs.
- Spring AI: All LLM calls should go through Spring AI abstractions (`ChatClient`, `VectorStore`) — avoid direct HTTP calls to model endpoints in business logic.
- Preview features: Java 25 preview features are used. Gradle/JDK runs must include `--enable-preview` where applicable (the gradle build config already handles this in the backend wrapper).
- RAG vector store: Use pgvector-backed VectorStore with the embedding model defined in infra/docs and `infra/init-db/` migrations.
- Gradle wrappers: Use `backend/gradlew` and `frontend/gradlew` — do not rely on a globally installed Gradle version.
- Makefile: Preferred top-level entrypoint for common dev flows (`make dev`, `make dev-full`, `make setup`, etc.). Use it to orchestrate infra + services.
- Tests: Backend uses JUnit (run via Gradle). Frontend shared tests run with the wasmJsBrowser target — test task names are module-qualified (see Makefile targets).
- E2E tests: Playwright lives in `e2e/`. Use `make e2e-install` then `make e2e-test`.
- Secrets/config: Use Spring profiles and environment variables (see `backend/src/main/resources/application-*.yml`).

---

## Other AI assistant/config files to consider
- CLAUDE.md — contains project context and quickstart (kept in root)
- `.github/workflows/ci.yml` — CI configuration

If adding or updating Copilot/assistant guidance, prefer short, deterministic instructions (commands, file paths, and single-test examples). Do not duplicate long-form docs — link to `docs/` for details.

---

Last changes
- Consolidated Makefile/README/CLAUDE guidance into actionable commands and single-test examples.
- Referenced key files for deeper exploration.

If further tailoring is desired (e.g., adding mapping of common Gradle tasks to test classes, or enumerating key Spring profiles), say which area to expand.
