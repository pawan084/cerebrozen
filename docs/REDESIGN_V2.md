# Redesign V2 — the whole app, compact (2026-08-15)

> **Lineage note (2026-08-15):** this filename previously held the 2026-08-06 Light-Dawn
> spec (ref/-derived; the ONB-xx contract and the five-tab §6.1 ruling live there). That
> document is preserved at [REDESIGN_V2_2026-08-06-lightdawn.md](REDESIGN_V2_2026-08-06-lightdawn.md);
> where the two disagree — tabs (Sleep returns, Explore retires) and the onboarding step
> count (10 → 4) — THIS document carries the newer owner approval and governs.

Supersedes `TODAY_REDESIGN.md` (folded in here as §3.1) and executes `REDESIGN.md` (2026-07,
F1–F11) at screen level after Audit L and the 2026-08-15 four-thread research pass
(competitors, distress cognitive load, India-first, layout systems). Owner correction
applied: the first Today draft ran too airy — **the scale below is compact**. Density is
not the enemy; *competition for attention* is. WhatsApp-dense, calm-ordered.

## 1. Global system (every screen)

### Spacing (dp) — base-4, compact
| Token | dp | |
|---|---|---|
| screen margin | **16** | M3 compact spec |
| zone gap | **24** | between top-level sections |
| heading → content | **10** | binds heading to its group |
| card padding | **14** (hero 16) | |
| sibling rows | **8** | |
| intra-card | 6 (title→body 3) | |
| last item → nav | 24 + insets | |

Invariant unchanged: inner space ≤ outer space; every step up in dp = a step up in meaning;
**no dividers** — spacing and surface do the grouping. Rows are 48–52dp, not 76dp.

### Type — 5 sizes, roles from `Type.kt`
displayLarge **32/38** (greeting/screen hero only — down from 36) · titleLarge 18/24 ·
body 16/22 & 14/20 · labelLarge 15/20 (pill, links) · labelSmall 11/14 (eyebrows).
Hierarchy beyond five sizes = weight + `textSecondary`/`textFaint`, never new sizes.

### Rules (all screens, enforceable in review)
1. **One primary action** — exactly one white `PrimaryButton` per screen; secondary = tonal;
   tertiary = accent text link. `ReferenceAction` retired.
2. **≤4 zones per screen; ≤9 content tappables** (Cowan/Chernev budget).
3. **Strings ≤7 familiar words**, reading age 9–11, no clinical acronyms unexpanded, no
   academic citations in UI; `*_why` = one line behind a tap.
4. **Icon + one-word label** on every door; never icon-only, never a paragraph.
5. **Crisis shield in the same pixels on every screen** — `Page` gains `onUrgent`; Talk,
   Journal and the breathe session get it (Audit L defect).
6. **Stable layout** — zones never reorder by state; content swaps inside its zone.
7. **Doors are unique** — one door per destination per screen; tabs own their features
   (nothing on Today duplicates a tab).
8. **Every list caps at 3 + "see all →"**; discovery lives in Explore only.
9. Shoulder-surfing safe: no condition words in large type; generic notification copy.
10. Four async states designed everywhere (Shimmer / honest empty / one-line error + retry
    / content); cached render offline — never an empty home.

### Visual language (the "world-class" layer)
- **Aurora depth**: every screen opens on one soft radial glow at the top (existing
  `Tokens.kt` gradient stops) over a solid ground; cards are soft-solids with a bevel
  hairline — depth from the surface ladder, never shadows or fake glass.
