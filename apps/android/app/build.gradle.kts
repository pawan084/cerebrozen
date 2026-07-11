import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

// Resolve a config value from (in order) a -Pgradle property, local.properties,
// or an environment variable. Falls back to "" so every integration stays inert
// until a key is supplied (the "degrades without keys" rule).
fun secret(name: String): String =
    (project.findProperty(name) as? String)
        ?: localProperties.getProperty(name)
        ?: System.getenv(name)
        ?: ""

// Escape a value for embedding as a buildConfigField String literal.
fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Convenience: pull the web (client_type 3) OAuth client id out of a
// google-services.json if one is present (git-ignored, never committed).
fun googleServicesWebClientId(): String {
    val file = project.file("google-services.json")
    if (!file.isFile) return ""
    return Regex(
        """\{\s*"client_id"\s*:\s*"([^"]+)"\s*,\s*"client_type"\s*:\s*3\s*}""",
    ).find(file.readText())?.groupValues?.get(1).orEmpty()
}

// Google web (server) OAuth client id — mirrors iOS's GIDClientID. Blank by
// default so "Continue with Google" degrades gracefully until configured.
val googleWebClientId = secret("googleWebClientId")
    .ifBlank { secret("GOOGLE_WEB_CLIENT_ID") }
    .ifBlank { googleServicesWebClientId() }

android {
    namespace = "com.cerebrozen.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cerebrozen.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Same backend as iOS/web. Debug talks to the dev machine via the
        // emulator's host loopback (cleartext allowed only in the debug
        // manifest overlay); release is pinned to production HTTPS.
        buildConfigField("String", "API_BASE_URL", "\"https://api.cerebrozen.in\"")
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            quoted(googleWebClientId),
        )
    }

    buildTypes {
        debug {
            // Emulator default: host loopback. Real-device runs override it via
            // -PapiBaseUrl=…, an apiBaseUrl in local.properties, or an env var —
            //   ./gradlew assembleDebug -PapiBaseUrl=http://localhost:8000
            // paired with `adb reverse tcp:8000 tcp:8000` (device localhost →
            // this machine's dev backend). See ANDROID_QA.md.
            val apiBaseUrl = secret("apiBaseUrl").ifBlank { "http://10.0.2.2:8000" }
            buildConfigField("String", "API_BASE_URL", quoted(apiBaseUrl))
        }
        release {
            // R8 + resource shrinking. App code is reflection-free (org.json
            // parsing, Intent-only class refs) and every AAR ships consumer
            // keep rules — emulator-smoked on a debug-signed release build.
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Robolectric needs merged Android resources to render Compose off-device.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    // Google sign-in via Credential Manager (inert until a web client id is set).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // Async image loading (matches iOS AsyncImage — real content photos with a
    // gradient fallback when the url is empty/unreachable).
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Journal lock (screen-lock/biometric gate — mirrors iOS Face ID lock).
    implementation("androidx.biometric:biometric:1.1.0")
    // Layered soundscape mixer — gapless looping per ambient layer (parity with
    // the iOS AVAudioEngine SoundscapePlayer). ExoPlayer loops each track seamlessly.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // Real backdrop blur for the frosted-glass cards over the aurora (API 31+).
    implementation("dev.chrisbanes.haze:haze:1.2.1")
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // Real org.json so JSONObject works in JVM unit tests (Android's stub throws).
    testImplementation("org.json:json:20240303")
    // Off-device Compose tests (Robolectric): renders composables in the JVM unit
    // job so the Reduce-Motion branch is guarded without an emulator.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
