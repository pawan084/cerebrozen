# Product Definition

Last updated: 2026-07-21

## One-liner

An AI coaching platform: people get a private, always-on performance coach in
the moments that matter; where an employer is the buyer, HR and leadership get
aggregated, anonymized behavioral analytics — never transcripts.

**Two commercial models, one product.** B2B enterprise seats (the original and
still the P&L) and, since 2026-07-19, B2C freemium. They share every mechanism:
the same coaching graph, the same safety code, the same privacy schema. What
differs is who the "org" is and who pays.

## The B2B model (patterned on sherlockperformance.com)

- **Buyer**: CHRO, L&D, business leaders at organizations of 500+ employees.
- **User**: every employee — ICs, managers, high-potential talent.
- **Sales motion**: demo-gated enterprise subscription, invoiced seat
  licensing. **Enterprise** pricing is not public; the marketing site's job is
  to earn the demo request. (Consumer pricing *is* public — see below.) Our
  differentiator on top of Sherlock's positioning: *verifiability* — routing an
  auditor can read, tests run in front of the customer's DPO, air-gapped
  deployment, and the Evidence page's honest-numbers posture.
- **Trust is the product**: employees only use a coach they believe is
  private; buyers only approve one that survives a security review. Every
  product decision is subordinate to those two facts.

## The B2C model (freemium, added 2026-07-19)

- **Why at all**: run as a **B2B2C funnel and evidence engine, not the P&L** —
  the pattern Wysa, Headspace and Calm all use (free consumer app → enterprise
  money). Measure it as top-of-funnel, not as a profit centre.
- **Why it's defensible**: the line governing legal exposure is **functional,
  not contractual**. An employer intermediary is no shield if you act as
  therapy; conversely, non-clinical + disclaimers + deterministic crisis
  routing + human handoff + 18+ + no disease claims is defensible on either
  side. We already have that posture, which is why B2C did not require a
  safety redesign.
- **Tenancy**: `POST /auth/signup` mints a **personal org-of-one**
  (`is_personal_org`, slug `personal-*`, one seat). Personal orgs are excluded
  from the ops-admin tenant list; consumer scale is visible only as counts.
- **Tiers**: Free · **Plus $9.99/mo or $59.99/yr** (public on `/pricing`) ·
  enterprise. Entitlements resolve from the DB and ride the `plan` JWT claim so
  the engine can gate offline; free coaching is capped at **5 turns/day**.
- **The hard rule**: never drift into **companion framing**. A simulated
  sustained emotional relationship is precisely what the consumer AI statutes
  target. Our commit-gate and action-orientation are what keep us the other
  side of that line — protect them. **Since 2026-07-21 the rule is also code**,
  not only intent: `guardrails.NON_COMPANION` rides every turn's system prompt
  (in code, outside the editable workbook, because "warmer" is the likeliest
  direction for a prompt edit to drift), and `safety/boundaries.py` forces a
  disclosure into any turn where the user treats the coach as a person, a
  relationship, or a clinician. `/v1/governance` attests it; the
  `cerebrozen_boundary_prompted_total{kind}` counter is the drift telemetry —
  a climbing `attachment` count means people are using this as something we do
  not sell, which is a product signal before it is a compliance one.

## Surfaces

| Surface | Users | Purpose | Status |
|---|---|---|---|
| Marketing site (`apps/web`) | Prospects | Positioning, evidence, public consumer pricing, demo requests | **Built** |
| Android app (`apps/android`) | Employees **and consumers** | The coach: sessions, actions, journeys, check-ins; onboarding, paywall, restore | **Built** — 5 tabs, 39 routes; device-verification status in [ANDROID_QA.md](ANDROID_QA.md) |
| Web app (`apps/app`) | Employees | Browser client at `app.cerebrozen.in`, same API | **Built** |
| HR portal (`apps/admin`, org role) | Customer HR/L&D | Aggregate analytics, program management, rollout tools | **Built** |
| Ops admin (`apps/admin`, internal role) | CereBroZen staff | Tenant management, prompt workbook, safety queue, demo pipeline, consumer-stats tile | **Built** |

The HR portal and ops admin are one Next.js app with role-gated tabs (the
`ref/Zen` admin pattern), not two codebases.

## Core product capabilities

### For the employee (Android first)

1. **Coaching sessions** — the governed arc from the AgentMan engine: intake
   or returning-user check-in → challenge context → coaching (CIM/CBT/CH
   paths) → role-play/simulation when practice beats advice → commit gate (a
   session cannot close without a saved action) → mood/feedback capture.
2. **Actions** — every session ends in concrete commitments; the app surfaces
   them, nudges follow-through, and the coach asks how it went in ~7 days.
3. **Journeys** ("Coaching Horizons") — multi-week structured programs:
   first-time manager, feedback, delegation, well-being/resilience.
4. **Check-ins & nudges** — two-minute prompts before the moments that matter
   (meetings, deadlines), plus reminder notifications.
5. **My insights** — the employee's own view of their patterns: actions
   kept vs dropped, themes across sessions, session history with generated
   titles (engine endpoints exist: actions-insights, history, title).
6. **Personalized coaching style** — the engine adapts to thinking-style
   profiles (NBI/DISC stores already supported); v1 seeds this from intake
   questions, org-level assessment import is v2.
7. **Privacy controls** — export and delete are product functions in the app
   (not support tickets); a clear "what your employer sees" screen.
8. **Crisis safety** — deterministic takeover with regional helplines; the
   coaching model is never in the loop for a crisis reply.
9. **Progress, not gamification** — streaks and journey progress are shown
   calmly; **no coins, badges, or leaderboards** (deliberate contrast with
   Sherlock's coins — our brand is calm and anti-gimmick, and leaderboards
   contradict "counts never content" instincts even when technically private).

