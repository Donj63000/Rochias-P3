pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "peroxyde-android"

include(":app")
include(":feature-test")
include(":feature-historique")
include(":feature-aide")
include(":core-camera")
include(":core-analysis")
include(":core-sync")
include(":core-db")
