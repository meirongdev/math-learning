# Tutorial: Add PSLE Questions to the Knowledge Base

The RAG retrieval system draws on a set of PSLE questions stored in the vector database. This tutorial explains how to add new questions so they become available as context in the Planner Agent.

---

## How Questions Are Loaded

On every backend startup, `QuestionImportService` reads `backend/src/main/resources/data/sg-math-questions.json` and imports any questions not already present in the `vector_store` table. Deduplication is by content hash — re-running the app with the same questions is safe.

The import process:
1. Reads the JSON file
2. Embeds each question using `nomic-embed-text` (768-dim via Ollama)
3. Stores the embedding + metadata in `vector_store`

---

## Question JSON Format

File: `backend/src/main/resources/data/sg-math-questions.json`

```json
[
  {
    "id": "p5-ratio-001",
    "grade": 5,
    "topic": "ratio.before_after",
    "difficulty": "medium",
    "content": "The ratio of Ali's savings to Ben's savings is 3:5. After Ali saves another $12, the ratio becomes 3:4. How much does Ben have?",
    "answer": "$20",
    "solution_steps": [
      "Let Ali = 3u, Ben = 5u initially",
      "After: Ali = 3u + 12, Ben = 5u",
      "New ratio: (3u + 12) : 5u = 3 : 4",
      "4(3u + 12) = 3(5u) → 12u + 48 = 15u → u = 16",
      "Ben = 5 × 16 = $80... (check and revise)"
    ]
  }
]
```

| Field | Required | Description |
|-------|----------|-------------|
| `id` | yes | Unique identifier (e.g. `p5-ratio-001`) |
| `grade` | yes | Integer 1–6 — used for grade filtering in RAG retrieval |
| `topic` | yes | Knowledge tag (see [API reference](../reference/api.md#knowledge-tag-reference)) |
| `difficulty` | yes | `"easy"`, `"medium"`, or `"hard"` |
| `content` | yes | The question text that gets embedded and used as context |
| `answer` | no | Final answer (not embedded, stored in metadata) |
| `solution_steps` | no | Step-by-step solution (not embedded, stored in metadata) |

The `content` field is what gets embedded and retrieved; keep it complete and self-contained.

---

## Adding Questions

1. Open `backend/src/main/resources/data/sg-math-questions.json`

2. Add new question objects to the JSON array:

   ```json
   {
     "id": "p4-fractions-015",
     "grade": 4,
     "topic": "fractions.of_remainder",
     "difficulty": "easy",
     "content": "Mary spent 1/3 of her money on a book and 1/4 of the remainder on a pen. She had $18 left. How much money did she start with?"
   }
   ```

3. Restart the backend — questions are imported automatically on startup:

   ```bash
   make backend-run
   ```

   Look for log lines like:
   ```
   Importing 3 new questions into vector store
   Question import completed: 3 questions added
   ```

---

## Verifying the Import

```bash
# Count questions in vector store
psql postgresql://mathlearning:mathlearning@localhost:5432/mathlearning \
  -c "SELECT COUNT(*) FROM vector_store;"

# Check by grade
psql postgresql://mathlearning:mathlearning@localhost:5432/mathlearning \
  -c "SELECT metadata->>'grade' AS grade, COUNT(*) FROM vector_store GROUP BY grade ORDER BY grade;"
```

---

## Removing or Updating Questions

There is no automatic update mechanism — the import is append-only (dedup by content).

To remove a question or force a full re-import:

```bash
# Delete all vectors and re-import from scratch
psql postgresql://mathlearning:mathlearning@localhost:5432/mathlearning \
  -c "DELETE FROM vector_store;"

make backend-run   # re-imports all questions
```

Or reset the entire database (deletes all data including users):

```bash
make infra-reset
make backend-run
```

---

## Grade Filtering

RAG retrieval only returns questions where `metadata.grade <= request.grade`. A P5 student will see questions from P1–P5 as context, never P6. This is enforced in `RagRetrievalService.retrieveSimilarQuestions()`.

Keep the `grade` field accurate so the RAG context is appropriate for each grade level.
