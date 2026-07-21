# Home screen ("Today") — world-class spec (Android)

Target: turn Today from "a fixed list of cards" into a **living, relevance-ordered, brand-
continuous** home that carries the splash's craft (readiness-gating, time-of-day sky,
choreographed entrance) all the way through the first real screen.

> **All 32 points implemented 2026-07-20.** Full unit suite green, JaCoCo **96.21%**
> (gate 95%), debug + release (R8 + lintVital) builds both succeed. **Re-verified
> 2026-07-21 on the current tree: 38 suites · 289 tests · 0 failures · 96.21%; release
> R8 + lintVital green.** **Not yet device-verified** — no physical device was connected
> that session; the checklist for what to confirm on reconnect lives in
> [ANDROID_QA.md](ANDROID_QA.md) §2, with the rest of the app's device-verification state,
> rather than in a second copy here. One real bug
> was caught and fixed by the new tests before it could ship: `buildDoors`'s
> `.sortedByDescending` was silently a no-op due to Kotlin operator precedence (`.` binds
> tighter than `+`, so it sorted only the trailing one-item list) — the whole relevance-
> ordering feature (#1, #3, #4) would have shipped visually unsorted. Caught by
> `CoachHomeTest`, fixed by assigning the concatenation to a variable before sorting it.
>
> **#27 (Material You dynamic color) was deliberately NOT implemented.**
> `ui/screens/Settings.kt`'s `AppearanceScreen` documents an explicit 2026-07-15 owner
> decision: CereBroZen is **dark-only**, "one calm indigo theme on every screen," and
> light/variable options were removed because they'd "reintroduce the very inconsistency we
> removed." Wallpaper-driven dynamic color is exactly that inconsistency — implementing it
> would contradict a documented product decision, so it's recorded here as a considered
> skip, not an oversight.
>
> **Shipped, by batch** (all in `apps/android/.../net/HomeCache.kt` (new),
> `ui/screens/CoachHome.kt`, `ui/screens/CheckIn.kt`, `ui/screens/Common.kt`,
> `MainActivity.kt`, `ui/PendingRoute.kt` (new), `widget/HomeWidget.kt` (new),
> `notify/Reminders.kt`, `AndroidManifest.xml`, `res/xml/shortcuts.xml` (new),
> `res/xml/home_widget_info.xml` (new)):
>
> - **A (#11–#16, perceived performance):** `HomeCache` warms name/moods/streak/active-
>   program/resumable CONCURRENTLY during the splash's `Session.warmBoot()`; `TodayHome`/
>   `CheckInCard` read it first and only cold-fetch if empty. `PageHeader`'s title crossfades
>   on change (kills the name pop-in relayout). `CheckInSkeleton` shimmer while consent
>   resolves. `PullToRefreshBox` re-warms the whole cache. An inline retry banner when every
>   call fails and nothing is cached.
> - **B (#7–#10, brand continuity):** `Page`/`PageHeader` gained an additive `leadingMark`
>   param — a small `BrandMark` beside Home's eyebrow, the same mark the splash arrives on.
>   `homeSkyBrush` tints Today's background by hour, mirroring the splash's sky. `PresenceOrb`
>   takes a `done` param — a calmer, gold-shifted breath once today is checked in
>   (`HomeCache.markCheckedInToday()` updates it optimistically, no waiting for the next
>   warm). `StaggerItem` gives the check-in/focus/door cards a ~45ms-apart cascade instead of
>   one shared fade.
> - **C (#1–#6, relevance ordering):** `Door`/`buildDoors` (both `internal`, pure, unit-
>   tested) replace the hardcoded card sequence — Commitments rises with open count, Journeys
>   carries "Day X of Y" and outranks the generic doors once a program is active, Wind-down
>   is promoted (not appended) in the evening window, Need-a-human rises only on an ordering
>   signal (open ≥ 3), never styling. A one-time dismissable intro card on the first-ever
>   visit (`home_intro_seen` pref).
> - **D (#22–#25, OS integration):** `res/xml/shortcuts.xml` — two launcher app-shortcuts
>   (Talk it through / Breathe) via `ui/PendingRoute.kt`, consumed once by the NavHost;
>   `MainActivity` is now `singleTask` so a warm re-tap delivers through `onNewIntent`. The
>   daily reminder (`notify/Reminders.kt`) deep-links straight to Actions instead of a bare
>   app-open. A real Glance home-screen widget (`widget/HomeWidget.kt`) — deliberately
>   action-only (Talk it through/Breathe), NOT a live mirror of the in-app streak/mood state;
>   see the widget file's docstring for why that's the honest scoping call, not a shortcut.
>   `HomeCache.resumable` (warmed via the existing `/v1/sessions/resumable`) changes the
>   FocusCard's copy/button to "Continue talking" when there's a session to return to.
> - **E (#17–#21, progress & delight):** `MonthDensity` — a 30-tick strip beneath the week
>   ring once a streak passes 7 days. `milestoneTint` — a quiet warmth shift at 7/30/100
>   days, the same dot, never a badge. Mood chips now vary haptic softness by intensity. A
>   scroll-edge fade hints there's a mood chip off-screen. `DoorCard`'s count-bearing
>   description crossfades on change instead of hard-swapping.
> - **F (#26–#28, visual craft):** Rest & recovery and Wind-down got their OWN accent
>   (deepened cyan / periwinkle-toward-violet) instead of colliding with Reset toolkit and
>   Journeys. A 2-up grid on tablets/unfolded foldables (`LocalConfiguration` width ≥ 600dp).
>   #27 skipped — see above.
> - **G (#29–#30, accessibility + landscape):** `DoorCard`'s row merges its title+description
>   into one TalkBack announcement; the chevron is now `clearAndSetSemantics` (purely
>   decorative, no longer read as a bare glyph). #30 (landscape) needs the physical device to
>   actually confirm — nothing crashes in the composable structure, but it hasn't been seen.
> - **H (#31–#32, tests + golden):** `CoachHomeTest` (13 tests) locks `buildDoors`/
>   `presenceLines`' assembly rules — this is what caught the sort-precedence bug.
>   `TodayHomeGoldenTest` (2 Robolectric Compose tests) renders the fully-assembled screen for
>   a signed-in mid-streak account and for an every-call-failed account, asserting the real
>   composable tree (not just the pure functions) holds together.
>
> **Device checklist** → [ANDROID_QA.md](ANDROID_QA.md) §2. Ten items, none seen on a real
> screen yet; the launcher shortcuts (cold **and** warm app-state) and the pinned widget are
> the two worth doing first, because they are the only points here whose behaviour lives
> entirely in the OS.

**The state this spec was written against** — i.e. what Home looked like *before* the work
above, kept as the baseline the 32 points argue from
(`apps/android/.../ui/screens/CoachHome.kt` + `CheckIn.kt`):
`TodayHome` = a time/name-aware greeting (`presenceLines`) → a coral `PresenceOrb` (breathing
scale) → `CheckInCard` (mood chips + a 7-day `WeekRing`) → `FocusCard` (orb + line + "Talk it
through"/"Breathe") → a **fixed-order** stack of `DoorCard`s (Commitments, Journeys, Reset
toolkit, Rest & recovery, conditionally Wind-down after 8pm, Need a human). `Page` gives the
whole column one shared fade/rise on entry. `CheckInCard` is consent-gated (absent, not
disabled, when declined) and pulls `Api.moods()`/`Api.streak()` itself, independently of
`TodayHome`'s own `Api.me()` call — three uncoordinated network calls feeding one screen.

---

## A. Personalization & relevance — the biggest gap

1. **Cards never reorder.** The DoorCard stack is hardcoded (Commitments → Journeys → Reset →
   Rest → [Wind-down] → Human). A person who's never opened Journeys sees it ranked above
   Rest & recovery every single day, forever. Rank by recency/frequency of use, not source order.
2. **No "continue" surfacing for an active program.** Once someone enrolls in a Journey
   (`programs_limit`, backlog #48), Home gives no hint — Journeys reads the same generic
   "feedback, delegation, influence" copy whether they're on Day 1 or Day 6 of 7.
3. **Wind-down is appended, not promoted.** The `hour >= 20 || hour < 3` card is tacked onto
   the *bottom* of the stack instead of rising near the top when it's actually the most
   relevant door in the house.
4. **The "Need a human?" door never reorders.** It's always last, fixed `Warm` accent — a
   calm, ambient safety door is right, but it should rise in *position* (not urgency-styling)
   after signals like a broken streak or several unresolved commitments.
5. **DoorCard copy is static marketing text**, not state-aware. "Multi-week practice for the
   skills that used to take a decade" never becomes "Day 3 of 7 — The Feedback Conversation."
6. **No first-visit moment.** A brand-new signer and a 200-day veteran get the structurally
   identical screen — no one-time "This is your Today" orientation beat.

## B. Brand continuity with the (newly rebuilt) splash

7. **Two competing orb identities.** The splash's `BrandMark` (periwinkle→cyan C-ring) and
   Home's `PresenceOrb` (coral radial) don't read as the same brand object — the continuity
   the splash rebuild just earned stops at the front door.
8. **No time-of-day sky on Home.** The splash now tints its gradient/aurora by hour (dawn
   rose / day periwinkle / dusk violet / deep night — shipped this session); Home's
   background is flat and untouched by the same signal, so the brand moment doesn't survive
   past launch.
9. **The orb never signals "done."** `PresenceOrb`'s breathing scale is identical whether
   today is checked in or not — no calm visual tell (a held stillness, a warmer tone)
   distinguishing "already showed up today" from "not yet."
10. **No entrance choreography — the whole page moves as one slab.** `Page`'s single shared
    `rise` animates orb, check-in, focus, and every door together; a light stagger (orb →
    check-in → doors, ~40ms apart) would read as considered rather than dumped.

## C. Loading & perceived performance

11. **The name pop-in causes a visible relayout.** `userName` resolves asynchronously
    (`Api.me()`), so the header renders "Good afternoon" then jumps to "Good afternoon,
    Alex" a beat later — reserve the space or fade the name in rather than reflowing the title.
12. **`CheckInCard` renders nothing, then appears whole.** No skeleton while consent + moods +
    streak resolve — the page grows once data lands instead of holding a placeholder shape.
13. **Three independent, uncoordinated network calls feed one screen** (`TodayHome`'s
    `Api.me()`, `CheckInCard`'s `Api.moods()` + `Api.streak()`) — nothing batches or races
    them, so Home visibly assembles piece by piece.
14. **None of this is warmed by the splash.** The new `Session.warmBoot()` (shipped this
    session) only refreshes billing; Home's own data still cold-loads *after* the splash
    hands off. The first real frame is not actually fully populated.
15. **No pull-to-refresh.** If a commitment or mood was added elsewhere (web/another device),
    Today has no way to force a resync short of leaving and re-entering the tab.
16. **No offline/error state specific to Home.** If `Api.me()`/`moods()`/`streak()` fail, the
    screen just keeps its defaults (blank name, empty ring) silently — no retry affordance,
    unlike the `Failed`/`Skeleton` pattern used elsewhere in the app.

## D. Progress & delight (calm, not gamified — matching the file's own stated intent)

17. **The 7-dot `WeekRing` has no longer view.** A 40-day streak has no way to see its own
    shape beyond a text line ("40 days in a row") — a subtle month-density view would extend
    the "mirror, not scorecard" idea the code already commits to.
18. **No milestone acknowledgment at all.** The header explicitly rejects confetti/badges —
    right call — but there's room for one quiet, non-congratulatory signal (a slightly deeper
    ring color at 7/30/100) that still honors "shown calmly."
19. **Mood chips give identical haptic feedback for every option.** "Good" and "Rough" both
    fire the same `Haptics.selection()` — a marginally softer tap for heavier moods would be a
    small, real piece of craft.
20. **No horizontal-scroll affordance on the mood row.** Four chips scroll with no edge fade
    or hint, so "Rough" can sit off-screen on narrower phones with no visual cue it's there.
21. **Open-commitment counts don't animate.** `DoorCard`'s "1 open commitment" swaps to "2 open
    commitments" (or back to "Nothing open") with a hard text-swap, not a counted transition.

## E. OS integration — the richest work is trapped inside the app

22. **No home-screen widget** (Glance) surfacing today's streak ring, the mood chips, or a
    one-tap "Talk it through" — all of this personalization currently requires opening the app.
23. **No launcher app-shortcuts** (long-press the icon) for the two highest-frequency actions —
    "Talk it through" and "Breathe" — a zero-navigation entry point costs nothing to add.
24. **Notification → Home wiring is unverified.** The onboarding pre-permission gate promises
    "Tuesday's commitment — how did it go? Two minutes to close the loop," but nothing tests
    that tapping it actually deep-links into Actions on that specific commitment.
25. **No "resume where you left off."** If a coaching turn was left mid-session, Home always
    starts fresh rather than offering a direct continue.

## F. Visual craft

26. **DoorCard accents are hardcoded, not semantic.** `Cyan` is reused for both Reset toolkit
    and Rest & recovery; `Periwinkle` for both Journeys and Wind-down — two pairs of cards
    are only distinguishable by icon, not by their supposedly distinct accent color.
27. **No dynamic color (Material You).** The palette is always the fixed brand set regardless
    of the OS wallpaper-derived theme — an easy, opt-in modern-Android signal currently unused.
28. **No adaptive/large-screen layout.** DoorCards are a single fixed-width column regardless
    of available width — a tablet or unfolded foldable gets a stretched column instead of a
    2-up grid.

## G. Accessibility & robustness

29. **`DoorCard`'s "›" chevron is a bare `Text`, not a semantic affordance** — screen readers
    hear the raw glyph rather than a meaningful "opens X" cue layered onto the row.
30. **No landscape verification.** Nothing in the file suggests Home has been checked in
    landscape, where the FocusCard's centered orb + two-button row may crowd.

## H. Quality & instrumentation

31. **`TodayHome`'s assembly rules are untested.** `presenceLines`/`checkInDates`/`weekPresence`
    (the pure functions) have coverage (`CheckInTest.kt`), but the composable's own business
    rules — which doors appear, the `hour >= 20 || hour < 3` Wind-down gate, ordering — are
    locked nowhere. A refactor could silently drop a door and nothing would fail.
32. **No golden/screenshot test of the assembled Home** for a signed-in, consented, mid-streak
    account — the one screen every session starts on has no regression net at all.

---

### Suggested build order (highest impact first)

1. **#11–#16** — perceived-performance cluster: fix the pop-in, add a skeleton, coordinate the
   three network calls, and actually warm Home's data during the splash's `warmBoot()`. This
   is the direct sequel to the splash work — "the brand arrives fully formed" should mean the
   *screen after it* too.
2. **#7–#10** — brand continuity: unify the orb identity, carry the time-of-day sky through,
   give the orb a "done" state, stagger the entrance.
3. **#1–#6** — relevance ordering: this is the single biggest state-of-the-art jump for Home,
   parallel to what the SplashScreen API was for launch.
4. **#22–#25** — OS integration (widget + shortcuts): the highest-leverage, lowest-risk
   addition — turns existing personalization into a zero-navigation entry point.
5. **#17–#21, #26–#32** — the finishing craft and quality net.
