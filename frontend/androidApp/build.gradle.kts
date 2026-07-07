plugins {
    id("com.android.application")
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
}

dependencies {
    // Shared KMP module
    implementation(project(":shared"))

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Koin
    implementation("io.insert-koin:koin-android:4.1.0")
    implementation("io.insert-koin:koin-androidx-compose:4.1.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")

    // ML Kit OCR
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
