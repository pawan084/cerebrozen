# Evaluation harness

```bash
python -m scripts.eval                       # current provider
python -m scripts.eval --provider ollama     # the offline model
python -m scripts.eval --compare             # openai vs ollama, side by side
python -m scripts.eval --repeat 3            # LLMs are stochastic — one sample is not a result
python -m scripts.eval --min-score 0.75      # non-zero exit → gate a merge
```

It runs the **real** prompts and the real parser: composed by `guardrails.build_system_prompt`,
output parsed by `tools.parse_control`. A pass means what ships works, not a mock of it.

> **The score is the RAW agent, not the served pipeline — read it that way.** The harness calls the
> agent directly, so it deliberately does *not* go through the graph, which means **contract repair
> does not run here**. A `path=None` failure in this table is recovered in production (the node
> re-prompts and adopts the retry, 6/6). A *wrong* path — `path=CIM want=CH` — is not recoverable by
> anything, because the model confidently emitted a decision; that one is a prompt bug and it is the
> real signal in this table. Do not quote this number as user-visible routing accuracy: it
> understates the pipeline on the omission cases and is exactly right on the wrong-path ones.

## What it tests, and what it deliberately does not

This is **not** a coaching-quality benchmark. It tests the **contract**: does the agent emit the
structured fields the deterministic graph routes on? A warm, insightful reply that omits
`coaching_path` still sends the session down the wrong path — silently, with no error.

Three families: **routing** (`coaching_path` correct), **non-empty reply** (an empty reply is a
dead turn — this has happened in production), and **no placeholder leak** (a literal `{userName}`
reaching the user).

---

## Re-measured 2026-07-17 — and the model, not the prompt, is the story

> **The 79–81% below is STALE.** Re-run with 3 samples on 2026-07-17: the Catalog's own
> configuration scores **100% (16/16)**. Whatever caused the routing failures documented
> below has since been fixed or has moved with the model. Do not quote 79–81% as current.
>
> The finding that replaces it: **the shipping config is not the configured one, and it is
> 5–8× slower.** `docker-compose.prod.yml` sets
> `CEREBROZEN_MODEL_OVERRIDE: ${CEREBROZEN_MODEL_OVERRIDE:-gpt-5-mini}`, which forces ONE
> model on every agent and ignores the Catalog — the thing `responses_client.model_for`
> calls "the sole source of truth". Same prompts, same cases, only the model differs:
>
> | config | model | score | latency/case |
> |---|---|---|---|
> | Catalog, as authored | `gpt-5.4` (9 agents) | **100%** (16/16) | **2.4–4.3s** |
> | `docker-compose.prod.yml` | `gpt-5-mini` (all) | 98% (15/16) | **9.8–29.7s** |
>
> **FIXED 2026-07-17: the override default is gone; production honours the Catalog.**
> The decision looked like latency-vs-money and turned out to be neither — measuring the
> token mix showed the forced model was worse on BOTH axes:
>
> | | gpt-5.4 (Catalog) | gpt-5-mini (was forced) |
> |---|---|---|
> | latency/turn | **3.8s** | 13.7s |
> | reasoning tokens/turn | **0** | **1,408** |
> | visible answer | 229 tok | 286 tok |
> | cost, cached turn | **$0.0048** | $0.0080 |
>
> "mini" is not cheaper here. It spends ~1,408 reasoning tokens on the same question for a
> near-identical answer, and those bill at output rates ($4.50/M) — which its cheaper input
> ($0.75/M vs $2.50/M) cannot pay for once the prompt caches at 90% off. Forcing it bought
> 3.6× the latency and 1.7× the cost. (Mini prices are the published mini-tier rates; the
> Catalog id is `gpt-5-mini`, the pricing page lists `gpt-5.4-mini` — close enough for the
> direction, not exact.)
>
> Cold turn 1 is the one case mini wins ($0.0116 vs $0.0168), because the full prompt is
> uncached. It does not survive turn 2.
>
> **The override's premise is false.** Its docstring says it exists "when the workbook
> lists placeholder ids like gpt-5.4". `gpt-5.4` is a real model — asked the API directly:
> HTTP 200. It was never a placeholder, so the default that flattens every agent onto
> `gpt-5-mini` is guarding against a problem that does not exist.
>
> Corollary for the prompt-budget work (`PROMPTS_SPEC.md` priority 2): the ≤8K target is
> justified by "latency, money, and offline viability". **All three are now measured and none
> of them holds** — see PROMPTS_SPEC.md §"The budget, measured". The last one to fall was
> offline: I asserted here that "a local model cannot hold CH's 16.5K-token prompt at all".
> That was wrong, and it was an assumption, not a measurement. gemma4's context is **131,072
> tokens** — the prompt fits eight times over — and it prefills in **2.1s at 7,756 tok/s**.

## The baseline as first measured (stale — see above)

**OpenAI — the stack that ships today — scores 79–81%.**

```
score 79%   (9/16 cases fully passing, 3 samples each)

path-ch-1   FLAKY 2/3      path-cim-1   FLAKY 2/3
path-ch-2   FLAKY 2/3      path-cim-2   FLAKY 1/3
path-ch-3   FAIL  0/3   ← consistently misrouted
path-ch-4   PASS  3/3      path-cim-3   PASS  3/3
```

