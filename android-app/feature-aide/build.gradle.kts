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
