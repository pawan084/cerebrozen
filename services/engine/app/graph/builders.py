"""Always-on background builders — OFF the request path.

After a coaching turn (and at session close) these run async on a daemon
ThreadPoolExecutor, never blocking the user's reply:

  - dynamic_actions_insights_agent → detect actions/insights from the turn,
    dedup against what's stored, persist to the per-user agentic store.
  - user_context_builder_agent → at session close, build the 10-dimension User
    Context Model and persist it.

`profile_read` reads both back next session, so the user_profile_retrieval agent
has real continuity data. Everything here is best-effort and swallows errors —
a builder failure must never affect a turn.
"""

from __future__ import annotations

import json
import logging
from concurrent.futures import ThreadPoolExecutor
from typing import Any, Dict, List, Optional, Tuple

from app import config, trace
from app.graph.runtime import get_client, get_registry
from app.graph.tools import _safe_json
from app.llm.responses_client import reasoning_effort_for
from app.stores import agentic

logger = logging.getLogger("cerebrozen.builders")

# Small daemon pool: builders are I/O+LLM bound and off the hot path.
_EXECUTOR = ThreadPoolExecutor(max_workers=2, thread_name_prefix="builder")

ACTIONS_INSIGHTS_AGENT = "dynamic_actions_insights_agent"
USER_CONTEXT_AGENT = "user_context_builder_agent"
PATTERN_AGENT = "pattern_agent"

# Maps the triggering coaching/simulation agent to the action verb the
# dynamic_actions_insights_agent prompt resolves via {verb}.
_AGENT_VERB_MAP: Dict[str, str] = {
    "core_coaching_agent": "explore",
    "CH_coaching_agent": "develop",
    "role_play_agent": "rehearse",
    "SJT_simulation_agent": "reflect",
    "learning_aid_agent": "apply",
}

# The action inlay card title is a FIXED backend string (per the action-agent prompt's
# response_to_user contract) — the backend sets it whenever actions are present rather
# than reading it from the LLM output (which nests it under `actions` in the combined
# shape and would otherwise be missed). Never paraphrase this.
ACTION_CARD_TITLE = "Suggested Action. Please review and save"


# --- output parsing ----------------------------------------------------------


def _normalize_action_roi(a: Dict[str, Any]) -> Dict[str, Any]:
    """Coerce the LLM's roi_metric (single string) to roi_metrics (list).

    The prompt emits ``roi_metric`` as a string; we store and serve a list so the
    UI can assign multiple Development Areas per action. Handles both old
    (``roi_metric``) and new (``roi_metrics``) LLM output shapes, and drops the
    legacy field so only ``roi_metrics`` (list) is stored."""
    a = dict(a)
    if "roi_metrics" not in a:
        raw = a.pop("roi_metric", None)
        if isinstance(raw, list):
            a["roi_metrics"] = [str(r).strip() for r in raw if str(r).strip()]
        elif raw:
            a["roi_metrics"] = [str(raw).strip()]
        else:
            a["roi_metrics"] = []
    else:
        # Already a list from a newer prompt version — clean up any stray singular.
        a.pop("roi_metric", None)
        if not isinstance(a["roi_metrics"], list):
            v = str(a["roi_metrics"]).strip()
            a["roi_metrics"] = [v] if v else []
    return a


def extract_actions_insights(obj: Any) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    """Normalise the builder's output (actions-only / insights-only / combined /
    null shapes) into (actions, insights) lists of dicts.

    Each action dict is normalised so that ``roi_metrics`` is always a list
    (coerced from the LLM's singular ``roi_metric`` string when needed)."""
    actions: List[Dict[str, Any]] = []
    insights: List[Dict[str, Any]] = []
    if not isinstance(obj, dict):
        return actions, insights
    a_block = obj.get("actions")
    if isinstance(a_block, dict):  # combined shape: {actions:{actions:[...]}}
        actions = a_block.get("actions") or []
    elif isinstance(a_block, list):  # actions-only: {actions:[...]}
        actions = a_block
    i_block = obj.get("insights")
    if isinstance(i_block, dict):
        insights = i_block.get("insights") or []
    elif isinstance(i_block, list):
        insights = i_block
    return (
        [_normalize_action_roi(a) for a in actions if isinstance(a, dict)],
        [i for i in insights if isinstance(i, dict)],
    )


def _transcript(history: List[Dict[str, str]]) -> str:
    return "\n".join(
        f"{h.get('role', '')}: {h.get('content', '')}" for h in (history or [])
    ).strip()


