plugins {
    kotlin("multiplatform") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
    id("org.jetbrains.compose") version "1.10.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.1" apply false
    id("com.diffplug.spotless") version "7.0.2"
}

allprojects {
    apply(plugin = "com.diffplug.spotless")

    spotless {
        kotlin {
            target("**/*.kt")
            ktlint("1.5.0").editorConfigOverride(mapOf(
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "ktlint_standard_function-naming" to "disabled"
            ))
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
