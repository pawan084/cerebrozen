"""Graph nodes — plain functions calling the Responses API (no ChatOpenAI).

One user-facing streamed call per turn: routing picks the single active stage
node, it streams its reply, and on handoff_ready the stage advances for the NEXT
turn. The crisis screen and profile_read are non-LLM, so a continuing turn has
exactly one critical-path LLM call.
"""

from __future__ import annotations

import json
import logging
import os
import re
from typing import Any, Dict
import pytz
from datetime import datetime

from langchain_core.runnables import RunnableConfig

from app import config, trace
from app.config import (
    FORCE_HANDOFF_STAGES,
    JSON_OUTPUT_STAGES,
    STUB_CHALLENGE,
    CEREBROZEN_TEST_USERS,
)
from app.graph.contracts import check_handoff_contract, check_turn_contract
from app.graph.contracts import report as contract_report
from app.graph.guardrails import build_system_prompt
from app.graph.runtime import get_client, get_registry
from app.graph.state import (
    COACHING_STAGES,
    STAGE_ACTION_CHECKIN,
    STAGE_CH,
    STAGE_CHALLENGE,
    STAGE_CHECKIN,
    STAGE_CLOSE,
    STAGE_CORE,
    STAGE_DYNAMIC_ACTIONS,
    STAGE_FEEDBACK,
    STAGE_FINAL_ACTION_CHECK,
    STAGE_INTAKE,
    STAGE_LEARNING_AID,
    STAGE_PATTERN,
    STAGE_ROLEPLAY,
    STAGE_SIMULATION_DECISION,
    STAGE_SJT,
    SIMULATION_STAGES,
    CereBroZenState,
    next_stage,
)

# Stages whose multi-step arc progress is persisted + re-injected each turn so it
# survives history truncation. Coaching has always done this; simulation (role_play
# / sjt) is added here (A) — without it role_play loses its current_step/round and
# loops in persona setup forever instead of advancing to handoff. challenge_context
# has the SAME failure mode: without progress persistence it forgets its current_step
# and re-confirms the same summary every turn instead of advancing — so it's added too.
_PROGRESS_STAGES = COACHING_STAGES | SIMULATION_STAGES | {STAGE_CHALLENGE}
from app.graph.tools import (
    _safe_json,
    _truthy,
    crisis_screen,
    extract_progress,
    extract_variables,
    parse_control,
    profile_read,
)
from app.llm.resilience import BreakerOpen
from app.llm.responses_client import UserTextStreamer, model_for, reasoning_effort_for
from app.metrics import record_stage_watchdog
from app.request_context import request_id as _ctx_request_id
from app.safety import boundaries, pacing
from app.stores.agentic import save_mood_capture
from app.stores.variable_capture_registry import VariableCaptureRegistry as _VarRegistry
from app.tracing_otel import get_tracer

# Returned to the user when the circuit breaker is open (OpenAI persistently down)
# so a turn degrades cleanly instead of 500-ing or hanging. Distinct from the
# crisis SAFE_RESPONSE — this is an availability message, not a safety one.
BREAKER_REPLY = (
    "I'm having trouble reaching my coaching engine right now. Please give it a "
    "moment and try again — I'll be right here."
)

# Returned once a session has reached its terminal `close` stage (the closing
# layer fired EndOfConversation). A further message doesn't reopen coaching — it
# gets this, and the user starts fresh with /startconversation_<bot> or /restart.
SESSION_COMPLETE_REPLY = (
    "This session is complete — thank you for the conversation. When you're ready "
    "to begin a new one, just start again and I'll be here."
)

# Misconfig safeguard: the closing feedback/mood-capture agent is the SOLE path to
# a terminal close, so if its prompt is missing we must NOT silently end the
# session. The user gets this holding reply and the session stays open (only
# /endconversation can force-close) until the prompt is authored.
FEEDBACK_UNAVAILABLE_REPLY = (
    "Thanks for sharing all of that. Give me a moment — I'll be right back with you."
)

logger = logging.getLogger("cerebrozen.nodes")

# Cap conversation history fed to a node. Must be generous enough that the intake
# agent (an 8-question Coachable Index) retains its FULL Q&A — otherwise it loses
# early answers, never sets agent_complete, and loops asking questions. A small
# cap (12) caused exactly that. Safe to keep high: CIM latency is reasoning-bound,
# not prompt-bound (measured), so extra history doesn't slow coaching.
MAX_HISTORY_MESSAGES = int(os.environ.get("CEREBROZEN_MAX_HISTORY", "40"))

# User-facing "which agent is running" labels (status shown while the node works).
# The instant non-LLM nodes mostly stay silent so they don't flash on every turn.
# Exception: profile_read announces itself on the FIRST turn only (see below),
# where it does real work (reads Mongo) right before intake.
STATUS_LABELS = {
    "profile_read": "Retrieving user profile…",
    "repeat_user_checkin_agent": "Running: repeat_user_checkin_agent",
    "coaching_intake_agent": "Running: coaching_intake_agent",
    "challenge_context_agent": "Running: challenge_context_agent",
    "core_coaching_agent": "Running: core_coaching_agent (CIM/CBT)",
    "CH_coaching_agent": "Running: CH_coaching_agent",
    "dynamic_actions_insights_agent": "Running: dynamic_actions_insights_agent",
    "role_play_agent": "Running: role_play_agent (simulation)",
    "SJT_simulation_agent": "Running: SJT_simulation_agent (simulation)",
    "pattern_agent": "Running: pattern_agent (reflection)",
    "learning_aid_agent": "Running: learning_aid_agent",
    "feedback_mood_capture_agent": "Running: feedback_mood_capture_agent (closing)",
    "safe_response": "Running: safe_response",
}


def _emit_status(config: RunnableConfig, key: str) -> None:
    """Announce the running node at its START so the UI shows the right agent
    during the (possibly long) call, not the previous node.

    Two channels, both best-effort:
      on_status — a human label ("Running: core_coaching_agent"), for the chat UI.
      on_node   — a STRUCTURED start event, for the flow view. It fires here (at the
                  node's start) because a node's LLM call can take seconds; the
                  matching `end` event is emitted by the engine from the node's state
                  delta, which is what carries tokens/cost.
    """
    cfg = config.get("configurable") or {}
    fn = cfg.get("on_status")
    if fn and key in STATUS_LABELS:
        try:
            fn(STATUS_LABELS[key])
        except Exception:  # noqa: BLE001 — status is best-effort
            pass
    on_node = cfg.get("on_node")
    if on_node:
        try:
            on_node({"phase": "start", "key": key})
        except Exception:  # noqa: BLE001 — telemetry must never break a turn
            pass


def _emit_status_text(config: RunnableConfig, text: str) -> None:
    """Emit an arbitrary one-off status line on the same channel (for step
    completion notices that aren't tied to a node's STATUS_LABELS entry)."""
    fn = (config.get("configurable") or {}).get("on_status")
    if fn:
        try:
            fn(text)
        except Exception:  # noqa: BLE001 — status is best-effort
            pass


# --- non-LLM nodes -----------------------------------------------------------


def safety_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    # No status emit: announcing this flashed "Running: crisis_safety_classifier" on every
    # turn before the real coaching agent — pure noise.
    #
    # `full_screen`, not `crisis_screen`: the lexicon runs first (1ms, free, offline) and the
    # classifier only sees what it let through. The lexicon alone catches ~1 implicit
    # disclosure in 22, and the messages it misses are the ones that read as ordinary
    # sadness — which is exactly the shape a coaching product receives.
    #
    # The TAKEOVER is unchanged and stays deterministic: a "crisis" flag routes to
    # safe_response, which replies from a script with no model in the loop. Detection got
    # smarter. The response did not become negotiable.
    from app.graph.crisis import full_screen

    flag, lang, why = full_screen(state.get("user_message", ""))
    logger.info(
        "node.safety",
        extra={"safety_flag": flag, "detected_by": why, "llm": why.startswith("classifier")},
    )
    if flag == "crisis":
        from app.metrics import record_crisis
        from app.safety.escalation import escalate

        # Count the takeover as a content-free safety event (detection layer + language only)
        # for the release-gate metrics — never a word the person wrote. See app/metrics.py.
        record_crisis(detected_by=why, lang=lang)
        # Fire-and-forget, and it never raises into the turn: a failed notification must not
        # cost the user their reply. See app/safety/escalation.py.
        escalate(
            user_id=state.get("user_id", ""),
            session_id=state.get("session_id", ""),
            detected_by=why,
        )
    return {"safety_flag": flag, "active_node": "crisis_safety_classifier"}


def safe_response_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    _emit_status(config, "safe_response")
    # Answer in the language the person wrote in. Detecting a crisis in Spanish and
    # replying in English is half a fix — it proves the screen fired and still leaves
    # someone reading a language they may not speak, at the worst possible moment.
    #
    # The session's declared language wins when we have one (it is authoritative, and it
    # survives a user who writes one crisis line in English); otherwise we use whichever
    # lexicon actually matched. English if neither is available — a reply the person might
    # not read still beats no reply.
    from app.graph.crisis import safe_response, screen

    lang = (state.get("user_language") or "").strip().lower()[:2]
    if not lang:
        _, lang = screen(state.get("user_message", ""))
    logger.info("node.safe_response", extra={"llm": False, "reply_language": lang})
    return {
        "reply_text": safe_response(lang),
        "handoff_ready": False,
        "active_node": "safe_response",
    }


def profile_read_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    out: Dict[str, Any] = {}
    # Read once, on the first turn only. On continuing turns the context is
    # already in checkpointed state (even when empty), so we don't re-hit Mongo.
    # Announce the retrieval ONLY on that first turn — that's when it does real
    # work, right before intake. On continuing turns it stays silent (no flash).
    if state.get("is_first_turn"):
        _emit_status(config, "profile_read")
        ctx = profile_read(state.get("user_id", ""), state.get("session_id", ""))
        # Request-supplied session language → user_context.language so the env
        # prompt's {language} placeholder resolves (retrieval can't know it).
        lang = state.get("user_language")
        if lang:
            ctx["language"] = lang
        # Trace language propagation from request → user_context on the first turn.
        logger.info(
            "profile_read.language",
            extra={
                "user_id": state.get("user_id", ""),
                "session_id": state.get("session_id", ""),
                "user_language_from_state": lang,
                "language_set_in_context": ctx.get("language"),
                "request_id": _ctx_request_id.get(""),
            },
        )
        # Pin {Time} to SESSION START (the env prompt's documented meaning: "time of
        # day at session start"), captured once and checkpointed. This is also a
        # prompt-CACHING lever: the resolver otherwise fills {Time} with a fresh
        # datetime.now() every call, which sits near the top of the 22k-char env
        # prompt and changes the cached prefix every turn — killing OpenAI prompt
        # caching (cached_tokens=0). A session-stable value makes the big static
        # prefix identical across a session's turns, so warm turns cache it.
        from datetime import datetime, timezone

        user_timezone_str = ctx.get("timezone") or "UTC"
        logger.info("profile_read.timezone", 
                    extra={"user_id": state.get("user_id", ""), 
                           "user_timezone": user_timezone_str,
                            "session_id": state.get("session_id", ""),
                            "request_id": _ctx_request_id.get("")})
        try:
            user_timezone = pytz.timezone(user_timezone_str)
        except pytz.exceptions.UnknownTimeZoneError :
            logger.warning(
                "profile_read.invalid_timezone",
                extra={
                    "user_id": state.get("user_id", ""),
                    "invalid_timezone": user_timezone_str,
                    "fallback": "UTC",
                    "session_id": state.get("session_id", ""),
                    "request_id": _ctx_request_id.get(""),
                },
            )
            user_timezone = pytz.UTC

        current_time_in_user_tz = datetime.now(user_timezone).isoformat()  # moved outside try/except
        ctx["Time"] = current_time_in_user_tz
        ctx["session_continued"] = state.get("session_continued", "")
        # Channel signal ("voice" | "text") → {conversation_mode} placeholder, so a
        # prompt can vary its response style (e.g. short/no-markdown for TTS).
        ctx["conversation_mode"] = state.get("conversation_mode", "text")
        out["user_context"] = ctx
        # Announce the (non-LLM) retrieval step finished, before intake runs, so
        # the user sees one "User Profile Retrieval done" line on the first turn.
        _emit_status_text(config, "User Profile Retrieval done")
    else:
        # On continuing turns user_context is already checkpointed. Propagate any
        # per-turn metadata changes (language, CH phase button press) so the prompt
        # resolver always sees the latest values.
        existing_ctx = dict(state.get("user_context") or {})
        _ctx_changed = False
        lang = state.get("user_language")
        if lang and existing_ctx.get("language") != lang:
            existing_ctx["language"] = lang
            _ctx_changed = True
        session_cont = state.get("session_continued", "")
        if existing_ctx.get("session_continued") != session_cont:
            existing_ctx["session_continued"] = session_cont
            _ctx_changed = True
        # Re-sync every turn — a session can switch channel mid-conversation
        # (voice-flow-architecture.md: one session_id across typing/talking).
        conv_mode = state.get("conversation_mode", "text")
        if existing_ctx.get("conversation_mode") != conv_mode:
            existing_ctx["conversation_mode"] = conv_mode
            _ctx_changed = True
        if _ctx_changed:
            out["user_context"] = existing_ctx
    # Structured log every turn so "voice" vs "text" is greppable in deployed logs
    # (validates the channel actually threaded state -> user_context this turn).
    logger.info(
        "profile_read.conversation_mode",
        extra={
            "user_id": state.get("user_id", ""),
            "session_id": state.get("session_id", ""),
            "conversation_mode_from_state": state.get("conversation_mode", ""),
            "conversation_mode_in_context": (out.get("user_context") or state.get("user_context") or {}).get(
                "conversation_mode", ""
            ),
            "is_first_turn": state.get("is_first_turn", False),
            "request_id": _ctx_request_id.get(""),
        },
    )
    if not state.get("stage"):
        # Entry stage. Pre-session order is profile → intake → check-in → challenge.
        # A FRESH user enters intake. A RETURNING user who already has prior
        # intake/coaching data must NOT re-enter intake — doing so surfaces the
        # "coaching readiness Snapshot / Coachable Index" greeting and then skips the
        # questions (the prompt self-skips), leaving a dangling intro with no Qs
        # (Round-1 bug #2). Instead skip intake entirely and go exactly where a
        # completed intake would hand off: check-in when one is DUE (7-day rule) and
        # enabled, otherwise straight to challenge ("no pre-session touch", by design).
        ctx0 = out.get("user_context") or state.get("user_context") or {}
        repeat_with_data = (
            ctx0.get("userRepeatFresh") == "repeat"
            and bool(ctx0.get("intake_vars") or ctx0.get("previousUserContext"))
        )
        if repeat_with_data:
            out["stage"] = (
                STAGE_CHECKIN
                if (ctx0.get("checkinDue") and get_registry().is_enabled(STAGE_CHECKIN))
                else STAGE_CHALLENGE
            )
            logger.info(
                "node.intake_skipped_repeat_user",
                extra={
                    "user_id": state.get("user_id", ""),
                    "session_id": state.get("session_id", ""),
                    "routed_to": out["stage"],
                    "checkin_due": bool(ctx0.get("checkinDue")),
                },
            )
        else:
            out["stage"] = STAGE_INTAKE
    out["active_node"] = "profile_read"

    # Log the full profile context available to agents this turn (first or continuing).
    # On turn 1 this is the freshly-read Mongo context; on turns 2+ it's the
    # checkpointed state. This single log line per turn is the canonical CloudWatch
    # query point for "what did the agent see in its profile this turn?"
    effective_ctx = out.get("user_context") or state.get("user_context") or {}
    logger.info(
        "profile_read.context_snapshot",
        extra={
            "user_id": state.get("user_id", ""),
            "session_id": state.get("session_id", ""),
            "request_id": _ctx_request_id.get(""),
            "is_first_turn": bool(state.get("is_first_turn")),
            "key_count": len(effective_ctx),
            "keys": sorted(effective_ctx.keys()),
            "context": effective_ctx,
        },
    )

    return out


