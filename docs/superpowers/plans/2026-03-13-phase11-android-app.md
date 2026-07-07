# Phase 11: Android App Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Android app with full feature parity to the existing KMP project, reusing the shared API/model/storage layer.

**Architecture:** Single Activity + Jetpack Navigation + ViewModel per screen. Koin for DI. Room for offline read cache. CameraX + ML Kit for OCR. MathApi from shared module with configurable base URL via `() -> String` lambda.

**Tech Stack:** Kotlin 2.2.20, Compose BOM 2025.01.01, Jetpack Navigation, Koin 4.0, Room 2.6, DataStore, CameraX 1.4, ML Kit Text Recognition v2.

**Spec:** `docs/superpowers/specs/2026-03-13-phase11-android-app-design.md`

---

## File Structure

### New files in `frontend/shared/src/androidMain/`

| File | Responsibility |
|------|---------------|
| `kotlin/com/mathlearning/shared/storage/TokenStore.kt` | `actual` implementations using SharedPreferences + `initTokenStore(Context)` |

### Modified files in `frontend/`

| File | Change |
|------|--------|
| `settings.gradle.kts` | Add `include(":androidApp")` |
| `build.gradle.kts` | Add AGP + KSP plugins `apply false` |
| `gradle.properties` | Add `android.useAndroidX=true` |
| `shared/build.gradle.kts` | Add `com.android.library` plugin, `androidTarget`, android block, androidMain deps |
| `shared/src/commonMain/kotlin/.../api/MathApi.kt` | Change `baseUrl: String` to `baseUrl: () -> String` with backward-compatible default |

### New files in `frontend/androidApp/`

| File | Responsibility |
|------|---------------|
| `build.gradle.kts` | Android app build config, all dependencies |
| `src/main/AndroidManifest.xml` | App manifest with CAMERA + INTERNET permissions |
| `src/main/kotlin/.../MathLearningApp.kt` | Application class, Koin init, TokenStore init |
| `src/main/kotlin/.../MainActivity.kt` | Single Activity entry point |
| `src/main/kotlin/.../di/AppModule.kt` | Koin module definitions |
| `src/main/kotlin/.../navigation/Navigation.kt` | NavHost with all routes |
| `src/main/kotlin/.../ui/UiState.kt` | Sealed interface for screen states |
| `src/main/kotlin/.../ui/theme/Theme.kt` | Material3 theme + colors |
| `src/main/kotlin/.../ui/auth/AuthScreen.kt` | Login/register UI |
| `src/main/kotlin/.../ui/auth/AuthViewModel.kt` | Auth state management |
| `src/main/kotlin/.../ui/solve/SolveScreen.kt` | Problem input + results display |
| `src/main/kotlin/.../ui/solve/SolveViewModel.kt` | Solve logic + rating |
| `src/main/kotlin/.../ui/knowledge/KnowledgeScreen.kt` | Knowledge graph tree |
| `src/main/kotlin/.../ui/knowledge/KnowledgeViewModel.kt` | Graph + mastery state |
| `src/main/kotlin/.../ui/growth/GrowthScreen.kt` | Achievements + learning path |
| `src/main/kotlin/.../ui/growth/GrowthViewModel.kt` | Achievement state |
| `src/main/kotlin/.../ui/mistakes/MistakesScreen.kt` | Mistake list + filters |
| `src/main/kotlin/.../ui/mistakes/MistakesViewModel.kt` | Mistakes state |
| `src/main/kotlin/.../ui/history/HistoryScreen.kt` | Record list + rating |
| `src/main/kotlin/.../ui/history/HistoryViewModel.kt` | History state |
| `src/main/kotlin/.../ui/settings/SettingsScreen.kt` | Backend URL config |
| `src/main/kotlin/.../ui/settings/SettingsViewModel.kt` | URL persistence |
| `src/main/kotlin/.../ui/components/StarRating.kt` | Reusable star rating |
| `src/main/kotlin/.../ui/components/MasteryBadge.kt` | Mastery level badge |
| `src/main/kotlin/.../ui/components/StudentSelector.kt` | Student dropdown + management |
| `src/main/kotlin/.../ui/components/BarModelCard.kt` | Bar model visualization |
| `src/main/kotlin/.../ui/components/OfflineBanner.kt` | Offline status indicator |
| `src/main/kotlin/.../ocr/OcrManager.kt` | CameraX + ML Kit integration |
| `src/main/kotlin/.../ocr/CameraScreen.kt` | Camera preview composable |
| `src/main/kotlin/.../cache/AppDatabase.kt` | Room database + entities + DAOs |
| `src/main/kotlin/.../cache/Converters.kt` | Room TypeConverters |
| `src/main/kotlin/.../cache/NetworkMonitor.kt` | ConnectivityManager wrapper |
| `src/main/res/values/strings.xml` | String resources |
| `src/main/res/values/themes.xml` | Android theme (splash) |

All `kotlin/...` paths expand to `kotlin/com/mathlearning/android/`.

---

## Chunk 1: Build Setup & Shared Module Android Target

### Task 1: Configure root build files

**Files:**
- Modify: `frontend/settings.gradle.kts`
- Modify: `frontend/build.gradle.kts`
- Modify: `frontend/gradle.properties`

- [ ] **Step 1: Add androidApp to settings.gradle.kts**

Add after `include(":webApp")`:
```kotlin
include(":androidApp")
```

- [ ] **Step 2: Add AGP and KSP plugins to root build.gradle.kts**

Add inside the `plugins {}` block:
```kotlin
id("com.android.application") version "8.9.1" apply false
id("com.android.library") version "8.9.1" apply false
id("com.google.devtools.ksp") version "2.2.20-2.0.1" apply false
```

- [ ] **Step 3: Add Android properties to gradle.properties**

Append:
```properties
android.useAndroidX=true
```

- [ ] **Step 4: Commit**

```bash
git add frontend/settings.gradle.kts frontend/build.gradle.kts frontend/gradle.properties
git commit -m "chore: add androidApp module and AGP/KSP plugins to root build"
```

---

### Task 2: Add Android target to shared module

**Files:**
- Modify: `frontend/shared/build.gradle.kts`
- Create: `frontend/shared/src/androidMain/kotlin/com/mathlearning/shared/storage/TokenStore.kt`
- Create: `frontend/shared/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Update shared/build.gradle.kts**

Replace entire file content with:
```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    jvm()
    wasmJs {
        browser()
    }
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("io.ktor:ktor-client-core:3.0.2")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            implementation("io.ktor:ktor-client-mock:3.0.2")
        }

        val wasmJsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:3.0.2")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.0.2")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:3.0.2")
            }
        }
    }
}

