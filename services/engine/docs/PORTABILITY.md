# Porting to a second client: offline, Postgres, no S3

**Verdict: offline works — but ONLY if you shrink the prompts first.**

I ran a full coaching session on the local 8B with no OpenAI at all, and turn 2 came back in
**1.5 s**. But the same model on the **worst-case prompt (CH, 26,857 tokens) took 19.4 s** — 3×
over budget. The good result and the bad result are the same model on the same server; the only
difference is prompt size. That is the whole finding.

Everything below is *measured on your Ollama at `122.180.255.176:11434` with the real prompts*.
Where independent research (RULER/NoLiMa, structured-output benchmarks) agrees or disagrees with
my measurements, I say so.

---

## 1. Offline / Ollama — VIABLE, with three hard conditions

### Measured, end-to-end (real graph, real prompts, `OPENAI_API_KEY` empty)

| Turn | prompt tokens | prompt-eval | KV cache | total |
|---|---|---|---|---|
| 1 | 17,671 | 2,664 ms | **miss** | 5.6 s |
| 2 | 17,709 | **47 ms** | **hit** | **1.5 s** |
| 3 | 17,756 | **114 ms** | **hit** | 4.3 s |

Cold turn 5.6s, warm turns 1.5–4.3s. **Inside your <6s turn budget.** The coach produced real,
coherent coaching text and the graph routed on it correctly.

### The three conditions — none optional

**(a) The system prompt must be byte-identical across turns.** It is ~20–27K tokens. Re-reading
it costs 2.7s of prompt-eval, which alone blows the 1.5s TTFT budget. Ollama's KV cache reuses an
identical prefix — that's the 2,664ms → 47ms drop above, a **56× speedup**, and it is the *only*
reason this is viable.

> **It was broken.** `{Time}` resolved to `datetime.now()` with **microsecond** precision, 130
> tokens into the prompt. Every turn had a unique prefix → 0% cacheable → ~21,000 tokens
> re-encoded *every turn*. Fixed (hour granularity) in `app/rag/placeholders.py`; pinned by
> `test_system_prompt_prefix_is_stable_across_turns`.
>
> **This was also costing you money on OpenAI today** — the prompt caching in the current
> product was buying ~nothing for the same reason.

**(b) Every field the graph ROUTES on must be `required` in the schema.** This is the single most
important engineering finding here, and it is counter-intuitive.

Ollama's `format: <json-schema>` constrains decoding to a grammar, so the envelope is
**structurally guaranteed** — our real `parse_control` accepted the output unmodified. But *shape*
is not *correctness*, and the research is right to warn about it. Measured:

| `coaching_path` in the schema | what the 8B did | consequence |
|---|---|---|
| **optional** | **omitted it — 3/3 CH-shaped goals** | router sees no path → **silent CIM fallback → the CH path is unreachable** |
| **required** | committed to a value, and got it **right 5/5** (3 CH, 2 CIM) | routing works |

Grammar-forcing turns *"the model might mention a path"* into *"the model must pick one"* — and
once forced to commit, it picked correctly. `_ROUTING_REQUIRED` in `providers/ollama.py` encodes
this per stage.

**Honest caveat:** 5/5 on unambiguous cases is encouraging, not proof. Published benchmarks put
value-accuracy for open-weight models well below 100%, and ambiguous goals are where this will
fail. It needs a golden eval set per stage before you trust it in production — which is the eval
harness already sitting at the top of `TODO.md`.

**(c) `think: false` is mandatory.** gemma4 is a reasoning model. Left alone it spent its **entire**
output budget on hidden thinking and returned an **empty string** (`done_reason: length`, 0 chars).
That is a silent, total failure — the coach just says nothing.

All three are implemented in `app/llm/providers/ollama.py`, behind the existing `LLMProvider` seam.
Switching is config only: `CEREBROZEN_LLM_PROVIDER=ollama`. **No node was changed.**

### What is actually on that server

| model | local? | verdict |
|---|---|---|
| `gemma4:latest` (8B, Q4_K_M, 9.6 GB) | **yes** | the only genuinely offline option — and it works |
| `kimi-k2.7-code:cloud` | **no** | `:cloud` = runs on Ollama's servers. **Needs internet. Not air-gapped.** |
| `glm-5.2:cloud` | **no** | same |

