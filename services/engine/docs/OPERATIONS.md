# Operations

## Running it

```bash
# Local (boots with only an OpenAI key; SQLite + fakeredis + mock LLM fill the gaps)
python3.12 -m venv .venv && .venv/bin/pip install -r requirements.txt
cp .env.example .env                     # set OPENAI_API_KEY
.venv/bin/uvicorn app.main:app --reload --port 8000

# Docker (api :8000, mongo :27017, redis :6379)
docker compose up --build
```

With `MONGO_DB_URL` / `REDIS_URL` empty the app falls back to SQLite/in-memory and fakeredis.
With no `OPENAI_API_KEY` it selects the **mock provider** — the whole graph runs offline with
deterministic canned replies, which is what the test suite uses.

## The env vars that actually matter

`app/config.py` reads ~100 vars. These are the ones that change behaviour you'll care about;
the rest are tuning knobs with sane defaults.

### Prompts
| Var | Default | Notes |
|---|---|---|
| `PROMPT_SOURCE` | `s3` | `codebase` = local file, `s3` = download on reload. |
| `PROMPT_WORKBOOK` | `./agent_prompts.xlsx` | The **only** var that selects the local workbook. |
| `PROMPT_S3_BUCKET` / `PROMPT_S3_KEY` | — | Required when `PROMPT_SOURCE=s3`. |
| `CEREBROZEN_LLM_PROMPT_CACHE` | `false` | **Set to `true`. Measured: 48% cheaper and 23% faster per turn.** It needed two fixes to work at all — see below. |

### Prompt caching: it was buying nothing, and now it pays for itself

Turning the flag on was never enough. Two separate defects had to be fixed, and each was
invisible:

1. **The prefix was never stable.** `{Time}` resolved to `datetime.now()` with *microsecond*
   precision, 130 tokens into a 16.5K-token prompt — so every turn had a unique prefix and
   **0%** could ever be cached. Fixed to hour granularity (`rag/placeholders.py`), pinned by
   `test_system_prompt_prefix_is_stable_across_turns`.
2. **The requests scattered across machines.** A cache only pays if the request LANDS on a
   machine holding the prefix. With no `prompt_cache_key`, OpenAI routes freely: measured
   **1 turn in 6** hit the cache even with a byte-identical prompt. Now keyed on
   `stage:workbook-version`.

Measured on a live 6-turn session (gpt-4o-mini, 16.5K-token system prompt):

| | cached tokens | cost/turn |
|---|---|---|
| before both fixes | **0%** | $0.00249 |
| stable prefix only | 16% | — |
| **+ cache key** | **83%** (99% after turn 1) | **$0.00129** |

The key is `stage:workbook-version` on purpose — **not** per-user. All users of an agent share
one system prompt, so they share one cache entry; a per-user key would fragment it and make
every new session pay full price. The workbook version busts the cache automatically on a
prompt edit, so a reload can never serve a stale prefix.

### Model / latency
| Var | Default | Notes |
|---|---|---|
| `CEREBROZEN_REASONING_<STAGE>` | per-stage | Overrides reasoning effort, e.g. `CEREBROZEN_REASONING_CORE=medium`. Stage keys are in `responses_client.STAGE_REASONING_EFFORT`. |
| `CEREBROZEN_STAGE_OPT` | `true` | `false` reverts every stage to the pre-optimization `low` effort. |
| `CEREBROZEN_MODEL_OVERRIDE` | — (leave UNSET) | Forces one model id for every agent, ignoring the Catalog. An escape hatch for an account that cannot reach a model the workbook names — **not** a default. `gpt-5.4` is a real model, not a placeholder (this table said otherwise until 2026-07-17); setting this flattens the Catalog's per-agent choice and, measured, runs 5–8× slower per turn (docs/EVALS.md). |
| `OPENAI_TIMEOUT` | | Per-request cap (one CIM call once took 341s). |

### Routing / features
| Var | Default | Notes |
|---|---|---|
| `CEREBROZEN_GRAPH_ENABLED` / `_PERCENT` / `_ALLOWLIST` / `_BLOCKLIST` | | Strangler gate — which traffic reaches the graph (`app/selector.py`). |
| `CEREBROZEN_ENABLE_MULTIPATH` | `true` | `false` forces every session down CIM (validation mode). |
| `CEREBROZEN_ENABLE_BUILDERS` | | Off-path background agents (actions, pattern, context model). |
| `CEREBROZEN_FORCE_HANDOFF` | — | **Test only.** Named stages hand off after one turn so a smoke test can reach the close. |

### Persistence
`MONGO_DB_URL`, `MONGO_CHECKPOINT_DB`, `MONGO_TIMEOUT_MS`, `REDIS_URL`,
`SQLITE_CHECKPOINT_PATH` (default `./cerebrozen.db`).

