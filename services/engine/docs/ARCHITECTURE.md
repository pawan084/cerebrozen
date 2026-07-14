# Architecture

## Three layers, strictly separated

1. **Reasoning layer** — one LangGraph `StateGraph` of coaching nodes. The only place an LLM
   persona lives (`app/graph/nodes.py`).
2. **Tool / data layer** — deterministic in-process functions: profile reads, RAG retrieval,
   memory CRUD, crisis screening. No LLM (`app/graph/tools.py`, `app/stores/`, `app/rag/`).
3. **External integration** — MCP servers per client system (later phases).

A node never calls another node. It returns a state delta; LangGraph merges it into
`CereBroZenState`; a conditional edge reads the merged state and picks what runs next.

```
HTTP (/v1/sessions/*)  →  CoachingService  →  CereBroZenEngine  →  compiled StateGraph
                              │                    │                    │
                       strangler gate        SSE callbacks        checkpointer
                    (app/selector.py)     (on_token/on_status)   (thread = session_id)
```

## Per-turn flow

```
user message
   │
   ▼
safety ──crisis──▶ safe_response ──▶ END        (rule-based screen, no LLM)
   │ ok
   ▼
profile_read ──▶ dispatch by state.stage        (Mongo read, no LLM)
   ├─ coaching_intake        (8-question Coachable Index)
   ├─ repeat_user_checkin    (gated; 7-day check-in on prior actions)
   ├─ challenge_context      (decides coaching_path: CIM | CBT | CH)
   ├─ core_coaching (CIM/CBT)  ──or──  CH_coaching (Capability, 3 phases)
   ├─ dynamic_actions        (action cards + insights)
   ├─ simulation_decision ─▶ role_play | SJT_simulation
   ├─ pattern                (post-simulation reflect beat)
   ├─ learning_aid           (gated; Grasp→Practise→Apply→Commit)
   ├─ final_action_check     (mandatory: ≥1 action saved?)
   └─ feedback_mood_capture ─▶ close            (sole path to a terminal close)
```

The one model-driven branch (CIM/CBT/CH) is a single output field of `challenge_context`.
Everything downstream of it is a code predicate. See [AGENT_FLOW.md](AGENT_FLOW.md) for the
exact routing table.

## Invariants

These hold, and tests pin them. Break one and something real breaks.

| Invariant | Why | Enforced by |
|---|---|---|
| **No LLM call exists solely to route.** | Reproducibility and latency: a routing hop is a whole turn the user waits through. | `build_graph.STAGE_NODE` — one table, pure predicates. |
| **One streamed user-facing reply per turn.** | A second reply in one turn is a UI bug. | A stage that produced text ends the turn (`_after_stage`). A *text-free* control handoff may chain to the next node, which is how a stage advances without a dead turn. |
| **Only `feedback_mood_capture` reaches `close`.** | The closing agent captures mood + confirms actions. A session that ends elsewhere loses that data. | `state.next_stage()`; `feedback_mood_capture` is `ALWAYS_ENABLED` so it cannot be gated off. |
| **Every road to `feedback` passes the final action check.** | A session must not close with zero saved actions. | `_node_for_stage()` — including when `learning_aid` is disabled (a hole that used to let sessions skip the gate). |
| **A raw `{placeholder}` never reaches the model or the user.** | Leaked tokens are user-visible, and worse, a literal `{coaching_style_context}` reads as a *non-empty value* to a prompt's field-presence gate — that is how a first-timer got treated as a repeat user. | `app/rag/placeholders.py` blanks unresolved context tokens; `build_system_prompt` sanitizes even on the exception path. |
| **Prompt edits cannot change graph structure, and vice versa.** | The whole point of the workbook seam. | The registry supplies *text and model*; the graph supplies *structure*. |

## Stack

| Layer | Tech |
|---|---|
| Orchestration | LangGraph `StateGraph` + conditional edges + checkpointer |
| LLM | OpenAI **Responses API** via one thin client (`app/llm/responses_client.py`); mock provider when no key |
| State / persistence | Mongo (checkpointer + sessions/conversation/agentic/profile) → SQLite → memory |
| Hot tier | Redis (per-session lock + profile cache; fakeredis fallback) |
| Retrieval | LanceDB (SSKB global + CSKB per-org), S3-backed |
| Serving | FastAPI + SSE |
| Auth | JWT (HS512) |
| Observability | structured JSON logs + OpenTelemetry + Prometheus |

## Degradation

Every dependency has a fallback, and **every fallback is loud** (this is a change: several
used to be silent, which is how a misconfigured Mongo hid behind a working-looking app whose
sessions vanished on restart).

| Dependency | Missing → | Signal |
|---|---|---|
| Mongo | SQLite → in-memory checkpointer | `checkpointer.mongo_unavailable_falling_back` (ERROR); memory saver logs `checkpointer.memory_no_durability` |
| Redis | fakeredis | log |
| OpenAI key | mock provider (deterministic canned replies) | `llm.provider_mock` |
| S3 prompt workbook | bundled local workbook | `prompt_store.s3_fallback` (ERROR) **and** `degraded: true` in `/health` |
| A prompt reload fails | previous prompts keep serving | `prompts.reload_failed_keeping_previous` + `degraded: true` |

## Where things live

```
app/
  graph/        the reasoning layer
    build_graph.py   nodes, edges, the ONE stage→node table, checkpointer selection
    state.py         CereBroZenState: every field routing can read
    nodes.py         node implementations; _run_stage is the shared LLM turn
    contracts.py     output-contract monitors (agent drift detection)
    guardrails.py    composes env prompt + identity + node prompt, resolves placeholders
    crisis.py        the crisis pre-filter: ~20 languages, and the reply, in the user's
                     language. No LLM. The highest-consequence code in the repo.
    tools.py         profile read, control-envelope parsing (no LLM). Re-exports crisis_screen.
    builders.py      off-path background agents (actions, pattern, context model)
    engine.py        drives the graph, streams tokens, fires off-path builders
  llm/
    prompts.py       the prompt registry: load, validate, version
    prompt_store.py  workbook source resolution (codebase | S3) + upload/backup
    responses_client.py  the single LLM client; reasoning-effort + cache policy
    providers/
      ollama.py      the offline provider; grammar-forces the routing field so the model
                     CANNOT omit coaching_path (OpenAI has no equivalent — see EVALS.md)
  rag/            extraction registry, placeholder resolver, LanceDB store
    pgvector_store.py  the no-S3 vector store (HNSW cosine, metadata pre-filter)
  stores/         Mongo/Redis/SQLite persistence
    pg.py          Mongo-compatible JSONB shim — the seam that makes Postgres a drop-in
  ratelimit.py    the limiter on the two endpoints that spend money (turn, start)
  routers/        HTTP surface
```
