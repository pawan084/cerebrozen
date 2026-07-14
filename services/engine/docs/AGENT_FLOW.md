# Agent flow (the graph)

Everything here is in `app/graph/`. Routing is pure Python over typed state — you can read and
test all of it without an LLM (`tests/test_graph.py` does exactly that).

## Nodes

18 nodes. Only the ones marked **LLM** make a model call.

| Node | Stage constant (= workbook sheet) | LLM | What it does |
|---|---|---|---|
| `safety` | — | | Rule-based crisis screen on the message. Crisis → `safe_response`. |
| `safe_response` | — | | Returns the crisis-support reply **in the user's language**, ends the turn. No LLM call. |
| `profile_read` | — | | Loads user context from Mongo; sets the entry stage. |
| `intake` | `coaching_intake_agent` | ✔ | One-time intake: role, prior coaching, style preference, 8-question Coachable Index. |
| `checkin` | `repeat_user_checkin_agent` | ✔ | Repeat users, when a 7-day check-in is due. Gated in the Catalog. |
| `challenge` | `challenge_context_agent` | ✔ | Captures the session's expectations and **decides the coaching path**. |
| `core` | `core_coaching_agent` | ✔ | The CIM **and** CBT coaching slot (unified). |
| `capability` | `CH_coaching_agent` | ✔ | The CH path: 3 phases (Goals → Commitments → Development). |
| `dynamic_actions` | `dynamic_actions_insights_agent` | ✔ | Produces the action cards + insights. Two-shot: first visit replies with cards and ends the turn; next visit hands off with no text. |
| `simulation_decision` | `simulation_decision_agent` | ✔ | Offers simulation and routes on the answer. Gated. |
| `role_play` | `role_play_agent` | ✔ | Persona rehearsal. Gated. |
| `sjt` | `SJT_simulation_agent` | ✔ | Situational-judgment scenario. Gated. |
| `pattern` | `pattern_agent` | ✔ | Post-simulation reflect beat: one pattern mirror. |
| `learning_aid` | `learning_aid_agent` | ✔ | One retrieved micro-learning / curated item + debrief. Gated. |
| `final_action_check` | `final_action_check` | | **Mandatory gate**: if zero actions were saved, re-surface the cards and block. |
| `feedback` | `feedback_mood_capture_agent` | ✔ | Closing layer: mood + feedback capture. Always enabled. |
| `session_complete` | `close` | | Terminal reply. |
| `action_checkin` | `action_checkin_agent` | ✔ | **Standalone**: user taps an action card; 15-step reflection on that one action. Not part of the main flow — seeded at entry, closes directly. |

## Routing

There is **one** stage→node table, `build_graph.STAGE_NODE`, read by both the turn-entry
dispatch edge and the in-turn chain edge. (These used to be two hand-maintained copies, and
they had already drifted — `STAGE_CH` was missing from the chain half.)

Three stages resolve dynamically in `_node_for_stage()`:

| Stage | Resolves to | Rule |
|---|---|---|
| the coaching slot (`core_coaching_agent` / `CH_coaching_agent`) | `core` or `capability` | `coaching_path`: CIM/CBT → `core`, CH → `capability`. No usable path → **logged** CIM fallback, never a silent default. |
| `repeat_user_checkin_agent` | `checkin` or `challenge` | Disabled in the Catalog → advance to `challenge` (never re-run intake). |
| `learning_aid_agent` | `learning_aid` or the closing layer | Disabled → resolve the **feedback stage through the same rules**, so a disabled aid can't smuggle a session past the action check. |
| `feedback_mood_capture_agent` | `feedback` or `final_action_check` | Feedback is entered only after the action check passes (or a CH early exit). |

### The two edges

- **`_dispatch_stage`** (turn entry, after `profile_read`) — resume the checkpointed stage.
  An unknown stage recovers into the coaching slot **and logs**.
- **`_after_stage`** (on every stage node) — chain to the next node **within the same turn**
  *only* when the stage handed off with **no user-facing text** (a pure control envelope).
  Otherwise the turn ends. An unknown stage stops rather than inventing a destination.

That text-free-handoff rule is what lets a stage advance without burning a turn on a blank
reply, while still guaranteeing one visible reply per turn.

## How a turn executes

1. `POST /v1/sessions/{id}/turn` → `CoachingService._run_turn` → strangler gate
   (`app/selector.py`) → per-session Redis lock → `CereBroZenEngine.run_turn_stream`.
2. The engine builds the graph input, resumes the checkpoint (`thread_id = session_id`), and
   streams the graph. Tokens flow out through `on_token` (SSE); status through `on_status`.
3. The active node runs `_run_stage()`: compose the system prompt (env guardrails + identity +
   node prompt, placeholders resolved), one **streamed** LLM call, parse the control envelope
   (`reply_text`, `handoff_ready`, `coaching_path`, `context_update`, `variables_set`).
4. Gates run (below), the state delta is returned, the edge picks what's next.
5. After the stream, off-path builders fire (context model, pattern write, captured variables) —
   they never add latency to the reply.

