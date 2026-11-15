import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional shared signing key (see README). Without it, CI signs both apps with the
// runner's debug key, which still matches between the phone and watch APKs of one build.
val keystorePath: String? = System.getenv("TICKALARM_KEYSTORE")

android {
    namespace = "com.shir0o.tickalarm.wear"
    compileSdk = 35

    defaultConfig {
        // The phone and watch apps must share this id (and signing key) to talk to each other.
        applicationId = "com.shir0o.tickalarm"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("shared") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("TICKALARM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TICKALARM_KEY_ALIAS")
                keyPassword = System.getenv("TICKALARM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":ring"))
}
