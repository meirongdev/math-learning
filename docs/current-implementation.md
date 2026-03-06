# Current Feature Implementation Overview

This document describes how the core features of the Math Learning application are implemented as of March 2026.

## 1. Solve Pipeline

### Backend
- **MathSolverOrchestrator** orchestrates the entire solve flow.  It accepts a `SolveRequest` (question, grade, optional studentId, optional `ExplanationMode`).
- A deterministic arithmetic fast path intercepts simple expressions (`3+4`, `What is 3 ÷ 1?`, etc.) using a regex pattern. When matched, the result is computed locally and returned immediately without LLM or RAG calls.
- RAG retrieval (`RagRetrievalService`) queries pgvector with `TOP_K = 3` and builds a compact bullet-list context string. Empty string when no hits.
- The planner agent is invoked via `ChatClient` (default model `qwen3.5:2b`). It returns JSON including `knowledgeTags`, `steps`, and `answer`.
- Based on `ExplanationMode`:
  * **ORIGINAL**: pass planner result to a Content Agent prompt to generate parentGuide, childScript, bar model.
  * **SOCRATIC**: do not call a second LLM; instead, turn planner JSON into a sequence of heuristic guiding questions. A fallback is available if parsing fails.
- Results are returned as `SolveResult` with parentGuide, childScript, barModelJson, knowledgeTags.
- Responses are cached (Caffeine/Redis) with key `question:grade:mode`.

### Models
- `SolveRequest` and `SolveResult` records; `ExplanationMode` enum added.
- `SolveService` handles caching and delegates to `MathSolverOrchestrator`.

## 2. Frontend

### Technology
- Kotlin Multiplatform (2.2.20) targeting Wasm via Compose for Web (1.10.2).
- Host page (`index.html`) defines a CJK font stack with `Noto Sans SC` loaded via Google Fonts and preconnect links for performance.

### OCR
- Client-side OCR done with `tesseract.js` in `frontend/webApp/src/wasmJsMain/resources/ocr-helper.js`.
- A module `window.mathLearningOcr.pickAndRecognize()` presents a hidden file input, handles cancel via focus event, and invokes Tesseract with explicit `workerPath`, `corePath`, `langPath`.
- Interop wrapper `runBrowserOcr` in `OcrInterop.kt` uses manual JSON parsing (avoids serialization plugin issues) and returns `OcrPayload` containing `fileName`, `text`, `cancelled`.
- Solve screen `App.kt` has upload button, status messages, and integrates OCR results into the question field. Cancelled state resets appropriately.

### UI
- Solve screen includes:
  * Question input (multi-line `OutlinedTextField` with CJK placeholder)
  * OCR upload button with loading/status messages
  * Explanation mode filter chips (Direct vs Socratic)
  * Grade/Student selectors
  * Star rating component (`RatingSelector`, `RatingBadge`) bound to last solve record; persists via API.
- History screen displays compact rating badges and allows inline rating via selector.

### E2E Testing
- Playwright tests under `e2e/tests` verifying:
  * App loads without Wasm/runtime errors
  * Canvas renders and no uncaught exceptions
  * OCR helper availability
  * Host page fonts and scripts configuration
  * OCR cancellation and text extraction behaviours
- Additional API health tests check backend endpoints.

## 3. OCR Flow to LLM
- OCR output is simply assigned to the question field. When `api.solve` is called, the backend receives the text and passes it unchanged to the planner LLM (`qwen3.5:2b`), so OCR content is processed by the same model as typed text.
- If a vision-enabled model were required, the API would need to evolve to accept images or use a separate vision call.

## 4. Notes
- Chinese input issues were mitigated by font stack and preloading; further improvements could replace the Compose text field with a native DOM textarea for IME reliability.

---
*Document generated automatically to capture current architecture and behaviors.*
