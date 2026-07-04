# CereBro — Technical Reference

> Setup, environment, testing, CI/CD, deployment, conventions.
> Companions: [ARCHITECTURE.md](ARCHITECTURE.md), [TODO.md](TODO.md), root [CLAUDE.md](../CLAUDE.md).

## Stack

| Area | Tech |
| --- | --- |
| iOS | SwiftUI, iOS 17+, Xcode 27, zero external deps, XCUITest, fastlane |
| Android | Kotlin 2.0 + Jetpack Compose (Material 3) — scaffold only, non-blocking in CI |
| Backend | Python 3.12, FastAPI, async SQLAlchemy, Alembic, Postgres 16, pytest (asyncio auto) |
| Agent | LangGraph (Oracle), SSE streaming |
| Web/App/Admin | Next.js 14 App Router, React 18, TypeScript, Playwright e2e (landing :3000 · web app :3002 · admin :3001) |
| Infra | Docker Compose, Caddy (auto-HTTPS), GitHub Actions, Contabo VPS (Ubuntu 24.04) |

## Local development

```bash
docker compose up --build       # db + api :8000 (/docs) + web :3000 + admin :3001 + app :3002
```

Seeded dev logins: `admin@cerebro.app / admin12345`, `pawan@cerebro.app / demo12345`
(dev only — the prod boot guard rejects these).

iOS: open `apps/ios/CereBro.xcodeproj`, run on a Simulator. DEBUG builds talk to
`http://localhost:8000` (ATS allows it via `NSAllowsLocalNetworking`); a DEBUG-only
"Server URL" field in Cloud Sync lets a physical device point at your LAN IP.
The Xcode project uses synchronized file groups — new `.swift` files are auto-included.

## Environment variables (backend)

Full list with placeholders: `backend/.env.example`. Everything degrades gracefully when unset.

| Group | Vars | Effect when unset |
| --- | --- | --- |
| Core | `ENV`, `SECRET_KEY`, `DATABASE_URL`, `CORS_ORIGINS`, `WEB_CONCURRENCY` | dev defaults; `ENV=production` triggers the boot guard |
| LLM | `OPENAI_API_KEY` (`OPENAI_MODEL`) → `ANTHROPIC_API_KEY` (`AI_MODEL`) | deterministic local replies/plans/topics |
| Oracle | `ORACLE_ENABLED` (+ an LLM key) | `/oracle` 503 → clients use `/chat` |
| Voice | `DEEPGRAM_API_KEY`, `ELEVENLABS_API_KEY` | `/voice/status` reports disabled |
| Sign-in | `APPLE_CLIENT_ID`, `GOOGLE_CLIENT_ID` | social sign-in 400s; email auth works |
| Subscriptions | `APPSTORE_BUNDLE_ID`, `APPSTORE_ROOT_CERT_PATH` | unpinned chain when blank (dev); prod template pins to the bundled `app/certs/AppleRootCA-G3.pem` |
| Nudges | `NUDGE_DISPATCH_INTERVAL_MINUTES` | default 5; 0 = external cron via `POST /admin/nudges/dispatch` |
| Push/Email/SMS | `APNS_*`, `SMTP_*`, `TWILIO_*`, `OPS_ALERT_EMAIL`, `APP_BASE_URL` | logged instead of sent |
| Web billing | `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_*`, `STRIPE_RETURN_URL` | checkout 503s; webhook rejects |
| Quota | `FREE_DAILY_MESSAGES` | default free-tier cap |
| Seed | `SEED_DEMO_DATA`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | prod guard forbids demo values |

Provider selection is runtime, per-request capability checks (`settings.ai_provider`,
`stt_enabled`, `tts_enabled`, `oracle_available`) — no restart-time wiring beyond env.

**Secrets are never committed.** They live in git-ignored `backend/.env` /
`backend/.env.production` and GitHub Actions secrets. If a key is ever pasted anywhere
shared, rotate it.

## Testing

