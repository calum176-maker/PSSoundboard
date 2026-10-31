import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// In-app feedback endpoint. Set in local.properties (never committed) or the environment:
//   feedback.url=https://…/webhook/pssoundboard-feedback   (PSSB_FEEDBACK_URL)
//   feedback.key=…                                          (PSSB_FEEDBACK_KEY)
// Builds without them still work: the feedback sheet falls back to sharing the report.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun feedbackSetting(key: String, env: String): String =
    (localProps.getProperty(key) ?: System.getenv(env) ?: "").replace("\"", "")

// Shared test signing key, so every test APK (CI or local) installs over the last one.
// Set PSSB_KEYSTORE_FILE and PSSB_KEYSTORE_PASSWORD (CI does, from repo secrets), or signing.file in
// local.properties with the password still from the environment. Without them, the machine's debug key is used.
val sharedKeyFile = (localProps.getProperty("signing.file") ?: System.getenv("PSSB_KEYSTORE_FILE"))
    ?.let { file(it) }?.takeIf { it.isFile }
val sharedKeyPassword: String? = System.getenv("PSSB_KEYSTORE_PASSWORD")

android {
    namespace = "com.jms.pssoundboard"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jms.pssoundboard"
        // API 26+ required for TYPE_APPLICATION_OVERLAY and AudioFocusRequest.
        minSdk = 26
        targetSdk = 37
        // CI sets these from the run number so each test build counts up.
        versionCode = System.getenv("PSSB_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("PSSB_VERSION_NAME") ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FEEDBACK_URL", "\"${feedbackSetting("feedback.url", "PSSB_FEEDBACK_URL")}\"")
        buildConfigField("String", "FEEDBACK_KEY", "\"${feedbackSetting("feedback.key", "PSSB_FEEDBACK_KEY")}\"")
    }

    signingConfigs {
        if (sharedKeyFile != null && sharedKeyPassword != null) {
            create("shared") {
                storeFile = sharedKeyFile
                storePassword = sharedKeyPassword
                keyAlias = "pssb"
                keyPassword = sharedKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("shared")?.let { signingConfig = it }
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
