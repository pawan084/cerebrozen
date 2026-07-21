# Developing — run it, test it, and the traps that cost us days

Last updated: 2026-07-21.

Everything here is **measured on this codebase**, not general advice. Most of it exists
because it cost someone hours. Read the traps before the commands.

---

## Run the stack

```bash
./launch.sh                       # build from source, wait for health, open the 3 front-ends
npm run stack:up --prefix e2e     # or the raw compose: db, redis, engine, platform, web, admin, app
```

`./launch.sh` wraps compose: it frees a stray local dev server off 3000-3002, rebuilds from
your current source, health-checks every surface, and opens landing/admin/app in the browser.
`./launch.sh down` stops the stack with **plain** `down` (preserves data) — unlike
`stack:down`, which is `down -v` and wipes the engine's Postgres. `./launch.sh logs` tails.

| Surface | Port | What |
|---|---|---|
| web | 3000 | marketing site |
| admin | 3001 | ops + HR console |
| app | 3002 | employee web client |
| **platform** | **8100** | auth, orgs, people, privacy, analytics (FastAPI + **sqlite** locally) |
| **engine** | **8000** | coaching turns (SSE), prompts, RAG, safety (FastAPI + **compose Postgres**) |

Health is **`/health`**, not `/v1/health`. Session start is `POST /v1/sessions/start`
(`/v1/sessions` is GET/DELETE only).

### Cross-surface URLs — dev vs production

Each front-end links out to the others (the web "Sign in" menu → app/admin; app/admin →
their APIs and back to the marketing site). Every such URL is a `NEXT_PUBLIC_*` value
**inlined at build time**, so it's fixed per build, not per request. The dev↔prod switch:

| Set on | Var | Dev (local) | Production default |
|---|---|---|---|
| web | `NEXT_PUBLIC_APP_URL` | `http://localhost:3002` | `https://app.cerebrozen.in` |
| web | `NEXT_PUBLIC_ADMIN_URL` | `http://localhost:3001` | `https://admin.cerebrozen.in` |
| app, admin | `NEXT_PUBLIC_API_URL` | `http://localhost:8100` | `https://api.cerebrozen.in` |
| app | `NEXT_PUBLIC_ENGINE_URL` | `http://localhost:8000` | `https://api.cerebrozen.in/engine` |
| app, admin | `NEXT_PUBLIC_SITE_URL` | `http://localhost:3000` | `https://cerebrozen.in` |

**web** needs no config: `site.ts` defaults the two portal links on `NODE_ENV`, so
`next dev` links localhost and `next build` links the prod subdomains automatically. Set
the env vars only to override (e.g. point a dev build at staging).

**app / admin** read the env directly. For `next dev`, `cp .env.example .env.local` in each
(the examples already carry the localhost values). For production, the Dockerfiles bake the
prod defaults via build `ARG`; `./launch.sh` builds those images, so a local compose stack
still links the **prod** hostnames — run the raw `next dev` servers when you want the
front-ends cross-linked to each other on localhost.

### Dev sign-ins (seeded by the platform on boot)

| Persona | Credentials | Role |
|---|---|---|
| ops | `admin@cerebrozen.in` / `admin12345` | `internal_admin`, no org |
| HR | `hr@cerebrozen.in` / `demo12345` | `org_admin` of Demo Co |
| employee | `demo@cerebrozen.in` / `demo12345` | `user` of Demo Co |

Same three in `e2e/tests/helpers.ts` (`PERSONAS`) and the admin's dev chips. Keep them in
step; a prefill naming an account nobody seeds is worse than an empty field (it happened —
Android prefilled `worker@acme-test.example` and tapping Continue silently did nothing).

---

## Traps

### `npm run stack:down` DESTROYS **both** services' data

It is `docker compose down -v`. The `-v` removes the `cere_pg-data` volume — and under
compose that one volume holds **both** databases (see the next trap). You lose the engine's
conversations, escalations, wellness and vector index **and** the platform's accounts, orgs,
invitations, subscriptions and consent history. Use plain `docker compose down` unless a
total wipe is the point.

Recovery: `docker compose up -d db --wait`; the shim recreates the engine's tables and the
platform reseeds its personas on boot. Anything you created by hand — a real signup, an
invite you redeemed, a purchase — is gone for good.

### `JWT_SECRET` is base64, and a bad one silently disables auth

`config.py` does `base64.b64decode(JWT_SECRET)`. Sign a hand-made test token with the
**decoded bytes**, not the string. A malformed value decodes to `b""` → treated as unset →
with `ENV=local` that turns **auth off entirely** (`auth_enabled()` false), so your test
passes against no auth at all.

The engine and platform must share it or every platform-issued token 401s at the engine.
There is no committed `.env`; both read the shell.

`docker-compose.yml` publishes a dev secret on purpose (a fresh clone must run). It cannot
reach production: both services **refuse to boot** a non-dev `ENV` with it, or with any
secret decoding to a dev placeholder.

### Two databases — and **where** they live depends on how you started the platform

This is the trap, and it is the opposite of what this doc used to say. The engine is always
the compose Postgres (`localhost:55432`). The **platform moves**:

