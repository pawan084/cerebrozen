"""Typed shared state for the coaching graph.

State is the single source of truth routing reads — edges are code predicates
over these fields, never an LLM. The MongoDB/Memory checkpointer persists this
per `session_id`, so a continuing turn resumes the right stage and runs exactly
one user-facing call.
"""

from __future__ import annotations

import logging
import operator
from typing import Annotated, Any, Dict, List, Optional, TypedDict

logger = logging.getLogger("cerebrozen.graph")

# Ordered coaching stages. Phase 1 ships the CIM slice; challenge_context stubs
# the path to CIM. CBT/CH/simulation/close-builders arrive in later phases.
STAGE_CHECKIN = "repeat_user_checkin_agent"  # Phase 4 pre-session entry (gated)
STAGE_INTAKE = "coaching_intake_agent"
STAGE_CHALLENGE = "challenge_context_agent"
STAGE_CORE = "core_coaching_agent"  # the CIM coaching node + the coaching "slot"
# Phase 3 coaching paths (sheet names = the integration contract). The state's
# `stage` stays STAGE_CORE through the coaching slot; which node runs is picked
# from `coaching_path` (see build_graph._coaching_route), so these constants name
# the workbook sheet + reasoning/log stage each path-specific node uses.
STAGE_CH = "CH_coaching_agent"
# Phase 6 simulation stages (sheet names). Reached only for CIM/CBT when the
# chosen simulation agent is enabled in the Catalog tab.
STAGE_ROLEPLAY = "role_play_agent"
STAGE_SJT = "SJT_simulation_agent"
# Post-coaching simulation gate (sheet name). Runs after core/CH coaching: reads the
# session context, decides skip / role_play / SJT, OFFERS simulation to the user
# (turn 1) and routes on their yes/no (turn 2). role_play/SJT → the sim nodes; skip →
# pattern. Replaces the deterministic `specific_person_identified` gate WHEN enabled
# in the Catalog; disabled/unauthored → the old code gate is used (reversible).
STAGE_SIMULATION_DECISION = "simulation_decision_agent"
# Learning-aid support stage (sheet name). Reached after the coaching/simulation
# slot for every coaching path (CIM/CBT/CH) when enabled in the Catalog tab;
# surfaces one retrieved aid + debrief, then advances to the closing layer.
STAGE_LEARNING_AID = "learning_aid_agent"
# Closing layer (sheet name) — the last substantive agent and the SOLE legitimate
# path to the terminal `close`. On completion it fires the prompts'
# EndOfConversation signal; the graph then advances to `close`. Every coaching /
# simulation / learning-aid flow funnels through here first.
STAGE_FEEDBACK = "feedback_mood_capture_agent"
STAGE_CLOSE = "close"
# Phase 5 dynamic actions/insights gate — intercepts after coaching and learning_aid,
# delivers response_to_user + action cards, then hands off to simulation/feedback.
STAGE_DYNAMIC_ACTIONS = "dynamic_actions_insights_agent"
# Post-simulation reflect beat (sheet name = the pattern_agent prompt). Reached ONLY
# after a simulation agent (role_play / SJT) hands off: pattern_agent's in_session
# invocation surfaces ONE pattern mirror as its OWN user-facing turn, then forwards to
# the dynamic_actions gate (spec: reflect beat sits between simulation and learning_aid).
# CIM/CBT only — CH has no simulation. Runtime-gated by config.ENABLE_BUILDERS in the node.
STAGE_PATTERN = "pattern_agent"
# Mandatory pre-feedback "Final action check" (Edge case: all actions skipped). A
# conditional node placed right before the closing feedback layer: if the user SAVED
# >=1 action this session (UI Save → status "saved") it passes straight through; if 0
# were saved it nudges and re-surfaces the session's already-generated action cards
# (reuse, no regeneration), blocking until one is saved. Every road to feedback is
# routed through it (build_graph._dispatch_stage / _after_stage).
STAGE_FINAL_ACTION_CHECK = "final_action_check"
# Standalone per-action check-in (sheet name). NOT part of the main session flow — it is
# an independent entry point: the user taps "Action Check-In" on one action card and the
# agent runs a self-contained 15-step reflection on that single action, then closes. No
# coaching edge routes to it; it's seeded at entry (engine) with `checkin_action` and
# advances straight to `close` on handoff (bypasses feedback / final_action_check).
STAGE_ACTION_CHECKIN = "action_checkin_agent"