# --- coaching arc-progress carry-over ----------------------------------------
# Empty-ish values that must NOT overwrite an already-established truthy value
# when merging this turn's progress onto the carried-over progress (so a stage
# that momentarily omits a field — or regresses behavioral_intake_complete back
# to false — can't erase real progress).
_EMPTY = (None, "", 0, False, {}, [])


def _merge_progress(old: Dict[str, Any], new: Dict[str, Any]) -> Dict[str, Any]:
    """Merge this turn's arc progress onto what's carried over. Nested dicts (e.g.
    behavioral_context) merge one level deep; an empty new value never clobbers an
    existing truthy one (progress is monotonic — intake stays complete once set)."""
    out = dict(old or {})
    for k, v in (new or {}).items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = _merge_progress(out[k], v)
        elif v in _EMPTY and out.get(k) not in _EMPTY:
            continue
        else:
            out[k] = v
    return out


# --- registry-driven context_update → persistence bridge --------------------
# CH_coaching_agent captured these two before this bridge existed; they were
# never added as rows to dynamic_variables_persistent (the sheet's tab behind
# variable_capture_registry). Kept as a stage-scoped supplement so switching to
# a registry-driven bridge doesn't silently stop capturing them — once they're
# added to the sheet, this special case can be deleted.
_CH_LEGACY_UNREGISTERED_KEYS = frozenset({"short_term_goal", "competency_source"})


def _snake_to_camel(name: str) -> str:
    head, *rest = name.split("_")
    return head + "".join(w.capitalize() for w in rest)


def _bridge_registry_vars(stage: str, progress: Dict[str, Any]) -> Dict[str, Any]:
    """Lift a stage's context_update fields into a flat dict ready for
    captured_variables (→ Mongo, via the engine's dispatch_dynamic_vars) and
    user_context (so later turns in THIS session already see the value — same
    as intake's variables_set path below).

    Which top-level keys to look for comes ENTIRELY from
    variable_capture_registry (source_agent == stage, keyed by each
    variable_name's segment before its first "."): a stage that emits
    context_update instead of variables_set (challenge_context_agent,
    core_coaching_agent, CH_coaching_agent) needed a hand-maintained whitelist
    here before this existed — coaching_style_context/behavioral_intake_responses
    were captured in-session (coaching_progress) but never reached Mongo, so a
    brand-new session had no way to know they were already answered. Now: add a
    row to the sheet and it's captured, no code change required.

    `progress` should be the CARRIED/merged coaching_progress (not just this
    turn's delta) so a value captured on an earlier turn this session is still
    bridged on a turn where the agent doesn't re-emit it. Falls back to a
    camelCase spelling of each key (some agent contracts emit context_update in
    camelCase while the registry/storage convention is snake_case) but always
    stores under the registry's canonical snake_case name. A stage with no
    registered rows (e.g. intake, which uses variables_set) returns {}."""
    keys = set(_VarRegistry.get().top_level_vars_for_agent(stage))
    if stage == STAGE_CH:
        keys |= _CH_LEGACY_UNREGISTERED_KEYS
    lifted: Dict[str, Any] = {}
    for key in keys:
        val = progress.get(key)
        if val in _EMPTY:
            val = progress.get(_snake_to_camel(key))
        if val not in _EMPTY:
            lifted[key] = val
    return lifted


def _progress_block(progress: Dict[str, Any]) -> str:
    """Render carried-over arc progress as an authoritative system-prompt block so
    the node continues from it even when early turns have scrolled out of the
    history window. Empty fields are dropped to keep it compact."""
    compact = {k: v for k, v in (progress or {}).items() if v not in _EMPTY}
    if not compact:
        return ""
    return (
        "## CARRY-OVER SESSION PROGRESS (authoritative)\n"
        "Earlier turns may be truncated from the transcript below. The JSON here is "
        "the ground-truth state of THIS session's arc — honour it. Continue from "
        "where it says you are; do NOT repeat or restart a step already captured. "
        "For coaching: if `behavioral_intake_complete` is true, skip the behavioral "
        "intake and continue the Q1–Q6 arc from `current_question_number`, keeping "
        "any `selected_model`/`selected_concept`. For simulation: if `persona_summary` "
        "is set, do NOT rebuild the persona — continue from `current_step` / "
        "`role_play_round`.\n"
        "```json\n" + json.dumps(compact, ensure_ascii=False) + "\n```"
    )


def _learning_aid_progress_block(progress: Dict[str, Any]) -> str:
    """Authoritative carry-over for the learning-aid delivery arc — kept in its own
    isolated channel (see `learning_aid_progress` in state / `_run_stage`), NOT the
    shared coaching_progress, so a stale coaching `current_step` can never bleed in.

    Two modes:
      - FIRST turn (no carried progress): seed the arc so a history that already
        looks "done" (coaching covered the topic, actions committed) cannot collapse
        the agent straight to `commit`. It begins the arc and presents the retrieved
        learning item instead of skipping the whole delivery.
      - LATER turns: re-inject where the delivery left off so completion survives
        history truncation and the node advances instead of repeating a step.
    """
    compact = {k: v for k, v in (progress or {}).items() if v not in _EMPTY}
    if not compact:
        return (
            "## LEARNING-AID DELIVERY (authoritative)\n"
            "This is the FIRST turn of the learning-aid delivery (delivery_turn_number = 1). "
            "Begin the delivery arc at its FIRST step: if a learning item was retrieved "
            "(the SSKB micro-learning / CSKB learning-aid content injected above), present "
            "it to the user now. Do NOT jump straight to `commit` and do NOT skip the "
            "delivery arc, even if earlier coaching already touched this topic."
        )
    return (
        "## CARRY-OVER LEARNING-AID PROGRESS (authoritative)\n"
        "Earlier turns may be truncated from the transcript below. The JSON here is the "
        "ground-truth state of THIS learning-aid delivery — honour it. Continue the "
        "delivery arc from `current_step` / `delivery_turn_number`; do NOT jump to "
        "`commit` and do NOT repeat a step you have already delivered.\n"
        "```json\n" + json.dumps(compact, ensure_ascii=False) + "\n```"
    )


def _feedback_progress_block(progress: Dict[str, Any], coaching_path: str) -> str:
    """Authoritative carry-over for the closing feedback/mood-capture arc — kept in
    its OWN isolated channel (`feedback_progress`), NOT the shared coaching_progress,
    so a stale coaching `current_step` can never bleed in.

    The feedback agent infers its phase/step from history; at session close the 40-msg
    window is saturated with closing-layer noise (action cards, pattern mirror, learning
    aid, final-action-check), so an earlier step's Q&A (e.g. the Phase 1 commitment
    scale) scrolls out and the agent re-asks it — the live "repeated commitment
    question" loop (QA-user-1/+32). Re-injecting the accumulated context_update lets
    the arc advance instead of restarting.

    Two parts:
      - CH sessions: assert the Phase 1 gate outcome on EVERY turn (not just the first) —
        live QA (QA-user-2 15:02Z, QA-user-1 15:15Z, 2026-07-09, post-ab2b0e5) showed
        the first-turn-only assertion losing to the prompt's own Phase 1 script on later
        turns, re-opening the commitment loop mid-arc. (CIM's Phase 1 is not gated.)
      - Carried progress (any path): re-inject where the arc left off so completion
        survives truncation.
    """
    ch_line = ""
    if (coaching_path or "").strip().upper() == "CH":
        ch_line = (
            "## FEEDBACK ARC (authoritative — overrides the phase script)\n"
            "`coaching_path == \"CH\"`: Phase 1 (Commitment & Support) already ran "
            "upstream in CH_coaching_agent — it is SKIPPED (`commitment_support."
            "skipped: true`). Do NOT run ANY Phase 1 step, on ANY turn: never ask the "
            "commitment scale, the \"what will keep you committed\" question, or the "
            "accountability-person question. Begin at / continue from Phase 2 (Future "
            "Visualization) onward."
        )
    compact = {k: v for k, v in (progress or {}).items() if v not in _EMPTY}
    if not compact:
        return ch_line
    carry = (
        "## CARRY-OVER FEEDBACK PROGRESS (authoritative)\n"
        "Earlier turns may be truncated from the transcript below. The JSON here is the "
        "ground-truth state of THIS closing arc — honour it. Continue from the FIRST "
        "phase whose `*_complete` flag is not yet true; do NOT re-ask a step already "
        "captured. If `commitment_support.commitment_level` is set, never re-ask the "
        "commitment scale; once `commitment_support_complete` OR `commitment_support."
        "skipped` is true, never restart Phase 1.\n"
        "```json\n" + json.dumps(compact, ensure_ascii=False) + "\n```"
    )
    return f"{ch_line}\n\n{carry}" if ch_line else carry


# action_checkin emits a FLAT output contract (no context_update / current_step), so
# extract_progress doesn't apply — these are the top-level fields that mark step
# completion and must survive history truncation.
_CHECKIN_PROGRESS_FIELDS = (
    "satisfaction_rating", "step9_triggered", "gap_response", "unresolved_concerns",
    "reflection", "story_shared", "suggestions_requested",
)


def _extract_checkin_progress(text: str) -> Dict[str, Any]:
    """Pull action_checkin's step-completion fields out of its (flat) JSON output so
    they can be carried across turns and re-injected — the checkin analog of
    extract_progress. {} when the turn emits no recognisable fields."""
    obj = _safe_json(text)
    if not obj:
        return {}
    return {k: obj[k] for k in _CHECKIN_PROGRESS_FIELDS if k in obj}


def _checkin_progress_block(progress: Dict[str, Any]) -> str:
    """Authoritative carry-over for the action_checkin arc — its OWN isolated channel
    (`checkin_progress`), NOT coaching_progress. action_checkin infers its step from
    which fields are already populated; the OSCAR reflection / story tail can push
    earlier turns out of the 40-msg window, so it loses that a rating / reflection /
    story was already captured and re-asks it. Re-injecting the populated fields lets
    it advance instead of repeating a step. Empty fields are dropped to stay compact."""
    compact = {k: v for k, v in (progress or {}).items() if v not in _EMPTY}
    if not compact:
        return ""
    return (
        "## CARRY-OVER CHECK-IN PROGRESS (authoritative)\n"
        "Earlier turns may be truncated from the transcript below. The JSON here is the "
        "ground-truth state of THIS check-in — honour it. A field that is already "
        "populated means that step is DONE — never re-ask it: if `satisfaction_rating` "
        "is set, do NOT re-ask the rating; if a `reflection.*` field is set, do NOT "
        "re-ask that reflection; if `story_shared` is true, do NOT retell the story; if "
        "`step9_triggered` is true, the gap question was already asked. Continue from the "
        "first step whose field is still null.\n"
        "```json\n" + json.dumps(compact, ensure_ascii=False) + "\n```"
    )


# --- shared LLM stage runner -------------------------------------------------

# Empty-reply recovery (Round-1 bug #1): when an agent returns no user-facing text
# on a non-handoff turn the user would see a blank bubble. We retry once with this
# nudge appended to the user turn; if that still comes back empty we deliver the
# neutral fallback so the bubble is never blank.
_EMPTY_REPLY_RETRY_NUDGE = (
    "\n\n(System: your previous output contained no user-facing text. Output your "
    "next single user-facing question NOW in the JSON's question/response_to_user "
    "field — never return an empty response.)"
)
_EMPTY_REPLY_FALLBACK = "Could you share a little more about that?"


def _salvage_prose_around_json(text: str) -> str:
    """User-facing prose the model wrote AROUND a JSON control envelope that itself
    carried no user-text key (e.g. a greeting followed by the raw handoff JSON —
    live incident 2026-07-06, action_checkin: the greeting was already STREAMED to
    the client in raw mode, then the silent retry recorded a different fallback
    message, so the screen and the saved transcript diverged). Salvaging that prose
    keeps the recorded reply identical to what the user saw and skips a 20–30s
    retry. Returns "" when nothing meaningful surrounds the JSON (pure-envelope
    outputs still go through the normal retry → fallback path)."""
    i, j = text.find("{"), text.rfind("}")
    if i < 0 or j <= i:
        return ""
    around = f"{text[:i]}\n{text[j + 1:]}"
    # Drop code-fence debris so ```json fences don't count as "prose".
    around = around.replace("```json", " ").replace("```", " ").replace("`", " ")
    around = re.sub(r"\s+", " ", around).strip()
    return around if len(around) >= 20 and " " in around else ""

