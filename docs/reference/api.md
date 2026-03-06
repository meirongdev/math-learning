# API Reference

Base URL: `http://localhost:8080` (development)

All request/response bodies are `application/json` unless noted.

---

## Scope Notes

- `Implemented` sections reflect current backend code behavior.
- `Planned (Phase 6+)` sections are design targets and are not yet available by default.

---

## Auth (Implemented)

### Register

`POST /api/v1/auth/register`

Request:

```json
{
  "email": "parent@example.com",
  "password": "Test1234!"
}
```

Response `201 Created`:

```json
{
  "message": "Registration successful",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

Error `409 Conflict`:

```json
{
  "error": "Email already registered"
}
```

### Login

`POST /api/v1/auth/login`

Request:

```json
{
  "email": "parent@example.com",
  "password": "Test1234!"
}
```

Response `200 OK`:

```json
{
  "token": "<jwt>",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "expiresAt": "2026-03-07T10:00:00Z"
}
```

Error `401 Unauthorized`:

```json
{
  "error": "Invalid email or password"
}
```

Authentication rule:
- All endpoints require `Authorization: Bearer <jwt>` except `/api/v1/auth/**` and `/actuator/**`.

---

## Solve (Implemented)

### Solve JSON

`POST /api/v1/solve`

Request:

```json
{
  "question": "Amy has 24 sweets. She gives 1/3 to Bob. How many does Amy have left?",
  "grade": 3,
  "studentId": "optional-student-uuid"
}
```

Constraints:
- `question`: non-blank, max 500 chars
- `grade`: integer between 1 and 6

Response `200 OK`:

```json
{
  "parentGuide": "This question covers P3 fractions...",
  "childScript": "Let's share some sweets...",
  "barModelJson": "{\"title\":\"Sweets\",\"bars\":[...]}",
  "knowledgeTags": ["basic_fractions", "whole_numbers"]
}
```

Behavior:
- If `studentId` is provided and exists, a solve record is persisted and knowledge progress is updated.
- Response is cached by `question + grade` for 24h.

### Solve Stream (SSE)

`POST /api/v1/solve/stream`

Headers:
- `Accept: text/event-stream`
- `Content-Type: application/json`

Request body is the same as `POST /api/v1/solve`.

SSE events (current behavior):

```text
data: {"type":"parent_guide","content":"..."}

data: {"type":"child_script","content":"..."}

data: {"type":"bar_model","content":"{...}"}

data: {"type":"knowledge_tags","content":"basic_fractions, whole_numbers"}

data: [DONE]
```

Note:
- Current implementation computes the full solve result first, then emits section events. It is not token-level streaming.

---

## Students (Implemented)

### Create Student

`POST /api/v1/students`

Request:

```json
{
  "name": "Alice",
  "grade": 3
}
```

Response `201 Created`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Alice",
  "grade": 3
}
```

### List Students

`GET /api/v1/students`

Response `200 OK`:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Alice",
    "grade": 3,
    "createdAt": "2026-03-06T10:00:00Z"
  }
]
```

---

## Records (Implemented)

### Get Solve History

`GET /api/v1/records/{studentId}?page=0&size=20`

Response `200 OK`:

```json
{
  "records": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "questionText": "Amy has 24 sweets...",
      "parentGuide": "This covers P3 fractions...",
      "childScript": "Let's share some sweets...",
      "barModelJson": "{...}",
      "knowledgeTags": ["basic_fractions"],
      "createdAt": "2026-03-06T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## Knowledge (Implemented)

### Get Knowledge Progress

`GET /api/v1/knowledge/{studentId}`

Response `200 OK`:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "knowledgeCode": "basic_fractions",
    "attemptCount": 3,
    "correctCount": 0,
    "masteryScore": "0.00",
    "updatedAt": "2026-03-06T10:00:00Z"
  }
]
```

---

## Error Format

Current state:
- Most application exceptions are returned by global handler as:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "question must not be blank"
}
```

- Auth controller currently returns `{ "error": "..." }` for login/register business errors.

Planned cleanup:
- Unify auth error responses to `{code, message}` in a later refactor.

---

## Planned (Phase 6+)

These endpoints are part of design docs and not implemented yet:

- `DELETE /api/v1/students/{id}`
- `GET /api/v1/knowledge/graph`
- `GET /api/v1/knowledge/{studentId}/progress`
- `PUT /api/v1/knowledge/{studentId}/progress/{nodeCode}`
- `PATCH /api/v1/records/{recordId}/rating`
- `GET /api/v1/questions?tag={code}&grade={n}&limit={n}`
