import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    jacoco
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
        buildConfigField("String", "ENGINE_BASE_URL", "\"https://api.cerebrozen.in/engine\"")
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
            // Platform API (auth, orgs, privacy) — services/platform on :8100.
            val apiBaseUrl = secret("apiBaseUrl").ifBlank { "http://10.0.2.2:8100" }
            buildConfigField("String", "API_BASE_URL", quoted(apiBaseUrl))
            // Coaching engine (session SSE) — services/engine on :8000.
            val engineBaseUrl = secret("engineBaseUrl").ifBlank { "http://10.0.2.2:8000" }
            buildConfigField("String", "ENGINE_BASE_URL", quoted(engineBaseUrl))
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
        unitTests.all {
            // SessionTransportTest exercises the real HttpURLConnection PATCH
            // fallback (a reflective write to java.net.HttpURLConnection.method —
            // open on Android, module-sealed on the test JVM).
            it.jvmArgs("--add-opens", "java.base/java.net=ALL-UNNAMED")
        }
    }
    lint {
        // values-hi is a deliberate PARTIAL locale: safety/clinical strings are
        // untranslated by design (English fallback pending clinical review — see
        // the values-hi/strings.xml header). MissingTranslation would demand we
        // translate exactly the strings we've decided a human must review first.
        disable += "MissingTranslation"
    }
}

// JVM unit tests run against the debug variant only: release differs solely by
// R8/shrinking (validated by the release smoke), and the Robolectric Compose
// tests need the debug-only ui-test-manifest to inflate activities.
tasks.configureEach { if (name == "testReleaseUnitTest") enabled = false }

// ── Coverage gate (mirrors the backend's pytest --cov-fail-under=95 philosophy) ──
//
// The gate measures the app's TESTABLE LOGIC SCOPE only — the layers that can run
// hermetically on the JVM (plain unit tests + Robolectric). Exactly like the
// backend's .coveragerc (which omits prestart/seed/agent/oracle live-LLM paths),
// everything excluded below has a written reason: it needs a real device,
// a system service, or a Compose renderer to execute at all.
jacoco { toolVersion = "0.8.12" }