# Floor-style completion gate (Round-1 #2 role_play, #5 feedback). Some agents
# self-certify `agent_complete` after too FEW turns — role_play bailing right after
# `persona_build` (skipping the rounds), feedback completing after 4 of 8 steps. When
# a gated stage tries to complete before this many substantive turns, DEFER it: this
# turn re-prompts the agent to CONTINUE its arc instead of ending. FLOOR not SCRIPT —
# it only blocks an early completion, never forces per-turn pacing — and is bounded by
# `_MAX_FLOOR_REPROMPTS` so a genuinely-finished or declining user is never trapped.
# Crisis + /endconversation don't run as a normal stage turn, so they bypass entirely.
_COMPLETION_FLOOR_TURNS = {
    STAGE_ROLEPLAY: 4,   # persona_build + the actual simulation rounds
    STAGE_FEEDBACK: 6,   # the closing V1→M1→M2→M3→F1… ritual (8 steps; floor of 6)
}
_MAX_FLOOR_REPROMPTS = 2  # after this many deferrals, honour completion (never trap)
_FLOOR_CONTINUE_NUDGE = (
    "\n\n(System: do NOT end or complete this turn. You have not finished your "
    "required step sequence. Continue with the NEXT step you have not yet delivered "
    "and ask its single user-facing question now.)"
)

# Completion CEILING (the counterpart to the floor). The closing feedback arc can LOOP
# on one step — re-asking the same question turn after turn despite valid answers — and,
# because a stage advances only when its own prompt signals completion (no sticky-turn
# cap), the session then never reaches `close` (live: CH feedback stuck on the commitment
# scale; QA-user-1 emotion-loop repeated 7x). We count CONSECUTIVE same-step turns:
# from _FEEDBACK_NUDGE_AT we push the agent to advance; at _COMPLETION_CEILING we force
# the closing handoff so the user is never trapped in the wrap-up. No legitimate step
# repeats this many times in a row (M2 is the longest at 2), so this only fires on a loop.
_COMPLETION_CEILING = {STAGE_FEEDBACK: 4}   # force-complete after N consecutive same-step turns
_FEEDBACK_NUDGE_AT = 2                        # from this many repeats, nudge to advance
_FEEDBACK_ADVANCE_NUDGE = (
    "\n\n(System: the user has ALREADY answered the current step more than once — do NOT "
    "ask it again in any form. Record their answer and advance to the NEXT step now; if "
    "every step is done, complete and hand off.)"
)

# ── Stage-stuck watchdog ─────────────────────────────────────────────────────
# A stage advances ONLY when its own prompt signals completion. A prompt that never
# emits `handoff_ready` therefore pins the session on one stage forever — live: a
# voice session sat in challenge_context for 78 consecutive turns. The floor/ceiling
# gates above are per-stage and step-aware; this is the blunt, universal backstop:
# past _STAGE_MAX_TURNS turns on ANY stage, force the handoff so the session always
# makes forward progress. Set generously — it must never fire on a legitimately long
# arc (the CH coaching arc is the longest at ~30 turns across 3 phases), only on a
# genuinely stuck one.
_STAGE_MAX_TURNS_DEFAULT = 25
_STAGE_MAX_TURNS = {
    STAGE_INTAKE: 20,              # 8 Coachable-Index questions + context
    STAGE_CHALLENGE: 15,           # the 78-turn incident stage — scoring, not coaching
    STAGE_SIMULATION_DECISION: 6,  # sticky 2-turn gate (offer → route)
    STAGE_CH: 45,                  # 3 phases × ~10-15 turns
    STAGE_ACTION_CHECKIN: 25,      # 15-step reflection
}


def _stage_turn_cap(stage: str) -> int:
    return _STAGE_MAX_TURNS.get(stage, _STAGE_MAX_TURNS_DEFAULT)


# Contract violations we REPAIR rather than merely record, mapped to the nudge that asks
# for the missing field. Only violations that silently corrupt ROUTING belong here — a
# repair costs an extra LLM call, so it must buy correctness, not tidiness.
#
# `challenge_no_coaching_path` is the whole reason this exists: measured ~1 turn in 6 on
# the live stack, and the consequence is that the user is coached with the wrong
# methodology for the entire session while nothing errors.
_CONTRACT_REPAIR = {
    "challenge_no_coaching_path": (
        "\n\n(System: your response is missing the REQUIRED routing decision. You must set "
        "`coaching_path` in your context_update to exactly one of: \"CIM\" (a live, present "
        "problem to work through now), \"CBT\" (unhelpful thinking to reframe), or \"CH\" (a "
        "capability/competency the user wants to BUILD over time, across phases). Re-emit "
        "your full JSON response with coaching_path set. Do not leave it empty.)"
    ),
}


# How many times the pre-feedback "Final action check" may nudge a user who has saved no
# action before it relents and lets the session close anyway. The gate is a NON-LLM node,
# so it never passes through _run_stage and inherits neither the completion ceiling nor the
# stage watchdog — without this bound it re-nudges forever and the session can never reach
# the closing layer (found by walking the graph against a live model).
_FINAL_ACTION_CHECK_MAX_NUDGES = 3


