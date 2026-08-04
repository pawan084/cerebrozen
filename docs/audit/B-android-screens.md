# Audit B — Android sub-screens: defects & polish

Scope: pushed sub-screens (Player, Plan, Goals, Insights, Trends, Patterns, Crisis,
Safety plan, Settings screens, Programs, Search, Onboarding, Baseline, tool screens,
Breathe, Rituals, Games/Toolkit activities), `ui/games/`, `ui/offline/`,
`ui/breathing/`, the `audio/` engine classes and `net/Session.kt`.
Excluded per brief: the five tab screens and missing-CTA/dead-end findings (covered
by the separate CTA audit). Every claim was checked against the current code.
Paths are relative to `apps/android/app/src/main/java/com/cerebrozen/app/`.

Note on config-change claims: the activity is portrait-locked
(`AndroidManifest.xml: screenOrientation="portrait"`), so "recreation" below means
dark/light theme switch, locale change, split-screen/desktop-window resize, or
process death — all of which recreate the Activity and wipe plain `remember {}`.

## State bugs & logic errors

1. [BUG] GroundingScreen keeps `step`/`done` in plain `remember` — a theme switch, window resize or process death mid-exercise silently restarts the 5-4-3-2-1 practice at step 1; the sibling wind-down ritual explicitly uses `rememberSaveable` "because losing your place is the worst moment for it" (Extras.kt:2394-2395).
2. [BUG] BaselineScreen `stress`/`sleep`/`saved` are plain `remember` — recreation wipes both picks on a two-question one-shot screen; `rememberSaveable` is the codebase's own stated pattern for funnel state (Baseline.kt:41-43).
3. [BUG] JournalingTool holds all typed CBT-reframe answers in `remember { mutableStateOf(List(...)) }` — four fields of unguarded personal writing are lost on any activity recreation (ToolScreens.kt:105).
4. [BUG] SafetyPlanScreen keeps all seven sections of unsaved plan text in plain `remember` (`values`) — recreation discards edits on the single screen where losing the user's words matters most; every ritual writing step nearby uses `rememberSaveable` (SafetyPlanScreen.kt:62).
5. [BUG] PlanScreen optimistic toggles race: each `Api.togglePlanStep` response calls `adopt()` which replaces the *entire* steps list, so toggling two steps quickly lets the first (stale) server payload arrive last and visually revert the second toggle; the failure branch also blindly flips `!done` even if the user re-toggled meanwhile (PlanScreen.kt:133-144).
6. [BUG] PatternScreen "Delete everything" success clears `learned` and `remembered` but leaves `suggestions` rendered — recommendations derived from the patterns the user just erased stay on screen claiming "because <pattern>" (PatternScreen.kt:313-317).
7. [BUG] Sequence/path recall games only test the FIRST cell: the prompt says "repeat the sequence" (`mg_repeat_sequence`) and the engine grows span 3→6 for "working memory", but the screen settles the round on one tap of `prompt.cells.first()` — a 6-item sequence is scored by remembering item one (GameSession.kt:442-444; GameEngine.kt:175-183, 203-216).
8. [BUG] BreathLoops CompletionScreen fires `Haptics.success()` *and* `Celebrations.trigger()`, and the Celebration overlay fires `Haptics.success()` itself — two success pulses for one completion, violating Haptics.kt's own rule "never fire it twice for the same moment" (BreathLoopsScreen.kt:434-437; Celebration.kt:92-93).
9. [BUG] BreatheEngine defaults `hapticsOn = true` / `chimeOn = false` and the ritual/tool hosts (BreatheStep, BreathingScreen, onboarding ResetStep) never pass the persisted `Chime.breatheHapticsEnabled` / `breatheChimeEnabled` — a user who turned breathe haptics off in BreatheScreen still gets pulsed every phase in the wind-down, ritual runner and onboarding (Breathe.kt:198-205; Rituals.kt:325-331; ToolScreens.kt:67; OnboardingScreen.kt:652-657).
10. [BUG] BubblePop under Reduce Motion: the spawn loop exits after seeding one static set, so popping all seven bubbles leaves the 430dp pool permanently empty until the user discovers the small Reset text button — "static, never blank" holds only until the first minute of play (Extras.kt:2286-2303).
11. [BUG] WritingStep surfaces `it.message ?: saveFailed` — the raw exception text, bypassing the `userMessage()` helper the codebase built precisely because this leaks "Failed to connect to localhost/127.0.0.1:8000" onto user-facing screens; every other save path uses `userMessage` (Rituals.kt:228; Common.kt:571-586).
12. [BUG] GoalsScreen habit day-dots label days with `day.dayOfWeek.name.take(1)` — the English enum initial ("M", "T"…) regardless of locale, on an app that ships Hindi/Urdu notices (GoalsScreen.kt:286).
13. [BUG] Trends `durationLabel` builds "6h 40m" from hardcoded English "h"/"m" literals — the one place duration copy is built is the one place it isn't localizable (TrendsScreen.kt:121-122).
14. [BUG] CrisisRegionScreen: a failed `Api.me()` read leaves `region = ""` which renders the "Auto-detect" row as *selected* — a failed read displayed as a confident, possibly false answer about crisis-resource routing, the exact "failed read as confident state" shape the codebase fixed three times elsewhere (Settings.kt:227-229).
15. [BUG] BreathingScreen/JournalingTool saves are double-fire-able: `saved` only flips on success, so rapid taps before the network returns queue multiple identical `Api.createJournal` writes (no in-flight guard, unlike SafetyPlan's `savingField`) (ToolScreens.kt:71-80, 151-165).
16. [BUG] InsightsScreen renders the baseline via `stressWords()[stress - 1]` / `sleepWords()[sleep - 1]` with no clamp — BaselineStore returns whatever int parses from the pref, so a stale/foreign/corrupt value outside 1..5 crashes Insights with IndexOutOfBounds; neither store nor reader validates (Extras.kt:529-534; Stores.kt:38-50).

## Failed reads rendered as confident empty states

17. [RISK] GoalsScreen initial load: both `runCatching { parseGoals(...) }` and `parseHabits(...)` have no failure branch — a network error renders the "No goals yet" / "no habits" empty copy with zero indication anything failed, the documented anti-pattern (GoalsScreen.kt:122-127, 162-164, 262-264).
18. [RISK] PatternScreen memories load failure sets `remembered = emptyList()` — the "Saved notes" section shows "nothing saved yet" after a failed read; `memoryError` is only set by *action* failures, never the load (PatternScreen.kt:118-120, 222-223).
19. [RISK] PatternScreen recommendations load failure silently collapses to `emptyList()` — the section just vanishes with no retry, inconsistent with the patterns card's explicit error + Try again (PatternScreen.kt:121-123).
20. [RISK] CrisisScreen trusted-contact read: `runCatching { Api.trustedContact() }` has no failure branch, so an offline open shows the "add one" empty subtitle even when a contact exists on the server — on the crisis surface specifically (Extras.kt:2538-2543).
21. [RISK] CompanionStyleScreen: failed `Api.me()` leaves `current = ""` — no row selected, no error shown, and any pick then writes over a server value the screen never actually learned (Settings.kt:175-177).
22. [RISK] SearchScreen partial failure is invisible: one kind failing silently narrows the pool (`loadError` only when all five kinds fail) — a user searching sleep stories while `/content?kind=sleep` 500s gets "no match" with no hint (SearchScreen.kt:60-78).

## Reduce Motion & animation clocks

23. [PERF] PremiumMixerSlider runs an infinite `gradientMotion` transition unconditionally — not gated by Reduce Motion and ticking even while the slider is idle; the Mixer pane hosts five of these, so five endless clocks animate a barely-perceptible gradient lerp forever (Extras.kt:1436-1445).
24. [PERF] MixerHeroCard's `glow` infinite transition runs under Reduce Motion — only the *read* is gated (`if (reduceMotion) 0.18f else glow`), so the composition never idles (Extras.kt:1188-1190, 1246).
25. [PERF] ToolkitScreen's ambient `glowY` transition runs always; Reduce Motion discards the value (`if (reduceMotion) 0f else glowY`) but the clock keeps recomposing the hub (Extras.kt:1932-1945).
26. [PERF] FeaturedGameCard's `drift`/`pulse` transitions run under Reduce Motion with values discarded at the read — same gate-the-read-not-the-clock bug, third instance in one file (Extras.kt:2195-2208, 2234-2242).
27. [PERF] ImmersiveBreatheFrame's background `drift` infinite transition never pauses under Reduce Motion (gated only inside the Canvas read) — the breathing screens burn an animation clock precisely for users who asked for stillness (Breathe.kt:469-475, 486-493).
28. [POLISH] BreatheWhyCard animates chevron rotation and `animateContentSize` without a Reduce Motion branch, while its sibling `WhyThisWorks` is deliberately unanimated "so it's the same with or without Reduce Motion" — the two provenance footers now disagree about their own design rule (Breathe.kt:612-638 vs Common.kt:1078-1082).

## Coroutines, timers, engines

29. [RISK] GuidedImageryScreen's countdown loop `while(true){ delay(1000); if (left>0) left-=1 else break }` only survives because the separate advance effect resets `left` within the same 1s tick — if that recomposition is ever delayed past the next tick the loop breaks and the reel silently stalls with no ticker to restart it (keys `started/done/paused` unchanged) (Rituals.kt:770-787).
30. [POLISH] BreatheEngine's pacer is keyed on `hapticsOn`/`chimeOn`, so toggling either mid-session cancels and restarts the tick coroutine — the current second stretches (fresh `delay(1_000)`) and `if (chimeOn) playBreathingCue(phases[phase])` replays a cue mid-phase (Breathe.kt:227-242).
31. [PERF] BubblePop drift loop rebuilds and re-filters the entire bubble list every 40ms on the UI thread (`bubbles.map{...}.filter{...}` at 25Hz), recomposing every bubble Box each pass — a frame-clock/graphicsLayer approach is the pattern ZenRipples already uses next door (Extras.kt:2305-2311 vs Games.kt:237-245).
32. [RISK] ToolAmbience.stop() from a disposing screen can kill the bed a newly-entered screen just started: during navigation the incoming screen's `ToolAmbience.start()` runs before the outgoing screen's `onDispose { ToolAmbience.stop() }`, and `stop()` tears down whatever player is current — safe today only because no two ambience screens are ever adjacent in the nav graph; nothing enforces that (ToolAmbienceUi.kt:39-45; ToolAmbience.kt:67-70).
33. [RISK] PremiumSleepTimerCard's `choose()` reaches a target by firing up to four `cycleTimer` service intents in a blind `repeat(5)` loop — each resets the service's fade state, and the whole scheme silently breaks if the cycle order (`0→15→30→45→60`) ever changes in one place but not the other (Extras.kt:1586-1594; SoundscapeMixer.kt:217-225).
34. [POLISH] BodyScanScreen auto-advance restarts the full `step.seconds` delay on every pause/resume or recomposition key change — pausing at second 14 of 15 replays the whole step wait (OfflineGuidanceScreens.kt:196-201).
35. [RISK] ContentList's `items`/`error` are `remember {}` without keying on `kind` — if a call site's `kind` ever becomes dynamic, the old kind's rows and a stale `error` persist across the change while `LaunchedEffect(kind)` refetches; latent trap the pure `contentListState` tests can't catch (Extras.kt:414-421).

## Edge cases & layout at 720px / large font

36. [RISK] TrendsScreen window picker is three PickChips in a plain `Row` — no `FlowRow`, no scroll; GoalsScreen documents the exact 720px/Hindi failure ("three labelled actions do not fit one 720px line") that this row reproduces at large font scale (TrendsScreen.kt:200-214 vs GoalsScreen.kt:179-186).
37. [RISK] Onboarding consent rows are fixed `height(64.dp)` with the hint at `maxLines = 1` + ellipsis — on the DPDP consent surface the explanation of what each switch collects truncates at large font or in longer locales; "specific and informed" copy that ends in "…" (OnboardingScreen.kt:472-487).
38. [RISK] StateOptionRow is fixed `height(55.dp)` — a long localized feeling label at large font clips instead of wrapping; DisclosureTile in the same file was converted to `heightIn(min)` for exactly this bug (OnboardingScreen.kt:786-798 vs 807-816).
39. [RISK] MindfulGames tiles fix `height(210.dp)` and the round card fixes `height(410.dp)` — name+description+practice rows and prompts/answers clip at large font scale rather than growing (MindfulGamesScreen.kt:98; GameSession.kt:189).
40. [RISK] MindfulGameScreen with an unknown/stale `gameId` returns before any chrome renders — a completely blank screen with no back affordance on a bad route or retired game id (GameSession.kt:85-86).
41. [POLISH] GratitudeGarden `plantFraction` is a mod-100 lattice — distinct entries can resolve to identical (x, y) and stack perfectly, hiding earlier flowers under later ones with no jitter or collision nudge (Games.kt:320-323, 382-399).
42. [POLISH] TippScreen `idx` is plain `remember` — the four-step walkthrough restarts at step 1 on recreation (its sibling WindDown uses `rememberSaveable` for the same shape) (ToolScreens.kt:236).
43. [POLISH] BodyScanStep (ritual variant) keeps `i` in plain `remember` while every surrounding step is `rememberSaveable` — the auto-advancing scan restarts on recreation (Rituals.kt:273).
44. [POLISH] PatternScreen edit state `editingId`/`editText` are plain `remember` — an in-progress memory edit is dropped on recreation (PatternScreen.kt:98-99).
45. [POLISH] PatternGlow `best`/`score`-adjacent state (`best`, `sequence`, `note`) is all plain `remember` — the "Best: N" claim resets on recreation mid-session (Games.kt:80-94).

## Accessibility

46. [A11Y] PremiumMixerSwitch is a 52×31dp tappable — below the 48dp floor — and its `clickable(role = Role.Switch)` carries no toggle-state semantics, so TalkBack announces a switch but never whether it's on or off (Extras.kt:1566-1583).
47. [A11Y] SleepTimerPill is a tappable pill roughly 26dp tall (labelSmall + 6dp vertical padding) — well under the 48dp target the same file enforces on the favourite button two hundred lines up (Extras.kt:1071-1089).
48. [A11Y] ChipWrap sets `Role.RadioButton` but never `selected` semantics — the onboarding language and reminder pickers, the privacy notice-language picker and both baseline scales all read as unlabeled-state radio buttons; TalkBack users can't hear which is chosen (OnboardingScreen.kt:836-861; used at Settings.kt:293, Baseline.kt:75-79).
49. [A11Y] BreathePaceControl: same gap — `Role.RadioButton` with no selected-state semantics on the Gentle/Classic/Slow segments (Breathe.kt:558-567).
50. [A11Y] BreatheSettingRow: the label Text and AppSwitch are unmerged siblings — the switch reaches TalkBack with no name at all ("switch, on"), while PlanScreen shows the correct row-level `toggleable` pattern (Breathe.kt:592-605 vs PlanScreen.kt:129-145).
51. [A11Y] Memory-game Grid cells are anonymous clickable Boxes — no contentDescription, no role, no position info — so the sequence/path/tray games are unusable with TalkBack, in contrast to PatternGlow's per-pad `patternglow_pad_cd` (GameSession.kt:474-510 vs Games.kt:178-193).
52. [A11Y] RitualBuilder reorder arrows are `IconButton`s forced to `size(36.dp)` — beneath the 48dp floor the same row's own comment block champions (Rituals.kt:630-641).
53. [A11Y] SafetyPlan text fields have no label or placeholder — the visible section title is a separate unassociated Text, so focusing a field announces only "edit box"; on this screen field identity is safety-relevant (SafetyPlanScreen.kt:167-178).
54. [A11Y] BubblePop bubbles are clickable Boxes with no semantics at all (and the pool container has no description either) — the featured Toolkit activity is invisible to screen readers; ZenRipples next door at least labels its canvas (Extras.kt:2335-2372 vs Games.kt:267-290).
55. [A11Y] PatternGlow pads stay enabled-announcing during the "watch" phase — taps are silently ignored (`if (showing) return`) but semantics never mark the pads disabled, so a TalkBack user gets no feedback about why activation does nothing (Games.kt:110-117, 176-193).
56. [A11Y] Sounds/Toolkit "pick up where you left off" chips reuse PickChip with `selected = false` — an action chip announcing "not selected" state it doesn't have; a plain button role would be honest (Extras.kt:969-975, 1962-1967).
57. [A11Y] BreathLoops PatternCard rows are selectable cards with no `Role`/`selected` semantics — the visual check icon (with cd "Selected") is the only signal, read out of context after the card body (BreathLoopsScreen.kt:223-239).
58. [A11Y] RoundTimer renders a draining bar with no `progressBarRangeInfo` and no non-visual warning — a timed round's only expiry cue is color, while the mixer sliders in the same codebase set range semantics properly (GameSession.kt:258-288 vs Extras.kt:1474-1477).
59. [POLISH] PrivacyScreen applies the Urdu RTL `LocalLayoutDirection` only around the consent-switch card — the notice's caption, the language-picker chips and the setup-sync/error cards above it stay LTR for an RTL notice (Settings.kt:285-324).

## Token discipline & component-grammar drift

60. [POLISH] Toolkit hub hardcodes a parallel accent palette — `Color(0xFF4ADE80)`, `0xFF64C9FF`, `0xFF7A5CFF`, `0xFFB18CFF`, `0xFFFFD166`, `0xFFFF6B81`, `0xFF9D7CFF` — while importing theme tokens in the same file; `0xFF7A5CFF` is a *near*-BrandPrimary (token is `0xFF7C6FF0`), so the hub's purple subtly disagrees with the brand purple (Extras.kt:1968-2049 vs ui/theme/Color.kt:290). (Mixer-hero constants are exempt: the code documents them as deliberate art-surface constants.)
61. [POLISH] GroundingScreen mixes more raw hexes into the step UI — `0xFF4ADE80` progress dots, `0xFF78E6A1` counter, `0xFF4BAE83`/`0xFF64C9FF` CTA gradient, `0xFFB8C2D9` back label — where Ok/Cyan/TextSoft tokens exist (Extras.kt:2414-2448).
62. [POLISH] Games colour vocabulary is raw hex end to end — `gameAccent`, `colorFor`, the amber timer `0xFFF59E0B`, feedback scrim `0xF20A1020`, result greens `0x2234D399`/`0xFF34D399` — duplicating Ok/Cyan/Warm-family tokens outside the theme (MindfulGamesScreen.kt:78-84; GameSession.kt:285, 317-323, 686, 736-738).
63. [POLISH] BreathePaceControl's selected fill is `Color(0xFF7158E8)` — a third distinct near-brand purple (vs token `0xFF7C6FF0` and the hub's `0xFF7A5CFF`) on a primary selection control (Breathe.kt:559).
64. [POLISH] BreathLoops phase palette (`InhaleTop = 0xFF34D399` …) is six more file-local raw hexes shadowing Ok/Cyan/Iris-family tokens (BreathLoopsScreen.kt:100-105).
65. [POLISH] ImageryIntro's caution card uses raw `0x10FF6B81`/`0x35FF6B81` — the Danger/Warm tokens exist for exactly this warning tint (Rituals.kt:875-887).
66. [POLISH] Goals, Patterns and SafetyPlan use raw Material `OutlinedTextField` at seven call sites while `AppTextField` exists specifically to "replace the default Material OutlinedTextField" — the three data-entry screens render off-brand field chrome (GoalsScreen.kt:200-206, 303-318; PatternScreen.kt:226-231, 276-282; SafetyPlanScreen.kt:173-178).
67. [POLISH] GroundingScreen's Next/Done pill is a bespoke gradient `Box.clickable` with no `Role.Button` and no haptic — every equivalent primary action in the app is a `PrimaryButton` (56dp, role, haptic) (Extras.kt:2425-2445).
68. [POLISH] Onboarding Welcome/Funnel primary CTAs are `Row.clickable` without `Role.Button` — the shared `PrimaryButton` sets the role; the funnel's most-pressed controls don't (OnboardingScreen.kt:583-592, 752-762).
69. [POLISH] Haptic-grammar drift between the two game surfaces: GameSession fires `Haptics.success()` on every correct *round* and `soft()` on a wrong answer, while PatternGlow follows the documented vocabulary (selection per correct tap, success once per completion, warning on miss) — "yes" and "no" feel different per screen, which Haptics.kt names as the failure mode (GameSession.kt:175-185 vs Games.kt:117-136; Haptics.kt:24-29).
70. [POLISH] PlanScreen step toggle fires no haptic at all — the one place the user completes plan work is silent while every chip/row elsewhere ticks `Haptics.selection()` (PlanScreen.kt:129-145).
71. [POLISH] BreathLoops "Clear" history is one-tap destructive with no arm/confirm/undo — the app's own two-tap arm pattern (patterns hide, memory delete, account delete) exists for exactly this class of action (BreathLoopsScreen.kt:205-210; BreathLoopsViewModel.kt:167-169).