- **Per-tab accent light** (existing `Accent.*` tokens): Today lavender `#D9ACDE` ·
  Sleep night-blue `#9CC4DC` (Sleep's whole surface shifts to the blue aurora) · Talk warm
  rose · Practices green `#AFD6B2` · crisis stays `#FF8C82` for the shield only. One
  saturated hue per screen.
- **The orb is the brand motif**: the breathing orb glows in the Today hero and Sleep
  hero, guides the breath in-session; static under Reduce Motion.
- **Serif voice sparingly**: display serif only for the greeting and the step's name;
  everything else quiet sans. Icons: one 24dp stroke family (Feather-weight), always with
  a one-word label.
- White pill primary gets a soft outer glow — the brightest object on any screen.

## 2. Navigation — tabs redesigned (pending owner/Deepak approval)

**Tabs: `Today · Sleep · Talk · Journal · You`** — Explore retires, Sleep takes its slot.
Rationale: Sleep is the only bias-robust evidence domain (F2, g=0.71) yet had no tab, while
Explore held a permanent slot for browsing — the behavior stressed users measurably abandon
("immediate relief, not discovery"; Wysa ships no explore tab). Hindi labels are single
spoken words: **आज · नींद · बात · डायरी · आप**.

Explore's content keeps doors (nothing orphaned): the merged **Practices** library (one hub:
search + Breathe/Ground/Reframe/Settle + programs + games) is reached from the Today hero's
**"more options →"** (the moment browsing is actually wanted), Talk's activity chips, and a
You row. Sounds already lives in Sleep; the urgent door is the shield + You Support card.

Bar spec: 64dp · icons 24dp + labels always visible (NBU: never icon-only) · active =
accent tint + soft tonal pill behind the icon; inactive = `textFaint` · no badges/dots ·
haptic tick on switch · targets ≥48dp.

Route count 67 → ~40:
aliases killed (`dailyplan`, `talk/live`, `talk/chat`), orphans deleted (`breathing`,
`onegoodthing`, `intention`, unrouted `InsightsScreen`, `GratitudeGardenScreen`), one
implementation per behavior (6 breathing → 1 engine · 5 gratitude → 1 · 4 grounding → 2
(normal+crisis) · 2 imagery → 1 · 2 Simon games → 1 · 2 crisis screens → 1 · Toolkit +
Practice library → **one library**). Analytics: 5 named surfaces → **2** ("Your week" in
You/Insights; sleep trends inside Sleep).

## 3. Screens

### 3.1 Today (enriched 2026-08-15 — owner: home should carry more relevant items)
Zones: greeting + one interpreted sentence (32sp, the only display text) → **THE CARD**
(ask: 6 mood chips 2×3 @48dp → morphs into one step: title ≤4 words, why ≤10 words, one
white pill, one tertiary "more options →" to the Practices hub; intensity/note = skippable
bottom sheet) → **Your day**: Tonight door (stable slot, clock-aware: morning 1-tap sleep
log / evening wind-down) + plan-progress row ("Today's plan · 2 of 3 done") + clock-aware
journal-prompt row (evening) → **Quick helps**: one row of 4 icon-chips (Breathe · Ground ·
Sounds · Games — direct doors, no browsing) → **this week** passive row (7 presence dots →
Insights) → guest one-liner.
Discipline that keeps "more" from becoming clutter: rows not cards, ≤7-word labels, one
white pill still, contextual rows appear by clock/state (never all at once), total ≤14
tappables / ~55 words — still a fraction of the old 32/140. Deleted stays deleted: lede,
rail, folds, banners, bell, tour modal, duplicate insights doors, `GuestSignInCard`.