For an air-gapped install, **gemma4 8B is the only candidate here.** Budget ~10 GB of VRAM/unified
memory for weights, plus KV cache for a 32K context.

### Gotcha that will bite you

**Ollama's default `num_ctx` is 4096.** Send it a 20K-token prompt and it **silently truncates** —
the model reads the *middle* of its own instructions and behaves bizarrely, with no error. The
provider sets `num_ctx=32768` explicitly. Worst case (CH_coaching + environment) is **26,682
tokens**, so 32K is the floor; anything less is broken.

### What you lose leaving OpenAI

| Lost | Replacement |
|---|---|
| `reasoning_effort` (the main latency lever) | gone — no equivalent. Levers become `num_predict`, model size, quantisation. Ignored, not emulated. |
| Server-side prompt caching | **Ollama KV prefix cache — better**, if (a) holds. Measured 56× on prompt-eval. |
| Cost accounting (`cost_usd`) | meaningless — self-hosted. Reports `0.0`. Track GPU-seconds instead. |
| Structured outputs | **`format: <schema>` — stronger.** Grammar-constrained, not prompt-requested. |

---

## 2. The prompts do NOT need to shrink. Keep them.

I said twice that the prompts were the blocker. **Measurement refuted me both times.** Here is
what actually turned out to be true.

### The 19.4s CH turn was a COLD CACHE, not a big prompt

| CH_coaching_agent (27,487 tokens) | prompt-eval | turn |
|---|---|---|
| turn 1 — cold | 8.99 s | **23.2 s** |
| turn 2 — warm | 0.37 s | **2.5 s** |
| turn 3 — warm | 0.44 s | **4.9 s** |
| turn 4 — warm | 0.39 s | **1.7 s** |

Warm, the biggest prompt in the system runs in **1.7–4.9 s** — inside budget. The cost is paid
once per prefix, not once per turn.

### The model caches ONE prefix at a time — so a stage change is what hurts

Measured: warm CH → call CORE → CH is **evicted** (9.35 s to re-read). Ollama is single-slot.
So the pain isn't prompt size; it's **stage transitions**, of which a session has ~6–8.

### The fix: prewarm the next agent, because the graph already knows who it is

The graph is deterministic. The moment a stage hands off, we know exactly which agent runs next —
so we load its prompt into the KV cache **in the background**, during the seconds the user spends
reading the last reply and typing the next one.

```
stage transition, no prewarm : user waits 10.6 s
stage transition, prewarmed  : user waits  1.8 s      ← same model, same prompt
```

Implemented: `builders.dispatch_prewarm()`, fired from the engine **only when the stage changed**
(prewarming mid-stage would evict the prefix the user is about to reuse and make them *slower*).
`CEREBROZEN_ENABLE_PREWARM=true`. Across a real 8-turn session: **6 cache hits, 2 misses**, and
prompt-eval collapsed from 5.4–5.8 s to **0.29–0.44 s**.

### What is actually left, and it is not the prompts

Turn-time breakdown over that session:

```
TOTAL prompt-eval :  13.3 s   ← solved (prewarm + stable prefix)
TOTAL generation  :  47.9 s   ← 78% of the time
```

I assumed a 21K KV cache would tax decode. **It does not** — measured 38 tok/s on a ~10-token
prompt vs 44 tok/s on the full 21K prompt. Prompt size costs you prompt-*eval*, and nothing else.

So the remaining latency is **raw decode throughput** (~40–55 tok/s on this box) × reply length
(150–330 tokens) ≈ 3–7 s. Mean turn ≈ 7.7 s.

**This is a hardware question, not a prompt question.** Shrinking the prompts would not have
fixed it. To hit p95 < 6 s: put it on a real GPU (an 8B Q4 does 100–150 tok/s on a 4090, which
lands the same replies in 1.5–3 s), or pick a faster model. No prompt edit required — which is
exactly what you asked for.

### One production caveat: don't share the Ollama server

Single-slot caching means **any other user of that Ollama evicts your prefix.** Two of the eight
turns above missed for exactly this reason. In production, give the app its own Ollama (or raise
`OLLAMA_NUM_PARALLEL` so several prefixes stay resident — each slot costs its own KV memory).

---

## 3. Postgres — the checkpointer is DONE and PROVEN

Ran the client's exact target stack: **Postgres + Ollama, no Mongo, no OpenAI, no S3, no Redis.**

