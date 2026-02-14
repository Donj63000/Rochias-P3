plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rochias.peroxyde"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rochias.peroxyde"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":feature-test"))
    implementation(project(":feature-historique"))
    implementation(project(":feature-aide"))
    implementation(project(":core-camera"))
    implementation(project(":core-analysis"))
    implementation(project(":core-sync"))
    implementation(project(":core-db"))

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.ui:ui:1.6.7")
    implementation("androidx.navigation:navigation-compose:2.7.7")
}
