# Product Outline
Singapore Primary Math Tutoring App targeting the 2026 PSLE syllabus, providing AI-powered math tutoring with problem generation, step-by-step explanations, and progress tracking.

# Architecture & Tech Stack
- **Backend**: Java 25 + Spring Boot 4.0.3 (Spring Framework 7) + Spring AI 2.0.0-M2
- **Frontend**: Kotlin Multiplatform (KMP) + Compose for Web (Wasm)
- **Database/Cache**: PostgreSQL 17 with `pgvector` for RAG embeddings, Redis 7
- **LLM Engine**: Ollama (local) or cloud LLMs via Spring AI

# Key Directories
- [`backend/`](backend/) - Spring Boot application containing core logic (e.g., [`MathSolverOrchestrator.java`](backend/src/main/java/com/mathlearning/agent/MathSolverOrchestrator.java)).
- [`frontend/`](frontend/) - KMP + Compose Wasm frontend, split into [`shared/`](frontend/shared/) and [`webApp/`](frontend/webApp/).
- [`infra/`](infra/) - Docker provisioning and database initializations (e.g., pgvector init in [`infra/init-db/01-init-pgvector.sql`](infra/init-db/01-init-pgvector.sql)).

# Conventions & Patterns
- **DTOs & Models**: Use Java Records for DTOs and value objects (e.g., [`SolveRequest.java`](backend/src/main/java/com/mathlearning/model/SolveRequest.java)).
- **AI Integration**: Use Spring AI `ChatClient` for all LLM interactions and `VectorStore` (backed by pgvector) for RAG retrieval tasks.
- **Language Features**: Ensure preview features are enabled (`--enable-preview`) to utilize the latest Java 25 capabilities.
- **Backend Structure**: Follow standard Spring Boot conventional layers (`controller`, `service`, `repository`, `model`).

# Developer Workflows
- **Start Infrastructure**: Run `cd infra && docker compose up -d` to initialize Postgres+pgvector and Redis.
- **Run Backend locally**: Execute `./gradlew bootRun --args='--spring.profiles.active=dev'` from the backend root.
