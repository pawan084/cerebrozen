# Coaching Flow — Agents, Routing, and the Prompt Workbook

Last updated: 2026-07-14. Derived from `ref/Agent` (`docs/AGENT_FLOW.md`,
`docs/PROMPT_REGISTRY.md`, `app/graph/`, and the workbook itself). This is
CereBroZen's spec for the engine we adopt in Phase 1 — what we keep, what we
drop, and the traps the reference already paid for.

**Prompt content note.** All workbook prompts are extracted verbatim to
[`docs/prompts/`](prompts/README.md) and are the working base for our
content (owner decision, 2026-07-14 — provenance concern raised and
accepted). Adaptation rules live in `PROMPTS_SPEC.md`. The reference's own
Evidence framing still applies: content quality is the long pole, and a
qualified coach's review remains a release condition.

## The graph: 18 nodes, 15 LLM agents

Only nodes marked LLM call a model. Everything else is code.

| Node | Workbook sheet | LLM | Role |
|---|---|---|---|
| `safety` | — | | Rule-based crisis screen, runs first, ~1ms, no model. Crisis → `safe_response`. |
| `safe_response` | — | | Scripted crisis reply in the user's language, zero tokens, ends turn. |
| `profile_read` | — | | Loads user context, sets entry stage. |
| `intake` | `coaching_intake_agent` | ✔ | One-time: role, prior coaching, style preference, Coachable Index (8 questions). |
| `checkin` | `repeat_user_checkin_agent` | ✔ | Repeat users when a 7-day check-in is due. Catalog-gated. |
| `challenge` | `challenge_context_agent` | ✔ | Session expectations; **emits `coaching_path`** — the single model-driven routing decision. |
| `core` | `core_coaching_agent` | ✔ | The CIM + CBT coaching slot (unified). |
| `capability` | `CH_coaching_agent` | ✔ | Coaching Horizons: Goals → Commitments → Development phases. |
| `dynamic_actions` | `dynamic_actions_insights_agent` | ✔ | Action cards + insights. Two-shot: first visit shows cards, next visit hands off silently. |
| `simulation_decision` | `simulation_decision_agent` | ✔ | Offers rehearsal, routes on the answer. Gated. |
| `role_play` | `role_play_agent` | ✔ | Persona rehearsal against a profiled counterpart. Gated. |
| `sjt` | `SJT_simulation_agent` | ✔ | Situational-judgment scenario. Gated. |
| `pattern` | `pattern_agent` | ✔ | Post-simulation reflection: one pattern mirror. |
| `learning_aid` | `learning_aid_agent` | ✔ | One retrieved micro-learning item + debrief. Gated. |
| `final_action_check` | — | | **The commit gate**: zero saved actions → re-surface cards and block close. |
| `feedback` | `feedback_mood_capture_agent` | ✔ | Closing layer: mood + feedback. **Always-on** (sole path to terminal close). |
| `session_complete` | — | | Terminal reply. |
| `action_checkin` | `action_checkin_agent` | ✔ | Standalone: user taps an action card → focused reflection on that action. Not in the main arc. |

Supporting LLM agents outside the arc: `user_context_builder_agent`
(off-path builder maintaining the 10-dimension user context model) and
`environment` (the always-on guardrail wrapper composed into every prompt).

**Workbook sheets we do NOT adopt** (legacy in the reference): `orchestrator`
(pre-LangGraph router — routing is code now), `placeholder_replacement_agent`
and `user_profile_retrieval_agent` (replaced by the code resolver and
`profile_read`). Our fork starts clean without them.

## Routing rules (all code, no routing LLM)

- **One stage→node table** (`STAGE_NODE`) read by both the turn-entry
  dispatch and the in-turn chain — never two copies (they drifted once in
  the reference's history; that's why the rule exists).
- Dynamic resolution: coaching slot → `core` (CIM/CBT) or `capability` (CH)
  by `coaching_path`, with a **logged** CIM fallback, never silent; disabled
  check-in advances to `challenge` (never re-runs intake); a disabled
  learning aid resolves the closing layer through the same rules so it can
  never smuggle a session past the commit gate; `feedback` is entered only
  after the action check passes (or a CH early exit).