# --- actions / insights (per coaching turn) ----------------------------------


def _active_actions(stored: Dict[str, Any]) -> List[Dict[str, Any]]:
    """User-confirmed ("saved") actions only — what the dynamic agent dedups against
    and what feeds the coaching context. Active (unconfirmed) and deleted actions are
    excluded so they can resurface as fresh cards in a future session."""
    return [a for a in stored.get("actions", []) if a.get("status") == "saved"]


def _generate_and_store(
    user_id: str, session_id: str, history: List[Dict[str, str]], agent_type: str,
    user_context: Optional[Dict[str, Any]] = None, coaching_path: Optional[str] = None,
    max_actions: Optional[int] = None,
) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]], str]:
    """Run the dynamic actions/insights agent once and persist anything new.
    Returns (new_actions, new_insights, response_to_user). The response_to_user is the
    FIXED action-card title (set only when actions were actually ADDED — a beat whose
    every action deduped away must not ship a card-title reply with zero cards).
    `max_actions` caps the extracted actions BEFORE persistence (CH: one card per
    phase) so the store never accumulates actions the user was never shown.
    `user_context`/`coaching_path` are threaded so the always-on environment_system
    guardrails wrap this call too (same as every coaching node). Shared by the
    foreground node and legacy background helpers; never raises."""
    try:
        prompt = get_registry().get(ACTIONS_INSIGHTS_AGENT)
        if not prompt.strip():
            logger.warning("builder.actions_prompt_missing")
            return [], [], ""
        # Guardrails always-on: compose the environment_system_agent constraints +
        # placeholder resolution around the action prompt, exactly like _run_stage does
        # for coaching nodes. The action agent's own JSON schema stays authoritative.
        from app.graph.guardrails import build_system_prompt

        verb = _AGENT_VERB_MAP.get(agent_type, "act")
        system_prompt = build_system_prompt(
            get_registry().environment, prompt, coaching_path,
            {**(user_context or {}), "verb": verb},
            invoking_agent=ACTIONS_INSIGHTS_AGENT,
        )
        stored = agentic.load(user_id)
        # Field names MUST match the action-agent prompt's input contract
        # (`action_response` + `insight_history`) — otherwise the model can't see the
        # dedup inputs and re-surfaces already-shown items.
        payload = {
            "conversation_history": history,
            "agent_type": agent_type,
            "action_response": [a.get("full_text") for a in _active_actions(stored)],
            "insight_history": [
                {"insight_title": i.get("insight_title"), "insight_body": i.get("insight_body")}
                for i in stored.get("insights", [])
            ],
        }
        trace.io("builder.input", builder=ACTIONS_INSIGHTS_AGENT, user_id=user_id,
                 agent_type=agent_type, payload=payload)
        _model = get_registry().model_for(ACTIONS_INSIGHTS_AGENT)
        if not _model:
            logger.error("builder.model_missing", extra={"agent": ACTIONS_INSIGHTS_AGENT})
            return [], [], ""
        resp = get_client().generate(
            system_prompt=system_prompt,
            user_prompt=json.dumps(payload, ensure_ascii=False),
            model=_model,
            reasoning_effort=reasoning_effort_for("builder", _model),
            stage=ACTIONS_INSIGHTS_AGENT,
            session_id=session_id,
        )
        obj = _safe_json(resp.text) or {}
        actions, insights = extract_actions_insights(obj)
        # Cap BEFORE persistence so the store matches what the user is shown. The
        # generator is non-deterministic about how many actions it extracts; trimming
        # only the shipped payload (the old post-hoc cap in the node) left never-shown
        # actions in the store, which leaked out later via the final-action-check /
        # UC3 carousels (QA 2026-07-10: CH session persisted 8, showed 5).
        if max_actions is not None and len(actions) > max_actions:
            logger.info(
                "builder.actions_capped",
                extra={"user_id": user_id, "agent_type": agent_type,
                       "generated": len(actions), "kept": max_actions},
            )
            actions = actions[:max_actions]
        new_actions, new_insights = agentic.append_actions_insights(
            user_id, actions, insights, session_id, agent_name=agent_type
        )
        # response_to_user is the FIXED card title and is shown ONLY when a card will
        # actually render — i.e. when actions were ADDED after dedup. Keying it off
        # the raw extraction shipped a bare "Suggested Action…" reply with zero cards
        # whenever a later beat regenerated only already-delivered actions.
        response_to_user = ACTION_CARD_TITLE if new_actions else ""
        trace.io("builder.output", builder=ACTIONS_INSIGHTS_AGENT, user_id=user_id,
                 raw_output=resp.text, actions=actions, insights=insights,
                 response_to_user=response_to_user)
        logger.info(
            "builder.actions_insights_done",
            extra={"user_id": user_id, "agent_type": agent_type,
                   "detected_actions": len(actions), "detected_insights": len(insights),
                   "added": len(new_actions) + len(new_insights)},
        )
        return new_actions, new_insights, response_to_user
    except Exception as exc:  # noqa: BLE001 — best-effort capture
        logger.warning("builder.actions_insights_failed", extra={"error": str(exc)})
        return [], [], ""