### For the customer organization (HR portal)

1. **Aggregate analytics only** — engagement, action completion, well-being
   trends by cohort/program. Counts and trends, never content; minimum cohort
   sizes enforced server-side (see SECURITY.md).
2. **Program management** — which journeys are live, enrollment, completion.
3. **Rollout tools** — seat provisioning (CSV/SSO later), branding hooks.
4. **Company coaching context (v1 curated, v2 self-serve)** — the per-org
   knowledge base (engine CSKB) that grounds coaching in the customer's own
   leadership frameworks and values — the mechanism behind the site's
   "Tuned to Your Culture" claim. v1: we curate and load it per customer;
   self-serve upload waits for the prompt-injection guardrails (SECURITY.md).

### For CereBroZen internally (ops admin)

1. **Tenant management** — orgs, seats, config (regulated mode, crisis
   region, model provider).
2. **Prompt workbook** — the versioned coaching-content registry with
   validation and rollback (AgentMan `PromptRegistry` + Zen's admin Prompts
   tab pattern).
3. **Safety queue** — review of crisis escalations.
4. **Demo pipeline** — demo requests from the marketing site.

## What we deliberately do not build (v1)

- iOS (Android first; the Zen reference proves the parity path later).
- Emotion inference and person-scoring in any EU/regulated deployment —
  regulated mode is our **default posture**, not an opt-in (see SECURITY.md).
- Marketplace integrations (calendar/Slack nudge triggers) — v2; v1 nudges
  are time/reminder-based.
- Human-coach marketplace. The product is the AI coach plus escalation paths.
- Coins/badges/leaderboards (see "Progress, not gamification" above).
- Micro-content library — a v2 candidate from the Zen reference (learning-aid
  content the `learning_aid` agent can already draw on).

**Two items have since shipped and were moved off this list** (they were
contradicted by this document's own feature matrix below, which is the
authority):

- **Journaling — shipped, v1.** Engine `stores/wellness.py`, web
  `apps/app/journal`, and Android read+write (`JournalScreen.kt`,
  `Session.createJournal`) with `BiometricGate` attached.
- **Voice coaching — partly shipped.** Engine `/voice/token` (Plus-gated) and
  the Android `CloudVoice` loop (Deepgram/ElevenLabs) with an on-device
  fallback; `voice_storage` is a live consent key. Coverage is the remaining
  gap — the engine's `.coveragerc` omits `app/voice/*` — so treat it as
  shipped-but-under-tested, not as unbuilt.

Note (2026-07-14): the well-being suite (sounds/sleep/breathe/games) was
originally cut here as "wellness-app scope" and RESTORED the same day by
owner decision — it lives behind Today's "Reset toolkit" and "Rest &
recovery" doors, not as tabs, keeping the five coaching tabs primary.

## Feature coverage matrix (sources → plan)

| Feature | Source | v1 | Later | Dropped |
|---|---|---|---|---|
| Governed session arc, CIM/CBT/CH paths | Agent | ✔ | | |
| Role-play / SJT simulation | Agent | ✔ | | |
| Commit gate + action lifecycle + 7-day check-in | Agent | ✔ | | |
| Journeys / programs | Agent (CH) + Zen + Sherlock | ✔ | | |
| Check-ins, nudges, reminders | Zen + Sherlock | ✔ | | |
| Crisis screen + takeover + human-support screens | Agent + Zen | ✔ | | |
| Employee insights (patterns, history, titles) | Agent endpoints + Zen insights | ✔ | | |
| Thinking-style personalization (NBI/DISC) | Agent stores + Sherlock (DISC/MBTI) | seeded | org import v2 | |
| Per-org knowledge base ("Tuned to Your Culture") | Agent CSKB | curated | self-serve v2 | |
| HR aggregate analytics + cohort floors | Zen admin + Sherlock Acumen | ✔ | | |
| Prompt workbook admin | Agent + Zen admin | ✔ | | |
| Safety review queue | Zen admin | ✔ | | |
| Session time-travel edit/fork | Agent (`?edit=`) | | v2 power feature | |
| Voice coaching | Agent voice stack + Zen voice loop | | v2 (after tests) | |
| Journaling | Zen | ✔ engine (`stores/wellness.py`) + web (`apps/app/journal`) + **Android read+write** (`JournalScreen.kt`, 2026-07-17 — the read view the B2C strip deleted; `BiometricGate` is attached to it again, so Settings' `journal_locked` toggle finally locks something) | | |
| Micro-content / resources library | Zen content + Sherlock Resources | | v2 | |
| Web employee app | Zen `apps/app` | ✔ (built 2026-07-16, ahead of Phase 5) | | |
| Gamification coins | Sherlock | | | ✔ deliberate |
| Well-being suite: soundscapes/mixer, sleep scenes, breathe, reset tools, games | Zen | ✔ (owner call 2026-07-14 — reversed the v1 strip; supports the site's Well-Being pillar) | | |
| Health Connect sleep prefill | Zen | ✔ (restored with the sleep surface) | | |
| Consumer billing/premium | Zen | ✔ **CereBro Plus freemium** (2026-07-19 — B2C self-serve added alongside enterprise seats; `services/platform/app/routers/billing.py` mock provider, keyless; Android paywall `Paywall.kt`; real Stripe/Play adapters pending keys) | | ✔ seats **and** Plus |

## Success metrics (the ones the HR portal must eventually prove)

Action completion rate, weekly active coaching users per seat, commitment →
follow-through conversion at 7 days, journey completion, and aggregate
well-being/engagement trend deltas over 90 days. These mirror the claims the
marketing site makes — the product must be able to measure every number the
site advertises, or the site must stop advertising it.