```
checkpointer.postgres          ← chosen
turn 1  4.8s   turn 2  1.2s   turn 3  2.5s
checkpoints=15  sessions=1  (4 langgraph tables created)

… then KILLED the server and restarted it (fresh process, empty memory) …

resume same session → coach: "We've touched on the difficulty of approaching your
manager, and you mentioned that fear — the worry of looking incapable."
checkpoints now: 20
```

**Session state survives a restart in Postgres.** That is the durability guarantee, verified —
not asserted. Implemented in `build_graph.get_checkpointer()`: `POSTGRES_URL` → Postgres → Mongo
→ SQLite → memory, each fall-back loud.

### Two traps that cost me real time — you will hit both

**1. `langgraph-checkpoint-postgres` 3.x SILENTLY BREAKS THE APP.** Installing the current version
drags in `langgraph-checkpoint>=4`, which is incompatible with the `langgraph 0.2.x` core this app
is built on. Result: **3 tests fail and the graph breaks.** It is *not* a drop-in — my first draft
of this document said it was, and that was wrong.

> **Pin `langgraph-checkpoint-postgres==2.0.21`.** Moving to the 3.x line means upgrading
> LangGraph itself, which is a separate project with its own migration.

**2. `PostgresSaver.from_conn_string()` yields a DEAD connection.** It is a context manager — the
connection closes as soon as the generator is collected, so the saver fails on its first real query
with `the connection is closed` and falls through to SQLite. **You must pass an explicit
`ConnectionPool`** with `autocommit=True`. (The loud-fallback logging is what caught this; the
silent version of that code would have quietly run on SQLite and nobody would have known.)

### The stores: DONE TOO — via a Mongo-compatible shim, not a rewrite

The five store modules are ~2,200 lines of pymongo. But the API surface they *actually* use is
tiny (measured): `find_one` ×16, `update_one` ×14, `find` ×2, `count_documents` ×2, `delete_one`,
one `aggregate` — with operators `$set $push $each $slice $addToSet $setOnInsert $inc $ne $exists`.

Small enough to **emulate**. `app/stores/pg.py` implements a collection that quacks like pymongo
and stores each document as a JSONB row. Result: **all 5 store modules and all 17 caller files run
unchanged** — the Postgres port is a config switch, not a rewrite.

**One seam, not five patches:** `stores/mongo.get_client()` returns a Postgres-backed client when
`POSTGRES_URL` is set. Everything downstream — including the read path that feeds `profile_read` —
follows automatically.

Proven on the full stack (**Postgres + Ollama; no Mongo, no OpenAI, no S3, no Redis**):

```
demo-fresh      safety → profile_read → intake      "…ready to pick things up again."
demo-repeat     safety → profile_read → challenge   "Welcome back. What's on your mind today?"
demo-checkin    safety → profile_read → checkin     "Last time we spoke…"
```

Three users, **three different paths** — which only happens if the stores genuinely read back.
`demo-checkin` is check-in-due with 2 eligible actions, identical to the Mongo backend.

**And Mongo still works, unchanged.** With `POSTGRES_URL` unset the old path is byte-for-byte
what it was. The seam is *additive*: one codebase serves both clients.

### Three silent bugs this port surfaced — all in the emulation, all now tested

Each one left the app *running* while quietly losing data. This is the failure mode to fear.

| Bug | Symptom | Why it was silent |
|---|---|---|
| `count_documents(query, limit=1)` — shim didn't accept `limit` | **every repeat user read back as FRESH** | the store caught the `TypeError` and degraded to "fresh" |
| `find()` returned a list, not a cursor | `.sort().limit()` blew up | same swallow-and-degrade |
| Seeder used Mongo's `actions.$[].ts` all-positional operator | **check-in never became due** | on JSONB it wrote a literal key named `"actions.$[].ts"` and changed nothing |

`tests/test_backends.py` pins all three, plus the operator semantics ($slice keeps the *last* N,
$addToSet doesn't duplicate, $setOnInsert doesn't overwrite).

### RAG: pgvector — DONE, and S3 is now gone entirely

`app/rag/pgvector_store.py` replaces LanceDB-on-S3 behind the *same* interface, so
`extractors.py` and everything above it is unchanged. `CEREBROZEN_RAG_BACKEND=pgvector`.

