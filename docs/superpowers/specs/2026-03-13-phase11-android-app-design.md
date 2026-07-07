# Phase 11: Android App Design Spec

## Overview

Add an Android app to the existing KMP project, reusing the `shared` module (API client, models, token storage) with independent Compose UI. Targets minSdk 29 (Android 10+), includes CameraX + ML Kit OCR, Room-based offline read cache, and configurable backend URL.

## Scope

- Android only (iOS deferred)
- Full feature parity with Web: Solve, Knowledge, Growth, Mistakes, History
- OCR via ML Kit (Chinese + English)
- Offline read-only (cached knowledge graph, history, achievements)
- Configurable backend URL with connection test

## Project Structure

```
frontend/
├── shared/                          # Existing, add androidMain
│   └── src/
│       ├── commonMain/              # Unchanged (MathApi, Models, TokenStore expect)
│       ├── wasmJsMain/              # Unchanged
│       ├── jvmMain/                 # Unchanged
│       └── androidMain/             # NEW
│           └── kotlin/.../storage/
│               └── TokenStore.kt    # SharedPreferences implementation
├── webApp/                          # Unchanged
└── androidApp/                      # NEW
    ├── build.gradle.kts
    ├── src/main/
    │   ├── AndroidManifest.xml
    │   ├── kotlin/com/mathlearning/android/
    │   │   ├── MathLearningApp.kt          # Application class
    │   │   ├── MainActivity.kt             # Single Activity
    │   │   ├── navigation/Navigation.kt    # NavHost routing
    │   │   ├── ui/
    │   │   │   ├── theme/Theme.kt
    │   │   │   ├── auth/AuthScreen.kt
    │   │   │   ├── solve/SolveScreen.kt
    │   │   │   ├── knowledge/KnowledgeScreen.kt
    │   │   │   ├── growth/GrowthScreen.kt
    │   │   │   ├── mistakes/MistakesScreen.kt
    │   │   │   ├── history/HistoryScreen.kt
    │   │   │   ├── settings/SettingsScreen.kt
    │   │   │   └── components/
    │   │   │       ├── StarRating.kt
    │   │   │       ├── MasteryBadge.kt
    │   │   │       ├── StudentSelector.kt
    │   │   │       └── BarModelCard.kt
    │   │   ├── ocr/OcrManager.kt
    │   │   └── cache/LocalCache.kt
    │   └── res/
    └── src/test/
```

## Architecture

### Data Flow

```
MathApi (shared)  <->  ViewModel  <->  Compose UI
                          |
                    Room LocalCache (read-only cache)
```

Single Activity + Jetpack Navigation + ViewModel per screen.

### ViewModels

| ViewModel | Responsibility | Cache |
|-----------|---------------|-------|
| AuthViewModel | Login/register, token lifecycle | None |
| StudentViewModel | Student CRUD, current selection | None |
| SolveViewModel | Solve request/result/rating | None |
| KnowledgeViewModel | Graph tree + mastery levels | Room (graph tree) |
| GrowthViewModel | Achievements + learning path | Room (achievements) |
| HistoryViewModel | Records + mistakes | Room (last 50 records) |
| SettingsViewModel | Backend URL config | DataStore |

### Dependency Injection

Use **Koin** (lightweight, KMP-friendly). Modules:
- `sharedModule`: `MathApi` singleton (with baseUrl lambda from DataStore)
- `viewModelModule`: all ViewModels
- `cacheModule`: Room database, DAOs
- `ocrModule`: OcrManager

Koin is initialized in `MathLearningApp.onCreate()`.

### Token Lifecycle

- Login success -> `TokenStore.saveToken()` (SharedPreferences via expect/actual)
- App launch -> `TokenStore.loadToken()` + check expiry -> skip login if valid
- 401 response -> clear token -> navigate to AuthScreen

**Android Context for TokenStore**: The `actual` implementations in `androidMain` need a `Context`. Solution: add an `initTokenStore(context: Context)` function in `androidMain` that stores the application context in a module-level `lateinit var`. Called once from `MathLearningApp.onCreate()`. This pattern is already used by libraries like Firebase and Koin on Android.

## Navigation

```
AuthScreen (no bottom bar)
    | login success
MainScaffold (BottomNavigationBar, 5 tabs)
+-- Solve      -> SolveScreen
+-- Knowledge  -> KnowledgeScreen
+-- Growth     -> GrowthScreen
+-- Mistakes   -> MistakesScreen
+-- History    -> HistoryScreen

TopAppBar: Student selector (dropdown) + Settings gear + Logout
Settings -> SettingsScreen (URL config)
```

### Mobile UI Adaptations

| Web | Android |
|-----|---------|
| Horizontal TopBar tabs | BottomNavigationBar |
| Results side-by-side | Vertical scroll, collapsible Cards |
| Knowledge tree flat | LazyColumn + indentation + expand/collapse |
| StudentManagementDialog popup | BottomSheet |
| OCR file upload | CameraX preview -> ML Kit |

## OCR Integration

**Stack**: CameraX + ML Kit Text Recognition v2

**Flow**:
1. SolveScreen camera button -> fullscreen CameraPreview (CameraX PreviewView)
2. User captures photo (ImageCapture)
3. ML Kit TextRecognizer processes InputImage
4. Recognized text fills question input

