# Audit K — Emulator UI/UX walk, screen ratings, icons, and first-use comprehension (2026-08-15)

**Rig:** headless emulator `Cerebro_Pixel6_API34` (1080×2400 @420dpi, en-US locale), fresh
install → full onboarding as a brand-new user → guest walk → signed-in walk
(smoke account). ~28 screens exercised live; screenshots in the session scratchpad.

**Rig caveat:** the emulator on this machine segfaults (exit 139) in the host
SwiftShader renderer under sustained full-screen animation — it killed four runs,
reproducibly on the BreatheEngine full run. Reinstalling the Emulator package
(36.6 → 37.1) did not fix it; `-gpu guest` did not either. Headless `-no-window`
*does* bypass the old Qt/opengl32sw crash, so static screens are walkable in
1–25-minute windows. Animation-heavy surfaces (breathe run, bubble pop, zen
ripples, wind-down) remain **device-only** checks. The app itself never crashed —
every kill was the emulator process.

Ratings are /10, judged as a normal first-time user with no product knowledge.
Focus areas per the owner's ask: every component and icon, and "would a normal
user understand this?"

## Ratings — onboarding funnel

| # | Screen | Rating | What a normal user experiences |
|---|--------|--------|--------------------------------|
| SPL-01 | Welcome | 8.5 | Calm, honest ("About 2 minutes · No account required"). **Privacy is stated three times in three consecutive lines** — sub, privacy line, meta line all repeat "privately / no account". |
| ONB-01 | Choose language | 9 | Best-in-class: detected language stated as fact, "View all languages" names what's behind it, CTA confirms the choice ("Continue in English"). The red shield in the bar is unexplained the first time a user sees it (it's the crisis door). |
| ONB-02 | What you can do here | 6.5 | **Card-suit glyphs ♧ □ ▣ as feature icons carry zero meaning** (a club for "Settle your body"). Cards carry chevrons that all just advance to the next step — the affordance implies destinations they don't have. CTA "Continue to Honesty first" reads oddly without knowing step names. |
| ONB-03 | Honesty first | 7.5 | Danger card + urgent door + age gate all correct. **"This prototype currently provides the adult wellness experience" — the word "prototype" is user-facing copy** and torpedoes trust on a compliance surface. |
| ONB-11 | Under-18 branch | 8.5 | Warm, no dead end, urgent support stays. "No age response is sent to an organisation" is B2B language a consumer won't parse. |
| ONB-04 | First check-in | 7 | Copy is right ("There is no wrong answer…"). **The six tiles' icons are near-invisible glyphs in cream circles (◌ ⌁ ↓ ☾ ⁘ …) — at rendered size they read as six identical placeholder dots**, i.e. "unfinished app". |
| ONB-05 | Reset (breathe) | 7.5 | Skip honored, honest "first win" logic. (Full engine run is a device-only check — see rig caveat.) |
| ONB-06 | Reflection | 7 | ASCII arrows (↘ — ↗ ·) as icons again; otherwise fine. |
| ONB-07 | Consent | 9 | DPDP-correct: every category visible, nothing pre-ticked, hints readable. |
| ONB-08 | Notifications | 7 | Options fine (♧/× glyphs again). **The Android 13 permission dialog fires one screen late — it lands on top of the *Guest* step**, visually disconnected from the choice that caused it. Request it before advancing. |
| ONB-09 | Save your progress | 8.5 | Guest-first promise is clear and honest. |
| ONB-10 | Ready | 8 | "Enter Today" assumes the user already knows the tab is called Today. |

## Ratings — tabs (guest + signed-in)

| Screen | Rating | Notes |
|--------|--------|-------|
| Today (guest) | 8.5 | Fallback hero is *exactly* honest ("Nothing is planned for today yet… It did not read your journal"). Weak points: mood-tile glyph icons; **no persistent selected state after tapping a mood** (only a text line appears below); bare "+" on the "This week" card explains nothing; content ghosts through the translucent app bar when scrolled. |
| Today (signed-in) | 9 | Personalized hero (plan step "Nature Walk") with honest provenance ("…It did not read the entries themselves"), program chip ("Day 7 of 7 · Sleep Reset"). The redesign is working as designed. |
| Explore | 8.5 | Real Material icons, clear need-based taxonomy, honest "Urgent support — real people, any hour". The Quick-reset banner's big empty lavender blob is pure dead space. |
| Talk (guest) | 7 | AI-disclosure pill ✔. But the screen sends **mixed signals: "Tap the orb", "Speak or type — small worries welcome" vs. "Chatting needs an account — as a guest, messages can't be sent yet"**. Three invitations, one refusal. "Calm Guide" floats unexplained; "Type instead" is a quirky placeholder; send button looks enabled. |
| Talk (signed-in) | 8.5 | Guest banner replaced by "Remembers · Privacy" links. Clean. |
| Journal | 9 | Prompt card, honest empty state, Private mode row with state ("Lock is off"). |
| Journal editor | 8.5 | Prompt-seeded title, optional mood chips, save disabled until content. Label-and-placeholder duplication ("Title" fields repeat their labels as placeholder text — also on Trusted contact and Safety plan). |
| You | 9 | Best-organized screen in the app: real icons, informative subtitles, sensible groups, region-aware crisis card. "988 Suicide & Crisis Lifeline · 988" says 988 twice; guest avatar is a dot-in-circle placeholder. |

