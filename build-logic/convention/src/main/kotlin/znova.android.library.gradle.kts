import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

plugins {
    id("com.android.library")
    id("dev.detekt")
}

extensions.configure<LibraryExtension> {
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}