def _run_stage(
    stage: str,
    state: CereBroZenState,
    config: RunnableConfig,
    resolve_next_stage=None,
    require_agent_complete: bool = False,
) -> Dict[str, Any]:
    _emit_status(config, stage)  # announce BEFORE the (slow) LLM call
    client = get_client()
    registry = get_registry()
    node_prompt = registry.get(stage)
    coaching_path = state.get("coaching_path")
    user_context = state.get("user_context", {})
    
    
    #Incase of a test user (for performance tests and security tests) we will stub the challenge context agent and return a fixed response
    user_id = state.get("user_id", "")
    logger.info("User Id logged: %s", user_id, extra={"stage": stage, "llm": False})
    if user_id in CEREBROZEN_TEST_USERS:
           logger.info("node.llm_stubbed_test_user", extra={"stage": stage, "user_id": user_id, "llm": False})
           reply = "[Static reply — test user, no LLM call]"
           out = {
               "reply_text": reply,
               "handoff_ready": True,
               "active_node": stage,
               "prompt_tokens": 0,
               "completion_tokens": 0,
               "cost_usd": 0.0,
               "history": [
                   {"role": "user", "content": state.get("user_message", "")},
                   {"role": "assistant", "content": reply},
               ],
           }
           out["stage"] = resolve_next_stage(reply) if resolve_next_stage else next_stage(stage)
           return out

    # Bridge: several agents emit fields in context_update (→ coaching_progress) rather
    # than variables_set (→ user_context). Lift non-empty progress values so the
    # placeholder resolver finds them in downstream agents.
    #   challenge_context_agent: session_goal, presenting_issue_summary, time_context,
    #                            coaching_style_context
    #   core_coaching_agent:     committed_action, committed_by_when,
    #                            behavioral_intake_responses
    #   CH_coaching_agent:       confirmed_competency, user_blueprint, mastery_rubric,
    #                            long_term_goal, short_term_goal, user_career_aspirations,
    #                            user_strengths, user_gaps, user_work_environment,
    #                            current_phase
    _cp_bridge = state.get("coaching_progress") or {}
    if _cp_bridge:
        for _key, _aliases in (
            ("session_goal", ()),
            ("presenting_issue_summary", ()),
            ("coaching_style_context", ()),
            ("committed_action", ()),
            ("committed_by_when", ()),
            ("behavioral_intake_responses", ()),
            # CH-specific carry-over fields (populated from context_update by CH agent).
            # userStrengths/userGaps/userWorkEnvironment are the CH contract's actual
            # camelCase context_update keys — alias them onto the snake_case name so
            # both the {user_gaps}-style and {userStrengths.self_reported}-style
            # placeholders resolve.
            ("confirmed_competency", ()),
            ("competency_source", ()),
            ("user_blueprint", ()),
            ("mastery_rubric", ()),
            ("long_term_goal", ()),
            ("short_term_goal", ()),
            ("user_career_aspirations", ()),
            ("user_strengths", ("userStrengths",)),
            ("user_gaps", ("userGaps",)),
            ("user_work_environment", ("userWorkEnvironment",)),
            ("current_phase", ()),
        ):
            _val = _cp_bridge.get(_key)
            if not _val:
                for _alias in _aliases:
                    _val = _cp_bridge.get(_alias)
                    if _val:
                        break
            if _val:
                user_context[_key] = _val
        _tc = _cp_bridge.get("time_context") or {}
        if _tc.get("available_time"):
            user_context["timeAvailable"] = _tc["available_time"]
        # coaching_style_context.selected_style — the CH prompt uses the dot-notation
        # token {coaching_style_context.selected_style} which the placeholder regex
        # doesn't resolve. Inject a flat underscore alias so either token works.
        _csc = _cp_bridge.get("coaching_style_context")
        if isinstance(_csc, dict) and _csc.get("selected_style"):
            user_context["coaching_style_context_selected_style"] = _csc["selected_style"]
        # currentPhase — CH prompt uses camelCase {currentPhase}; alias the snake_case field
        if _cp_bridge.get("current_phase"):
            user_context["currentPhase"] = _cp_bridge["current_phase"]

    # Live turn signals the RAG placeholder resolver needs to build queries: the
    # current message + a flat conversation string + ids. A coaching prompt that
    # contains a RAG placeholder (e.g. {SSKB_Concept}) triggers retrieval here, in
    # this single pre-stream pass — no message-number trigger, no extra LLM turn.
    _hist = state.get("history") or []
    _progress = state.get("coaching_progress") or {}
    _flat_history = "\n".join(
        f"{m.get('role')}: {m.get('content')}" for m in _hist if m.get("content")
    )
    query_context = {
        "user_message": state.get("user_message", ""),
        # The caller's local hour; guardrails turns it into {time}. None is fine.
        "local_hour": state.get("local_hour"),
        "conversation": _flat_history,
        # Canonical name (matches builders.py's {conversation_history} convention);
        # same value as "conversation" above — this is what Extract1/2/5 now query
        # on (previously dropped silently since nothing forwarded this key).
        "conversation_history": _flat_history,
        "user_id": state.get("user_id", ""),
        "session_id": state.get("session_id", ""),
        # The CH user-stated outcome captured by challenge_context_agent
        # (context_update.session_goal → coaching_progress). Feeds
        # Extract1/2/4/5/8 (query on this name directly).
        "session_goal": _progress.get("session_goal", ""),
        # Skill/competency to develop (CH only) — populated once competency mapping
        # exists; empty in CIM/CBT so it drops from the query.
        "skill_to_develop": _progress.get("skill_to_develop") or _progress.get("competency", ""),
        # Confirmed competency (CH only, set by CH_coaching_agent) — feeds Extract6.
        "confirmed_competency": _progress.get("confirmed_competency", ""),
    }
    system_prompt = build_system_prompt(
        registry.environment, node_prompt, coaching_path, user_context, query_context,
        invoking_agent=stage,  # attribute any RAG retrieval to this agent in the logs
    )
    # Re-inject the carried-over arc progress for the coaching slot so completion
    # survives history truncation (root cause of the CIM "repeats questions, never
    # ends" loop): even when early turns scroll out of the window below, the node
    # still sees behavioral_intake_complete / current_question_number / selected_*
    # and continues the arc instead of restarting it.
    carried_progress = state.get("coaching_progress") or {}
    if stage in _PROGRESS_STAGES and carried_progress:
        block = _progress_block(carried_progress)
        if block:
            system_prompt = f"{system_prompt}\n\n{block}"
    # learning_aid uses its OWN isolated progress channel (not coaching_progress).
    # Always inject: on the first turn this seeds the delivery arc (so the node can't
    # collapse straight to `commit`); on later turns it continues from where the
    # delivery left off. See _learning_aid_progress_block.
    if stage == STAGE_LEARNING_AID:
        la_block = _learning_aid_progress_block(state.get("learning_aid_progress") or {})
        if la_block:
            system_prompt = f"{system_prompt}\n\n{la_block}"
    # Feedback/mood-capture uses its OWN isolated progress channel (not coaching_progress)
    # so its arc position survives the noisy closing-layer history window and it stops
    # re-asking captured steps (the "repeated commitment question" loop).
    if stage == STAGE_FEEDBACK:
        fb_block = _feedback_progress_block(state.get("feedback_progress") or {}, coaching_path)
        if fb_block:
            system_prompt = f"{system_prompt}\n\n{fb_block}"
        # Anti-loop: if the arc has been stuck re-asking one step, push it to advance now.
        if (state.get("feedback_step_repeats") or 0) >= _FEEDBACK_NUDGE_AT:
            system_prompt = f"{system_prompt}\n\n{_FEEDBACK_ADVANCE_NUDGE}"
    # action_checkin — same isolated-channel carry so its rating/reflection/story
    # steps survive the noisy OSCAR-reflection history window and aren't re-asked.
    if stage == STAGE_ACTION_CHECKIN:
        ck_block = _checkin_progress_block(state.get("checkin_progress") or {})
        if ck_block:
            system_prompt = f"{system_prompt}\n\n{ck_block}"
    user_message = state.get("user_message", "")
    # Coach-not-companion (SB243 / NY companion law): a message that treats the coach as a
    # person, a relationship, or a clinician gets a mandatory disclosure appended to THIS
    # turn's prompt. Appended last so it outranks anything above it, and owned by code so a
    # prompt author tuning warmth cannot soften a legal disclosure out of existence.
    from app.metrics import record_boundary, record_pacing

    # Session pacing (#27/#28): offer a break on a long unbroken run, and a support route
    # when someone has said several times they are not coping. Both fire on a crossing and
    # then periodically — see pacing.block_for.
    pacing_block, pacing_kind = pacing.block_for(state.get("history"), user_message)
    if pacing_block:
        record_pacing(kind=pacing_kind)  # kind only — never the message (rule 5)
        system_prompt = f"{system_prompt}\n\n{pacing_block}"
    # The coach-not-companion disclosure goes LAST, always: on a turn that needs both, a
    # pacing instruction appended after it would be the last thing the model reads about
    # how to open its reply, and the mandatory disclosure must hold that position.
    for _kind in boundaries.detect(user_message):
        record_boundary(kind=_kind)  # kind only — never the message (rule 5)
    boundary_block = boundaries.block_for(user_message)
    if boundary_block:
        system_prompt = f"{system_prompt}\n\n{boundary_block}"
    # Per-stage model (Phase 9 TTFT lever): hot-path stages (intake/challenge/
    # checkin) → fast non-reasoning model; CIM core stays on the reasoning model.
    # reasoning_effort_for returns None for non-reasoning models, so no reasoning
    # kwarg is sent to gpt-4o-mini.
    model = model_for(stage, catalog_model=registry.model_for(stage))
    effort = reasoning_effort_for(stage, model)

    # Prior conversation so the node remembers what it already asked/heard and
    # advances. Capped to the most recent turns to bound prompt growth.
    history = (state.get("history") or [])[-MAX_HISTORY_MESSAGES:]

    on_token = (config.get("configurable") or {}).get("on_token")
    streamer = UserTextStreamer(on_token) if on_token else None

    # Trace what goes INTO the agent (gated; off by default). Captures the exact
    # resolved system prompt, the user message, and the history window sent.
    trace.io(
        "agent.input",
        stage=stage,
        model=model,
        reasoning_effort=effort,
        coaching_path=coaching_path,
        user_message=user_message,
        history=history,
        system_prompt=system_prompt,
    )

    try:
        with get_tracer().start_as_current_span(f"llm.{stage}") as _llm_span:
            _llm_span.set_attribute("cerebrozen.stage", stage)
            _llm_span.set_attribute("cerebrozen.session_id", state.get("session_id", ""))
            _llm_span.set_attribute("cerebrozen.coaching_path", coaching_path or "")
            _llm_span.set_attribute("llm.model", model)
            _json_out = ("__all__" in JSON_OUTPUT_STAGES
                         or stage in JSON_OUTPUT_STAGES)
            resp = client.generate_stream(
                system_prompt=system_prompt,
                user_prompt=user_message,
                model=model,
                on_token=streamer.feed if streamer else None,
                reasoning_effort=effort,
                history=history,
                stage=stage,
                session_id=state.get("session_id", ""),
                user_id=state.get("user_id", ""),
                json_output=_json_out,
        )
            _llm_span.set_attribute("llm.total_tokens", resp.total_tokens)
            _llm_span.set_attribute("llm.cached_tokens", resp.cached_tokens)
            _llm_span.set_attribute("llm.cost_usd", resp.cost_usd)
    except BreakerOpen:
        # OpenAI is persistently down (breaker open) — degrade to an availability
        # message instead of 500-ing the turn. No tokens were streamed (the breaker
        # short-circuits before the call), so this is the whole reply. Don't advance
        # the stage; the user can retry the same step.
        logger.warning("node.breaker_open", extra={"stage": stage, "llm": False})
        return {
            "reply_text": BREAKER_REPLY,
            "handoff_ready": False,
            "active_node": stage,
        }

    reply, handoff, path = parse_control(resp.text)

    # Verify the model replied in the correct language for this stage/turn.
    logger.info(
        "node.llm_reply",
        extra={
            "user_id": state.get("user_id", ""),
            "session_id": state.get("session_id", ""),
            "stage": stage,
            "user_language": state.get("user_language", ""),
            "reply_preview": reply[:120] if reply else "",
            "request_id": _ctx_request_id.get(""),
        },
    )
    # Simulation agents (role_play / SJT) run a fixed multi-turn arc and signal the
    # REAL end with `agent_complete: true`. Their `handoff_ready` is unreliable
    # mid-arc — the model sometimes flips it true on the persona-build / intro turn
    # (parse_control treats handoff_ready OR agent_complete as done). Left unchecked
    # that skips the whole simulation after its FIRST question AND fires the
    # post-simulation pattern mirror against that first question instead of at the
    # end of the role play. For these stages, ONLY `agent_complete: true` completes.
    if require_agent_complete and handoff:
        _rac_obj = _safe_json(resp.text) or {}
        # A handoff is "real" when agent_complete is set. CH additionally marks its genuine
        # Phase-3 completion with current_step == "phase_3_complete" (the same current_step
        # signal family used for phase_1/2 boundaries) while emitting handoff_ready:true but
        # NOT agent_complete — accept that too, ONLY for the CH stage. This keeps the
        # premature-handoff guard intact (a stray handoff_ready mid-phase has current_step
        # like "step_8", so it is still downgraded) while letting a legitimate Phase-3
        # completion hand off to the post-coaching layer.
        #
        # `_truthy`, not `bool()`: bool("false") is True, so a model that emits the STRING
        # "agent_complete": "false" was read as DONE and the simulation ended on its first
        # turn. Not hypothetical — json_repair, which salvages output truncated at the token
        # ceiling, repairs a cut-off `"agent_complete": tru` into the string "tru", which
        # bool() also reads as True. An ambiguous flag must never complete a stage.
        _rac_cs = str(_rac_obj.get("current_step") or "").strip().lower()
        handoff = _truthy(_rac_obj.get("agent_complete")) or (
            stage == STAGE_CH and _rac_cs == "phase_3_complete"
        )
        if not handoff:
            # Contract-violation telemetry: the agent tried to hand off (handoff_ready
            # / status) without agent_complete — e.g. CH emitting handoff_ready:true at
            # Phase-1 step_8. The turn continues as a normal coaching turn; this line is
            # what makes the misfire visible in dashboards instead of a QA escalation.
            logger.warning(
                "node.premature_handoff_ignored",
                extra={
                    "stage": stage,
                    "session_id": state.get("session_id", ""),
                    "user_id": state.get("user_id", ""),
                    "current_step": _rac_obj.get("current_step", ""),
                    "phase": _rac_obj.get("phase", ""),
                },
            )

    # Salvage first: the model may have written real prose AROUND the control JSON
    # (greeting + envelope). That prose already streamed to the client in raw mode,
    # so using it keeps the transcript identical to the screen — and avoids the
    # retry entirely. Pure-envelope outputs (no surrounding prose) fall through to
    # the retry/fallback below unchanged; silent HANDOFF turns are untouched.
    if not reply and not handoff:
        _salvaged = _salvage_prose_around_json(resp.text)
        if _salvaged:
            reply = _salvaged
            logger.info(
                "node.empty_reply_prose_salvaged",
                extra={"stage": stage, "session_id": state.get("session_id", ""),
                       "user_id": state.get("user_id", ""),
                       "salvaged_chars": len(_salvaged)},
            )

    # Empty, non-handoff reply = the user would see a BLANK bubble (prompt-contract
    # violation: every non-handoff turn must carry a user-facing key). Retry ONCE with
    # an explicit nudge before handing the user a dead turn; if the retry is still empty,
    # deliver a safe neutral re-prompt so the bubble is never blank (Round-1 bug #1).
    if not reply and not handoff:
        logger.warning(
            "node.empty_reply_retrying",
            extra={
                "stage": stage,
                "session_id": state.get("session_id", ""),
                "user_id": state.get("user_id", ""),
                "raw_response_chars": len(resp.text),
                "raw_response_prefix": resp.text[:300],
            },
        )
        try:
            retry_resp = client.generate_stream(
                system_prompt=system_prompt,
                user_prompt=user_message + _EMPTY_REPLY_RETRY_NUDGE,
                model=model,
                on_token=None,  # don't re-stream the recovered turn
                reasoning_effort=effort,
                history=history,
                stage=stage,
                session_id=state.get("session_id", ""),
                user_id=state.get("user_id", ""),
            )
            r2, h2, p2 = parse_control(retry_resp.text)
            if require_agent_complete and h2:
                # `_truthy`, not `bool()` — same reason as the primary path above: a
                # stringified "false"/"tru" flag must never complete the stage.
                h2 = _truthy((_safe_json(retry_resp.text) or {}).get("agent_complete"))
            if r2.strip() or h2:
                # Recovered. Use the retry's output for the reply AND for ALL downstream
                # parsing below (context_update / variables / progress read resp.text).
                reply, handoff, path, resp = r2, h2, (p2 or path), retry_resp
                logger.info(
                    "node.empty_reply_recovered",
                    extra={"stage": stage, "session_id": state.get("session_id", "")},
                )
            else:
                reply = _EMPTY_REPLY_FALLBACK
                logger.error(
                    "node.empty_reply_fallback",
                    extra={"stage": stage, "session_id": state.get("session_id", "")},
                )
        except Exception:  # noqa: BLE001 — the retry itself must never dead the turn
            reply = _EMPTY_REPLY_FALLBACK
            logger.error("node.empty_reply_retry_failed", extra={"stage": stage}, exc_info=True)

    # Count this stage's turns. Tracked for EVERY stage (not just floor-gated ones) so
    # the stage-stuck watchdog below has a turn count to work with on all of them.
    _turns = (state.get("gate_turns") or {}).get(stage, 0) + 1
    _gate_turns_update = {stage: _turns}

    # Floor-style completion gate: defer an EARLY completion for gated stages (role_play
    # /feedback) and re-prompt to continue the arc instead. Floor not script; bounded by
    # _MAX_FLOOR_REPROMPTS. See _COMPLETION_FLOOR_TURNS for the rationale.
    _floor = _COMPLETION_FLOOR_TURNS.get(stage)
    _gate_reprompts_update = None
    if _floor:
        _reprompts = (state.get("gate_reprompts") or {}).get(stage, 0)
        if handoff and _turns < _floor and _reprompts < _MAX_FLOOR_REPROMPTS:
            logger.info(
                "node.completion_floor_deferred",
                extra={"stage": stage, "turns": _turns, "floor": _floor,
                       "reprompts": _reprompts, "session_id": state.get("session_id", "")},
            )
            try:
                floor_resp = client.generate_stream(
                    system_prompt=system_prompt,
                    user_prompt=user_message + _FLOOR_CONTINUE_NUDGE,
                    model=model, on_token=None, reasoning_effort=effort,
                    history=history, stage=stage,
                    session_id=state.get("session_id", ""), user_id=state.get("user_id", ""),
                )
                fr, _fh, fp = parse_control(floor_resp.text)
                # Force continuation regardless of what the re-prompt's handoff says;
                # use its text + context_update for this turn when non-empty.
                if fr.strip():
                    reply, handoff, path, resp = fr, False, (fp or path), floor_resp
                else:
                    reply, handoff = _EMPTY_REPLY_FALLBACK, False
            except Exception:  # noqa: BLE001 — the gate must never break the turn
                handoff = False  # at minimum, don't honour the early completion
                logger.error("node.completion_floor_failed", extra={"stage": stage}, exc_info=True)
            _gate_reprompts_update = {stage: _reprompts + 1}

    # Completion CEILING (feedback anti-loop): count CONSECUTIVE same-step turns and,
    # past the ceiling, force the closing handoff so a stuck wrap-up can never trap the
    # user. Runs before `out` is built so the forced completion flows through normally.
    _fb_repeat_update = None
    _ceiling = _COMPLETION_CEILING.get(stage)
    if _ceiling:
        _cur_step = str((_safe_json(resp.text) or {}).get("current_step") or "").strip()
        _last_step = str(state.get("feedback_last_step") or "")
        _rep = ((state.get("feedback_step_repeats") or 0) + 1
                if (_cur_step and _cur_step == _last_step) else 1)
        _fb_repeat_update = (_cur_step, _rep)
        if not handoff and _cur_step and _rep >= _ceiling:
            logger.warning(
                "node.feedback_completion_ceiling",
                extra={"stage": stage, "step": _cur_step, "repeats": _rep,
                       "session_id": state.get("session_id", "")},
            )
            handoff = True  # force the closing handoff → route to `close`

    # Stage-stuck watchdog (universal backstop). A prompt that never signals completion
    # would otherwise pin the session on this stage indefinitely (live: 78 turns in
    # challenge_context). Past the cap, force the handoff so the graph always advances.
    # The user still receives THIS turn's reply; the next turn starts on the next stage.
    _cap = _stage_turn_cap(stage)
    if not handoff and _turns >= _cap:
        logger.error(
            "node.stage_watchdog_forced_advance",
            extra={"stage": stage, "turns": _turns, "cap": _cap,
                   "session_id": state.get("session_id", ""),
                   "user_id": state.get("user_id", "")},
        )
        record_stage_watchdog(stage=stage)
        handoff = True

    # Output-contract monitor — and, for the one violation that silently corrupts
    # routing, a REPAIR.
    #
    # Measured on the production stack (scripts/eval): challenge_context hands off with
    # NO coaching_path roughly 1 turn in 6. The router then falls back to CIM, so the
    # user gets the wrong coaching methodology for their entire session — and nothing
    # errors, because the fallback is a legitimate code path.
    #
    # The envelope is REQUESTED in the prompt, not ENFORCED (the client uses
    # `json_object`, i.e. "valid JSON", not `json_schema` with `required`). We cannot
    # switch to a strict schema without enumerating every field each agent emits —
    # `context_update` varies per stage, and strict mode forbids extra keys, so that
    # would break the envelopes we depend on.
    #
    # So instead: DON'T HONOUR a handoff that breaks the routing contract. Re-prompt once,
    # naming the missing field. This reuses the deferred-handoff pattern already used for
    # empty replies, costs a call only on the ~1-in-6 that actually fail, and turns a
    # silent mis-route into a corrected decision.
    try:
        _raw_envelope = _safe_json(resp.text) or {}
        _violations = check_turn_contract(stage, _raw_envelope)
        if handoff:
            _violations += check_handoff_contract(stage, _raw_envelope, state)

        _repairable = [v for v in _violations if v in _CONTRACT_REPAIR]
        if _repairable and handoff:
            _nudge = _CONTRACT_REPAIR[_repairable[0]]
            logger.warning(
                "node.contract_repair_attempt",
                extra={"stage": stage, "violation": _repairable[0],
                       "session_id": state.get("session_id", "")},
            )
            try:
                fix = client.generate_stream(
                    system_prompt=system_prompt,
                    user_prompt=user_message + _nudge,
                    model=model, on_token=None, reasoning_effort=effort,
                    history=history, stage=stage,
                    session_id=state.get("session_id", ""), user_id=state.get("user_id", ""),
                )
                r2, h2, p2 = parse_control(fix.text)
                fixed = _safe_json(fix.text) or {}
                still = check_handoff_contract(stage, fixed, state)
                if not [v for v in still if v in _CONTRACT_REPAIR]:
                    # Repaired: adopt the retry for the reply AND every downstream parse.
                    reply, handoff, path, resp = (r2 or reply), h2, (p2 or path), fix
                    _raw_envelope, _violations = fixed, still
                    logger.info("node.contract_repaired",
                                extra={"stage": stage, "coaching_path": p2})
                else:
                    logger.error("node.contract_repair_failed",
                                 extra={"stage": stage, "violation": _repairable[0]})
            except Exception:  # noqa: BLE001 — a repair must never break the turn
                logger.error("node.contract_repair_errored", extra={"stage": stage},
                             exc_info=True)

        contract_report(stage, _violations, state)
    except Exception:  # noqa: BLE001 — a monitor must never break the turn it watches
        logger.warning("node.contract_check_failed", extra={"stage": stage}, exc_info=True)

    # Trace what came OUT of the agent (gated): raw model text + the parsed
    # control fields, alongside tokens/cost/latency for this single call.
    trace.io(
        "agent.output",
        stage=stage,
        raw_output=resp.text,
        reply=reply,
        handoff_ready=handoff,
        coaching_path=path,
        prompt_tokens=resp.prompt_tokens,
        completion_tokens=resp.completion_tokens,
        cost_usd=resp.cost_usd,
        latency_ms=round(resp.model_latency_ms, 1),
        model=resp.model,
    )

    # Test-only override (CEREBROZEN_FORCE_HANDOFF): advance past this stage even if
    # the prompt didn't signal completion, so a live smoke-test can reach
    # simulation/close without playing the full arc. No-op in prod (set empty).
    if FORCE_HANDOFF_STAGES and (
        "__all__" in FORCE_HANDOFF_STAGES or stage in FORCE_HANDOFF_STAGES
    ):
        handoff = True

    # Handoff is decided by the prompt (`handoff` from parse_control's `handoff_ready`),
    # EXCEPT the feedback completion ceiling above, which forces it when the closing arc
    # is stuck looping on one step so the session can always reach `close`.
    out: Dict[str, Any] = {
        "reply_text": reply,
        "handoff_ready": handoff,
        "active_node": stage,
        "prompt_tokens": resp.prompt_tokens,
        "completion_tokens": resp.completion_tokens,
        "cost_usd": resp.cost_usd,
    }
    # Persist the per-stage turn count (watchdog + floor gate) — merge_dict reducer, so
    # each stage's counter accumulates across turns without clobbering the others.
    out["gate_turns"] = _gate_turns_update
    if _gate_reprompts_update is not None:
        out["gate_reprompts"] = _gate_reprompts_update
    # Persist the feedback same-step repeat counter (anti-loop ceiling above).
    if _fb_repeat_update is not None:
        out["feedback_last_step"], out["feedback_step_repeats"] = _fb_repeat_update

    # Track exploration questions (M2) and pair them with user answers at
    # completion so we can save {question, answer} objects to Mongo.
    if stage == STAGE_FEEDBACK:
        # Carry the feedback arc's context_update across turns (isolated channel) so
        # completion/step survives history truncation — the "repeated commitment
        # question" loop (QA-user-1/+32). Mirrors coaching_progress for the coaching
        # slot; re-injected next turn by _feedback_progress_block.
        _fb_new = extract_progress(resp.text)
        # CH: hard-seed the Phase-1 skip into the carried state (not only the prompt
        # assertion) — the model doesn't reliably echo `skipped: true` itself, and
        # without it in feedback_progress the carry-over JSON on later turns says
        # nothing about Phase 1, letting the arc drift back into the commitment loop
        # (live: QA-user-2 / QA-user-1, 2026-07-09, post-ab2b0e5). _merge_progress
        # is monotonic, so once seeded the skip can never be clobbered by the model.
        if (coaching_path or "").strip().upper() == "CH" and not (
            (state.get("feedback_progress") or {}).get("commitment_support")
        ):
            _fb_new = _merge_progress(
                {"commitment_support": {"skipped": True},
                 "commitment_support_complete": True},
                _fb_new or {},
            )
        if _fb_new:
            out["feedback_progress"] = _merge_progress(
                state.get("feedback_progress") or {}, _fb_new)
        try:
            _raw = json.loads(resp.text)
        except Exception:
            _raw = {}
        _step = _raw.get("current_step", "")
        _q_text = _raw.get("question", "")

        # M2: bot is asking the positive/negative exploration question — store it.
        if _step == "M2" and _q_text:
            _carried_exp = dict(state.get("feedback_exp_questions") or {})
            # Determine valence by which emotions are present
            _fb_prog_m2 = extract_progress(resp.text)
            _mc_m2 = _fb_prog_m2.get("mood_capture", {})
            _has_pos = bool(_mc_m2.get("positive_emotions"))
            _has_neg = bool(_mc_m2.get("negative_emotions"))
            if _has_pos and not _carried_exp.get("pos_q"):
                _carried_exp["pos_q"] = _q_text
            if _has_neg and not _carried_exp.get("neg_q"):
                _carried_exp["neg_q"] = _q_text
            # Fallback: no emotion info yet, store as pos_q
            if not _has_pos and not _has_neg and not _carried_exp.get("pos_q"):
                _carried_exp["pos_q"] = _q_text
            out["feedback_exp_questions"] = _carried_exp

        # On completion: lift mood_capture and attach a `responses` list that
        # pairs the bot's M2 exploration question with the user's raw answer.
        if handoff:
            _fb_progress = extract_progress(resp.text)
            _mc = _fb_progress.get("mood_capture")
            if isinstance(_mc, dict):
                _exp_qs = dict(state.get("feedback_exp_questions") or {})
                _exp_qs.update(out.get("feedback_exp_questions") or {})
                _q = _exp_qs.get("pos_q") or _exp_qs.get("neg_q") or ""
                _raw_ans = _mc.get("raw_user_response", "")
                if _q or _raw_ans:
                    _mc["responses"] = [{"question": _q, "answer": _raw_ans}]
                out["mood_capture_data"] = _mc

    # Carry the action_checkin arc's step-completion fields across turns (isolated
    # channel) so the OSCAR reflection tail can't truncate out the fact that a rating /
    # reflection / story was already captured and make it re-ask (same class as the
    # feedback loop). Flat contract → own extractor; re-injected by _checkin_progress_block.
    if stage == STAGE_ACTION_CHECKIN:
        _ck_new = _extract_checkin_progress(resp.text)
        if _ck_new:
            out["checkin_progress"] = _merge_progress(
                state.get("checkin_progress") or {}, _ck_new)

    # Persist this turn's arc progress (merged onto what's carried over) so the
    # next turn re-injects it. Coaching slot only — that's where the long Q1–Q6
    # arc lives and where truncation broke completion.
    if stage in _PROGRESS_STAGES:
        new_progress = extract_progress(resp.text)
        if new_progress:
            out["coaching_progress"] = _merge_progress(carried_progress, new_progress)
            # Simulation routing signal (routing spec): the coaching agent emits
            # `specific_person_identified` in context_update during Q1–Q2. Lift it
            # top-level so the deterministic simulation edge (_route_simulation) can
            # read it. Only set when the agent emitted a real bool, so a later turn
            # that omits it never clobbers the established value (the channel keeps
            # its last value across turns).
            spi = new_progress.get("specific_person_identified")
            if isinstance(spi, bool):
                out["specific_person_identified"] = spi
            elif isinstance(spi, str) and spi.strip().lower() in {"true", "false"}:
                out["specific_person_identified"] = spi.strip().lower() == "true"

    # learning_aid: persist its delivery-arc position to the isolated channel so the
    # NEXT learning-aid turn continues the arc instead of re-guessing from history
    # (which collapsed it straight to `commit`). See _learning_aid_progress_block.
    if stage == STAGE_LEARNING_AID:
        la_new = extract_progress(resp.text)
        if la_new:
            out["learning_aid_progress"] = _merge_progress(
                state.get("learning_aid_progress") or {}, la_new
            )

    # CH coaching: extract active phase and phase transition buttons (dynamic-var
    # capture is handled below by the registry-driven bridge, shared with every
    # other context_update-based stage). Both pieces share one JSON parse.
    if stage == STAGE_CH:
        _ch_raw = _safe_json(resp.text) or {}
        # active_phase: the CH output contract (2026-07 prompt revision) carries the
        # phase indicator as a top-level `phase` field — always "1", "2", or "3" — NOT
        # inside context_update (there is no context_update.current_phase in this
        # contract). Pass it through as-is; the UI wants the bare digit string, not a
        # "phase_N"-formatted value.
        _ch_phase_num = str(_ch_raw.get("phase") or "").strip()
        if _ch_phase_num:
            out["active_phase"] = _ch_phase_num

        # Detect phase completion. The CH agent marks a finished phase two ways that are
        # meant to agree: current_step = "phase_1_complete" | "phase_2_complete" (its
        # per-turn step tracker) AND awaiting_phase_transition:true + transition_options.
        # Trigger on EITHER so a boundary is never missed when the model emits one signal
        # but omits the other (LLM output isn't perfectly consistent) — this is what left
        # per-phase actions ungenerated in QA. Phase 1/2 build the Continue/Save&Exit
        # buttons → per-phase Action beat; Phase 3 completion stays on the agent_complete
        # handoff path (unchanged). transition_options, when the prompt sends it, wins over
        # the phase-derived default so the prompt keeps control of the exact button set.
        _cs = str(_ch_raw.get("current_step") or "").strip().lower()
        _pc = re.match(r"phase_([12])_complete$", _cs)
        _phase_done = _pc.group(1) if _pc else ""
        _opts = _ch_raw.get("transition_options") or []
        if not _opts:
            if _phase_done == "1":
                _opts = ["continue_to_phase_2", "save_and_exit"]
            elif _phase_done == "2":
                _opts = ["continue_to_phase_3", "save_and_exit"]
        if _phase_done or _ch_raw.get("awaiting_phase_transition"):
            _btn_labels = {
                "continue_to_phase_2": "Continue to Phase 2",
                "continue_to_phase_3": "Continue to Phase 3",
                "save_and_exit": "Save & Exit",
            }
            out["phase_buttons"] = [
                {"label": _btn_labels.get(o, o), "user_selection": o}
                for o in _opts
                if o in _btn_labels
            ]
        else:
            out["phase_buttons"] = []

    # Registry-driven capture: ANY stage whose agent has rows in
    # variable_capture_registry (dynamic_variables_persistent sheet, keyed by
    # source_agent == this stage) gets those context_update fields lifted from
    # the carried coaching_progress into captured_variables (→ Mongo) and
    # user_context (→ resolves for later turns THIS session too, same as
    # intake's variables_set path below). Was CH-only via a hand-maintained
    # whitelist; challenge_context_agent's coaching_style_context and
    # core_coaching_agent's behavioral_intake_responses used the same
    # context_update mechanism but had no bridge, so they never reached Mongo —
    # a brand-new session had no way to know they were already answered even
    # though the field-presence gates in those prompts were written correctly.
    # A stage with no registered rows (intake) is a no-op here.
    _progress_now = out.get("coaching_progress") or {}
    if _progress_now:
        _dyn = _bridge_registry_vars(stage, _progress_now)
        if _dyn:
            out["captured_variables"] = {**(out.get("captured_variables") or {}), **_dyn}
            out["user_context"] = {**(out.get("user_context") or user_context), **_dyn}

    # Capture any structured variables the node emitted (e.g. intake's role /
    # coachability / style) into user_context so downstream stages resolve their
    # placeholders instead of leaving {userRoleContext} etc. unfilled. Merged via
    # _merge_progress (not a blind spread) because intake's variables_set schema
    # emits ALL fields every turn with null for anything not yet answered — a
    # naive spread would let that null punch out a value already seeded from
    # Mongo (e.g. a {ci_accountability} answered in a prior abandoned session),
    # silently re-opening a question the user already answered.
    variables = extract_variables(resp.text)
    if variables:
        out["user_context"] = _merge_progress(out.get("user_context") or user_context, variables)
        # Also carry the raw variables_set on its own channel (reducer-merged) so
        # the engine can persist ONLY what the agent captured to the agentic store
        # at session close — without having to disentangle it from profile/NBI/DISC
        # data that also lives in user_context.
        out["captured_variables"] = _merge_progress(out.get("captured_variables") or {}, variables)

    # Append this turn to history (reducer concatenates) so the next turn has
    # full context.
    out["history"] = [
        {"role": "user", "content": user_message},
        {"role": "assistant", "content": reply},
    ]

    # challenge_context scores the user's challenge and emits the coaching path
    # (CIM / CBT / CH) in its structured output. Store the agent's decision as-is —
    # do NOT pre-stamp CIM when nothing was emitted, so a genuine CBT/CH choice is
    # never masked. The router (_coaching_route) applies a single logged last-resort
    # default if the path is missing. (CEREBROZEN_STUB_CHALLENGE=true forces the
    # Phase-1 stub → CIM for CIM-only validation; that path is handled in
    # challenge_node before this runs.)
    if stage == STAGE_CHALLENGE:
        if path:
            out["coaching_path"] = path
        if STUB_CHALLENGE:
            handoff = True

    out["handoff_ready"] = handoff
    # On handoff, advance the stage for the NEXT turn (sticky single-call model).
    # A node may supply resolve_next_stage to pick the next stage from its output
    # (the check-in agent maps its next_agent → intake/challenge); else linear order.
    if handoff:
        out["stage"] = resolve_next_stage(resp.text) if resolve_next_stage else next_stage(stage)

    logger.info(
        "node.stage",
        extra={
            "user_id": state.get("user_id", ""),
            "session_id": state.get("session_id", ""),
            "stage": stage,
            "handoff_ready": handoff,
            "coaching_path": out.get("coaching_path", coaching_path),
            "total_tokens": resp.total_tokens,
            "latency_ms": round(resp.model_latency_ms, 1),
            "cost_usd": resp.cost_usd,
            "model": resp.model,
            "llm": True,
        },
    )
    return out


