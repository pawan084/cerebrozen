# Android — Release & Play Submission

Companion to [RELEASE_PLAN.md](RELEASE_PLAN.md) (iOS) and [ANDROID_QA.md](ANDROID_QA.md).
What's verified, and the exact owner steps left to ship on Google Play.

## Build status — verified 2026-07-06

- `./gradlew :app:assembleRelease` **and** `:app:bundleRelease` both green.
  Play artifact: `app/build/outputs/bundle/release/app-release.aab` (~12 MB).
- Release config confirmed from the merged manifest + `BuildConfig`:
  - `allowBackup="false"` + `dataExtractionRules` — the refresh token and the
    offline cache of personal data are excluded from cloud backup **and**
    device-to-device transfer (all re-syncs from the server).
  - **HTTPS-only** — no `usesCleartextTraffic` (that's a debug-only overlay).
  - `API_BASE_URL = https://api.cerebrozen.in` (debug = `10.0.2.2:8000`).
  - Permissions: INTERNET, RECORD_AUDIO, POST_NOTIFICATIONS, FOREGROUND_SERVICE(+MEDIA_PLAYBACK)
    — each justified; receiver + service are `exported="false"`.
- The AAB is **unsigned** — it needs the owner's upload key (below).

## Owner must-do to ship (no app code left)

1. **Upload key** — create an upload keystore; add a `signingConfigs.release` in
   `app/build.gradle.kts` fed from `~/.gradle`/CI secrets (never committed), or
   rely on Play App Signing with an uploaded upload-key.
2. **Play Console** — create the app for `com.cerebrozen.app`, enable Play App
   Signing, push the AAB to an internal-testing track first.
3. **Google Sign-In** — create the OAuth **Web** client id + an **Android** client
   id (registered with the signing key's SHA-1); set `google_web_client_id`
   server-side. Also unblocks iOS/web. Until then "Continue with Google" degrades
   gracefully. (See [TODO.md](TODO.md) owner block.)
4. **Play Billing** — create the subscription products in Play Console mirroring
   the App Store SKUs and wire Play RTDN → the server webhook. *(The Android IAP
   client is not built yet — a separate code item; iOS/web billing is done.)*
5. **Privacy policy URL** — required by Play; the app already surfaces one
   (`privacypolicy` screen). Host it publicly and paste the URL into the listing.
6. **FCM** — only if you want push. v1 reminders are local (AlarmManager), so no
   Firebase is required to launch.

## Play Data Safety form (declare truthfully)

- **Collected:** account (email, name); health & wellness (moods, journal text,
  sleep logs); diagnostics (first-party anonymous event counts).
- **Audio:** the mic feeds **on-device** speech-to-text; raw audio is never
  uploaded — only the transcribed text reaches `/chat`.
- **In transit:** encrypted (HTTPS). **Backups:** off. **Deletion:** in-app
  (`DELETE /users/me`) + data export.
- **No** third-party advertising or analytics SDKs.

## Store listing (owner)

Phone screenshots (the polished Home / Sleep / Talk / Journal shots work well),
feature graphic, short + full description, **category: Health & Fitness**,
content-rating questionnaire, target-audience + medical-disclaimer copy
("supportive AI, not medical care").

## Before each release

- Bump `versionCode` (+1 integer) and `versionName` in `app/build.gradle.kts`
  (currently `1` / `0.1.0`).
- `:app:testDebugUnitTest` (CI runs it) + walk the [ANDROID_QA.md](ANDROID_QA.md)
  real-device / TalkBack checklist.

## Deferred: R8 minification (needs a device)

`isMinifyEnabled = false` today, so the release APK is ~13 MB. Turning on R8
shrink+obfuscate would trim it, but must be validated on a real device
(reflection paths in org.json / Coil / Credential Manager) with keep-rules and a
full smoke test — do it as its own pass, not blind before launch.
