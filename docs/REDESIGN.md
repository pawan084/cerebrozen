# CereBro Android Redesign Spec

> Output of the 2026-07-12 evidence-based redesign audit. Inputs: (a) a deep-research pass
> (107 agents, adversarially verified — 11 surviving findings, 0 refuted; sources below),
> (b) a complete inventory of the current 40-destination Android surface.
> Status: **APPROVED 2026-07-12 — Phases 1–2 FULLY IMPLEMENTED same day** (8 waves, all
> compile/test-green, emulator smoke-verified in both themes). Includes the Dawn light
> theme (theme-aware token getters; Night byte-identical, pinned by regression test;
> System/Night/Dawn setting in You; Sleep + signed-out funnel always Night). Phase 3
> (Hindi localization, CBT-I backend program, premium ethics gate) remains roadmap.

## 0. The one-paragraph verdict

The evidence says CereBro's competitive lever is **not visual polish** (Indian-market apps
already average 4.09/5 on MARS aesthetics; only 10.9% cite research, only 26.9% ship crisis
strategies, only 2.9% offer an Indian language — *that's* the gap). The redesign therefore
spends its budget on: (1) a **credibility layer** (evidence provenance in the UI),
(2) **crisis surfacing ≤2 taps from anywhere**, (3) **consolidation** — 40 routes → ~28,
four breathing surfaces → one, three audio engines → one mental model, (4) **CBT structure**
in the two evidenced pillars (companion, sleep), and (5) **softened streaks** (gamification
is meta-analytically null and pressure loops stress unwell users most). The visual language
gets a considered evolution, honestly labeled as design judgment — the research explicitly
found **no** surviving evidence for palette/typography/glassmorphism choices either way.

## 1. What the evidence established (each drives decisions below)

| # | Finding | Confidence | Design consequence |
|---|---------|-----------|--------------------|
| F1 | App effects are small (dep. g=0.45→0.18 after bias adj.) — adjunct, never treatment | High | Copy audit: no cure claims anywhere; "companion, not care" framing stays |
| F2 | **Sleep is the strongest, only bias-robust domain (g=0.71)** — but driven by CBT-I-style apps, not tracking/soundscapes | High | Sleep becomes the flagship: CBT-I-informed module, not just diary + sounds |
| F3 | **Chatbots (g=0.53 vs 0.26) and CBT features (g=0.35 vs 0.21) are the only feature classes with moderator backing**; rule-based beats pure-LLM | High | Talk companion restructured around scripted CBT exercises with LLM glue |
| F4 | Mood monitoring helps in anxiety-targeted use; keep lightweight (can heighten distress awareness) | Medium | Check-in stays 1-tap simple; no granularity expansion |
| F5 | **Gamification/persuasive-design stacking is null** for both efficacy and adherence (3 independent meta-analyses) | High | No new badges/points; streaks softened, expectations reset |
| F6 | Competitive/social gamification actively stresses unwell users most | Medium | No leaderboards/social pressure, ever; streaks get forgiveness mechanics |
| F7 | **Apps show zero effect on suicidality; risk feedback without a pathway is a documented anti-pattern** | High | Crisis = escalation to Tele-MANAS, never in-app management; every risk signal pairs with a route |
| F8 | India gaps: crisis strategies 26.9%, research citations 10.9%, professional-involvement disclosure 34.9%, Indian languages 2.9% | High | Crisis + credibility + language are the differentiators |
| F9 | Aesthetics already high across the market; information quality is what lags | High | Visual budget capped; credibility surfacing funded instead |
| F10 | ~76% of subscription apps use dark patterns (ICPEN 2024) | High | Premium + consent audited against the OECD six-indicator taxonomy |
| F11 | Realistic D30 <6% category-wide; mechanics don't move it, utility does | Medium | Retention strategy = sleep/companion/check-in utility, not loops |

**Explicit evidence gaps (honesty ledger):** visual language (palettes, dark-vs-light,
serif-vs-sans, glassmorphism, motion), onboarding funnel patterns, consent UX under DPDP,
voice-UI conventions, notification cadence, and fidget micro-game efficacy. Decisions in
those areas below are labeled **[judgment]** and justified by craft/a11y/brand reasoning,
not evidence.

