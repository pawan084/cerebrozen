# Audit L — "App is cluttered" feedback review (2026-08-15)

**Trigger:** first outside-tester feedback (Deepak, 2026-08-15, real device over Wi-Fi):
the app is cluttered; a distressed person should not be given this much to read; it
should be self-intuitive; nothing repetitive or over-verbose; and **launch early,
iterate after**. This audit checked all four points against the Android codebase
(4 parallel deep passes: copy, Today+navigation, onboarding, secondary screens).

**Verdict: the feedback is correct, and the code quantifies it.** The house rules
(one primary action, one implementation per behavior, one-line provenance,
lowercase-calm copy) are written down and repeatedly violated by accretion.
Several real defects — two of them DPDP-relevant — were found in the same pass.

---

## 1. The numbers behind "cluttered"

### First run (cold install → usable Today)
- **13 screens, ≈610 words, 14 taps** (15 on Android 13+ with the notification
  dialog) before Today is interactive — while the Welcome screen promises
  "About 2 minutes". Funnel is an 11-step state machine (`OnboardingScreen.kt:174-185`,
  steps rendered `:476-775`) + the 4-stop Guided Tour modal (118 words,
  `GuidedTour.kt:65-114`) landing the moment the user reaches Today.
- Only 3 steps are load-bearing: Disclosure/age (legal), Consent (DPDP), the
  guest/account branch. Language is auto-detected already (`:1094-1110`); the state
  check-in duplicates Today's own tiles; the Reset breathing step is a feature
  staged as setup; Reflection's answer is **discarded** (`:662` — `onClick = { next() }`);
  Ready (`:762-775`) is a pure interstitial whose copy promises "not a wall of
  features" on page 11 of 11.
- Intro (ONB-02) is the wordiest screen (62 words) and its three feature cards all
  just call `next()` (`:489-491`) — a brochure disguised as navigation.

### Today (the screen Deepak saw first)
- Worst-case **22 stacked blocks ≈ 3.5–4 screenfuls, 32 tappable CTAs, ~140 words
  of running prose** (`TodayScreen.kt:1129-1936`).
- The 6-tile mood grid is 380dp (`:754` min 120dp × 3 rows) vs the hero's 210dp —
  the "deliberately quieter" block (comment `:1455`) visually outweighs the primary 1.8:1.
- The hero carries **two** full-width CTAs (`:1439`, `:1441-1451`); the primary is an
  off-system `ReferenceAction` with hardcoded plum `Color(0xFF7B376E)`, no
  `Role.Button`, no haptics (`:1940-1948`) instead of house `PrimaryButton`.
- Fold "Your day" defaults **open** (`:887`) and contains a browse `ContentRail`
  (`:1776-1782`) on a screen whose lede promises "without browsing the whole product"
  (`strings.xml:1943`). Fold "This week" holds 8 sub-blocks incl. **two doors to the
  same Insights screen** (`:1826` and `:1919`).
- "Your next helpful step" prints twice on one screen (top-bar subtitle
  `strings.xml:11` + hero eyebrow `:1944`). An empty subtitle `""` is still rendered
  (`:1483`, `strings.xml:375`). "Add intensity or a private note →" is a hardcoded
  English literal (`:1538`).
- **Sleep — the documented flagship — has no permanent Today entry point.** The
  "Tonight" fold was dropped but its KDoc (`:860-875`), section comment (`:1786-1791`)
  and all five `today_fold_tonight*` strings (`strings.xml:1966-1970`) survive, dead.

### Copy corpus
- **1,891 strings, ≈10,300 words**; 28 strings >25 words; the four longest are 49/44/41/41
  words (`journal_private_body:593`, `privacypolicy_private_body:1102`,
  `imagery_intro:1513`, `builder_cue_why:1554`).
- Zero exclamation marks, zero guilt language — the tone rules held. What failed is
  **volume and repetition**, not tone.

### Repetition (the "repetitive/over-verbose" point, verified)
- "Not medical care" restated **12×** in resources + 4 more hardcoded in Kotlin.
- "No streaks / blank days are blank" said **6×** (`habits_body`, `today_day_blank`,
  `today_hero_done_why`, `ob_notify_sub`, `programs_path_sub`, `trends_why`).
- 28 `*_why` provenance strings at 15 call sites; the same claim duplicated:
  slow-exhale ×4, CBT-I ×4, expressive-writing ×4, gratitude ×4, implementation-
  intentions ×3 — several citing author-year-journal in shipped UI (house rule: one line).