- **One visible reply per turn**: a stage that produced text ends the turn;
  a text-free control handoff chains to the next node in the same turn.
  Bounded extra model calls exist (chaining, one empty-reply retry, ≤2
  completion-floor re-prompts) — deliberate, don't "fix" them.

## Safety nets (each encodes a real production incident)

| Net | Guards against |
|---|---|
| Crisis screen | A coaching reply to a self-harm disclosure; multilingual (~20 languages screened; replies only in clinically-reviewed languages). |
| Completion floor | An agent ending its arc too early (bounded re-prompts). |
| Completion ceiling | The closing arc looping one step forever (commitment question re-asked 7× live). |
| Stuck-stage watchdog | A session pinned on one stage (78 consecutive turns in `challenge_context`, live) — per-stage turn caps force the handoff. |
| Output-contract monitors | Agent drift: prompts stop emitting a routed field → fallback path taken invisibly. Advisory counters, alert on sustained non-zero. |

Monitored contracts we inherit: `challenge_no_coaching_path`,
`ch_no_phase_milestone`, `learning_aid_commit_without_delivery`,
`dynamic_actions_no_cards`.

## SSE event vocabulary (a cross-stack contract)

`status` (node start, human label) · `node` start/end (end carries the real
routing decision, tokens, cost — emitted from the node's own state delta) ·
`token` (streamed chunk) · `done` (full payload). The Android Coach tab and
Flow Studio both consume this; changes follow the ENGINEERING.md protocol.

## Prompt workbook (mechanism we adopt wholesale)

- One sheet per agent, prompt in **B7** spilling to B8/B9…; `Catalog` tab
  (`agent_name | role | enabled | model | sheet_name | description`) is the
  single source of agent enable/disable and per-agent model. `extraction`
  (RAG queries) and `dynamic_variables_persistent` ride in the same file.
- **Content-hash version** = what's live (`/health`) and the LLM
  prompt-cache key (reload busts cache automatically; enable prompt cache
  outside dev).
- **Validation** on load and save: enabled-but-empty blocks a save; unknown
  placeholders (would be blanked), orphaned continuation rows (silent
  truncation), oversize (>24K chars), missing Catalog rows all reported.
- Always-on and non-editable-by-content rules: `environment` and
  `feedback_mood_capture_agent` cannot be disabled; crisis text lives in
  code, not the workbook.
- Adding an agent = workbook sheet + Catalog row + 3 code touches (stage
  constant, `STAGE_NODE` wiring, **reasoning-effort map entry** — a missing
  effort entry is the reference's measured 32s-turn defect class).

## Known traps (inherited knowledge — do not relearn these)

1. **Trailing spaces in sheet names**: the live workbook has
   `action_checkin_agent ` and `feedback_mood_capture_agent ` (with trailing
   spaces). Our fork normalizes all sheet names and adds a validator check
   rejecting leading/trailing whitespace.
2. **Case convention split**: profile/intake placeholders are camelCase
   (`userName`, `coachingHistory`); session scores snake_case
   (`coachability_score`); prompts emit `coachingPath`, code reads
   `coaching_path` — the parser bridges. Document in the workbook README tab.
3. **Blank row mid-prompt silently truncates** (loader now reports orphans —
   keep that check).
4. **A raw `{token}` must never reach the model**: unresolved context tokens
   are blanked (a literal `{token}` once made a field-presence gate treat a
   first-time user as returning); unresolved RAG tokens stay and retry.
5. **Catalog is only the source of truth if nothing overrides it**: the
   reference's global `MODEL_OVERRIDE` env var silently contradicted the
   Catalog. Our fork: the override logs loudly at boot and `/health` shows
   the effective model per agent.

## Phase 1 adoption checklist (feeds docs/TODO.md)

- [ ] Fork workbook: strip legacy sheets, normalize names, replace incumbent
      content with CereBroZen-authored prompts (licensing gate applies).
- [ ] Keep `STAGE_SHEET`/`STAGE_NODE`/state constants as-is initially —
      rename only env/metric prefixes in the Phase 1 sweep, not stage names
      (they are load-bearing across workbook + code + evals).
- [ ] Port the evals golden cases to our content as it's authored (routing
      contract per agent: path cases, reply cases, leak cases).
- [ ] Whitespace-in-sheet-name validator; effective-model in `/health`.
