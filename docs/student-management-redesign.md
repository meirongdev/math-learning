# Student Management Redesign

## Current Issues

### Bug: Grade selector missing in Add Student form

`App.kt:199` declares `var newStudentGrade by remember { mutableStateOf("P3") }` but the
Add Student form (lines 281–313) only renders a name field — no grade picker. Every student is
created with grade P3 regardless of intent.

### Design problem: student management embedded in the tutoring screen

The current inline `+ Add Student` expansion inside the Student card mixes two distinct concerns
into one screen, and provides no way to edit or delete existing students.

---

## Proposed Design

### Screen flow

```
AuthScreen
    └── MathTutorScreen
            ├── StudentCard  (selector + "Manage" button)
            │       └── StudentManagementDialog  (modal)
            └── SolveCard
```

No separate navigation route is needed. A `StudentManagementDialog` (`AlertDialog`) keeps the
single-page feel while giving student management its own focused surface.

### StudentCard (simplified)

```
┌─────────────────────────────────────────┐
│ Student                      [Manage]   │
│ ┌─────────────────────────────────────┐ │
│ │  Alice (P4)                      ▼  │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

- Dropdown shows existing students for selection.
- "Manage" button opens `StudentManagementDialog`.
- Remove the inline `+ Add Student` expansion entirely.

### StudentManagementDialog

```
┌────────────────────────────────┐
│  Manage Students          [×]  │
├────────────────────────────────┤
│  Alice  P4        [Delete]     │
│  Bob    P6        [Delete]     │
├────────────────────────────────┤
│  Add Student                   │
│  Name:  [________________]     │
│  Grade: [P3            ▼]      │
│                    [Add]       │
└────────────────────────────────┘
```

- List section: each row shows `name (Px)` and a delete icon button.
- Add section: name text field + grade dropdown (P1–P6) + Add button.
- On successful add or delete, the list refreshes in place.
- Dismissing the dialog re-evaluates `selectedStudent` (deselect if deleted).

---

## Implementation Checklist

### Backend

- [x] `DELETE /api/v1/students/{id}` — delete a student owned by the authenticated user.
  - Returns `204 No Content` on success.
  - Returns `404` if not found or not owned by the caller.
  - Cascade deletes associated solve records and knowledge progress (via JPA).
- [ ] `PUT /api/v1/students/{id}` — update name/grade (optional, deferred).

### Frontend – shared module (`Models.kt` / `MathApi.kt`)

- [x] Add `suspend fun deleteStudent(id: String)` to `MathApi`.
- [ ] Add `suspend fun updateStudent(id: String, name: String, grade: Int): Student` (deferred).

### Frontend – `App.kt`

- [x] Remove `showAddStudent`, `newStudentName`, `newStudentGrade` state from `MathTutorScreen`.
- [x] Add `showManageDialog: Boolean` state.
- [x] Extract `StudentManagementDialog` composable:
  - Parameters: `students`, `onAdd`, `onDelete`, `onDismiss`.
  - Internal state: `newName`, `newGrade` (defaults P3), `addGradeExpanded`, `isAdding`.
  - Grade picker uses `ExposedDropdownMenuBox` pattern.
- [x] After dialog closes, if `selectedStudent` was deleted, reset to `students.firstOrNull()`.

### Frontend – error handling

- [x] Show inline error inside the dialog (not the global `errorMessage`) so the tutoring screen
  is not polluted by student management errors.

---

## API Contract

### DELETE /api/v1/students/{id}

**Request**

```
DELETE /api/v1/students/{id}
Authorization: Bearer <token>
```

**Responses**

| Status | Body | Meaning |
|--------|------|---------|
| 204 | — | Deleted successfully |
| 404 | `{"code":"NOT_FOUND","message":"..."}` | Student not found or not owned by caller |
| 401 | — | Not authenticated |

### Updated POST /api/v1/students response

Current response uses key `studentId`; this is inconsistent with `GET /api/v1/students` which
returns `id`. The field has been corrected to `id` (fix applied in `StudentController.java`).

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Alice",
  "grade": 4
}
```

---

## Out of Scope

- Student progress history or solve record association — tracked separately in `KnowledgeProgress`.
- Avatar or profile photo upload.
- Parent account linking to multiple parent users.
