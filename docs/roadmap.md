# Roadmap

Full task details are in [dev-plan.md](dev-plan.md). This page gives the high-level picture.

## Status Overview

| Phase | Focus | Status |
|:------|:------|:-------|
| [Phase 1](#phase-1--core-agent-chain) | Core Agent chain + Auth + SSE | Done |
| [Phase 2](#phase-2--rag--caching) | RAG knowledge base + Redis cache + Prompt tuning | Done |
| [Phase 3](#phase-3--web-frontend) | Web frontend (Compose Wasm) | Done |
| [Phase 4](#phase-4--code-quality) | Code quality & robustness | Done |
| [Phase 5](#phase-5--feature-completeness) | Feature completeness (auth enforcement, student profiles, history) | Done |
| [Phase 6](#phase-6--ux--knowledge-graph) | UX fixes + Knowledge graph + Assessment + Star rating | Done |
| [Phase 7](#phase-7--local-performance) | Local performance optimisation | Done |
| [Phase 8](#phase-8--socratic-mode) | Socratic explanation mode + core capability expansion | Done |
| [Phase 9](#phase-9--interactive-enhancements) | Interactive enhancements & parent productivity tools | Planned |
| [Phase 10](#phase-10--gamification) | Gamification & adaptive learning | Planned |
| [Phase 11](#phase-11--production-deployment) | Production deployment (homelab k8s) | After local app is complete |

---

## Done

**Phase 1** — Backend API, Planner → Content Agent pipeline, Auth (JWT scaffolded), `/api/v1/solve` + `/api/v1/solve/stream` (SSE).

**Phase 2** — pgvector RAG (40 PSLE questions, grade-filtered, top-5 context), Redis `@Cacheable` (24h TTL), `OllamaConfig` interceptor (Spring AI 2.0.0-M2 `think` bug workaround).

**Phase 3** — Compose for Web (Wasm) SPA: solve form, four-section result display, SSE stream handling, responsive layout.

**Phase 4** — Unified `ObjectMapper` bean, global `@ControllerAdvice` exception handler (`ErrorResponse{code, message}`), LLM response Jackson parsing, Bean Validation (question ≤ 500 chars, grade 1–6), LLM timeout + friendly error, Testcontainers integration test skeleton (47 tests).

**Phase 5** — JWT enforcement via JJWT 0.12.6 (`JwtAuthenticationFilter` + `HttpStatusEntryPoint(401)`), student profile CRUD (`POST/GET /api/v1/students`), solve record persistence, knowledge progress tracking (upsert on solve), solve history API (`GET /api/v1/records/{studentId}`), knowledge progress API (`GET /api/v1/knowledge/{studentId}`), frontend login/register + student selector.

**Phase 6** — Session persistence (JWT in `localStorage`, auto-restore on refresh, 401 interceptor). Student management redesign (`StudentManagementDialog` with grade picker + delete, `DELETE /api/v1/students/{id}`). Knowledge graph (`knowledge_nodes` table with 63 nodes P1–P6, mastery levels UNKNOWN/FAMILIAR/MASTERED, `GET /api/v1/knowledge/graph` public tree endpoint, `GET /api/v1/knowledge/{studentId}/progress`, `PUT /api/v1/knowledge/{studentId}/progress/{nodeCode}`). Assessment question bank (68 tagged questions, `GET /api/v1/questions?tag=&grade=&limit=`). Star rating (`solve_records.rating` 1–5, `PATCH /api/v1/records/{recordId}/rating` with auto mastery suggestion). Frontend: three-tab navigation (Solve / Knowledge / History), knowledge graph tree view with mastery badges, solve history page with expandable records and inline rating.

**Phase 7** — Multi-layer caching (L1 Redis exact match → L2 semantic cache via pgvector cosine similarity ≥ 0.98 + Caffeine in-memory → L3 full LLM pipeline). Startup cache warmup (top 20 recent solve records pre-loaded). Resilience4j retry (3 attempts, exponential backoff) + circuit breaker (50% threshold, 60s recovery). LLM parse failure fallback (returns plain-text result instead of error). Ollama keep-alive 30m.

---

**Phase 8** — `ExplanationMode` enum (ORIGINAL / SOCRATIC) added to solve request. Socratic Agent system prompt generates 2–4 heuristic guiding questions instead of direct answers. Mode routing in `MathSolverOrchestrator` selects Content Agent or Socratic Agent based on mode. Frontend Filter Chips for mode selection on solve screen. Cache key includes mode for separate caching per explanation style.

---

## Upcoming

### Phase 9 — Interactive Enhancements & Parent Productivity

- Interactive Bar Models (drag/drop/resize)
- Error notebook (`GET /api/v1/records/mistakes`, rating ≤ 2)
- PDF export of parent guide + child script
- Weekly learning report with AI summary

### Phase 10 — Gamification & Adaptive Learning

- Achievement badges and skill tree visualisation
- Prerequisite-aware adaptive learning path
- Automatic question recommendation based on weaknesses

### Phase 11 — Production Deployment

Intentionally deferred until the local application is functionally complete and quality is high.

- CI: GitHub Actions builds and pushes Docker images to `ghcr.io`
- Helm chart (`deployment/charts/math-learning/`) for homelab k8s
- ArgoCD application syncing from the Helm chart
- Micrometer + Prometheus metrics, Grafana dashboard
- Production profile validation with DeepSeek-R1