Proven end-to-end — retrieval → extraction → the placeholder in the live coaching prompt:

```
ingested 3 evidence-based concepts into pgvector (dim=1536) — no S3, no LanceDB
Extract1 → status=resolved
  "Care personally while challenging directly …"     ← for "avoiding a hard conversation"
{SSKB_Concept} still a literal token?  False
retrieved concept IS in the coach's prompt: True
```

The retrieval is also *semantically* right, not just mechanically wired: the query
"avoiding a hard conversation with my manager" pulled back Radical Candour.

**Org isolation holds.** The metadata pre-filter runs BEFORE the ranking, so a CSKB query
scoped to one org cannot surface another org's documents — verified (`org_id=other → 0 hits`).
That is the security-critical property of a per-client knowledge base.

Index choice: **HNSW over cosine**, not IVFFlat. IVFFlat's lists must be trained *after* the
data is loaded — indexing an empty table gives you silently terrible recall. HNSW needs no
training step.

> **⚠ EMBEDDINGS ARE NOT PORTABLE.** OpenAI is 1536-dim; `nomic-embed-text` is 768. A query
> vector from one model **cannot** search an index built with another. There is no migration —
> only a re-index. Postgres rejects the mismatch, but with `expected 8 dimensions, not 1536`,
> which tells you nothing; `_ensure()` now raises an error that says *re-ingest*.

### The one thing genuinely BLOCKED (and it's on your side)

**That Ollama server cannot embed at all.** It answers `/api/embed` with:

```
This server does not support embeddings. Start it with `--embeddings`
```

— for *every* model, including chat models. And no embedding model is installed. So the
**fully** air-gapped RAG path is blocked until whoever runs `122.180.255.176:11434`:

1. restarts Ollama with embeddings enabled, and
2. pulls an embedding model (`ollama pull nomic-embed-text`).

The code is ready (`CEREBROZEN_EMBED_PROVIDER=ollama`); it has nowhere to call. Everything above
was proven with OpenAI embeddings, which validates the *store and the integration* — only the
embedding provider is outstanding.

### Where the port stands

| Layer | Status |
|---|---|
| **Graph state** (checkpointer) | ✅ **Postgres — survives restart** |
| **Stores** (~30 functions) | ✅ **Postgres — routing parity with Mongo** |
| **LLM** | ✅ **Ollama — no OpenAI** |
| **Prompts** | ✅ local workbook — **S3 gone** |
| **RAG store** | ✅ **pgvector — S3 gone** |
| **Embeddings** | ⚠️ code ready — **blocked: their Ollama has `--embeddings` off** |
| **Prompt size** | ❌ CH is 19.4 s offline — **the remaining gate** |

## 3b. Notes on the store port

The stores already expose a **clean public function API**; Mongo is an implementation detail
behind it. **17 caller files stay untouched** if the signatures hold.

| Store | public functions to reimplement |
|---|---|
| `agentic.py` | 12 (`load`, `append_actions_insights`, `set_action_status`, `get_action`, `save_intake_vars`, …) |
| `conversation.py` | 13 (`record_turn`, `get_prior_transcripts`, `list_sessions`, `delete_session`, …) |
| `dynamic_vars.py` | 2 |
| `org.py` / `mongo.py` | 3 |
| **total** | **~30 functions** |

**Checkpointer:** `langgraph-checkpoint-postgres` (**verified on PyPI: 3.1.0, actively maintained**)
is a drop-in for the Mongo saver in `build_graph.get_checkpointer()` — one function, already the
single choke point. `pgvector` 0.5.0 likewise.

> The deep-research pass could **not** substantiate the Postgres/pgvector/embedding/licensing
> questions — zero claims survived its own verification step. I checked the packages directly
> instead. Treat anything in this section as verified-by-me, not by literature; the embedding
> swap (`nomic-embed-text`) and the **commercial-resale licence terms of gemma4** are still
> **open questions you must answer before selling this**.

**The one real design decision:** the stores use document-shaped operators (`$push`, `$addToSet`,
`$each`, `$slice`, `$setOnInsert`, aggregation). Two options:

- **JSONB columns** — keep the document shape, port fastest, least risk. Recommended for v1.
- **Proper relational** (users / sessions / messages / actions / insights) — cleaner and
  queryable, but rewrites all ~2,200 lines of store code.

