plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rochias.peroxyde"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-camera"))
    implementation(project(":core-analysis"))
    implementation(project(":core-db"))
    implementation(project(":core-sync"))

    testImplementation("junit:junit:4.13.2")
}
