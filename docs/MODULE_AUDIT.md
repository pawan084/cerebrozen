# Module Audit Protocol

> One module at a time: look at it on a real device, score it, fix everything
> found, re-verify, commit. Invoked by naming a module ("audit Splash") — no
> further briefing needed.
>
> Born out of the 2026-07-30 device pass, which found three defects that every
> automated gate had passed: a goal row that broke across three lines, a
> full-screen bright player at bedtime, and (earlier) a fabricated Pattern
> Dashboard. The common factor is that **none of them were visible without
> looking at the screen.** This protocol exists to make looking systematic.

## The one rule

**A finding is not real until it is seen, and a fix is not done until it is
seen again.** Screenshots before and after, on hardware where possible. A
passing build is not evidence about a layout.

## Steps

1. **Locate** — list every file the module owns (screen, view-model, strings in
   both locales, routes in, routes out). State them, so scope is explicit.
2. **Read the intent** — what do PRD.md / REDESIGN.md / the design system say
   this module is *supposed* to do? A module can be flawless and still wrong.
3. **Capture** — launch it on the device and screenshot every state reachable
   without inventing data: empty, populated, loading, error, signed-out. Long
   text and Hindi are states too.
4. **Score** (below), with the reasons that cost points.
5. **Fix everything found**, hardest first. If something cannot be fixed
   without a product decision, say so and leave it — do not guess.
6. **Re-verify on the device**, then run the gates the module touches
   (`testDebugUnitTest` + `lintVitalRelease`, backend suite, `check-claims`).
7. **Commit** with the finding, the evidence, and what was deliberately left.
8. **Update** PRD/ARCHITECTURE/TODO if behaviour or a contract changed.

## Scoring — 1 to 10

Score the module as a user meets it, not as code. Start at 10 and subtract:

| Dimension | What costs points |
| --- | --- |
| **Truthfulness** | Anything shown that is not real: invented data, a control that does nothing, a claim the code cannot back. **Any fabrication caps the module at 3**, however pretty the rest is. |
| **Layout** | Clipping, overlap, text wrapping badly, tap targets under 48dp, breakage at 720px or with Hindi. |
| **Theme** | Wrong palette for the context, raw hex instead of tokens, contrast below 4.5:1, a light screen in a dark moment. |
| **State coverage** | Empty/loading/error states missing, or dishonest ("no data" rendered as zeros). |
| **Accessibility** | Missing labels, unlabelled icon buttons, nothing announced when content changes on its own. |
| **Copy** | Overclaims, jargon, catastrophising, or promises the backend does not keep. |
| **Behaviour** | Actions that fail silently, non-idempotent taps, no undo for destructive things. |

Bands: **9–10** ship as-is · **7–8** minor polish · **5–6** real defects, fix
before release · **3–4** a user would lose trust · **1–2** actively misleading
or broken.

Score honestly. A 6 that gets fixed is worth more than a 9 that was flattery.

## Module list

Android (primary target — a real device is attached):

`splash` · `onboarding` · `home` · `sleep` · `talk` · `journal` · `you` ·
`patterns` · `goals` · `safety-plan` · `programs` · `toolkit` · `crisis` ·
`premium` · `privacy` · `insights`

iOS and web mirror most of these; audit them separately, since the same module
can be right on one platform and wrong on another — the Pattern Dashboard was.

## Log

| Date | Module | Platform | Before | After | Commit |
| --- | --- | --- | --- | --- | --- |
| 2026-07-30 | splash | Android | **5** | 9 | `Splash: the first frame, fixed` |
| 2026-07-30 | onboarding | Android (+iOS/web copy) | **3** | 8 | `Onboarding: four things it told users that were not true` |
| 2026-07-30 | home | Android (+backend seed) | **5** | 8 | `Home: a bright screen at bedtime, and a plan that said its own name twice` |

### splash — Android, 2026-07-30 (5 → 9)

Six findings, all measured on an OPPO CPH2681 at 23:00 by frame-stepping a cold
launch (`am start` + an on-device `screencap` loop, then sampling pixels — a
single `adb exec-out screencap` is too slow to catch a 1.1s screen).