## 2. Information architecture

### 2.1 Tabs — keep 5, unchanged roles
`Home · Sleep · Talk · Journal · You` — the structure is sound and matches the three
evidenced pillars (check-ins, sleep, companion) plus journal and settings. **[keep]**

### 2.2 Route consolidation: 40 → 28

| Current | Verdict | Why |
|---------|---------|-----|
| `breathing` (Tools), `colorbreathing` (Games), BoxBreathing (inline Games), onboarding Reset | **MERGE → one `breathe` engine** | Four implementations of one behavior. One parameterized screen (presets: Box 4-4-4-4 / Color 4-phase / 2-min Reset), used everywhere incl. onboarding. Kills 2 routes + inline dupe |
| `bubblepop` + `bubblewrap` | **KILL one (keep `bubblepop`)** | Near-duplicates; bubblepop is the richer one (drift + ambience). Fidget efficacy is an evidence gap — keeping one is scaffolding, keeping two is bloat |
| `games` + `tools` hubs | **MERGE → one `toolkit` hub** | Same job ("do a small regulating activity"). "Games" framing undermines the credibility positioning (F9); reframe sections as *Ground · Breathe · Reframe · Settle*. Kills 1 route + 1 nav level |
| `memorymatch`, `slidingpuzzle` | **KILL both** | Pure casual games with zero mental-health claim; weakest inventory items against the F9 credibility bar. (Available to revisit if usage data ever argues otherwise) |
| `zenripples`, `gratitude` | **KEEP** (in Toolkit) | Sensory grounding + gratitude practice — closest to evidenced practices; gratitude persists entries (real utility) |
| `patternglow` | **KEEP** (in Toolkit, "Focus") | Borderline; retained as the single "attention anchor" activity. Honest label: engagement scaffolding |
| `sounds` + `soundscape` + `player` | **MERGE → one `sounds` surface** | Three routes + three audio engines for one job. One hub: Library / Mixer / Now-Playing as sections; one transport; `Player`+`SoundscapeMixer` engines unify behind one facade (ToolAmbience stays internal) |
| `cbt` | **ELEVATE** | The only tool with direct evidence lineage (F3). First-class Toolkit card *and* offered inside Talk as a structured exercise widget |
| `tipp` | **KEEP, cross-link from Crisis** | DBT distress-tolerance; belongs in the safety pathway (F7) |
| `onegoodthing`, `intention` | **MERGE → Journal quick-entries** | They're 1-field journal writes with ambience; become prompt chips in the Journal composer. Kills 2 routes |
| `baseline` | **MERGE → Insights** | Currently a conditional Home row that's easy to never see; becomes the Insights empty-state ("set your starting point") |
| `plan`, `programs`, `insights`, `search`, `patterns`, `crisis`, settings routes | **KEEP** | Each has a distinct job; duplication is at Home (see 3.1), not in the routes |
| `humansupport` | **CHANGE: make real, minimally** | "Coming soon" stubs contradict F8 (professional-help nudges = differentiator, only 22.9% of apps do it). Ship real links now: Tele-MANAS 14416, iCall, vetted directories. No fake promises |
| `premium` | **KEEP stub, add ethics checklist** | Before billing ships it must pass the OECD taxonomy (F10): full price disclosure pre-purchase, no preselected plans, cancel-path parity, no guilt copy |

### 2.3 Safety architecture **[F7/F8 — the differentiator]**
- **Crisis reachable in ≤2 taps from any screen**: persistent, calm "Support" affordance in
  the You tab header + Talk (existing banner) + Journal support card + Toolkit footer.
  Not a screaming SOS button — a steady, always-there door.
- Crisis screen leads with **Tele-MANAS 14416** (call + WhatsApp), then 112, then
  findahelpline; works offline (already true — keep).
- **Rule: any risk signal the app surfaces must arrive with a pathway attached** (the
  journal support card already models this — generalize it).
- No in-app "risk scores" ever shown without context + route (documented anti-pattern).

