# API Reference

Base URL: `http://localhost:8080` (development)

All request/response bodies are `application/json` unless noted.

---

## Auth

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
  "code": "CONFLICT",
  "message": "Email already registered"
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
  "code": "UNAUTHORIZED",
  "message": "Invalid email or password"
}
```

Authentication rule:
- All endpoints require `Authorization: Bearer <jwt>` except `/api/v1/auth/**`, `/api/v1/knowledge/graph`, and `/actuator/**`.

---

## Solve

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

## Students

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

### Delete Student

`DELETE /api/v1/students/{id}`

Deletes a student owned by the authenticated user. Cascades to associated solve records and knowledge progress.

Response `204 No Content` — deleted successfully.

Error `404 Not Found` — student not found or not owned by the caller.

---

## Records

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
      "rating": 4,
      "createdAt": "2026-03-06T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### Rate a Solve Record

`PATCH /api/v1/records/{recordId}/rating`

Request:

```json
{
  "rating": 4
}
```

Constraints:
- `rating`: integer between 1 and 5

Response `200 OK`:

```json
{
  "recordId": "550e8400-e29b-41d4-a716-446655440000",
  "rating": 4,
  "suggestedMastery": "MASTERED"
}
```

Behavior:
- Saves the rating to `solve_records.rating`.
- Auto-updates mastery level for associated knowledge tags based on rating:
  - 4–5 stars → `MASTERED`
  - 2–3 stars → `FAMILIAR`
  - 1 star → `UNKNOWN`

Error `404 Not Found` — record not found.

---

## Knowledge

### Get Knowledge Progress (legacy)

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
    "masteryLevel": "FAMILIAR",
    "updatedAt": "2026-03-06T10:00:00Z"
  }
]
```

### Get Knowledge Graph (public)

`GET /api/v1/knowledge/graph`

No authentication required. Returns the full P1–P6 knowledge node tree.

Response `200 OK`:

```json
[
  {
    "code": "numbers",
    "nameEn": "Numbers & Algebra",
    "nameZh": "数与代数",
    "parentCode": null,
    "gradeStart": 1,
    "children": [
      {
        "code": "whole_numbers",
        "nameEn": "Whole Numbers",
        "nameZh": "整数",
        "parentCode": "numbers",
        "gradeStart": 1,
        "children": [
          {
            "code": "wn.addition",
            "nameEn": "Addition",
            "nameZh": "加法",
            "parentCode": "whole_numbers",
            "gradeStart": 1,
            "children": []
          }
        ]
      }
    ]
  }
]
```

### Get Student Knowledge Progress

`GET /api/v1/knowledge/{studentId}/progress`

Same response format as `GET /api/v1/knowledge/{studentId}`.

### Update Mastery Level

`PUT /api/v1/knowledge/{studentId}/progress/{nodeCode}`

Request:

```json
{
  "masteryLevel": "MASTERED"
}
```

Constraints:
- `masteryLevel`: must be `UNKNOWN`, `FAMILIAR`, or `MASTERED`

Response `204 No Content` — mastery updated successfully.

---

## Assessment Questions

### Get Questions

`GET /api/v1/questions?tag={nodeCode}&grade={n}&limit={n}`

Query parameters (all optional):
- `tag`: knowledge node code to filter by (e.g. `frac.add_sub`)
- `grade`: grade level 1–6
- `limit`: max results (default 10, max 50)

Response `200 OK`:

```json
[
  {
    "id": "a0000005-0000-0000-0000-000000000001",
    "questionText": "Amy has 3/4 of a pizza. She eats 1/3 of what she has...",
    "grade": 5,
    "difficulty": "medium",
    "answerHint": "1/3 x 3/4 = 1/4"
  }
]
```

---

## Error Format

All application exceptions use a unified format:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "question must not be blank"
}
```

Error codes:
- `VALIDATION_ERROR` (400) — input validation failure
- `UNAUTHORIZED` (401) — invalid or missing credentials
- `CONFLICT` (409) — duplicate resource (e.g. email already registered)
- `LLM_TIMEOUT` (504) — LLM call timed out
- `LLM_PARSE_ERROR` (500) — LLM response could not be parsed
- `ACCESS_DENIED` (403) — forbidden
- `INTERNAL_ERROR` (500) — unexpected server error
