# Air-gapped deployment

The same codebase that runs against a frontier cloud model runs entirely inside your
perimeter — Postgres, local vector search, a local model — with no internet at all. This is
the sovereignty wedge, and unlike a contractual "we don't retain your data," it is a
property you can **verify**, on your own hardware, before you trust it.

## The recipe

Everything is in [`.env.offline`](../.env.offline). It selects the offline provider and
blanks every cloud dependency:

| Concern | Air-gapped choice |
|---|---|
| LLM | Ollama (`CEREBROZEN_LLM_PROVIDER=ollama`, `OLLAMA_HOST=http://localhost:11434`) |
| Embeddings | Ollama `nomic-embed-text` (`CEREBROZEN_EMBED_PROVIDER=ollama`) |
| Vector search | Postgres + pgvector (no S3, no LanceDB) |
| Prompts | Local workbook (no S3) |
| Mongo / Redis / S3 / OpenAI | **all blank** — the stack boots without them |

```bash
cp .env.offline .env          # then pull your model:  ollama pull <model>
docker compose up -d db       # Postgres + pgvector
uvicorn app.main:app          # the engine, talking only to localhost
```

## Verify it — don't take our word for it

Two artifacts make the two hardest claims checkable, offline, with no keys:

**1. Nothing leaves the network.**
```bash
python -m scripts.egress_audit         # runs a real coaching turn under a socket guard
```
It reports every connection attempted to anything other than loopback. A correct deployment
prints `egress_count: 0` / `verdict: SEALED` and exits 0. A regression that introduces a
phone-home — telemetry, an un-gated API call — fails it. The same guard runs in CI
(`tests/test_zero_egress.py`), so a leak cannot merge.

**2. The safety floor is exactly what we say.**
```bash
python -m scripts.redteam_report       # scores the crisis screen on the published scenarios
```
Offline it reprints the lexicon floor (1/22 implicit, 16/16 explicit, 0/7 false positives) —
the same scenarios CI gates on. With a local model configured, `--full` adds the two-layer
screen (classifier), reported as measured-now.

## The one thing that is NOT yet measured

**Coaching quality on the local model.** The deploy path runs and is sealed; what a specific
local model (Gemma, Llama, Qwen…) produces as a *coach* is not yet benchmarked, and it will
be worse than a frontier model — the question is how much, for your model and your prompts.
Measure it before you rely on it:

```bash
python -m scripts.eval --provider ollama    # coaching-quality score on your local model
```

We do not publish a number here because it depends on your model choice, and a borrowed
number would be exactly the kind of unverifiable claim the rest of this page exists to
avoid. See [FEATURE_MATRIX.md](FEATURE_MATRIX.md) §5 — the deploy path is ✅; the local-model
quality bar is the honest ⚠️.