### 3.2 Onboarding — 13 → 5 screens
1 **Welcome** (name the promise in ≤12 words; "2 minutes" now true) → 2 **Age +
disclosure** (one screen: 18+ toggle + can/can't in 3 bullets ≤6 words each) →
3 **Consent** (all 6 categories visible, all default OFF, 13-language notice reachable
here — fixes both DPDP defects) → 4 **"How are you right now?"** (same 6-chip picker as
Today; correct mood mapping — fixes the wrong-write defect; seeds the first step) →
5 lands on **Today** with the hint chip (no Ready page, no tour modal, no breathing step —
the first recommendation IS the breathing exercise). Language auto-detected (chip to
change on Welcome); reminders asked in-context after first real check-in. Guest is the
default; account ask stays a step, not a wall.

### 3.3 Explore — the one library
Zones: search field → **4 need-doors** (Breathe · Ground · Reframe · Settle — the merged
Toolkit/Practice-library taxonomy, ≤4 items each + see-all) → Programs row → Games row
(registry's 8) → quiet Urgent-support door. Sounds lives in Sleep; content rail lives
here. ≤9 taps. "Mindful activities"/"Toolkit" naming resolved: **one name — "Practices"**.

### 3.4 Talk — chat-first (Wysa evidence, F3)
The thread is the screen. Top bar: AI pill + shield (new `onUrgent`). Empty state: 3
starter chips max (was: banners + hints + cards). Inline activity chips stay (evidenced
routing). Crisis banner unchanged — never blocks. Disclosure re-sheet cadence unchanged.
Signed-out: local companion + one sign-in chip after first refused send (not before).

### 3.5 Journal
Zones: prompt-of-day card with **Start writing** (the white pill) → recent entries (3 +
see all) → lock chip row. One good thing / intention / gratitude become **prompt chips in
the composer** (kills 3 routes). Reflection appears on the entry, not the hub. Biometric
lock unchanged.

### 3.6 Sleep — split "Tonight" vs "Your sleep" (worst screen: 14 blocks → 5)
Tab shows **Tonight**: player hero (play/pause + timer chip — the white pill) → wind-down
ritual door → morning-aware 1-tap sleep log card (chips, not steppers) → Sounds door →
**Your sleep** door (diary history, trends, Health Connect, insights — all one level
down). Sleep-insights screen localized (Audit L defect) and folded into "Your sleep".

### 3.7 You — 23 rows → 10
Identity row → **Support card** (crisis, unchanged prominence) → *Care*: Your week
(insights+trends+patterns as tabs inside), Safety plan, Trusted contact → *Settings*:
Reminders, Appearance & language (one screen, two sections), Privacy & data (consent,
export, delete, policy inside — one door) → *About*: Help & about (tour replay, version,
legal inside). Premium row hidden until Play Billing exists (honesty). Work-coaching row
stays sponsored-only. One footer line.

### 3.8 Crisis — one screen (delete the twin)
Dial hero (region line, bilingual: "मदद चाहिए? / Need help? 14416") → 3 rows: call/WhatsApp
Tele-MANAS · my safety plan · grounding now → change-region link. `CrisisScreen` +
16 `urgent_*` strings deleted; onboarding points at `UrgentSupportScreen`.

### 3.9 Plan, Settings sub-screens, Games
Plan already passes (one pill, ~35 words) — becomes the template. Settings sub-screens:
intro paragraphs cut to ≤12 words (`language_intro` 35→12, `appearance_intro` 25→8);
13-language chips behind a disclosure; error bodies → one line + Retry. SafetyPlan: 7 Save
buttons → autosave + one Done; intro 32→12 words. Games hub: registry list only, one
Simon, one tap-calm; card copy loses the redundant `practiceRes` line.

## 4. Copy system (app-wide)
- EN: simple English, ≤7 words/string, contractions fine, no jargon (CBT-I → "sleep
  method, tested"), zero citations on-screen.
- HI: warm spoken Hindi, loanwords in Devanagari (चेक-इन, प्लान); body ≥16sp, lh 1.6–1.8;
  crisis lines bilingual. `ConsentNotice.kt` languages move to `values-*/`.
- Disclaimers: "not medical care" said **twice** in the app (onboarding disclosure + Talk
  pill), not 12×. "No streaks" said **once** (Insights). One canonical claim line per
  evidence family (4 dedup groups from Audit L).

## 5. Delivery order (compact waves, each shippable)
| Wave | Scope | Depends on |
|---|---|---|
| V2-a | Global: spacing/type tokens compacted, `Page.onUrgent`, `PrimaryButton` everywhere, row heights | — |
| V2-b | Today (§3.1) + dead-string/route cleanup | V2-a |
| V2-c | Onboarding 5-screen cut + consent/mood defect fixes (= Audit L Waves L1+L2) | — |
| V2-d | Sleep split + You 10-row + Crisis merge | V2-a |
| V2-e | Explore one-library + Talk/Journal simplification + route consolidation | V2-d |
| V2-f | Copy diet + Hindi parity for every touched string | any |

Cross-stack: zero contract changes (taxonomy, widget kinds, crisis regions, product ids
untouched). iOS/web adopt the V2 system in a later pass — Android first, before the Play
internal-testing build.