android {
    namespace = "com.mathlearning.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kover {
    reports {
        filters {
            excludes {
                packages("com.mathlearning.shared.model")
            }
        }
        verify {
            rule {
                minBound(60)
            }
        }
    }
}
```

- [ ] **Step 2: Create androidMain TokenStore actual implementation**

Create `frontend/shared/src/androidMain/kotlin/com/mathlearning/shared/storage/TokenStore.kt`:
```kotlin
package com.mathlearning.shared.storage

import android.content.Context
import android.content.SharedPreferences

private lateinit var appContext: Context

fun initTokenStore(context: Context) {
    appContext = context.applicationContext
}

private val prefs: SharedPreferences
    get() = appContext.getSharedPreferences("math_learning_auth", Context.MODE_PRIVATE)

actual fun saveToken(token: String, expiresAt: String) {
    prefs.edit()
        .putString("token", token)
        .putString("expiresAt", expiresAt)
        .apply()
}

actual fun loadToken(): String? = prefs.getString("token", null)

actual fun loadExpiresAt(): String? = prefs.getString("expiresAt", null)

actual fun clearToken() {
    prefs.edit().clear().apply()
}
```

- [ ] **Step 3: Create androidMain AndroidManifest.xml**

Create `frontend/shared/src/androidMain/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 4: Verify shared module compiles for all targets**

Run: `cd frontend && ./gradlew :shared:compileKotlinAndroid :shared:compileKotlinWasmJs :shared:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add frontend/shared/
git commit -m "feat: add Android target to shared KMP module with TokenStore actual"
```

---

### Task 3: Update MathApi for configurable base URL

**Files:**
- Modify: `frontend/shared/src/commonMain/kotlin/com/mathlearning/shared/api/MathApi.kt`

- [ ] **Step 1: Change baseUrl parameter to a lambda**

In `MathApi.kt`, change the constructor:
```kotlin
class MathApi(
    private val baseUrl: () -> String = { "http://localhost:8080" },
    httpClient: HttpClient? = null,
) {
```

This is backward-compatible: Kotlin can pass a string literal as a lambda via `{ "..." }`.

However, the webApp creates `MathApi()` with no args (uses default), so it still works.

- [ ] **Step 2: Update all usages of `baseUrl` to `baseUrl()`**

Replace every `$baseUrl/` with `${baseUrl()}/` throughout MathApi.kt. There are 14 occurrences.

- [ ] **Step 3: Run existing shared tests to verify nothing breaks**

Run: `cd frontend && ./gradlew :shared:jvmTest`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
git add frontend/shared/src/commonMain/kotlin/com/mathlearning/shared/api/MathApi.kt
git commit -m "refactor: make MathApi.baseUrl a lambda for runtime configurability"
```

---

### Task 4: Create androidApp module skeleton

**Files:**
- Create: `frontend/androidApp/build.gradle.kts`
- Create: `frontend/androidApp/src/main/AndroidManifest.xml`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/MathLearningApp.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/MainActivity.kt`
- Create: `frontend/androidApp/src/main/res/values/strings.xml`
- Create: `frontend/androidApp/src/main/res/values/themes.xml`

- [ ] **Step 1: Create androidApp/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.mathlearning.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mathlearning.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Shared KMP module
    implementation(project(":shared"))

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Koin
    implementation("io.insert-koin:koin-android:4.0.3")
    implementation("io.insert-koin:koin-androidx-compose:4.0.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // ML Kit OCR
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```

- [ ] **Step 2: Create AndroidManifest.xml**

Create `frontend/androidApp/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:name=".MathLearningApp"
        android:label="@string/app_name"
        android:theme="@style/Theme.MathLearning"
        android:usesCleartextTraffic="true"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Note: `usesCleartextTraffic="true"` needed for HTTP connections to local backend.

- [ ] **Step 3: Create MathLearningApp.kt**

Create `frontend/androidApp/src/main/kotlin/com/mathlearning/android/MathLearningApp.kt`:
```kotlin
package com.mathlearning.android

import android.app.Application
import com.mathlearning.shared.storage.initTokenStore

class MathLearningApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initTokenStore(this)
    }
}
```

Koin init will be added in Task 7 after modules are defined.

- [ ] **Step 4: Create MainActivity.kt**

Create `frontend/androidApp/src/main/kotlin/com/mathlearning/android/MainActivity.kt`:
```kotlin
package com.mathlearning.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mathlearning.android.ui.theme.MathLearningTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MathLearningTheme {
                // Navigation will be added in Task 8
                androidx.compose.material3.Text("Math Learning App")
            }
        }
    }
}
```

- [ ] **Step 5: Create res/values/strings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">SG Math Tutor</string>
</resources>
```

- [ ] **Step 6: Create res/values/themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MathLearning" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

- [ ] **Step 7: Verify androidApp compiles and installs**

Run: `cd frontend && ./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL, APK produced at `androidApp/build/outputs/apk/debug/`

- [ ] **Step 8: Commit**

```bash
git add frontend/androidApp/
git commit -m "feat: add androidApp module skeleton with Application, Activity, and manifest"
```

---

## Chunk 2: Theme, DI, Navigation, UiState, and Core Components

### Task 5: Create Material3 theme

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/theme/Theme.kt`

- [ ] **Step 1: Create Theme.kt**

```kotlin
package com.mathlearning.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    tertiary = Color(0xFFF57C00),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    error = Color(0xFFD32F2F),
)

@Composable
fun MathLearningTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/theme/
git commit -m "feat: add Material3 theme matching web app colors"
```

---

### Task 6: Create UiState and reusable components

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/UiState.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/components/StarRating.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/components/MasteryBadge.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/components/StudentSelector.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/components/BarModelCard.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/components/OfflineBanner.kt`

- [ ] **Step 1: Create UiState.kt**

```kotlin
package com.mathlearning.android.ui

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
```

- [ ] **Step 2: Create StarRating.kt**

```kotlin
package com.mathlearning.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StarRating(
    rating: Int,
    onRatingChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        for (i in 1..5) {
            Icon(
                imageVector = if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Star $i",
                tint = if (i <= rating) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.outline,
                modifier = if (onRatingChange != null) Modifier.clickable { onRatingChange(i) }
                    else Modifier,
            )
        }
    }
}
```

