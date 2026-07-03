# CereBro — Project Context

CereBro is a privacy-first, safety-aware mental-wellness product: a native SwiftUI iOS app
(primary client) + FastAPI/Postgres backend + Next.js landing & admin, in one monorepo.
Ships native iOS first (Android scaffold exists); follows Apple App Store guidelines first.
Domain: **cerebrozen.in** · bundle id **com.cerebrozen.app**.

## Read these first

- [docs/PRD.md](docs/PRD.md) — product definition, feature inventory with honest shipped/partial/concept status, roadmap
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — system map, backend/iOS/web layers, data flows, cross-stack contracts
- [docs/TECHNICAL.md](docs/TECHNICAL.md) — setup, env vars, testing, CI/CD, deploy, conventions
- [docs/TODO.md](docs/TODO.md) — prioritized known debt + open work (update it when you fix or find something)
- [docs/RELEASE_PLAN.md](docs/RELEASE_PLAN.md) / [docs/SHIP_READINESS.md](docs/SHIP_READINESS.md) / [docs/PRIVACY_LABELS.md](docs/PRIVACY_LABELS.md) — launch runbooks

## Layout

```
apps/ios/      SwiftUI app (Xcode 27 project, synchronized file groups — new .swift auto-included)
apps/android/  Compose scaffold only
apps/web/      Next.js landing :3000        apps/admin/  Next.js dashboard :3001
backend/       FastAPI + Postgres :8000     e2e/         Playwright (web+admin)
deploy/        Caddyfile + bootstrap.sh     docs/        this doc set
```

## Commands

```bash
docker compose up --build                      # full dev stack (api/web/admin/db)
docker compose run --rm api sh -c \
  "pip install -r requirements-dev.txt && python -m pytest -q --cov=app"   # backend tests
docker compose -f docker-compose.e2e.yml up --build \
  --abort-on-container-exit --exit-code-from e2e                           # web+admin e2e
xcodebuild test -project apps/ios/CereBro.xcodeproj -scheme CereBro \
  -destination 'platform=iOS Simulator,name=<installed sim>'               # iOS UI tests
```

Dev logins (dev only; prod boot guard rejects them): `admin@cerebro.app/admin12345`,
`pawan@cerebro.app/demo12345`.

## Hard rules

- **Secrets never committed** — only git-ignored `backend/.env*` + GitHub secrets. If a key is
  exposed anywhere, rotate it.
- **iOS reads design tokens only** (`DesignSystem/Theme.swift`: Palette/Accent/Radius/Stroke/
  Gradient) — never raw hex in screens. Web/admin mirror the palette as CSS vars in `globals.css`.
- **Everything degrades without keys** — LLM/voice/push/email/SMS integrations must no-op
  cleanly and be stubbed in hermetic tests (CI runs with blank keys).
- **Safety never blocks** — crisis scanning adds resources/escalation, never rejects a message.
- **Cross-stack contracts are duplicated by hand** (assessment taxonomy, widget kinds, crisis
  regions, product ids) — change backend + iOS in the same commit; table in ARCHITECTURE.md.
- **Schema changes = Alembic revision** (applied by `prestart.py` at boot).

## Gotchas

- Backend tests need a live Postgres (fixtures call `init_db()`); in the api container use
  `python -m pytest` (the `pytest` console script isn't on PATH in the prod image).
- iOS UI tests pass `-resetState YES`: wipes state, seeds a demo streak, **skips the splash and
  the real audio engine** — keep new animated/async features gated the same way or the suite hangs.
- Live-backend iOS tests `XCTSkip` when `http://localhost:8000` is unreachable (that's why CI
  macOS runs show skips).
- iOS DEBUG talks to `http://localhost:8000` (ATS `NSAllowsLocalNetworking`); Release is
  hardcoded to `https://api.cerebrozen.in` (`Networking/APIClient.swift`).
- Sign in with Apple / Google are code-complete but **inert until configured** (no
  `.entitlements` file yet; no `GIDClientID`) — buttons degrade gracefully.
- Oracle (LangGraph agent) needs `ORACLE_ENABLED=true` + an LLM key; it checkpoints to
  Postgres (`AsyncPostgresSaver`), falling back to in-process MemorySaver only when the
  DB checkpointer can't init (logged).
- Nudge delivery runs in-process every `NUDGE_DISPATCH_INTERVAL_MINUTES` (0 = rely on an
  external cron hitting `POST /admin/nudges/dispatch`).
- Coverage gate is `--cov-fail-under=95` in CI; `.coveragerc` intentionally omits
  prestart/seed/agent/oracle (live-LLM streaming paths).

## Working agreements

- Keep `docs/TODO.md` current: tick items you complete, add debt you discover.
- Update `docs/ARCHITECTURE.md` / `docs/TECHNICAL.md` when you add a service, route group,
  env var, or cross-stack contract.
- Commit style follows history: short scope-prefixed subjects ("Landing: …", "Sleep: …",
  "Backend tests: …").