1. **Status-bar icons rendered near-black over the Night splash.** Clock pixels
   measured luminance 6–56 against a sky of 49: the darkest ink was *darker*
   than the background. `SyncSystemBarIcons()` sat above `forceNight = true`, so
   it read the previous theme; any phone whose system theme is light got an
   unreadable status bar for the app's entire first second. After: 56–215.
2. **`@color/night` had drifted from the palette** — `#080B22` (the iOS value)
   against Compose's `#100D2B`, so the platform splash and the app floor were
   different colours. Now gated by a test.
3. **A hard brightness cut on handoff:** flat `#100D2B` → `#3A3372` in one
   frame, ~5× the luminance, at 22:54. The splash now opens the sky from the
   exact colour the platform handed over.
4. **A retired colour in the brand glow** — hardcoded `0x668A7BF0`, the
   periwinkle replaced on 2026-07-12. Raw hex in a screen, and the wrong hex.
5. **"Soft aurora ribbons" were hard-edged stripes** — the band resolved from
   backdrop to full colour in under 4px (Δ29 luminance in one pixel). Feathered
   in overlapping passes; steepest step now Δ8.7, a continuous falloff.
6. **Reduce Motion still waited the full 1100ms** on a frame that had already
   finished. Now 450ms — less motion, not more waiting.

Not fixed, deliberately: the launch shows two brand screens back to back (the
platform splash, then ours). Collapsing them into one via
`setOnExitAnimationListener` would drop the wordmark, so it is a product call,
not a defect. The remaining point is that cost.

### onboarding — Android, 2026-07-30 (3 → 8)

Capped at 3 by the rubric: four separate truthfulness findings. Walked all eight
steps on an OPPO CPH2681 (`pm clear` is blocked by the OEM — uninstall and
reinstall for a genuine first run).

**Not true:**

1. **"Private by design — nothing is ever shared."** The second sentence a new
   user ever reads, on Android, iOS *and* the browser client. A Talk message goes
   to OpenAI or Anthropic (`services/ai.py`); voice goes to Deepgram or
   ElevenLabs (`services/voice.py`). The app's own privacy screen was always
   careful — "Support tooling sees counts and account state, never the words" —
   and the funnel flattened it into an absolute the product cannot keep. Replaced
   with three things that are true and mechanised, added to `CLAIMS_MAP.md`, and
   the phrase is now banned by `check-claims`.
2. **The age gate showed a confirmation nobody had given.** A tick, "Confirmed: I
   am 18 or older", and "Thank you" — rendered on arrival, before any input. A
   compliance surface presenting a pre-made affirmation. It now states the
   requirement; the CTA is the confirmation.
3. **"Private previews" silently turned reminders off.** It sat in the
   single-select reminder-*time* group, nothing ever read the value, no preview
   setting exists, and `applyReminderChoice`'s `else` branch meant a user who
   asked for a discreet daily nudge got none and was told nothing. Removed, and
   the invariant is now `reminderHourFor` + a test rather than a comment.
4. **"You've had your first win"** was shown to everyone — including users who
   had just pressed "Skip for now". Now conditional on actually breathing.

**Also fixed:** the "Can't do" tile was a fixed `129.dp` and cut the word
*emergencies* in half — the most important limitation on the screen whose whole
job is stating limitations, truncated in the shortest locale we ship; and
`DisclosureTile.accent` was accepted and never applied, so the two tiles rendered
identically in white despite the call site passing Cyan and TextSoft.

**Behaviour:** the system back gesture fell through to the Activity and finished
it. A back swipe from any of the eight steps dropped the user on the launcher,
and relaunching restarted at Welcome with language, feeling and consent choices
gone — `rememberSaveable` cannot survive an activity that was destroyed rather
than recreated. Android's most-used navigation control was a trapdoor out of
onboarding. Now a `BackHandler` walks the funnel.

**Left deliberately:**

- **"Try a 2-minute reset" / "Two minutes of guided breathing."** The Reset preset
  has no timer — it cycles until tapped, and the same sentence then says "for a
  few cycles". The claim is imprecise rather than false, and it lives on iOS too
  with UI tests asserting the button label, so it needs one cross-client copy
  pass rather than an Android-only edit.