def _checkin_due(state: CereBroZenState) -> bool:
    """Whether a repeat-user check-in is due THIS session — the 7-day scheduler's
    verdict, computed by profile_read into user_context.checkinDue (BRD §3, R1–R6).
    This is the code-side gate that replaces the prompt's deleted eligibility gate."""
    return bool((state.get("user_context") or {}).get("checkinDue"))


def _intake_next_stage(state: CereBroZenState) -> str:
    """After intake: go to the check-in node only when a check-in is actually DUE
    (7-day rule) AND the agent is enabled in the workbook Catalog tab; otherwise
    straight to challenge. A repeat user inside the 7-day window has nothing due,
    so intake (which already skipped) hands straight to challenge → no pre-session
    touch, by design."""
    if _checkin_due(state) and get_registry().is_enabled(STAGE_CHECKIN):
        return STAGE_CHECKIN
    return STAGE_CHALLENGE


def intake_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    return _run_stage(
        STAGE_INTAKE, state, config, resolve_next_stage=lambda _t: _intake_next_stage(state)
    )


def checkin_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    # Reached only when the 7-day scheduler said a check-in is due (code-gated via
    # _intake_next_stage), so the agent runs the ELIGIBLE conversation directly.
    # The only forward move from this position is challenge_context — intake has
    # already run, so check-in hands off to challenge (not back to intake).
    return _run_stage(STAGE_CHECKIN, state, config, resolve_next_stage=lambda _t: STAGE_CHALLENGE)


