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

**Use the demo account for any UI walk.** Since 2026-08-16 `pawan@cerebro.app`
boots with a month of history — 19 check-ins across 30 days, 12 sleep nights, 5
journal entries (each carrying a `mood:` tag, so the journal pills are visible),
an active 3-step plan with one step done, and a Sleep Reset enrolment on day 4
(`backend/app/seed.py` `_seed_demo_journey`, behind `SEED_DEMO_DATA`). An empty
account hides most of the design: charts, rings, presence dots and the
re-engagement card all render their empty states.

It is idempotent via a **marker** on the rows it writes (`MoodLog.trigger =
"demo-seed"`), not via "does this account have data" — a demo account collects
stray taps the moment anyone opens it, and a presence check would then refuse to
seed forever. Seeding only ever INSERTs, so a tester's own rows are never
touched.

> **The api image is baked** (no source bind-mount). Editing `seed.py` needs
> `docker compose up -d --build api` — a plain `docker compose restart api`
> re-runs the OLD code and looks like the seed silently did nothing.

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
| Voice | `DEEPGRAM_API_KEY`, `ELEVENLABS_API_KEY` | `/voice/status` reports disabled; admin narration generation (`POST /admin/content/{id}/narrate`) 503s |
| Bot protection | `BOT_CHALLENGE_SECRET` (+ `BOT_CHALLENGE_PROVIDER`, `turnstile` or `hcaptcha`) | The challenge is inert — `verify_challenge` returns True without ever calling out, so signup and waitlist behave exactly as before. The throwaway-address check needs no key and always runs. Note the failure directions differ on purpose: a provider that REJECTS a token refuses the signup, a provider that cannot be REACHED does not (see `services/botcheck.py`) |
| Email verification gate | `SMTP_HOST` (the same one email uses) + `UNVERIFIED_DAILY_MESSAGES` | With no SMTP the gate is **inert** — `verification.gate_active()` is False and every account is exempt, because a verification nobody can receive is not a gate. Set SMTP and unverified free accounts lose voice/plans/goals/assessment/oracle and get `UNVERIFIED_DAILY_MESSAGES` chat messages a day instead of `FREE_DAILY_MESSAGES` |
| **Testing the enabled paths** | — | The e2e stack runs a second `api-gated` service with all of the above switched ON (plus a `challenge-stub` standing in for Cloudflare), driven by `e2e/tests/gated-api.spec.ts`. The main `api` keeps them off because every browser test shares one address. Note `api-gated` builds to its own image: rebuild it explicitly, or it silently keeps running old code |
| Media | `MEDIA_ROOT` (default `media`, relative to the working dir) | generated narration MP3s (`/media/narration/`) and admin-uploaded catalogue assets (`/media/assets/`) land here, served at `/media` behind a signed per-item grant (`MEDIA_TOKEN_TTL_HOURS`, default 12 — premium narration is not fetchable by URL alone); prod compose mounts the named `media` volume at `/app/media` so files survive redeploys (dev bind-mount writes to git-ignored `backend/media/`). **Route order:** `app.include_router` must stay above `app.mount("/media", …)` — the media router owns `GET /media/catalog` under the same prefix, and a Mount registered first would swallow it (`tests/test_media_catalog.py` locks this) |
| Sign-in | `APPLE_CLIENT_ID`, `GOOGLE_CLIENT_ID` | social sign-in 400s; email auth works |
| Subscriptions | `APPSTORE_BUNDLE_ID`, `APPSTORE_ROOT_CERT_PATH` | unpinned chain when blank (dev); prod template pins to the bundled `app/certs/AppleRootCA-G3.pem` |
| Nudges | `NUDGE_DISPATCH_INTERVAL_MINUTES` | default 5; 0 = external cron via `POST /admin/nudges/dispatch` |
| Push/Email/SMS | `APNS_*`, `SMTP_*`, `TWILIO_*`, `OPS_ALERT_EMAIL`, `APP_BASE_URL` | logged instead of sent |
| Web Push (browser nudges) | `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` — self-generated pair (`npx web-push generate-vapid-keys` emits env-ready base64url strings); no third-party account | web client's notifications toggle disables with an honest note; delivery logs |
| Web billing | `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_*`, `STRIPE_RETURN_URL` | checkout 503s; webhook rejects |
| Play billing (receipt verification) | `PLAY_LICENSE_KEY` — the developer public key from Play Console → Monetisation setup, base64 DER; `PLAY_PACKAGE_NAME` (default `com.cerebrozen.app`), checked against the purchase's own `packageName` because a valid signature over ANOTHER app's purchase is still valid | `/users/me/subscription/verify-play` refuses every purchase with a 400 rather than trusting the client — for a paywall the honest no-op is "unverified, therefore not premium" |
| Web Apple sign-in | `APPLE_SERVICES_CLIENT_ID` (second token audience) | native audience only |
| Landing → app links (client) | `NEXT_PUBLIC_APP_URL` (`apps/web`, build arg in all three compose files; default `http://localhost:3002`, prod `https://app.cerebrozen.in` via `PUBLIC_APP_URL`) | Every "Open the app" link on the landing points at localhost — the page still builds and renders, so this fails silently in production. `e2e/tests/landing.spec.ts` asserts the hrefs against `APP_URL` to catch it |
| iOS Google sign-in (client) | `GIDClientID` **in `apps/ios/Info.plist`** (the OAuth *iOS* client id, read by `GoogleAuth.clientID`) plus a `CFBundleURLTypes` entry whose scheme is the **reversed** client id (`com.googleusercontent.apps.<id>`). Both need the real credential — a placeholder registers a bogus URL scheme, so neither is committed. | `GoogleAuth.isConfigured` is false, the button degrades with an honest notice, and email/OTP sign-in is unaffected |
| Web app social (client) | `NEXT_PUBLIC_GOOGLE_CLIENT_ID`, `NEXT_PUBLIC_APPLE_SERVICES_ID`, `NEXT_PUBLIC_APPLE_REDIRECT_URI` (`apps/app`) | Apple/Google buttons stay inert (no SDK loaded, honest notice) — email/OTP works; when enabling, add the provider script/connect hosts to each app's `middleware.ts` CSP (the apps set their own CSP with a per-request script nonce; Caddy only sets one for the API) |
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
| Backend | `docker compose run --rm api sh -c "pip install -r requirements-dev.txt && python -m pytest -q --cov=app"` | ~445 async tests; needs live Postgres (fixtures call `init_db()`); `TESTING=1` set by conftest disables rate limits. CI gate: `--cov-fail-under=95` (`.coveragerc` omits prestart/seed/agent/oracle — LLM-streaming code is integration-only). **Run it with the LLM keys blank** — a populated `backend/.env` makes `test_habits`/`test_safety_plan` take the live-LLM path, which fails their keyless assertions and bills real calls |
| Backend per-module coverage floors | `pytest --cov-report=json:coverage.json` then `python tests/coverage_floors.py` | Nine modules where a silent regression costs most carry their own floor, independent of the global 95%. The global gate protects an average, and safety.py is 63 statements out of ~6,100 — deleting all its tests moves the global number about one point. Floors are a ratchet: lower one deliberately, in a diff, with a reason |
| Backend mutation check | `docker compose run --rm api python tests/mutation/run.py` | Twelve curated mutants against `services/safety.py` and `services/entitlements.py` — the two modules where a false-passing test costs most. Exits 1 on a survivor. Found three real gaps on its first run, all in safety, including that NOTHING in the suite stubbed the classifier to disagree, which made the keyword floor's whole reason for existing untestable. Add a mutant by writing the wrong behaviour in prose in `tests/mutation/catalogue.py` |
| iOS | `xcodebuild test -project apps/ios/CereBro.xcodeproj -scheme CereBro -destination '<simulator>'` or `bundle exec fastlane ios test` | XCUITest walk-throughs; pass `-resetState YES` for determinism (wipes state, seeds demo streak, skips splash, disables the real audio engine, **and every auto-advancing timer in the guided routines** — an ungated one makes the suite wait forever). Cloud tests `XCTSkip` without a reachable backend. **Physical-device runs:** export `CEREBRO_TEST_SERVER=http://<mac>.local:8000` — the runner forwards it as the app's API base URL, and the suite auto-**allows** the resulting iOS Local Network prompt (`allowLocalNetworkIfAsked`), which otherwise belongs to springboard, swallows every tap and kills the run at the first assertion with a misleading "element not found". Simulator runs poll for 0s and pay nothing. `apps/ios/CereBroTests/` (`ContrastTest`, `RitualsTest`) is written but **not yet in the project** — it needs a one-time Unit Testing Bundle target named `CereBroTests` in Xcode, after which synchronized file groups pick both files up |
| Web unit (4 Next apps) | `npm ci && npm test` (repo root) · `npm run coverage` · typecheck is TWO projects: `npx tsc --noEmit` and `npx tsc -p tsconfig.portal.json` | Vitest + jsdom, one runner at the ROOT rather than four per-app setups: the `lib/` modules are plain TypeScript, only `apps/app` uses the `@/` alias, and one coverage number for the whole web surface is the point. Tests live in `tests/`, NOT beside the sources, so `next build` and the production images never see them. **Before this there were no unit tests and no coverage instrumentation in any of the four apps** — 71 Playwright tests were the entire net. Covers the offline write queue, the `?next=` allow-list, both crisis directories, the DPDP consent notice, the session/error mapping in the app and admin API clients, the portal IA + privacy-wall copy, `pageMeta`, the anonymous analytics contract, the onboarding draft, the Oracle SSE reader, voice capture and Web Push. **1,156 tests.** Coverage is **98.8%** over `lib/` AND `components/`, every file walked (the two dead components that held it at 95.8% were deleted 2026-08-22). Historically: **`lib/` was 97.6%** (web 100%, app 97.7%, admin 97.3%, portal 97%), components are newly in scope and mostly uncovered, so the headline reads ~50%. The scope widened on purpose — a number that excluded components was flattering. Mutation-tested: 191 mutants, 187 caught, 4 proven equivalent, 5 real weaknesses found and fixed. Component tests use `@testing-library/react` and need `resolve.dedupe` for react/react-dom, or the component and the test library get separate React copies and every render dies on a null hook dispatcher. `testTimeout` is 20s because the API-client tests re-import their module per case (the token lives in module state on purpose) and the transform, not the test, is what takes the time |
| Web+Admin+App+Portal e2e | `docker compose -f docker-compose.e2e.yml up --build --abort-on-container-exit --exit-code-from e2e` | Playwright; isolated network; asserts landing, waitlist, admin CRUD, the web app's journey, and the portal against a real organisation it provisions itself. **Compare *declared* against *passed*** — `retries: 1` means a flaky test is reported as passed in the summary line, which hid a real defect on 2026-08-13. `portal-a11y.spec.ts` adds axe (`@axe-core/playwright`, serious+critical only) plus phone-viewport, keyboard and `prefers-reduced-motion` checks |
| Android | `cd apps/android && JAVA_HOME="<Android Studio>/jbr" ./gradlew :app:testDebugUnitTest :app:jacocoLogicCoverageVerification` | JVM + Robolectric unit tests (no emulator, no keys). **Coverage gate: ≥95% line coverage over the testable logic scope**, mirroring the backend's `--cov-fail-under=95`. The gate task prints total + per-package percentages and fails below 95; HTML report at `app/build/reports/jacoco/jacocoTestReport/html/`. The gate is also wired into `:app:check`, but `check` additionally runs `lintDebug` (currently red: untranslated Hindi strings) and `testReleaseUnitTest` (currently red: Robolectric Compose tests need the debug-only `ui-test-manifest`) — pre-existing debt, independent of the coverage gate |