**RAG → pgvector:** the vector search is cosine + metadata prefilter + top-k
(`app/rag/store.py:search`). That maps to pgvector 1:1. Embeddings are **1,536-dim** today
(`text-embedding-3-small`); an offline swap to `nomic-embed-text` (768-dim) both removes the
internet dependency and halves the index size.

---

## 4. Dropping S3 entirely

| Uses S3 today | Replace with |
|---|---|
| `llm/prompt_store.py` (workbook download/upload) | local file, or a Postgres blob/table |
| `rag/store.py` (LanceDB on S3) | pgvector |
| `rag/startup.py`, `rag/ingest/*` | ingest from a local directory |
| `voice/ssm_config.py` (AWS SSM) | env vars / Postgres config table |

None of it is load-bearing — `PROMPT_SOURCE=codebase` already bypasses S3 entirely, and it's what
you run today.

---

## 5. White-labelling — the part that is actually hard

A full audit is in the footprint report. **The technical port is the easy half.** Three items are
**stop-ship** for a handover:

1. **A live OpenAI API key is sitting in `.env`.** Revoke it. Today.
2. **Real QA staff are named in code comments** — `mithilesh+31…+37`, `vikash+340`, `romila+200`,
   `puja+107` (14 occurrences, mostly `graph/nodes.py`), plus a real session id. These are the
   previous client's employees' plus-addressed emails. This is a **legal/GDPR exposure**, not a
   style nit.
3. **Production Mongo ObjectIds** are hardcoded as test users (`CEREBROZEN_TEST_USERS`,
   `config.py:222`) and get their LLM calls stubbed.

Then the substantive one: **the coaching methodology is the previous client's IP**, and it is
everywhere — the **Coachable Index** (with its weighted formula), **Kobus Neethling's NBI/Whole
Brain**, **DISC**, the **CIM/CBT/CH** framework, the **SSKB/CSKB** two-tier KB, **PCC** maturity,
their **BRD** cited as spec, and 22 Strapi asset hashes that only resolve on their CDN. That is
~200 references across code *and* the prompt workbook.

**You cannot resell that by renaming it.** Establish what you own before promising a second client
a delivery date.

**The crisis screen — FIXED, with a caveat you must not skip.** It shipped hardcoded to **988
(US)**, *test-locked* (the suite asserted `"988" in reply`), with an **English-only** regex — in a
product that ships a `{language}` variable to every agent and runs multilingual speech-to-text. A
user writing "quiero morir" was screened by a pattern that could not match, and the turn proceeded
to normal coaching.

Now: `CEREBROZEN_CRISIS_LINE` is per-region (default routes to findahelpline.com, which answers
internationally), and `app/graph/crisis.py` screens ~20 languages and replies in the user's
language, with no LLM call.

> **The lexicon and its 7 translated replies were written without native-speaker review.** That is
> not good enough to ship to a market — an inaccurate list is worse than an absent one, because it
> looks like coverage. Have each language reviewed before you enable it and supply the corrections
> via `CEREBROZEN_CRISIS_TERMS_FILE` / `CEREBROZEN_CRISIS_MESSAGES_FILE`: no code change, no release,
> no waiting on us. Detection spans ~20 languages but the reply is written in 7, so a language
> outside that set is detected and answered in English — it logs
> `crisis.reply_language_unavailable` naming the language, which is your commission-a-translation
> signal.

---

## 6. Recommended order

1. **Revoke the key. Strip the QA names and prod ObjectIds.** Nothing else matters until this is done.
2. **Keep the `{Time}` fix** — it pays for itself on the *current* OpenAI product immediately (your
   prompt caching is buying ~nothing without it).
3. **Shrink the prompts.** Volatile blocks to the end; cut the 9,977-token environment prompt.
   Target: worst case under ~12K tokens. **This is the gate on offline** — CH is 19.4 s today.
4. **Then ship the Ollama provider** (written — `CEREBROZEN_LLM_PROVIDER=ollama`). Pin
   `num_ctx=32768`; force routing fields via `_ROUTING_REQUIRED`.
5. **Build the eval set** before trusting local routing decisions in production.
6. **Postgres**: JSONB stores + `langgraph-checkpoint-postgres` + pgvector. ~30 functions.
7. **Settle the IP question**, and check gemma4's licence for commercial resale.
8. **Extract branding + methodology into config** so client #3 is a config change, not a fork.