def _shape_for_payload(actions: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Surface each new action with its `id` (= action_id) bound, so the UI can
    render the action card AND call the save/delete API on it within the same
    session. All other fields (full_text, verb, expected_outcome, roi_metrics,
    confidence, session_id, status) pass through unchanged."""
    return [
        {"id": a.get("action_id") or agentic.stable_id(a.get("full_text")), **a}
        for a in actions
        if a.get("full_text")
    ]


def _shape_insights_for_payload(insights: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Surface each new insight with its `id` (= insight_id) bound — parallel to
    _shape_for_payload for actions. All stored fields pass through unchanged."""
    return [
        {"id": i.get("insight_id") or agentic.stable_id(i.get("insight_title")), **i}
        for i in insights
        if i.get("insight_title")
    ]


def collect_actions_insights(
    user_id: str, session_id: str, state: Dict[str, Any], agent_type: str = ""
) -> List[Dict[str, Any]]:
    """FOREGROUND: run the dynamic actions/insights agent over the session so far and
    return the freshly-generated actions (insights are persisted but NOT returned —
    the turn payload carries actions only; the right-panel endpoint serves both).

    Fired by the engine ONLY when a trigger agent has COMPLETED this turn
    (Post-Coaching CIM/CBT/CH or Post-learning-aid — not after simulation), passing
    that completing agent as `agent_type`. When omitted it falls back to the turn's
    `active_node` (ad-hoc callers); either way it must be in BUILDER_TRIGGER_AGENTS.
    The coaching reply has
    already streamed by the time this runs, so it only delays the final `done` event
    that carries the cards — never the reply tokens."""
    if not config.ENABLE_BUILDERS or not user_id:
        return []
    agent_type = agent_type or state.get("active_node", "")
    if agent_type not in config.BUILDER_TRIGGER_AGENTS:
        return []
    history = list(state.get("history") or [])
    new_actions, _, _ = _generate_and_store(
        user_id, session_id, history, agent_type,
        state.get("user_context"), state.get("coaching_path"),
    )
    return _shape_for_payload(new_actions)


def run_actions_insights_for_node(
    user_id: str, session_id: str, history: List[Dict[str, str]], agent_type: str,
    user_context: Optional[Dict[str, Any]] = None, coaching_path: Optional[str] = None,
    max_actions: Optional[int] = None,
) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]], str]:
    """Called by the dynamic_actions_insights_node graph node (foreground, in-graph).
    Returns (shaped_new_actions, shaped_new_insights, response_to_user) from a single
    LLM call so the node can set reply_text + action/insight cards in one shot.
    `max_actions` caps extraction before persistence (CH: one card per phase).
    `user_context`/`coaching_path` are passed through so the environment guardrails
    wrap the call. Never raises."""
    new_actions, new_insights, response_to_user = _generate_and_store(
        user_id, session_id, history, agent_type, user_context, coaching_path,
        max_actions=max_actions,
    )
    return _shape_for_payload(new_actions), _shape_insights_for_payload(new_insights), response_to_user


