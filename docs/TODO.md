# TODO — Build Plan

Last updated: 2026-07-14. Format follows `ref/Zen/docs/TODO.md`: checkboxes,
dated notes, grouped by priority; items needing an owner decision are marked
**[decision]**.

## P0 — before any product code

- [ ] **Rotate the OpenAI key found in `ref/Agent/.env`** (and any other
      credentials in `ref/**/.env*`). Treat all committed reference secrets
      as compromised. Never load a `ref/` env file.
- [x] 2026-07-14 — Repo consolidated: `git init` at root, initial commit
      `52dd2ba` (348 files, clean tree; secrets audit passed — only
      `.env.example`/`.env.offline`/`.env.demo` placeholders tracked).
      `apps/web`'s pre-monorepo history preserved locally at
      `apps/web/.git.pre-monorepo-backup`. `ref/` is UNTRACKED pending the
      licensing decision below — flip the `.gitignore` entry if it clears.
      Also purged 2.7MB of dead soundscape audio the Android strip left in
      res/raw. Next: add a remote + push; then the CI skeleton.
- [x] 2026-07-14 — Root scaffolding: `.gitignore`, `README.md`, `CLAUDE.md`,
      `design/tokens.css` seeded from `apps/web` globals, `scripts/sync-tokens.mjs`
      with `--check` (passing, 17 tokens), full directory skeleton
      (`apps/{web,android,admin}`, `services/{engine,platform}`, `design`,
      `scripts`, `e2e`, `deploy` — placeholders carry READMEs), and the
      `Apps`→`apps` case normalization (Linux CI safety).
- [x] 2026-07-14 — CI skeleton: `.github/workflows/ci.yml`, six hermetic
      jobs — engine (pytest + 96% branch gate, pgvector service container),
      platform (pytest + 95% gate), web (lint + build), admin (build),
      android (assemble + `check`: tests, JaCoCo 95%, lint), hygiene
      (token-drift check + gitleaks over full history). All commands mirror
      what was verified locally; `gradlew`'s executable bit fixed in the
      index (Windows checkout trap). Goes live on first push to a remote.
- [ ] **[decision] Engine licensing/provenance**: confirm we have the right
      to reuse `ref/Agent` and `ref/Zen` **code** for this product (both
      carry another project's history; `ref/Agent` docs reference an
      incumbent client contract under legal review). This gates everything
      below. Prompt **content**: owner decided (2026-07-14) to use the
      reference prompts as the working base — extracted verbatim to
      `docs/prompts/`, adapted per `docs/PROMPTS_SPEC.md`. The provenance
      concern is recorded there; confirming content rights is part of this
      same legal check.
- [ ] **[decision] Coaching author**: identify who writes the coaching
      method/content (qualified coach on team, contractor, or founders +
      review). The prompt spec is ready; the words need an owner.

## Phase 1 — Foundations (services boot, nothing user-visible)

- [x] 2026-07-14 — Engine adopted: `ref/Agent` → `services/engine` (app,
      tests, evals, scripts, technical docs; no secrets/DBs), single-sweep
      rename complete (938 replacements, zero brand terms remain), 4 latent
      bugs fixed (console 500-before-validation, console model fallback,
      sessions-list sort tiebreak, two Windows/timing test bugs — see
      `services/engine/docs/FORK_NOTES.md`). **1,443 tests passing, 98.66%
      branch coverage, /health green with the CereBroZen workbook fork.**
