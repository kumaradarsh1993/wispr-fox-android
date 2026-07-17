import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Dev convenience: read API keys from a gitignored keys.properties at the repo
// root so iterative debug installs auto-seed them (no re-pasting). Empty when
// the file is absent.
val devKeys = Properties().apply {
    val f = rootProject.file("keys.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun devKey(name: String): String = (devKeys.getProperty(name) ?: "").trim().replace("\"", "")

android {
    namespace = "com.wisprfox.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wisprfox.android"
        minSdk = 31
        targetSdk = 35
        versionCode = 16
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }

        // Passphrase-encrypted owner keys for friends & family builds. Safe to
        // ship in release (useless without the passphrase). Empty when absent.
        buildConfigField("String", "FAMILY_BLOB", "\"${devKey("familyBlob")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // No baked keys in release builds.
            buildConfigField("String", "DEV_GROQ_KEY", "\"\"")
            buildConfigField("String", "DEV_OPENAI_KEY", "\"\"")
            buildConfigField("String", "DEV_DEEPGRAM_KEY", "\"\"")
            buildConfigField("String", "DEV_ELEVENLABS_KEY", "\"\"")
            buildConfigField("String", "DEV_GEMINI_KEY", "\"\"")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "DEV_GROQ_KEY", "\"${devKey("groq")}\"")
            buildConfigField("String", "DEV_OPENAI_KEY", "\"${devKey("openai")}\"")
            buildConfigField("String", "DEV_DEEPGRAM_KEY", "\"${devKey("deepgram")}\"")
            buildConfigField("String", "DEV_ELEVENLABS_KEY", "\"${devKey("elevenlabs")}\"")
            buildConfigField("String", "DEV_GEMINI_KEY", "\"${devKey("gemini")}\"")
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

    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    sourceSets["test"].kotlin.srcDirs("src/test/kotlin")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.browser)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
