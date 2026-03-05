# Roadmap

Full task details are in [dev-plan.md](dev-plan.md). This page gives the high-level picture.

## Status Overview

| Phase | Focus | Status |
|:------|:------|:-------|
| [Phase 1](#phase-1--core-agent-chain) | Core Agent chain + Auth + SSE | Done |
| [Phase 2](#phase-2--rag--caching) | RAG knowledge base + Redis cache + Prompt tuning | Done |
| [Phase 3](#phase-3--web-frontend) | Web frontend (Compose Wasm) | Done |
| [Phase 4](#phase-4--code-quality) | Code quality & robustness | Next |
| [Phase 5](#phase-5--feature-completeness) | Feature completeness (auth enforcement, student profiles, history) | Planned |
| [Phase 6](#phase-6--local-performance) | Local performance optimisation | Planned |
| [Phase 7](#phase-7--advanced-features) | Advanced features (weakness analysis, OCR) | Optional |
| [Phase 8](#phase-8--production-deployment) | Production deployment (homelab k8s) | After local app is complete |

---

## Done

**Phase 1** — Backend API, Planner → Content Agent pipeline, Auth (JWT scaffolded), `/api/v1/solve` + `/api/v1/solve/stream` (SSE).

**Phase 2** — pgvector RAG (40 PSLE questions, grade-filtered, top-5 context), Redis `@Cacheable` (24h TTL), `OllamaConfig` interceptor (Spring AI 2.0.0-M2 `think` bug workaround).

**Phase 3** — Compose for Web (Wasm) SPA: solve form, four-section result display, SSE stream handling, responsive layout.

---

## Upcoming

### Phase 4 — Code Quality

Priority: address tech debt before adding more features.

- Unified `ObjectMapper` bean (replace `new ObjectMapper()` instances)
- Global `@ControllerAdvice` exception handler (`ErrorResponse{code, message}`)
- LLM response parsing migrated from hand-written string extraction to Jackson
- Input validation: question length ≤ 500 chars, grade 1–6 (Bean Validation)
- LLM call timeout + friendly error on failure
- Integration test skeleton (Testcontainers + PostgreSQL)

### Phase 5 — Feature Completeness

Goal: all DB tables that exist are actually used; app forms a real product loop.

- JWT enforcement on protected endpoints (currently scaffolded but not applied)
- Student profile CRUD: `POST/GET /api/v1/students`
- Solve record persistence: write to `solve_records` on every solve
- Knowledge progress tracking: upsert `knowledge_progress` from `knowledgeTags`
- Solve history API: `GET /api/v1/records/{studentId}`
- Knowledge progress API: `GET /api/v1/knowledge/{studentId}`
- Frontend: login/register page + student selector on solve form

### Phase 6 — Local Performance

- Semantic cache: skip LLM call if a prior answer has cosine similarity ≥ 0.95
- Common questions pre-warm on startup
- LLM retry (max 2, exponential backoff 1s/2s) for transient errors
- pgvector HNSW query plan verification (`EXPLAIN ANALYZE`)

### Phase 7 — Advanced Features (Optional)

Priorities to be decided based on actual usage.

- Weakness analysis: `GET /api/v1/knowledge/{studentId}/weaknesses`
- Personalised question recommendation (weakness + RAG)
- Knowledge dependency graph (prerequisite topics)
- OCR image input (Qwen-VL or Tesseract)

---

## Phase 8 — Production Deployment

Intentionally deferred until the local application is functionally complete and quality is high.

- CI: GitHub Actions builds and pushes Docker images to `ghcr.io`
- Helm chart (`deployment/charts/math-learning/`) for homelab k8s
- ArgoCD application syncing from the Helm chart
- Micrometer + Prometheus metrics, Grafana dashboard
- Production profile validation with DeepSeek-R1
