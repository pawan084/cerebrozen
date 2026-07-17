# Developing — run it, test it, and the traps that cost us days

Last updated: 2026-07-17.

Everything here is **measured on this codebase**, not general advice. Most of it exists
because it cost someone hours. Read the traps before the commands.

---

## Run the stack

```bash
npm run stack:up --prefix e2e     # docker compose: db, redis, engine, platform, web, admin, app
```

| Surface | Port | What |
|---|---|---|
| web | 3000 | marketing site |
| admin | 3001 | ops + HR console |
| app | 3002 | employee web client |
| **platform** | **8100** | auth, orgs, people, privacy, analytics (FastAPI + **sqlite** locally) |
| **engine** | **8000** | coaching turns (SSE), prompts, RAG, safety (FastAPI + **compose Postgres**) |

Health is **`/health`**, not `/v1/health`. Session start is `POST /v1/sessions/start`
(`/v1/sessions` is GET/DELETE only).

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

### `npm run stack:down` DESTROYS the engine's data

It is `docker compose down -v`. The `-v` removes the `cere_pg-data` volume: conversations,
escalations, wellness, the vector index. Use plain `docker compose down` unless a wipe is
the point. (Recovery: `docker compose up -d db --wait`; the shim recreates its tables and
the platform reseeds personas on boot.)

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

### Two databases, and they are unrelated

Platform = **sqlite** (`services/platform/platform.db`). Engine = **compose Postgres**
(`localhost:55432`). Wiping one does not touch the other. If sign-in works but coaching
does not, you are looking at the wrong database.

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
