# Configuration Reference

## Profiles

| Profile | Command | Use case |
|---------|---------|----------|
| `dev` | `./gradlew bootRun --args='--spring.profiles.active=dev'` | Local development with Ollama |
| `prod` | set `SPRING_PROFILES_ACTIVE=prod` | Production with DeepSeek-R1 |
| (default) | no profile arg | Base config only — requires manual DB/Redis settings |

---

## application.yml (base — all profiles)

```yaml
server:
  port: 8080
  servlet:
    async:
      timeout: 300000         # 5 min SSE timeout

spring:
  threads:
    virtual:
      enabled: true           # Java 25 Virtual Threads

  jpa:
    hibernate:
      ddl-auto: validate      # Flyway owns schema; Hibernate only validates
    open-in-view: false

  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## application-dev.yml (local development)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mathlearning
    username: mathlearning
    password: mathlearning

  data:
    redis:
      host: localhost
      port: 6379

  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: qwen3.5
      embedding:
        model: nomic-embed-text
    vectorstore:
      pgvector:
        initialize-schema: true    # Auto-creates vector_store table
        dimensions: 768            # Must match nomic-embed-text output
        index-type: hnsw
```

> To change Ollama models or switch to DeepSeek-R1, see [tutorials/switch-llm-provider.md](../tutorials/switch-llm-provider.md).

---

## application-prod.yml (production)

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}

  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}   # empty = no auth

  ai:
    openai:
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        options:
          model: deepseek-reasoner
```

### Required environment variables (production)

| Variable | Example | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://db:5432/mathlearning` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | `mathlearning` | DB user |
| `DATABASE_PASSWORD` | `<secret>` | DB password |
| `REDIS_HOST` | `redis` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port (default 6379) |
| `REDIS_PASSWORD` | `<secret>` | Redis password (optional) |
| `DEEPSEEK_API_KEY` | `sk-...` | DeepSeek API key |

---

## Infrastructure (Docker Compose)

`infra/docker-compose.yml` starts:

| Service | Image | Port | Notes |
|---------|-------|------|-------|
| PostgreSQL | `pgvector/pgvector:pg17` | 5432 | pgvector extension included |
| Redis | `redis:7-alpine` | 6379 | No auth in dev |

Ollama runs as a host process (`ollama serve`), not in Docker.

```bash
make infra-up      # start PostgreSQL + Redis
make infra-down    # stop
make infra-reset   # WARNING: wipes all data and restarts
```

---

## Logging

Default log levels (`application.yml`):

```yaml
logging:
  level:
    com.mathlearning: DEBUG         # all app code
    org.springframework.ai: DEBUG   # Spring AI internals
```

Dev profile adds:

```yaml
logging:
  level:
    org.springframework.web.servlet.DispatcherServlet: DEBUG
```

To see LLM request/response bodies, add:

```yaml
logging:
  level:
    org.springframework.ai.ollama: TRACE
```

