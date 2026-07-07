plugins {
    kotlin("multiplatform") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
    id("org.jetbrains.compose") version "1.10.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.8" apply false
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.3" apply false
    id("com.diffplug.spotless") version "8.8.0"
}

allprojects {
    apply(plugin = "com.diffplug.spotless")

    spotless {
        kotlin {
            target("**/*.kt")
            ktlint("1.6.0").editorConfigOverride(mapOf(
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "ktlint_standard_function-naming" to "disabled"
            ))
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
