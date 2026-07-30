# CereBro — B2C Feature Backlog

> Candidate features for the **consumer** product, filtered from a second CereBro
> codebase at `~/Desktop/workspace/cerebro` (2026-07-30 comparison). That repo is a
> **different product on a different domain** (`cerebrolearning.com`, 6 repos, Vite +
> Expo + FastAPI, 111 API domains) — it is not an older version of this one, and nothing
> here is a merge. It is a source of *designs already worked out elsewhere*.
>
> Companions: [PRD.md](PRD.md) (what ships today, with honest status),
> [ARCHITECTURE.md](ARCHITECTURE.md) (cross-stack contracts), [TODO.md](TODO.md).

## 0. What this doc does and does not claim

**Does:** list features that (a) make sense for a B2C-only product, (b) do not already
exist here, and (c) have a worked-out shape in the sibling repo worth copying.

**Does not:** claim any of it *works* there. This was a read of routers, models and
screen names — **the sibling's 450 test files were not run, and no implementation was
read end to end.** "It exists there" is not "it is proven there". Every scope estimate
below is the work *here*, and is larger than it looks in the sibling because this repo
ships four clients against hand-duplicated contracts (ARCHITECTURE §Cross-stack
contracts), where the sibling has one Expo client.

## 1. Scope decision: B2C only

Two whole categories of the sibling are excluded by that decision — roughly 25 of its
111 domains before any prioritising:

| Excluded | Domains | Why |
| --- | --- | --- |
| **B2B / HR plane** | `organizations`, `departments`, `hris`, `scim`, `sso`, `rbac`, `org_analytics`, `executive`, `culture`, `exit_interviews`, `leave`, `wellness_pulse`, `hr_recommendations`, `cohorts` | Out of scope by decision |
| **Clinical / regulated** | `therapists`, `therapeutic_alliance`, `ehr`, `insurance_claims`, `intake`, `psych_profile`, `medical_docs`, `medical_profile` | Contradicts this product's stated line — "wellness support, not therapy, diagnosis, or a crisis service" — and App-Store-guidelines-first. PRD §2 already calls coach/therapist booking **"the sharpest honesty risk in the product"**; importing a booking plane makes that worse, not better |

Anything that survives still has to pass the existing bar: it degrades cleanly without
keys, safety never blocks, and copy states only what ships. That last one is now gated
again: `docs/CLAIMS_MAP.md` + `scripts/check-claims.mjs` were revived 2026-07-30 and run in
CI, widened from the sibling's web-only scan to all four clients — because every over-claim
actually found here lived in Android `strings.xml` or Swift, which the original would have
missed.

## 2. Tier 1 — each closes a gap this repo's own PRD documents

### 1.1 Persisted, addressable memory (`memory`)

**The gap here.** PRD §2 "Memory detail / edit / delete" is 🟡 with an unusually blunt
note: granular editing *"is not merely unbuilt — it is not implementable against the
current schema: patterns are computed on the fly and never persisted, so there is no
addressable row to edit or suppress"*. Today the only control is
`DELETE /users/me/memory` — an all-or-nothing wipe.

**The shape there.** `domains/memory` persists a `ContextMemory` row per remembered
item: `body`, `salience` (0–1), `source` (default `manual`), `expires_at`,
`dismissed_at`, `created_at/updated_at`, owned by `user_id`. Routes are
`GET /memory`, `PATCH /memory/{id}`, `DELETE /memory/{id}`.

