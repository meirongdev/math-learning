plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    jvm()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.ktor:ktor-client-core:3.2.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation("io.ktor:ktor-client-mock:3.2.0")
        }

        val wasmJsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:3.2.0")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.2.0")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:3.2.0")
            }
        }
    }

    android {
        namespace = "com.mathlearning.shared"
        compileSdk = 35
        minSdk = 29
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
