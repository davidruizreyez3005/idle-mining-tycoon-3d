plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.idlemining.tycoon3d"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.idlemining.tycoon3d"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // Vertical slice: no minification yet (R8 is a later-phase optimization).
            // Debug-signed so the APK is directly installable from the GitHub release.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // The JVM unit tests exercise pure game logic (content/economy/save/simulation)
    // and never touch android.* classes.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM keeps all Compose artifact versions in sync.
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Game content + save format.
    implementation(libs.kotlinx.serialization.json)

    // 3D rendering: SceneView (Filament engine, Compose-first API).
    implementation(libs.sceneview)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Unit tests (pure domain/economy/simulation logic).
    testImplementation(libs.junit)
}
