# Splash screen — world-class spec (Android)

Target: turn the launch moment from "a logo on a timer" into a **single, readiness-driven
brand moment** that is fast, alive, accessible, and seamless into the app.

> **Shipped 2026-07-20 (device-verified on a physical OnePlus / Android 14):** the top
> cluster — **#1** `core-splashscreen` + `installSplashScreen()`, **#2** branded system splash
> (the `ic_launcher_foreground` orb on `@color/night`), **#3** `setKeepOnScreenCondition` on a
> real readiness signal, **#4** `postSplashScreenTheme`, **#5** fade+lift exit handoff, **#6**
> `Session.warmBoot()` readiness (min-floor + `withTimeoutOrNull` failsafe → **#34**), **#7**
> plan/entitlement warm-path under the splash, **#8** once-per-process (skips config changes).
> Verified: branded orb → aurora arrival (same orb, continuous) → populated home; no unbranded
> first frame, no arbitrary loading timer. `warmBoot` unit-tested; JaCoCo 95.70%.
>
> **Batch 2 shipped 2026-07-20 (device-verified):** **#10** spring-like orb overshoot
> (peak ~1.04 → rest at 1.0), **#14** per-star twinkle (independent phase, not one global
> fade), **#18** a light glint sweeping across "Bro" once as it arrives, **#27**
> `reportFullyDrawn()` + a cold-start time-to-full-display log (measured live: e.g.
> "cold-start fully-drawn in 4116ms" on a debug build), **#29** a TalkBack announce
> (`contentDescription` + polite live-region "CereBro, loading"). The choreography curves are
> unit-tested (`SplashCurvesTest`).
>
> **Batch 3 shipped 2026-07-20 (device-verified):** **#21** time-of-day sky — the gradient top
> and the aurora carry a hint of the hour (dawn rose / day periwinkle / dusk violet / deep
> night), still a night scene; **#22** an earned greeting — a returning (signed-in) account is
> welcomed back with a time-of-day line under the wordmark ("Good afternoon" verified at
> 16:35), a first-run user sees only the wordmark. Remaining below are further craft.

**The state this spec was written against** — i.e. the splash *before* the work above, kept
as the baseline the numbered points argue from. **None of this is true any more**; see the
shipped banner above, and `MainActivity.kt` (`installSplashScreen()`,
`setKeepOnScreenCondition { !Session.bootReady }`, `setOnExitAnimationListener`),
`build.gradle.kts` (`core-splashscreen:1.0.1`) and `res/values/themes.xml`
(`postSplashScreenTheme`) for what replaced it:

> (`apps/android/.../ui/Brand.kt::Splash()`, gated in `ui/CereBroApp.kt`): Compose splash —
> night gradient, Canvas aurora ribbons + 46-star field, `BrandMark` (C-ring + orb)
> scale-settle + glow-bloom, "CereBro" wordmark fade-up, reduce-motion aware — shown for a
> **hardcoded `delay(1100)`**. There is **no `androidx.core:core-splashscreen` integration
> and no branded window theme**, so the real first frame is an unbranded
> `windowBackground=@color/night`, then Compose draws its own `Splash()` (a double-splash on
> a fixed timer).

---

## A. Cold-start & system integration — the biggest gaps

1. **Adopt the AndroidX Core SplashScreen API.** Add `androidx.core:core-splashscreen` and
   call `installSplashScreen()` first in `MainActivity.onCreate` so the OS-drawn splash IS
   the brand from frame zero — killing the unbranded `windowBackground` → Compose-`Splash()`
   flash.
2. **Put the mark in the system splash.** `windowSplashScreenAnimatedIcon` (an animated
   vector of the C-ring orb) + `windowSplashScreenBackground=@color/night`, so there is ONE
   continuous brand moment, not two different logos back to back.
3. **Gate on readiness, not a timer.** `splashScreen.setKeepOnScreenCondition { !ready }` —
   hold only until session restore + first data land. No artificial `delay(1100)`.
4. **`postSplashScreenTheme`.** Swap to the app theme exactly at handoff so there's no color
   pop between the window background and the first composition.
5. **Choreograph the exit.** `setOnExitAnimationListener` hands the OS icon off to the
   Compose scene with a matched transform, so the system splash and the app's first frame
   read as one motion instead of a cut.

## B. Timing & readiness

6. **Readiness signal.** Expose a `StateFlow<Boolean> ready` that flips when `Session.init` +
   token restore + theme resolution complete; dismiss on it, clamped to a **min ~450 ms**
   (never a flicker) and a **max ~2 s failsafe** (never a trap).
7. **Warm the critical path under the splash.** Kick off `Session.restore()`, `/billing/me`,
   and first-screen prefetch in parallel while the splash covers them — the first real frame
   arrives already populated, no post-splash spinner.
8. **Skip on warm start / config change.** Show the splash only on a true cold launch
   (`savedInstanceState == null`); a brand moment on every rotation is friction.
9. **First-run vs returning.** A slightly warmer, one-beat-longer first-ever launch (a single
   value line) vs a near-instant returning-user flash.

