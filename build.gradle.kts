plugins {
    base
    alias(libs.plugins.spotless)
    alias(libs.plugins.android.library) apply false

    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint("1.7.1")
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint("1.7.1")
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
    dependsOn(":app:lintDebug")
}
