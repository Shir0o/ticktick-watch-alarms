pluginManagement {
    repositories {
        // Skipped for the plain-Kotlin build so it works where Google's Maven is unreachable.
        if (providers.gradleProperty("sharedOnly").orNull != "true") google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.10.1"
        id("com.android.library") version "8.10.1"
        id("org.jetbrains.kotlin.android") version "2.1.21"
        id("org.jetbrains.kotlin.jvm") version "2.1.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("sharedOnly").orNull != "true") google()
        mavenCentral()
    }
}

rootProject.name = "ticktick-watch-alarms"

include(":shared")
// `-PsharedOnly=true` builds and tests the plain-Kotlin core without the Android SDK.
if (providers.gradleProperty("sharedOnly").orNull != "true") {
    include(":ring", ":phone", ":wear")
}
