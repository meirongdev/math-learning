# CLAUDE.md - Project Context

## Project

Singapore Primary Math Tutoring App targeting the 2026 PSLE syllabus. The app provides AI-powered math tutoring for primary school students, with problem generation, step-by-step explanations, and progress tracking.

## Tech Stack

- **Backend**: Java 25 + Spring Boot 4.0.3 (Spring Framework 7) + Spring AI 2.0.0-M2
- **Build**: Gradle 9.2
- **Frontend**: Kotlin Multiplatform (KMP) + Compose for Web (Wasm)
- **Database**: PostgreSQL 17 with pgvector (768-dim, HNSW index) for RAG embeddings
- **Cache**: Redis 7
- **LLM**: Ollama (local, qwen3.5) or cloud LLM providers (DeepSeek-R1) via Spring AI

## Local Development

1. Start infrastructure services:
   ```bash
   cd infra && docker compose up -d
   ```
2. Run the backend:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

## Key Directories

- `backend/` - Spring Boot application (Java 25)
- `frontend/` - KMP + Compose Wasm frontend
- `infra/` - Docker Compose, Dockerfiles, DB init scripts
- `docs/` - Design documents and specifications

## Coding Conventions

- Use Java records for DTOs and value objects.
- Use Spring AI `ChatClient` for all LLM interactions.
- Use pgvector with Spring AI `VectorStore` for RAG retrieval.
- Enable preview features (`--enable-preview`) for latest Java 25 capabilities.
- Follow standard Spring Boot project structure (`controller/`, `service/`, `repository/`, `model/`).
