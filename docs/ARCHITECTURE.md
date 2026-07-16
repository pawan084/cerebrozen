# Architecture

Last updated: 2026-07-14

## System shape

Two backend services, three client surfaces, one reverse proxy.

```
                        ┌────────────────────────────────────────────┐
                        │                Caddy (TLS)                 │
                        │  cerebrozen.in        → web  (marketing)   │
                        │  app.cerebrozen.in    → app  (web client)† │
                        │  admin.cerebrozen.in  → admin              │
                        │  api.cerebrozen.in    → platform API       │
                        └───────┬──────────────────────┬─────────────┘
                                │                      │
                 ┌──────────────▼──────────┐   ┌───────▼──────────────────┐
   Android ──────►      Platform API       │   │     Coaching Engine      │
   (direct to    │  FastAPI + Postgres 16  ├──►│  FastAPI + LangGraph     │
    api.*)       │  auth, orgs, users,     │JWT│  the governed arc,       │
                 │  actions mirror,        │   │  prompt workbook, RAG,   │
                 │  analytics, nudges,     │   │  crisis screen,          │
                 │  admin APIs, billing    │   │  checkpointer (Postgres) │
                 └──────────────┬──────────┘   └───────┬──────────────────┘
                                │                      │
                        ┌───────▼──────────────────────▼───────┐
                        │   Postgres 16 (+ Redis, pgvector)    │
                        └──────────────────────────────────────┘
   † later phase

**How the surfaces link to each other.** Each is a separate deployment on its own
host, so every link between them is a real navigation — never a `next/link` route.
The hosts are `NEXT_PUBLIC_*`, which Next inlines at **build** time: setting them in
a container's `environment:` has no effect on an already-built image, so they are
passed as Docker **build args** (both compose files) and the `ARG` defaults are the
production domains.

| From | To | Carried by | Build arg |
|---|---|---|---|
| web | app · admin | "Sign in" menu — asks who you are rather than guessing; a wrong guess lands someone on a login their account can't pass | `NEXT_PUBLIC_APP_URL`, `NEXT_PUBLIC_ADMIN_URL` |
| app · admin | web | wordmark (login + signed-in) and a login footer to Privacy/Terms | `NEXT_PUBLIC_SITE_URL` |

The clients link **out** to the site's Privacy and Terms rather than hosting their
own copy — one set of published terms, one place to change them.
```

**Why two services.** The references are two different backends with
different strengths. `ref/Agent` (AgentMan) is the coaching engine — the
LangGraph graph, prompt workbook, safety pipeline, and evals are its whole
value, and it should stay a focused, independently-deployable unit (it also
must run air-gapped on its own). `ref/Zen`'s backend is the platform layer —
auth, orgs, roles, analytics aggregates, admin APIs, deletion ledger,
billing scaffolding — proven with the exact admin and Android clients we're
about to build. Gluing both jobs into one service would mean rewriting one
reference into the other's idioms for no product gain.

**Contract between them.** The platform issues JWTs (HS512 shared secret —
the engine already validates exactly this, `ref/Agent/app/auth/`). Claims
carry `user_id`, `org_id`, and role. Clients talk to the platform for
everything except live coaching turns, which stream (SSE) from the engine —
either directly (engine mounted under `api.cerebrozen.in/engine/` via Caddy)
or proxied by the platform if we need one egress point; decide in Phase 1,
default is Caddy-mounted direct.

## Backend 1: Coaching Engine (adapted from `ref/Agent`)

Take as-is (these are the crown jewels):

- **The graph** — 18 nodes, 15 workbook agents, deterministic routing: all
  edges are code predicates over typed state; the single model-emitted
  routing field (`coaching_path`) is grammar-forced on local models
  (`ref/Agent/app/graph/build_graph.py`, `state.py`).
- **Safety pipeline** — lexicon screen (~1ms, offline) + cheap-model
  classifier, deterministic zero-token crisis takeover, per-region helplines;
  crisis reply text lives in code, not the editable workbook
  (`app/graph/crisis.py`, `crisis_classifier.py`, `app/safety/escalation.py`).
- **Commit gate** — `final_action_check` is the only road to session close.
- **Prompt workbook** — content-hashed, validated, hot-reloadable,
  S3-or-codebase sourced (`app/llm/prompts.py`, `prompt_store.py`).
