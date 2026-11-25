plugins {
    id("znova.jvm.library")
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true

    source.setFrom(
        files("src/main/kotlin"),
    )
}

dependencies {
    testImplementation(kotlin("test"))
}