## Ratings — tools & sub-screens

| Screen | Rating | Notes |
|--------|--------|-------|
| Urgent support | 9 | Ordering correct (danger banner → crisis line first → emergency → trusted person → "I cannot call" alternatives), region-aware (988 on a US-locale device; Tele-MANAS leads on IN). **Judgment call:** the "Not verified yet / confirm the number locally" pill is honest but may cause hesitation at the exact moment action matters — verify the four seeded regions' numbers and drop the pill for known-good regions. |
| Grounding intro | 8 | Structure excellent (what/when/how + duration + a11y promises). "Guide a calm, **interruption-tolerant regulation exercise**" is clinical jargon. "This may help when" chips look like selectable filters but are informational. |
| Grounding run | 8.5 | Clear segmented progress, "Why this works" provenance ✔. Lower half of the screen is empty. |
| Toolkit | 8.5 | Clear; 5-4-3-2-1 card repeats its own name in its description. |
| Practice library | 8 | Clear seven families. **The "Ground" family icon is the same red-cross shield used everywhere else for Urgent support** — the app's crisis symbol on a routine practice. "TIPP and sensory skills" is unexplained jargon. |
| Breathing prep | 8.5 | Safety note ("Stop if you feel dizzy"), chime/haptics toggles. |
| Daily plan (guest) | **3** | **Defect: infinite "Loading your plan…" spinner** — no error state, no sign-in prompt, never resolves (verified 8+ s, re-entry same). Also "**Agentic** plan · adapts to your check-ins" — jargon. |
| Sounds (guest) | **3.5** | **Defect: dead screen with wrong recovery copy.** Both sections show "Sign in to keep this… nothing is saved yet" + "Try again" for a *browse/listen* library that simply never loads for guests. "Try again" can never succeed; nothing explains that listening needs an account (or let guests listen). |
| Sleep | 8 | Morning check-in works; disabled-save helper text ✔; Health Connect copy honest but dense. Emoji scale (😴😟😐🙂😊) clashes with the app's line-icon language (known owner decision #25). Letter-spaced "chip" typography is overused on body copy. |
| Privacy & memory | 8 | 13-language DPDP notice ✔; fail-safe error card ("Try again before you touch them") is the right pattern — **but a guest sees "we could not reach the server", which is false: there is simply no account.** |
| Safety plan | 7.5 | Concept and copy excellent. **Guest error says "your plan is still on your account" — a guest has no account.** |
| Trusted contact | 9 | Exemplary transparency ("Nothing is sent unless you switch it on… nothing is sent for an ordinary bad day"), default off. |
| Appearance | 9 | Clear, applies-everywhere note matches decided behavior. |
| Premium | 8 | OECD-clean: full price pre-purchase, nothing preselected, "Cancel anytime", no countdowns. "**Billing isn't wired on Android yet — Play Billing lands with Play Console setup**" is developer-speak shipped to users. ₹ pricing regardless of locale. |
| Sign in | 8.5 | Clean, "Private by design" framing, sign-in worked first try against the local backend. Post-sign-in returns to the bottom of You (disorienting but harmless). |

**Overall: ~8/10.** The product's honesty patterns (provenance footers, fallback
heroes, consent, crisis ordering) are genuinely strong — the failures cluster in
four systemic classes, below.

## The four systemic findings

1. **Guest states reuse server-error machinery and lie about it** (Daily plan
   spinner-forever; Sounds "Try again"; Privacy "could not reach the server";
   Safety plan "still on your account"). One fix: an explicit guest-aware state
   per async surface — "Sign in to use this" with a sign-in button — instead of
   funneling 401s into network-error copy. This is the single biggest
   normal-user comprehension failure.
2. **Two icon languages.** Post-onboarding screens use real Material icons
   (good); onboarding and the check-in tiles use text glyphs (♧ □ ▣ ◌ ⌁ ⁘ ↘)
   that render as meaningless dots and suits — a first-time user's *first hour*
   is spent in the glyph half. Replace with the same outlined icon set used in
   Explore/You (there are obvious matches: Air/Spa for settle, chat bubble for
   talk, book for write, WbSunny/Bedtime/Bolt etc. for moods). Also: the Ground
   family should not wear the crisis shield.
3. **The guided tour doesn't point at anything.** Four bottom cards over a
   dimmed Home; stop 1 says "check in daily" while the check-in grid is hidden
   under the scrim/fold, stop 2 says "tap the hero" — a term users don't know.
   Anchor each stop to its element (spotlight cutout or at least scroll-to +
   outline), and say "the big card at the top" not "the hero".
