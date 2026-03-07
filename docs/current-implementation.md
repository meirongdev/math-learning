# Current Implementation Overview

This document maps the shipped features to the concrete parts of the codebase.

## Backend

### Solve orchestration

- `backend/src/main/java/com/mathlearning/agent/MathSolverOrchestrator.java`
  - entry point for the solve pipeline
  - deterministic arithmetic fast path for trivial expressions
  - planner call + mode routing
  - local Socratic question generation from planner JSON

- `backend/src/main/java/com/mathlearning/service/SolveService.java`
  - wraps orchestration in exact-match + semantic caching
  - persists solve records when `studentId` is present
  - tracks knowledge tags into `knowledge_progress`

- `backend/src/main/java/com/mathlearning/service/RagRetrievalService.java`
  - pgvector lookup with `TOP_K = 3`
  - compact planner context formatting

### Student progress and Phase 10

- `backend/src/main/java/com/mathlearning/service/KnowledgeService.java`
  - increments attempts per knowledge tag
  - updates mastery from parent actions and ratings

- `backend/src/main/java/com/mathlearning/service/StudentPhase10Service.java`
  - computes achievement badges dynamically
  - derives current streaks from solve history
  - selects the weakest non-mastered node
  - checks the direct prerequisite only
  - returns up to three recommended challenge questions

- `backend/src/main/java/com/mathlearning/controller/StudentController.java`
  - standard student CRUD
  - `GET /api/v1/students/{id}/achievements`
  - `GET /api/v1/students/{id}/learning-path`

- `backend/src/main/java/com/mathlearning/controller/RecordController.java`
  - paged solve history
  - Phase 9 mistakes endpoint: `GET /api/v1/records/mistakes`
  - Phase 9 export payload endpoint: `GET /api/v1/records/{recordId}/export`

### Persistence

- `solve_records`
  - full tutoring output per solve
  - parent rating field
- `knowledge_progress`
  - attempts, correctness, mastery score, mastery level
- `knowledge_nodes`
  - hierarchical PSLE skill tree
- `assessment_questions` + tag join table
  - recommendation pool and targeted challenge bank

## Frontend

### Wasm app shell

- `frontend/webApp/src/wasmJsMain/kotlin/com/mathlearning/web/App.kt`
  - auth shell and top-level tab routing
  - split pages: Solve, Knowledge, Growth, Mistakes, History
  - adaptive challenge handoff from Growth -> Solve

### Solve screen

- direct question entry
- OCR upload with retry-safe cancel flow
- explanation mode chips
- result cards for knowledge tags, parent guide, child script, and bar model
- persisted star ratings after solve

### Knowledge screen

- star-map visualization grouped by grade
- original collapsible tree view for manual mastery editing

### Growth screen

- knowledge snapshot counters
- badge wall
- adaptive challenge card with `Start This Challenge`

### Mistakes screen (Phase 9 skeleton)

- low-rated record list (`rating <= 2`)
- optional tag filtering
- export preview retrieval for printable/PDF-ready content

### OCR

- `frontend/webApp/src/wasmJsMain/resources/ocr-helper.js`
  - hidden file input lifecycle
  - cancel detection on focus regain
  - `eng+chi_sim` recognition via `tesseract.js`

- `frontend/webApp/src/wasmJsMain/kotlin/com/mathlearning/web/OcrInterop.kt`
  - Wasm interop wrapper
  - manual JSON parsing to avoid runtime serializer issues

- `frontend/webApp/src/wasmJsMain/resources/index.html`
  - `Noto Sans SC` + CJK fallback stack
  - Tesseract CDN loading

## Shared module

- `frontend/shared/src/commonMain/kotlin/com/mathlearning/shared/model/Models.kt`
  - API DTOs used across frontend targets
  - includes Phase 8 and Phase 10 response models

- `frontend/shared/src/commonMain/kotlin/com/mathlearning/shared/api/MathApi.kt`
  - all HTTP calls used by the Wasm app
  - includes achievements/learning-path plus Phase 9 mistakes/export methods

## Test coverage

### Backend

- `backend/src/test/java/com/mathlearning/controller/StudentControllerTest.java`
  - verifies achievements and adaptive learning-path endpoints
- `backend/src/test/java/com/mathlearning/controller/RecordControllerTest.java`
  - verifies mistakes endpoint ownership scope and export endpoint behavior

### Frontend/shared

- `frontend/shared/src/commonTest/kotlin/com/mathlearning/shared/api/MathApiTest.kt`
  - covers new Phase 10 client methods

### Browser smoke/E2E

- `e2e/tests/app-loading.spec.js`
  - Wasm load health
  - OCR helper availability
  - OCR cancel and recognition regression coverage
  - host-page font/script checks

- `e2e/tests/api-health.spec.js`
  - auth and backend reachability smoke checks

## Known boundaries

- OCR is text extraction only; image understanding is not sent directly to a vision model.
- The knowledge star map is a high-signal visualization layer on top of the existing graph, not a new graph engine.
- Achievements are computed dynamically, so badge taxonomy can evolve without a database migration.
