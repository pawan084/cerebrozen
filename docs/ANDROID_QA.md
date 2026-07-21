# Android — QA, device verification, and the deferred nav refactor

Last verified: **2026-07-21**.

This doc holds the line between **what CI proves** and **what only a physical device can**.
Two source files point here (`ui/CereBroApp.kt`, `ui/screens/AuthScreen.kt`) and so does
`IMPROVEMENT_BACKLOG.md` #174 — it is where an Android finding goes when it is real but not
yet fixable from a keyboard, and where a deliberately-deferred architectural task is scoped
instead of quietly dropped.

To actually run the app on hardware, see **[DEVELOPING.md](DEVELOPING.md) §Android** (JDK
path, the two `adb reverse` tunnels, the seeded logins). None of that is repeated here.

---

## 1. What CI proves

```
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd apps/android && ./gradlew :app:jacocoLogicCoverageVerification
```

Verified 2026-07-21 on the current tree: **40 suites · 297 tests · 0 failures · 0 skipped**,
and the coverage gate passes.

| Package | Line coverage | |
|---|---|---|
| `net` | 96.22% | 483/502 |
| `ui/screens` | 97.47% | 154/158 |
| `notify` | 100.00% | 46/46 |
| `audio` | 99.55% | 220/221 |
| `health` | 68.18% | 15/22 |
| `data` | 100.00% | 24/24 |
| `ui/theme` | 94.36% | 301/319 |
| **TOTAL** | **96.21%** | 1243/1292 — gate **95%** |

The gate is deliberately **logic-scope**, not whole-app: `jacocoClassDirs()` in
`app/build.gradle.kts` selects the packages above, because a line count that includes every
Compose lambda measures how much UI you wrote, not how much behaviour you tested. Two extra
build-failing checks ride along: `ui/theme/ContrastTest` (WCAG ratios on the real tokens) and
`ThemeTokensTest` (drift against `design/tokens.css`).

Release packaging is part of the definition of done, not a separate concern —
`./gradlew :app:assembleRelease :app:lintVitalRelease` (R8 + resource shrink + lintVital) is
green as of 2026-07-21. It catches what unit tests structurally cannot: a `BuildConfig.DEBUG`
branch R8 fails to fold, a keep-rule gap, a manifest a receiver needs.

**There is no `androidTest` source set.** Everything runs on the JVM (Robolectric for the two
Compose golden tests). That is a deliberate trade — instrumented tests need a booted emulator
in CI — and it is the whole reason section 2 exists.

### Structurally not unit-tested

Not gaps to be embarrassed about; framework surfaces with no seam worth faking:

- **Glance widget rendering** (`widget/HomeWidget.kt`) — runs in the launcher's process.
- **`AlarmManager` firing** (`notify/Reminders.kt` schedules; the *scheduling* is tested, the
  OS actually waking us at 09:00 is not).
- **Launcher app-shortcuts** (`res/xml/shortcuts.xml`) — the manifest wiring is only real
  once a launcher parses it.
- **`SpeechRecognizer` / `TextToSpeech` / `MediaPlayer` / Health Connect** — permission
  prompts, real transcription, audio focus, Bluetooth routing.
- **TalkBack's semantic merging.** A `uiautomator` dump does *not* reflect what TalkBack
  merges for a `clickable` wrapper and its children, so a dump is not evidence of an
  accessible screen.

---

## 2. Pending device verification

### Home / "Today" batch — **device pass done 2026-07-21**

All 32 points of [HOME_SPEC.md](HOME_SPEC.md) shipped 2026-07-20 with tests green but no
device connected. Verified on the OnePlus CPH2681 on 2026-07-21 (debug build against a local
platform+engine over two `adb reverse` tunnels):

- [x] **Brand mark** renders beside "CEREBROZEN" in Home's header — splash continuity holds.
- [x] **Hour-tinted sky** — the 14:00 sky matches the afternoon band.
- [x] **Pull-to-refresh** — indicator visible mid-gesture and the backend saw the re-warm
      (`GET /users/me/streak` fired). NOTE: `adb shell input swipe` does **not** trigger
      Compose's `PullToRefreshBox`; drive it with discrete `input motionevent DOWN/MOVE/UP`.