- [ ] **Step 3: Create MasteryBadge.kt**

```kotlin
package com.mathlearning.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MasteryBadge(level: String, modifier: Modifier = Modifier) {
    val (bg, label) = when (level) {
        "MASTERED" -> Color(0xFF4CAF50) to "Mastered"
        "FAMILIAR" -> Color(0xFFFFC107) to "Familiar"
        else -> Color(0xFF9E9E9E) to "Unknown"
    }
    Text(
        text = label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
```

- [ ] **Step 4: Create StudentSelector.kt**

```kotlin
package com.mathlearning.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.shared.model.Student

@Composable
fun StudentSelector(
    students: List<Student>,
    selectedStudent: Student?,
    onStudentSelected: (Student) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(selectedStudent?.let { "${it.name} (P${it.grade})" } ?: "Select Student")
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            students.forEach { student ->
                DropdownMenuItem(
                    text = { Text("${student.name} (P${student.grade})") },
                    onClick = {
                        onStudentSelected(student)
                        expanded = false
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 5: Create BarModelCard.kt**

```kotlin
package com.mathlearning.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BarModelCard(barModelJson: String?, modifier: Modifier = Modifier) {
    if (barModelJson.isNullOrBlank() || barModelJson == "{}") return
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Bar Model",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(12.dp),
        )
        Text(
            text = barModelJson,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}
```

- [ ] **Step 6: Create OfflineBanner.kt**

```kotlin
package com.mathlearning.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3E0))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.WifiOff, contentDescription = null, tint = Color(0xFFF57C00))
        Text(
            text = "Offline — showing cached data",
            fontSize = 12.sp,
            color = Color(0xFFF57C00),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/
git commit -m "feat: add UiState, theme, and reusable components (StarRating, MasteryBadge, StudentSelector, BarModelCard, OfflineBanner)"
```

---

### Task 7: Create Koin DI modules and network monitor

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/di/AppModule.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/cache/NetworkMonitor.kt`
- Modify: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/MathLearningApp.kt`

- [ ] **Step 1: Create NetworkMonitor.kt**

```kotlin
package com.mathlearning.android.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _isOnline.value = true }
            override fun onLost(network: Network) { _isOnline.value = false }
        })
    }

    private fun checkCurrentConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
```

- [ ] **Step 2: Create AppModule.kt**

```kotlin
package com.mathlearning.android.di

import androidx.datastore.preferences.core.stringPreferencesKey
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.auth.AuthViewModel
import com.mathlearning.android.ui.growth.GrowthViewModel
import com.mathlearning.android.ui.history.HistoryViewModel
import com.mathlearning.android.ui.knowledge.KnowledgeViewModel
import com.mathlearning.android.ui.mistakes.MistakesViewModel
import com.mathlearning.android.ui.settings.SettingsViewModel
import com.mathlearning.android.ui.solve.SolveViewModel
import com.mathlearning.shared.api.MathApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val BASE_URL_KEY = stringPreferencesKey("backend_url")
const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"

