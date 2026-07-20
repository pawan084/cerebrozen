# TODO — Build Plan

Last updated: 2026-07-14. Format follows `ref/Zen/docs/TODO.md`: checkboxes,
dated notes, grouped by priority; items needing an owner decision are marked
**[decision]**.

## P0 — before any product code

- [ ] **Rotate TWO OpenAI keys.** (1) the one in `ref/Agent/.env` — and any
      other credential under `ref/**/.env*`; treat every committed reference
      secret as compromised, and never load a `ref/` env file. (2) **the key
      pasted into a chat transcript on 2026-07-17** to give the eval harness a
      real model. It currently sits in `services/engine/.env` (git-ignored,
      verified never staged) but transcript exposure is independent of file
      permissions. Both are burned; neither is in git.
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
- [ ] **[decision] Engine licensing/provenance** → **brief written for counsel:
      [docs/LICENSING.md](LICENSING.md)** (2026-07-16). Full inventory of what was
      inherited (engine code, the 476K-char verbatim prompt extraction, the
      CIM/CBT/CH taxonomy, named third-party frameworks), the reference's own IP
      assessment, and the six questions counsel must answer. **This gates
      everything below.** Owner decided (2026-07-14) to use the reference prompts
      as the working base — that was a product decision, **not a legal clearance**.
      Next action: **get the prior client's engagement agreement reviewed**
      (work product / derived IP / non-compete) — per the reference's own docs the
      contract is "decisive, and not visible from here".
  - [x] 2026-07-16 — **Public-exposure containment**: `pawan084/cerebrozen` was
        PUBLIC (confirmed via the GitHub API) with `docs/prompts/` (476,154 chars,
        extracted verbatim) and the forked `agent_prompts.xlsx` tracked. Set
        private on discovery. Note this does NOT undo publication — forks/clones/
        caches may exist; treat the content as having been public. `ref/` itself
        was never tracked (`.gitignore:52`).
  - [ ] The prior client's name still appears in **8 tracked files** (incl.
        `docs/prompts/orchestrator.md`, `_catalog.md`, `docs/PRODUCT.md`) — the
        938-identifier sweep covered the engine, not the extracted docs. Clean up
        (or remove the extraction) once §6 of LICENSING.md is answered.
  - [ ] Rotate the `OPENAI_API_KEY` found in `ref/Agent/.env` — unconditional,
        do regardless of the licensing outcome.
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
- [x] 2026-07-14 — Root `docker-compose.yml`: pgvector db (two databases
      via initdb script) + redis + engine + platform + web + admin, zero
      keys (mock provider, fixed DEV shared JWT secret so tokens work
      across services — prod injects real ones). Dockerfiles added for
      web/admin. Verified: config valid, all four images build, engine +
      platform images boot to healthy on offset ports. Full `up` not
      switched to yet — the host dev stack occupies the ports and holds
      the walkthrough data the owner's device is using.
