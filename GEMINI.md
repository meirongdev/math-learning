# Math Learning Project - GEMINI Context

This project is a specialized Singapore Primary Math (P1-P6) tutoring application designed for the 2026 MOE syllabus. It leverages advanced AI orchestration to guide parents and students through complex mathematical concepts using the Concrete-Pictorial-Abstract (CPA) approach and Model Method.

## 🚀 Project Overview

- **Core Mission**: Help parents/students navigate the 2026 PSLE math challenges, specifically focusing on **Algebra** and **Ratio & Average**.
- **Key Feature**: An **Agentic Workflow** that decomposes math problems into a structured plan, generates visual Bar Models (CPA approach), and tailors the explanation for both parents (teaching guide) and children (fun script).
- **Primary Tech Stack**:
    - **Backend**: Java 25 (LTS) with Project Loom (Virtual Threads), Spring Boot 4.0.3, Spring AI 2.x.
    - **AI Models**: DeepSeek-R1 / Gemini 2.0 Pro (Production), Ollama + qwen3.5 (Local Dev).
    - **Frontend**: Kotlin Multiplatform (Compose Wasm) for cross-platform Web, Android, and iOS support.
    - **Database**: PostgreSQL 17+ with `pgvector` for RAG-based similarity search, Redis for caching.

## 🛠️ Architecture

The system uses a multi-agent orchestration pattern via **Spring AI 2.x**:
1.  **Planner Agent**: Parses problem intent and identifies 2026 syllabus knowledge points.
2.  **CPA Designer Agent**: Converts steps into structured **Bar Model** JSON for frontend rendering.
3.  **Persona Agent**: Refines the output tone for different audiences (Parent vs. Child).

## 💻 Development Commands

A root-level `Makefile` is provided for common tasks:

### Infrastructure
- `make infra-up`: Starts PostgreSQL, Redis, and Ollama via Docker.
- `make infra-down`: Stops all infrastructure containers.

### Backend (Spring Boot)
- `make backend-run`: Runs the backend with the `dev` profile (uses Ollama/Local models).
- `make backend-test`: Executes the backend test suite (JUnit).
- `make backend-build`: Builds the project and runs tests.
- **Note**: Requires Java 25 with preview features enabled (`--enable-preview`).

### Frontend (Kotlin Multiplatform)
- `make frontend-run`: Starts the Web development server with hot-reload.
- `make frontend-dist`: Builds the production-ready Wasm distribution.
- `make frontend-test`: Runs the shared module logic tests.

### Composite
- `make dev`: Convenience command to start infrastructure and the backend in dev mode.

## 📏 Development Conventions

- **Java Standard**: Java 25 with `--enable-preview` for Structured Concurrency and Scoped Values.
- **Async/Concurrency**: Use Virtual Threads (`spring.threads.virtual.enabled=true`) for I/O-bound AI agent calls.
- **AI Integration**: Prefer Spring AI abstractions for model interaction and vector storage.
- **Frontend Styling**: Use Compose Multiplatform Material3 components.
- **Database Migrations**: Managed via Flyway in `backend/src/main/resources/db/migration`.
- **RAG Implementation**: Knowledge base queries use `pgvector` for semantic similarity matching of Singapore Math heuristics.

## 📁 Directory Structure

- `backend/`: Spring Boot Java application.
- `frontend/`: Kotlin Multiplatform project.
    - `shared/`: Common business logic and API models.
    - `webApp/`: Compose Wasm application.
- `infra/`: Docker, Flyway, and environment configurations.
- `docs/`: System design, requirements, and development plans.