**OcrManager.kt**:
- Manages CameraX lifecycle (bound to LifecycleOwner)
- `captureAndRecognize(): Flow<OcrState>`
  - `OcrState.Preview` - camera active
  - `OcrState.Processing` - recognizing
  - `OcrState.Success(text)` - done
  - `OcrState.Error(message)` - failed
- Runs both `DEFAULT_OPTIONS` (English) and `ChineseTextRecognizerOptions`, merges results

**Permissions**: `CAMERA` via `rememberLauncherForActivityResult(RequestPermission)`

**Dependencies**:
```
com.google.mlkit:text-recognition:16.0.1
com.google.mlkit:text-recognition-chinese:16.0.1
androidx.camera:camera-camera2:1.4.1
androidx.camera:camera-lifecycle:1.4.1
androidx.camera:camera-view:1.4.1
```

## Offline Cache (Room)

**Database**: `MathLearningDatabase`, 3 tables, read-only cache.

```kotlin
@Entity "cached_knowledge_nodes"
CachedKnowledgeNode(code PK, nameEn, nameZh, parentCode?, gradeStart, sortOrder)

@Entity "cached_records"
CachedRecord(id PK, studentId, questionText, parentGuide?, childScript?,
             barModelJson?, knowledgeTags?, rating?, createdAt)

@Entity "cached_achievements"
CachedAchievement(code PK, studentId, title, description, icon, unlocked,
                   currentValue, targetValue)
```

**Strategy**:
- Write: after each successful API response, background coroutine writes to Room
- Read: only when network request fails, fallback to Room
- Eviction: `cached_records` keeps last 50 per student; others overwrite fully
- Not cached: solve results (always new), learning path (depends on real-time progress)

**Network detection**:
- `ConnectivityManager.NetworkCallback` -> `isOnline: StateFlow<Boolean>`
- Offline: yellow "Offline" banner in TopAppBar, solve submit button disabled

## Settings Page

- Backend URL text field, default `http://10.0.2.2:8080` (emulator)
- "Test Connection" button -> calls `GET /api/v1/knowledge/graph` (public endpoint)
- URL persisted to DataStore
- `MathApi.baseUrl` is currently an immutable `val`. Solution: accept a `() -> String` lambda so the URL can be read from DataStore at each request time without recreating the client. The lambda is provided by the Application class reading from DataStore.

## Build Configuration

### androidApp/build.gradle.kts

```
plugins: android.application, kotlin.android, kotlin.plugin.compose,
         kotlin.plugin.serialization, com.google.devtools.ksp

android:
  namespace = "com.mathlearning.android"
  compileSdk = 35, minSdk = 29, targetSdk = 35

dependencies:
  implementation(project(":shared"))
  platform("androidx.compose:compose-bom:2025.01.01")
  material3, ui, ui-tooling-preview, navigation-compose
  lifecycle-viewmodel-compose, lifecycle-runtime-compose
  room-runtime, room-ktx, ksp(room-compiler)
  datastore-preferences
  CameraX + ML Kit (as listed above)
  kotlinx-coroutines-android
```

### shared/build.gradle.kts changes

- Add plugin: `id("com.android.library")` to the shared module's plugins block
- Add `androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`
- Add `android { namespace = "com.mathlearning.shared"; compileSdk = 35; defaultConfig.minSdk = 29 }`
- Add `androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.0.2") }`

### settings.gradle.kts

Add `include(":androidApp")`

### Root build.gradle.kts

Add `id("com.android.application") apply false` and `id("com.android.library") apply false`

## Testing

| Layer | Method | Coverage |
|-------|--------|----------|
| ViewModel | JUnit + Turbine + MockK | State transitions, error handling, cache fallback |
| Room DAO | AndroidJUnit4 + in-memory DB | Insert/query/eviction |
| Shared API | Existing MockEngine tests | Serialization (unchanged) |
| UI | Manual verification | No automated UI tests in v1 |

## Error Handling

ViewModels expose UI state as a sealed interface:
```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
```

- Network errors -> `UiState.Error` with user-friendly message, Snackbar display
- 401 -> global handler clears token, navigates to AuthScreen
- Retry: each error screen shows a "Retry" button that re-triggers the ViewModel action
- No raw exception messages shown to users

## API Endpoint Reference

Each tab's ViewModel uses these existing shared `MathApi` methods:

| Tab | API Methods |
|-----|------------|
| Auth | `login()`, `register()` |
| Solve | `solve()`, `rateRecord()` |
| Knowledge | `getKnowledgeGraph()`, `getKnowledgeProgress()`, `updateMastery()` |
| Growth | `getStudentAchievements()`, `getLearningPath()` |
| Mistakes | `getMistakes()`, `exportRecord()` |
| History | `getRecords()`, `rateRecord()` |
| Settings | `getKnowledgeGraph()` (connection test) |

## Room TypeConverters

`barModelJson` and `knowledgeTags` are stored as plain `String` columns in Room (not structured objects). A `@TypeConverter` converts `List<String>` (knowledgeTags) to/from a JSON string for Room storage.

## Out of Scope

- iOS app (future phase)
- Compose UI sharing between Web and Android (future refactor)
- Espresso/Compose UI automated tests
- CI/CD (Phase 12)
- Push notifications
- Dark mode (follow system default for now)
- ProGuard/R8 rules (deferred to Phase 12 CI/CD, needed for ML Kit + Ktor in release builds)
