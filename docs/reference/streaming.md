# Streaming Strategy: POST/JSON vs SSE vs WebSocket

This document records the analysis and tradeoffs for the solve endpoint's response strategy. The backend currently exposes both a JSON endpoint (`POST /api/v1/solve`) and an SSE endpoint (`POST /api/v1/solve/stream`). The frontend uses the JSON endpoint.

---

## Current State

### What the backend does

`POST /api/v1/solve` → waits for full pipeline → returns `SolveResult` JSON (~16s cold, <100ms cached).

`POST /api/v1/solve/stream` → waits for full pipeline → emits all events via `Flux.fromIterable` → terminates. This is **fake streaming**: the SSE events all arrive at once because `solveService.solve()` is synchronous and blocks until both LLM agents complete.

### What the frontend does

`MathApi.solve()` sends a single POST and waits for the full JSON body. The Compose UI shows a loading spinner with elapsed-time messages until the complete result arrives, then renders all four sections simultaneously.

---

## Option A — Current: POST + JSON (status quo)

### How it works

```
Client                    Server
  │── POST /api/v1/solve ──▶│
  │                         │  RAG retrieval (~1s)
  │                         │  Planner Agent (~8s)
  │                         │  Content Agent (~8s)
  │◀──── SolveResult ───────│  (full JSON, ~17s total)
```

### Pros

- **Simple**: one request, one response, standard HTTP semantics.
- **Cache-friendly**: `@Cacheable` on `SolveService.solve()` works perfectly. Cached results return instantly with zero protocol overhead.
- **Wasm/Ktor compatible**: `client.post(...).bodyAsText()` is the most reliable Ktor Wasm pattern. No SSE parsing, no connection lifecycle to manage.
- **Stateless**: no open connection held for 16s on the server side (virtual threads handle blocking cheaply, but the HTTP connection is still held).
- **Proxy/CDN safe**: ordinary POST; no special headers or connection upgrades required.

### Cons

- **No progressive disclosure**: all four result sections appear at once. The user sees nothing for ~16s, then everything.
- **Perceived latency**: a spinner for 16s feels slow even if the actual compute time is unavoidable. Progressive results would make the app feel faster.
- **Wasted opportunity**: Planner Agent finishes after ~8s and its output (`knowledgeTags`, solution plan) could be shown to the user while the Content Agent is still running.

---

## Option B — True SSE (requires backend + frontend changes)

SSE is a one-way, server-to-client push protocol over a persistent HTTP connection. The server sends `data: ...\n\n` frames and the client reads them incrementally.

### How true SSE would work

```
Client                         Server
  │── POST /api/v1/solve/stream ──▶│
  │                                │  RAG retrieval (~1s)
  │                                │  Planner Agent (~8s)
  │◀── data: {knowledge_tags} ─────│  ← emitted immediately after Planner
  │◀── data: {steps}  ─────────────│
  │                                │  Content Agent (~8s)
  │◀── data: {parent_guide} ───────│  ← emitted immediately after Content
  │◀── data: {child_script} ───────│
  │◀── data: {bar_model} ──────────│
  │◀── data: [DONE] ───────────────│
```

The key difference from the current `/stream` endpoint: events are emitted **as each agent completes**, not after the entire pipeline finishes.

### Pros

- **Progressive disclosure**: `knowledgeTags` appears after ~9s while the user waits for the remaining ~8s. The app feels more responsive.
- **Natural fit for long LLM calls**: SSE was designed for exactly this use case — server work that takes time and produces results incrementally.
- **HTTP/1.1 compatible**: works through most reverse proxies (nginx, Caddy) with a `proxy_buffering off` setting. No upgrade handshake.
- **Auto-reconnect**: browser `EventSource` API handles reconnection automatically (not applicable here since we use POST — see constraint below).

### Cons and constraints

**1. Browser `EventSource` does not support POST.**

The browser's native `EventSource` API only supports GET requests. The current endpoint is `POST /api/v1/solve/stream` with a JSON body. Options:

- Switch to `GET /api/v1/solve/stream?question=...&grade=3` — awkward, question text is up to 500 chars which risks URL length limits, and GET with side-effects is semantically wrong.
- Use Ktor's `HttpStatement` with `execute { response -> response.bodyAsChannel() }` to manually read the SSE stream from a POST response. This works on Wasm/JS but requires hand-written SSE frame parsing.
- Use the browser Fetch API via `interopWith` from Kotlin/Wasm for a streaming POST body. More complex interop.

The **recommended approach** if SSE is adopted: Ktor streaming body parsing.

**2. Cache interaction becomes awkward.**

`@Cacheable` on `SolveService.solve()` caches the complete `SolveResult`. With true streaming, the cache would need to be checked before starting the stream. Options:

- Check cache first; if hit, emit all events immediately (fast stream from cache). If miss, emit progressively as agents complete. This means the endpoint behaves differently for cache hits vs misses — acceptable but worth documenting.
- Alternatively, cache at the controller level instead of service level.

**3. Backend pipeline refactoring required.**

`MathSolverOrchestrator.solve()` is currently a synchronous method that calls both agents and returns `SolveResult`. For true streaming it needs to return an event source — either a `Flux<SolveEvent>` or a callback-based design where each completed agent emits immediately.

**4. Proxy buffering.**

nginx buffers SSE by default. Production deployment (Phase 8) would need `proxy_buffering off; proxy_cache off;` on the `/stream` path.