| How you started the platform | Its database |
|---|---|
| `docker compose` / `npm run stack:up` / `launch.sh` | **Postgres** — `cerebrozen_platform` on the **same `db` container and the same `pg-data` volume as the engine** (`docker-compose.yml:53`, `deploy/initdb/01-platform-db.sql`) |
| bare `uvicorn app.main:app` with no `DATABASE_URL` | **sqlite** — `services/platform/platform.db` (`services/platform/app/config.py:14-16`) |

So "two unrelated databases, wiping one does not touch the other" is true **only** for the
bare-uvicorn path. Under compose they share a volume, which is why `stack:down -v` above
takes your accounts with it.

Two practical consequences:

- **Your signup can vanish while your engine data survives, or neither** — check which
  platform you actually started before concluding data was lost.
- **Editing platform code may change nothing you can see.** Two platforms can bind `:8100`
  at once — the compose container binds `[::]:8100` (IPv6) while a bare local uvicorn binds
  `127.0.0.1:8100` (IPv4). They coexist. `curl localhost:8100` from the Mac may resolve to
  IPv6 → the **container**, but `adb reverse tcp:8100` forwards to IPv4 → the **local
  uvicorn**. So your phone and your curl can hit different servers running different code,
  and the symptom is the app 404-ing an endpoint curl says works. Confirm with
  `lsof -nP -iTCP -sTCP:LISTEN | grep 8100` (docker vs python), then edit and restart the one
  the app actually reaches. The bare uvicorn has **no `--reload`**, and it needs the same
  base64 `JWT_SECRET` handed to it on restart or its tokens 401 at the engine.

If sign-in works but coaching does not, you are looking at the wrong database.

### The engine suite runs on mongomock; **Postgres is what ships**

This is the single biggest source of bugs in this repo's history — **six** so far, each of
which passed a green suite while being broken in production (`find_one(sort=)`,
missing `insert_one`, `_project` dropping `_id`, a key guess that overwrote crisis records,
a cold-start `CREATE TABLE` race, erasure never matching a thread id).

`tests/conftest.py` provides **`pgdb`** and **`requires_pg`**. If you are testing store
BEHAVIOUR, use them. A unit test against mongomock proves nothing about the shim.

### The eval harness is meaningless without a real model

`python -m scripts.eval` against the **mock** provider scores ~75% while ignoring the
system prompt entirely (canned `coaching_path=CIM`, 0.0s/case). It cannot see a prompt
regression. Give it a real key, or `--provider ollama`.

It now prints the **KB state next to the score**: a 100% over a 0-row index means "the
routing contract holds with no knowledge base attached", not "the coaching works".

### gitleaks must run from the repo root

The allowlist is `.gitleaks.toml` at the root; run it from `services/engine` and it finds
7 "leaks" that are all false positives. CI names `GITLEAKS_CONFIG` explicitly.

---

## Test

```bash
cd services/engine  && .venv/bin/python -m pytest --cov -q   # 96% branch gate
cd services/platform && .venv/bin/python -m pytest --cov -q  # 95% gate
cd e2e && npx playwright test                                # needs the stack up
node scripts/sync-tokens.mjs --check                         # design-token drift
gitleaks detect --no-banner                                  # FROM THE ROOT
```

The e2e suite runs against **either** compose or hand-started dev servers (same ports). Dev
servers already on 3000-3002 make `stack:up` fail on a port bind.

---

## Android

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # no system JDK
cd apps/android
./gradlew :app:assembleDebug -PapiBaseUrl=http://localhost:8100 \
                             -PengineBaseUrl=http://localhost:8000
adb reverse tcp:8000 tcp:8000     # engine
adb reverse tcp:8100 tcp:8100     # platform  <-- BOTH, or nobody signs in
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Two tunnels, not one.** The app talks to the platform for auth and the engine for turns.
A single 8000 tunnel gets you a sign-in screen that does nothing.

`adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` against a build signed with
a different key → `adb uninstall com.cerebrozen.app` first (this clears sign-in). Re-run
`adb reverse` after any replug. Emulator builds default to `10.0.2.2`.

Driving the UI: dump to a file and parse with python —
`adb exec-out uiautomator dump /dev/tty > /tmp/ui.xml` — piping straight into shell
arithmetic breaks on multi-match output. `adb exec-out screencap -p > f.png`. More in
`apps/android/README.md`.

---

## The rules that are not style

`CLAUDE.md` is authoritative. The two that catch people:

- **Rule 5, "counts, never content"**: no admin/HR surface or API exposes transcripts,
  journals, or commitment bodies. It is why the safety queue shows *that* someone hit the
  crisis screen and never *what they said*, and why the reference's "Excerpt" column must
  never be ported.
- **Rule 4, safety is code**: the crisis screen and its reply live in code, never in the
  editable prompt workbook. An e2e asserts no workbook agent can be the crisis responder.

`ref/` is **read-only**. Never edit it, never run it, and never reuse a credential from it.

## Verify by driving it

Nearly every bug found on 2026-07-17 — the empty safety queue, erasure reporting success
while deleting nothing, the Android tab that stopped being itself, an upload 500 with an
empty body — was invisible in the code and obvious within a minute of using the thing.
The suite was green for all of them.

Run it. Click it. Read the screenshot.
