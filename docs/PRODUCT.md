# Product Definition

Last updated: 2026-07-14

## One-liner

An enterprise AI coaching platform: employees get a private, always-on
performance coach in the moments that matter; HR and leadership get
aggregated, anonymized behavioral analytics — never transcripts.

## The B2B model (patterned on sherlockperformance.com)

- **Buyer**: CHRO, L&D, business leaders at organizations of 500+ employees.
- **User**: every employee — ICs, managers, high-potential talent.
- **Sales motion**: demo-gated enterprise subscription. Pricing is not public;
  the marketing site's job is to earn the demo request. Our differentiator on
  top of Sherlock's positioning: *verifiability* — routing an auditor can
  read, tests run in front of the customer's DPO, air-gapped deployment, and
  the Evidence page's honest-numbers posture.
- **Trust is the product**: employees only use a coach they believe is
  private; buyers only approve one that survives a security review. Every
  product decision is subordinate to those two facts.

## Surfaces

| Surface | Users | Purpose | Status |
|---|---|---|---|
| Marketing site (`apps/web`) | Prospects | Positioning, evidence, demo requests | **Built** |
| Android app (`apps/android`) | Employees | The coach: sessions, actions, journeys, check-ins | To build |
| Web app (`apps/app`) | Employees | Browser client at `app.cerebrozen.in`, same API | **Built** |
| HR portal (`apps/admin`, org role) | Customer HR/L&D | Aggregate analytics, program management, rollout tools | To build |
| Ops admin (`apps/admin`, internal role) | CereBroZen staff | Tenant management, prompt workbook, safety queue, demo pipeline | To build |

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
- Voice coaching — the engine ships a full voice stack (LiveKit + STT/TTS)
  but it is the one untested subsystem in the reference; it becomes a v2
  differentiator once covered by tests, not a v1 risk.
- Coins/badges/leaderboards (see "Progress, not gamification" above).
- Journaling and micro-content library — v2 candidates from the Zen
  reference (private reflections attached to sessions; learning-aid content
  the `learning_aid` agent can already draw on).

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
| Journaling | Zen | ✔ (engine `stores/wellness.py`, Android wellness, `apps/app/journal`) — biometric lock remains v2 | biometric lock | |
| Micro-content / resources library | Zen content + Sherlock Resources | | v2 | |
| Web employee app | Zen `apps/app` | ✔ (built 2026-07-16, ahead of Phase 5) | | |
| Gamification coins | Sherlock | | | ✔ deliberate |
| Well-being suite: soundscapes/mixer, sleep scenes, breathe, reset tools, games | Zen | ✔ (owner call 2026-07-14 — reversed the v1 strip; supports the site's Well-Being pillar) | | |
| Health Connect sleep prefill | Zen | ✔ (restored with the sleep surface) | | |
| Consumer billing/premium | Zen | | | ✔ seats instead |

## Success metrics (the ones the HR portal must eventually prove)

Action completion rate, weekly active coaching users per seat, commitment →
follow-through conversion at 7 days, journey completion, and aggregate
well-being/engagement trend deltas over 90 days. These mirror the claims the
marketing site makes — the product must be able to measure every number the
site advertises, or the site must stop advertising it.