- **Regulated mode** — one env flag disables emotion capture and person
  scoring at the store layer (`app/config.py`, enforcement in
  `app/stores/agentic.py` and `variable_capture_registry.py`).
- **Resilience** — retry/cascade/circuit-breaker, checkpointer fallback
  chain, mock provider for offline tests (`app/llm/resilience.py`).
- **Evals + red-team** — routing-contract golden cases and the 22-case
  crisis red-team (`evals/`, `tests/test_crisis_redteam.py`).

Change (the known sharp edges — details in SECURITY.md and TODO.md):

1. **Tenancy in the app layer, not deploy config.** `org_id` becomes a
   first-class key on every store read/write and on checkpointer thread ids.
   The reference's per-deploy DB-name isolation (and hard-coded incumbent
   resource names) is the documented sharpest edge; we fix it in code.
2. **Storage consolidation: Postgres-first.** The reference's Mongo→Postgres
   shim and pgvector RAG path (`app/stores/pg.py`, `app/rag/pgvector_store.py`)
   already exist — we make them the default and drop Mongo/LanceDB/S3 from
   the critical path. One database technology across both services; Redis
   stays for locks/cache. (Also collapses the air-gapped and cloud profiles
   into one storage story.)
3. **Rebrand/rename** `AGENTMAN_*` → `CEREBROZEN_*` env prefix in one sweep,
   with the metrics/UI-topic renames done in lockstep (the reference
   documents this migration half-finished — finish it on day one).
4. **Providers: OpenAI (cloud) + Ollama (air-gap), by decision** — no
   Anthropic/multi-vendor fallback is built or claimed (owner call,
   2026-07-14). The provider abstraction (`app/llm/providers/`) keeps the
   door open if that changes.
5. **Regulated mode defaults ON** for new tenants (opt-out per contract, not
   opt-in).

## Backend 2: Platform API (adapted from `ref/Zen/backend`)

Take: FastAPI + async SQLAlchemy 2 + asyncpg + Alembic-at-boot; JWT auth
with single-use refresh rotation; role enforcement in dependencies
(`get_current_admin` pattern, extended to `org_admin` vs `internal_admin`);
account deletion cascade + deletion ledger; data export; first-party
analytics aggregates (DAU/retention/funnels) computed in Postgres with no
third-party SDKs; nudge/notification services; prod boot-guard refusing
insecure defaults.

Change/add:

- **Org model is central**: orgs, seats, invitations, org-scoped everything.
  The reference is B2C (no org concept); this is the main new code.
- **Aggregation guardrails**: minimum-cohort thresholds (k-anonymity floor)
  enforced in the analytics queries themselves, not the UI.
- **Actions mirror**: the engine owns coaching state; the platform keeps a
  minimal per-user actions/commitments mirror (id, status, due, session ref —
  no transcript content) so the app's Actions tab and HR completion metrics
  don't need engine reads.
- Drop B2C features from the surface: consumer billing (Stripe/App Store),
  waitlist, sleep/health tracking, media/audio catalogue, web push — either
  delete or leave dormant behind flags. Seat licensing replaces consumer
  billing in v1 (invoiced, not self-serve).

## Android app (adapted from `ref/Zen/apps/android`)

Take the architecture wholesale — it is the most valuable part of the Zen
reference:

- Kotlin + Jetpack Compose, Material3, single-Activity NavHost, **zero
  third-party networking SDKs**: the hand-rolled `Session.kt` transport
  (REST + SSE streaming + multipart, coalesced token rotation, encrypted
  refresh-token storage, offline GET cache with `servedStale` banner).
- The 95% JaCoCo coverage gate and Robolectric test approach.
- Theme system with token parity to web (see DESIGN.md) and the
  build-failing `ContrastTest` pattern.
- Biometric lock (for the coaching history), reminders/notifications.

Replace the B2C content with the coaching product — 5 tabs:
**Today** (check-in, next nudge, open actions) · **Coach** (session UI with
SSE streaming, phase cards, role-play) · **Journeys** (programs) ·
**Actions** (commitments, status, 7-day follow-ups) · **You** (profile,
privacy center with export/delete, "what your employer sees", appearance).
Crisis and HumanSupport screens carry over nearly unchanged.