⚠️ **Backend suite wall-clock on a Windows/OneDrive checkout is meaningless — don't
tune against it.** The same 330 tests measured 8, 10, 21 and 60 minutes across four runs
on one machine with no code change between the last two. `--durations=25` shows why the
number is not the tests: the slowest twenty-five sum to ~110 s (worst single test
11.7 s), so ~98 % of a 60-minute run is outside test bodies entirely. The cause is the
`./backend:/app` bind mount — Docker Desktop crossing the Windows filesystem is slow and
highly variable, and a OneDrive-synced path makes it worse (the same class of problem as
the read-only `media/` mode already documented in the Dockerfile). Linux CI is the
meaningful timing signal; locally, run a single test file while iterating and treat the
full suite as a pass/fail gate, not a benchmark.

Android coverage scope (defined with rationale in `apps/android/app/build.gradle.kts`):
the gate measures `net/**` (Session/Api/Analytics), the audio controllers
(`MediaUrls`/`Player`/`SoundscapeMixer`), `notify/**` (Reminders/BootReceiver),
`health/**` (Health Connect prefill), `ui/theme/**` (palettes, AppTheme, typography,
CereBroTheme) and the two screen classes not dominated by composable bodies
(`ConsentNoticeKt`, `StoresKt`). Excluded, same philosophy as the backend's
`.coveragerc` (paths that cannot run hermetically): Compose screen rendering
(`ui/screens/**` — top-level `@Composable` bodies compile into the same `*Kt`
classes as the pure helpers, which ARE unit-tested, so a line gate over them would
measure whatever Robolectric happens to render), the foreground audio services
(`AmbientService`/`SoundscapeService` — real MediaPlayer/ExoPlayer + MediaSession),
`CloudVoice`/`VoiceEngine`/`ToolAmbience` (framework recorder/STT/TTS),
`GoogleAuth` (Credential Manager UI), `MainActivity`, and generated code
(BuildConfig/R/ComposableSingletons). Even the raw HttpURLConnection transports are
gated — they run against a loopback socket server in `SessionTransportTest`.

