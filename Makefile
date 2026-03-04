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

.PHONY: infra-up infra-down infra-logs infra-ps

infra-up: ## Start infrastructure services (PostgreSQL, Redis, Ollama)
	docker compose -f $(INFRA_DIR)/docker-compose.yml up -d

infra-down: ## Stop infrastructure services
	docker compose -f $(INFRA_DIR)/docker-compose.yml down

infra-logs: ## Tail infrastructure logs
	docker compose -f $(INFRA_DIR)/docker-compose.yml logs -f

infra-ps: ## Show infrastructure service status
	docker compose -f $(INFRA_DIR)/docker-compose.yml ps

# ---- Backend ----

.PHONY: backend-build backend-run backend-test backend-clean backend-jar

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

# ---- Frontend ----

.PHONY: frontend-build frontend-run frontend-test frontend-dist frontend-clean

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

# ---- Docker ----

.PHONY: docker-build docker-up docker-down

docker-build: ## Build backend Docker image
	docker build -f $(INFRA_DIR)/backend.Dockerfile -t math-learning-backend $(BACKEND_DIR)

docker-up: infra-up ## Start all services including backend container
	docker compose -f $(INFRA_DIR)/docker-compose.yml up -d

docker-down: ## Stop all Docker services and remove volumes
	docker compose -f $(INFRA_DIR)/docker-compose.yml down -v

# ---- Composite ----

.PHONY: build test clean dev

build: backend-build frontend-build ## Build backend and frontend

test: backend-test frontend-test ## Run all tests

clean: backend-clean frontend-clean ## Clean all build artifacts

dev: infra-up backend-run ## Start infra + backend in dev mode
