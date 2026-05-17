plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.masakasakasama.home"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.masakasakasama.home"
        minSdk = 26
        targetSdk = 34

        // Auto-numbered by CI: the release workflow passes VERSION_CODE
        // (git commit count) so every push to main gets a higher number
        // with no manual editing. Falls back to 1 for local builds.
        val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionCode = ciVersionCode
        versionName = System.getenv("VERSION_NAME") ?: "1.0.$ciVersionCode"
    }

    signingConfigs {
        create("release") {
            // Keystore is committed on purpose: this is a personal
            // side-loaded launcher, so a stable signature (same across CI
            // builds) is what lets the app update itself in place.
            storeFile = file("../home-release.jks")
            storePassword = "homestore"
            keyAlias = "home"
            keyPassword = "homestore"
            // Enable every signature scheme so even picky side-load
            // installers (some Xiaomi/MIUI ones) accept the APK.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/*.version",
                "/META-INF/**.kotlin_module",
                "/META-INF/androidx.*",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