STAGE_ORDER = [STAGE_INTAKE, STAGE_CHALLENGE, STAGE_CORE, STAGE_CLOSE]

# Every coaching node (any path) advances to `close` on handoff, not back to core.
COACHING_STAGES = {STAGE_CORE, STAGE_CH}
# Simulation nodes also advance to `close` on handoff.
SIMULATION_STAGES = {STAGE_ROLEPLAY, STAGE_SJT}
# The learning-aid support node also advances to the closing layer on handoff.
SUPPORT_STAGES = {STAGE_LEARNING_AID}

# coaching_path → the node key the router dispatches to (build_graph reads this).
# CIM and CBT are unified under core_coaching_agent (CBT is now the core method);
# "CBT" is kept as an alias so any in-flight/legacy coaching_path="CBT" still routes
# to the unified core node instead of hitting the fall-back path.
PATH_TO_NODE = {"CIM": "core", "CBT": "core", "CH": "capability"}


def merge_dict(existing: Dict[str, Any], update: Dict[str, Any]) -> Dict[str, Any]:
    """State reducer: shallow-merge dict updates (later turn wins per key). Used by
    `captured_variables` so a node's `variables_set` accumulates across turns
    instead of replacing the whole bag."""
    if not existing:
        return dict(update or {})
    if not update:
        return existing
    return {**existing, **update}


def next_stage(stage: str) -> str:
    """The stage that follows `stage` once the active node hands off.

    The ONLY rule that yields the terminal `close` is feedback handing off: the
    closing feedback/mood-capture agent is the sole legitimate end of a session
    (the deck's "End of chat" agent). Coaching / simulation / learning-aid nodes
    never resolve to `close` here — they advance toward feedback via their node
    closures (see nodes._coaching_next_stage / _closing_next_stage). Keeping that
    invariant in one place means no flow can terminate a session early."""
    if stage == STAGE_FEEDBACK:
        return STAGE_CLOSE  # closing layer done → terminal close (the only path)
    if stage in COACHING_STAGES or stage in SIMULATION_STAGES or stage in SUPPORT_STAGES:
        return STAGE_FEEDBACK  # coaching / simulation / learning-aid → closing layer
    try:
        idx = STAGE_ORDER.index(stage)
    except ValueError:
        # An unrecognised stage means state was corrupted or a stage was added
        # without a successor rule. Recovering into the coaching slot is the safe
        # default (never terminate a session on a routing bug), but it MUST be
        # visible — the silent version of this masked mis-routes as real choices.
        logger.warning("state.next_stage_unknown_fallback_core", extra={"stage": stage})
        return STAGE_CORE
    return STAGE_ORDER[min(idx + 1, len(STAGE_ORDER) - 1)]