def challenge_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    # Phase-1 stub (per spec: challenge_context → CIM). When stubbed it's a CODE
    # router — no LLM call, no user-facing status — so it sets the path and hands
    # off instantly, and the turn chains straight to core_coaching (CIM). This
    # avoids a wasted ~9s LLM call and the confusing "challenge invoked" mid-turn.
    # Set CEREBROZEN_STUB_CHALLENGE=false to run the full multi-turn challenge agent.
    if STUB_CHALLENGE:
        logger.info(
            "node.stage",
            extra={
                "stage": STAGE_CHALLENGE,
                "handoff_ready": True,
                "coaching_path": "CIM",
                "stub": True,
                "llm": False,
            },
        )
        return {
            "coaching_path": "CIM",
            "handoff_ready": True,
            "reply_text": "",  # no user text → chains to CIM in the same turn
            "active_node": STAGE_CHALLENGE,
            "stage": next_stage(STAGE_CHALLENGE),
        }
    return _run_stage(STAGE_CHALLENGE, state, config)


def _closing_next_stage() -> str:
    """The stage after the last substantive coaching/simulation/support step is
    ALWAYS the closing feedback/mood-capture agent — never the terminal `close`
    directly. Feedback is the sole legitimate end of a session (the deck's "End of
    chat" agent), so every flow funnels through it. Deterministic — no LLM."""
    return STAGE_FEEDBACK


def _learning_aid_next_stage(state: CereBroZenState) -> str:
    """After the coaching/simulation slot: insert the learning-aid node for every
    coaching path (CIM, CBT, CH) when enabled in the Catalog tab, else go to the
    closing layer. Deterministic — no LLM. (`state` kept for signature parity with
    the other *_next_stage helpers and the closures that call it.)"""
    if get_registry().is_enabled(STAGE_LEARNING_AID):
        return STAGE_LEARNING_AID
    return _closing_next_stage()


def _route_simulation(state: CereBroZenState) -> str:
    """Pick the simulation agent (simulation-routing spec). The coaching agent
    infers `specific_person_identified` during Q1–Q2:
        True  → role_play_agent (rehearse a real conversation with that person)
        False / absent → SJT_simulation_agent (situational-judgement default)
    Pure state predicate — no LLM hop (constitution: deterministic routing)."""
    return STAGE_ROLEPLAY if state.get("specific_person_identified") else STAGE_SJT


def _post_actions_stage(state: CereBroZenState) -> str:
    """Stage to advance to AFTER dynamic_actions_insights hands off from a coaching
    trigger (CIM/CBT/CH). All three paths route to simulation (role_play or SJT based
    on specific_person_identified). If simulation is disabled, falls back to
    learning_aid or feedback so the session never dead-ends. Stored as
    `actions_next_stage` in state so the gate node can read it on its second
    (handoff) invocation without re-computing."""
    path = (state.get("coaching_path") or "CIM").upper()
    if path in {"CIM", "CBT", "CH"}:
        reg = get_registry()
        # simulation_decision_agent (when authored + enabled) is the intelligence that
        # decides whether to OFFER role_play / SJT / skip — both CIM and CH route to it.
        # It forwards to role_play_agent / SJT_simulation_agent / pattern. Reversible:
        # a disabled/unauthored agent falls through to the original deterministic gate
        # (specific_person_identified), so nothing changes until the agent is turned on.
        if reg.is_enabled(STAGE_SIMULATION_DECISION) and reg.get(STAGE_SIMULATION_DECISION).strip():
            return STAGE_SIMULATION_DECISION
        sim_stage = _route_simulation(state)
        if reg.is_enabled(sim_stage):
            return sim_stage
        other = STAGE_SJT if sim_stage == STAGE_ROLEPLAY else STAGE_ROLEPLAY
        if reg.is_enabled(other):
            return other
    return _learning_aid_next_stage(state)


def _post_simulation_actions_stage(state: CereBroZenState) -> str:
    """Stage to advance to AFTER the post-simulation pattern reflect beat: always
    learning_aid (when enabled) or feedback. Consumed by pattern_node (and by the
    simulation-prompt-missing fallback, which skips the action/pattern beats and jumps
    straight here)."""
    if get_registry().is_enabled(STAGE_LEARNING_AID):
        return STAGE_LEARNING_AID
    return STAGE_FEEDBACK


def _coaching_next_stage(state: CereBroZenState) -> str:
    """After coaching hands off, always route to the dynamic_actions_insights gate.
    The gate delivers the actions/insights to the user; on the NEXT turn it hands off
    to simulation / learning_aid / feedback (whichever _post_actions_stage computed)."""
    return STAGE_DYNAMIC_ACTIONS


def _route_simulation_decision(resp_text: str) -> str:
    """Map simulation_decision_agent's `simulation_route` → the next graph stage, with an
    is_enabled fallback so a disabled simulation node never dead-ends:

      "role_play_agent"      → role_play   (if enabled)
      "SJT_simulation_agent" → sjt         (if enabled)
      "skip" / unknown / disabled target → pattern (reflect beat; the prompt's skip→pattern)
    """
    obj = _safe_json(resp_text) or {}
    route = str(obj.get("simulation_route") or "").strip().lower()
    reg = get_registry()
    if route == "role_play_agent" and reg.is_enabled(STAGE_ROLEPLAY):
        return STAGE_ROLEPLAY
    if route == "sjt_simulation_agent" and reg.is_enabled(STAGE_SJT):
        return STAGE_SJT
    return STAGE_PATTERN


def simulation_decision_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    """Post-coaching simulation gate (simulation_decision_agent). Runs after core/CH.

    Sticky 2-turn node: turn 1 either OFFERS simulation (offer text in `question`,
    handoff_ready false → user sees the invitation, turn ends) or SKIPS immediately
    (handoff_ready true, no text → chains straight on). Turn 2 reads the user's yes/no
    (from history) and routes via `simulation_route` → role_play / SJT / (skip →) pattern.

    Missing/disabled prompt → skip straight to the reflect beat; never run a bare LLM.
    Entry is already gated in _post_actions_stage, so this branch is defensive."""
    reg = get_registry()
    if not reg.is_enabled(STAGE_SIMULATION_DECISION) or not reg.get(STAGE_SIMULATION_DECISION).strip():
        logger.warning("node.simulation_decision_unavailable_skip",
                       extra={"stage": STAGE_SIMULATION_DECISION})
        return {"reply_text": "", "handoff_ready": True,
                "active_node": STAGE_SIMULATION_DECISION, "stage": STAGE_PATTERN}
    return _run_stage(STAGE_SIMULATION_DECISION, state, config,
                      resolve_next_stage=_route_simulation_decision)


def core_coaching_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    out = _run_stage(STAGE_CORE, state, config, resolve_next_stage=lambda _t: STAGE_DYNAMIC_ACTIONS)
    if out.get("handoff_ready"):
        out["actions_next_stage"] = _post_actions_stage(state)
        out["action_agent_type"] = out.get("active_node", STAGE_CORE)
    return out


