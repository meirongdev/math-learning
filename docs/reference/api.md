# API Reference

Base URL: `http://localhost:8080` (development)

All request and response bodies are `application/json` unless noted.

---

## Auth

### Register

```
POST /api/v1/auth/register
```

**Request**

```json
{
  "email": "parent@example.com",
  "password": "Test1234!"
}
```

**Response `201 Created`**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Errors**: `409 Conflict` if email already registered.

---

### Login

```
POST /api/v1/auth/login
```

**Request**

```json
{
  "email": "parent@example.com",
  "password": "Test1234!"
}
```

**Response `200 OK`**

```json
{
  "token": "<jwt>",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "parent@example.com",
  "expiresAt": "2026-03-07T10:00:00Z"
}
```

> JWT is **enforced** on all endpoints except `/api/v1/auth/**` and `/actuator/**`. Include `Authorization: Bearer <jwt>` in all requests.

---

## Solve

All solve endpoints require a valid JWT token.

### Solve (structured JSON)

```
POST /api/v1/solve
Authorization: Bearer <jwt>
```

**Request**

```json
{
  "question": "Amy has 24 sweets. She gives 1/3 to Bob. How many does Amy have left?",
  "grade": 3,
  "studentId": "optional-student-uuid"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `question` | string | yes | Math problem text (max 500 chars) |
| `grade` | integer | yes | Student grade: 1–6 |
| `studentId` | string (UUID) | no | Student profile ID; if provided, the solve result is saved to `solve_records` and `knowledge_progress` is updated |

**Response `200 OK`**

```json
{
  "parentGuide": "This question covers P3 fractions — finding a fraction of a whole number...",
  "childScript": "Imagine 24 sweets in a bag! We split them into 3 equal groups...",
  "barModelJson": "{\"title\":\"Amy's Sweets\",\"bars\":[{\"label\":\"Total\",\"segments\":[...]}]}",
  "knowledgeTags": ["basic_fractions", "whole_numbers"]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `parentGuide` | string | 2–3 sentence guide for the parent |
| `childScript` | string | Fun step-by-step explanation for the child |
| `barModelJson` | string | JSON-encoded bar model (see Bar Model schema below) |
| `knowledgeTags` | string[] | PSLE knowledge codes covered by this question |

Results are **cached by question + grade** for 24 hours. First call ~16s, cached calls < 100ms.

---

### Solve Stream (SSE)

```
POST /api/v1/solve/stream
Content-Type: application/json
Accept: text/event-stream
Authorization: Bearer <jwt>
```

Same request body as `/api/v1/solve`. Response is a Server-Sent Events stream — each section arrives as a separate event once the full pipeline completes.

**SSE Events**

```
data: {"type":"parent_guide","content":"This question covers..."}

data: {"type":"child_script","content":"Imagine 24 sweets..."}

data: {"type":"bar_model","content":"{\"title\":\"Amy's Sweets\",...}"}

data: {"type":"knowledge_tags","content":"basic_fractions, whole_numbers"}

data: [DONE]
```

On error:

```
data: {"type":"error","content":"LLM timeout after 60s"}

data: [DONE]
```

> Implementation note: the current SSE endpoint computes the full result first, then emits it section by section. True token-level streaming requires a future refactor.

---

## Students

All student endpoints require a valid JWT token. Students are scoped to the authenticated user (parent).

### Create Student Profile

```
POST /api/v1/students
Authorization: Bearer <jwt>
```

**Request**

```json
{
  "name": "Alice",
  "grade": 3
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | yes | Student name (non-blank) |
| `grade` | integer | yes | Student grade: 1–6 |

**Response `201 Created`**

```json
{
  "studentId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Alice",
  "grade": 3,
  "createdAt": "2026-03-06T10:00:00Z"
}
```

---

### List Students

```
GET /api/v1/students
Authorization: Bearer <jwt>
```

**Response `200 OK`**

```json
[
  {
    "studentId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Alice",
    "grade": 3,
    "createdAt": "2026-03-06T10:00:00Z"
  }
]
```

---

## Solve Records

### Get Solve History

```
GET /api/v1/records/{studentId}?page=0&size=20
Authorization: Bearer <jwt>
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | 0 | Page number (0-based) |
| `size` | integer | 20 | Page size |

**Response `200 OK`** — paginated `Page<RecordResponse>`

```json
{
  "content": [
    {
      "id": "...",
      "questionText": "Amy has 24 sweets...",
      "parentGuide": "This covers P3 fractions...",
      "childScript": "Let's share some sweets...",
      "barModelJson": "{...}",
      "knowledgeTags": ["basic_fractions"],
      "createdAt": "2026-03-06T10:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

---

## Knowledge Progress

### Get Knowledge Progress

```
GET /api/v1/knowledge/{studentId}
Authorization: Bearer <jwt>
```

**Response `200 OK`**

```json
[
  {
    "id": "...",
    "knowledgeCode": "basic_fractions",
    "masteryScore": 0.00,
    "attemptCount": 3,
    "correctCount": 0,
    "updatedAt": "2026-03-06T10:00:00Z"
  }
]
```

Results are sorted by `attemptCount` descending (most practiced topics first).

---

## Bar Model Schema

The `barModelJson` field contains a JSON string with this structure:

```json
{
  "title": "Amy's Sweets",
  "bars": [
    {
      "label": "Total (24)",
      "segments": [
        { "value": 8, "color": "#EF9A9A", "label": "Bob's share (1/3)" },
        { "value": 16, "color": "#A5D6A7", "label": "Amy keeps (2/3)" }
      ]
    }
  ],
  "annotations": ["1/3 of 24 = 8", "24 - 8 = 16"]
}
```

---

## Knowledge Tag Reference

Tags follow the format `topic` or `topic.subtopic`.

| Tag | Grade | Description |
|-----|-------|-------------|
| `whole_numbers` | P1–P3 | Addition, subtraction, multiplication, division |
| `basic_fractions` | P1–P3 | Simple fractions of a whole |
| `measurement` | P1–P3 | Length, mass, volume, time |
| `geometry` | P1–P3 | Shapes, angles, perimeter |
| `fractions.of_remainder` | P4 | Fraction of a remainder |
| `fractions.multiplication` | P4 | Multiplying fractions |
| `decimals.money` | P4 | Decimal operations in money context |
| `measurement.area_perimeter` | P4 | Area and perimeter |
| `ratio.basic` | P5 | Basic ratio concepts |
| `ratio.before_after` | P5 | Before-and-after ratio problems |
| `average.find_missing` | P5 | Finding missing value given average |
| `fractions.working_backwards` | P5 | Working backwards with fractions |
| `algebra.substitution` | P6 | Substituting values into expressions |
| `algebra.forming_equation` | P6 | Forming equations from word problems |
| `algebra.multi_step_equation` | P6 | Multi-step algebraic equations |

---

## Error Response Format

All error responses use a consistent format via `@ControllerAdvice`:


```json
{
  "code": "INVALID_INPUT",
  "message": "grade must be between 1 and 6"
}
```

| Code | HTTP Status | Meaning |
|------|-------------|---------|
| `VALIDATION_ERROR` | 400 | Validation failure |
| `UNAUTHORIZED` | 401 | Missing or invalid JWT |
| `NOT_FOUND` | 404 | Resource not found |
| `LLM_TIMEOUT` | 504 | LLM call exceeded timeout |
| `LLM_PARSE_ERROR` | 500 | LLM returned invalid JSON |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