- [x] 2026-07-14 — Prompt workbook forked to
      `services/engine/agent_prompts.xlsx`: 15 live agents (legacy sheets
      dropped), sheet names normalized, rebrand sweep verified (zero
      agentman/sherlock/xcalibrate leaks), clean Catalog with explicit
      environment row, environment wrapper rewritten 45,014 → 1,739 chars,
      extraction + dynamic-variables tabs carried over.
      Still open from this item:
  - [ ] Content adaptation of the big agents (CH 70.9K, core 39K → ≤8K
        target per `docs/PROMPTS_SPEC.md`) — coach-review release condition.
        **Shrink strategy drafted (2026-07-16, `docs/prompts/PROMPT_SHRINK_DRAFT.md`):**
        the size is real methodology, not padding, so the reduction must come
        from EXTERNALIZING the M1–M6 module playbooks (core) and the per-step
        phase scripts (CH) into retrieved SSKB playbooks + code-side tables,
        leaving only identity + output-contract + selection logic inline (~6.5K).
        Includes an illustrative slimmed `core_coaching_agent` skeleton. NOT
        applied to the live workbook — needs a coach to author the playbooks and
        sign off on quality (the loadability gate + eval smoke verify structure,
        not coaching quality).
  - [x] 2026-07-16 — Evals golden cases confirmed ported (identical to the
        reference's routing/reply/leak sets); added the workbook-loadability
        CI gate + eval-harness smoke (`tests/test_workbook_loadable.py`, runs
        in the 96%-gated engine job). The gate loads the REAL `agent_prompts.xlsx`
        and fails on any STRUCTURAL issue (missing sheet, enabled-no-prompt/model,
        orphaned continuation) or a degraded load; oversize/unknown-placeholder
        warnings are tolerated (the prompt-shrink item below) via
        `issue_count == oversize + unknown_placeholders`. The routing-contract
        cases still run nightly with a real model, off the merge path.
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

- [x] 2026-07-20 — Billing security review-fixes (2 adversarial passes) all closed:
      #1 free daily-turn cap on engine `/start`; #2 monthly Stripe subs get 30d not
      365d (interval via checkout metadata→webhook); #3 token rotation on
      purchase/cancel (`_plan_out_rotated`; Android `applyBilling` adopts the fresh
      pair) so the engine plan gate updates immediately; #4 partial UNIQUE index on
      non-empty `provider_ref` (idempotent DDL in `db.create_all()`, no Alembic)
      backstops the Play-token TOCTOU replay → 409; #5 Stripe cancel failure surfaces
      502 and keeps the sub active rather than showing a cancel that never reached
      Stripe. Platform 230 tests green, 97.2% branch; engine 1860 green.
- [x] 2026-07-20 — Admin ↔ B2C reconciliation. Consumer self-serve signup mints
      a `personal-*` org-of-one each, which the internal-admin Tenants list
      (`GET /orgs`) was returning — every signup would bury the real B2B tenants.
      `list_orgs` now filters personal orgs out. Added `GET /orgs/consumer-stats`
      (internal_admin, **counts only** — personal-account and active-subscriber
      totals, no identities/content) and a "Consumer (B2C)" tile on the Tenants
      tab. Tests: exclusion + stats counts + stats role-gate (403). Verified live:
      tenant list clean, stats `{accounts, subscribers}` served.
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
- [x] 2026-07-16 — Admin: nonce-CSP middleware (the ref/Zen pattern), then
      extended to `apps/app` and `apps/web` (the app holds the transcripts).
      **Not** the ref's recipe on web: a nonce forces dynamic rendering and the
      marketing site is static, so it gets a static CSP via `next.config.ts`
      headers(). Measured caveat in the commit: SRI does NOT permit a strict
      script-src on a static Next build — tried, hydration died, reverted.
- [x] 2026-07-16 — Ops tabs wired to the engine: **Prompt workbook**
      (view/edit prompt·model·enabled via `GET /v1/prompts` + `PUT
      /v1/prompts/{stage}`; validate-on-save surfaces hard errors/warnings;
      validation-report banner from the registry; reload-from-source;
      always-on agents locked, edit gated on the engine's `editable` flag) and
      **Safety queue** (`GET /v1/safety/escalations` — NEW engine endpoint in
      `app/routers/safety.py`, signal-only + org-scoped, `limit` guarded).
      Escalations now stamp `org_id` so each tenant sees only its own queue
      (`app/safety/escalation.py::list_escalations`, 11 tests: org-scoping,
      no-content-leak, 422 guard). Verified end-to-end: internal_admin login →
      both tabs render against a live platform+engine; full engine suite green
      (1491 passed), new router 100% covered.
- [x] 2026-07-14 — Marketing `/api/demo` → platform pipeline wired:
      the form posts to `POST /demo-requests` first (PLATFORM_API_URL,
      5s timeout, best-effort), and succeeds if EITHER pipeline or email
      lands — a lead is never lost to one channel being down. Verified
      live: site form submission → row visible in the ops Demo tab.
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
      **Foundation built (2026-07-16):** local (no-S3) corpus ingestion +
      a bundled demo corpus so retrieval works out of the box instead of the
      coach improvising over an empty index. `app/rag/seed_demo.py` walks a
      directory mirroring the S3 taxonomy (`rag_seed/sskb/<type>/`,
      `rag_seed/cskb/<orgId>/<group>/`) and ingests via the same
      `chunk_doc`/`embed_and_upsert` primitives; `python -m scripts.seed_demo_rag`
      seeds it. 7 tests (`test_rag_demo_seed.py`) prove ingest→search across
      both KBs incl. CSKB org-isolation. Real content drops into the same
      layout. **Admin UI done 2026-07-17:** `GET/POST /v1/cskb/{org_id}` +
      `DELETE .../docs` (internal_admin), and a per-tenant panel on the Tenants
      row — list, chunk counts, curated upload, remove, and a grounded/
      ungrounded pill naming which doc_types this tenant retrieves NOTHING for.
      That gap was the point: no values doc → no `{CSKB_Values}` → the prompt's
      field-presence gate takes the absent branch → the coaching quietly runs on
      the general method with no error. The org is a PATH PARAMETER, never
      `current_org()` — an operator's token carries `org_id="internal"`, which is
      what made the safety and nudge queues permanently empty. Deletes are
      org-scoped inside the DELETE's own WHERE (`store.delete_org_doc`), pinned
      by a two-org test that fails with "one tenant deleted another tenant's
      knowledge base". Stays curated: SECURITY.md gates self-serve on injection
      (indexed text is retrieved into the coach's context on a later turn), so
      `org_admin` cannot reach it. **Remaining:** the RAG-with-KB eval (R3,
      Phase 5). The demo corpus is placeholder — replace with real SSKB/CSKB
      before shipping.
- [x] 2026-07-16 — e2e suite (Playwright) against the **composed stack**, with the
      seeded dev logins: tenancy/cross-tenant denial, employee journey (SSE
      vocabulary read off the wire), HR aggregates + cohort floors, ops workbook +
      CSP, surface links. 34 specs, wired into CI (`e2e` job) alongside a new
      `app` build job — apps/app had no CI job at all. It found a real hole on its
      first run: the engine had no role checks, so any employee could download the
      coaching workbook and any token could rewrite it for every tenant (fixed,
      `require_internal_admin` + `test_role_gate.py`).

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
- [x] 2026-07-14 — REAL-DEVICE smoke run (OPPO CPH2681, Android 14):
      installed via `installDebug -PapiBaseUrl/-PengineBaseUrl=localhost` +
      `adb reverse` tunnels; owner-verified live — sign-in against the
      platform, Coach tab STREAMING from the engine (mock provider) over
      SSE on the device. The debug manifest's blanket cleartext covers
      localhost; release stays HTTPS-only.
- [x] 2026-07-14 — Android HR-analytics beats wired (`net/Events.kt`,
      fire-and-forget, kind-only): first turn → session_started; done
      payload stage=close → session_completed; ActionsStore add →
      action_saved, done → action_completed (reopen ≠ completion). 3 tests
      incl. failing-beat-never-breaks-the-caller; coverage gate green;
      reinstalled on device. Also: auth screen is email+password only
      (Google/OTP/signup/forgot hidden — no platform endpoints; found
      on-device), debug builds prefill the walkthrough user.
- [x] 2026-07-14 — Well-being suite RESTORED (owner reversal of the v1
      strip): audio/ (14 files incl. mixer/soundscapes/Sfx), sleep screen +
      scene video, breathe engine (incl. onboarding demo), reset tools
      (grounding/CBT/TIPP/baseline), games (BubblePop/PatternGlow/
      ZenRipples/Gratitude), Health Connect prefill, res/raw loops,
      manifest services, coverage-gate scope + 9 test files. Entry points:
      Today's "Reset toolkit" + "Rest & recovery" cards (tabs stay the five
      coaching tabs). check green; installed on device. Media catalogue
      still ships EMPTY (no licensed audio/video — the reference's
      copyright rule holds; synthesized tones/bundled loops until licensed
      media exists).
- [x] 2026-07-14 — Calm-parity slice DONE (source: the reference's Calm
      teardown §3). Verification-first finding: the restored W27 code had
      already shipped most of it — crossfades (VolumeRamp in both audio
      services), playing-aware aurora tint, mixer presets (monsoon night /
      shoreline / still air), breathe pace choice + persisted haptics
      toggle + off-by-default chime, timer bell. Closed the two real gaps
      today: (a) demoted the fake-reactive MixerWaveform bars (both sites)
      to the honest BreathingDot and deleted the component — "the one
      element Calm would cut"; (b) the session-end settling moment — when
      playback ends on the player, a quiet "Notice how you feel." replaces
      the abrupt silence. Per §4/§6: ambient-welcome autoplay deliberately
      NOT built (default-off evaluate; Calm's own top support complaint).
      check green; on device. Content library (guided sessions / sleep
      stories) remains its own item: platform media+content endpoints +
      LICENSED audio only.
- [x] 2026-07-16 — Mira screen-reference slice (owner-shared prototype, artifact
      0376c052 "mira_app" — the sibling companion app; full HTML saved in
      session tool-results). Reviewed 2026-07-14, verdicts:
      PROGRESS 2026-07-16: adapt items 1/2/3/6 BUILT, and 4/5 are now BUILT too —
      `NotifGate.kt` (NotificationPrePermission) and `WindDownScreen.kt`, both
      wired into CereBroApp.kt's NavHost. Slice complete; checkbox was stale.
      ADAPT (high value, backend already exists):
      (1) Today presence moment — living hero orb + context-aware greeting
      + one "say" line proposing two actions (Talk/Breathe); coral-tinted.
      (2) Coach "memory chip" — "Your coach remembers: …" above the thread
      from the engine's agentic context (previousUserActions / patterns);
      view first, edit needs an engine endpoint. Transparency win.
      (3) "Grounded" marker under coach bubbles when RAG/learning-aid
      content is served ("grounded in reviewed material · not diagnosis").
      (4) Pre-permission notification screen (shows a sample notification
      BEFORE the OS prompt) in onboarding.
      (5) Wind-down: guided 4-step pre-sleep routine (sleep suite gap).
      (6) Typing indicator pre-first-token in CoachScreen; loading
      skeleton shimmer for list screens.
      BUILT 2026-07-14 (check green, on device): (1) Today presence — the
      breathing coral PresenceOrb + time/state-aware greeting (name from
      /users/me, commitment-aware say line) + Talk/Breathe action row;
      (2) Coach memory chip from on-device state ("Your coach remembers ·
      N open commitments" → Actions tab; privacy line when empty);
      (3) grounded marker — Coach.turn now captures SSE `node` stages,
      and replies where learning_aid ran show "Grounded in reviewed
      material — guidance, not diagnosis" (+ stage-capture test);
      (6) TypingDots pre-first-token (Reduce Motion static). Still open
      from this slice: NONE — skeleton shimmers done 2026-07-14 (ShimmerBox
      already existed; the Journeys tab's bare loading line was the last
      gap, now layout-preserving skeletons). (4) pre-permission BUILT
      2026-07-14: NotifGate — one-time post-sign-in gate (Android 13+,
      ungranted only) showing the REAL check-in notification (commitment
      follow-up copy) before the one-shot OS prompt; Maybe-later never
      burns the prompt. The funnel's bare launch remains for the
      reminder-picker path (context already given there). (5) wind-down BUILT 2026-07-14:
      WindDownScreen — intro with the four steps listed, then one
      unhurried card per step (name tomorrow's first thing / dim the
      world / one minute with the real compact BreatheEngine / settle
      into sound via the mixer or straight to sleep), progress dots,
      nothing timed. Route 'winddown'; Today surfaces it 20:00-03:00.
      REFERENCE LATER: voice-mode screen (rings/wave/transcript/stop) for
      the parked VoiceEngine wiring; Discover layout (hero + tile rows +
      durations) for the future content library; crisis sheet polish.
      EVALUATE WITH CARE: standalone mood check-in + morning sleep
      check-in — conflicts with regulated-mode default (no emotion
      persistence); device-local-only variant or skip.
      REJECT: paywall/Mira+ subscription screens (B2B seats), their tabbar
      (ours is the five coaching tabs), account sign-in providers.
      Note: Mira's palette (peach glow #F1B27A/#E98D7C on deep plum) is
      near our coral family — controls re-skin naturally.
- [x] 2026-07-14 — Coaching tab icons (compass/check-circle, 2dp line set).
- [x] 2026-07-15 — **App is now DARK-ONLY (theme consistency fix).** The report:
      some screens were light (Today/Coach/Actions/Journeys — they FOLLOW the
      theme, which resolved to light because the default was System + a
      light-set phone) while others were dark (You/Sleep/Sounds hardcode a night
      bg; Privacy was even MIXED — dark header, white consent cards). Root cause
      was NOT hardcoded-light screens (a parallel audit confirmed the coaching
      screens are fully token-based and adapt correctly) — it was the theme
      DEFAULT. Fix: `AppTheme.mode` default System → **Night**, and CereBroApp
      only overrides from an EXPLICIT saved pref (absent pref must not reset to
      System). One switch flipped every theme-following screen dark AND fixed
      the mixed Privacy cards (themed `CardFill` resolves dark). Then closed the
      re-introduction hole: the Appearance picker could still select System/Dawn
      and rebuild the exact split (worse — several premium/sleep screens paint a
      FIXED night bg, so "Dawn" was already half-broken). Made Appearance
      dark-only (single "Always night", updated copy + the You-row subtitle).
      The Dawn/System palettes still exist in the theme layer and ContrastTest
      still tests both — they're just no longer selectable. Device-verified
      dark + consistent across Today, Coach, Actions, Journeys, You, Privacy,
      Crisis region, Companion, Appearance. Android check + 95.80% JaCoCo, theme
      + contrast tests green.
- [x] 2026-07-15 — **Production deploy config built** (Phase 4 deploy slice).
      `deploy/Caddyfile` (auto-HTTPS, security headers) + `docker-compose.prod.yml`
      (all services internal, only Caddy on 80/443, ENV=production, restart
      policies, persistent caddy-data/pg-data volumes) + `.env.production.example`
      (documented placeholders; real file git-ignored). Topology matches the
      Android release contract and the cerebroSG reference: cerebrozen.in→web,
      admin.→admin, api.cerebrozen.in/engine/*→engine (prefix stripped),
      api./*→platform; app. reserved (not proxied). Secrets are ${VAR:?}
      fail-fast — a missing DB_PASSWORD/JWT_SECRET/OPENAI_API_KEY stops the boot
      with a named error. ENV=production activates the guards (engine CORS,
      platform guard_production/no-dev-seed, enforced auth). Compose validated.
      deploy/README updated with topology + steps + the human-owned go-live items
      (key rotation, counsel sign-off, SMTP). NOT reused: any reference-project
      secret — all generated fresh per the compromised-ref rule.
- [x] 2026-07-15 — **Content-aware banners + engine CORS prod boot-guard.**
      (1) Banners: the user asked to lift sounds/banners from a `calm/extracted`
      teardown of Calm/BetterHelp/Rosebud/Youper — DECLINED (proprietary,
      copyright, the project's own rule; would expose a commercial B2B product).
      Did it legitimately instead: the generative art system (ContentArt.kt,
      pure Canvas) already gives every sleep tile a per-title moon, but they all
      looked the same. Added content-aware overlays keyed off the scene's OWN
      words — "Gentle Rain" → slanted rain streaks, "Distant Thunder" → storm
      cloud + rain, "Snowfall" → scattered flakes, "Deep Forest" → fir
      silhouettes — same soft translucent house style, deterministic, zero
      licensed assets. Device-verified all render distinctly on the Sleep
      stories list. (2) Engine CORS: defaulted to `*` with no prod guard (the
      platform had one, the engine didn't) — added a boot guard that refuses to
      start with wildcard CORS outside a dev-class ENV (mirrors
      guard_production + the AUTH_DEV_BYPASS refusal). +3 tests; the reload_config
      fixture now defaults CEREBROZEN_CORS_ORIGINS so unrelated deployed-ENV
      reloads don't trip it (same pattern it already uses for STRICT_TENANT).
      Engine 1,510 / 98.62%; Android check + 95.80% JaCoCo green.
- [x] 2026-07-15 — **Today home cards: added relevant icons (were bare text).**
      The five home "doors" (Commitments, Journeys, Reset toolkit, Rest &
      recovery, Need a human) were `SectionCard { Text; Text }` — title +
      description with NO icon and no chevron, while every You-tab row already
      carried a leading icon. Added a `DoorCard(icon, title, desc, accent)`
      composable: leading icon in a tinted circle + title/desc + a chevron, same
      icon language as the rest of the app. Content-relevant icons: Commitments
      = TaskAlt (check), Journeys = Explore (compass), Reset toolkit =
      SelfImprovement (meditation), Rest & recovery = Bedtime (moon), Wind-down
      = NightsStay, Need a human = Diversity3 (people). Accent-coded warm
      (action/social) vs cyan (calm/rest). Device-verified all five render.
      Android check + 95.80% JaCoCo green.
- [x] 2026-07-15 — **Audited EVERY pushed screen for the back-button dead-end;
      found + fixed a second one (Wind-down).** Code-classified all pushed
      routes by frame: 11 use SubPage/PremiumSubPage (auto back button); Toolkit
      + Breathe render their own ArrowBackIosNew back; the rest safe. The one
      other trap besides Sleep: `WindDownScreen` used `Page` (the TAB frame, no
      back) — `onBack` only fired from the final "Good night" CTA, so a user
      mid-routine had system-gesture only. Switched it to `SubPage(onBack)` (a
      drop-in that renders the back button; back leaves the routine, matching
      system-back and every other sub-screen). Device-verified a working back
      button across ALL frame types: PremiumFrames (Crisis region / Companion /
      Appearance), SubPage (Sounds), PremiumSubPage games (Zen Ripples), custom
      ToolkitHeroHeader (Toolkit), custom BreatheCompactHeader (Two-minute
      reset), Sleep — and confirmed back navigation works. Android check +
      95.80% JaCoCo green. Net: no pushed screen can be a dead-end anymore.
- [x] 2026-07-15 — **Fixed the Sleep screen dead-end (no back button).**
      Reported as "features/tab bar gone, only Sleep shows" — nothing was
      removed; the app had been left on the Sleep pushed sub-screen (no tab bar
      by design) and Android restored it on resume. Root gap: `SleepScreen` was
      the ONE pushed screen with no `onBack` — NavHost called it without one, so
      it rendered no back affordance (system-gesture only), reading as a
      trap. Added `onBack` param + a circular back button in `SleepPremiumHeader`
      (same pattern as the ~20 PremiumFrames screens), wired `back` in the
      NavHost. Verified on device: back button present, tapping it returns to
      Today with the full tab bar. (`home`/`talk` alias routes checked — never
      navigated as pushed pages, so no trap there.) Android check + 95.80%
      JaCoCo green.
- [x] 2026-07-15 — **Converted the last 4 theme-blind screens to the theme
      token.** PremiumFrames (`PremiumBackground` const, ~20 sub-screens),
      SleepScreen, ToolkitScreen (Extras), BreatheScreen each hardcoded a
      `#0D1424→#182447→#241A4A` night gradient (plum-tinted bottom). Swapped all
      four to `Gradients.night` (the themed `NightMid #1E2A47 → Night #0F172A`
      navy — the same base the aurora/coaching screens use), keeping each
      screen's own ambient layer (PremiumFrameAmbience, ToolkitAmbientLayer, the
      Breathe canvas) on top. Now EVERY screen shares one themed dark canvas and
      follows the theme; the bespoke plum bottom is gone in favour of the
      consistent navy. Device-verified You (PremiumFrames) + Sleep look clean
      and consistent with Today; ambient glows still read. Android check +
      95.80% JaCoCo green.
- [x] 2026-07-15 — **Three more programs — the ones the Journeys tab already
      promised.** The tab card advertises "feedback, delegation, influence" but
      only two wellness programs existed (Better Sleep, Steadier Focus). Added
      The Feedback Conversation (5d), Delegation That Sticks (5d), Quiet
      Influence (5d) — workplace coaching, NOT therapy: each day is one concrete
      rehearsal in the CereBroZen voice. Pure catalog data (app/catalog.py), no
      rebuild. New hygiene test asserts EVERY program is well-formed (>=3 days,
      non-blank guides >=20 chars, last day resolves + completes) so a future
      malformed program fails the build. Platform content tests 17 green.
      Device-verified: all 5 in "Start something new"; enrolling The Feedback
      Conversation → day 1 "Name the one thing" with the guide. (Minor existing
      copy nit noticed, not fixed: the Programs screen intro still says "calmer
      baseline / sleep-science" while the tab card says feedback/delegation/
      influence — the two entry points frame the same list differently.)
- [x] 2026-07-15 — **More sleep stories + soundscapes.** Sleep scenes 5 → 11
      (Snowfall, Cabin in the Rain, Meadow at Dusk, Harbour at Night, Riverbank,
      Before First Light) — pure catalog additions (app/catalog.py), no rebuild,
      live on device. For soundscapes, the HONEST constraint drove the design:
      every catalog list item plays the ONE bundled `ambient_bed` (Player →
      MediaUrls.urlFor → blank → bed), so naming new "soundscape" scenes would
      imply sounds the app can't make. The genuinely-distinct audio is the four
      real bundled loops (rain/ocean/wind/drone) in the mixer — so I added three
      new mixer PRESETS (Rainforest [0.65,0.15,0.4,0], Deep current [0,0.6,0,0.5],
      Thunderhead [0.85,0,0.55,0.35]): real distinct blends over the four loops,
      no new audio files. +EN/HI strings, presetLabel arms, presets test extended
      (6 keys, distinct-blend + distinct-label assertions). Android check +
      95.80% JaCoCo; platform content tests green. Device-verified: 11 stories
      list; tapping Thunderhead set Rain 85 / Wind 55 / Drone on / Ocean off —
      the authored blend, audibly. Truly-new soundscape TEXTURES (beyond the 4
      loops) still need new audio assets (licensing) — flagged, not faked.
- [x] 2026-07-15 — **Physical-device connection fixed + rest-and-recovery
      catalog built.** Two things this session:
      (1) The device couldn't reach the backend — my wellness-work rebuilds
      dropped the `-PapiBaseUrl` flag, so the debug APK fell back to `10.0.2.2`
      (the EMULATOR's host alias, meaningless on a real phone). Every auth call
      failed, so the app showed fallback state everywhere (no name, consent all
      OFF, screens stuck loading). Fix: wrote the host LAN IP into
      `apps/android/local.properties` (`apiBaseUrl`/`engineBaseUrl`) so every
      build picks it up — can't regress the way the flag did. Verified on device:
      login, real name, and consent screen all show true server state (Mood ON,
      Sleep ON, matching the DB). Note: the phone had ALSO been signed out
      because an earlier sweep withdrew consent (revokes tokens across devices —
      by design); fresh login recovered it.
      (2) Sleep stories / soundscapes / programs showed the raw "Not Found"
      because `/content`, `/media/catalog`, `/programs/*` were never built (the
      404 error path prints the FastAPI detail). Built them on the PLATFORM
      (matches the app's existing base): `app/catalog.py` — a static, curated
      catalog of sleep scenes, soundscapes, wind-down, and two authored CBT-I /
      focus programs, all serving the phone's OWN bundled beds / synth tones
      (`audio_url` blank → MediaUrls bundled fallback), so lists populate and
      PLAY with zero licensed media. Program enrolment is per-user (id + start
      date, day DERIVED so it can't drift). `+2 User columns` (dev.db migrated
      in place, 3 accounts kept). Platform 93 tests / 97.48%. Live-verified on
      the device: Programs screen shows "Better Sleep in 7 Days · Day 1 of 7"
      with the real day-1 guide; Sleep screen shows "Night Lake"/"Gentle Rain"
      as playable Sleep stories; tapping Play started the bundled bed (NOW
      PLAYING bar appeared). GAMES were never broken — they're on-device in the
      Reset toolkit (Today → Reset toolkit), just nested. ARCHITECTURE contract
      table updated (JWT `consent` + wellness-in-engine + catalog rows).
      Deliberately still not built: licensed narrated audio/video (copyright
      rule) — the catalog entries carry blank urls until rights exist.
- [x] 2026-07-14 — **Wellness backend slice BUILT** (the review's "backend-less
      screens" decision, resolved). Split on the content firewall: **content in
      the engine, account in the platform.**
      *Engine* — `stores/wellness.py` + `routers/wellness.py`: journal, sleep,
      mood check-ins, personal weekly insights at `/v1/wellness/*`. Subject
      comes from the token and NOWHERE else (no `user_id` param exists — the
      privacy-router rule; a diary must not be addressable). One doc per user,
      `$push`/`$each`/`$slice` capped at 2000 (an unbounded push is a 16MB
      document years later). Registered for erasure + export (the store-scan
      guard in test_privacy.py fails the build otherwise). Field names are the
      CLIENT's (`duration_min`, `wake_time`) — the sleep duration crosses
      midnight correctly (23:30→07:00 = 450min, not −960).
      *Platform* — consent (6 DPDP keys), profile, trusted contact, 18+
      attestation, streak. New columns; `dev.db` migrated in place by hand
      (17 ALTERs, 3 accounts preserved) — **the no-Alembic gap, biting exactly
      as predicted; Alembic is now a real Phase-2 item, not a doc nit.**
      *Consent is ENFORCED, not recorded.* The platform signs the six flags into
      the access token; the engine refuses (403) to store a category the person
      declined. A withdrawal ROTATES the token pair (returned from the PATCH;
      the app adopts it) so it bites on the next request instead of at token
      expiry — otherwise we'd go on storing what they just switched off for up
      to ACCESS_TTL_MIN.
      *Self-report is not inference* — regulated mode turns off the SYSTEM
      reading emotions off a worker; it does not confiscate the worker's own
      diary. Both paths pinned in one test so they don't get "simplified" into
      one flag. `CEREBROZEN_SELF_REPORT_WELLNESS=false` for the tenant who wants
      none of it stored.
      *Android* — `Session.api(base=)` seam (cache key is now base+path, or two
      services' identical paths would collide); wellness calls repointed at the
      engine with the same bearer token; **consent survives a dropped packet**
      (onboarding queues a failed consent write and replays it on next launch —
      it used to be a bare runCatching, so six DPDP answers could vanish while
      the app carried on as though it had them); mood check-in only fires if
      they consented to it; `leaveProgram` is Result-typed (it used to look like
      it worked whether or not the server heard).
      Corrections to the review: the journal did NOT show a false "Saved" (it
      honestly printed "Not Found"). The real false-success surfaces were
      onboarding's silently-discarded consent/mood/goals, `leaveProgram`, and —
      worst — `GET /users/me/consent` 404ing so all six DPDP toggles rendered
      OFF as though authoritative. All fixed.
      Gates: engine 1,503 tests / 98.62%; platform 76; Android check + 95.79%
      JaCoCo. Live E2E 18/18 (scratchpad/wellness_sweep.py): consent refusal →
      grant → token rotation → writes land → withdrawal bites next request →
      entries already written stay readable → HR surfaces leak nothing → an HR
      admin's token reads zero journals. Docs: ARCHITECTURE contract table (3
      rows), SECURITY privacy model (self-report vs inference, consent
      enforcement, structural content firewall, k-floor wording corrected).
      NOT built, deliberately: `/content`, `/media/catalog`, `/programs/*` —
      they need licensed media and authored program content, and the copyright
      rule holds. Those screens still degrade to bundled/synth audio.
      Follow-up: `ai_memory` consent is stored and signed but not yet enforced
      in the engine's memory path (profile_read/prior transcripts) — the other
      five are honoured or refused for real; this one is recorded only.
- [x] 2026-07-14 — **Live E2E sweep (14/14) — and it found what the 1,468 unit
      tests could not.** Driving the REAL stack with a REAL platform token (not
      the dev bypass) exposed a silent data-loss bug that only appears once auth
      is enforced with real org claims — i.e. only in production:
      `dispatch_title_generation` and the 7 `builders.py` background writes ran
      on bare `ThreadPoolExecutor`s, which do NOT copy ContextVars, so
      `current_org()` fell back to the DEFAULT org in those workers. The title
      writer created the session doc under org `default`; the foreground
      `record_turn` (correct org) then hit the pg shim's cross-tenant guard
      (`pg.py:325` — refuses a row that doesn't match the whole filter) and its
      write was DROPPED — silently, while still logging `conversation.recorded`.
      Net: **coaching messages were never persisted**; transcripts, history,
      "your coach remembers", and check-in context were all dead the moment auth
      went on. Hidden in dev because auth-off ⇒ org == `default` everywhere ⇒
      consistent. Fixes: (a) `ContextThreadPoolExecutor` (request_context.py) —
      per-task `copy_context()`, the pattern rag/placeholders.py already used —
      now backs both pools; (b) a refused write logs `conversation.record_dropped`
      at ERROR and never reports success (`_write_landed`, backend-agnostic:
      pymongo signals an insert via `upserted_id`, the pg shim via
      `modified_count`); (c) +4 tenancy/regression tests, verified RED without
      the fix. Live proof: old doc = org `default`, titled, **0 messages**; new
      doc = real org, titled, 2 messages, transcript reads back.
      **Also fixed the hermeticity hole this exposed** (CLAUDE.md rule 3): the
      suite's offline guarantee rested on `os.environ[X] = ""`, but the loader
      deliberately treats an empty var as overridable — so the day `.env` gained
      a `POSTGRES_URL`, 38 "offline" store tests silently swung onto the live
      database. `CEREBROZEN_SKIP_DOTENV` now makes the suite refuse the .env
      outright (+1 test). Engine 1,468 tests / 98.68%; platform 56 / 96.93%;
      Android check + 95.87% JaCoCo; web + admin build. Sweep script:
      scratchpad/live_sweep.py (login → me → events → funnel → k-floor 403 →
      export → demo pipeline → real billed coaching turn → stage/transcript as
      owner → IDOR denial as colleague → sessions list).
- [x] 2026-07-14 — Full-repo review (3 parallel reviewers: backend, web+admin,
      Android) + all 6 criticals fixed the same day: (1) engine flow-view
      IDOR — `/v1/sessions/{id}/stage|transcript` were org-scoped but not
      user-scoped; now 404 for a colleague's session id, same JWT-only
      pattern as delete (+6 tests); (2) Android You-tab crashes — "patterns"
      and "premium" rows navigated to unregistered routes (both removed;
      premium was B2C leftover); (3) evidence page numbers refreshed from a
      live run (1,457 tests / 98.7% — page previously OVERstated coverage);
      (4) admin `NEXT_PUBLIC_API_URL` is build-time — Dockerfile build-arg +
      compose args + `.env.example` added (runtime env alone never reached
      the browser bundle); (5) dev SQLite DBs were tracked in git —
      gitignored + untracked (scrub history before first push); (6) Android
      anonymous funnel beacon posted to `/events` which didn't exist —
      platform now serves it (FunnelEvent table: NO user/org columns by
      schema, `FUNNEL_EVENTS` allowlist, batch/length caps; +3 tests,
      ARCHITECTURE contract row updated). Review backlog kept (NOT
      criticals): backend-less wellness screens decision (journal "Saved"
      is a false confirmation today), SECURITY.md "floor in SQL" wording,
      no-Alembic claim, deletion keeps ActivityEvent rows, engine CORS `*`
      default needs a prod boot-guard, admin tabs shown to plain `user`
      role, missing admin loading/error states, footer placeholder socials,
      `resolve_user_id` payload-wins design (documented service-to-service
      decision; revisiting it = cross-stack contract change).
- [ ] Play readiness runbook (adapt ANDROID_RELEASE + PRIVACY_LABELS).
- [x] Auth screens: email/password (OTP/SSO deliberately hidden — no endpoints; see 2026-07-14 auth cleanup).
- [x] Coach tab: engine SSE streaming, action cards → ActionsStore, memory chip, grounded marker, typing dots, voice. (Phase-card/mood-capture UX polish remains with prompt adaptation.)
- [x] Today tab: presence orb + state-aware greeting, open commitments, journeys, toolkit/rest doors, evening wind-down.
- [ ] Actions tab: commitment lifecycle, 7-day follow-through prompts.
- [x] 2026-07-16 — Journeys tab: `ProgramsScreen` (Extras.kt) wired into the
      NavHost with list/enrollment/day view (`parseTodayGuide`, enrollment
      `refresh()`); device-verified with 5 programs (see lines above).
- [x] 2026-07-16 — You tab: `YouScreen` (Screens.kt) wires profile header,
      Insights, Privacy, Appearance, Reminders, crisis region, human support,
      privacy policy, export (`DataExportScreen`), delete
      (`AccountDeletionScreen`).
- [ ] You tab — remaining: the privacy centre has no "what your employer sees"
      view. `privacy_stats_hint` says "Counts only — never your content or
      account", which states the rule but doesn't *show* the HR-side aggregate.
      The strongest version of the "counts, never content" promise is letting an
      employee see exactly what their org admin sees.
- [ ] Intake personalization: thinking-style seed questions wired to the
      engine's NBI/DISC profile stores.
- [x] 2026-07-16 — Crisis + HumanSupport screens serve **regional** helplines from
      the engine. The contract (ARCHITECTURE.md) already said "never hardcoded in
      clients" and the clients broke it: Tele-MANAS 14416 / 112 / 1800-599-0019 /
      iCall were literals in Crisis.kt, Settings.kt and strings.xml, shown to every
      user, while Settings offered a region picker whose answer only the picker read
      — a placebo control. Now: engine `app/safety/helplines.py` owns the directory
      (safety is code), `GET /v1/safety/helplines?region=` serves it and is total (no
      input returns an empty list); the platform resolves `crisis_region` on
      `/users/me` (person's choice > org default > unknown); Android renders it and
      falls back to a region-**neutral** finder offline, never a country's numbers.
      47 engine tests, 5 platform, 16 Android; `data/**` added to the JaCoCo gate at
      100% (total 95.39% → 95.89%).
- [ ] Notifications: reminder + nudge channels. **Backend delivery built
      (2026-07-16):** engine `POST /v1/nudges/dispatch` scans every tenant's
      due check-ins (`checkin_scheduler`) and emits a **content-free** nudge
      signal per user (counts + batch ids, never a commitment body) to
      `CEREBROZEN_NUDGE_DELIVERY_URL` — a webhook the deployment wires to
      push/email/Slack. `GET /v1/nudges` is the observability read;
      `/health.nudges.nudge_delivery_armed` surfaces a silently-off channel.
      Mirrors the crisis-escalation pattern (signal only, degrades to a logged
      no-op; `app/notifications.py`, `app/routers/nudges.py`, `test_nudges.py`).
      Remaining: mobile device-token registration + a concrete channel
      integration, and an external cron calling the dispatch endpoint.
- [ ] Play readiness: adapt `ref/Zen/docs/ANDROID_RELEASE.md` +
      `PRIVACY_LABELS.md` (data-safety honesty).

## Phase 4 — Compose it, prove it, ship it

- [x] 2026-07-16 — e2e suite: composed Docker stack; full employee journey, HR
      aggregates with cohort floors, ops workbook flow, cross-tenant denial — all
      built (`e2e/`, 34 specs, CI job). Remaining for Phase 4: the evals harness +
      nightly crisis red-team gate (separate item below).
- [ ] Evals harness adopted + nightly; crisis red-team wired as release gate;
      publish the real catch-rate (with classifier) on the Evidence page.
- [x] 2026-07-15 — **LIVE in production** at `cerebrozen.in` (Cloud VPS; host
      details in the ops vault, not this public repo). The full 7-service stack
      (web/admin/engine/platform + db/redis/caddy) runs from the host checkout
      via `docker-compose.prod.yml`;
      Caddy terminates real Let's Encrypt TLS on the four subdomains. Replaced
      the prior `cerebroSG` deployment (checkout at the same path, same repo).
      Deploy runbook + the SSH/secret specifics live in `deploy/README.md`.
- [ ] Prod deploy — remaining ops hardening: backup/restore for Postgres,
      OTel + Prometheus dashboards, breach runbook adapted and rehearsed,
      root-password rotation + key-only SSH, real `OPENAI_API_KEY` on the host.
- [ ] Marketing site truth pass: every number/claim re-checked against what
      the product now actually measures (PRODUCT.md success-metrics rule).

## Phase 5 — Enterprise readiness (first real tenant gates)

- [ ] SSO (OIDC/SAML) + SCIM or CSV seat provisioning.
- [x] 2026-07-16 — At-rest encryption resolved as a **datastore-layer** concern
      (DB TDE / encrypted volume), NOT app-layer field crypto — that would add a
      native crypto dep the project avoids and break content queryability. The
      engine can't verify the datastore is encrypted, so it carries the operator's
      attestation `CEREBROZEN_DATASTORE_ENCRYPTED`, surfaces it at
      `/health.storage.encrypted` (true/false/unknown), and logs a loud boot
      warning in a deployed env that hasn't attested true — so a deployment is
      never *silently* assumed encrypted (`app/config.py`, `routers/api.py`,
      `test_datastore_encryption.py`; SECURITY.md updated). Enabling the datastore
      encryption itself stays a deploy step, not an app feature.
- [ ] Crisis classifier measurement program; native-speaker + clinical
      review pipeline for localized safety strings.
- [ ] Escalation-to-human end-to-end (webhook → safety queue → contact
      procedure) — only then may marketing claim it.
- [ ] Retention windows per tenant; DPDP checklist adapted from
      `ref/Zen/docs/DPDP_COMPLIANCE.md`; DPIA template for EU customers.
- [ ] Air-gapped profile: prompt-size reduction, quality eval vs cloud model
      (with the design partner, per the Evidence page's honest framing).
- [x] 2026-07-16 — Web app (`apps/app`) for desktop employees. Built ahead of
      this phase: `/` (dashboard + mood check-in), `/coach` (SSE streaming chat,
      commitment cards, session history), `/journal`, `/insights`, `/settings`
      (6 DPDP consent toggles), behind a real auth gate (`components/shell.tsx`).
      Linked from the site's "Sign in" menu (see ARCHITECTURE.md link table).
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
| R3 | RAG silently degraded (0 rows) → coaching never evaluated with KB | `ref/Agent/docs/TODO.md` 1.7 | **PARTLY CLOSED 2026-07-17.** It had come true here: every eval run printed a score with `rag.search_failed` in the log above it and nothing in the result (POSTGRES_URL unset → pgvector off → every retrieval failing → harness reporting 100%). `scripts/eval.py` now prints the KB state beside the score, so a 0-row run can no longer read as "the coaching works". **And it is deeper than 0 rows:** `_extract_values` makes a SECOND model call to structure the passages, and when that call never answers the result is null — which the prompts read as "this organisation has no stated values". CH gates Step 5 on non-null and routes null to Step 9, so a rate limit had the coach ASKING THE EMPLOYEE for their own company's values while we held the document. Fixed 2026-07-17: an unavailable CALL is now distinct from a model that ANSWERED "none" (`_LLM_UNAVAILABLE`), and the former logs ERROR + a counted violation naming the org. It still BLANKS — serving the top-hit passage instead was my first fix and `test_a_crashed_values_extraction_is_logged_and_blanks_rather_than_guessing` rejected it, correctly: values are quoted to the user as their company's own words, and a top hit is whatever chunk ranked highest (a page header, a disclaimer). The turn still degrades; it is no longer silent. **Closed:** the positive assertion now runs the REAL path (OpenAI embeddings, pgvector, the real extraction registry, no stand-ins) and fails with "the coaching is ungrounded, silently" when the KB is empty. It skips without a key — a fake embedder answers a different question, and trying to use one is what defeated the first two attempts (a 16-dim query against a 1536-dim `rag_sskb` raises INSIDE the learning-aid pool and empties the placeholder, failing exactly like the bug for an unrelated reason). Isolation, degradation and the harness report are covered deterministically and run everywhere. |
| R4 | Oversized prompts (70K-char agent, 45K-char env wrapper) — cost/latency, blocks offline | `ref/Agent/docs/TODO.md` 2.2 | Phase 5 air-gap work |
| R5 | Model-id config contradicts "Catalog is source of truth" | `ref/Agent/docs/TODO.md` 1.8 | Phase 1 adoption sweep |
| R6 | Public claims vs code (Anthropic fallback, at-rest transcript encryption, SOS escalation) | `ref/Agent/docs/COMPLIANCE_GAPS.md` | SECURITY.md mapping; Phases 1/5 |
| R7 | Prompt-injection surface if self-serve KB upload ships | `ref/Agent/docs/TODO.md` 4 | Gated feature, SECURITY.md |
| R8 | Half-done rename breaks voice/UI topics silently | `ref/Agent` migration notes | Phase 1 (single sweep rule) |
| R9 | `nodes.py::_run_stage` god-function (~700 lines) | ref review | Refactor opportunistically, never during a feature PR |
| R10 | No licensed media; teardown assets must not ship | `ref/Zen/docs/TODO.md` | N/A for v1 (audio dropped) |

## Done

- [x] 2026-07-20 — **B2C freemium program — the product is now B2B *and* B2C.**
      Deliberate reversal of the B2B-only stance (rationale: the legal line is
      *functional* not contractual — non-clinical coaching + safety-as-code is
      defensible on either side; see `market-positioning` research + the review
      artifact). Shipped and gate-green across platform/engine/web:
      **Signup + tenancy** — `POST /auth/signup` → personal org-of-one
      (`is_personal_org`), full self-serve auth suite (OTP + password-reset,
      previously 404), all per-IP rate-limited and non-enumerating; clean
      account+org teardown (no orphans).
      **Billing** — `Subscription` model + `/billing` (mock provider, keyless) with
      **real Stripe** checkout+webhook behind a provider seam (`billing_providers.py`,
      inert without keys); `/billing/prices` single source of truth; correct
      period-end cancel semantics.
      **Entitlements enforced server-side** — `plan` added to the signed JWT
      (ARCHITECTURE contract table updated); engine caps free coaching at the turn
      endpoint (`ratelimit.limit_free_daily_turns`). The paywall is not bypassable.
      **Android** — onboarding→paywall→checkout→unlock→cancel flow (device-verified);
      lock-badges written (device-verify pending).
      **Sovereignty** — `/health/status` self-checks (both services) + `SELF_HOSTING.md`.
      **Privacy** — counts-never-content firewall test extended to every table;
      consent audit-trail (`ConsentEvent`) + freshness signal.
      **Web** — `/pricing` (+ Product JSON-LD), `/sovereignty`, `/accessibility`,
      `security.txt`; a **claims gate** (`scripts/check-claims.mjs`, CI) enforcing rule 6.
      Full backlog + proofs: `docs/IMPROVEMENT_BACKLOG.md`, `docs/CLAIMS_MAP.md`,
      `docs/DATA_SAFETY.md`. Consumer ToS draft (needs counsel): `docs/legal/CONSUMER_TERMS_DRAFT.md`.
      Still yours: Stripe/Play keys, Google sign-in backend, store submission, legal sign-off.
- [x] 2026-07-14 — Marketing site reviewed, fixed (consistency, a11y, SEO,
      no-JS fallback), demo form wired to SMTP email, legal pages written,
      illustrative disclaimers added.
- [x] 2026-07-14 — Deep review of `ref/Agent`, `ref/Zen`, and
      sherlockperformance.com; this docs set created.