Ten identical calls, an unambiguously CH goal ("develop strategic influence as a capability over
the next few months"), production stack:

```
CH   9/10
CIM  1/10   ← this user gets the wrong methodology for their entire session
```

### The root cause is the PROMPT, not sampling — and the harness proved it by refuting me

My first hypothesis was that the flaky routing was a temperature problem. I set the routing agent
to `temperature=0` and re-measured. **It made CH routing worse — 1/6, down from 3/6.**

That is the useful result. At temperature 0 the model's *true* answer for *"build my competency
in leading through influence, over time"* is **CIM, 4/6**. Randomness had been accidentally
landing on CH some of the time and **masking a prompt bug**.

So:

**1. `challenge_context`'s path-deciphering logic is wrong.** A competency-built-over-time goal is
textbook CH, and the prompt scores it CIM. Pinning the temperature would only make the wrong
answer *consistent*. This is a **prompt-team fix**; the temperature knob is left in as opt-in
(`CEREBROZEN_TEMP_CHALLENGE=0`) and **defaults off**, because shipping it would make CH
systematically unreachable for that phrasing.

**2. The envelope is requested, not enforced.** The OpenAI client uses `json_object` (free-form
JSON), not `json_schema` with `required`. So the model is *permitted* to omit `coaching_path`
entirely — which it does ~1 in 6. **The offline Ollama provider does not have this failure mode**,
because the grammar makes the field impossible to omit (`_ROUTING_REQUIRED`).

The obvious inference — "so do the same on OpenAI" — is **wrong**, and the next section is
entirely about why. It is fixed instead by *repairing* the omission in-turn, not by preventing it.

Both are the failure the `challenge_no_coaching_path` contract monitor was built to catch — now
*measured* rather than suspected.

---

## This recalibrates the offline question

I had been holding the local model to "must be reliable". **The correct bar is: no worse than the
incumbent** — and the incumbent is ~80%.

That is a very different conversation. The offline model already showed **5/5 correct routing on
clear cases when the field was forced by the grammar** (`_ROUTING_REQUIRED` in the Ollama
provider). Grammar-forcing gives the local model something OpenAI does *not* have here: it
**cannot** omit the field.

> **The number, finally (2026-07-17).** The box came back. `--provider ollama` against
> `gemma4:latest` (8B), `num_ctx=32768`, the real full-size prompts:
>
> ```
> score 100%   (16/16 cases fully passing, 1 sample)   1.8-9.8s per case
> ```
>
> The local 8B model matches the cloud on the contract eval — and beats the config
> production shipped until today (gpt-5-mini forced: 96-98%, 9.8-29.7s).
>
> Caveats, so this number is not over-read: 1 sample not 3; 16 cases is a thin net; it tests
> the CONTRACT, never coaching quality; and the host is a remote GPU box, so a true
> air-gapped deployment on weaker on-prem hardware would prefill slower (the context math
> holds regardless).

---

## The `json_schema` recommendation was wrong. Don't do it.

An earlier version of this document said the highest-impact fix was to enforce the envelope with
strict `json_schema` + `required` instead of `json_object`, so the model would be *unable* to omit
`coaching_path`. That advice is **withdrawn**, and it is worth explaining why, because it sounds
obviously correct and it would quietly break coaching.

The envelope contains `context_update` — an **open-ended** object. The agents write whatever the
turn produced into it: `session_goal`, `presenting_issue_summary`, `coaching_path`, and any of the
~30 variables in the capture registry. OpenAI's strict mode does not permit that. Asked directly:

```
400 invalid_json_schema
  In context=('properties', 'context_update'),
  'additionalProperties' is required to be supplied and to be false.
```

Strict mode requires every object to enumerate its keys and forbid the rest. So making the envelope
strict means enumerating every key an agent may ever write into `context_update` — and because the
decoder is *grammar-constrained*, any key we failed to enumerate becomes a key the model **cannot
emit**. The failure mode is not an error. It is coaching context silently disappearing, on every
turn, across every stage. We would have traded a visible 1-in-6 routing miss for an invisible
continuous data loss.

**The offline provider gets away with it because it does not do this.** `_ROUTING_REQUIRED` in the
Ollama provider constrains only the routing field on only the routing stage; it does not attempt to
schema-lock the whole envelope.

### What is in place instead, and why it is sufficient

**Contract repair** (`_CONTRACT_REPAIR` in `app/graph/nodes.py`). When `challenge_context` hands off
without a `coaching_path`, the handoff is not honoured: the agent is re-prompted once, naming the
missing field and its options, and the retry is adopted. Measured recovery: **6/6**. Eval score with
it: **94%**, up from the 79–81% baseline.

Strict schema would have saved the one extra LLM call that repair costs on ~1 handoff in 6. It would
not have scored better — repair already recovers that case — and it would have risked the context
dropping described above. That is a bad trade, and the measurement is the only reason we know.

## Next, in order of expected impact

1. **Fix `challenge_context`'s path logic** so a competency-over-time goal scores CH. Measured: it
   currently prefers CIM 4/6 for exactly that phrasing. This is the entire remaining gap — a
   prompt-team fix, and the last thing standing between 94% and ~100% on these cases.
2. **Wire `--min-score` into CI** so a prompt edit that degrades routing cannot merge.
- **Add a case for every routing bug that escapes.** That is what makes this a regression gate
  rather than a vanity metric.
- The harness currently uses `generate()` (non-streamed). Streaming shares the same parser, so
  the contract is identical, but a streamed run would also exercise `UserTextStreamer`.