- **16 dead `urgent_*` strings are byte-identical twins of `crisis_*`**
  (`strings.xml:151-166`, zero Kotlin references) — a fully duplicated crisis block.
- "Urgent support" exists under 6 keys; "Done" under 5; "One good thing" under 5;
  plus 8 more exact-dupe pairs (`sounds_recent_chip`/`toolkit_recent_chip`,
  `today_milestone*`, `today_checkin_queued`/`journal_entry_queued`,
  `talk_hint_orb*`, 4 `companion_*` twins).
- Guest sign-in wall: same action on **12 surfaces in 4 different visual forms**;
  hardcoded title "Want to keep your progress?" not overridable (`Common.kt:1097`);
  Today can show the card **twice in one scroll** (`TodayScreen.kt:1211` + `:2307`).

### Duplicate implementations (one-per-behavior rule vs reality)
- **Breathing: 6 implementations** (`BreathLoopsScreen`, `BreathingScreen`/
  `BreatheEngine` 644 lines, `PracticeBreathingScreen` prep, ritual `BreatheStep`,
  game `breathing-rhythm`, TIPP step 3) behind **6 doors**.
- **Gratitude: 5** (incl. `GratitudeGardenScreen` — **orphaned, no route**,
  `Games.kt:347`; and the Toolkit "Gratitude garden" card `Extras.kt:2258` that
  actually opens the plain text-box screen — the door lies).
- **Grounding: 4. Crisis screens: 2** (`UrgentSupportScreen` + onboarding-only
  `CrisisScreen` `Extras.kt:2782`). **Simon-type games: 2. Tap-a-calm-thing games: 3.**
- **Insights: 5 similarly-named analytics surfaces** (Insights / Weekly insights /
  Trends / Patterns / Sleep insights); the Insights screen lists the same four
  destinations twice (pills `:2272-2292` + rows `:2340-2343`); a second 180-line
  `InsightsScreen` (`Extras.kt:585`) is unrouted dead code.
- **67 routes ≈ 55 user-facing features**; aliases (`plan`/`dailyplan`,
  `talk`×3, `breathe/box|reset`) and code-acknowledged orphans (`breathing`,
  `onegoodthing`, `intention`, `guidedimagery` comment `CereBroApp.kt:776-783`).
- Two practice libraries with overlapping contents (Explore→`practice-library`
  7 families vs Toolkit 15 cards) under two different names for the same route
  ("Toolkit" vs "Mindful activities").

### Worst screens by words-per-useful-action
SleepScreen (14 blocks, ~35 tappables, **3 competing primaries** — `:511` play,
`:650` save night, `:928` ritual) · SafetyPlanScreen (**7 Save buttons** + Done,
32-word intro) · ToolkitScreen (15 co-equal doors, 0 state-changing actions) ·
YouScreen (23 equal rows) · PrivacyPolicyScreen (~210 words, 0 actions).
Best in class: PlanScreen (one primary, ~35 words) — proof the pattern exists.

---

## 2. Defects found in the same pass (fix regardless of design taste)

1. **Consent toggles pre-ticked** — `OnboardingScreen.kt:417-425`: comment says
   "NOTHING pre-ticked — consent must be an action"; code ships `mood_history=true,
   ai_memory=true`. Contradicts the design rule and DPDP "specific and informed".
2. **3 of 6 consent categories shown, all 6 POSTed** — rows render mood/ai/journal
   (`:685-689`) while `:805` serialises `sleep_history`, `voice_storage`,
   `model_training` the user never saw. DPDP-relevant.
3. **Onboarding state tiles write the wrong mood** — tile labelled "Clear / I feel
   steady" writes `mood="Anxious", goal="Reduce stress"` (`:263` vs
   `strings.xml:1629,1635`); "distant"→label "Overwhelmed" writes mood "Low".
   First data written to a new profile is wrong.
4. **Reflection step claims its answer matters, then discards it** (`:645-665`,
   `strings.xml:2013`).
5. **"Custom time" reminder is not custom** — fixed 21:00, same as "evening", no
   picker (`:317-323`). Dead identical branch `ob_notify_sub(_skipped)` (`:713`).
6. **Crisis shield is not position-constant** — Talk uses `Page` which has no
   `onUrgent` slot (`TalkScreen.kt:722`, `Common.kt:711-728`); Journal has the
   param available and unpassed (`JournalScreen.kt:323`); a running breathing
   session (`Breathe.kt:514`) has **no urgent door at all**. ≤2-taps technically
   holds via tab-hop, but the "same pixels on every screen" property is broken.