### 2.4 Credibility layer **[F8/F9 — new]**
- **"Why this works" provenance footer** on every Toolkit tool and Program: one line +
  source ("Box breathing — paced breathing is used in clinical distress-tolerance
  protocols"), expandable. Content ships hardcoded per tool (no backend change needed).
- **About → "How CereBro is built"**: professional involvement disclosure, evidence policy,
  what the app is not (F1). Honest even where the answer is "engagement scaffolding."
- Store listing + onboarding Disclosure step audited to adjunct-not-treatment framing (F1).

### 2.5 Language **[F8 — roadmap]**
13-language DPDP notice already exists; full UI localization (Hindi first) is the single
highest-leverage differentiator remaining (2.9% of market). **Phase 3 roadmap item** —
flagged now so string externalization discipline starts with this redesign's new strings.

## 3. Per-surface changes

### 3.1 Home (Today) — **CHANGE: de-densify around one daily action**
Current: 11 stacked blocks. Redesigned order:
1. Greeting (keep)
2. **Mood check-in** — the primary daily action, moves to top (F4; 1-tap, unchanged mechanics)
3. Plan hero (keep; the *only* plan surface on Home — step list stays in `plan`)
4. One content rail (time-matched, keep)
5. **Presence ring** (was: streak card) — see 3.6
6. Recent check-ins (keep, collapsed)
- **Cut from Home:** program card (lives in `programs`, linked from plan hero when enrolled),
  4 QuickTiles (replaced by a single quiet "Toolkit" row), sleep NavRow (Sleep tab exists),
  baseline row (moved to Insights). Guided tour: keep, re-record stops.

### 3.2 Sleep — **INVEST: the evidenced flagship (F2)**
- Keep: morning check-in (quality + times + Health Connect prefill), week chart, diary.
- **Add (the CBT-I-informed layer, phased):**
  - *Phase 1 (client-only):* sleep-window insight ("your average night: 6h 40m; consistency
    beats duration"), stimulus-control micro-education cards in the wind-down section,
    honest framing shift from "track sleep" to "improve sleep."
  - *Phase 2 (needs backend):* personalized sleep-window recommendation, weekly CBT-I
    program as a first-class Program.
- Sounds sections collapse to one entry into the unified `sounds` surface (3.4).

### 3.3 Talk — **STRENGTHEN: structure around CBT (F3)**
- Keep: disclosure pill + dialog (also satisfies AI-disclosure law direction), crisis
  banner, voice loop, starters, save-to-journal.
- **Change:** the companion offers **structured exercises as first-class chat widgets** —
  CBT reframe, box breathing, TIPP — proactively when context fits (widgets already exist;
  they become the spine, LLM chat the glue — rule-based-first architecture per F3).
- Voice UX unchanged (evidence gap; current design is already restrained). **[judgment]**

### 3.4 Sounds (unified) — **CONSOLIDATE**
One surface, three sections: **Library** (content lists + favourites), **Mixer** (4-layer),
**Now Playing** (full-screen transport, kept zoom transition). One `NowPlayingBar`
everywhere. Engineering note: `Player` and `SoundscapeMixer` unify behind one
`AudioFacade`; exactly one thing plays at a time (currently the two engines can fight).
Honest positioning: comfort content, not therapy (F2 caveat).

### 3.5 Journal — **KEEP + absorb micro-tools**
- Keep: lock, prompts, history, safety card.
- Absorb `onegoodthing`/`intention` as one-tap prompt chips in the composer.
- Evidence honesty: journaling's direct evidence didn't survive verification — it stays as
  a well-loved core practice **[judgment]**, and it feeds the evidenced mood/insight loop.

### 3.6 Streaks → **"Presence" (F5/F6)**
- Rename streak → presence. Copy never counts misses, never says "don't break it."
- **Forgiveness built in:** a missed day dims, never resets; weekly ring fills by "days you
  showed up," month view is a gentle constellation, not a chain.
- Milestone celebrations stay (positive-only) — but no new badges/points/levels, ever (F5).
- Celebration overlay: keep, reduce frequency to genuinely notable moments.

### 3.7 Onboarding — **TRIM + honesty pass**
- 10 steps → 8: **kill the fake Plan preview step** (static mock presented as real — fails
  the credibility bar; replaced by one line in the value prop), **merge Age into
  Disclosure** (single "who this is for + what it isn't" step, F1 framing).
- Consent step: show **all 6 categories** (currently 3 shown, 3 silently sent false —
  DPDP "specific and informed" gap flagged in the earlier review).
- Keep: value-first Reset-before-signup **[judgment — evidence gap on funnels]**, language
  step, 1-tap State assessment (matches F4 lightweight principle).

### 3.8 Settings/You — **KEEP + credibility/safety surfacing**
- You header gains the persistent Support door (2.3).
- Privacy screen: keep (already strong); language selector surfaces here too.
- Premium: OECD checklist gate before launch (F10). Human support: real links now (2.2).

## 4. Visual language — **[judgment, evidence-gap declared]**

The research's only visual finding: polish is not the gap. So: **evolve, don't torch** —
the radical budget went to IA/credibility/safety above. Proposed direction:

### 4.1 "Dusk & Dawn" — context-aware dual theme
- **Night (evolved current identity):** deep-indigo base retained but *simplified* — kill
  the fake glassmorphism (post-reskin fills are opaque; the Haze blur dependency does
  nothing behind solid fills — from the earlier code audit). Honest **soft-solid surfaces**:
  one elevation ladder of solid indigo fills + the existing bevel hairline. Aurora backdrop
  stays (it's the brand) but drops to 2 orbs and pauses under reduce-motion (already does).
- **Dawn (new, Phase 2):** warm off-white/cream light theme (`Cream`/`Ink` tokens already
  exist) for daytime use — better outdoor readability and WCAG headroom. Default follows
  system dark/light; Sleep tab and wind-down hours always Night.
- **Why dual:** daytime check-ins in sunlight are a real Indian-context use case; a
  night-only app quietly tells users it's a bedtime app. **[judgment]**

### 4.2 Tokens (Phase 1 refactor, both themes derive from it)
- Re-architect `Color.kt` from ~70 ad-hoc constants → **semantic roles**:
  `surface/surfaceRaised/surfaceField/line/textPrimary/textSecondary/textFaint/accent/
  accentSoft/danger/ok` + per-section accent (exists). The 44 onboarding one-off tokens
  collapse into the role scale.
- **Contrast gate:** every text role ≥ 4.5:1 on its surface (current `TextMuted2` on
  `CardFill` ≈ 3.9:1 — fix). Automated check in a unit test.
- Type: keep Newsreader serif display + system sans body (brand equity, no evidence
  against). Motion: current restraint + reduce-motion discipline stays; page transitions
  standardize on the existing gentle fade-scale.

## 5. Phasing

| Phase | Scope | Risk |
|-------|-------|------|
| **1. Foundation** | Token role refactor + contrast fixes + kill fake glass; unified `breathe` engine; Games+Tools→Toolkit merge; route kills/merges (memorymatch, slidingpuzzle, bubblewrap, onegoodthing, intention, colorbreathing); Home de-densify; presence rename+forgiveness; crisis ≤2-tap affordance; humansupport real links; onboarding trim + 6-category consent; credibility footers | Client-only, no backend changes |
| **2. Flagship** | Sounds consolidation (AudioFacade); Sleep CBT-I Phase-1 content; Talk structured-exercise widgets; Dawn theme | Client-only, larger |
| **3. Roadmap** | Hindi localization; CBT-I program (backend); premium ethics-gated launch | Cross-stack |

## 6. Sources (verified findings)

- Kulke et al. 2025, *Lancet Digital Health* — 72-RCT meta-analysis (sleep g=0.71; bias adjustment)
- Linardon et al. 2024, *World Psychiatry* — 176-RCT meta-analysis (CBT/chatbot/mood-monitoring moderators)
- Valentine et al. 2025, *npj Digital Medicine* — 92 RCTs (persuasive-design null)
- Six et al. 2021 — gamification null (38 RCTs); Yang & Li 2021 — gamification stress moderation
- Mehrotra et al. 2025, *JMIR mHealth* 13:e79238 — 350-app Indian market review (crisis/citation/language gaps, MARS)
- FTC/ICPEN/GPEN 2024 sweeps — subscription dark patterns (~76%)
- *Current Treatment Options in Psychiatry* 2023 — retention baselines

*Companion doc: `.claude/skills/mobile-design/SKILL.md` (the distilled, reusable design rules).*