def dispatch_actions_insights(
    user_id: str, session_id: str, state: Dict[str, Any], agent_type: str = ""
) -> None:
    """Fire the actions/insights builder off-path, gated to coaching/simulation turns.

    This is the REQUEST-PATH entry (Art. 15): the engine calls it after the reply has
    streamed so the builder LLM call never delays `done`. `agent_type` is the agent
    that COMPLETED this turn (Post-Coaching/Post-Simulation) — passed explicitly by the
    engine so a control-only handoff that chains to the next stage still attributes the
    builder correctly; it falls back to the turn's `active_node`. Generated
    actions/insights persist to the agentic store and are served by the
    /actions-insights endpoint. `collect_actions_insights` (foreground, returns the
    actions inline) is retained only for scripts/tests that want the result back."""
    if not config.ENABLE_BUILDERS or not user_id:
        return
    agent_type = agent_type or state.get("active_node", "")
    if agent_type not in config.BUILDER_TRIGGER_AGENTS:
        return
    history = list(state.get("history") or [])
    _EXECUTOR.submit(
        _generate_and_store, user_id, session_id, history, agent_type,
        state.get("user_context"), state.get("coaching_path"),
    )


# --- intake variables (when an agent captures variables_set) ------------------


def dispatch_intake_vars(user_id: str, variables: Dict[str, Any]) -> None:
    """Persist a node's captured `variables_set` (intake's flat Table-1 vars) to the
    per-user agentic store, OFF the request path. Non-LLM — a single field-level
    upsert — but dispatched async so a flaky-Mongo write never delays the reply.
    Gated by the same builders flag; fired by the engine only on the turn the vars
    are freshly captured (intake's final handoff), so it writes once."""
    if not config.ENABLE_BUILDERS or not user_id or not variables:
        return
    _EXECUTOR.submit(agentic.save_intake_vars, user_id, dict(variables))


# --- dynamic session variables (any agent, any stage) -------------------------


def dispatch_dynamic_vars(
    user_id: str,
    session_id: str,
    variables: Dict[str, Any],
    stage: str = "",
    turn_seq: int = 0,
) -> None:
    """Persist any agent's `variables_set` block to agentic_session_dynamic_variables,
    OFF the request path.

    Works for ALL agents — intake, challenge_context, CH, core_coaching — without
    distinguishing which one emitted the variables. Once-in-lifetime vars (e.g.
    userRoleContext) are protected inside save_session_dynamic_vars: if a non-empty
    value is already stored, the new value is silently skipped.

    The request_id is captured from the calling thread's ContextVar NOW, before the
    async executor runs, so it correctly reflects the originating API request rather
    than whatever request the background thread might see later.

    Stage and turn_seq give the CloudWatch trail: every written variable's provenance
    includes session_id + stage + turn_seq + generated_at, which matches the
    structured-log lines emitted by _run_stage at the same moment.
    """
    if not config.ENABLE_BUILDERS or not user_id or not variables:
        return
    from app.stores import dynamic_vars as _dv
    from app.request_context import request_id as _req_id_ctx

    rid = _req_id_ctx.get("")  # capture ContextVar value in the calling thread
    _EXECUTOR.submit(
        _dv.save_session_dynamic_vars,
        user_id,
        session_id,
        dict(variables),
        stage,
        turn_seq,
        rid,
    )


# --- repeat-user check-in: mark batches closed (BRD R3, one-time-only) --------


def dispatch_checkin_complete(user_id: str, session_ids: List[str]) -> None:
    """Permanently mark the checked-in prior-session batches complete, OFF the
    request path. Fired by the engine when the check-in agent hands off, using the
    code-computed `checkinSessionIds` (not LLM-echoed ids) as the source of truth.
    `$addToSet` makes it idempotent (NFR), and the scheduler then excludes these
    sessions from every future check-in (R3)."""
    ids = [s for s in (session_ids or []) if s]
    if not user_id or not ids:
        return
    _EXECUTOR.submit(agentic.mark_checkin_complete, user_id, ids)


# --- user context model (session close) --------------------------------------


