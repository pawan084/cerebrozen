# CereBro

A calm, proactive mental-wellness product. Monorepo:

```
cere/
  apps/ios/       iOS app (SwiftUI) + live backend cloud sync
  apps/android/   Android app (Kotlin + Jetpack Compose) — scaffold
  apps/web/       Next.js marketing site + landing page
  apps/admin/     Next.js admin dashboard
  backend/        FastAPI + Postgres backend (auth, data, proactive AI, voice)
  e2e/            Playwright end-to-end tests (web + admin)
  screenshots/    iOS walkthrough + web/admin/cloud proofs
  temp/           reference designs + scratch — git-ignored, not part of the build
  docker-compose.yml        local dev stack
  docker-compose.e2e.yml    isolated test stack
  .github/workflows/ci.yml  backend · e2e · ios
```

> `temp/` (which holds the original HTML reference designs under `temp/ref/`) is
> git-ignored on purpose — it's scratch, not shipped code.

## Status
- ✅ **iOS app** — 47-screen SwiftUI app with local persistence, a11y + perf pass,
  and live cloud sync to the backend (Cloud Sync screen under the **You** tab).
- ✅ **Backend** — FastAPI + Postgres: JWT auth, user data, and proactive services
  (agentic plan, push nudges, weekly insights, crisis/safety detection, voice). See
  [`backend/README.md`](backend/README.md).
- ✅ **Web (landing)** — Next.js marketing site (`apps/web`, port 3000): hero,
  features, agentic section, pricing, working waitlist.
- ✅ **Admin** — Next.js dashboard (`apps/admin`, port 3001): overview stats, users,
  content, safety review queue, waitlist.
- ✅ **iOS ↔ backend** — the app authenticates and renders the server-driven agentic
  plan + weekly insights live; mood/journal writes mirror to the API.
- ✅ **Voice companion** — the **Talk** tab is a full voice loop: mic → Deepgram
  (speech-to-text) → OpenAI (reply) → ElevenLabs (text-to-speech) → playback.
  Keys live server-side; the iOS app only hits `/voice/stt` and `/voice/tts`.
- ✅ **Store compliance** — in-app **account deletion** (`DELETE /users/me`,
  Postgres-cascades all user data; App Store 5.1.1(v)), **data export**
  (`GET /users/me/export`), an in-app + web **privacy policy** (`/privacy`, `/terms`),
  and **Sign in with Apple** (`POST /auth/apple` verifies the Apple identity token →
  find-or-create user). *Enabling the SIWA flow needs the "Sign in with Apple"
  capability turned on in Xcode + your Apple Developer account — see below.*
- ✅ **Activities & games** — chat-invoked inline activities now include
  `one_good_thing`, `intention_set`, and `dbt_skill` (TIPP) alongside breathing /
  grounding / mood / journal, plus a **Calm games** hub of 8 games (Bubble pop,
  Bubble wrap, Color breathing, Zen ripples, Memory match, Pattern glow, Sliding
  puzzle, Gratitude garden) reachable from Home.

### Sign in with Apple — one manual step

The code is complete on both ends, but the entitlement can't be enabled from
source. In Xcode: select the **CereBro** target → **Signing & Capabilities** →
**+ Capability** → **Sign in with Apple**. Set `APPLE_CLIENT_ID` (your bundle id,
default `com.cerebrozen.app`) in `backend/.env`. Until the capability is on, the
button renders but Apple authorization fails gracefully and email auth still works.

## AI & voice providers

The backend picks an LLM provider at runtime: **OpenAI** when `OPENAI_API_KEY` is
set, else **Anthropic** when `ANTHROPIC_API_KEY` is set, else deterministic local
fallbacks (everything still runs offline). Voice is split in two halves —
**Deepgram** for speech-to-text and **ElevenLabs** for text-to-speech — each
enabled only when its key is present (`GET /voice/status` reports which are live).

### Oracle (LangGraph agent)

The text chat can run through an agentic **Oracle** built on **LangGraph** —
tool-calling (suggest activity, log mood, save journal, weekly insights) with
**confirm-before-write** (`interrupt()` → an inline approve/decline card) and
**SSE token streaming** into the iOS chat. Enable with `ORACLE_ENABLED=true` (+ an
LLM key); endpoints are `POST /oracle/messages` and `POST /oracle/confirm`. When
disabled, the deterministic `/chat` router (inline activity widgets + suggestion
chips) is the always-on, key-free fallback. State uses an in-process checkpointer;
for multi-worker production swap in `AsyncPostgresSaver`.

### Self-reflection → conversation topics

Onboarding includes a short **self-reflection assessment** — *motivations*
(psychological drivers: Focus, Calm, Confidence, Discipline, Connection) and
*goals* (concrete practices grouped into Daily Rituals / Personal Development).
From the selected subset the backend generates personalized, tappable
**conversation starters** (`POST /assessment/topics`, taxonomy at
`GET /assessment/structure`). Topics use the LLM provider when available and a
curated deterministic fallback otherwise — each is ≤6 words, grounded in a
selection, and de-duplicated. In the app they seed the Talk chat (flowing into
the Oracle when enabled). The taxonomy is shared between the iOS onboarding and
`backend/app/services/assessment.py`.