## Copy, duplication, dead code

72. [POLISH] MindfulGamesScreen shows `mg_subtitle` twice — as the SubPage eyebrow and again as the first body line directly beneath the title (MindfulGamesScreen.kt:88-89).
73. [POLISH] All four offline guidance screens pass the same string as eyebrow *and* title — headers read "GUIDED IMAGERY / Guided imagery", "5-4-3-2-1 / 5-4-3-2-1" etc. (OfflineGuidanceScreens.kt:132, 203, 250, 301).
74. [POLISH] Two different `GuidedImageryScreen` composables ship and are both routed — the offline photographic journeys ("guidedimagery") and the dusk text reel ("imagery") — same name, overlapping purpose, divergent UX and no cross-reference (OfflineGuidanceScreens.kt:118; Rituals.kt:760; ui/CereBroApp.kt:589, 599).
75. [POLISH] Toolkit's "Box breathing · guided" card routes `breathe/box` to BreathLoopsScreen (a four-pattern picker with history), leaving `BreatheScreen`'s Box and Color branches as unreachable dead code — only the Reset route still uses it (ui/CereBroApp.kt:588, 595; Breathe.kt:416-459).
76. [POLISH] BreathPattern's `displayName`/`description` are hardcoded English ("Box Breathing", "Two-minute reset"…) and completely unused — the UI resolves `patternName()` from resources; the enum strings are a localization trap waiting for a caller (BreathingRules.kt:9-54).
77. [POLISH] `patternDescription()` in BreathLoopsScreen is dead private code — defined, localized, never called, so the picker never shows the pattern descriptions it has strings for (BreathLoopsScreen.kt:513-519).
78. [POLISH] Funnel's `progress` parameter is dead: it is immediately shadowed by `val progress = funnelProgress(step)`, yet all six call sites still pass constants (`progress = 0.25f` …) that are silently ignored — an edit to a call-site value would do nothing (OnboardingScreen.kt:668-685, 374, 422, 431, 496).
79. [POLISH] Unused `ToneGenerator`/`AudioManager` imports in MindfulGamesScreen — leftovers from the retired per-tap tone path (MindfulGamesScreen.kt:3-4).
80. [POLISH] InsightsScreen builds the baseline sentence by string concatenation (`stringResource(...) + if (date...) stringResource(...) else ""`) — word-order breaks in localized/RTL text; the codebase's i18n passes moved everything else to placeholder templates (Extras.kt:528-535).
81. [POLISH] ChipWrapOptions round-trips selection through the *localized label* (`labels.indexOf(label)`), and ScaleRow does the same with scale words — two options translated to the same string mis-map to the wrong stable id (OnboardingScreen.kt:864-873; Baseline.kt:75-79).
82. [POLISH] SearchScreen shows the raw backend kind as user copy — `item.kind.replace('_', ' ')` renders "wind down"/"soundscape" untranslated in result meta (acknowledged in a comment, still user-facing English in a localized list) (SearchScreen.kt:127-130).

