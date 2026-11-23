plugins {
    base
    alias(libs.plugins.spotless)
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
