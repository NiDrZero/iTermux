plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
    namespace = "com.darkian.itermux.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nidrzero.atomux"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Atomux targets 64-bit ARM (Snapdragon and other arm64 SoCs) only.
        // proroot ships arm64-v8a native launchers exclusively, and the
        // bootstrap payload is a single arm64-v8a variant, so no other ABI is
        // packaged. This deliberately drops armeabi-v7a and x86_64.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("org.tukaani:xz:1.10")
}