- Language's "Mix more than one if that's you" over single-select chips; the
  State rows' chevron (a navigation affordance on a selection control); the large
  dead space below short steps; "Continue with Google" as the visual primary
  while Google sign-in is unconfigured. All design/product calls, not defects.
- The daily reminder's own text is hardcoded English in `notify/Reminders.kt` — a
  Hindi user gets an English notification. Belongs to the `notify` module.

### home — Android, 2026-07-30 (5 → 8)

Audited signed in as the demo account at 23:43.

1. **The whole screen was Dawn at 23:43 — under a banner reading "The day is
   winding down."** REDESIGN §4.1 says "Sleep tab **and wind-down hours** always
   Night"; only the Sleep half was ever built. This is the third time the same
   harm has been found on hardware (the sleep player at 22:46 was the second).
   Now `nightFor(mode, systemDark, hour)` in AppTheme, with the resolution
   matrix as a test. It applies to **System mode only** — an explicit Dawn choice
   is a choice, and the clock does not overrule it. The 21:00 boundary is one
   shared constant with Home's banner so theme and copy cannot disagree about
   when evening is.
2. **The plan hero printed its own title twice.** The generator names a plan
   after its focus goal, so `title` and `focus` come back identical and the card
   rendered "Sleep before midnight" on two consecutive lines. `rationale` — the
   "why this, today" line — was being fetched and thrown away. Now shown.
3. **The wind-down banner truncated, and offered a door it does not open.** ~250px
   of text column next to two controls, so "The day is winding down — a quieter
   mix, or tonight's wind-down guide?" ellipsised away the entire offer. It also
   named the wind-down guide while the button calls `openMixer`. Copy, label and
   action now agree.
4. **"anxious · "** — Recent check-ins emitted the separator whether or not there
   was a note. The same line used `getString`, which throws on a null field
   inside a `runCatching`, so one null note would have made the section silently
   vanish instead of degrade.
5. **The 2026-07-04 "imagery honesty pass" never reached existing data.** It set
   `seed._IMG = ""` so clients render branded symbol wells instead of hotlinked
   stock photos — but seeding is additive by title, so only rows created after
   that date got it. Every earlier database still served Unsplash URLs: on device
   the sleep story "Rain over quiet hills" was illustrated with a sunlit desert
   canyon, fetched straight from a third-party CDN with the user's IP attached,
   on the Home screen of a privacy-first product. Backfilled, scoped to
   `images.unsplash.com` so admin-attached licensed art is never touched.

**Honest and left alone:** the presence week ring shows seven empty days, and the
backend agrees — the demo account's last check-in was three weeks ago. The client
is telling the truth; that is the state working.

**Left deliberately:**

- The "For tonight" rail holds one card and leaves half a row empty, because
  `/content?kind=sleep` genuinely has one item. Whether a one-item rail should
  render full-width or stop being a rail is a design call.
- Clients still load admin-attached art directly from whatever host the URL names
  (Coil, no proxy), so a third party can learn a user's IP and which piece of
  wellness content they were shown. Nothing seeded points off-domain any more,
  but the capability is unchanged. Proxying media through the backend is an
  architecture decision, not a Home fix.
- Plan screen shows the plan name twice too — once as the screen title, once as
  the hero title under "WHY THIS PLAN". Found from here; belongs to `plan`.
- "Recent check-ins" carries no dates, so three-week-old entries read as recent.

**Note:** two accidental taps during this pass toggled a plan step on the demo
account. Both were reverted through the API (`PATCH /plans/steps/{id}`), and the
plan is back at 1 of 3.

## Device commands

```bash
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
adb reverse tcp:8000 tcp:8000                       # phone → host API
cd apps/android && ./gradlew installDebug -PapiBaseUrl=http://localhost:8000
adb exec-out screencap -p > shot.png                # look at it
adb shell uiautomator dump /sdcard/ui.xml           # exact tap coordinates
adb logcat -d -s CereBroApi:D                       # what it actually called
```

Gotchas already paid for: `adb shell input keyevent 4` closes the *screen*, not
just the keyboard — tap the target directly while the keyboard is up. `input
swipe` does not drive Compose `PullToRefresh`. The OEM blocks `pm grant`.
