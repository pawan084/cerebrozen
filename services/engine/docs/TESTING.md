# Testing

```bash
.venv/bin/pytest -q                    # 1,373 tests, ~30s, fully offline
.venv/bin/pytest -q --cov              # + coverage; fails the build under 96%
```

**99.35% line-and-branch coverage.** Branch, not just line: it requires *both* sides of every
`if` to be exercised. In a system where a wrong branch silently routes someone into the wrong
coaching methodology, the branch is the thing worth covering.

The gate is `fail_under = 96` in `.coveragerc`, so a coverage regression fails CI.

## The one exclusion, stated out loud

**`app/voice/*` is excluded, and it is the only exclusion.** Not "hard to test" — *impossible to
import*: it is built on `livekit`, which is not a dependency of this service (the voice agent is a
separate deployable that joins a LiveKit room). `import app.voice.agent` raises
`ModuleNotFoundError`, so no test in this suite can execute a line of it.

The alternative was to install the SDK and mock the room, the track and the realtime session. That
would exercise our mocks, score ~231 statements, and prove nothing about whether voice works. It is
metric-gaming, so it is not done.

**What that costs: ~231 statements of shipped, untested code.** It is a real gap, tracked in
[TODO.md](TODO.md), not papered over in a config file. `routers/voice.py` sits at 90% for the same
reason — the 9 uncovered lines are the LiveKit token mint.

## The suite was not actually offline, and had not been for its entire life

`conftest.py` blanked `OPENAI_API_KEY`, which looks sufficient. It is not: `app.main` calls
`load_local_env()` at import, and the loader treats an *empty* variable as unset and overridable —
so it repopulated the key from the repo's own `.env`, and the "offline, no network" tests went out
to the real OpenAI API and **billed for it**. Nobody noticed because they still passed, only slowly
and for money. It was a third of the runtime (63s → 20s once fixed).

The provider is now pinned explicitly (`CEREBROZEN_LLM_PROVIDER=mock`), which a `.env` cannot undo.
The lesson generalises: **an offline guarantee built on "we removed the credential" fails the moment
anything puts the credential back.** Build it on "the network client is not the one we use."

Same class of bug, same fix: `conftest` also forces `POSTGRES_URL=""`. It is the seam that switches
the whole app from Mongo to the Postgres shim, so a developer with it exported in their shell
silently ran every Mongo store test against Postgres — 73 failures with no relation to the code.
**A suite that depends on your shell is not a suite.**

## What's covered

| Suite | Pins |
|---|---|
| `test_graph.py`, `test_nodes.py` | Routing and the turn engine. Every stage resolves to a node; the coaching slot dispatches by path; feedback is gated by the action check on **both** edges. The stuck-stage watchdog, completion floor/ceiling, breaker-open degradation, contract repair. |
| `test_contracts.py` | Each output-contract monitor, one test per production incident. |
| `test_crisis.py` | The crisis pre-filter across ~20 languages, and the reply in the user's language. |
| `test_security.py` | The dev auth bypass cannot be honoured in a deployment; the paid endpoints are rate limited. |
| `test_stores.py` | All five Mongo stores against `mongomock` — the real `$set`/`$push`/`$addToSet`/dotted-path expressions, asserted on resulting **document state**. |
| `test_rag_layer.py` | The Postgres JSONB shim and pgvector against a **real Postgres**; LanceDB against a real local directory. No cloud. |
| `test_rag_extract.py` | The nine extraction tokens, and the silent-degradation path that hid a dead RAG for months. |
| `test_llm_layer.py`, `test_llm_client.py` | Resilience (retry/backoff/breaker/cascade), pricing, the prompt store's S3 fallback, the Ollama grammar, the prompt-cache key. |
| `test_api_layer.py` | Every route through a real `TestClient`. The 7-day check-in rule at every boundary. The prompts route-order bug, pinned behaviourally *and* structurally. |
| `test_prompts_and_registry.py` | Content-hash versioning, the Catalog gate, validate-on-save, degradation. |
| `test_whitelabel.py` | No real person named in source; the crisis line is regional; a second tenant cannot inherit the first's infrastructure. |
| `test_config_obs.py` | Every env var is defended — a typo must yield the default, not crash the process at import. |

## Principles

**Never mock the module under test.** A test that mocks the thing it is testing passes forever and
catches nothing: it inflates coverage while making the suite *worse*. Fake only true boundaries — the
LLM, S3, the OpenAI SDK — and assert on **outcomes**: the document that landed in the store, the
status code on the wire, the tokens the user actually sees. "A mock was called" is not an assertion.

**Every test named after an incident is load-bearing.** `test_ch_must_emit_a_phase_milestone` is not
hypothetical — a 27-turn live session emitted that milestone zero times. If one of these fails, do
not weaken it; a regression class has come back.

**Routing is pure — test it as such.** Every edge is a code predicate over a state dict. You do not
need a graph run, a checkpointer, or an LLM to assert that a disabled `learning_aid` still routes
through the final action check. That test is a dict and an assert, and it caught a real bug.

**Never mutate the repo workbook.** Registry tests copy `agent_prompts.xlsx` into `tmp_path` first.

**A bug found gets a test named after the fix, not the bug.** A passing test called `test_BUG_x`
reads as "the bug is still here and we're fine with it".

## What this exercise found

Writing the tests was worth more than the number. Fifteen real bugs, all fixed, all now pinned —
see [BUGS_FOUND.md](BUGS_FOUND.md).

## Still not covered by anything

- **Voice.** See above. Needs an integration suite against a real LiveKit server.
- **Multi-turn scenario tests through the real edges.** Several bugs lived *across* turn boundaries,
  where single-node unit tests are blind.
- **Coaching quality.** The suite proves the machine works. It says nothing about whether the
  coaching is any good — that is [EVALS.md](EVALS.md), and it needs a real model.
- **RAG with a real corpus.** Every test here uses a local LanceDB or Postgres with synthetic
  documents. Nobody has yet seen this system coach with its real evidence base attached.
