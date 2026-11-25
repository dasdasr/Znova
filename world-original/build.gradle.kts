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
    implementation(project(":engine"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
