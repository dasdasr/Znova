plugins {
    id("znova.android.library")
}

android {
    namespace = "com.znova.data"
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
