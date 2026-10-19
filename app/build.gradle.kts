plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.masakasakasama.home"
    compileSdk = 34

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (project.hasProperty("securityMigrationQa")) applicationIdSuffix = ".securityqa"
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

    // Private signing material is injected at build time; never stored in Git.
    val signingPath = System.getenv("ANDROID_SIGNING_KEYSTORE_PATH")
    val signingStorePassword = System.getenv("ANDROID_SIGNING_STORE_PASSWORD")
    val signingKeyAlias = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
    val signingKeyPassword = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")
    val hasSigning = listOf(signingPath, signingStorePassword, signingKeyAlias, signingKeyPassword)
        .all { !it.isNullOrBlank() }
    signingConfigs {
        create("release") {
            if (hasSigning) {
                storeFile = file(signingPath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Standard Android debug signing only.
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

    testOptions { unitTests.isIncludeAndroidResources = true }

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
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