4. **Jargon leaks:** "prototype" (ONB-03), "Agentic plan" (Daily plan), 
   "interruption-tolerant regulation exercise" (grounding), "TIPP" (unexpanded),
   "Billing isn't wired… Play Console setup" (Premium), "Enter Today" (ONB-10).
   Each is one string fix.

## Tutorials & tips — the direct answer

Reviewed end-to-end this session:

- **Onboarding funnel (10 steps): strong (9/10).** Value-first, honest, skippable
  reset, DPDP consent, crisis door on every step, back-gesture correct.
- **Guided tour: exists, shows once on first Home entry, replayable from
  You → "Take a quick tour" — but weak (6.5/10)** for the anchoring reasons
  above. It is also Home-only: Talk/Journal/Sleep get no first-run orientation.
- **Contextual tips are the app's real strength:** hero provenance lines,
  "Why this works" on tools, "This may help when" chips, disabled-CTA helper
  text ("Pick how rested you felt to save."), guest banners on Today/Talk.
- **Gaps:** no tooltip/coach-mark system outside the tour; the Talk orb, the
  "This week +" control, and the You shield/gear icons are never introduced;
  permission dialog timing (ONB-08) undercuts the notify step's own explanation.

## Smaller polish notes (report only)

- Welcome: privacy claim ×3 in adjacent lines; "step—privately" em-dash spacing.
- Today: hero/app-bar ghosting on scroll; mood tap needs a visible selected ring.
- Talk: guest send button should look disabled, invitation copy should switch to
  "read-only" phrasing for guests.
- 988 row: "…Lifeline · 988" repeats the number; drop one.
- Label==placeholder duplication across all form fields.
- Sleep: letter-spaced micro-type used for full sentences.
- Post-sign-in should land on Today (or You top), not You-bottom.

## Addendum (same day) — complete icon census + the remaining 17 screens

The owner asked whether *all* icons were reviewed on real snaps. Follow-up pass:
every remaining routed screen was captured live (search, toolkit-via-Explore,
games list, programs, insight reel, notifications inbox, goals, insights hub,
trends, pattern dashboard, companion style, reminders, crisis region, human
support, export, delete, journal history), and the code was censused for every
icon reference. **The app has FOUR icon families:**

1. **92 distinct Material icons (~230 uses)** — the deliberate system; consistent
   and legible everywhere it's used (tabs, You, Explore, Journal, tools).
2. **Text glyphs** (♧ □ ▣ ◌ ⌁ ↓ ☾ ⁘ … ↘ ↗ × ◇ ✦ ≈ •) — onboarding, mood tiles,
   Insights-hub rows, Today "Patterns" row, one game tile. The placeholder-dot
   problem from the main report.
3. **Emoji** — sleep rest scale (😴😟😐🙂😊, known owner decision #25) **and the
   Mindful Games tiles (🎯 🛑 🔢 + a bare letter "S" for Stroop Flow)** — a third
   visual language, inconsistent with the line-icon system.
4. **Generative-art medallions** (`EmptyStateArt`/`ContentArt`, seeded by kind) —
   **defect found on two live screens:** on Trends-empty and Journal-History-empty
   the "insight"/"journal" seeds render as a featureless dark plum square at
   52–56dp in Dawn — it reads as a broken/unloaded image, not art. Either give
   the medallion a guaranteed-contrast overlay glyph or floor its size.
   (Custom drawables — 5 tab icons, launcher set, notification orb, 4 guided-
   imagery photos — are fine; tab icons verified on every capture.)

New-screen ratings (same /10 scale): Search 8.5 (clean, guiding empty state) ·
Games list 7.5 (honest "never a measurement of you"; emoji tiles + letter-"S"
icon) · Programs 8.5 (complete-state card + open week path; good) · Insight Reel
8.5 (eyebrow duplicates the title verbatim) · Notifications inbox 8 ("Server-sent
nudges are switched off **in this build**" — dev-speak to users; privacy footer ✔)
· Goals & habits 8 ("After I…" habit field needs an example to be understood) ·
Insights hub 8 (stat-tile "No diary yet" overflows its pill; glyph row icons) ·
Trends 7 (good empty copy; broken-looking medallion) · Pattern dashboard 9
(outstanding honesty) · Companion style 9 · Reminders 8.5 · Crisis region 8.5 ·
Human support 9 — **but it is India-hardcoded (Tele-MANAS/iCall) while Urgent
support is region-aware (988 on this US-locale device): the two surfaces disagree
about where the user is** · Export 8 · Delete account 7 (spartan but honest;
confirmation not exercised) · Journal history 7 (medallion defect).

## What this session could not verify (device-only)

- BreatheEngine full run, bubble pop, zen ripples, wind-down ambience
  (emulator renderer dies — see rig caveat), TalkBack (needs the owner's hand),
  WorkCoach walk (no sponsored account in dev seed), voice path, haptics.