- [x] **Presence orb** renders warm with today checked in.
- [x] **Launcher shortcuts** — both registered (`adb shell dumpsys shortcut`) and both
      deep-link, **cold and warm**. Warm was broken and is now fixed — see §4.
- [x] **Reminder deep-link** — the daily notification posts on device, and its exact intent
      shape (`MainActivity` + `route=actions`, no CLEAR_TASK) lands on **Actions**. The tap
      itself was replayed as the equivalent intent rather than tapped in the shade, to avoid
      reading the owner's personal notifications.
- [ ] **Door reorder** in the 8pm–3am window — needs the device clock moved, which was not
      done on the owner's personal phone. `buildDoors` is pure and unit-covered; only the
      visual ordering is unconfirmed.
- [ ] **Widget** — pin "Today" from the launcher's widget picker; both buttons launch.
      (Requires launcher interaction; not automatable over adb.)
- [ ] **Landscape** (HOME_SPEC #30) — the activity is portrait-locked in the manifest, so
      this is a product decision before it is a test. Tracked in [CHAT_SPEC.md](CHAT_SPEC.md) §8.81.
- [ ] **≥600dp** (tablet or unfolded foldable) — the 2-up door grid.

### Coach / "Talk" batch — **not device-verified** (shipped 2026-07-21)

[CHAT_SPEC.md](CHAT_SPEC.md) §1.4/§1.5/§1.8 + §10.99. The copy change (#4) and its guard
(#99) are fully covered by `DisclosureCopyTest`; the two UI changes are not, because
`CoachScreen` is composable-dominated and outside the gated denominator (§1). So:

- [ ] **SOS control** — visible in the input row at every state (keyboard up and down, mic
      present and absent), one tap reaches the crisis screen, and Back returns to the
      conversation *with the draft intact*.
- [ ] **It must not be reachable by accident.** It sits beside the mic; a mis-tap while
      reaching for dictation is a bad surprise. Check the gap at a real thumb width.
- [ ] **Disclosure sheet** — the added "What's kept" block does not push "Got it" or "Get
      crisis support" off-screen on a 720×1600 device, and "Privacy & memory" lands on the
      unlocked privacy screen (**not** the entitlement-locked Pattern dashboard).
- [ ] **At 200% font scale** the sheet scrolls rather than clipping a legal sentence
      (CHAT_SPEC §5.61 — the pill already wraps to two lines at default).
- [ ] **TalkBack** announces the SOS control by name and role. Its label is the only thing
      distinguishing it from the mic to a non-sighted user.
- [ ] **AI disclosure line** (CHAT_SPEC §1.6) appears once at the head of a new session, is
      **absent** from a resumed thread's replay, and returns on the 20th user turn. TalkBack
      should read it politely — after the reply it follows, not interrupting it.
- [ ] **Support-route card** (CHAT_SPEC §1.7) renders under the reply on a `distress_route`
      turn and taps through to Human support. **Hard to reach on purpose:** with the crisis
      classifier on, the real model escalates most "can't cope" phrasing to a takeover
      before the route can fire (see IMPROVEMENT_BACKLOG #28), so provoking it on a device
      means the softer lexicon ("drowning", "barely holding it together") three times in one
      session. Confirm the card does **not** appear on the crisis turn that follows.

### Accessibility — needs TalkBack on hardware

- [ ] TalkBack through every screen: reading order, and every control announcing a
      meaningful name + role. `DoorCard` is the one to check first — its row merges
      title+description into one announcement and marks the chevron
      `clearAndSetSemantics` (HOME_SPEC #29), which is exactly the kind of change a dump
      cannot validate.
- [ ] Font scaling at 130% / 200% — heroes, chips, and the check-in card are the risk spots.
- [ ] Display-size / density scaling.
- [ ] RTL mirroring (`values-hi` ships, but Hindi is LTR — RTL is untested by any locale we
      currently ship).

### i18n — measured, not guessed

Counted 2026-07-21: **`values/strings.xml` has 770 strings; `values-hi/strings.xml` has
560** — a **210-string gap**, so a Hindi device silently falls back to English across whole
screens (breathe, CBT, and auth-error copy are the biggest clusters). This is pre-existing,
not a regression: the Home batch's four new shortcut labels *are* translated in both files.

Worth a build check that fails on a key present in `values` and absent in `values-hi`, the
same way `ThemeTokensTest` fails on colour drift — a gap this size grew precisely because
nothing was watching. No marketing copy currently claims Hindi support, so this is a product
gap rather than a rule-6 claims violation — but it becomes one the moment anyone writes
"available in Hindi" (`docs/CLAIMS_MAP.md`).

---

### Defects the device found that CI could not — 2026-07-21

All three were invisible to the JVM suite, and they are the reason this section exists.

1. **A crisis takeover rendered as "…".** The engine detected the disclosure, escalated it
   and served the scripted helpline reply; the app showed an ellipsis. A crisis reply emits
   **no token frames** (there is no model in that path), so the whole message arrives in the
   `done` SSE frame under `response_to_user` — and `CoachScreen` read `reply`/`text`, keys
   the engine has never sent. Every other reply streams, so nothing else ever reached that
   fallback. Fixed + pinned by `CoachReplyFallbackTest`; re-verified on device (the reply now
   renders in full, ending with the engine's AI disclosure).
2. **The warm launcher shortcut ignored its route.** A shortcut carries
   `NEW_TASK|CLEAR_TASK|CLEAR_TOP`, and CLEAR_TASK **recreates** MainActivity rather than
   delivering `onNewIntent`. With a process-global `PendingRoute`, the outgoing activity's
   composition observed the new activity's route, navigated its own dying NavHost and
   cleared it — so the user landed on Home. Cold start always worked, which is why the first
   device pass missed it. Holder is now per-activity; both routes verified cold and warm.

3. **The app called itself an "AI companion"** in 17 strings including the compliance
   disclosure pill — the exact word CA SB243 / NY GBL art. 47 attach to, inside the one
   sentence written to satisfy them, while `/v1/governance` attested "non-companion by
   design". Not a device *defect* so much as something only reading the screen surfaces:
   the contradiction is invisible in a diff and invisible in a test suite that never
   asserts on copy. Self-description is now "AI coach" and the feature is "Coaching style",
   in English and Hindi; `DisclosureCopyTest` keeps it that way. See
   [CHAT_SPEC.md](CHAT_SPEC.md) §1.4.

## 3. Verified on hardware

Physical OnePlus CPH2681 / Android 14, unless noted.

| Date | What | Notes |
|---|---|---|
| 2026-07-20 | Splash batches 1–3 | Branded orb → aurora arrival → populated home; no unbranded first frame, no arbitrary timer. Time-of-day sky and the returning-user greeting confirmed live at 16:35. See [SPLASH_SPEC.md](SPLASH_SPEC.md). |
| 2026-07-20 | B2C purchase loop | Onboarding (18+ attest → consent → account) → free-tier lock badges → paywall → mock purchase → **instant unlock** → cancel → **instant re-lock**. The instant part is the token rotation on purchase/cancel; without it the engine's plan gate lags up to 15 min. |
| 2026-07-20 | Org invite redemption | A **real** invite token redeemed end-to-end → signed into the employer's org as Employee. |
| 2026-07-20 | Tab back-stack no-leak | Today → "Talk it through" (coach) → tap Today returns to **Today**. This is the user-facing symptom of #174 (§4). |
| 2026-07-20 | Warm-path shortcut delivery | A second shortcut tap while the app was already running — the bug in §4's log. |

---

## 4. Deferred: #174, the full nested-graph refactor

**Status: symptom fixed and device-verified; the architectural fix is deliberately deferred.**

**The bug.** Home's "Talk it through" called `navigate("coach")` — pushing the **Coach tab's
own destination** onto **Today's** back stack. The tab bar saves and restores each tab's stack
(`saveState`/`restoreState` — the textbook pattern, working exactly as designed), so from then
on tapping Today faithfully restored `[today → coach]` and landed on Coach. Today stopped
being Today for the life of the process. Six call sites did this: `onOpen("actions")` ×3,
`onOpen("coach")` ×2, `onOpen("journeys")`.

**The interim fix** (`ui/CereBroApp.kt`, `open`): a route that belongs to a tab is *selected*
(`popUpTo(start) { saveState }` + `launchSingleTop` + `restoreState`); anything else is pushed
on the current tab. A tab route can no longer enter another tab's stack. Device-verified.

**Why the real fix is deferred, not forgotten.** The correct structure is nested graphs, one
per tab, so a pushed screen lives *inside* its tab and "restore this tab" cannot mean "restore
some other tab". That is not a mechanical refactor — it is a **39-route information-architecture
decision**: which tab owns `sounds`, `player`, `toolkit`, `winddown`, `insights`? Each answer
changes where Back goes from that screen. The risk is spread across *all* navigation, and
there is no automated way to verify 39 routes (no `androidTest` source set — §1). It needs a
device session with a human walking the routes, so it is scoped here rather than rushed.

**What it would take:** decide tab ownership for all 39 routes (product call, not technical) →
one `navigation()` block per tab → move each `composable` into its owner → re-verify Back from
every screen on hardware.

---

## 5. Bugs only the phone found

Kept because each one is a class of mistake that repeats, and none of them could have failed
in CI.

| Finding | Why CI could never catch it |
|---|---|
| **Warm-start shortcut delivery did nothing.** `PendingRoute` was a plain `var`; `onNewIntent` set it, but nothing told the already-composed NavHost's `LaunchedEffect` to look again — only a *cold* start ever navigated. Fixed by making it `mutableStateOf` and keying the effect on the **value**, not `Unit`. | Needs a running app, a real launcher, and a *second* tap. Both halves (`singleTask` + `onNewIntent`) are OS behaviour. |
| **The debug sign-in prefill named an account that does not exist.** `worker@acme-test.example` was never seeded, so a one-tap convenience silently did nothing — no error worth reading. Now the real seeded member; keep it in step with the platform seeder (`PERSONAS` in `e2e/tests/helpers.ts` is the same three accounts). | A prefill's *content* is not a testable assertion; it only fails against a live backend on a fresh device. |
| **Two `adb reverse` tunnels are required**, not one — platform **8100** *and* engine **8000**. A single 8000 tunnel signs nobody in. | Environment, not code. Recorded in [DEVELOPING.md](DEVELOPING.md) §Android. |

---

## 6. Known code-health follow-ups

Small, real, not urgent — recorded so they don't get re-discovered.

- **`0xFF6C7BD8` is a literal in three places** (`ui/Brand.kt:190` splash day-sky,
  `ui/screens/CoachHome.kt` `homeSkyBrush`, and the Wind-down door accent). It should be a
  named token in `ui/theme/Color.kt` so `ThemeTokensTest`'s drift check can actually see it —
  right now the app's one cool evening hue is invisible to the very gate that exists to stop
  colours drifting. `S`.
- **`Periwinkle` and `Violet` are misleading names.** In this app's night palette
  `NightPalette.periwinkle` is `0xFFF58A8A` — a coral-pink, not the cool blue-violet the name
  promises. This cost real time twice during the Home batch (two "fixed" accents turned out
  visually identical to Commitments' coral, both caught only on a device). Rename to what
  they are. `S`.
- **`TodayHome`'s `hour` is `remember { … }`** — captured once per composition, so an app
  left open past 8pm won't promote the Wind-down door until the next recomposition. Correct
  99% of the time (people don't hold Today open across the boundary) and cheap to leave;
  noted so it isn't mistaken for a bug in the ordering rules, which are pure and tested. `S`.

## 7. When you finish a device session

Update this file in the same PR: tick §2, add a row to §3, and add anything the phone found
to §5. A checklist that is never ticked stops being read, and an untracked device finding is
one that gets re-discovered six weeks later.
