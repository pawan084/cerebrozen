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
