# ============================================================
# Math Learning Project - Root Makefile
# ============================================================

BACKEND_DIR   := backend
FRONTEND_DIR  := frontend
INFRA_DIR     := infra

BACKEND_GRADLE  := $(BACKEND_DIR)/gradlew
FRONTEND_GRADLE := $(FRONTEND_DIR)/gradlew

.PHONY: help
help: ## Show this help
	@grep -E '^[a-zA-Z_.-]+:.*##' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*## "}; {printf "  \033[36m%-24s\033[0m %s\n", $$1, $$2}'

# ---- Infrastructure ----

.PHONY: infra-up infra-down infra-reset infra-logs infra-ps ollama-pull

infra-up: ## Start infrastructure services (PostgreSQL, Redis)
	docker compose -f $(INFRA_DIR)/docker-compose.yml up -d

infra-down: ## Stop infrastructure services
	docker compose -f $(INFRA_DIR)/docker-compose.yml down

infra-reset: ## Reset infra: stop, remove volumes, restart (WARNING: deletes all data)
	docker compose -f $(INFRA_DIR)/docker-compose.yml down -v
	docker compose -f $(INFRA_DIR)/docker-compose.yml up -d

infra-logs: ## Tail infrastructure logs
	docker compose -f $(INFRA_DIR)/docker-compose.yml logs -f

infra-ps: ## Show infrastructure service status
	docker compose -f $(INFRA_DIR)/docker-compose.yml ps

ollama-pull: ## Pull required Ollama models (qwen3.5 + nomic-embed-text)
	ollama pull qwen3.5
	ollama pull nomic-embed-text

# ---- Backend ----

.PHONY: backend-build backend-run backend-test backend-clean backend-jar backend-format

backend-build: ## Build backend
	cd $(BACKEND_DIR) && ./gradlew build

backend-run: ## Run backend in dev mode
	cd $(BACKEND_DIR) && ./gradlew bootRun --args='--spring.profiles.active=dev'

backend-test: ## Run backend tests
	cd $(BACKEND_DIR) && ./gradlew test

backend-clean: ## Clean backend build artifacts
	cd $(BACKEND_DIR) && ./gradlew clean

backend-jar: ## Build backend fat JAR
	cd $(BACKEND_DIR) && ./gradlew bootJar

backend-format: ## Format backend code (Java)
	cd $(BACKEND_DIR) && ./gradlew spotlessApply

# ---- Frontend ----

.PHONY: frontend-build frontend-run frontend-test frontend-dist frontend-clean frontend-format

frontend-build: ## Build frontend (all modules)
	cd $(FRONTEND_DIR) && ./gradlew build

frontend-run: ## Run frontend dev server with hot-reload
	cd $(FRONTEND_DIR) && ./gradlew :webApp:wasmJsBrowserRun -t

frontend-test: ## Run frontend shared module tests
	cd $(FRONTEND_DIR) && ./gradlew :shared:wasmJsBrowserTest

frontend-dist: ## Build frontend production distribution
	cd $(FRONTEND_DIR) && ./gradlew :webApp:wasmJsBrowserDistribution

frontend-clean: ## Clean frontend build artifacts
	cd $(FRONTEND_DIR) && ./gradlew clean

frontend-format: ## Format frontend code (Kotlin)
	cd $(FRONTEND_DIR) && ./gradlew spotlessApply

# ---- Infra ----

.PHONY: infra-format

infra-format: backend-format ## Format infrastructure files (SQL, Docker)

# ---- Docker ----

.PHONY: docker-build docker-up docker-down

docker-build: ## Build backend Docker image
	docker build -f $(INFRA_DIR)/backend.Dockerfile -t math-learning-backend $(BACKEND_DIR)

docker-up: infra-up ## Start all services including backend container
	docker compose -f $(INFRA_DIR)/docker-compose.yml up -d

docker-down: ## Stop all Docker services and remove volumes
	docker compose -f $(INFRA_DIR)/docker-compose.yml down -v

# ---- Health Check ----

.PHONY: check check-backend check-db check-ollama

check: check-backend check-db check-ollama ## Run all health checks

check-backend: ## Check backend API health
	@curl -sf http://localhost:8080/actuator/health | python3 -m json.tool || echo "[FAIL] Backend not reachable"

check-db: ## Check PostgreSQL connection
	@psql postgresql://mathlearning:mathlearning@localhost:5432/mathlearning -c "SELECT 1;" > /dev/null 2>&1 \
		&& echo "[OK] PostgreSQL" || echo "[FAIL] PostgreSQL not reachable"

check-ollama: ## Check Ollama service
	@curl -sf http://localhost:11434/api/tags > /dev/null \
		&& echo "[OK] Ollama" || echo "[FAIL] Ollama not reachable (run: ollama serve)"

# ---- Smoke Test ----

.PHONY: smoke-test

smoke-test: ## Quick API smoke test (register + login + generate problem)
	@echo "--- Register ---"
	@curl -s -X POST http://localhost:8080/api/v1/auth/register \
		-H "Content-Type: application/json" \
		-d '{"username":"testuser","password":"Test1234!","email":"test@example.com"}' | python3 -m json.tool
	@echo "--- Login ---"
	@curl -s -X POST http://localhost:8080/api/v1/auth/login \
		-H "Content-Type: application/json" \
		-d '{"username":"testuser","password":"Test1234!"}' | python3 -m json.tool

# ---- Logs & Stop ----

.PHONY: logs stop

logs: ## Tail backend and frontend logs (from dev-full)
	@tail -f .logs/backend.log .logs/frontend.log 2>/dev/null || echo "No logs found. Run 'make dev-full' first."

stop: ## Stop background services started by dev-full
	@pkill -f "spring.profiles.active=dev" 2>/dev/null && echo "Backend stopped" || echo "Backend was not running"
	@pkill -f "wasmJsBrowserRun" 2>/dev/null && echo "Frontend stopped" || echo "Frontend was not running"

# ---- Composite ----

.PHONY: build test clean dev dev-full setup format

format: backend-format frontend-format infra-format ## Format all code (backend, frontend, infra)

setup: infra-up ollama-pull ## First-time setup: start infra and pull Ollama models
	@echo "Setup complete. Run 'make dev' to start the backend."

build: backend-build frontend-build ## Build backend and frontend

test: backend-test frontend-test ## Run all tests

clean: backend-clean frontend-clean ## Clean all build artifacts

dev: infra-up backend-run ## Start infra + backend in dev mode (blocking, logs to terminal)

dev-full: infra-up ## Start infra + backend + frontend in background (logs → .logs/)
	@mkdir -p .logs
	@echo "Starting backend..."
	@cd $(BACKEND_DIR) && ./gradlew bootRun --args='--spring.profiles.active=dev' > ../.logs/backend.log 2>&1 &
	@echo "Starting frontend..."
	@cd $(FRONTEND_DIR) && ./gradlew :webApp:wasmJsBrowserRun > ../.logs/frontend.log 2>&1 &
	@echo ""
	@echo "Services starting in background. Check URLs once logs show ready:"
	@echo "  Backend:  http://localhost:8080"
	@echo "  Frontend: http://localhost:8081"
	@echo ""
	@echo "  make logs   — follow all logs"
	@echo "  make stop   — stop all background services"