## Health

`GET /health` — includes prompt-registry state:

```json
{
  "status": "ok",                     // "degraded" when the registry is degraded
  "prompts": {
    "version": "2da1fe2d1510",        // content hash of what is LIVE
    "source": "codebase",             // codebase | s3 | s3-fallback
    "degraded": false,
    "validation_issues": 11
  }
}
```

`degraded: true` means **the registry is not serving what it was configured to serve** — S3 fell
back to the bundled workbook, or a reload failed and the previous prompts are still in memory.
The app keeps working; it is just not running what you think it is running.

`GET /metrics` — Prometheus.

## Metrics & alerts

Existing: `cerebrozen_llm_cost_usd_total`, `cerebrozen_llm_tokens_total` (by kind — **watch
`cached`**), `cerebrozen_llm_latency_seconds`, `cerebrozen_llm_calls_total`, all by stage + model.

Added, and the two most important on this list:

| Metric | Target | Alert when |
|---|---|---|
| `cerebrozen_agent_contract_violations_total{stage,contract}` | **~0** | any sustained non-zero — a prompt has drifted from its contract and the graph is silently taking a fallback path |
| `cerebrozen_stage_watchdog_total{stage}` | **~0** | any non-zero — a stage is not completing on its own; the watchdog is papering over it |
| `cerebrozen_rate_limited_total{bucket}` | low | a **spike** — either a client is retrying in a loop or someone is probing, and both end in an LLM bill |
| `cerebrozen_session_pacing_total{kind}` | `pause` healthy, `distress_route` low | a **rising `distress_route`** — the population using this is carrying more than a coaching product is the right answer for. Not an incident; a signal to check what the org is actually going through |
| `cerebrozen_boundary_prompted_total{kind}` | low, flat | a **climbing `attachment`/`persistence` count** — people are using a coaching product as a companion. A design signal long before it is a compliance one; the disclosure is firing, but something upstream is inviting the mistake |

Also worth alerting on, from logs that already exist:

| Signal | Meaning |
|---|---|
| `auth.dev_bypass_refused` (ERROR) | **`AUTH_DEV_BYPASS=true` reached a deployed environment.** Auth is still on (the flag is refused outside a development-class `ENV`), but the config that shipped intended to turn authentication off — find out how it got there |
| `prompt_store.s3_fallback` (ERROR) | serving stale bundled prompts |
| `prompts.reload_failed_keeping_previous` | a bad workbook was published |
| `checkpointer.mongo_unavailable_falling_back` / `checkpointer.memory_no_durability` | **sessions will not survive a restart** |
| `placeholder.blanked_unresolved` | a prompt references a variable nothing provides |
| `node.empty_reply_fallback` | the model returned nothing; the user got a filler line |
| `route.coaching_path_unset_fallback_cim` | challenge_context gave no usable path |
| `crisis.terms_file_unreadable` (ERROR) | the client's crisis lexicon didn't load; the built-in one is running instead |
| `crisis.reply_language_unavailable` (ERROR) | a crisis was detected in a language the lexicon can flag but nothing replies in — since every built-in language now has a reply, this means a **client term file** added a language without adding its reply. The user got English. Names the language |
| `crisis.reply_language_unreviewed` (WARN) | a drafted (not native-speaker-reviewed) crisis reply was served. Not an error — it is your **translation queue**, ordered by the languages people are actually in crisis in |

Suggested SLOs (from the July-6 review): TTFT p95 < 1.5s, turn latency p95 < 6s per stage,
cached-token share > 60%, contract-violation rate ~0.

## Safety and spend controls

Two guards protect against a *configuration* mistake rather than a coding one, which is
why neither fails visibly when it regresses.

**The dev auth bypass cannot be honoured in a deployment.** `AUTH_DEV_BYPASS=true` only
takes effect when `ENV` is one of `local`, `dev`, `development`, `test`, `ci`. Anywhere
else it is refused, auth stays on, and `auth.dev_bypass_refused` is logged at ERROR. The
failure mode of a misconfiguration is now "nobody can get in" (loud, immediate, fixable)
rather than "everybody can get in" (silent, indefinite, a breach).

**The paid endpoints are rate limited.** `POST /v1/sessions/{id}/turn` (20/min) and
`POST /v1/sessions/start` (30/hour), keyed on the **signed JWT subject** — never the
request body's `user_id`, which the caller controls and could rotate to escape the limit.
It fails **open**: a Redis outage must not become a coaching outage, and the LLM circuit
breaker is the real spend cap. **Without a shared `REDIS_URL` the counter is per-process**,
so N instances allow N× the configured rate — set `REDIS_URL` in any real deployment.