7. **Localization holes invisible to values-hi**: the entire Sleep-insights screen
   is hardcoded English (`TodayScreen.kt:2380-2480`, 34 literals incl. a duplicated
   error string on consecutive lines); `ConsentNotice.kt:46-203` hardcodes 13
   languages as Kotlin literals; a **safety warning** is English-only
   (`PracticeLibraryScreen.kt:209`). values-hi is missing **760 keys** overall.
8. Dead code/strings: `today_fold_tonight*` ×5, `today_quick_access_*`, 16
   `urgent_*`, `InsightsScreen`, `GratitudeGardenScreen`, `Funnel(progress)` dead
   param (`OnboardingScreen.kt:976,990`).

---

## 3. Recommended fix waves (each independently shippable)

**Wave L1 — correctness & safety (small, do first):** consent defaults to false +
show all 6 categories (or POST only the 3 shown); fix state-tile mood mapping;
delete `CrisisScreen` + 16 `urgent_*` dupes (point onboarding at
`UrgentSupportScreen`); pass `onUrgent` on Talk/Journal frames + add an urgent door
to the breathe session; fix/remove "Custom time"; delete the Reflection step (its
data is discarded anyway).

**Wave L2 — onboarding cut 13→8:** also delete Ready (fold `continueAsGuest` into
the Guest CTA), collapse Intro to one line or delete (tour re-tells it), defer
Reminders + the system permission dialog to after the first real check-in, replace
the 4-stop tour with one inline hint on the check-in row.
Net: **14 taps → 9, ≈610 → ≈365 words, 13 → 8 screens.**

**Wave L3 — Today declutter:** mood grid to compact tiles (reclaim ~250dp); hero
gets ONE CTA and becomes house `PrimaryButton`; remove `ContentRail` from Today;
fold-day defaults closed (or dissolve the fold); fold-week collapses to presence
ring + one door (kill the duplicate insights door); remove the bell (You→Inbox owns
it); stop rendering the empty subtitle; localize the note-link literal; decide the
"Tonight" fold — restore it as Sleep's permanent door or delete its dead strings
and give Sleep a stable door another way. (Overlaps remaining Audit-K smalls.)

**Wave L4 — copy diet:** every `WhyThisWorks` ≤1 line, citations dropped, 4
duplicate claims → 4 canonical lines; "not medical care" 12→2 surfaces (onboarding
+ Talk pill); "no streaks" 6→1; 4 longest strings rewritten ≤15 words; error
bodies → one sentence + Retry; expand-or-drop CBT-I/CBT/DBT/MBCT/TIPP acronyms;
delete exact-dupe keys.

**Wave L5 — IA consolidation (the deep de-clutter):** one breathing screen (retire
`BreatheEngine` stack + prep interstitial), one gratitude (route or delete the
garden; fix the lying Toolkit card), one Simon game, Toolkit 15→~6 doors, You
23→~10 rows, SleepScreen split into "tonight" vs "your sleep", SafetyPlan single
save, unify the guest wall into one shape with per-surface copy, kill route aliases
+ orphans, one name per destination ("Toolkit" vs "Mindful activities").

**Wave L6 — localization:** Sleep-insights + ConsentNotice + stray literals into
resources; close the values-hi gap for every string the waves above touch.

---

## 4. "LAUNCH THE APP" — where that actually stands

Deepak's launch instinct matches the docs: **no app-code blockers remain** for a
Play internal-testing track (`docs/ANDROID_RELEASE.md`: release build green,
16.6 MB, R8 on, HTTPS-only, signing config wired). What's left is owner/external:

1. Upload keystore (backed up twice) + Play Console account ($25).
2. Production backend deploy per `docs/RELEASE_PLAN.md` (VPS phases 0–3) — the
   Release build points at `https://api.cerebrozen.in`.
3. Play listing: data-safety form, **Health Connect READ_SLEEP declaration** (known
   first-submission delay), account-deletion URL (page written, needs web deploy),
   feature graphic 1024×500 + screenshots. Copy drafted in `apps/android/playstore/`.
4. Play Billing client is **not built** — launch free/guest-first without it; the
   Premium screen already degrades honestly.

**Sequencing that serves both of Deepak's points:** ship Wave L1 (correctness — the
consent issues should not reach the store) → cut a signed internal-testing build →
run L2–L4 (the clutter he actually complained about) during the internal-test/
review window → promote to production with the calmer app.