**Why it is first.** It is a *schema* answer, not a feature port, and it unblocks a
promise the Pattern Dashboard already makes on three clients ("You can edit or delete
any of it"). `expires_at` + `dismissed_at` also give memory a decay story, which fits
consent-first framing better than permanent recall.

**Scope here.** New `memory` model + Alembic revision · new router (or extend
`users.py`) · write path from the existing pattern miner (`services/insights.py`
`compute_patterns` currently returns computed statements — decide what gets persisted
and what stays derived) · per-item edit/delete UI on iOS `PatternsView`, Android and
web `/patterns` · a firewall test proving memory rows are counts-never-content safe on
the admin side. **Open question for the owner:** persisting what the AI inferred is a
privacy posture change — it must be consent-gated (`ai_memory`) and exportable.

### 1.2 Interventions that act on patterns (`recommendations` + `interventions`)

**The gap here.** PRD §3 "Deliberately deferred" names it exactly: *"an interventions
engine that acts on mined patterns (today patterns are display-only)"*.

**The shape there.** `Recommendation` + `PracticeCatalog` models with a real feedback
loop — `GET /recommendations/mine`, `POST /mine/seed`, `POST /{id}/accept`,
`POST /{id}/dismiss`, plus admin acceptance analytics and a curated practice catalogue
(`GET/PATCH/DELETE /admin/practices/{slug}`). Separately `interventions` holds
`InterventionRule` + `InterventionRecommendation` — a rule engine.

**Note.** In the sibling's own clinical-safety review, `interventions.*` is in the
**always-excluded `destructive` tier** for agentic tool use. Copy the accept/dismiss
loop and the practice catalogue; do not hand the rule engine to the Oracle.

**Scope here.** Two models + migration · router · seed the practice catalogue in
`seed.py` · surface on the Pattern Dashboard and the daily plan · accept/dismiss
telemetry through the existing `/events` allow-list.

### 1.3 Personal safety plan (`safety_plans`) — **highest-value, highest-care**

**The gap here.** This repo has region-aware crisis resources, a trusted contact and
consent-gated escalation, but **no personal safety plan**.

**The shape there.** `SafetyPlan` is the six-section **Stanley-Brown Safety Planning
Intervention**: `warning_signs`, `internal_coping`, `social_distractors`,
`social_support`, `professionals`, `means_safety`, plus `notes`, `version` and
`archived_at` (versioned, archive-not-delete). Routes: `GET /me`, `GET /me/history`,
`DELETE /me`, `GET /me/pdf`.

**The thing to notice before copying.** There is **no POST/PUT** — the plan is not
user-authored. It is written by `app/ai/agents/risk_classifier.py`, i.e. **an AI writes
the user's safety plan**. That is a clinical-safety decision, not an implementation
detail, and the sibling has a whole `docs/clinical-safety-review-agentic-writes.md`
gated on sign-off before rollout.

**Recommendation:** take the *schema and the PDF export*, not the authorship model.
Ship it **user-authored** (the AI may suggest into fields the user edits and confirms),
which matches this repo's existing confirm-before-write Oracle pattern and its
non-clinical line. A means-safety section authored by a language model is exactly the
thing the safety posture here exists to prevent.

**Scope here.** Model + migration · router · a guided authoring flow on iOS/Android/web ·
PDF export (new dependency — or reuse the existing export path) · offline availability
(a safety plan is worthless if it needs a network) · a row in the cross-stack contract
table · crisis-flow tests.

### 1.4 Weekly digest delivery (`digest`)

**The gap here.** Weekly insights are computed and rendered, but never *delivered* —
`services/nudges.py` does contextual and bedtime nudges only; there is no weekly digest.

**The shape there.** `WeeklyDigest` model, `GET /digest/me` + `GET /digest/me/history`.

**Why it is cheap here.** It rides on infrastructure that already exists: the insight
computation (`services/insights.py`), the dispatcher, and the web-push → email fallback
chain. Mostly a scheduled job plus a template.

## 3. Tier 2 — the consumer habit loop (the weakest area here)

There is an agentic daily plan and a streak, but **nothing the user defines**. Onboarding
captures goals as strings that never become anything trackable. No `Habit` or `Goal`
model exists in `backend/app/models/`.

| Feature | Shape in the sibling |
| --- | --- |
| `habits` | `Habit` + `HabitCompletion`; full CRUD + `GET /{id}/completions` |
| `goals` | `Goal` with CRUD **and `POST /{id}/decompose`** (goal → steps, the interesting part — it connects to the existing agentic planner) |
| `rituals` / `morning_rituals` | Daily intention (idempotent upsert per UTC day), gratitude, breathing-session records |
| `commitments` | `GET /commitments/me` — user-made commitments |
| `affirmations` | `GET /affirmations/today` + favourites; small and cheap |

**Sequencing note:** `goals.decompose` is the one with leverage — it turns the static
onboarding goals into input for the daily plan that already exists.

## 4. Tier 3 — skills content (needs framing work, not new architecture)

`dbt_skills` (catalog + practice log; TIPP ships on Android only here) · `mbct`
(curriculum + enroll + session completion) · `behavioral_activation` (activity
scheduling) · `role_play` (scenario practice) · `guided_imagery` · `dreams` (entries +
async LLM theme extraction).

These extend what already ships (CBT reframe, grounding, breathing, TIPP) and mostly
reuse the `/content` + `/programs` machinery. **Each needs the non-clinical framing pass**
— an "MBCT curriculum" with enrollment and session completion reads as delivering a
treatment protocol. Ship as *skills practice*, keep the "why this works" provenance copy,
and give each a PRD row stating exactly what is and is not claimed.

## 5. Flagged — do not start without a decision

- **`gamification` (XP) + `rewards` + leaderboard.** Buildable, but the premium/engagement
  surfaces here are gated behind an **OECD dark-pattern checklist** (TODO.md, Phase 3),
  and a *leaderboard on mental-health activity* is close to what that checklist exists to
  stop. Recommend XP/streak-adjacent progress only, **no social ranking**.
- **`buddy_pairs` / `groups` / `live_groups` / `posts`.** Largest lift and largest
  liability. Peer support in a crisis-adjacent product is a 24/7 human-moderation
  commitment, not a `moderation` module. `buddy_pairs` does have one good idea worth
  stealing independently — anonymous handles (`_anon_handle`). **Recommend deferring the
  category entirely** rather than half-shipping it.

## 6. Worth stealing on principle — `graduation`

A deliberate **off-ramp**: `GET /graduation` plus a closure-actions catalogue, helping a
user finish and leave. It is the opposite of engagement farming and lines up with the
anti-manipulation stance already written into the docs here. Small, and genuinely
differentiating in a category where every competitor optimises for retention.

## 7. Recommended first slice

**`memory` (1.1) + `safety_plans` (1.3), backend-first.**

- Both close gaps this repo has already written down, so neither needs a new product
  decision to justify.
- Both are schema-first: the backend can land with tests, and iOS/Android/web can follow
  at their own pace behind the existing "degrades cleanly" rule.
- They are independent of each other, so they can go in either order or in parallel.

Both need one owner decision before code:
1. **memory** — persisting AI-inferred statements is a privacy-posture change. Consent
   gate, retention/expiry, export and delete all have to be answered first.
2. **safety_plans** — user-authored (recommended) vs AI-authored (as the sibling does).

## 8. Not carried over from the comparison

`bandit` / `experiments` (A/B infrastructure — premature at this stage), `agent_traces` /
`trace_preferences` (observability, worth revisiting when the Oracle is under real load),
`knowledge_curation` / `learning_board` / `topic_planner` (content-ops tooling — the admin
CMS here already covers the need), and the psychosynthesis track (`act_of_will`,
`will_steps`, `subpersonality`, `disidentification`, `zow`, `spiritual_development`).
That last one is a coherent product philosophy in the sibling, but it is *a different
product's* philosophy — adopting it piecemeal would blur what this app is.
