# Engineering Standards

Last updated: 2026-07-14

These are inherited from the two references — both enforce their standards
mechanically (build-failing gates), and that discipline is the reason their
codebases are trustworthy. We keep every gate.

## Testing gates (build-failing, non-negotiable)

| Surface | Gate | Reference precedent |
|---|---|---|
| Coaching engine | pytest, branch coverage `fail_under=96`, fully offline on the mock provider | `ref/Agent/.coveragerc` |
| Platform API | pytest ≥95% coverage, runs without any API keys | `ref/Zen/backend` |
| Android | JaCoCo 95% on the gated scope (documented include/exclude), Robolectric for UI logic | `ref/Zen/apps/android/app/build.gradle.kts` |
| Admin + web | Playwright e2e against the composed Docker stack | `ref/Zen/e2e` |
| Design tokens | sync script `--check` fails CI on drift; contrast test fails on AA violations | `ref/Zen/scripts/sync-tokens.mjs` |
| Engine quality | evals harness (routing-contract golden cases) nightly, off the merge path | `ref/Agent/evals` |
| Safety | crisis red-team suite runs in CI; its catch-rate is a **release gate**, published on the Evidence page | `ref/Agent/tests/test_crisis_redteam.py` |

Additional CI checks: gitleaks (secret scanning), `.env`-is-ignored check,
ruff (Python), ktlint/detekt (Kotlin), eslint (web/admin), and the
workbook-loadability gate (a prompt-workbook change that fails validation
cannot merge).

## Principles (the references' rules we adopt verbatim)

1. **Everything degrades without keys.** Every service boots and every test
   suite passes with zero external credentials: mock LLM provider, local
   fallbacks, no-op integrations. A developer clones and runs in minutes.
2. **No LLM call exists solely to route.** Routing is code predicates over
   typed state. The one model-emitted routing field is schema/grammar-forced
   and drift-monitored (contract counters, never exceptions on the user path).
3. **Safety is code, not content.** Crisis reply text and the takeover path
   live in source control, not the editable workbook; a content author must
   not be able to weaken a safety mechanism.
4. **Coaching content is config, not code.** The workbook is versioned by
   content hash, validated before it can ship, hot-reloadable, reversible.
5. **Counts, never content.** No admin or HR surface ever renders a
   transcript, journal, or commitment body. This is enforced in APIs (the
   endpoints don't exist), not by UI restraint.
6. **Secrets never committed.** `.env*` git-ignored with committed
   `.env.example` placeholders; prod boot-guard refuses default secrets
   outside dev envs.
7. **Loud degradation.** Every fallback (checkpointer, prompt source, RAG,
   provider cascade) logs loudly and flips `/health` to `degraded` — silent
   fallback is how the references' worst bugs shipped.
8. **One source of truth per contract.** The stage→node table, the token
   file, the analytics vocabulary — each lives in exactly one place; the
   references both document bugs caused by hand-maintained duplicates.

## Conventions

- **Python** (both services): 3.12, FastAPI, Pydantic v2, structlog JSON,
  OpenTelemetry; `ruff` formatted. LangGraph stays on the engine's pinned
  major until deliberately migrated (the pin is load-bearing).
- **Kotlin**: 2.x + Compose, no Hilt/Retrofit/Room — the zero-SDK transport
  (`Session.kt` pattern) is the standard; new deps need a written
  justification in the PR.
- **TypeScript**: Next.js App Router; admin keeps zero runtime deps beyond
  next/react; marketing site conventions as established in `apps/web`.
- **Naming**: env prefix `CEREBROZEN_`, metric prefix `cerebrozen_`, one
  rename sweep from reference names at adoption time — never a partial
  migration (the AgentMan reference documents the cost of a half-done one).
- **Branches/commits**: trunk-based, short-lived branches, conventional
  commit prefixes (`feat:`, `fix:`, `docs:`…); every PR states which docs it
  updates or why none.

## Cross-stack change protocol

Any change to a contract listed in ARCHITECTURE.md §"Cross-stack contracts"
must: (1) update the contract table in the same PR, (2) land server-side as
backward-compatible first, (3) note the earliest client version that may
rely on it. SSE vocabulary and JWT claims changes additionally require an
e2e run against the composed stack before merge.

## Definition of done (per feature)

Code + tests meeting the gate + docs touched (PRODUCT/ARCHITECTURE/TODO as
applicable) + runs in `docker compose up` from a clean clone + degrades
without keys + no new contrast/lint/secret findings. For anything touching
the coaching path: evals still green and the crisis red-team rate not
regressed.