def _run_context_builder(user_id: str, session_id: str, history: List[Dict[str, str]], user_context: Dict[str, Any]) -> None:
    try:
        prompt = get_registry().get(USER_CONTEXT_AGENT)
        if not prompt.strip():
            logger.warning("builder.context_prompt_missing")
            return
        from app.rag.placeholders import PLACEHOLDER_RE, PlaceholderResolver

        stored = agentic.load(user_id)
        ctx = {
            **(user_context or {}),
            "session_transcript": _transcript(history),
            "previousUserContext": json.dumps(stored.get("user_context_model", {}), ensure_ascii=False),
            "previousUserActions": json.dumps(
                [a.get("full_text") for a in _active_actions(stored)], ensure_ascii=False
            ),
        }
        try:
            # rag_enabled=False: builders are background JSON processors — a RAG
            # token in their sheet must not fire a retrieval off the request path;
            # unresolved tokens are blanked (never left as literal {token}).
            system_prompt = PlaceholderResolver(user_context=ctx, rag_enabled=False).resolve_text(prompt)
        except Exception:  # noqa: BLE001 — resolution must never break the build
            system_prompt = PLACEHOLDER_RE.sub("", prompt)

        trace.io("builder.input", builder=USER_CONTEXT_AGENT, user_id=user_id,
                 system_prompt=system_prompt)
        _model = get_registry().model_for(USER_CONTEXT_AGENT)
        if not _model:
            logger.error("builder.model_missing", extra={"agent": USER_CONTEXT_AGENT})
            return
        resp = get_client().generate(
            system_prompt=system_prompt,
            user_prompt="Build and write the updated User Context Model now. Output only the JSON.",
            model=_model,
            reasoning_effort=reasoning_effort_for("builder", _model),
            stage=USER_CONTEXT_AGENT,
            session_id=session_id,
        )
        trace.io("builder.output", builder=USER_CONTEXT_AGENT, user_id=user_id,
                 raw_output=resp.text)
        obj = _safe_json(resp.text) or {}
        model = obj.get("user_context_model") or obj  # accept wrapped or bare
        if isinstance(model, dict) and model:
            agentic.save_user_context_model(user_id, model)
            logger.info("builder.context_done", extra={"user_id": user_id})
        else:
            logger.warning("builder.context_empty", extra={"user_id": user_id})
    except Exception as exc:  # noqa: BLE001
        logger.warning("builder.context_failed", extra={"error": str(exc)})


def dispatch_context_builder(user_id: str, session_id: str, history: List[Dict[str, str]], user_context: Dict[str, Any]) -> None:
    """Fire the user-context builder off-path at session close."""
    if not config.ENABLE_BUILDERS or not user_id:
        return
    _EXECUTOR.submit(_run_context_builder, user_id, session_id, list(history or []), dict(user_context or {}))


# --- pattern_agent (LangGraph two-invocation contract) -----------------------
# The workbook prompt (B7) defines two modes, each returning JSON:
#   in_session       — after simulation, before learning_aid: scan the current
#                      session, surface ONE pattern via a mirror block to the user.
#   background_write — post-session: full cumulative scan, merge with the prior
#                      ic_profile, return the updated Pattern Intelligence Model.
# Inputs are the prompt's own placeholders: {conversation_history} + {ic_profile};
# invocation_mode is passed in the user turn (the prompt reads it at call time).


def _call_pattern_agent(
    user_id: str, session_id: str, history: List[Dict[str, str]], invocation_mode: str
) -> Optional[Dict[str, Any]]:
    """Run pattern_agent in the given mode. Fills the prompt's {conversation_history}
    + {ic_profile} placeholders, passes invocation_mode in the user turn, and returns
    the parsed JSON (or None on any failure / empty output)."""
    try:
        prompt = get_registry().get(PATTERN_AGENT)
        if not prompt.strip():
            logger.warning("builder.pattern_prompt_missing")
            return None
        from app.rag.placeholders import PLACEHOLDER_RE, PlaceholderResolver

        stored = agentic.load(user_id)
        ic = stored.get("ic_profile")  # the prior Pattern Intelligence Model
        if isinstance(ic, (dict, list)):
            ic_profile = json.dumps(ic, ensure_ascii=False)
        else:
            ic_profile = str(ic).strip() if ic else "null"  # null string on first session
        ctx = {"conversation_history": _transcript(history), "ic_profile": ic_profile}
        try:
            # rag_enabled=False — same rationale as _run_context_builder above.
            system_prompt = PlaceholderResolver(user_context=ctx, rag_enabled=False).resolve_text(prompt)
        except Exception:  # noqa: BLE001
            system_prompt = PLACEHOLDER_RE.sub("", prompt)

        _model = get_registry().model_for(PATTERN_AGENT)
        if not _model:
            logger.error("builder.model_missing", extra={"agent": PATTERN_AGENT})
            return None
        directive = (
            "Scan the current session and surface one pattern via the mirror block."
            if invocation_mode == "in_session"
            else "Run the full cumulative scan, merge with ic_profile, and return the updated ic_profile."
        )
        resp = get_client().generate(
            system_prompt=system_prompt,
            user_prompt=f"invocation_mode: {invocation_mode}\n\n{directive}",
            model=_model,
            reasoning_effort=reasoning_effort_for("builder", _model),
            stage=PATTERN_AGENT,
            session_id=session_id,
        )
        return _safe_json(resp.text)
    except Exception as exc:  # noqa: BLE001
        logger.warning("builder.pattern_failed", extra={"error": str(exc), "mode": invocation_mode})
        return None


