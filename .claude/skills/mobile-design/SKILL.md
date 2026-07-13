---
name: mobile-design
description: CereBro's mobile design system + evidence-based wellness-app design rules. Use when designing, reviewing, or building any user-facing screen/component/flow (Android Compose, iOS SwiftUI, web) — covers tokens, components, motion, a11y, copy tone, feature-worthiness bar, and safety/ethics constraints.
---

# CereBro Mobile Design

Grounded in the 2026-07 verified research pass (see `docs/REDESIGN.md` §6 for sources).
Rules marked **[evidence]** trace to meta-analytic findings; **[judgment]** are house style.

## 1. Positioning rules (non-negotiable)

- **Adjunct, never treatment** [evidence: effects are small, g≈0.2–0.45]. No copy anywhere
  may claim to treat, cure, or replace care. "Companion, not a clinician."
- **Safety never blocks** (project hard rule) + **risk always pairs with a pathway**
  [evidence]: any UI that surfaces distress/risk must attach a route (Tele-MANAS 14416
  first, then 112). Never show a risk signal bare. Never attempt in-app crisis management.
- **Crisis ≤2 taps from any screen.** A calm, persistent "Support" door — not a scare button.
- **Credibility is the differentiator, not polish** [evidence: market MARS aesthetics 4.09/5;
  10.9% cite research]. New tools/programs ship with a one-line "why this works" provenance
  footer. If a feature is engagement scaffolding, we say so internally — never dress it as therapy.

## 2. Feature-worthiness bar

Before adding ANY feature, it must clear one of:
1. **Evidenced pillar** — sleep improvement (CBT-I-informed), CBT-structured companion,
   lightweight mood check-ins [evidence].
2. **Safety/credibility/privacy** — crisis, consent, transparency, export/delete.
3. **Scaffolding with a job** — comfort content (sounds), grounding activities. Allowed,
   capped: one implementation per behavior (never two pop games / four breathing screens).

Never add: badges, points, levels, leaderboards, social comparison, loss-aversion streaks
[evidence: gamification null for outcomes AND adherence; competitive mechanics stress
unwell users most]. Streak-like surfaces use **presence framing**: count showing up, never
count misses, never reset, forgiveness by default.

## 3. Tokens (Android: `ui/theme/`; iOS mirrors `Theme.swift`)

- Screens read **semantic role tokens only** — `surface / surfaceRaised / surfaceField /
  line / textPrimary / textSecondary / textFaint / accent / accentSoft / danger / ok` +
  per-section accent (`Accent.home/sleep/talk/journal/breathe/crisis`). Raw hex in a screen
  file is a review-blocking defect.
- **Contrast gate:** text roles ≥4.5:1 against their surface (large display text ≥3:1).
  Check when introducing any token or pairing.
- Two themes derive from one role scale: **Night** (deep indigo, evolved brand) and
  **Dawn** (warm cream light, Phase 2). Sleep contexts always Night.
- Surfaces are **honest soft-solids** — one elevation ladder of solid fills + bevel
  hairline. No fake translucency: if a blur effect sits behind an opaque fill, delete one.

## 4. Type, motion, haptics

- Display: Newsreader serif (brand). Body/UI: system sans. Never serif for body copy.
- Motion is calm and purposeful: entrances ≤450ms gentle fade/rise, transitions
  fade+whisper-scale, looping ambience allowed only where it *is* the content (orbs, aurora).
- **Reduce Motion is a contract:** every looping/entrance animation must branch on
  `rememberReduceMotion()` (static fallback, never blank). Test exists — keep it green.
- Haptics: soft ticks for selection, one success pulse for completions. Never rapid-fire.

## 5. Components (reuse before building)

`Page` / `SubPage` frames · `SectionCard` (glass) · `PrimaryButton` (white pill + Ink) ·
`DangerButton` · `AppTextField` · `PickChip` · `AppSwitch` · `NavRow` · `ContentRow`/
`ContentList` · `NowPlayingBar` · `HeroCard` · `ShimmerBox` (loading) · `Celebration`
(app-root, notable moments only) · `BiometricGate.requestScreenLock` (the only credential
gate) · one parameterized `breathe` engine (the only breathing implementation).
Duplicating any of these is a defect. New shared patterns go in `Common.kt` with tokens.

## 6. Screen anatomy defaults

- One primary action per screen; it gets the white pill. Everything else is quiet.
- Tab pages: eyebrow + serif title, then content by descending daily relevance.
- Every async surface has all four states designed: loading (Shimmer), empty (honest,
  warm), error (plain words + retry), content.
- Touch targets ≥48dp. Text fields show lavender focus. Destructive = two-step + Danger.
- State that a user typed survives rotation/process death (`rememberSaveable`) — losing
  user writing is the worst defect class in this app.

## 7. Copy tone

Lowercase-calm, second person, no exclamation marks, no guilt ("you missed…" is banned),
no medical verbs (diagnose/treat/cure), no dark-pattern verbs (hurry/last chance/don't
lose). Crisis copy: plain, direct, warm — "real people, 24/7."

## 8. Consent, subscription, disclosure ethics

- Consent: nothing pre-ticked, all categories shown (DPDP "specific and informed"),
  toggles revert + surface an error if the server write fails (never lie about state).
- Subscription surfaces must pass the OECD six-indicator audit [evidence: ~76% of apps
  fail]: full price pre-purchase, no preselected plan, cancel as easy as subscribe,
  no countdowns/guilt.
- AI disclosure: persistent pill on chat + periodic full sheet (already implemented — keep).
- Analytics: anonymous, opt-out surfaced, no content ever.

## 9. India-first

- Tele-MANAS 14416 leads every crisis surface; offline-safe helplines.
- 13-language DPDP notice exists; new user-facing strings must be externalized
  (localization to Hindi is the roadmap differentiator — 2.9% of market has it).
- Family-context stigma: privacy affordances (journal lock, discreet notifications
  "A moment for you" — never mention mental health on the lock screen).
