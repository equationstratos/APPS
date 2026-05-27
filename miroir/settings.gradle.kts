pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.0.2"
        id("com.android.library") version "8.0.2"
        id("org.jetbrains.kotlin.android") version "1.8.22"
        id("org.jetbrains.kotlin.plugin.compose") version "1.8.22"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Miroir"
include(":app")
