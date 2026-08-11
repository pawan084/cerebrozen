# Android — Release & Play Submission

Companion to [RELEASE_PLAN.md](RELEASE_PLAN.md) (iOS) and [ANDROID_QA.md](ANDROID_QA.md).
What's verified, and the exact owner steps left to ship on Google Play.

## Build status — re-verified 2026-08-05

- `./gradlew :app:assembleRelease` green: **BUILD SUCCESSFUL in 8m 11s**, R8 and
  `lintVitalRelease` both pass. Output
  `app/build/outputs/apk/release/app-release-unsigned.apk` — **16.6 MB**.
- Release config confirmed from the merged manifest + `BuildConfig`:
  - `allowBackup="false"` + `dataExtractionRules` — the refresh token and the
    offline cache of personal data are excluded from cloud backup **and**
    device-to-device transfer (all re-syncs from the server).
  - **HTTPS-only** — no `usesCleartextTraffic` (that's a debug-only overlay).
  - `API_BASE_URL = https://api.cerebrozen.in` (debug = `10.0.2.2:8000`).
  - Permissions: INTERNET, RECORD_AUDIO, POST_NOTIFICATIONS,
    RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE(+MEDIA_PLAYBACK), WAKE_LOCK,
    VIBRATE, and **`health.READ_SLEEP`** — each justified; receiver + services
    are `exported="false"`, and both services declare
    `foregroundServiceType="mediaPlayback"`.
- The artifact is **unsigned** — `signingConfigs.release` now exists in
  `app/build.gradle.kts` but is only created when an upload keystore is
  configured (`KEYSTORE_FILE` etc. via the same `secret()` chain as the API
  keys). With no key present the build stays green and simply emits the
  unsigned APK, which is what CI and every keyless checkout need.
- `versionName` is **1.0.0** (`versionCode` 1) as of 2026-08-05.

> **Correction (2026-08-05).** The R8 section below claimed a 2.5 MB release
> APK. That measurement is stale — it predates ExoPlayer/media3, Health
> Connect, Firebase Messaging, Credential Manager and Coil. The real figure
> today is 16.6 MB, which is unremarkable for this dependency set. The
> `bundleRelease` AAB will be smaller than the universal APK (per-device
> splits), but the "~12 MB" figure above was never re-measured either.

## Owner must-do to ship (no app code left)

1. **Upload key** — the `signingConfigs.release` block is now in
   `app/build.gradle.kts`; only the key itself is missing. Create it and put the
   four values in `local.properties` (git-ignored, as are `*.keystore` and
   `*.jks`):
   ```
   keytool -genkey -v -keystore cerebro-upload.jks -keyalg RSA \
     -keysize 2048 -validity 10000 -alias cerebro
   ```
   ```properties
   KEYSTORE_FILE=/absolute/path/cerebro-upload.jks
   KEYSTORE_PASSWORD=…
   KEY_ALIAS=cerebro
   KEY_PASSWORD=…
   ```
   **Back it up in two places.** Lose this key and the app can never be updated
   on Play — a new package name is the only recovery, and it costs every install
   and review.
2. **Play Console** — create the app for `com.cerebrozen.app`, enable Play App
   Signing, push the AAB to an internal-testing track first.
3. **Google Sign-In** — create the OAuth **Web** client id + an **Android** client
   id (registered with the signing key's SHA-1); set `google_web_client_id`
   server-side. Also unblocks iOS/web. Until then "Continue with Google" degrades
   gracefully. (See [TODO.md](TODO.md) owner block.)
4. **Play Billing** — create the subscription products in Play Console mirroring
   the App Store SKUs and wire Play RTDN → the server webhook. *(The Android IAP
   client is not built yet — a separate code item; iOS/web billing is done.)*
5. **Privacy policy URL** — done: <https://cerebrozen.in/privacy> is live
   (`apps/web/app/privacy/page.tsx`). Paste it into the listing.
6. **Account deletion URL** — Play requires a page reachable **without
   installing the app**. `apps/web/app/delete-account/page.tsx` is written
   (in-app path + a verified email route + what is and isn't erased); it needs a
   web deploy, then the URL goes in the Data safety section.
7. **Health Connect declaration** — the manifest holds
   `android.permission.health.READ_SLEEP`, so Play needs its separate Health
   Connect data-type declaration form and approves it out of band. Budget time
   for this; it is a common cause of a first-submission delay and was missing
   from this checklist until 2026-08-05.
8. **FCM** — `google-services.json` is present, so push is wired. v1 reminders
   are local (AlarmManager) regardless, so Firebase is not required to launch.

## Play Data Safety form (declare truthfully)

- **Collected:** account (email, name); health & wellness (moods, journal text,
  sleep logs); diagnostics (first-party anonymous event counts).
- **Audio:** the mic feeds **on-device** speech-to-text; raw audio is never
  uploaded — only the transcribed text reaches `/chat`.
- **In transit:** encrypted (HTTPS). **Backups:** off. **Deletion:** in-app
  (`DELETE /users/me`) + data export.
- **No** third-party advertising or analytics SDKs.

## Store listing (owner)

Drafted copy + the 512×512 store icon live in
[`apps/android/playstore/`](../apps/android/playstore/) — `LISTING_COPY.md`
(app name, short + full description, and the list of claims deliberately left
out) and `play-icon-512.png` (32-bit RGBA, rendered from the same vector layers
as the launcher icon so store and launcher art cannot drift).

Still to make: **feature graphic 1024×500** and **phone screenshots** (the
polished Home / Sleep / Talk / Journal shots work well). Plus in Console:
**category: Health & Fitness**, content-rating questionnaire, target-audience,
and the medical-disclaimer copy ("supportive AI, not medical care").

## Before each release

- Bump `versionCode` (+1 integer) and `versionName` in `app/build.gradle.kts`
  (currently `1` / `1.0.0` — `versionCode` 1 is correct for the first upload
  only; every later upload needs a strictly higher integer).
- `:app:testDebugUnitTest` (CI runs it) + walk the [ANDROID_QA.md](ANDROID_QA.md)
  real-device / TalkBack checklist.

## R8 minification — ENABLED (2026-07-07)

`isMinifyEnabled = true` + `isShrinkResources = true`: release APK went
**13.3 MB → 2.5 MB (−81%)** *as measured on 2026-07-07 — see the correction at
the top of this file; the same build today is 16.6 MB because of dependencies
added since.* App code is reflection-free (org.json parsing,
Intent-only class refs) and Coil / Credential Manager / googleid ship consumer
keep rules, so `proguard-rules.pro` stays empty. Emulator-smoked on a
debug-signed release build (API-35 AVD): launch → Welcome → age gate → AI
disclosure → language → state-check → auth screen incl. the inert
"Continue with Google" path — zero `AndroidRuntime` errors. Owner: repeat the
[ANDROID_QA.md](ANDROID_QA.md) pass on a real device before the Play upload
(mapping.txt from `bundleRelease` uploads alongside the AAB for de-obfuscated
crash reports).