### Where the "one LLM call per turn" claim is honest, and where it isn't

The invariant that actually holds is **one streamed user-facing reply per turn**. A single turn
can make more than one model call in these bounded cases:

- **In-turn chaining.** A text-free handoff chains into the next node, which may itself call the
  model (e.g. CH completing a phase → `dynamic_actions` generating cards in the same turn).
- **Recovery.** An empty reply triggers one non-streamed retry; the completion floor may
  re-prompt up to `_MAX_FLOOR_REPROMPTS` (2) times.

Both are deliberate. Don't "fix" them by removing the chain — you'd reintroduce dead turns.

## Watching a turn run (the node event stream)

The SSE stream carries three event types. Chat clients read `token` and `done`; the
[Flow Studio](../app/static/flow.html) reads `node` and animates the live path.

| Event | When | Carries |
|---|---|---|
| `status` | a node starts | a human label ("Running: core_coaching_agent") |
| `node` `{phase:"start"}` | a node starts | `key` — the stage/node about to run |
| `node` `{phase:"end"}` | a node finishes | `node`, `stage` (the branch routing took), `handoff_ready`, `coaching_path`, `prompt_tokens`, `completion_tokens`, `cost_usd` |
| `token` | during the LLM call | one streamed chunk of the reply |
| `done` | turn complete | the full result payload |

The `end` event is emitted by the engine from the node's **own state delta** — so it reports
what the node actually wrote, not what the UI hoped it wrote. That is why the flow view can be
trusted as a debugging tool: it is showing you the real routing decision.

Wiring: `nodes._emit_status` (start) → `engine.run_turn_stream` (end) → `service` → the
`on_node` callback in `routers/sessions._sse_response`. All of it is best-effort — a telemetry
failure can never break the turn it is watching.

## Safety nets

These exist because each one has already failed in production at least once.

| Net | Guards against | Where |
|---|---|---|
| **Crisis screen** | A coaching reply to a self-harm disclosure. Rule-based, runs before anything else, no LLM (0 tokens, $0). Screens ~20 languages — it was English-only in a product that runs multilingual STT, so "quiero morir" was matched by nothing and coached at. Replies in the user's language. | `graph/crisis.py` |
| **Completion floor** | An agent completing its arc far too early (role_play skipping the rounds; feedback closing after 4 of 8 steps). Defers the completion and re-prompts — bounded, so a genuinely finished user is never trapped. | `_COMPLETION_FLOOR_TURNS` |
| **Completion ceiling** | The closing arc looping on one step forever (live: the commitment question re-asked 7×). Forces the handoff. | `_COMPLETION_CEILING` |
| **Stuck-stage watchdog** | A prompt that never signals completion pinning the session on one stage (live: **78 consecutive turns** in `challenge_context`). Past a per-stage turn cap, the handoff is forced. Universal — every stage has a cap. | `_STAGE_MAX_TURNS`, metric `cerebrozen_stage_watchdog_total` |
| **Output-contract monitors** | Agent drift: the graph routes on fields the *agents* emit, so when a prompt stops emitting one, routing doesn't break — it takes the fallback path forever, invisibly. | `app/graph/contracts.py`, metric `cerebrozen_agent_contract_violations_total` |

### The contracts being monitored

| Contract | The incident it encodes |
|---|---|
| `challenge_no_coaching_path` | `challenge_context` handed off with no path → every session silently fell back to CIM. |
| `ch_no_phase_milestone` | A 27-turn CH session emitted `awaiting_phase_transition` **zero** times → the per-phase Action beat never fired → "no actions for Phase 1". |
| `learning_aid_commit_without_delivery` | The learning aid jumped straight to `commit` with retrieved content in hand. |
| `dynamic_actions_no_cards` | The actions gate produced nothing → a session that closes with zero actions. |

Violations are **advisory**: logged + counted, never raised. The point is the signal — the
fallbacks still run. Target rate is ~0; alert on any sustained non-zero. See
[OPERATIONS.md](OPERATIONS.md#alerts).

## State

`CereBroZenState` (`app/graph/state.py`) is the message bus. Notable fields:

- **routing**: `stage`, `coaching_path`, `specific_person_identified`, `simulation_route`,
  `final_action_check_done`, `ch_awaiting_transition`, `ch_early_exit`, `ch_beats_fired`
- **carry-over arcs** (each isolated so one stage's `current_step` can't bleed into another):
  `coaching_progress`, `learning_aid_progress`, `feedback_progress`, `checkin_progress`
- **reducers**: `captured_variables` and the `gate_*` counters merge across turns; `history`
  appends; everything else replaces.

### Naming trap (this has bitten before)

Profile/intake fields are **camelCase** and must be spelled that way in prompts — `userName`,
`userRoleContext`, `coachingHistory`. The snake_case spelling does *not* resolve and the token
gets blanked. Session scores are **snake_case** — `coachability_score`,
`coaching_style_preference`. Routing reads `coaching_path` (snake) in code, while prompts emit
`coachingPath` (camel); `parse_control` bridges both.