## C. Motion & choreography

10. **Physically-based settle.** Drive the arrival with a spring / Material 3 `MotionScheme`
    rather than `tween(900)` — springs read more premium and survive interruption gracefully.
11. **Gyro parallax.** Aurora ribbons and starfield drift at different depths to a low-passed
    accelerometer signal for a living, 3-D sky (gated by reduce-motion + battery saver).
12. **Waiting "breath".** If init out-runs the min duration, the orb does one slow
    inhale/exhale (scale 1.0↔1.03) — "alive, loading" without a spinner.
13. **Shared-element exit.** The orb rises and the wordmark trails, cross-dissolving into the
    Welcome/home orb (a shared element) so onboarding feels like a continuation.
14. **Per-star twinkle.** Give each star its own phase offset instead of one global `appear`
    alpha, so the sky lives rather than fading in as a single sheet.

## D. Brand & visual craft

15. **Ribbons that glow, not paint.** Render the aurora with an additive/soft-light blend and
    a faint blur so it reads as light; the current flat `Stroke` reads as ribbons of paint.
16. **Anti-banding grain.** A 2–3 % animated noise overlay kills gradient banding on OLED and
    gives the night sky texture.
17. **Depth / bokeh.** One or two large, blurred out-of-focus orbs behind the mark for
    cinematic layering.
18. **Wordmark glint.** Animate a single light sweep across "Bro" once, instead of a static
    gradient — a signature micro-moment.
19. **True-black OLED variant.** Offer a pure-black night gradient for AMOLED (power + contrast),
    selected by the OS.
20. **One logo source of truth.** Ship the mark as an animated vector (AVD) shared by the
    system splash and Compose, so the logo animation lives in exactly one place.

## E. Personalization & delight

21. **Time-of-day sky.** Warmer dawn hues in the morning, deep indigo at night — computed from
    the local hour; reinforces the "daily companion" positioning at ~zero cost.
22. **Earned greeting.** A returning user sees a one-word "Welcome back" under the mark that
    the first-run user does not.
23. **Arrival haptic (opt-in).** One soft `Haptics` tick as the orb blooms, matched to the
    visual; off by default, respects system haptics.
24. **Arrival chime (opt-in, DND-safe).** A single soft tone at arrival — premium, but strictly
    opt-in and silenced by ring/DND so it can never surprise.

## F. Performance

25. **Never block the main thread.** Run `Session.init` / `Haptics.init` off the UI thread with
    the splash covering the work, so cold start feels instant.
26. **Budget the Canvas.** Hoist the per-frame `Path()` allocation out of the drift animation,
    use `drawWithCache`, and cap star/ribbon counts — no allocations in the draw loop.
27. **Instrument cold start.** Track Time-to-Initial-Display and call `reportFullyDrawn()` for
    Time-to-Full-Display, so perceived speed is measured, not guessed.
28. **Pre-load first-frame assets.** Warm fonts/vectors used by the first real screen during the
    splash to avoid FOUT / icon-pop on handoff.

## G. Accessibility & inclusivity

29. **Announce it.** A `contentDescription` / live-region ("CereBro, loading") so TalkBack users
    get a spoken brand moment, not silence.
30. **Honor every motion setting.** Reduce-motion (already partial) AND animator-duration-scale
    AND battery-saver → static final frame with a gentle cross-fade, never a hard cut.
31. **Contrast floor.** The wordmark/greeting must meet contrast on the darkest gradient stop;
    don't rely on the gradient text alone for legibility.
32. **Token-driven, not hex.** The splash hard-codes colors (`0x668A7BF0`, …); move them to
    theme tokens so brand changes propagate and a high-contrast/light variant is possible.

## H. Robustness & states

33. **Offline at launch.** If the backend is unreachable, after the min duration proceed to a
    cached/offline state — never hold the user on the brand screen waiting for a network.
34. **Hung-init failsafe.** A hard max-timeout force-dismisses the splash and logs it, so a
    stuck init can never leave someone staring at the logo.
35. **Process-death restore.** If the OS restarts into a deep screen, restore state and show at
    most a minimal flash — don't replay the full brand sequence.

## I. Quality & instrumentation

36. **Lock it with tests.** A golden/screenshot test (Paparazzi/Roborazzi) of the final frame so
    the brand moment can't silently regress, plus unit tests of the readiness-gate logic
    (the `splashOrbScale` / `splashGlowBloom` / `splashWordmarkAppear` curves are already
    pure and testable).

---

### Suggested build order (highest impact first)

1. **#1–#5 + #3/#6** — SplashScreen API + readiness gate: fixes the double-splash and the
   arbitrary timer. This is the single biggest state-of-the-art jump.
2. **#7, #25, #26, #27** — warm the path + perf/instrumentation: makes launch genuinely fast,
   not just pretty.
3. **#13, #10, #14, #18** — the choreography and exit handoff: the "premium" feel.
4. **#21, #22, #29, #30, #32** — personalization + accessibility + tokens: the finishing craft.
