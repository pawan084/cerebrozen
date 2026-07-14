# Prompt registry

Agent behaviour lives in `agent_prompts.xlsx` so non-technical staff can change it without a
deploy. That makes the workbook a **production config surface** — so it gets config's
guarantees: versioning, validation, audit, and loud failure.

Code: `app/llm/prompts.py` (the registry), `app/llm/prompt_store.py` (where the file comes
from), `app/routers/prompts.py` (the admin API).

## The workbook

| Sheet | Holds |
|---|---|
| one per agent (e.g. `core_coaching_agent`) | the system prompt, in cell **B7** |
| `Catalog` | which agents are on, and which model each uses |
| `extraction` | the RAG extraction definitions (queries, filters, top-k) |
| `dynamic_variables_persistent` | which captured variables persist, and how often |

**B7 and the spill.** Excel caps a cell at 32,767 characters. A longer prompt continues into
**B8, B9, …** and the loader concatenates them. Two consequences:

- The reader **stops at the first blank cell**. A blank row in the middle of a spilled prompt
  silently truncates it — the classic "my edit vanished". The loader now scans past the blank
  and reports the orphaned rows (`orphaned_continuation` in the validation report).
- Fragments are joined with **no separator**, so a split can land mid-word. That's expected.

**Catalog columns**: `agent_name | role | enabled | model | sheet_name | description`. It is
keyed by **`sheet_name`**, and `enabled` / `model` are looked up **by header name**. An agent
missing from the Catalog is **disabled**. A Catalog missing those headers disables *everything*
except the always-on agents — so a workbook whose Catalog the loader can't read is a silent
"nothing runs" failure. `scripts/seed_prompts.py` asserts the round-trip for this reason.

**Always-on**: `environment` (the guardrail layer) and `feedback_mood_capture_agent` (the sole
path to a terminal close — if it could be disabled, sessions could never end).

## Versioning

The registry hashes the loaded content (all prompt text + the Catalog) into a short
**version** (e.g. `2da1fe2d1510`). Identical content → identical version, regardless of file
bytes or mtime.

It is the answer to "what is actually live?" (`/health`, every admin response) **and** the LLM
prompt-cache key:

- `CEREBROZEN_LLM_PROMPT_CACHE=false` (default) — a random UUID is prepended to every system
  prompt. Guarantees a 0% cache hit rate: safe, but you re-pay ~8–16K prompt tokens *per turn*.
- `CEREBROZEN_LLM_PROMPT_CACHE=true` — the **version** is stamped instead. The prefix is stable
  across requests (real cache hits, big TTFT + cost win) and a prompt reload changes the hash,
  so **the reload busts the cache by itself**. This is what you want outside local dev.

## Validation

Runs on **every load** (report at `GET /v1/prompts/validate`, issue count in `/health`) and on
**every save** (hard errors block the write; warnings are returned to the author).

| Check | Why it matters |
|---|---|
| `enabled_no_prompt` | An enabled agent with an empty sheet cannot run a turn. **Blocks a save.** |
| `enabled_no_model` | The Catalog is the only source of model selection; a blank cell raises at *turn* time. Catch it at load time instead. |
| `missing_sheets` | The stage sheet isn't in the workbook. |
| `not_in_catalog` | The agent has no Catalog row → it is silently disabled. |
| `orphaned_continuation` | Content below a blank cell in column B — **not loaded**, i.e. a silently truncated prompt. |
| `oversize` | Past ~24K chars (~6K tokens). Not an error, but a prompt that big is a latency/cost/quality smell in itself. |
| `unknown_placeholders` | A `{token}` that no data source can resolve — it will be **blanked** at runtime. Catches a typo'd or renamed variable before users see the hole. |

A token counts as resolvable if it is a registered RAG extraction, a variable in the capture
registry, a declared `CereBroZenState` field, or a known context key
(`prompts.KNOWN_CONTEXT_TOKENS`). **If you add a new context variable, add it there** or the
validator will (correctly) flag every prompt that uses it.

## Placeholders

One resolver: `app/rag/placeholders.py`. It resolves RAG tokens (via the extraction registry,
concurrently) and context tokens (from the turn context) in a single pass, before the model
call.

- An unresolved **context** token is **blanked** — a raw `{token}` must never reach the model
  or the user.
- An unresolved **RAG** token is left in place and retried next turn.

> The old `placeholder_replacement.py` left unresolved tokens as literal `{text}` and hardcoded
> `userName` → `"bibek"`. It is dead and out of the repo (`../archive/`). Don't resurrect it.

Why blanking matters beyond cosmetics: a literal `{coaching_style_context}` reads as a
*non-empty value* to a prompt's field-presence gate — which is how a first-time user got
treated as a repeat user.

## Editing prompts

### The admin API

| Endpoint | Does |
|---|---|
| `GET /v1/prompts` | full snapshot: every agent, enabled, model, size, text, + version/provenance |
| `GET /v1/prompts/{stage}` | one agent |
| `GET /v1/prompts/validate` | the issue report above |
| `PUT /v1/prompts/{stage}` | edit `prompt` / `enabled` / `model` → validate → write → reload. In S3 mode it publishes back to the canonical object **with an automatic backup**. |
| `POST /v1/prompts/reload` | re-read the workbook (re-downloads in S3 mode) |
| `POST /v1/prompts/upload` | publish a whole workbook: validate → back up the current S3 object → replace → reload |
| `GET /v1/prompts/checksum` | confirm the server cache matches S3 |
| `GET /v1/prompts/download` | the workbook the registry actually loaded |

All require auth. Mutations log `prompts.audit` with the caller (JWT `sub`) and the version
before/after. Concurrent edits are serialized — two edits used to be able to load the same base
workbook, with the second save silently dropping the first.

### Adding a new agent

1. Add the sheet to the workbook, prompt in **B7**.
2. Add its Catalog row (`sheet_name`, `enabled`, `model`).
3. Register it in `prompts.STAGE_SHEET` **and** add a stage constant in `graph/state.py`.
4. Wire it into `build_graph.STAGE_NODE` and add the node.
5. Set its reasoning effort in `responses_client.STAGE_REASONING_EFFORT` — **do not skip this**.
   A stage missing from that map runs at the model's *default* reasoning, which is the ~24–32s
   per turn defect class (measured: `action_checkin` 31.9s → 4.8s once mapped).

Steps 3–5 are code. That is deliberate: prompts change behaviour, code changes structure.

## Where the workbook comes from

`PROMPT_SOURCE`:
- `codebase` — the local file at `PROMPT_WORKBOOK` (default `./agent_prompts.xlsx`).
- `s3` — downloaded from `PROMPT_S3_BUCKET`/`PROMPT_S3_KEY`, cached to a temp file.

If S3 is unreachable, the registry falls back to the bundled workbook — **and says so**:
`prompt_store.s3_fallback` at ERROR, plus `degraded: true` in `/health`. Serving stale prompts
in an S3-configured environment is an incident, not routine.

If a *reload* fails, the previous prompts keep serving (a bad upload must never take prompts
down) and the registry is marked degraded. Only the very first load is allowed to fail hard —
there is nothing to serve on a cold start.

## Seeding

```bash
python -m scripts.seed_prompts     # writes PROMPT_WORKBOOK, then loads it back and asserts it works
```

The script loads the file it just wrote through the real registry and fails if any agent comes
back empty or disabled. A seed script that emits a workbook the loader can't read is worse than
no seed script.