val appModule = module {
    single { NetworkMonitor(androidContext()) }
    single {
        MathApi(
            baseUrl = {
                // Will be connected to DataStore in SettingsViewModel
                // For now, use default
                DEFAULT_BASE_URL
            },
        )
    }
    viewModel { AuthViewModel(get()) }
    viewModel { SolveViewModel(get()) }
    viewModel { KnowledgeViewModel(get(), get()) }
    viewModel { GrowthViewModel(get(), get()) }
    viewModel { HistoryViewModel(get(), get()) }
    viewModel { MistakesViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
}
```

Note: ViewModels will be defined in later tasks. The DI module references them here so they're all wired in one place. This file will be updated as ViewModels are created.

- [ ] **Step 3: Update MathLearningApp.kt with Koin init**

```kotlin
package com.mathlearning.android

import android.app.Application
import com.mathlearning.android.di.appModule
import com.mathlearning.shared.storage.initTokenStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MathLearningApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initTokenStore(this)
        startKoin {
            androidContext(this@MathLearningApp)
            modules(appModule)
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/di/ \
      frontend/androidApp/src/main/kotlin/com/mathlearning/android/cache/NetworkMonitor.kt \
      frontend/androidApp/src/main/kotlin/com/mathlearning/android/MathLearningApp.kt
git commit -m "feat: add Koin DI modules, NetworkMonitor, and app initialization"
```

---

### Task 8: Create navigation and main scaffold

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/navigation/Navigation.kt`
- Modify: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/MainActivity.kt`

- [ ] **Step 1: Create Navigation.kt**

```kotlin
package com.mathlearning.android.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.components.OfflineBanner
import com.mathlearning.android.ui.components.StudentSelector
import com.mathlearning.android.ui.growth.GrowthScreen
import com.mathlearning.android.ui.history.HistoryScreen
import com.mathlearning.android.ui.knowledge.KnowledgeScreen
import com.mathlearning.android.ui.mistakes.MistakesScreen
import com.mathlearning.android.ui.settings.SettingsScreen
import com.mathlearning.android.ui.solve.SolveScreen
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.Student
import org.koin.compose.koinInject

enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    Solve("solve", "Solve", Icons.Default.Calculate),
    Knowledge("knowledge", "Knowledge", Icons.Default.AccountTree),
    Growth("growth", "Growth", Icons.Default.EmojiEvents),
    Mistakes("mistakes", "Mistakes", Icons.Default.ErrorOutline),
    History("history", "History", Icons.Default.History),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    students: List<Student>,
    selectedStudent: Student?,
    onStudentSelected: (Student) -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val navController = rememberNavController()
    val networkMonitor: NetworkMonitor = koinInject()
    val isOnline by networkMonitor.isOnline.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        StudentSelector(
                            students = students,
                            selectedStudent = selectedStudent,
                            onStudentSelected = onStudentSelected,
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout")
                        }
                    },
                )
                if (!isOnline) {
                    OfflineBanner()
                }
            }
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Solve.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Solve.route) {
                SolveScreen(studentId = selectedStudent?.id, studentGrade = selectedStudent?.grade)
            }
            composable(Screen.Knowledge.route) {
                KnowledgeScreen(studentId = selectedStudent?.id)
            }
            composable(Screen.Growth.route) {
                GrowthScreen(studentId = selectedStudent?.id)
            }
            composable(Screen.Mistakes.route) {
                MistakesScreen(studentId = selectedStudent?.id)
            }
            composable(Screen.History.route) {
                HistoryScreen(studentId = selectedStudent?.id)
            }
        }
    }
}
```

- [ ] **Step 2: Update MainActivity.kt**

```kotlin
package com.mathlearning.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.mathlearning.android.navigation.MainNavigation
import com.mathlearning.android.ui.auth.AuthScreen
import com.mathlearning.android.ui.theme.MathLearningTheme
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.Student
import com.mathlearning.shared.storage.clearToken
import com.mathlearning.shared.storage.loadExpiresAt
import com.mathlearning.shared.storage.loadToken
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val api: MathApi by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MathLearningTheme {
                var isLoggedIn by remember { mutableStateOf(checkTokenValid()) }
                var students by remember { mutableStateOf<List<Student>>(emptyList()) }
                var selectedStudent by remember { mutableStateOf<Student?>(null) }

                if (isLoggedIn) {
                    LaunchedEffect(Unit) {
                        try {
                            api.token = loadToken()
                            students = api.listStudents()
                            if (selectedStudent == null && students.isNotEmpty()) {
                                selectedStudent = students.first()
                            }
                        } catch (e: Exception) {
                            isLoggedIn = false
                        }
                    }
                    MainNavigation(
                        students = students,
                        selectedStudent = selectedStudent,
                        onStudentSelected = { selectedStudent = it },
                        onLogout = {
                            clearToken()
                            api.token = null
                            isLoggedIn = false
                        },
                        onOpenSettings = {},
                    )
                } else {
                    AuthScreen(
                        onLoginSuccess = {
                            isLoggedIn = true
                        },
                    )
                }
            }
        }
    }

    private fun checkTokenValid(): Boolean {
        val token = loadToken() ?: return false
        val expiresAt = loadExpiresAt() ?: return false
        return try {
            // Simple check: expiresAt is an ISO string, compare with current time
            val expiryMillis = java.time.Instant.parse(expiresAt).toEpochMilli()
            System.currentTimeMillis() < expiryMillis
        } catch (e: Exception) {
            false
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/navigation/ \
      frontend/androidApp/src/main/kotlin/com/mathlearning/android/MainActivity.kt
git commit -m "feat: add navigation scaffold with bottom bar, student selector, and auth gate"
```

---

## Chunk 3: Auth, Settings, and Solve Screens

### Task 9: Create AuthScreen + AuthViewModel

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/auth/AuthViewModel.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/auth/AuthScreen.kt`

- [ ] **Step 1: Create AuthViewModel.kt**

```kotlin
package com.mathlearning.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.shared.api.MathApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val api: MathApi) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                api.login(email, password)
                onSuccess()
            } catch (e: Exception) {
                _error.value = "Login failed. Please check your credentials."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val success = api.register(email, password)
                if (success) {
                    api.login(email, password)
                    onSuccess()
                } else {
                    _error.value = "Registration failed. Email may already be in use."
                }
            } catch (e: Exception) {
                _error.value = "Registration failed. Please try again."
            } finally {
                _isLoading.value = false
            }
        }
    }
}
```

- [ ] **Step 2: Create AuthScreen.kt**

```kotlin
package com.mathlearning.android.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(onLoginSuccess: () -> Unit) {
    val viewModel: AuthViewModel = koinViewModel()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SG Math Tutor",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (isRegisterMode) viewModel.register(email, password, onLoginSuccess)
                    else viewModel.login(email, password, onLoginSuccess)
                },
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (isRegisterMode) viewModel.register(email, password, onLoginSuccess)
                else viewModel.login(email, password, onLoginSuccess)
            },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (isRegisterMode) "Register" else "Login")
            }
        }

        TextButton(onClick = { isRegisterMode = !isRegisterMode }) {
            Text(if (isRegisterMode) "Already have an account? Login" else "No account? Register")
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/auth/
git commit -m "feat: add AuthScreen with login/register and AuthViewModel"
```

---

### Task 10: Create SettingsScreen + SettingsViewModel

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/settings/SettingsViewModel.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create SettingsViewModel.kt**

```kotlin
package com.mathlearning.android.ui.settings

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.di.BASE_URL_KEY
import com.mathlearning.android.di.DEFAULT_BASE_URL
import com.mathlearning.shared.api.MathApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val android.content.Context.dataStore by preferencesDataStore(name = "settings")

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.dataStore

    private val _url = MutableStateFlow(DEFAULT_BASE_URL)
    val url: StateFlow<String> = _url

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    init {
        viewModelScope.launch {
            _url.value = dataStore.data.map { prefs ->
                prefs[BASE_URL_KEY] ?: DEFAULT_BASE_URL
            }.first()
        }
    }

    fun updateUrl(newUrl: String) {
        _url.value = newUrl
    }

    fun saveUrl() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[BASE_URL_KEY] = _url.value
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _testResult.value = "Testing..."
            try {
                val testApi = MathApi(baseUrl = { _url.value })
                testApi.getKnowledgeGraph()
                _testResult.value = "Connected successfully!"
            } catch (e: Exception) {
                _testResult.value = "Connection failed: ${e.message?.take(80)}"
            }
        }
    }
}
```

- [ ] **Step 2: Create SettingsScreen.kt**

```kotlin
package com.mathlearning.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val url by viewModel.url.collectAsState()
    val testResult by viewModel.testResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Backend URL", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { viewModel.updateUrl(it) },
                label = { Text("URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveUrl() }) { Text("Save") }
                OutlinedButton(onClick = { viewModel.testConnection() }) { Text("Test Connection") }
            }
            testResult?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = if (it.startsWith("Connected")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/settings/
git commit -m "feat: add SettingsScreen with configurable backend URL and connection test"
```

---

### Task 11: Create SolveScreen + SolveViewModel

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/solve/SolveViewModel.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/solve/SolveScreen.kt`

- [ ] **Step 1: Create SolveViewModel.kt**

```kotlin
package com.mathlearning.android.ui.solve

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.ui.UiState
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.ExplanationMode
import com.mathlearning.shared.model.SolveRequest
import com.mathlearning.shared.model.SolveResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SolveViewModel(private val api: MathApi) : ViewModel() {
    private val _solveState = MutableStateFlow<UiState<SolveResponse>?>(null)
    val solveState: StateFlow<UiState<SolveResponse>?> = _solveState

    private val _lastRecordId = MutableStateFlow<String?>(null)
    val lastRecordId: StateFlow<String?> = _lastRecordId

    fun solve(question: String, grade: Int, studentId: String?, mode: ExplanationMode) {
        viewModelScope.launch {
            _solveState.value = UiState.Loading
            try {
                val response = api.solve(SolveRequest(question, grade, studentId, mode))
                _solveState.value = UiState.Success(response)
            } catch (e: Exception) {
                _solveState.value = UiState.Error("Failed to solve. Please try again.")
            }
        }
    }

    fun rateRecord(recordId: String, rating: Int) {
        viewModelScope.launch {
            try {
                api.rateRecord(recordId, rating)
            } catch (_: Exception) {
                // Silent fail for rating
            }
        }
    }

    fun reset() {
        _solveState.value = null
    }
}
```

- [ ] **Step 2: Create SolveScreen.kt**

```kotlin
package com.mathlearning.android.ui.solve

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.android.ui.UiState
import com.mathlearning.android.ui.components.BarModelCard
import com.mathlearning.android.ui.components.StarRating
import com.mathlearning.shared.model.ExplanationMode
import com.mathlearning.shared.model.SolveResponse
import org.koin.androidx.compose.koinViewModel

@Composable
fun SolveScreen(studentId: String?, studentGrade: Int?) {
    val viewModel: SolveViewModel = koinViewModel()
    val solveState by viewModel.solveState.collectAsState()

    var question by remember { mutableStateOf("") }
    var grade by remember { mutableIntStateOf(studentGrade ?: 1) }
    var mode by remember { mutableStateOf(ExplanationMode.ORIGINAL) }

    // Update grade when student changes
    LaunchedEffect(studentGrade) {
        studentGrade?.let { grade = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Input section
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Enter math question") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            trailingIcon = {
                IconButton(onClick = { /* OCR - Task 14 */ }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "OCR")
                }
            },
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Grade selector
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..6).forEach { g ->
                FilterChip(
                    selected = grade == g,
                    onClick = { grade = g },
                    label = { Text("P$g") },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Mode selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExplanationMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { mode = m },
                    label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Solve button
        Button(
            onClick = { viewModel.solve(question, grade, studentId, mode) },
            enabled = question.isNotBlank() && solveState !is UiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Solve")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Results
        when (val state = solveState) {
            is UiState.Loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text("Solving... this may take a moment")
            }
            is UiState.Success -> SolveResultCards(state.data)
            is UiState.Error -> {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.solve(question, grade, studentId, mode) }) {
                    Text("Retry")
                }
            }
            null -> {} // Initial state
        }
    }
}