| Suite | Command | Notes |
| --- | --- | --- |
| Backend | `docker compose run --rm api sh -c "pip install -r requirements-dev.txt && python -m pytest -q --cov=app"` | ~138 async tests; needs live Postgres (fixtures call `init_db()`); `TESTING=1` set by conftest disables rate limits. CI gate: `--cov-fail-under=95` (`.coveragerc` omits prestart/seed/agent/oracle — LLM-streaming code is integration-only) |
| iOS | `xcodebuild test -project apps/ios/CereBro.xcodeproj -scheme CereBro -destination '<simulator>'` or `bundle exec fastlane ios test` | XCUITest walk-throughs; pass `-resetState YES` for determinism (wipes state, seeds demo streak, skips splash, disables real audio engine). Cloud tests `XCTSkip` without a reachable backend |
| Web+Admin e2e | `docker compose -f docker-compose.e2e.yml up --build --abort-on-container-exit --exit-code-from e2e` | Playwright; isolated network; asserts landing, waitlist, admin CRUD against seeded data |

Provider stubbing in backend tests is monkeypatch-based: swap `httpx.AsyncClient` for fakes,
patch `ai.complete`/`ai.complete_json`, toggle `settings` key properties, assert email/SMS via
`sent_outbox`. Tests stay hermetic with blank keys.

## CI/CD

- **ci.yml** (push/PR): `backend` (Ubuntu + Postgres service, coverage gate), `web`
  (`tsc --noEmit` for apps/web + apps/admin + apps/app), `e2e` (Docker stack, live keys
  optional via repo secrets), `ios` (macos-15, picks a simulator via `simctl`), `android`
  (`continue-on-error`). Concurrency cancels in-progress runs.
- **deploy.yml** (manual): SSH → `git reset --hard origin/main` → prod compose up → health-check
  loop on `https://api.cerebrozen.in/health`. Secrets: `DEPLOY_HOST/USER/SSH_KEY`; var `DEPLOY_PATH`.
- **testflight.yml** (manual): fastlane `ios beta` with App Store Connect API key
  (`ASC_KEY_ID/ASC_ISSUER_ID/ASC_KEY_CONTENT`).

## Production

```bash
cp backend/.env.production.example backend/.env.production   # fill real values
PUBLIC_API_URL=https://api.cerebrozen.in \
  docker compose -f docker-compose.prod.yml up -d --build
```

First-time server: `deploy/bootstrap.sh` (as root) — creates a deploy user, ufw + fail2ban,
Docker, generates strong `SECRET_KEY`/DB password/`ADMIN_PASSWORD` into `.env.production`,
brings the stack up; optional `--harden-ssh`.

Hardening in place: Caddy-only public surface with auto-TLS + security headers (shared
snippet in `deploy/Caddyfile`); internal-only db/app ports; non-root gunicorn with
healthcheck; migrations at boot (`prestart.py`, fails loudly in prod); `_guard_production`
refuses weak secret / demo admin password / `SEED_DEMO_DATA` / wildcard CORS; slowapi rate
limits on `/auth/*`, `/chat`, `/oracle/*`, `/voice/*`, `/waitlist` (keyed on
`X-Forwarded-For` behind Caddy); App Store receipts pinned to the bundled Apple root cert;
baseline security headers from the API. Remaining gaps tracked in [TODO.md](TODO.md).

## Release (iOS)

fastlane lanes in `apps/ios/fastlane/`: `test`, `build` (archive, timestamped build number),
`beta` (TestFlight), `metadata` (deliver en-US metadata). Bundle id `com.cerebrozen.app`,
team `9YG7G7YB2J`. StoreKit local products in `Products.storekit` (wired into the scheme);
App Store Connect products/notifications are config-only remaining work.
See [SHIP_READINESS.md](SHIP_READINESS.md) and [RELEASE_PLAN.md](RELEASE_PLAN.md);
privacy labels: [PRIVACY_LABELS.md](PRIVACY_LABELS.md).

## Conventions

- **Design tokens only** — iOS screens read `Theme.Palette/Accent/Radius/Stroke/Gradient`,
  never raw hex (only `Theme.Brand` + SplashView scenery hold literals). Web/admin mirror the
  palette as CSS vars in `globals.css`.
- **Graceful degradation everywhere** — new provider integrations must no-op cleanly without
  keys and be stubbed in hermetic tests.
- **Cross-stack contracts are manual** — taxonomy, widget kinds, crisis regions, and product ids
  are duplicated between backend and iOS (table in ARCHITECTURE.md). Touch both sides in one commit.
- **Safety never blocks** — crisis scanning adds resources/escalation; it must never reject or
  delay a user's message.
- **UI-test determinism** — anything animated/async/pop-up must be gated or settled under
  `-resetState` (splash skip, audio-engine off, no auto permission prompts).
- **Migrations** — schema changes ship as Alembic revisions; `prestart` applies them at boot.
