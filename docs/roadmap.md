# Roadmap

Full task details are in [dev-plan.md](dev-plan.md). This page gives the high-level picture.

## Status Overview

| Phase | Focus | Status |
|:------|:------|:-------|
| [Phase 1](#phase-1--core-agent-chain) | Core Agent chain + Auth + SSE | Done |
| [Phase 2](#phase-2--rag--caching) | RAG knowledge base + Redis cache + Prompt tuning | Done |
| [Phase 3](#phase-3--web-frontend) | Web frontend (Compose Wasm) | Done |
| [Phase 4](#phase-4--code-quality) | Code quality & robustness | Done |
| [Phase 5](#phase-5--feature-completeness) | Feature completeness (auth enforcement, student profiles, history) | Mostly done (UX issues remain) |
| [Phase 6](#phase-6--ux--knowledge-graph) | UX fixes + Knowledge graph + Assessment + Star rating | Next |
| [Phase 7](#phase-7--local-performance) | Local performance optimisation | Upcoming |
| [Phase 8](#phase-8--advanced-features) | Advanced features (weakness analysis, adaptive path, OCR) | Optional |
| [Phase 9](#phase-9--production-deployment) | Production deployment (homelab k8s) | After local app is complete |

---

## Done

**Phase 1** — Backend API, Planner → Content Agent pipeline, Auth (JWT scaffolded), `/api/v1/solve` + `/api/v1/solve/stream` (SSE).

**Phase 2** — pgvector RAG (40 PSLE questions, grade-filtered, top-5 context), Redis `@Cacheable` (24h TTL), `OllamaConfig` interceptor (Spring AI 2.0.0-M2 `think` bug workaround).

**Phase 3** — Compose for Web (Wasm) SPA: solve form, four-section result display, SSE stream handling, responsive layout.

**Phase 4** — Unified `ObjectMapper` bean, global `@ControllerAdvice` exception handler (`ErrorResponse{code, message}`), LLM response Jackson parsing, Bean Validation (question ≤ 500 chars, grade 1–6), LLM timeout + friendly error, Testcontainers integration test skeleton (47 tests).

**Phase 5** — JWT enforcement via JJWT 0.12.6 (`JwtAuthenticationFilter` + `HttpStatusEntryPoint(401)`), student profile CRUD (`POST/GET /api/v1/students`), solve record persistence, knowledge progress tracking (upsert on solve), solve history API (`GET /api/v1/records/{studentId}`), knowledge progress API (`GET /api/v1/knowledge/{studentId}`), frontend login/register + student selector. UX issues (token persistence, student management) remain and are carried into Phase 6.

---

## Upcoming

### Phase 6 — UX Fixes + Knowledge Graph + Assessment

See [knowledge-graph-requirements.md](knowledge-graph-requirements.md), [session-persistence.md](session-persistence.md), [student-management-redesign.md](student-management-redesign.md).

- **Session persistence**: store JWT in `localStorage`; restore session on page refresh without re-login
- **Student management redesign**: `StudentManagementDialog` with grade picker, delete, inline errors; `DELETE /api/v1/students/{id}`
- **Knowledge graph**: `knowledge_nodes` table (P1–P6 tree, ≥ 60 nodes), mastery level (Unknown / Familiar / Mastered), manual parent marking
- **Assessment question bank**: ≥ 60 tagged questions, `GET /api/v1/questions?tag=&grade=` random draw
- **Star rating**: `solve_records.rating` (1–5), `PATCH /api/v1/records/{id}/rating`, mastery suggestion on rating
- **Frontend pages**: knowledge graph view, solve history page, main navigation (Solve / Knowledge / History)

### Phase 7 — Local Performance

- Semantic cache: skip LLM call if a prior answer has cosine similarity ≥ 0.95
- Common questions pre-warm on startup
- LLM retry (max 2, exponential backoff 1s/2s) for transient errors
- pgvector HNSW query plan verification (`EXPLAIN ANALYZE`)

### Phase 8 — Advanced Features (Optional)

Priorities to be decided based on actual usage.

- Weakness analysis: `GET /api/v1/knowledge/{studentId}/weaknesses`
- Personalised question recommendation (weakness + RAG + question bank)
- Adaptive learning path (prerequisite-aware knowledge ordering)
- OCR image input (Qwen-VL or Tesseract)

---

## Phase 9 — Production Deployment

Intentionally deferred until the local application is functionally complete and quality is high.

- CI: GitHub Actions builds and pushes Docker images to `ghcr.io`
- Helm chart (`deployment/charts/math-learning/`) for homelab k8s
- ArgoCD application syncing from the Helm chart
- Micrometer + Prometheus metrics, Grafana dashboard
- Production profile validation with DeepSeek-R1