Provider stubbing in backend tests is monkeypatch-based: swap `httpx.AsyncClient` for fakes,
patch `ai.complete`/`ai.complete_json`, toggle `settings` key properties, assert email/SMS via
`sent_outbox`. Tests stay hermetic with blank keys.

## CI/CD

- **ci.yml** (push/PR): `backend` (Ubuntu + Postgres service, coverage gate), `web`
  (`tsc --noEmit` for apps/web + apps/admin + apps/app), `e2e` (Docker stack, live keys
  optional via repo secrets), `ios` (macos-15, picks a simulator via `simctl`), `android`
  (`continue-on-error`). Concurrency cancels in-progress runs.
- **Copy and pricing gates** run in the `web` job, before the typechecks, because both
  guard things review has already let through once:
  - `scripts/check-claims.mjs` — banned phrases across all four clients (web, app, iOS
    Swift, Android `strings.xml`). Every entry is there because it shipped; retire one by
    fixing the copy or adding the claim to `docs/CLAIMS_MAP.md`, never by allowlisting.
  - `scripts/check-prices.mjs` — every quoted `₹` price against
    `apps/ios/CereBro/Products.storekit`, which is what a user is actually charged. Added
    after the Android paywall drifted 25% under every other surface; prices are
    hand-written in four places, so nothing else could notice. `₹0` (the free tier) is
    ignored.
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