**The crisis screen is multilingual, and is not a classifier.** `app/graph/crisis.py`
covers ~20 languages with two matching strategies (letter-boundary + leet-tolerant for
Latin scripts, raw substring for CJK/Thai/Arabic/Devanagari, where `\b` never fires). It is a high-recall
pre-filter for *explicit* disclosures and will miss implicit ones — the coaching prompts
carry their own safety instructions and remain the second layer.

The reply is served in the user's language, with no LLM call (0 tokens, $0), by the
`safe_response` node. **Detection and reply now cover the same ~20 languages** — nothing
the screen can flag is answered in English any more. What is NOT equal across them is
review: only English has been read by a native speaker.

- Serving a drafted reply logs `WARNING crisis.reply_language_unreviewed{lang}`. **That
  log is your translation queue**, ordered by the languages people are actually in crisis
  in rather than by which markets sales is excited about. Reviewed text goes into
  `CEREBROZEN_CRISIS_MESSAGES_FILE` — no code change, no release.
- `ERROR crisis.reply_language_unavailable{lang}` now means something narrower and more
  urgent: a language we cannot even detect reached the reply path (usually a client term
  file in a new language, with no matching reply). Alert on it.
- `CEREBROZEN_CRISIS_REVIEWED_ONLY=1` takes the stricter posture — English instead of any
  unreviewed built-in reply, logged as `crisis.reply_language_suppressed_unreviewed`. It
  is the right setting where a clinical governance body owns crisis copy, and the wrong
  one where your users do not read English. See docs/SECURITY.md for the trade-off.

Every reply ends with an AI disclosure in its own language (`crisis._AI_DISCLOSURE`),
appended **after** the client override so a deployment can improve the body but cannot edit
a legally-required sentence out of it. It is last, not first: a person in crisis may read
two sentences, and those two must be the acknowledgement and the helpline.

The screen also tolerates obfuscated spellings (`su1c1de`, `k1ll_myself`, `$uicide`): each
letter accepts its leetspeak substitutes. It only ever adds *spellings* of terms already
in the lexicon — it cannot make a new word flaggable.

> **Before you enable a language, get its phrase list reviewed by someone who speaks it.**
> The built-in lexicon was assembled without native-speaker review. An inaccurate list is
> worse than an absent one because it looks like coverage. Corrections go in
> `CEREBROZEN_CRISIS_TERMS_FILE` (`{"latin": [...], "other": [...]}`) — no code change, no
> release, no waiting on us.

Set `CEREBROZEN_CRISIS_LINE` per region. The default routes to findahelpline.com because it
answers internationally; a hard-coded US 988 is worse than useless in the wrong market.

## Playbooks

**"A prompt edit didn't take effect."**
1. `GET /health` → compare `prompts.version` before/after your reload. Same hash = the content
   the server loaded did not change.
2. `degraded: true`? You are on the bundled fallback — your S3 edit is not being read.
3. `GET /v1/prompts/validate` → `orphaned_continuation` on your sheet means a blank row
   truncated your prompt and the tail below it was never loaded.
4. In Docker: the workbook is mounted from `./agent_prompts.xlsx`. Nothing else is read.

**"An agent is behaving as if a variable is empty."**
`GET /v1/prompts/validate` → `unknown_placeholders`. A token nothing can resolve is **blanked**
at runtime. Check the camelCase/snake_case trap in [AGENT_FLOW.md](AGENT_FLOW.md#naming-trap).

**"Sessions are being lost on restart."**
Look for `checkpointer.memory_no_durability` or `checkpointer.mongo_unavailable_falling_back` at
boot. A configured-but-unreachable Mongo degrades to SQLite, then to memory. It is loud now; it
did not used to be.

**"A stage is stuck / the session won't end."**
`cerebrozen_stage_watchdog_total` will be climbing for that stage. The watchdog force-advances
past the turn cap so users are not trapped — but the underlying prompt is not signalling
completion, and that is the real bug. Check for a contract violation on the same stage.

**"Latency regressed on one agent."**
Check that the stage is in `responses_client.STAGE_REASONING_EFFORT`. A stage **missing** from
that map runs at the model's default reasoning — the ~24–32s/turn class.

## Cost

Each turn logs `cost_usd`, `prompt_tokens`, `completion_tokens`, `cached_tokens`. The single
largest recurring cost is the environment prompt prepended to **every** call (currently ~45K
chars) plus the agent's own prompt — a real intake turn measures ~16.5K prompt tokens. Two
levers, in order: turn on prompt caching, then trim the oversize prompts the validator flags
(see [TODO.md](TODO.md)).