### Secrets

Keys are **never committed**. They live only in the git-ignored `backend/.env`
(see [`backend/.env.example`](backend/.env.example) for the full list with blank
placeholders) and, for CI, in **GitHub Actions repository secrets**:

| Secret | Used for |
| --- | --- |
| `OPENAI_API_KEY` | chat replies + agentic plan |
| `DEEPGRAM_API_KEY` | voice speech-to-text |
| `ELEVENLABS_API_KEY` | voice text-to-speech |

CI is hermetic by default (backend tests stub the providers; the e2e stack runs
with blank keys → voice disabled). Set the repo secrets above to exercise the
live loop in the e2e job. **If a key is ever pasted somewhere shared, rotate it.**

## Quick start (full stack)

```bash
docker compose up --build
# API:    http://localhost:8000   (docs: /docs)
# Web:    http://localhost:3000
# Admin:  http://localhost:3001
```

Seeded logins: `admin@cerebro.app / admin12345`, `pawan@cerebro.app / demo12345`.

## Tests
- **Backend:** `docker compose run --rm api sh -c "pip install -r requirements-dev.txt && pytest -q --cov=app"` (85 tests; CI enforces a **≥82% coverage gate** via `--cov-fail-under`, hermetic run sits at ~84%). Covers voice, assessment, account deletion/export, Apple-token verification, admin, and the service layer.
- **iOS:** `xcodebuild test` in `apps/ios/` (12 UITests, incl. 2 live-backend tests, a fresh-signup starters test, and a compliance + games walk-through)
- **Web + admin (e2e):** `docker compose -f docker-compose.e2e.yml up --build --abort-on-container-exit --exit-code-from e2e` (7 Playwright tests) — see [`e2e/README.md`](e2e/README.md)

All three run in **CI** on push/PR via [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
(jobs: `backend` on Ubuntu+Postgres, `e2e` on Ubuntu+Docker, `ios` on a macOS
Simulator). The two live-backend iOS tests self-skip in CI since no API runs on
the macOS runner.

> iOS note: the app talks to `http://localhost:8000` from the Simulator. A merged
> `apps/ios/Info.plist` adds `NSAllowsLocalNetworking` so ATS permits it in dev.

## Production deployment

A separate hardened stack lives in [`docker-compose.prod.yml`](docker-compose.prod.yml):

```bash
cp backend/.env.production.example backend/.env.production   # then fill in real values
PUBLIC_API_URL=https://api.your-domain.com \
  docker compose -f docker-compose.prod.yml up -d --build
```

How it differs from dev:
- **Real builds** — web/admin run `next build` + `next start` (not the dev server),
  installed via `npm ci` from committed lockfiles for reproducibility.
- **Backend** runs **gunicorn** with uvicorn workers (`WEB_CONCURRENCY`), as a
  **non-root** user, with a container healthcheck.
- **Fail-fast config** — with `ENV=production`, the API refuses to boot on insecure
  defaults: weak/placeholder `SECRET_KEY`, demo `ADMIN_PASSWORD`, `SEED_DEMO_DATA=true`,
  or wildcard CORS (`app/core/config._guard_production`).
- **Locked down** — Postgres is **not** published to the host; app ports bind to
  `127.0.0.1` only (front them with a TLS reverse proxy); `restart: unless-stopped`.
- **Auth rate limiting** (slowapi) on `/auth/login` + `/auth/signup`, plus baseline
  security headers on every response.

Secrets live only in `backend/.env.production` (git-ignored) or your secret manager.
Without AI/voice keys the app still runs — chat uses local fallbacks and the voice
loop reports disabled via `/voice/status`.

## Screenshots
`screenshots/` holds the iOS walkthrough (47 screens + contact sheet), the landing
page (`web-landing.png`), the admin dashboard (`admin-*.png`), and the live iOS
cloud-sync screen (`ios-cloud-connected.png`).

## Design principles
- **Brand** — orb-lotus app icon + a branded night-lake launch splash
  (`Assets.xcassets`, `Features/Splash/SplashView.swift`); the OS launch screen
  is a navy field so there's no white flash before the splash fades in.
- **One theme, centrally tuned** — every color, corner radius, hairline and
  gradient lives in `DesignSystem/Theme.swift` as a one-directional hierarchy:
  `Brand` (raw swatches lifted from the logo + splash — night `#080B22`,
  periwinkle `#6F7BF7`, iris `#8FA8FF`, lavender `#CBB6FF`, aurora cyan `#36C7F5`,
  aurora violet `#9B6BFF`, lotus teal) → semantic `Palette` (background/text/
  accent roles) → `Accent` (per-section hues) → `Radius`/`Stroke`/`Gradient`
  (shape + glass details). Screens and the shared `Components.swift` read tokens,
  never raw colors, so the splash and the whole app share one palette and retune
  from a single place.
- **Calm by default** — dark night theme, serif headings, periwinkle/lavender
  accents drawn from the orb-lotus mark.
- **Proactive, not noisy** — gentle nudges, agentic plans that adapt to real signals.
- **Privacy-first** — explicit consent flags gate what the AI remembers.
- **Safety-aware** — journal/chat are scanned for crisis signals that surface
  resources and an admin review queue (wellness support, never a clinical gate).