- [x] 2026-07-14 — Engine: Postgres-first storage. One `POSTGRES_URL` now
      drives everything: document stores via the pg shim (already the single
      seam), LangGraph checkpointer (already Postgres-first), and pgvector
      as the **default** RAG backend (LanceDB/S3 is an explicit opt-out).
      Compose stack is now pgvector+redis (Mongo behind a `--profile mongo`
      flag). Fixed three pg-shim gaps found on the way: `$and`/`$or` filter
      support (tenancy's scoped() shapes returned nothing on PG), pymongo
      list-form `.sort()`, and a missing `.skip()` (paged session lists were
      silently empty on PG even in the reference). Verified end-to-end on
      real Postgres: PgClient stores with cross-org isolation, PostgresSaver
      checkpointer, pgvector enabled. Suite: 1,456 passing, 98.67%.
- [x] 2026-07-14 — Engine: app-layer tenancy. `app/tenancy.py` (org
      contextvar + `scoped()` filters), org stamped from the JWT `org_id`
      claim (org-less tokens 401 by default, `CEREBROZEN_REQUIRE_ORG_CLAIM`
      opt-out), all engine stores scoped (transcripts, agentic, dynamic
      vars), Redis keys and checkpointer thread ids org-prefixed, business
      key now (org_id, session_id) with compound unique index, legacy
      org-less docs owned by the default org only. 13-test cross-tenant
      class in `tests/test_tenancy.py`. Suite: 1,456 passing, 98.67%.
      Note: the external `users` profile read stays user-keyed (documented
      in `app/tenancy.py`) — per-tenant user provisioning is the platform's
      contract, formalized in the Phase 1 platform work.
- [x] 2026-07-14 — Platform service built (`services/platform`): FastAPI +
      async SQLAlchemy (SQLite dev / Postgres deploy), auth with single-use
      refresh rotation + reuse detection (reuse revokes all sessions),
      org/seat/invitation lifecycle with hard seat limits, roles enforced in
      dependencies, deletion with PII scrub + no-PII ledger, export, demo
      pipeline, prod boot-guard, dev seed. **41 tests, 98.7% coverage**
      (gotcha fixed: coverage needs `concurrency=greenlet` for SQLAlchemy
      asyncio or it loses everything after the first awaited DB call).
- [x] 2026-07-14 — JWT contract platform↔engine verified END-TO-END: a real
      platform-issued token (HS512, shared base64 secret, `org_id` +
      `user.username` claims) accepted by the real engine (200); tokenless
      request 401. Deviations noted in `services/platform/README.md`
      (PBKDF2 not bcrypt; Alembic deferred while greenfield).
- [ ] `docker-compose.yml`: postgres + redis + engine + platform + web;
      clean-clone boot with zero keys (mock provider).
- [x] 2026-07-14 — Prompt workbook forked to
      `services/engine/agent_prompts.xlsx`: 15 live agents (legacy sheets
      dropped), sheet names normalized, rebrand sweep verified (zero
      agentman/sherlock/xcalibrate leaks), clean Catalog with explicit
      environment row, environment wrapper rewritten 45,014 → 1,739 chars,
      extraction + dynamic-variables tabs carried over.
      Still open from this item:
  - [ ] Content adaptation of the big agents (CH 70.9K, core 39K → ≤8K
        target per `docs/PROMPTS_SPEC.md`) — coach-review release condition.
  - [ ] Port evals golden cases; workbook-loadability CI gate (needs the
        engine adopted first).
- [x] 2026-07-14 — Regulated mode is now the DEFAULT (unset env → emotion
      inference and person-scoring OFF; `CEREBROZEN_REGULATED_WORKPLACE=false`
      is the conscious contract-level opt-out). Test suite pins the
      full-feature baseline; the default's direction and the store refusals
      are asserted in `tests/test_regulated_workplace.py` (17 tests).
      Also fixed on the way: a latent test-seam bug — four store modules
      early-bound `get_client`, so a store first imported during a patched
      test froze that test's client forever (order-dependent poisoning);
      all stores now late-bind through the module seam.
- [x] 2026-07-14 — **[decision] Model provider: OpenAI** (owner call).
      No Anthropic provider; cloud = OpenAI cascade, air-gap = Ollama.
      ARCHITECTURE/SECURITY updated — no multi-vendor fallback is claimed.

## Phase 2 — Admin (ops first, HR second)

- [x] 2026-07-14 — Admin app built (`apps/admin`): Next.js 16, zero runtime
      deps beyond next/react, tokens synced from `design/tokens.css` (2nd
      sync consumer, drift check passing), `lib/api.ts` with coalesced
      refresh rotation (single refresh shared across concurrent 401s — a
      second refresh would trip the platform's reuse detection by design),
      role-gated tabs. org_admin: Overview/People/Invite. internal_admin:
      Tenants (create/toggle, regulated-ON default surfaced)/Demo requests.
      Verified live against the platform (login → me → create tenant →
      list). Deviation: security headers via next.config, nonce-CSP deferred
      (below).
- [ ] Admin: nonce-CSP middleware (the ref/Zen pattern) — deferred from the
      skeleton; add before production exposure.
- [ ] Ops tabs, remaining: Prompt workbook (engine `/v1/prompts` API),
      Safety queue (engine escalations).
- [ ] Wire marketing `/api/demo` → platform demo-requests table (email
      delivery stays as fallback).
- [x] 2026-07-14 — HR analytics with the k-anonymity floor: first-party
      activity ingest (`POST /events/coaching`, kind whitelist = the content
      firewall; members report with their own JWT), aggregates at
      `GET /orgs/me/analytics` — active users, sessions started/completed,
      commitments made/kept, rates — with every behavioral metric suppressed
      below `CEREBROZEN_COHORT_FLOOR` (default 8) in the aggregation layer.
      Rates inherit their weakest component's cohort (a rate over a
      suppressed count would leak it). Admin gains the Analytics tab with a
      suppression explainer. 6 tests incl. the rate-leak case; 53 passing.
      Still open: Android event wiring (Coach/ActionsStore fire the beats);
      Programs tab; Rollout/CSV.
- [ ] Ops: per-org knowledge base (CSKB) management — curated upload +
      reindex + health view ("Tuned to Your Culture" mechanism); self-serve
      customer upload stays gated behind injection framing (SECURITY.md).
- [ ] Admin e2e specs (Playwright), seeded dev logins.

## Phase 3 — Android app

- [x] 2026-07-14 — App adopted from `ref/Zen/apps/android` (83 Kotlin files,
      already `com.cerebrozen.app`): Compose NavHost, zero-SDK `Session.kt`
      transport, theme system, JaCoCo gate all inherited. Windows
      `local.properties` written (SDK at `%LOCALAPPDATA%\Android\Sdk`; JDK =
      Android Studio JBR). Baseline `assembleDebug` verification in progress.
- [x] 2026-07-14 — Retab + transport done, 216 unit tests green:
      five coaching tabs (Today/Coach/Journeys/Actions/You) wired in
      `CereBroApp.kt`; new `CoachHome.kt` (TodayHome + ActionsScreen +
      persisted ActionsStore — commitments with open/done lifecycle);
      Journeys = ProgramsScreen; legacy routes kept as pushed aliases so
      inherited screens don't crash. Transport: `API_BASE_URL` → platform
      :8100 (login/refresh already matched the platform contract exactly),
      `/users/me` path fixed, new `ENGINE_BASE_URL` BuildConfig (:8000 dev,
      api.cerebrozen.in/engine prod). Sleep scene-video/forceNight
      special-casing removed from the shell.
- [x] 2026-07-14 — Coach tab → engine wired, 219 unit tests green. New
      `net/Coach.kt` (engine client over the existing sseExec seam:
      start → turn sequencing, session-id capture from `done`) and
      `ui/screens/CoachScreen.kt` (streaming chat UI; action cards from
      `done` land in ActionsStore + Today count; graceful unreachable
      state). `Session.sse()` gained a base-URL param (ENGINE_BASE_URL).
      3 new tests verify against the exact engine frame format
      (`data: {"type": status|token|done}`). Old TalkScreen stays on
      legacy routes until the B2C strip. Still open: emulator run against
      live engine; commit-gate UX polish (phase cards, mood capture).
- [x] 2026-07-14 — Coral theme pass: BrandPrimary → zen-500 #F56B6B; night
      text tier #F58A8A (~7:1), dawn text tier #B03A3A (~5:1 worst ground);
      CTA pill deepened (#C64444/#B03A3A) so its white label clears AA; nav
      selection wash uses zen-700 on night for the same reason; Coach accent
      = brand coral. Every pairing machine-verified by ContrastTest in both
      themes (all ratio checks passed first run; only the brand spec-pin
      needed its expected values updated).
- [x] 2026-07-14 — B2C strip done, build + tests green: deleted audio/*
      (15 files), health/, 12 B2C screens (Sleep/Talk/Today/Journal/Games/
      Tools/Breathe/Search/SceneVideo/Plan/Pattern), their routes/imports,
      the MediaCatalog/Sfx warm-up, manifest audio services, and 7 stale
      test files. Kept-feature code that lived inside stripped files was
      recovered intact from the reference (CrisisScreen + SupportLinkRow +
      support-target helpers → new Crisis.kt; Extras.kt rebuilt to exactly
      Insights+Programs+shared heroes). Onboarding's breathe demo replaced
      with a static beat (coaching moment TBD). App is now 33 main / 11
      test Kotlin files, 83 unit tests.
- [x] 2026-07-14 — JaCoCo gate re-scoped and GREEN: 95.24% (861/904 lines,
      gate 95%) via `:app:check`. Includes list cleaned of deleted
      audio/health entries (scope now net/notify/ui.theme + ConsentNotice/
      Stores); the strip had orphaned ConsentNotice's coverage — its
      exercising tests lived in the half-B2C ScreenLogicTest — so the
      keep-half (DPDP notice language routing, six-categories invariant,
      reduce-motion rule) was recovered as `KeptLogicTest.kt`.
- [ ] Android tail: emulator smoke run against live platform+engine;
      coaching tab icons; Play readiness runbook (adapt ANDROID_RELEASE +
      PRIVACY_LABELS from the reference docs).
- [ ] Auth screens: email/password + OTP (SSO later).
- [ ] Coach tab: session UI over engine SSE (`status/node/token/done`),
      phase cards, action-card save/skip, commit gate UX, mood capture.
- [ ] Today tab: check-in entry, open actions, next nudge.
- [ ] Actions tab: commitment lifecycle, 7-day follow-through prompts.
- [ ] Journeys tab: program list/enrollment/day view (platform-served).
- [ ] You tab: profile, my-insights view (patterns, session history with
      titles — engine endpoints exist), privacy center (export/delete,
      "what your employer sees"), appearance, reminders.
- [ ] Intake personalization: thinking-style seed questions wired to the
      engine's NBI/DISC profile stores.
- [ ] Crisis + HumanSupport screens (ported nearly unchanged); regional
      helpline config from engine, never hardcoded.
- [ ] Notifications: reminder + nudge channels.
- [ ] Play readiness: adapt `ref/Zen/docs/ANDROID_RELEASE.md` +
      `PRIVACY_LABELS.md` (data-safety honesty).

## Phase 4 — Compose it, prove it, ship it

- [ ] e2e suite: composed Docker stack, specs for the full employee journey
      (signup → session → commit → action follow-up), HR aggregates with
      cohort floors, ops workbook flow, cross-tenant denial.
- [ ] Evals harness adopted + nightly; crisis red-team wired as release gate;
      publish the real catch-rate (with classifier) on the Evidence page.
- [ ] Prod deploy: Caddyfile (web/app/admin/api subdomains, security
      headers), `docker-compose.prod.yml` (no host-published DB, expose-only
      services), backup/restore for Postgres, OTel + Prometheus dashboards,
      breach runbook adapted and rehearsed.
- [ ] Marketing site truth pass: every number/claim re-checked against what
      the product now actually measures (PRODUCT.md success-metrics rule).

## Phase 5 — Enterprise readiness (first real tenant gates)

- [ ] SSO (OIDC/SAML) + SCIM or CSV seat provisioning.
- [ ] App-layer encryption for transcript content at rest (closes the
      inherited compliance gap before we claim it).
- [ ] Crisis classifier measurement program; native-speaker + clinical
      review pipeline for localized safety strings.
- [ ] Escalation-to-human end-to-end (webhook → safety queue → contact
      procedure) — only then may marketing claim it.
- [ ] Retention windows per tenant; DPDP checklist adapted from
      `ref/Zen/docs/DPDP_COMPLIANCE.md`; DPIA template for EU customers.
- [ ] Air-gapped profile: prompt-size reduction, quality eval vs cloud model
      (with the design partner, per the Evidence page's honest framing).
- [ ] Web app (`apps/app`) for desktop employees — the Zen reference proves
      the pattern; Android contract makes it mostly a port.
- [ ] Voice coaching: bring the engine's voice stack (LiveKit/STT/TTS) under
      test coverage, then ship as a differentiator (v2 — see PRODUCT.md
      feature matrix).
- [ ] v2 candidates per feature matrix: session time-travel edit, org
      assessment import (DISC/NBI), journaling, micro-content library.

## Inherited-risk register (watch these; sources: ref docs + our review)

| # | Risk | Source | Owned in |
|---|---|---|---|
| R1 | Crisis lexicon catches ~1/22 implicit disclosures | `ref/Agent/docs/TODO.md` 0.0 | Phase 4 gate, Phase 5 program |
| R2 | Tenancy by deploy-config, hardcoded incumbent resource names | `ref/Agent/docs/WHITE_LABEL.md` | Phase 1 |
| R3 | RAG silently degraded (0 rows) → coaching never evaluated with KB | `ref/Agent/docs/TODO.md` 1.7 | Phase 1 storage + Phase 4 evals |
| R4 | Oversized prompts (70K-char agent, 45K-char env wrapper) — cost/latency, blocks offline | `ref/Agent/docs/TODO.md` 2.2 | Phase 5 air-gap work |
| R5 | Model-id config contradicts "Catalog is source of truth" | `ref/Agent/docs/TODO.md` 1.8 | Phase 1 adoption sweep |
| R6 | Public claims vs code (Anthropic fallback, at-rest transcript encryption, SOS escalation) | `ref/Agent/docs/COMPLIANCE_GAPS.md` | SECURITY.md mapping; Phases 1/5 |
| R7 | Prompt-injection surface if self-serve KB upload ships | `ref/Agent/docs/TODO.md` 4 | Gated feature, SECURITY.md |
| R8 | Half-done rename breaks voice/UI topics silently | `ref/Agent` migration notes | Phase 1 (single sweep rule) |
| R9 | `nodes.py::_run_stage` god-function (~700 lines) | ref review | Refactor opportunistically, never during a feature PR |
| R10 | No licensed media; teardown assets must not ship | `ref/Zen/docs/TODO.md` | N/A for v1 (audio dropped) |

## Done

- [x] 2026-07-14 — Marketing site reviewed, fixed (consistency, a11y, SEO,
      no-JS fallback), demo form wired to SMTP email, legal pages written,
      illustrative disclaimers added.
- [x] 2026-07-14 — Deep review of `ref/Agent`, `ref/Zen`, and
      sherlockperformance.com; this docs set created.