### Required changes for true SSE

#### Backend

1. **`MathSolverOrchestrator`**: refactor `solve()` to return `Flux<SolveEvent>` (or use a `FluxSink`) that emits after each agent:
   ```java
   // Emit after Planner
   sink.next(new SolveEvent("knowledge_tags", plannerResult.knowledgeTags()));
   sink.next(new SolveEvent("steps", plannerResult.steps()));
   // Emit after Content
   sink.next(new SolveEvent("parent_guide", contentResult.parentGuide()));
   // ...
   ```
2. **`SolveController.solveStream()`**: consume the `Flux<SolveEvent>` and map each event to an SSE frame string. Handle cache hit case (emit all events immediately).
3. **`SolveService`**: either remove `@Cacheable` from `solve()` and handle caching at the streaming layer, or add a separate cache-check path.

#### Frontend

4. **`MathApi`**: add `solveStream(request, onEvent)` using Ktor `HttpStatement`:
   ```kotlin
   client.preparePost("$baseUrl/api/v1/solve/stream") {
       contentType(ContentType.Application.Json)
       setBody(json.encodeToString(request))
   }.execute { response ->
       val channel = response.bodyAsChannel()
       while (!channel.isClosedForRead) {
           val line = channel.readUTF8Line() ?: break
           if (line.startsWith("data: ")) {
               val data = line.removePrefix("data: ")
               if (data != "[DONE]") onEvent(json.decodeFromString<SolveEvent>(data))
           }
       }
   }
   ```
5. **`App.kt`**: update state incrementally as each event arrives, so each result section appears as soon as its data is ready.

---

## Option C — WebSocket

WebSocket upgrades an HTTP connection to a full-duplex TCP channel. Both client and server can send messages at any time.

### How it would work

```
Client                         Server
  │── WS upgrade ─────────────────▶│
  │── {question, grade} ───────────▶│
  │◀── {type: knowledge_tags} ──────│
  │◀── {type: parent_guide} ────────│
  │◀── {type: [DONE]} ──────────────│
  │── close ───────────────────────▶│
```

### Pros

- **Full duplex**: client could send follow-up questions or cancel a request mid-stream.
- **Lower frame overhead** than SSE for high-frequency messaging.
- **Ktor WebSocket client** is well-supported on Wasm/JS.

### Cons

- **Overkill for this use case**: the solve interaction is strictly one-way after the request is sent (client sends question, server sends results). Full duplex adds complexity with no benefit here.
- **More complex lifecycle**: connection management, reconnection on failure, heartbeat/ping-pong.
- **Cache interaction**: same problem as SSE — harder to integrate with `@Cacheable`.
- **Proxy configuration**: WebSocket requires `Upgrade` header support in nginx/Caddy; SSE does not.
- **Spring Boot**: requires `spring-boot-starter-websocket` (adds stomp/sockjs dependencies); SSE uses the existing MVC stack.

WebSocket would make sense if the app ever needs bidirectional real-time communication (e.g., interactive tutoring sessions where the student can ask follow-up questions mid-stream, or collaborative parent-child sessions). For the current solve request/response pattern, it is unnecessary complexity.

---

## Comparison Summary

| Dimension | POST + JSON (current) | True SSE | WebSocket |
|:----------|:----------------------|:---------|:----------|
| Protocol | HTTP POST | HTTP POST + streaming body | TCP upgrade |
| Progressive results | No — all at once | Yes — per agent | Yes — per agent |
| Cache integration | Trivial (`@Cacheable`) | Requires extra logic | Requires extra logic |
| Frontend complexity | Low | Medium (manual SSE parse) | High (connection lifecycle) |
| Backend complexity | Low | Medium (Flux pipeline) | High (handler, lifecycle) |
| First-result latency | ~16s | ~9s (after Planner) | ~9s (after Planner) |
| Cached-result latency | <100ms | <100ms (instant stream) | <100ms (instant stream) |
| Proxy/CDN compatibility | Excellent | Good (needs `proxy_buffering off`) | Moderate (needs `Upgrade`) |
| `EventSource` API usable | N/A | No (POST not supported) | N/A |
| Added dependencies | None | None | `spring-boot-starter-websocket` |
| Appropriate for this app | Yes | Yes, if latency UX matters | Overkill for now |

---

## Decision

**Current decision: keep POST + JSON.**

Rationale:
- The main bottleneck is LLM compute time, not the protocol. Switching to SSE saves ~8s of perceived wait (Planner completes) but adds meaningful backend and frontend complexity.
- The Redis cache already solves the latency problem for repeated questions (<100ms).
- For a local-first dev tool, the loading spinner with stage messages (`"Analyzing..."`, `"Generating..."`) provides adequate feedback without true streaming.

**When to reconsider SSE:**
- If the app is opened to real users and the 16s first-call latency generates complaints.
- If the agent pipeline is expanded (more agents = longer total time = bigger UX benefit from progressive disclosure).
- If the Planner output (knowledge tags, difficulty, steps) becomes useful to show before the Content Agent finishes.

**When to consider WebSocket:**
- If interactive tutoring (follow-up questions, mid-stream interaction) is implemented in Phase 7.

The `/api/v1/solve/stream` endpoint is kept in place (as a pseudo-SSE endpoint) so the streaming path exists and can be evolved into true SSE without changing the frontend-facing API contract.