def run_pattern_mirror(
    user_id: str, session_id: str, history: List[Dict[str, str]]
) -> str:
    """in_session invocation — run in the FOREGROUND right after the simulation
    agent finishes. Returns the mirror text to surface to the user ("" when builders
    are off, no user, a null signal, or failure). Runs after the reply has streamed,
    so it only delays the `done` event, never the reply tokens."""
    if not config.ENABLE_BUILDERS or not user_id:
        return ""
    data = _call_pattern_agent(user_id, session_id, list(history or []), "in_session")
    cu = (data or {}).get("context_update") or {}
    mirror = (cu.get("pattern_mirror_output") or "").strip()
    if mirror:
        agentic.save_pattern_mirror(user_id, mirror)
        logger.info(
            "builder.pattern_mirror_done",
            extra={
                "user_id": user_id,
                "cluster": cu.get("pattern_cluster_surfaced"),
                "facet": cu.get("pattern_facet_surfaced"),
            },
        )
    else:
        # A null signal (no pattern cleared the Potential gate) is a valid outcome.
        logger.info("builder.pattern_mirror_null", extra={"user_id": user_id})
    return mirror


def _run_pattern_write(user_id: str, session_id: str, history: List[Dict[str, str]]) -> None:
    """background_write invocation — full cumulative scan; persist the updated
    Pattern Intelligence Model (ic_profile) as serialized JSON."""
    data = _call_pattern_agent(user_id, session_id, history, "background_write")
    ic = (data or {}).get("ic_profile")
    if isinstance(ic, (dict, list)):
        ic_profile = json.dumps(ic, ensure_ascii=False)
    elif isinstance(ic, str) and ic.strip():
        ic_profile = ic.strip()
    else:
        logger.warning("builder.pattern_write_empty", extra={"user_id": user_id})
        return
    if agentic.save_ic_profile(user_id, ic_profile):
        logger.info(
            "builder.pattern_write_done",
            extra={"user_id": user_id, "ic_profile_chars": len(ic_profile)},
        )


def dispatch_pattern_write(user_id: str, session_id: str, history: List[Dict[str, str]]) -> None:
    """Fire the background_write invocation OFF the request path at session close
    (cumulative Pattern Intelligence Model — captures the full session)."""
    if not config.ENABLE_BUILDERS or not user_id:
        return
    _EXECUTOR.submit(_run_pattern_write, user_id, session_id, list(history or []))


# --- KV-cache prewarm for the offline (Ollama) backend -----------------------


def _run_prewarm(stage: str, user_context: Dict[str, Any], coaching_path: str) -> None:
    """Load the NEXT agent's system prompt into the model's KV cache."""
    try:
        provider = get_client()
        if not hasattr(provider, "prewarm"):
            return                      # OpenAI: server-side caching, nothing to do
        from app.graph.guardrails import build_system_prompt
        from app.llm.responses_client import model_for

        reg = get_registry()
        node_prompt = reg.get(stage)
        if not node_prompt.strip():
            return
        system_prompt = build_system_prompt(
            reg.environment, node_prompt, coaching_path or None, user_context, {},
            invoking_agent=stage,
        )
        provider.prewarm(system_prompt, model_for(stage, catalog_model=reg.model_for(stage)))
    except Exception as exc:  # noqa: BLE001 — a prewarm must never affect a turn
        logger.warning("prewarm.failed", extra={"stage": stage, "error": str(exc)})


def dispatch_prewarm(stage: str, user_context: Dict[str, Any], coaching_path: str = "") -> None:
    """Fire-and-forget: warm the next stage's prompt prefix, OFF the request path.

    Only called when the stage CHANGED. That matters: the model caches one prefix at a
    time, so prewarming while the user is still mid-stage would EVICT the prefix they are
    about to reuse and make their next turn slower, not faster.

    Measured (gemma4, 27K-token CH prompt): a cold stage transition costs the user 10.6s;
    prewarmed, 1.8s. The cost doesn't vanish — it moves to the seconds the user spends
    reading the previous reply and typing the next one.
    """
    if not stage or not config.ENABLE_PREWARM:
        return
    _EXECUTOR.submit(_run_prewarm, stage, dict(user_context or {}), coaching_path or "")
