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


## Tab navigation: nested graphs (open — the real fix)

Fixed 2026-07-17 with a **narrow** fix; the structural one is still owed.

**The bug (reproducible in 3 taps, found on the owner's CPH2681):** open a sub-screen
from Today → go to another tab → tap Today → you land on the sub-screen. Today stopped
being Today for the life of the process, and a fresh launch hid it entirely.

**Why:** the graph is FLAT and `onOpen` pushed routes that are themselves TAB routes
(`onOpen("actions")` x3, `onOpen("coach")` x2, `onOpen("journeys")`). So a tab's own
destination landed on ANOTHER tab's back stack. The tab bar's `saveState`/`restoreState`
— the textbook pattern, working exactly as designed — then faithfully restored
`[today → actions]`. One `actions` destination was serving two roles ("the Actions tab"
and "Actions pushed from Today") and the graph could not tell them apart.

**What shipped:** `open` is route-aware — a TAB route SELECTS its tab (same NavOptions as
the tab bar); anything else still pushes. A tab route can no longer enter another tab's
stack, so there is nothing wrong to restore.

**What is still owed — nested graphs, one per tab.** The narrow fix removes the six
known offenders; it does not remove the CLASS. Any future `onOpen(<a tab route>)`, or any
two tabs sharing a pushed destination, reintroduces it. The real fix needs an IA decision
first, and it is a product call, not a mechanical refactor — of 39 routes, only 5 are tabs,
so someone must say which tab OWNS each of the rest:

    sounds · player · toolkit · winddown · insights · sleep · games ·
    breathe · journal · programs · the legacy aliases "home" and "talk"

Back-button behaviour changes on every one of them, so it wants a full tap-through of all
five tabs plus the wellness suite on a device. Do not start it without that time.

**Reproduction, for whoever picks it up:** `am force-stop` → launch → tap a sub-screen
from Today → tap Journeys → tap Today. Correct = "Good afternoon, ..."; the bug = the
sub-screen.