class CereBroZenState(TypedDict, total=False):
    # identity / request
    user_id: str
    session_id: str
    bot_name: str
    user_message: str
    is_first_turn: bool
    user_language: str  # from request metadata.user_language; → user_context.language
    # The caller's own local hour (0-23), sent by the CLIENT — the only party that
    # knows it. → user_context.time via guardrails.time_of_day. Absent is normal (an
    # older client, or one that declines): the coach then greets without naming a time
    # of day rather than guessing one.
    local_hour: Optional[int]
    # Per-turn CH phase signal from request metadata — the user_selection value of the
    # phase button the user pressed ("continue_to_phase_2", "continue_to_phase_3",
    # "save_and_exit") or "" when no button was pressed. Injected into user_context as
    # {session_continued} so the CH prompt knows which phase transition to follow.
    session_continued: str
    # CH phase-exit routing. `ch_awaiting_transition` marks that CH is currently showing
    # the Continue / Save & Exit choice, so a following `save_and_exit` is a GENUINE early
    # exit (branch A in capability_coaching_node) and not a "returning after break" resume
    # — the CH prompt reuses the same `save_and_exit` value for both. `ch_early_exit` then
    # tells pattern_node to close via feedback directly, skipping the learning-aid beat
    # (the light Phase-1/2 close). Plain bools; persisted across turns by the checkpointer.
    ch_awaiting_transition: bool
    ch_early_exit: bool
    # Which CH phase-completion Action beats have already fired this session (e.g. ["1","2"]).
    # Recorded when the normal phase_N_complete beat runs; read by the Phase-2 safety-net in
    # capability_coaching_node to detect a model that jumped phase 2 -> 3 without emitting the
    # Phase-2 milestone. No reducer — each write carries the full accumulated list.
    ch_beats_fired: List[str]
    # Channel the user is currently talking through — "voice" | "text". Set from
    # request metadata every turn (voice/agent.py stamps "voice"; the text router
    # defaults to "text"). Pure content signal for prompts (resolves as the
    # {conversation_mode} placeholder) — never used for routing (Article: no LLM
    # call/edge exists solely to route, and mode isn't even an LLM call).
    conversation_mode: str

    # routing (deterministic — all code-set)
    stage: str
    coaching_path: str  # CIM | CBT | CH
    # Simulation routing signal (simulation-routing spec): the coaching agent
    # (core/CBT) infers during Q1–Q2 whether the user's challenge involves a
    # SPECIFIC named/described person to rehearse with. The deterministic
    # simulation edge reads this: True → role_play_agent, False/absent → SJT.
    # Emitted by the coaching prompt in `context_update`; lifted top-level in
    # nodes._run_stage and persisted across turns by the checkpointer.
    specific_person_identified: bool
    # simulation_decision_agent (post-coaching gate). The agent emits `simulation_route`
    # ∈ {role_play_agent, SJT_simulation_agent, skip}; simulation_decision_node maps it to
    # the next stage.
    simulation_route: str
    safety_flag: str  # ok | crisis
    active_node: str

    # data layer
    user_context: Dict[str, Any]

    # Structured `variables_set` a node captured this session (intake's flat vars:
    # userRoleContext, coachingHistory, coachingNeeds, coaching_style_preference,
    # coachability_score, coachabilityDetail, userMotivations). Merged across turns
    # (reducer) and persisted off-path to the agentic store at session close so they
    # survive into the NEXT session — closes the "Table 1 flat intake vars aren't
    # written back" gap. Distinct from user_context (which also carries profile/NBI
    # /DISC and is rebuilt each session from Mongo).
    captured_variables: Annotated[Dict[str, Any], merge_dict]

    # Carry-over arc state for the coaching slot (core/CBT/Capability) — the
    # prompt's own `context_update` block (behavioral_intake_complete,
    # current_question_number, selected_model, behavioral_context, …). Persisted
    # here and re-injected each turn so completion does NOT depend on the full
    # transcript staying inside the history window: even after early turns are
    # truncated, the node still knows where it is in the Q1–Q6 arc and won't
    # re-ask intake or fail to reach agent_complete. Replaced (merged in code)
    # each turn — no reducer.
    coaching_progress: Dict[str, Any]

    # Carry-over arc state for the learning-aid delivery — kept in its OWN channel
    # (NOT coaching_progress) so its delivery-arc `current_step` can never collide
    # with the coaching slot's generic `current_step`. Persisted + re-injected each
    # turn so the node continues the Grasp→…→commit arc instead of re-guessing the
    # step from a history that already looks "done" and collapsing to `commit`
    # (skipping the delivery entirely). Replaced (merged in code) each turn — no reducer.
    learning_aid_progress: Dict[str, Any]

    # rolling conversation history ({role, content}); reducer appends each turn so
    # the active node sees prior Q&A and progresses instead of re-asking.
    history: Annotated[List[Dict[str, str]], operator.add]

    # Exploration questions asked by feedback_mood_capture_agent (step M2).
    # Keyed by "pos_q" and "neg_q"; populated each turn when M2 step is detected,
    # then paired with the user's answer at completion before saving to Mongo.
    feedback_exp_questions: Dict[str, str]

    # Feedback anti-loop ceiling: the last current_step the feedback agent emitted and how
    # many CONSECUTIVE turns it has stayed on it. When it loops on one step past the
    # ceiling, the node forces the closing handoff so the wrap-up can't trap the user.
    feedback_last_step: str
    feedback_step_repeats: int

    # Carry-over arc state for the closing feedback/mood-capture agent — kept in its
    # OWN channel (NOT coaching_progress) so a stale coaching `current_step` can never
    # bleed in. The feedback agent infers its phase/step from history, but at session
    # close the 40-msg window is saturated with closing-layer noise (action cards,
    # pattern mirror, learning aid, final-action-check) — so an earlier step's Q&A
    # (e.g. Phase 1 commitment scale) scrolls out and the agent re-asks it (the live
    # "repeated commitment question" loop). Persisted + re-injected each turn so the
    # arc advances instead of restarting. Replaced (merged in code) each turn — no reducer.
    feedback_progress: Dict[str, Any]

    # Carry-over step-completion fields for the action_checkin arc — its OWN isolated
    # channel (NOT coaching_progress). action_checkin has a FLAT contract (no
    # context_update / current_step) and infers its step from which fields are already
    # populated; the OSCAR reflection / story tail can push earlier turns out of the
    # 40-msg window, so it re-asks a rating/reflection/story already captured. Persisted
    # + re-injected each turn. Replaced (merged in code) each turn — no reducer.
    checkin_progress: Dict[str, Any]
    # dynamic_actions_insights gate state (Phase 5 two-shot node)
    # Set by coaching/learning_aid nodes on handoff so the gate knows where to go next.
    actions_next_stage: str
    # The agent that TRIGGERED the gate (core/CBT/Capability/role_play/SJT/learning_aid),
    # stamped by the triggering node on handoff. The gate reads THIS (not `active_node`)
    # for the action prompt's agent_type + the stored agent_name — because when the gate
    # runs on a later turn, `active_node` has already been reset to "profile_read" (the
    # turn-entry node), which mis-attributed the action. Durable across the turn boundary.
    action_agent_type: str
    # True after the gate's first-invocation LLM call; cleared when it hands off.
    actions_builder_done: bool
    # Shaped action/insight cards produced by the gate's first invocation; read by the
    # engine for the `done` payload. Replaced (not accumulated) each turn — no reducer.
    generated_actions: List[Dict[str, Any]]
    generated_insights: List[Dict[str, Any]]

    # Floor-style completion gate counters (Round-1 #2 role_play, #5 feedback). Per-stage
    # turn count + how many early completions have been deferred, so the gate (in
    # nodes._run_stage) can require a minimum number of substantive turns before honouring
    # `agent_complete`, bounded by a max-deferral safety. merge_dict so each stage's count
    # accumulates across turns without clobbering the others.
    gate_turns: Annotated[Dict[str, int], merge_dict]
    gate_reprompts: Annotated[Dict[str, int], merge_dict]

    # CH coaching — phase tracking returned to the UI each turn
    # active_phase: the phase/step name emitted by CH (e.g. "phase_1", "phase_1_complete")
    # phase_buttons: list of {label, user_selection} dicts when awaiting_phase_transition
    active_phase: str
    phase_buttons: List[Dict[str, Any]]

    # True once the pre-feedback "Final action check" has passed (>=1 action saved this
    # session, or nothing was ever generated to pick from) — so the closing feedback layer
    # is entered exactly once and the check never re-fires on the multi-turn feedback stage.
    final_action_check_done: bool

    # Standalone action_checkin_agent inputs, seeded at entry from the tapped card:
    # {"action_item": full_text, "action_outcome": expected_outcome}. Persisted across the
    # sticky 15-step arc; the node merges these into user_context each turn so the prompt's
    # {action_item}/{action_outcome} resolve. Empty for every non-check-in session.
    checkin_action: Dict[str, Any]

    # per-turn outputs
    handoff_ready: bool
    reply_text: str
    prompt_tokens: int
    completion_tokens: int
    cost_usd: float