Drop for v1: soundscapes/audio mixer, sleep/Health Connect, games toolkit,
premium/paywall. (The audio engine is impressive but it's a wellness-app
feature; revisit for well-being journeys later.)

## Admin app (adapted from `ref/Zen/apps/admin`)

Same skeleton: Next.js App Router, no runtime deps beyond next/react,
nonce-CSP middleware, `lib/api.ts` with refresh rotation, tabbed
single-page UI. Two role-gated views:

- **HR portal** (`org_admin`): Overview, Analytics (aggregates with cohort
  floors), Programs, People (seat status only — *counts never content*, the
  reference's user-drawer rule holds), Rollout.
- **Ops admin** (`internal_admin`): Tenants, Prompt workbook (versioned,
  validate/activate/revert — merge AgentMan's registry with Zen's Prompts
  tab), Safety queue, Demo requests, Platform analytics.

## Marketing site (`apps/web`) — built

Next.js 16 static-ish site behind the same Caddy. The demo-request endpoint
(`/api/demo`) later gains a second delivery target: the ops-admin demo
pipeline (email stays as the fallback).

## Cross-stack contracts

The Zen reference's most copy-worthy doc structure: a single table of
contracts that must be changed in lockstep across surfaces
(`ref/Zen/docs/ARCHITECTURE.md` §"Cross-stack contracts"). Ours starts as:

| Contract | Owner | Consumers | Rule |
|---|---|---|---|
| JWT claims (`sub`, `org_id`, `role`, `user.username`, `consent`) | Platform | Engine, Android, admin | Additive changes only; engine validates same secret. `consent` carries the six DPDP flags so the engine can enforce them without calling back — a consent change **rotates the token pair** (PATCH `/users/me/consent` returns a new pair; the app adopts it) so a withdrawal bites on the next request rather than at token expiry |
| Wellness content lives in the **engine**, never the platform | Engine | Android | Journal / sleep / check-ins are content: they are served from `/v1/wellness/*` on the engine, because the platform is the database an HR admin's token reaches and it must hold nothing to read. Pinned by tests on both sides (`test_wellness.py`, `test_wellness_account.py`, `ApiEndpointsTest`) |
| Rest-and-recovery catalog (`/content`, `/media/catalog`, `/programs/*`) | Platform (`app/catalog.py`) | Android | Static app configuration, NOT user content nor licensed media — a curated list of scene titles that play through the phone's own bundled beds / synth tones (`audio_url` blank → MediaUrls bundled fallback). Program enrolment is per-user preference (program id + start date; the day is DERIVED, never stored). When real narration is licensed, its URLs drop into the same entries |
| Consent keys (`mood_history`, `ai_memory`, `journal_memory`, `sleep_history`, `voice_storage`, `model_training`) | Platform (`models.CONSENT_KEYS`) | Android `ConsentNotice`, engine `_CONSENT_FOR_KIND` | Append-only. Renaming one silently unticks a box somebody consented to |
| SSE event vocabulary (`status`/`node`/`token`/`done`) | Engine | Android, web app | Renames require simultaneous client release |
| Action status lifecycle (`active/saved/skipped/deleted/completed`) | Engine | Platform mirror, Android, HR analytics | Enum is append-only |
| Design tokens | `design/tokens.css` | web, admin, Android `Color.kt` | `sync-tokens` script + CI drift check |
| Analytics event vocabulary | Platform | Android, admin dashboards | Documented enums (`EVENT_KINDS` authed coaching beats via `/events/coaching`; `FUNNEL_EVENTS` anonymous pre-auth beats via `/events`), no free-text event names |
| Crisis regions/helplines config | Engine | Android crisis screen, ops admin | Config file, never hardcoded in clients |

## Repo layout (target)

```
CerBroZen/
  apps/web/        # marketing (built)
  apps/android/    # employee app
  apps/admin/      # HR portal + ops admin
  services/engine/    # coaching engine (from ref/Agent)
  services/platform/  # platform API (from ref/Zen backend)
  design/tokens.css   # source of truth + sync script
  e2e/             # Playwright against a composed stack
  deploy/          # Caddyfile, bootstrap
  docs/            # these documents
  ref/             # read-only references
```

One git repository at the root (`apps/web`'s nested repo gets absorbed).
