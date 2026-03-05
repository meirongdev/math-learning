# Make Targets

Run `make help` to see all targets with descriptions.

---

## Composite (most commonly used)

| Target | Description |
|--------|-------------|
| `make setup` | First-time: `infra-up` + `ollama-pull` |
| `make dev` | Start infra + backend in dev mode (blocking, logs to terminal) |
| `make dev-full` | Start infra + backend + frontend in background (logs → `.logs/`) |
| `make logs` | Follow background logs from `dev-full` |
| `make stop` | Stop background services started by `dev-full` |
| `make build` | Build backend + frontend |
| `make test` | Run all tests (backend + frontend) |
| `make clean` | Clean all build artifacts |
| `make format` | Format all code (Java via Spotless + Kotlin via Spotless) |

---

## Infrastructure

| Target | Description |
|--------|-------------|
| `make infra-up` | Start PostgreSQL + Redis (detached) |
| `make infra-down` | Stop infrastructure services |
| `make infra-reset` | **WARNING: deletes all data** — stop + remove volumes + restart |
| `make infra-logs` | Tail Docker Compose logs |
| `make infra-ps` | Show service status |
| `make ollama-pull` | Pull `qwen3.5` + `nomic-embed-text` models |

---

## Backend

| Target | Description |
|--------|-------------|
| `make backend-run` | `bootRun --spring.profiles.active=dev` |
| `make backend-build` | Full Gradle build (compile + test + jar) |
| `make backend-test` | Run tests only |
| `make backend-jar` | Build fat JAR (`build/libs/*.jar`) |
| `make backend-clean` | Remove `build/` directory |
| `make backend-format` | Run Spotless formatter |

---

## Frontend

| Target | Description |
|--------|-------------|
| `make frontend-run` | Dev server with hot-reload (`http://localhost:8081`) |
| `make frontend-build` | Build all modules |
| `make frontend-dist` | Build production distribution (output: `frontend/webApp/build/dist/`) |
| `make frontend-test` | Run shared module tests |
| `make frontend-clean` | Remove `build/` directory |
| `make frontend-format` | Run Spotless formatter |

---

## Health Checks

| Target | Description |
|--------|-------------|
| `make check` | Run all health checks (backend + PostgreSQL + Ollama) |
| `make check-backend` | `GET /actuator/health` |
| `make check-db` | `psql SELECT 1` |
| `make check-ollama` | `GET /api/tags` |
| `make smoke-test` | Register + login via curl |

---

## Docker

| Target | Description |
|--------|-------------|
| `make docker-build` | Build backend Docker image locally |
| `make docker-up` | Start all services including backend container |
| `make docker-down` | Stop all Docker services and remove volumes |