def _run_path_stage(stage: str, state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    """Run a path-specific coaching node (CBT / Capability) — but only if its prompt
    has been authored. If the workbook sheet is missing/empty, fall back to CIM
    (core_coaching) so the user always gets coaching. This is the "wire now,
    drop the prompt in later" contract: the path activates the instant its B7 is
    filled, with no code change."""
    nxt = lambda _t: _coaching_next_stage(state)  # noqa: E731 — small closure
    if not get_registry().get(stage).strip():
        logger.warning(
            "node.path_prompt_missing_fallback_cim",
            extra={"stage": stage, "fallback": STAGE_CORE, "llm": True},
        )
        # CIM fallback runs WITHOUT the agent_complete gate: core's contract leans on
        # handoff_ready alone, so gating it here would strand fallback sessions.
        return _run_stage(STAGE_CORE, state, config, resolve_next_stage=nxt)
    # Gate the path agent's handoff on `agent_complete` (same guard as role_play/SJT):
    # CH's contract sets agent_complete ONLY at genuine Phase-3 completion, but the
    # model emitted a stray handoff_ready:true at Phase-1 step_8 (live QA, session
    # c2705307…, 2026-07-06 15:39) which yanked the user into the full post-coaching
    # layer mid-phase. With the gate, that turn downgrades to a normal coaching turn
    # (and logs node.premature_handoff_ignored). Phase buttons and Save & Exit are
    # unaffected — neither rides on handoff_ready.
    return _run_stage(stage, state, config, resolve_next_stage=nxt,
                      require_agent_complete=True)




# Action-inlay acknowledgment turns: one or more save/skip/delete outcome words joined
# by "|" and nothing else (e.g. "saved", "saved|saved|delete", "Skipped | saved").
_ACK_ONLY_RE = re.compile(
    r"^\s*(saved?|skip(ped)?|deleted?)(\s*\|\s*(saved?|skip(ped)?|deleted?))*\s*$",
    re.IGNORECASE,
)


def _boundary_hold_line(state: CereBroZenState) -> str:
    """Fixed one-liner shown when an inlay ack is swallowed at a parked CH phase
    boundary — keeps the turn from ending in a blank bubble and re-points the user
    at the transition choice. Deterministic; no LLM."""
    _next = {"1": "Phase 2", "2": "Phase 3"}.get(
        str(state.get("active_phase") or "").strip(), "the next phase")
    return (f"Your action is saved. Whenever you're ready, choose Continue to "
            f"{_next} — or Save & Exit to wrap up here.")


def capability_coaching_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    _sc = (state.get("session_continued") or "").strip()

    # (A) "Save & Exit" → DIRECT close (business decision 2026-07-10: exit means exit —
    # the user lands on Home; no pattern reflection, no feedback questions, no further
    # processing of any kind). Progress and this phase's actions were already persisted
    # by the Action beat on the phase-completion turn, so nothing is lost; the
    # session-close builders (user-context + cumulative pattern write) still run — the
    # engine treats a capability→close delta as a natural close, off-path. The empty
    # reply chains into session_complete THIS turn, so the response carries the close
    # message + `ended: true` and the client navigates Home on it.
    #
    # UNCONDITIONAL on the button value (contract confirmed with the client team
    # 2026-07-11): `save_and_exit` is sent EXCLUSIVELY by the Save & Exit button press —
    # never on session resume (the old prompt-era dual use is retired client-side). The
    # previous `ch_awaiting_transition` gate existed only for that dual use, and it made
    # the exit fragile: any non-matching ack / free-text turn between saving cards and
    # pressing the button cleared the flag and the press fell through to normal coaching
    # (live: "works with 1 saved action, chat continues with 2", 2026-07-10 QA).
    if _sc == "save_and_exit":
        return {
            "handoff_ready": True,
            "reply_text": "",              # chain into session_complete this turn
            "active_node": STAGE_CH,
            "stage": STAGE_CLOSE,
            "ch_early_exit": True,         # marker: engine fires close builders off this
            "ch_awaiting_transition": False,
        }

    # (A2) DETERMINISTIC BOUNDARY HOLD — action-inlay acks never reach the LLM.
    # While the user is parked at a phase boundary (ch_awaiting_transition), the client
    # sends the card save/skip outcomes as a bare text turn ("saved|saved|delete"). Fed
    # to the CH model — which is still standing at the boundary — that stray input made
    # it improvise: re-emit phase_N_complete (→ duplicate Action beats/cards, live:
    # QA-user-1 got the PH1 cards 3x, QA-user-2 5x) or drift into the returning-user
    # "Welcome back" opening mid-session. The card outcomes already reach the backend
    # via /actions/status, so the text is pure noise here: swallow it without an LLM
    # call and keep the boundary parked. Not added to history — it isn't conversation.
    if state.get("ch_awaiting_transition") and _ACK_ONLY_RE.match(state.get("user_message", "")):
        logger.info(
            "node.ch_boundary_ack_swallowed",
            extra={"user_id": state.get("user_id", ""),
                   "session_id": state.get("session_id", ""),
                   "llm": False},
        )
        return {
            "reply_text": _boundary_hold_line(state),
            "handoff_ready": False,
            "active_node": STAGE_CH,
            "stage": STAGE_CORE,                       # stay in the coaching slot
            # Explicit [] — NOT a re-offer. The client renders every phase_buttons
            # payload as a NEW live row and never retires old ones, so re-sending
            # duplicated the button set on screen (QA-user-3, 2026-07-10 16:50Z).
            # The card-delivery row above stays visible and pressable; the hold
            # line's TEXT points the user back at it. Explicit [] (vs omitting the
            # key) matters: phase_buttons is a state channel — omitted, the
            # checkpointed buttons ride the engine payload again anyway.
            "phase_buttons": [],
            "ch_awaiting_transition": True,
        }

    out = _run_path_stage(STAGE_CH, state, config)

    if out.get("handoff_ready"):
        # (B) Phase 3 complete (agent_complete → handoff_ready) → FULL post-coaching layer
        # (simulation_decision → pattern → learning_aid → feedback). Unchanged.
        out["actions_next_stage"] = _post_actions_stage(state)
        out["action_agent_type"] = out.get("active_node", STAGE_CH)
        out["ch_awaiting_transition"] = False
    elif out.get("phase_buttons"):
        # (C) Phase 1 or 2 complete: CH emitted `awaiting_phase_transition` (→ phase_buttons
        # present) but NOT the final handoff. Fire the Action beat NOW so each phase's
        # actions are captured, then return to the CH coaching slot for the next phase.
        # Chain straight into the dynamic_actions gate this turn (suppress CH's "continue
        # into Phase N?" line; the blueprint was already presented on a prior turn). The gate
        # delivers the action cards carrying the Continue/Save&Exit buttons; on the user's
        # next turn the gate hands back to STAGE_CORE → _coaching_route → capability, and CH
        # reads the fresh {session_continued}. `ch_awaiting_transition` marks that the user is
        # now being shown the transition choice, so a following save_and_exit is recognised
        # as a genuine exit (branch A). Actions dedup on full_text in the agentic store.
        #
        # ONCE-PER-PHASE GUARD: while parked at the boundary, the model keeps re-emitting
        # the phase_N_complete milestone on every non-button turn (that's its contract —
        # it IS still at the boundary). Unguarded, each re-emit re-ran the beat and
        # REGENERATED cards (live: QA-user-1 3x with different cards each time — 3/1/2).
        # ch_beats_fired makes the beat fire exactly once per phase; on a re-emit the
        # turn passes through as a normal reply.
        _beats = list(state.get("ch_beats_fired") or [])
        _done_phase = str(out.get("active_phase") or "").strip()
        if _done_phase and _done_phase in _beats:
            logger.info(
                "node.ch_beat_already_fired",
                extra={"user_id": state.get("user_id", ""),
                       "session_id": state.get("session_id", ""),
                       "phase": _done_phase},
            )
            out["ch_awaiting_transition"] = True
            # Suppress the re-emitted buttons: the client stacks every phase_buttons
            # payload as a new live row (duplication — QA-user-3 2026-07-10). The
            # original card-delivery row remains the one live button set on screen.
            out["phase_buttons"] = []
            if not (out.get("reply_text") or "").strip():
                # milestone-only re-emit with no prose → never end the turn on a blank
                # bubble; re-point at the transition choice instead.
                out["reply_text"] = _boundary_hold_line(state)
            return out
        out["handoff_ready"] = True
        out["reply_text"] = ""                  # chain to the gate this turn (no CH bubble)
        out["stage"] = STAGE_DYNAMIC_ACTIONS
        out["actions_next_stage"] = STAGE_CORE   # after the Action beat → back to CH slot
        out["action_agent_type"] = out.get("active_node", STAGE_CH)
        out["ch_awaiting_transition"] = True
        # Record which phase's Action beat just fired (the completing phase == this turn's
        # active_phase) so the recovery below and the once-per-phase guard above can tell
        # a genuine skip from a normal completion. Full-list write (no reducer on this field).
        if _done_phase and _done_phase not in _beats:
            _beats.append(_done_phase)
            out["ch_beats_fired"] = _beats
    elif _sc in ("continue_to_phase_2", "continue_to_phase_3"):
        # (D) User chose to continue to the next phase — consume the pending-transition flag.
        out["ch_awaiting_transition"] = False
    else:
        # (E) DETERMINISTIC PHASE-BOUNDARY RECOVERY. The CH model unreliably emits the
        # phase_N_complete / awaiting_phase_transition milestone (branch C) — and worse,
        # it often DROPS the JSON contract entirely and replies in plain prose, so
        # `active_phase` (only updated from the JSON `phase` field) doesn't advance on
        # those turns. Net effect: a phase's Action beat + Continue buttons silently never
        # fire (live: QA-user-1 lost Phase 2; the `phase` field skipped 1 -> 3 because
        # Phase 2 was all prose).
        #
        # The `phase` DIGIT is the one signal we can trust when it IS present, so recover
        # deterministically off it: whenever we ARRIVE at a later phase than we were in
        # (cur advanced past prev) and the phase we just completed hasn't fired its beat,
        # force that beat now. "Completed phase" = cur - 1 (arrive at 2 -> recover P1;
        # arrive at 3 -> recover P2, including the 1 -> 3 prose skip). Fires once per phase
        # (guarded by ch_beats_fired), stays dormant for a normally-completed phase (branch
        # C already recorded its beat), and never touches Phase 3's close (branch B,
        # agent_complete). Phase-1/2 completed normally still go through branch C above.
        #
        # First: the user answered a pending transition offer with FREE TEXT (no button),
        # i.e. they carried on coaching past the offer. Clear the pending flag so a LATER
        # `save_and_exit` — which the frontend also sends when RESUMING after a break —
        # can't be misread as a genuine exit (branch A requires this flag for that reason).
        # The recovery below re-sets it when it fires a fresh offer this same turn.
        if state.get("ch_awaiting_transition"):
            out["ch_awaiting_transition"] = False
        _prev_phase = str(state.get("active_phase") or "").strip()
        _cur_phase = str(out.get("active_phase") or "").strip()
        _beats = state.get("ch_beats_fired") or []
        _advanced = (_cur_phase in ("2", "3") and _prev_phase in ("", "1", "2")
                     and _cur_phase != _prev_phase and int(_cur_phase) > int(_prev_phase or 0))
        _completed = str(int(_cur_phase) - 1) if _cur_phase in ("2", "3") else ""
        if _advanced and _completed and _completed not in _beats and not out.get("phase_buttons"):
            _next_sel = f"continue_to_phase_{_cur_phase}"
            _labels = {"continue_to_phase_2": "Continue to Phase 2",
                       "continue_to_phase_3": "Continue to Phase 3",
                       "save_and_exit": "Save & Exit"}
            logger.warning(
                "node.ch_phase_beat_recovered",
                extra={"user_id": state.get("user_id", ""), "session_id": state.get("session_id", ""),
                       "prev_phase": _prev_phase, "cur_phase": _cur_phase, "recovered_phase": _completed},
            )
            out["handoff_ready"] = True
            out["reply_text"] = ""                 # suppress the stray next-phase bubble; chain to gate
            out["stage"] = STAGE_DYNAMIC_ACTIONS
            out["actions_next_stage"] = STAGE_CORE  # after the Action beat → back to the CH slot
            out["action_agent_type"] = out.get("active_node", STAGE_CH)
            out["ch_awaiting_transition"] = True
            out["phase_buttons"] = [
                {"label": _labels[_next_sel], "user_selection": _next_sel},
                {"label": "Save & Exit", "user_selection": "save_and_exit"},
            ]
            _beats = list(_beats)
            _beats.append(_completed)
            out["ch_beats_fired"] = _beats
    return out


def _run_simulation_stage(stage: str, state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    """Run a simulation node (role_play / SJT). On handoff it advances to the
    dynamic_actions gate — the Action agent runs IMMEDIATELY after simulation. The gate
    then forwards to the pattern reflect beat (STAGE_PATTERN), which finally advances to
    learning_aid / feedback. Missing prompt → skip simulation AND the action/pattern
    beats, advance directly to learning_aid / feedback."""
    nxt = lambda _t: STAGE_DYNAMIC_ACTIONS  # noqa: E731
    if not get_registry().get(stage).strip():
        logger.warning("node.simulation_prompt_missing_skip", extra={"stage": stage})
        return {"reply_text": "", "handoff_ready": True, "active_node": stage,
                "stage": _post_simulation_actions_stage(state)}
    return _run_stage(stage, state, config, resolve_next_stage=nxt,
                      require_agent_complete=True)


def role_play_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    out = _run_simulation_stage(STAGE_ROLEPLAY, state, config)
    # Action agent runs IMMEDIATELY after simulation, THEN the pattern reflect beat.
    # On handoff we route to the dynamic_actions gate and tell it to forward to
    # STAGE_PATTERN once the cards are delivered; pattern then advances to
    # learning_aid / feedback. Stamp the trigger so the gate builds the right payload.
    if out.get("handoff_ready") and out.get("stage") == STAGE_DYNAMIC_ACTIONS:
        out["actions_next_stage"] = STAGE_PATTERN
        out["action_agent_type"] = out.get("active_node", STAGE_ROLEPLAY)
    return out


def sjt_simulation_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    out = _run_simulation_stage(STAGE_SJT, state, config)
    if out.get("handoff_ready") and out.get("stage") == STAGE_DYNAMIC_ACTIONS:
        out["actions_next_stage"] = STAGE_PATTERN
        out["action_agent_type"] = out.get("active_node", STAGE_SJT)
    return out


def pattern_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    """Post-simulation reflect beat — pattern_agent's `in_session` invocation.

    Reached ONLY after the post-simulation Action agent (dynamic_actions gate) has
    delivered its cards — the Action agent runs FIRST, immediately after role_play /
    SJT, and the gate then forwards here. Surfaces ONE pattern mirror as its OWN
    user-facing turn (spec: reflect beat AFTER the action capture), then advances to
    the closing support layer (learning_aid / feedback).

    A non-empty mirror is delivered as `reply_text` (handoff_ready + text → the turn ENDS
    here, the mirror is its own message). A null mirror (no pattern cleared the Potential
    gate) or builders-off → forward with NO text so `_after_stage` chains straight on with
    no empty bubble. The cumulative Pattern Intelligence Model write is separate
    (dispatch_pattern_write at session close) and unaffected."""
    from app.graph.builders import run_pattern_mirror

    _emit_status(config, STAGE_PATTERN)
    user_id = state.get("user_id", "")
    session_id = state.get("session_id", "")
    mirror = ""
    try:
        mirror = run_pattern_mirror(user_id, session_id, list(state.get("history") or []))
    except Exception:  # noqa: BLE001 — the reflect beat must never break the turn
        logger.warning("node.pattern_mirror_failed", extra={"session_id": session_id}, exc_info=True)

    # Stream the mirror to the client so it animates like any other reply. run_pattern_mirror
    # can't stream its own tokens (its raw output is JSON; the mirror is one field of it), so
    # emit the finished text in one shot on the same token channel the LLM nodes use.
    if mirror:
        on_token = (config.get("configurable") or {}).get("on_token")
        if on_token:
            try:
                on_token(mirror)
            except Exception:  # noqa: BLE001 — live streaming is best-effort
                pass

    out: Dict[str, Any] = {
        "reply_text": mirror,             # non-empty → ends turn as its own message;
        "handoff_ready": True,            #   empty → _after_stage chains onward
        "active_node": STAGE_PATTERN,
        # Pattern runs AFTER the action gate (Action agent already delivered its cards),
        # so from here advance to the closing support layer: learning_aid / feedback.
        # EXCEPTION — a CH Phase-1/2 "Save & Exit" (ch_early_exit) is the LIGHT close: go
        # straight to feedback, skipping the learning-aid beat.
        "stage": STAGE_FEEDBACK if state.get("ch_early_exit") else _post_simulation_actions_stage(state),
    }
    # Record the surfaced mirror in the transcript so the close-time cumulative scan and
    # the next turn see it. Only when non-empty (a null signal adds nothing to history).
    if mirror:
        out["history"] = [{"role": "assistant", "content": mirror}]
    logger.info(
        "node.stage",
        extra={
            "stage": STAGE_PATTERN,
            "handoff_ready": True,
            "surfaced": bool(mirror),
            "llm": bool(mirror),
            "user_id": user_id,
            "session_id": session_id,
        },
    )
    return out


def _run_learning_aid_stage(stage: str, state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    """Run the learning-aid node. Missing prompt → skip straight to the
    dynamic_actions_insights gate (same as a normal handoff). On handoff it advances
    to STAGE_DYNAMIC_ACTIONS; the gate then delivers actions/insights and hands off to
    feedback on the following turn."""
    nxt = lambda _t: STAGE_DYNAMIC_ACTIONS  # noqa: E731
    if not get_registry().get(stage).strip():
        logger.warning("node.learning_aid_prompt_missing_skip", extra={"stage": stage})
        return {"reply_text": "", "handoff_ready": True, "active_node": stage,
                "stage": STAGE_DYNAMIC_ACTIONS}
    return _run_stage(stage, state, config, resolve_next_stage=nxt)


def learning_aid_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    out = _run_learning_aid_stage(STAGE_LEARNING_AID, state, config)
    if out.get("handoff_ready"):
        out["actions_next_stage"] = STAGE_FEEDBACK
        out["action_agent_type"] = out.get("active_node", STAGE_LEARNING_AID)
    return out


# --- dynamic actions/insights gate ------------------------------------------


def dynamic_actions_insights_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    """Two-shot gate that sits after every major stage: coaching → simulation →
    learning_aid → feedback. Each stage hands off to STAGE_DYNAMIC_ACTIONS and
    stores its intended successor in `actions_next_stage`.

    First invocation (actions_builder_done=False):
      LLM call → extracts response_to_user + actions/insights → persists to agentic
      store → ends the turn with response_to_user as reply and shaped cards in state.

    Second invocation (actions_builder_done=True, next user turn):
      No LLM call. Immediately hands off to `actions_next_stage` (set by the caller
      node when it handed off to us). _after_stage then chains to the correct next
      node in the SAME turn."""
    from app.graph.builders import run_actions_insights_for_node

    user_id = state.get("user_id", "")
    session_id = state.get("session_id", "")

    # Second invocation: user acknowledged the actions, hand off to next stage.
    if state.get("actions_builder_done"):
        _next_raw = state.get("actions_next_stage")
        next_s = _next_raw or STAGE_FEEDBACK
        # Diagnostic guard (LOG ONLY — no behaviour change): in normal flow the caller
        # node always sets actions_next_stage (coaching/simulation/learning_aid). If it
        # is empty here the gate silently defaults to feedback — the exact way
        # learning_aid can get skipped after a simulation. Flag it so the rare case is
        # catchable in production logs without altering routing.
        if not _next_raw:
            logger.warning(
                "node.dynamic_actions_no_next_stage",
                extra={
                    "defaulted_to": STAGE_FEEDBACK,
                    "prev_active_node": state.get("active_node", ""),
                    "user_id": user_id,
                    "session_id": session_id,
                },
            )
        logger.info(
            "node.stage",
            extra={
                "stage": STAGE_DYNAMIC_ACTIONS,
                "handoff_ready": True,
                "actions_next_stage": next_s,
                "llm": False,
                "user_id": user_id,
                "session_id": session_id,
            },
        )
        return {
            "reply_text": "",
            "handoff_ready": True,
            "active_node": STAGE_DYNAMIC_ACTIONS,
            "stage": next_s,
            "actions_builder_done": False,   # reset for any future re-entry
            "generated_actions": [],          # clear so engine doesn't re-send stale cards
            "generated_insights": [],         # clear alongside actions
            "action_agent_type": "",          # consumed — clear so a later gate can't reuse it
        }

    # First invocation: run LLM, generate actions/insights, deliver to user.
    _emit_status(config, STAGE_DYNAMIC_ACTIONS)
    # agent_type is the node that triggered us (coaching agent / simulation /
    # learning_aid). Read the durable `action_agent_type` the triggering node stamped on
    # handoff — NOT `active_node`, which has already been reset to "profile_read" (the
    # turn-entry node) whenever the gate runs on a later turn. Fall back to active_node
    # for safety (e.g. an ad-hoc re-entry that didn't set the field).
    agent_type = state.get("action_agent_type") or state.get("active_node", "")
    history = list(state.get("history") or [])

    # Business rule (2026-07-11, supersedes the 2026-07-10 one-card cap): a beat
    # delivers EVERY action the LLM generated, all in the SINGLE inlay carousel —
    # no cap, on any path (CH included). Row-stacking / repeat-card problems the
    # old cap papered over are handled by their real mechanisms instead: the
    # once-per-phase beat guard (a parked CH boundary can't re-fire the beat) and
    # the session-scoped dedup in agentic.append_actions_insights (a later beat
    # can't re-ship an earlier row's action). Card count per beat is inherently
    # LLM-variable; shaping it belongs to the action-agent prompt, not code.
    shaped_actions, shaped_insights, response_to_user = run_actions_insights_for_node(
        user_id, session_id, history, agent_type,
        state.get("user_context"), state.get("coaching_path"),
    )

    # Empty output: advance so the session never dead-ends. But a null signal is only
    # VALID after simulation / learning_aid — after a coaching agent (core / CH) the
    # action-agent contract mandates >=1 action, so an empty result there is a FAILURE
    # (prompt unauthored, LLM error, …), not a normal skip. Log it at error level so the
    # rare case is caught in production without blocking the user.
    if not shaped_actions and not shaped_insights and not response_to_user:
        next_s = state.get("actions_next_stage") or STAGE_FEEDBACK
        if agent_type in COACHING_STAGES:
            logger.error(
                "node.dynamic_actions_missing_action_after_coaching",
                extra={"reason": "no_action_after_coaching", "agent_type": agent_type,
                       "next_stage": next_s, "user_id": user_id, "session_id": session_id},
            )
        else:
            logger.warning(
                "node.dynamic_actions_skip",
                extra={"reason": "no_output", "agent_type": agent_type,
                       "next_stage": next_s, "user_id": user_id},
            )
        return {
            "reply_text": "",
            "handoff_ready": True,
            "active_node": STAGE_DYNAMIC_ACTIONS,
            "stage": next_s,
            "generated_actions": [],
            "generated_insights": [],
        }

    out: Dict[str, Any] = {
        "reply_text": response_to_user,
        "handoff_ready": False,
        "active_node": STAGE_DYNAMIC_ACTIONS,
        "stage": STAGE_DYNAMIC_ACTIONS,   # stays here; next turn invocation hands off
        "actions_builder_done": True,
        "generated_actions": shaped_actions,
        "generated_insights": shaped_insights,
    }
    # Do NOT thread response_to_user into conversation history here: it is the FIXED UI
    # card label ("Suggested Action. Please review and save", builders.ACTION_CARD_TITLE),
    # not coaching dialogue. The closing agents (feedback_mood_capture) infer their step
    # from history, and this non-conversational string derails them — it makes feedback run
    # C1/Commitment despite coaching_path=="CH" and oscillate/stall, never reaching Close
    # (reproduced in scripts/diag_feedback_loop*). The actions themselves are delivered via
    # `generated_actions` (structured), so nothing is lost by keeping the label out of history.
    logger.info(
        "node.stage",
        extra={
            "stage": STAGE_DYNAMIC_ACTIONS,
            "handoff_ready": False,
            "actions": len(shaped_actions),
            "insights": len(shaped_insights),
            "llm": True,
            "user_id": user_id,
            "session_id": session_id,
        },
    )
    return out


# --- pre-feedback "Final action check" (Edge case: all actions skipped) ------

# One-liner nudge shown when the user reaches the close having saved NO action.
FINAL_ACTION_CHECK_NUDGE = (
    "Looks like you skipped all your actions. Please pick one before we close the "
    "coaching conversation."
)


def _session_saved_action_count(user_id: str, session_id: str) -> int:
    """How many actions the user explicitly SAVED this session (UI Save → status
    'saved'). Generated-but-untouched cards are 'active', deleted are 'deleted' —
    neither counts. Best-effort: any store error reads as 0 saved (→ nudge)."""
    try:
        from app.stores import agentic
        doc = agentic.load(user_id) or {}
        return sum(
            1 for a in (doc.get("actions") or [])
            if isinstance(a, dict) and a.get("session_id") == session_id
            and a.get("status") == "saved"
        )
    except Exception:  # noqa: BLE001 — the gate must never break the turn
        logger.warning("node.final_action_check_read_failed",
                       extra={"user_id": user_id, "session_id": session_id}, exc_info=True)
        return 0


def final_action_check_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    """Mandatory pre-feedback gate (Edge case: all actions skipped). Every road to the
    closing feedback layer is routed through here (build_graph).

    - >=1 action SAVED this session  → pass straight to feedback, no interruption.
    - 0 saved, but cards exist       → nudge + re-surface the session's already-generated
                                        action cards (reuse, no regeneration) and BLOCK
                                        here until the user saves one (mandatory). Stays on
                                        this stage; re-checks each turn.
    - 0 saved AND nothing generated  → pass through (nothing to pick; never hang the user).

    Once passed, `final_action_check_done` is set so the multi-turn feedback stage is
    never re-intercepted."""
    from app.actions_insights import _shape
    from app.stores import agentic

    user_id = state.get("user_id", "")
    session_id = state.get("session_id", "")

    saved_count = _session_saved_action_count(user_id, session_id)
    # Re-surface everything generated this session (non-deleted) as the carousel — reuse
    # what the dynamic_actions gate already built; do NOT regenerate.
    try:
        cards = _shape((agentic.load(user_id) or {}).get("actions") or [],
                       "full_text", session_id, id_field="action_id")
    except Exception:  # noqa: BLE001
        cards = []

    if saved_count or not cards:
        logger.info(
            "node.final_action_check_pass",
            extra={"saved": saved_count, "cards": len(cards),
                   "reason": "saved" if saved_count else "nothing_to_pick",
                   "user_id": user_id, "session_id": session_id},
        )
        return {
            "reply_text": "",                 # control-only → chains to feedback this turn
            "handoff_ready": True,
            "active_node": STAGE_FINAL_ACTION_CHECK,
            "stage": STAGE_FEEDBACK,
            "final_action_check_done": True,
            # Clear any carousel this gate re-surfaced on a previous (nudge) turn —
            # without this the cards ride state into every subsequent feedback turn's
            # payload and the client re-renders the carousel on each closing exchange.
            "generated_actions": [],
        }

    # 0 saved and cards exist → mandatory nudge + re-surfaced carousel.
    #
    # BOUNDED. The gate blocks until an action is saved, but it must not block FOREVER:
    # this is a non-LLM node, so it never runs through _run_stage and inherits neither the
    # completion ceiling nor the stage watchdog. Unbounded, a user who simply doesn't want
    # an action (the literal "all actions skipped" edge case) gets the same canned nudge on
    # every turn, the session can never reach the closing layer, and mood/feedback is never
    # captured — a worse outcome than closing with zero actions. So: nudge, then relent.
    nudges = (state.get("gate_turns") or {}).get(STAGE_FINAL_ACTION_CHECK, 0) + 1
    if nudges > _FINAL_ACTION_CHECK_MAX_NUDGES:
        logger.error(
            "node.final_action_check_watchdog_pass",
            extra={"nudges": nudges, "cards": len(cards), "saved": saved_count,
                   "user_id": user_id, "session_id": session_id},
        )
        record_stage_watchdog(stage=STAGE_FINAL_ACTION_CHECK)
        return {
            "reply_text": "",                 # control-only → chains to feedback this turn
            "handoff_ready": True,
            "active_node": STAGE_FINAL_ACTION_CHECK,
            "stage": STAGE_FEEDBACK,
            "final_action_check_done": True,
            "generated_actions": [],
            "gate_turns": {STAGE_FINAL_ACTION_CHECK: nudges},
        }

    logger.info(
        "node.final_action_check_nudge",
        extra={"cards": len(cards), "nudge": nudges,
               "user_id": user_id, "session_id": session_id},
    )
    return {
        "reply_text": FINAL_ACTION_CHECK_NUDGE,
        "handoff_ready": False,               # turn ends; user must save one, then re-check
        "active_node": STAGE_FINAL_ACTION_CHECK,
        "stage": STAGE_FINAL_ACTION_CHECK,
        "generated_actions": cards,           # re-surface as inlay cards / carousel
        "gate_turns": {STAGE_FINAL_ACTION_CHECK: nudges},
    }


# --- standalone per-action check-in (outside the coaching pipeline) ----------


def action_checkin_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    """The standalone `action_checkin_agent` — a self-contained 15-step reflection on ONE
    action the user tapped "Action Check-In" on. It is NOT reached by any coaching edge:
    the engine seeds `stage=STAGE_ACTION_CHECKIN` + `checkin_action` at entry, so this runs
    as its own sticky mini-session and, on handoff (Step 15 → next_agent EndOfConversation),
    advances straight to `close` — never through feedback / final_action_check.

    Merges the tapped action's fields into user_context each turn so the prompt's
    {action_item} / {action_outcome} (and {userName}) resolve."""
    _reg = get_registry()
    # Activation is the Catalog `enabled` flag (single source of truth, toggled by prompt
    # engineers) AND an authored prompt. Either off → don't run a bare LLM; degrade and end.
    if not _reg.is_enabled(STAGE_ACTION_CHECKIN) or not _reg.get(STAGE_ACTION_CHECKIN).strip():
        logger.error(
            "node.action_checkin_unavailable",
            extra={"stage": STAGE_ACTION_CHECKIN,
                   "enabled": _reg.is_enabled(STAGE_ACTION_CHECKIN),
                   "prompt_len": len(_reg.get(STAGE_ACTION_CHECKIN) or "")},
        )
        return {
            "reply_text": "This action check-in isn't available right now. Please try again later.",
            "handoff_ready": True,
            "active_node": STAGE_ACTION_CHECKIN,
            "stage": STAGE_CLOSE,
        }
    ca = state.get("checkin_action") or {}
    if ca:
        ctx = dict(state.get("user_context") or {})
        if ca.get("action_item"):
            ctx["action_item"] = ca["action_item"]
        if ca.get("action_outcome"):
            ctx["action_outcome"] = ca["action_outcome"]
        state = {**state, "user_context": ctx}
    return _run_stage(
        STAGE_ACTION_CHECKIN, state, config, resolve_next_stage=lambda _t: STAGE_CLOSE
    )


# --- closing layer: feedback capture + real session termination --------------


def feedback_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    """The deck's closing layer (feedback_mood_capture_agent). Sticky multi-turn
    (mood capture → feedback capture); on agent_complete the prompt fires
    `next_agent: "EndOfConversation"` and the stage advances to the terminal close.
    Missing prompt → DO NOT close. The closing agent is the sole legitimate path
    to a terminal close, so we never end a session without it: log loudly and keep
    the session on the feedback stage (only /endconversation can force-close in
    this misconfig state)."""
    if not get_registry().get(STAGE_FEEDBACK).strip():
        logger.error("node.feedback_prompt_missing_no_close", extra={"stage": STAGE_FEEDBACK})
        return {"reply_text": FEEDBACK_UNAVAILABLE_REPLY, "handoff_ready": False,
                "active_node": STAGE_FEEDBACK, "stage": STAGE_FEEDBACK}
    out = _run_stage(STAGE_FEEDBACK, state, config)
    mc = out.pop("mood_capture_data", None)
    if mc:
        save_mood_capture(
            user_id=state.get("user_id", ""),
            session_id=state.get("session_id", ""),
            mood_capture=mc,
        )
    return out


def session_complete_node(state: CereBroZenState, config: RunnableConfig) -> Dict[str, Any]:
    """Terminal node for a finished session. Reached when a turn resumes at the
    `close` stage (the conversation already ended). Returns a fixed reply instead
    of falling back into coaching — this is what makes `close` a real terminal
    stage and what `EndOfConversation` ultimately resolves to."""
    logger.info("node.session_complete", extra={"llm": False})
    return {
        "reply_text": SESSION_COMPLETE_REPLY,
        "handoff_ready": True,
        "active_node": "session_complete",
        "stage": STAGE_CLOSE,
    }
