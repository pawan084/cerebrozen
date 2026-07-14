# Fork Notes — deviations from the reference (`ref/Agent`)

Adopted 2026-07-14. Everything not listed here is unchanged from the
reference at adoption time.

## Rename sweep (single pass, complete)

938 replacements across 101 files: `AGENTMAN_*` → `CEREBROZEN_*` (env vars),
`agentman_*` → `cerebrozen_*` (metrics), `agentman.*` → `cerebrozen.*`
(loggers), `AgentMan*` → `CereBroZen*` (classes), `sherlock`/`xcalibrate`
resource-name defaults → `cerebrozen`, `agentman.css` → `cerebrozen.css`.
Verified zero old-brand terms remain.

## Code changes

1. `app/routers/flow.py::console_run` — user-prompt validation now runs
   BEFORE model resolution (was returning 500 instead of 400 on a missing
   prompt), and free-form console runs fall back to `MODEL_CASCADE[0]`
   instead of requiring `CEREBROZEN_MODEL_OVERRIDE`. (The reference's tests
   only passed because its local `.env` set the override — an env-dependent
   test suite, now hermetic.)
2. `app/stores/conversation.py` — sessions list sorts
   `[("updated_at", -1), ("_id", -1)]`: deterministic tiebreak when two
   sessions update in the same clock tick (was flaky on Windows, same bug
   possible on real Mongo).

## Test changes

3. `tests/test_whitelabel.py` — `read_text(encoding="utf-8")`: the default
   cp1252 on Windows crashed on UTF-8 sources (suite had only ever run on
   Linux/macOS).
4. `tests/test_llm_client.py::test_the_counter_window_expires…` — clock
   frozen mid-window: the fixed-window key embeds `int(time)//window_s`, so
   a real hour boundary between hits split them across windows (1-in-3600
   flake).

## Environment

- `agent_prompts.xlsx` is the CereBroZen fork (see repo `docs/PROMPTS_SPEC.md`):
  15 agents, legacy sheets dropped, environment wrapper rewritten
  45,014 → 1,739 chars, clean Catalog with an explicit environment row.
- `langsmith` pinned `<0.4` on this dev machine (its newer versions hard-import
  the Rust `uuid_utils`, whose DLL the local Windows Application Control
  policy blocks). `.localdev/uuid_utils/` is a pure-Python shim used via
  `PYTHONPATH=.localdev` for the same reason — dev-machine-only, never ship.
- pgvector integration tests expect Postgres at `localhost:55432`
  (`docker run -d --name cbz-pgvector -e POSTGRES_PASSWORD=pg -e
  POSTGRES_DB=cerebrozen -p 55432:5432 pgvector/pgvector:pg16`).

## Verified at adoption

- `pytest --cov`: **1,443 passed, 1 skipped, 0 failed; 98.66% branch
  coverage** (gate 96%). Fully offline on the mock provider (plus the
  pgvector container for the RAG integration tests).
- `/health`: 200, brand CereBroZen, workbook fork loaded (version
  `fdd2d65a9e11`), all 15 agents enabled. Status `degraded` without S3 —
  loud fallback to the bundled workbook, as designed.
- Known validation issues carried from reference content (fix during prompt
  adaptation): 5 oversize prompts; unknown placeholders in 4 agents
  (`{Question}`, `{time}`, `{Examples}`, `{userContext}`, `{GlcoachingIntake}`)
  — these are blanked at runtime today.

## Added after adoption

5. **App-layer tenancy (2026-07-14)** — `app/tenancy.py`: org contextvar set
   by `require_auth` from the JWT `org_id` claim (org-less tokens are 401
   unless `CEREBROZEN_REQUIRE_ORG_CLAIM=false`); `scoped()` on every filter
   in `conversation`/`agentic`/`dynamic_vars` stores; org-prefixed Redis
   keys and checkpointer thread ids; conversation business key is now
   **(org_id, session_id)** (compound unique index; legacy `session_id_1`
   semantics superseded); pre-tenancy org-less documents are readable by the
   default org only. Cross-tenant test class: `tests/test_tenancy.py`.
   Exception, by design: the external `users` profile collection is read
   unscoped — those docs carry no org field and per-tenant user provisioning
   is the platform service's contract.

6. **Postgres-first storage (2026-07-14)** — pgvector is now the default RAG
   backend whenever `POSTGRES_URL` is set (`CEREBROZEN_RAG_BACKEND=lancedb`
   opts out); docker-compose runs pgvector/pg16 + redis with Mongo demoted
   to `--profile mongo`; `.env.example` promotes `POSTGRES_URL` to primary.
   pg-shim fixes: `_matches` gained top-level `$and`/`$or` (tenancy filters
   silently matched nothing on PG without them), `PgCursor.sort` accepts
   pymongo's list form, and `PgCursor.skip` was added (missing in the
   reference — offset-paged session lists returned [] on the PG backend).

7. **Regulated mode default-ON (2026-07-14)** — unset
   `CEREBROZEN_REGULATED_WORKPLACE` now means REGULATED (emotion capture and
   person-scoring off); `=false` is the explicit opt-out. The reference
   defaulted these on for its incumbent. The test suite pins the
   full-feature baseline in `tests/conftest.py`; the default's direction is
   asserted in `tests/test_regulated_workplace.py`.
8. **Store seam late-binding (2026-07-14)** — `agentic`/`conversation`/
   `dynamic_vars`/`org` now call `mongo.get_client()` through the module
   (was `from … import get_client`). The early binding froze whichever
   function stood at first import — under pytest, a store first imported
   inside a patched test captured that test's client permanently
   (order-dependent test poisoning; found via the regulated pair).
9. **Provider decision (2026-07-14)** — OpenAI only for cloud, Ollama for
   air-gap. No Anthropic provider will be added; nothing may claim
   multi-vendor fallback.

## Still open (tracked in repo `docs/TODO.md` Phase 1)

Big-prompt adaptation · evals port · CI gates.