## Network/client layer & performance

83. [RISK] `Session.api` caches every GET — including `/users/me/export`, the full personal-data export — into the pref-backed response cache, where it persists until sign-out and is replayed as stale data; on keystore-fallback devices (`buildStore`'s plain-prefs fallback) that is plaintext at rest (net/Session.kt:526-549, 82-98, 819).
84. [POLISH] Session's user-facing failure strings are hardcoded English — "Couldn't reach the server — check your connection.", "Signed out", "Request failed ($code)" — and flow through `userMessage()` onto localized screens (net/Session.kt:182, 501-502).
85. [PERF] The GET response cache is unbounded — every distinct path (`cache:` keys) accumulates in SharedPreferences until sign-out, including large content lists and the export payload; no eviction or size cap (net/Session.kt:546-553).
86. [PERF] SearchScreen fetches its five catalogue kinds strictly sequentially inside one LaunchedEffect — five serial round-trips before the pool is searchable; they are independent and could load concurrently (SearchScreen.kt:57-79).
87. [POLISH] ProgramsScreen matches the enrolled program by *title* (`active?.optString("title") == title`) although both payloads carry ids — two programs sharing a title would both render as enrolled/hide their Start buttons (Extras.kt:856-857).
88. [POLISH] ProgramsScreen "Leave" swallows failure (`runCatching { Api.leaveProgram() }` with no onFailure) — a failed leave just re-renders the enrolled hero with no message, indistinguishable from a mis-tap (Extras.kt:818-820).
89. [POLISH] GoalsScreen add-goal/habit clears the draft *before* the network result — on failure the typed title is gone and only a generic error line remains (GoalsScreen.kt:207-214, 319-327).
90. [POLISH] PatternScreen accept/dismiss recommendation failures are silent (`runCatching` with no onFailure, then `reload++`) — the card simply reappears with no explanation (PatternScreen.kt:188-199).
91. [POLISH] TrendsScreen shows the previous window's chart unmarked while a new window loads — `loading` only gates when `trends == null`, so switching 7d→90d renders 7d data under the 90d-selected chip until the fetch lands (TrendsScreen.kt:216-224, 258-288).
92. [POLISH] InsightsScreen metric bars `remember { Animatable(...) }` inside a plain `forEach` with no `key()` — positional memoization means a changed metrics order/length after a future refresh would reuse the wrong row's fill animation state; latent because the list currently loads once (Extras.kt:575-584).
93. [RISK] RemindersScreen's toggle reflects only the stored pref — if POST_NOTIFICATIONS is later revoked in system settings, the switch still shows reminders on while nothing can be delivered; and a permission denial at enable time produces no feedback at all (the launcher callback ignores `granted == false`) (Settings.kt:441-455).
94. [RISK] WritingStep's privacy promise ("what someone writes lives in this composable until they press Save") is weakened by `rememberSaveable` — the brain-dump text is serialized into the Activity's saved-instance Bundle by the framework, which is state *outside* the composable the copy doesn't account for (Rituals.kt:196-210, 220).