@Composable
private fun SolveResultCards(result: SolveResponse) {
    var rating by remember { mutableIntStateOf(0) }

    result.parentGuide?.let { guide ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Parent Guide", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(guide)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    result.childScript?.let { script ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Child Script", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(script)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    BarModelCard(result.barModelJson)
    Spacer(modifier = Modifier.height(8.dp))

    result.knowledgeTags?.takeIf { it.isNotEmpty() }?.let { tags ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tags.forEach { tag ->
                AssistChip(onClick = {}, label = { Text(tag) })
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Star rating
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Rate this explanation", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            StarRating(rating = rating, onRatingChange = { rating = it })
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/solve/
git commit -m "feat: add SolveScreen with question input, grade/mode selection, and result cards"
```

---

## Chunk 4: Knowledge, Growth, History, and Mistakes Screens

### Task 12: Create KnowledgeScreen + KnowledgeViewModel

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/knowledge/KnowledgeViewModel.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/knowledge/KnowledgeScreen.kt`

- [ ] **Step 1: Create KnowledgeViewModel.kt**

```kotlin
package com.mathlearning.android.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.UiState
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.KnowledgeNodeResponse
import com.mathlearning.shared.model.KnowledgeProgressResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class KnowledgeViewModel(
    private val api: MathApi,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _graphState = MutableStateFlow<UiState<List<KnowledgeNodeResponse>>>(UiState.Loading)
    val graphState: StateFlow<UiState<List<KnowledgeNodeResponse>>> = _graphState

    private val _progress = MutableStateFlow<Map<String, KnowledgeProgressResponse>>(emptyMap())
    val progress: StateFlow<Map<String, KnowledgeProgressResponse>> = _progress

    fun loadGraph() {
        viewModelScope.launch {
            _graphState.value = UiState.Loading
            try {
                val graph = api.getKnowledgeGraph()
                _graphState.value = UiState.Success(graph)
            } catch (e: Exception) {
                _graphState.value = UiState.Error("Failed to load knowledge graph")
            }
        }
    }

    fun loadProgress(studentId: String) {
        viewModelScope.launch {
            try {
                val progressList = api.getKnowledgeProgress(studentId)
                _progress.value = progressList.associateBy { it.knowledgeCode }
            } catch (_: Exception) {}
        }
    }

    fun updateMastery(studentId: String, nodeCode: String, level: String) {
        viewModelScope.launch {
            try {
                api.updateMastery(studentId, nodeCode, level)
                loadProgress(studentId)
            } catch (_: Exception) {}
        }
    }
}
```

- [ ] **Step 2: Create KnowledgeScreen.kt**

```kotlin
package com.mathlearning.android.ui.knowledge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.android.ui.UiState
import com.mathlearning.android.ui.components.MasteryBadge
import com.mathlearning.shared.model.KnowledgeNodeResponse
import com.mathlearning.shared.model.KnowledgeProgressResponse
import org.koin.androidx.compose.koinViewModel

@Composable
fun KnowledgeScreen(studentId: String?) {
    val viewModel: KnowledgeViewModel = koinViewModel()
    val graphState by viewModel.graphState.collectAsState()
    val progress by viewModel.progress.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadGraph() }
    LaunchedEffect(studentId) { studentId?.let { viewModel.loadProgress(it) } }

    when (val state = graphState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.loadGraph() }) { Text("Retry") }
            }
        }
        is UiState.Success -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(state.data) { node ->
                KnowledgeNodeItem(
                    node = node,
                    progress = progress,
                    depth = 0,
                    studentId = studentId,
                    onMasteryClick = { code, level ->
                        studentId?.let { viewModel.updateMastery(it, code, level) }
                    },
                )
            }
        }
    }
}

@Composable
private fun KnowledgeNodeItem(
    node: KnowledgeNodeResponse,
    progress: Map<String, KnowledgeProgressResponse>,
    depth: Int,
    studentId: String?,
    onMasteryClick: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(depth < 1) }
    val mastery = progress[node.code]?.masteryLevel ?: "UNKNOWN"
    val nextLevel = when (mastery) {
        "UNKNOWN" -> "FAMILIAR"
        "FAMILIAR" -> "MASTERED"
        else -> "UNKNOWN"
    }

    Column(modifier = Modifier.padding(start = (depth * 16).dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (node.children.isNotEmpty()) expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node.children.isNotEmpty()) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(node.nameEn, style = MaterialTheme.typography.bodyMedium)
                Text(node.nameZh, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (studentId != null) {
                MasteryBadge(
                    level = mastery,
                    modifier = Modifier.clickable { onMasteryClick(node.code, nextLevel) },
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                node.children.forEach { child ->
                    KnowledgeNodeItem(child, progress, depth + 1, studentId, onMasteryClick)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/knowledge/
git commit -m "feat: add KnowledgeScreen with tree view, mastery badges, and click-to-cycle"
```

---

### Task 13: Create GrowthScreen, HistoryScreen, MistakesScreen

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/growth/GrowthViewModel.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/growth/GrowthScreen.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/history/HistoryViewModel.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/history/HistoryScreen.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/mistakes/MistakesViewModel.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/mistakes/MistakesScreen.kt`

- [ ] **Step 1: Create GrowthViewModel.kt**

```kotlin
package com.mathlearning.android.ui.growth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.UiState
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.AchievementResponse
import com.mathlearning.shared.model.LearningPathResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GrowthViewModel(
    private val api: MathApi,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _achievements = MutableStateFlow<UiState<List<AchievementResponse>>>(UiState.Loading)
    val achievements: StateFlow<UiState<List<AchievementResponse>>> = _achievements

    private val _learningPath = MutableStateFlow<UiState<LearningPathResponse>?>(null)
    val learningPath: StateFlow<UiState<LearningPathResponse>?> = _learningPath

    fun load(studentId: String) {
        viewModelScope.launch {
            _achievements.value = UiState.Loading
            try {
                _achievements.value = UiState.Success(api.getStudentAchievements(studentId))
            } catch (e: Exception) {
                _achievements.value = UiState.Error("Failed to load achievements")
            }
        }
        viewModelScope.launch {
            _learningPath.value = UiState.Loading
            try {
                _learningPath.value = UiState.Success(api.getLearningPath(studentId))
            } catch (e: Exception) {
                _learningPath.value = UiState.Error("Failed to load learning path")
            }
        }
    }
}
```

- [ ] **Step 2: Create GrowthScreen.kt**

```kotlin
package com.mathlearning.android.ui.growth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.android.ui.UiState
import org.koin.androidx.compose.koinViewModel

@Composable
fun GrowthScreen(studentId: String?) {
    val viewModel: GrowthViewModel = koinViewModel()
    val achievements by viewModel.achievements.collectAsState()
    val learningPath by viewModel.learningPath.collectAsState()

    LaunchedEffect(studentId) { studentId?.let { viewModel.load(it) } }

    if (studentId == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a student to view growth")
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Learning path section
        item {
            Text("Learning Path", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
        }
        item {
            when (val lp = learningPath) {
                is UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is UiState.Success -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(lp.data.summary, style = MaterialTheme.typography.bodyLarge)
                        Text(lp.data.reason, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Focus: ${lp.data.focusNode.nameEn} (${lp.data.focusNode.nameZh})")
                    }
                }
                is UiState.Error -> Text(lp.message, color = MaterialTheme.colorScheme.error)
                null -> {}
            }
            Spacer(Modifier.height(16.dp))
        }

        // Achievements section
        item {
            Text("Achievements", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
        }
        when (val ach = achievements) {
            is UiState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            is UiState.Error -> item { Text(ach.message, color = MaterialTheme.colorScheme.error) }
            is UiState.Success -> items(ach.data) { achievement ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(achievement.icon, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(achievement.title, style = MaterialTheme.typography.titleSmall)
                            Text(achievement.description, style = MaterialTheme.typography.bodySmall)
                        }
                        if (achievement.unlocked) {
                            Text("Unlocked", color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text("${achievement.currentValue}/${achievement.targetValue}",
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Create HistoryViewModel.kt**

```kotlin
package com.mathlearning.android.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.UiState
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.PagedRecordResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val api: MathApi,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _records = MutableStateFlow<UiState<PagedRecordResponse>>(UiState.Loading)
    val records: StateFlow<UiState<PagedRecordResponse>> = _records

    private var currentPage = 0

    fun loadRecords(studentId: String, page: Int = 0) {
        currentPage = page
        viewModelScope.launch {
            _records.value = UiState.Loading
            try {
                _records.value = UiState.Success(api.getRecords(studentId, page))
            } catch (e: Exception) {
                _records.value = UiState.Error("Failed to load history")
            }
        }
    }

    fun rateRecord(recordId: String, rating: Int) {
        viewModelScope.launch {
            try { api.rateRecord(recordId, rating) } catch (_: Exception) {}
        }
    }
}
```

- [ ] **Step 4: Create HistoryScreen.kt**

```kotlin
package com.mathlearning.android.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.android.ui.UiState
import com.mathlearning.android.ui.components.StarRating
import com.mathlearning.shared.model.RecordResponse
import org.koin.androidx.compose.koinViewModel

@Composable
fun HistoryScreen(studentId: String?) {
    val viewModel: HistoryViewModel = koinViewModel()
    val records by viewModel.records.collectAsState()

    LaunchedEffect(studentId) { studentId?.let { viewModel.loadRecords(it) } }

    if (studentId == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Select a student to view history")
        }
        return
    }

    when (val state = records) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message)
                Button(onClick = { viewModel.loadRecords(studentId) }) { Text("Retry") }
            }
        }
        is UiState.Success -> {
            val data = state.data
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(data.records) { record ->
                    HistoryRecordCard(record, onRate = { rating ->
                        viewModel.rateRecord(record.id, rating)
                    })
                }
                // Pagination
                item {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                        if (data.page > 0) {
                            TextButton(onClick = { viewModel.loadRecords(studentId, data.page - 1) }) {
                                Text("Previous")
                            }
                        }
                        Text("Page ${data.page + 1} of ${data.totalPages}",
                            modifier = Modifier.padding(horizontal = 16.dp).align(Alignment.CenterVertically))
                        if (data.page + 1 < data.totalPages) {
                            TextButton(onClick = { viewModel.loadRecords(studentId, data.page + 1) }) {
                                Text("Next")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(record: RecordResponse, onRate: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var rating by remember { mutableIntStateOf(record.rating ?: 0) }

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Text(record.questionText.take(100), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(record.createdAt.take(10), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.weight(1f))
                StarRating(rating = rating, onRatingChange = { rating = it; onRate(it) })
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    record.parentGuide?.let {
                        Text("Parent Guide", style = MaterialTheme.typography.titleSmall)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                    }
                    record.childScript?.let {
                        Text("Child Script", style = MaterialTheme.typography.titleSmall)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Create MistakesViewModel.kt**

```kotlin
package com.mathlearning.android.ui.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.UiState
import com.mathlearning.shared.api.MathApi
import com.mathlearning.shared.model.MistakePageResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MistakesViewModel(
    private val api: MathApi,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val _mistakes = MutableStateFlow<UiState<MistakePageResponse>>(UiState.Loading)
    val mistakes: StateFlow<UiState<MistakePageResponse>> = _mistakes

    fun loadMistakes(studentId: String?, page: Int = 0) {
        viewModelScope.launch {
            _mistakes.value = UiState.Loading
            try {
                _mistakes.value = UiState.Success(api.getMistakes(studentId = studentId, page = page))
            } catch (e: Exception) {
                _mistakes.value = UiState.Error("Failed to load mistakes")
            }
        }
    }
}
```

- [ ] **Step 6: Create MistakesScreen.kt**

```kotlin
package com.mathlearning.android.ui.mistakes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mathlearning.android.ui.UiState
import com.mathlearning.android.ui.components.StarRating
import org.koin.androidx.compose.koinViewModel

@Composable
fun MistakesScreen(studentId: String?) {
    val viewModel: MistakesViewModel = koinViewModel()
    val mistakes by viewModel.mistakes.collectAsState()

    LaunchedEffect(studentId) { viewModel.loadMistakes(studentId) }

    when (val state = mistakes) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message)
                Button(onClick = { viewModel.loadMistakes(studentId) }) { Text("Retry") }
            }
        }
        is UiState.Success -> {
            if (state.data.records.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No mistakes found. Keep up the good work!")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(state.data.records) { mistake ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(mistake.questionText.take(120), style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(mistake.createdAt.take(10), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.weight(1f))
                                    StarRating(rating = mistake.rating ?: 0)
                                }
                                mistake.knowledgeTags?.let { tags ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 4.dp)) {
                                        tags.forEach { tag ->
                                            AssistChip(onClick = {}, label = { Text(tag) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/growth/ \
      frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/history/ \
      frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/mistakes/
git commit -m "feat: add Growth, History, and Mistakes screens with ViewModels"
```

---

## Chunk 5: OCR, Room Cache, and Final Integration

### Task 14: Create OCR integration (CameraX + ML Kit)

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ocr/OcrManager.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ocr/CameraScreen.kt`
- Modify: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/solve/SolveScreen.kt`

- [ ] **Step 1: Create OcrManager.kt**

```kotlin
package com.mathlearning.android.ocr

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface OcrState {
    data object Idle : OcrState
    data object Preview : OcrState
    data object Processing : OcrState
    data class Success(val text: String) : OcrState
    data class Error(val message: String) : OcrState
}

class OcrManager {
    private val _state = MutableStateFlow<OcrState>(OcrState.Idle)
    val state: StateFlow<OcrState> = _state

    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val executor = Executors.newSingleThreadExecutor()

    fun showPreview() { _state.value = OcrState.Preview }
    fun dismiss() { _state.value = OcrState.Idle }

    suspend fun captureAndRecognize(imageCapture: ImageCapture) {
        _state.value = OcrState.Processing
        try {
            val imageProxy = suspendCancellableCoroutine { cont ->
                imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) { cont.resume(image) }
                    override fun onError(e: ImageCaptureException) { cont.resumeWithException(e) }
                })
            }
            val inputImage = InputImage.fromMediaImage(
                imageProxy.image!!, imageProxy.imageInfo.rotationDegrees
            )
            // Run both recognizers and merge
            val latinResult = suspendCancellableCoroutine { cont ->
                latinRecognizer.process(inputImage)
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener { cont.resume("") }
            }
            val chineseResult = suspendCancellableCoroutine { cont ->
                chineseRecognizer.process(inputImage)
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener { cont.resume("") }
            }
            imageProxy.close()
            val combined = if (chineseResult.length > latinResult.length) chineseResult else latinResult
            _state.value = if (combined.isNotBlank()) OcrState.Success(combined)
                else OcrState.Error("No text detected. Try again with a clearer image.")
        } catch (e: Exception) {
            _state.value = OcrState.Error("Capture failed: ${e.message?.take(50)}")
        }
    }
}
```

- [ ] **Step 2: Create CameraScreen.kt**

```kotlin
package com.mathlearning.android.ocr

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.launch

@Composable
fun CameraScreen(ocrManager: OcrManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val ocrState by ocrManager.state.collectAsState()
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Check camera permission
    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission required for OCR")
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                            preview, imageCapture
                        )
                    }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Controls overlay
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onPrimary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                when (ocrState) {
                    is OcrState.Processing -> CircularProgressIndicator()
                    else -> FloatingActionButton(onClick = {
                        scope.launch { ocrManager.captureAndRecognize(imageCapture) }
                    }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Update SolveScreen to integrate OCR**

In `SolveScreen.kt`, add OCR state and camera screen toggle. Replace the `trailingIcon` onClick and add the camera screen:

After the existing imports, add:
```kotlin
import com.mathlearning.android.ocr.CameraScreen
import com.mathlearning.android.ocr.OcrManager
import com.mathlearning.android.ocr.OcrState
```

In the `SolveScreen` composable, add before the `Column`:
```kotlin
val ocrManager = remember { OcrManager() }
val ocrState by ocrManager.state.collectAsState()

// Handle OCR result
LaunchedEffect(ocrState) {
    if (ocrState is OcrState.Success) {
        question = (ocrState as OcrState.Success).text
        ocrManager.dismiss()
    }
}

if (ocrState is OcrState.Preview || ocrState is OcrState.Processing) {
    CameraScreen(ocrManager = ocrManager, onDismiss = { ocrManager.dismiss() })
    return
}
```

Update the camera icon `onClick`:
```kotlin
IconButton(onClick = { ocrManager.showPreview() }) {
```

- [ ] **Step 4: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/ocr/ \
      frontend/androidApp/src/main/kotlin/com/mathlearning/android/ui/solve/SolveScreen.kt
git commit -m "feat: add CameraX + ML Kit OCR integration with camera preview"
```

---

### Task 15: Create Room database and offline cache

**Files:**
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/cache/AppDatabase.kt`
- Create: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/cache/Converters.kt`

- [ ] **Step 1: Create Converters.kt**

```kotlin
package com.mathlearning.android.cache

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>?): String = json.encodeToString(value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(value)
}
```

- [ ] **Step 2: Create AppDatabase.kt**

```kotlin
package com.mathlearning.android.cache

import android.content.Context
import androidx.room.*

@Entity(tableName = "cached_knowledge_nodes")
data class CachedKnowledgeNode(
    @PrimaryKey val code: String,
    val nameEn: String,
    val nameZh: String,
    val parentCode: String?,
    val gradeStart: Int,
    val sortOrder: Int = 0,
)

@Entity(tableName = "cached_records")
data class CachedRecord(
    @PrimaryKey val id: String,
    val studentId: String,
    val questionText: String,
    val parentGuide: String?,
    val childScript: String?,
    val barModelJson: String?,
    @TypeConverters(Converters::class)
    val knowledgeTags: List<String>?,
    val rating: Int?,
    val createdAt: String,
)

@Entity(tableName = "cached_achievements", primaryKeys = ["code", "studentId"])
data class CachedAchievement(
    val code: String,
    val studentId: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlocked: Boolean,
    val currentValue: Int,
    val targetValue: Int,
)

@Dao
interface KnowledgeNodeDao {
    @Query("SELECT * FROM cached_knowledge_nodes ORDER BY sortOrder")
    suspend fun getAll(): List<CachedKnowledgeNode>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<CachedKnowledgeNode>)

    @Query("DELETE FROM cached_knowledge_nodes")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(nodes: List<CachedKnowledgeNode>) {
        deleteAll()
        insertAll(nodes)
    }
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM cached_records WHERE studentId = :studentId ORDER BY createdAt DESC LIMIT 50")
    suspend fun getByStudent(studentId: String): List<CachedRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<CachedRecord>)

    @Query("DELETE FROM cached_records WHERE studentId = :studentId")
    suspend fun deleteByStudent(studentId: String)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM cached_achievements WHERE studentId = :studentId")
    suspend fun getByStudent(studentId: String): List<CachedAchievement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(achievements: List<CachedAchievement>)
}

@Database(
    entities = [CachedKnowledgeNode::class, CachedRecord::class, CachedAchievement::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knowledgeNodeDao(): KnowledgeNodeDao
    abstract fun recordDao(): RecordDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "math_learning_cache")
                .fallbackToDestructiveMigration()
                .build()
    }
}
```

- [ ] **Step 3: Register Room in Koin AppModule**

Add to `AppModule.kt`:
```kotlin
single { AppDatabase.create(androidContext()) }
single { get<AppDatabase>().knowledgeNodeDao() }
single { get<AppDatabase>().recordDao() }
single { get<AppDatabase>().achievementDao() }
```

Add the imports:
```kotlin
import com.mathlearning.android.cache.AppDatabase
```

- [ ] **Step 4: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/cache/ \
      frontend/androidApp/src/main/kotlin/com/mathlearning/android/di/AppModule.kt
git commit -m "feat: add Room database with entities, DAOs, and TypeConverters for offline cache"
```

---

### Task 16: Final integration, DI wiring, and build verification

**Files:**
- Modify: `frontend/androidApp/src/main/kotlin/com/mathlearning/android/di/AppModule.kt` (finalize)

- [ ] **Step 1: Finalize AppModule.kt with all DI wiring**

Ensure AppModule.kt has complete imports and all ViewModels registered. The SettingsViewModel needs special handling as an AndroidViewModel:

```kotlin
package com.mathlearning.android.di

import androidx.datastore.preferences.core.stringPreferencesKey
import com.mathlearning.android.cache.AppDatabase
import com.mathlearning.android.cache.NetworkMonitor
import com.mathlearning.android.ui.auth.AuthViewModel
import com.mathlearning.android.ui.growth.GrowthViewModel
import com.mathlearning.android.ui.history.HistoryViewModel
import com.mathlearning.android.ui.knowledge.KnowledgeViewModel
import com.mathlearning.android.ui.mistakes.MistakesViewModel
import com.mathlearning.android.ui.settings.SettingsViewModel
import com.mathlearning.android.ui.solve.SolveViewModel
import com.mathlearning.shared.api.MathApi
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val BASE_URL_KEY = stringPreferencesKey("backend_url")
const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"

val appModule = module {
    // Infrastructure
    single { NetworkMonitor(androidContext()) }
    single { AppDatabase.create(androidContext()) }
    single { get<AppDatabase>().knowledgeNodeDao() }
    single { get<AppDatabase>().recordDao() }
    single { get<AppDatabase>().achievementDao() }

    // API
    single { MathApi(baseUrl = { DEFAULT_BASE_URL }) }

    // ViewModels
    viewModel { AuthViewModel(get()) }
    viewModel { SolveViewModel(get()) }
    viewModel { KnowledgeViewModel(get(), get()) }
    viewModel { GrowthViewModel(get(), get()) }
    viewModel { HistoryViewModel(get(), get()) }
    viewModel { MistakesViewModel(get(), get()) }
    viewModel { SettingsViewModel(androidApplication()) }
}
```

- [ ] **Step 2: Build the complete app**

Run: `cd frontend && ./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run shared module tests**

Run: `cd frontend && ./gradlew :shared:jvmTest`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add frontend/androidApp/src/main/kotlin/com/mathlearning/android/di/AppModule.kt
git commit -m "feat: finalize DI wiring and verify Android app builds"
```

---

### Task 17: Update documentation

**Files:**
- Modify: `docs/dev-plan.md`
- Modify: `docs/roadmap.md`

- [ ] **Step 1: Update dev-plan.md Phase 11 section with implementation notes**

Add implementation notes to the existing Phase 11 section, similar to how Phase 5 and 6 have `> **实施说明：**` blocks.

- [ ] **Step 2: Update roadmap.md**

Change Phase 11 status from "Upcoming" to "Done" in the status table, and add a summary paragraph in the "Done" section.

- [ ] **Step 3: Commit**

```bash
git add docs/dev-plan.md docs/roadmap.md
git commit -m "docs: update dev-plan and roadmap with Phase 11 Android implementation notes"
```