tasks.withType<Test>().configureEach {
    configure<JacocoTaskExtension> {
        // Robolectric loads classes through its own classloader; without these
        // flags JaCoCo drops that execution data on the floor.
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

// The measured scope: the app's logic layer. Everything here runs hermetically
// in JVM/Robolectric unit tests (CI needs no emulator and no keys).
val coverageIncludes = listOf(
    "com/cerebrozen/app/net/**",                  // Session/Api/Coach/Events/Analytics — auth, cache, SSE, engine client
    "com/cerebrozen/app/data/**",                 // Helplines — crisis directory parse + the offline floor. Gated deliberately: this is safety code

    "com/cerebrozen/app/audio/MediaUrls*",        // pure URL registry/resolution
    "com/cerebrozen/app/audio/MediaCatalog*",     // pure key→url catalogue + the empty-url fallback contract
    "com/cerebrozen/app/audio/AmbientSource*",    // pure "uploaded asset else bundled loop" resolution
    "com/cerebrozen/app/audio/SfxTones*",         // pure tone table
    "com/cerebrozen/app/audio/Player*",           // controller state (service intents recorded by Robolectric)
    "com/cerebrozen/app/audio/SoundscapeMixer*",  // mixer state machine + timer/volume/preset logic
    "com/cerebrozen/app/audio/VolumeRamp*",       // shared crossfade stepper (Robolectric drives its Handler)
    "com/cerebrozen/app/health/**",               // Health Connect sleep prefill (SDK-status seam short-circuits off-device)
    "com/cerebrozen/app/notify/**",               // Reminders + BootReceiver (Robolectric shadows AlarmManager/NotificationManager)
    "com/cerebrozen/app/ui/theme/**",             // palette objects, AppTheme mode logic, typography, CereBroTheme
    // Screen *Kt classes measured individually (2026-07 baseline): only these two
    // are NOT dominated by @Composable bodies and are fully exercisable off-device
    // (ConsentNoticeKt is rendered end-to-end by Robolectric; StoresKt is pure).
    "com/cerebrozen/app/ui/screens/ConsentNoticeKt*",
    "com/cerebrozen/app/ui/screens/StoresKt*",
)

// Excluded from the measured scope — each entry states WHY it cannot run
// hermetically (the backend-.coveragerc analogue). Everything else in the
// included packages must be covered.
val coverageExcludes = listOf(
    // Generated code — no behavior of ours to test.
    "com/cerebrozen/app/BuildConfig*",
    "**/R.class", "**/R$*.class",
    // Compose compiler artifacts inside included packages: synthetic singleton
    // holders for composable lambdas — rendering internals, not logic.
    "**/ComposableSingletons*",
    "**/LiveLiterals*",
)

// NOT in coverageIncludes (documented here so the omission is deliberate):
//  * ui/screens/** (except the two classes above) + ui/*.kt (CereBroApp/Brand/
//    Haptics) — Compose rendering. Screen files compile top-level @Composable
//    bodies into their *Kt classes, so even files with pure helpers are dominated
//    by composable UI code (measured 2026-07: AuthScreenKt 1.5%, TalkScreenKt 9%,
//    TodayScreenKt 16%, ExtrasKt 1.3% — all composable-dominated); pinning a line
//    gate to what Robolectric happens to render makes the number arbitrary. The
//    pure helpers in those files ARE unit-tested (ScreenLogicTest, SleepInsightTest,
//    TalkOffersTest, ContentArtTest…) — they're just not part of the gated denominator.

//  * auth/GoogleAuth — Credential Manager UI flow; needs Play services + a user.
//  * MainActivity — Activity lifecycle + edge-to-edge window plumbing.

fun jacocoClassDirs() = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
    include(coverageIncludes)
    exclude(coverageExcludes)
}

val jacocoTestReport by tasks.registering(JacocoReport::class) {
    group = "verification"
    description = "Line-coverage report over the app's testable logic scope."
    dependsOn("testDebugUnitTest")
    // The exact exec file — a broad fileTree(buildDir) would make every other
    // task's output an implicit input (Gradle dependency-validation failures).
    executionData.setFrom(layout.buildDirectory.file("jacoco/testDebugUnitTest.exec"))
    classDirectories.setFrom(jacocoClassDirs())
    sourceDirectories.setFrom(files("src/main/java"))
    reports {
        xml.required = true
        html.required = true
    }
}

// The gate: parses the XML report, prints the measured percentage (total and
// per-package), and fails the build below 95% — wired into `check` so
// `gradlew :app:check` enforces it exactly like the backend's CI coverage gate.
val jacocoLogicCoverageVerification by tasks.registering {
    group = "verification"
    description = "Fails unless line coverage over the logic scope is >= 95%."
    dependsOn(jacocoTestReport)
    // Resolve to plain, serializable values at configuration time — the doLast
    // closure must not capture script-object references (configuration cache).
    val reportFile = layout.buildDirectory
        .file("reports/jacoco/jacocoTestReport/jacocoTestReport.xml").get().asFile
    val htmlHint = layout.buildDirectory
        .dir("reports/jacoco/jacocoTestReport/html").get().asFile.absolutePath
    inputs.file(reportFile)
    doLast {
        val report = reportFile
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            // The JaCoCo DTD isn't shipped; don't try to fetch it.
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder().parse(report)
        fun lineCounter(el: org.w3c.dom.Element): Pair<Int, Int>? {
            val counters = el.childNodes
            for (i in 0 until counters.length) {
                val n = counters.item(i)
                if (n is org.w3c.dom.Element && n.tagName == "counter" && n.getAttribute("type") == "LINE") {
                    return n.getAttribute("covered").toInt() to n.getAttribute("missed").toInt()
                }
            }
            return null
        }
        fun pct(covered: Int, missed: Int) = 100.0 * covered / (covered + missed).coerceAtLeast(1)
        println()
        println("Logic-scope line coverage (JaCoCo):")
        val packages = doc.documentElement.getElementsByTagName("package")
        for (i in 0 until packages.length) {
            val p = packages.item(i) as org.w3c.dom.Element
            lineCounter(p)?.let { (c, m) ->
                println("  %-40s %6.2f%%  (%d/%d lines)".format(p.getAttribute("name"), pct(c, m), c, c + m))
            }
        }
        val total = lineCounter(doc.documentElement)
            ?: throw GradleException("No LINE counter in ${report}. Did testDebugUnitTest run?")
        val (covered, missed) = total
        val percent = pct(covered, missed)
        println("  %-40s %6.2f%%  (%d/%d lines)".format("TOTAL", percent, covered, covered + missed))
        println()
        if (percent < 95.0) {
            throw GradleException(
                "Logic-scope line coverage %.2f%% is below the 95%% gate ".format(percent) +
                    "(backend parity: pytest --cov-fail-under=95). " +
                    "See $htmlHint/index.html",
            )
        }
        println("Coverage gate OK: %.2f%% >= 95%%".format(percent))
    }
}

tasks.named("check") { dependsOn(jacocoLogicCoverageVerification) }

dependencies {
    implementation(libs.androidx.core.ktx)
    // Android 12+ SplashScreen API — a single branded, readiness-gated launch moment
    // (backfills the system splash on 6–11 too). See docs/SPLASH_SPEC.md.
    implementation("androidx.core:core-splashscreen:1.0.1")
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
    // Encrypted-at-rest storage for the refresh token + offline response cache.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Layered soundscape mixer — gapless looping per ambient layer (parity with
    // the iOS AVAudioEngine SoundscapePlayer). ExoPlayer loops each track seamlessly.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // Health Connect — optional last-night sleep prefill (Android's HealthKit analogue).
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    // callSuspend on Session's private real transport functions (coverage of the
    // genuine HttpURLConnection paths against a loopback server — no real network).
    testImplementation(kotlin("reflect"))
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
