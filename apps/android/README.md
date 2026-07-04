# CereBro — Android

Native Android scaffold in **Kotlin + Jetpack Compose (Material 3)**, brand-matched
to the iOS app and the same five-tab structure (Home · Sleep · Talk · Journal · You).
Live slices shipped 2026-07-04 (all verified end-to-end on an API-35 emulator
against the shared backend): auth (refresh-token rotation, same accounts as
iOS/web), **Today** (mood check-in, server streak, recent), **Journal**
(composer + history + never-blocking support card), **Sleep** (diary check-in,
honest `enough_data` summary, history), **Talk** (live /chat with suggestion
chips). Remaining tabs/features: see the roadmap below.

## Stack
- Kotlin 2.0 · AGP 8.7 · Gradle 8.11 (Kotlin DSL + version catalog `gradle/libs.versions.toml`)
- Jetpack Compose (BOM) · Material 3 · Navigation-Compose
- `minSdk 26` · `targetSdk/compileSdk 35` · package `com.cerebrozen.app`

## Structure
```
apps/android/
├── settings.gradle.kts · build.gradle.kts · gradle.properties
├── gradle/libs.versions.toml            version catalog
└── app/
    ├── build.gradle.kts                 module config (+ API_BASE_URL BuildConfig)
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/                  strings, colors, theme
        └── java/com/cerebro/app/
            ├── MainActivity.kt
            ├── net/Session.kt           zero-SDK API client (HttpURLConnection +
            │                            org.json): auth + refresh rotation + endpoints
            └── ui/
                ├── CereBroApp.kt        auth gate + Scaffold + bottom-nav + NavHost
                ├── theme/               Color · Type · Theme (brand palette)
                └── screens/             Auth/Today (live) · Sleep/Talk/Journal/You
```

## Run

Open `apps/android/` in **Android Studio** and Run, or from the CLI (the
wrapper is committed; it fetches Gradle 8.11.1 on first use):

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleDebug      # build (any JDK 17+ works)
./gradlew installDebug              # install on a running emulator/device
```

## Backend

`BuildConfig.API_BASE_URL` is per build type: **debug** → `http://10.0.2.2:8000`
(the emulator's alias for the host's `localhost`; cleartext is allowed only via
the debug manifest overlay) and **release** → `https://api.cerebrozen.in`.
The refresh token lives in plain SharedPreferences for now (parity with the web
app's localStorage) — move to EncryptedSharedPreferences with security-crypto.

## Roadmap to iOS parity
1. ~~Networking layer~~ ✅ minimal zero-SDK client (`net/Session.kt`) with JWT +
   refresh rotation; Apple/Google sign-in still to add.
2. Real screens per tab — Today/Journal/Sleep/Talk ✅ (live against the
   backend); next: offline-first local store (Room), sleep soundscapes via
   `AudioTrack`/ExoPlayer, the voice loop, plans/insights, crisis resources +
   trusted contact, You/settings parity.
3. StoreKit's counterpart: Google Play Billing for subscriptions.
4. Notifications (FCM) for the daily reminder.
