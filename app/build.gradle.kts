import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------
// Toon materials: compile .mat sources with Filament's matc at build time.
// matc is downloaded once from the official Filament GitHub release and cached
// in GRADLE_USER_HOME/filament-tools (so CI caches it via setup-gradle).
// ---------------------------------------------------------------------------
val filamentToolsVersion = "1.72.1" // must match SceneView's Filament dependency
// The tgz extracts a top-level "filament/" folder into [filamentToolsDir],
// so matc ends up at filament-tools/filament/bin/matc.
val filamentToolsDir = File(gradle.gradleUserHomeDir, "filament-tools")
val matcBinary = File(filamentToolsDir, "filament/bin/matc")
val materialsSrcDir = layout.projectDirectory.dir("src/main/materials")
val materialsOutDir = "build/generated/toonAssets"

tasks.register("downloadFilamentMatc") {
    description = "Downloads matc from the Filament release (cached in Gradle user home)."
    outputs.file(matcBinary)
    doLast {
        if (!matcBinary.isFile) {
            val tgz = File(temporaryDir, "filament-tools.tgz")
            logger.lifecycle("Downloading Filament $filamentToolsVersion tools (matc)…")
            URI(
                "https://github.com/google/filament/releases/download/" +
                    "v$filamentToolsVersion/filament-v$filamentToolsVersion-linux.tgz"
            ).toURL().openStream().use { input ->
                tgz.outputStream().use { input.copyTo(it) }
            }
            filamentToolsDir.mkdirs()
            exec {
                commandLine("tar", "-xzf", tgz.absolutePath, "-C", filamentToolsDir.absolutePath)
            }
            matcBinary.setExecutable(true)
            check(matcBinary.isFile && matcBinary.canExecute()) {
                "matc not found at ${matcBinary.absolutePath} after extraction"
            }
            logger.lifecycle("matc installed at ${matcBinary.absolutePath}")
        }
    }
}

tasks.register("compileToonMaterials") {
    description = "Compiles app/src/main/materials/*.mat into packaged .filamat assets."
    dependsOn("downloadFilamentMatc")
    inputs.dir(materialsSrcDir)
    outputs.dir(layout.projectDirectory.dir(materialsOutDir))
    doLast {
        // Outputs land in build/generated/toonAssets/materials/ — matching the
        // asset path "materials/<name>.filamat" used by ToonMaterials.
        val outDir = File(projectDir, materialsOutDir).resolve("materials").apply { mkdirs() }
        val matFiles = materialsSrcDir.asFile.listFiles { f -> f.extension == "mat" }.orEmpty()
        if (matFiles.isEmpty()) throw GradleException("No .mat sources found in ${materialsSrcDir}")
        matFiles.forEach { mat ->
            val out = File(outDir, mat.nameWithoutExtension + ".filamat")
            logger.lifecycle("matc: ${mat.name} -> ${out.relativeTo(projectDir)}")
            exec {
                commandLine(
                    matcBinary.absolutePath,
                    "--platform", "mobile",
                    "--api", "all",
                    "-o", out.absolutePath,
                    mat.absolutePath,
                )
            }
        }
    }
}

android {
    namespace = "com.idleshaft.tycoon"
    compileSdk = 36

    sourceSets {
        getByName("main") {
            assets.srcDir(materialsOutDir)
        }
    }

    defaultConfig {
        applicationId = "com.idleshaft.tycoon"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Compile the toon .mat sources before any build variant starts (skipped when
// up-to-date via the task's declared inputs/outputs).
tasks.named("preBuild") {
    dependsOn("compileToonMaterials")
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

    // Persistence: Preferences DataStore + kotlinx-serialization JSON game saves.
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // 3D rendering: SceneView (Filament engine, Compose-first API).
    implementation(libs.sceneview)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Unit tests (pure domain/economy logic).
    testImplementation(libs.junit)
}
