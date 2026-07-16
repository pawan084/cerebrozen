"""Agent-flow screen surface: the compiled graph as a diagram + the live stage of a
session, so the browser can visualise the deterministic agent flow and light up the node
a session is currently on.

Read-only introspection over the already-compiled LangGraph — no new LLM calls.
"""
from __future__ import annotations

import logging

from fastapi import APIRouter, Body, Depends, HTTPException
from fastapi.concurrency import run_in_threadpool

from app.auth import auth_enabled, require_auth, require_internal_admin
from app.service import get_service
from app.session import user_id_from_claims
from app.stores import conversation
from app.graph.state import (
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
)

logger = logging.getLogger("cerebrozen.flow")
router = APIRouter()

# stage constant -> graph node id (mirrors build_graph._dispatch_stage)
STAGE_TO_NODE = {
    STAGE_CHECKIN: "checkin",
    STAGE_ACTION_CHECKIN: "action_checkin",
    STAGE_INTAKE: "intake",
    STAGE_CHALLENGE: "challenge",
    STAGE_CORE: "core",
    STAGE_CH: "capability",
    STAGE_SIMULATION_DECISION: "simulation_decision",
    STAGE_ROLEPLAY: "role_play",
    STAGE_SJT: "sjt",
    STAGE_PATTERN: "pattern",
    STAGE_LEARNING_AID: "learning_aid",
    STAGE_DYNAMIC_ACTIONS: "dynamic_actions",
    STAGE_FINAL_ACTION_CHECK: "final_action_check",
    STAGE_FEEDBACK: "feedback",
    STAGE_CLOSE: "session_complete",
}

_mermaid_cache: str | None = None


@router.get("/v1/graph/mermaid")
async def graph_mermaid(_claims: dict = Depends(require_internal_admin)) -> dict:
    """The compiled agent graph as a Mermaid flowchart + the stage→node id map."""
    global _mermaid_cache
    if _mermaid_cache is None:
        try:
            _mermaid_cache = get_service().engine.graph.get_graph().draw_mermaid()
        except Exception as exc:  # noqa: BLE001
            logger.exception("flow.mermaid_error")
            raise HTTPException(500, f"could not render graph: {exc}")
    return {"mermaid": _mermaid_cache, "stage_to_node": STAGE_TO_NODE}


@router.get("/v1/graph")
async def graph_structure(_claims: dict = Depends(require_internal_admin)) -> dict:
    """The compiled agent graph as structured nodes + edges — the same object mermaid
    renders, handed over so a client can draw it properly instead of parsing a diagram.

    READ-ONLY by nature: the arc is compiled in ``app/graph/build_graph.py`` and routing
    is code predicates over typed state. There is no write side here and there must not
    be — an operator rewiring the governed coaching arc from a canvas is exactly what the
    safety model forbids.

    Note the edge count: conditional edges fan out to EVERY possible target (profile_read
    dispatches to every stage; every stage chains to every next stage), so this is a dense
    graph, not a line. ``conditional`` and ``label`` let a client draw the spine and reveal
    the rest on demand rather than rendering a hairball.
    """
    try:
        g = get_service().engine.graph.get_graph()
    except Exception as exc:  # noqa: BLE001
        logger.exception("flow.graph_error")
        raise HTTPException(500, f"could not read graph: {exc}")
    return {
        # dict order is the order nodes were added in build_graph — i.e. the arc order.
        "nodes": [{"id": n} for n in g.nodes],
        "edges": [
            {
                "source": e.source,
                "target": e.target,
                "label": getattr(e, "data", None) or "",
                "conditional": bool(getattr(e, "conditional", False)),
            }
            for e in g.edges
        ],
        "stage_to_node": STAGE_TO_NODE,
    }


def _assert_owner(session_id: str, claims: dict) -> None:
    """404 unless the session exists AND belongs to the caller.

    Org tenancy alone is not enough here: transcripts (and stage/safety
    flags) are content, and a colleague in the same org must not be able to
    read them by enumerating session ids. Same pattern and detail strings as
    sessions.py — user id ONLY from the JWT, ownership checked against the
    (already org-scoped) conversation doc."""
    if not auth_enabled():
        return  # tokenless dev mode: there is no identity to scope by
    user_id = user_id_from_claims(claims)
    if not user_id:
        raise HTTPException(status_code=400, detail="user_id not found in JWT.")
    doc = conversation.get_session(session_id)
    if not doc or (doc.get("user_id") and doc.get("user_id") != user_id):
        raise HTTPException(status_code=404, detail="Session not found for this user.")


@router.get("/v1/sessions/{session_id}/stage")
async def session_stage(session_id: str, claims: dict = Depends(require_auth)) -> dict:
    """Current stage / active node / path for a session (from the checkpointer)."""
    _assert_owner(session_id, claims)
    try:
        state = get_service().engine.session_state(session_id) or {}
    except Exception as exc:  # noqa: BLE001
        logger.warning("flow.session_state_error", extra={"error": str(exc)})
        state = {}
    stage = state.get("stage") or ""
    return {
        "session_id": session_id,
        "stage": stage,
        "active_node": state.get("active_node") or "",
        "coaching_path": state.get("coaching_path") or "",
        "node_id": STAGE_TO_NODE.get(stage, ""),
        "safety_flag": state.get("safety_flag") or "",
        "handoff_ready": state.get("handoff_ready", False),
    }


@router.get("/v1/sessions/{session_id}/transcript")
async def session_transcript(session_id: str, claims: dict = Depends(require_auth)) -> dict:
    """Replay a session's turns from the checkpointed graph state (for the history panel)."""
    _assert_owner(session_id, claims)
    try:
        state = get_service().engine.session_state(session_id) or {}
    except Exception as exc:  # noqa: BLE001
        logger.warning("flow.transcript_error", extra={"error": str(exc)})
        state = {}
    history = state.get("history") or []
    stage = state.get("stage") or ""
    return {
        "session_id": session_id,
        "stage": stage,
        "coaching_path": state.get("coaching_path") or "",
        "node_id": STAGE_TO_NODE.get(stage, ""),
        "turns": [{"role": m.get("role", ""), "content": m.get("content", "")}
                  for m in history if isinstance(m, dict)],
        "count": len(history),
    }


# ─────────────────────────── Console: run one agent in isolation ───────────────────────────
_TEXT_FIELDS = ["next_question", "question", "action_response", "response", "message", "reply", "text"]


def _parse_envelope(raw: str):
    """Best-effort: return (reply_text, control_dict) from a JSON-envelope reply."""
    try:
        from json_repair import repair_json

        data = repair_json(raw or "", return_objects=True)
    except Exception:
        data = None
    if not isinstance(data, dict):
        return (raw or "").strip(), {}
    for f in _TEXT_FIELDS:
        v = data.get(f)
        if isinstance(v, str) and v.strip():
            return v.strip(), data
    return "", data


@router.get("/v1/agents")
async def list_agents(_claims: dict = Depends(require_internal_admin)) -> dict:
    """Agents runnable in the Console (those with a non-empty prompt)."""
    from app.graph.runtime import get_registry
    from app.llm.responses_client import model_for as resolve_model

    reg = get_registry()
    out = []
    for stage in STAGE_TO_NODE:
        if stage == STAGE_CLOSE:
            continue
        prompt = reg.get(stage)
        if not prompt:
            continue
        try:
            model = resolve_model(stage, reg.model_for(stage))
        except Exception:
            model = reg.model_for(stage) or ""
        out.append({"stage": stage, "model": model, "enabled": reg.is_enabled(stage), "size": len(prompt)})
    return {"agents": out}


@router.post("/v1/console/run")
async def console_run(body: dict = Body(...), _claims: dict = Depends(require_internal_admin)) -> dict:
    """Free-form single prompt run (Console 'Prompt' tab): system + user → live model."""
    from app.graph.runtime import get_client
    from app.llm.responses_client import model_for as resolve_model

    system = body.get("system") or "You are a helpful assistant."
    user = (body.get("user") or "").strip()
    if not user:
        raise HTTPException(400, "user prompt is required")
    try:
        model = resolve_model("", body.get("model") or "")
    except RuntimeError:
        # Free-form console runs have no Catalog row; fall back to the
        # configured cascade instead of requiring a global model override.
        from app import config as _config

        model = _config.MODEL_CASCADE[0]
    try:
        resp = await run_in_threadpool(
            get_client().generate, system, user, model, None, None, "console", "console", "console",
        )
    except Exception as exc:  # noqa: BLE001
        logger.exception("console.prompt_run_error")
        raise HTTPException(500, f"run failed: {exc}")
    reply, control = _parse_envelope(resp.text)
    return {
        "model": resp.model or model, "text": resp.text, "reply": reply or resp.text, "control": control,
        "prompt_tokens": resp.prompt_tokens, "completion_tokens": resp.completion_tokens, "cost_usd": resp.cost_usd,
    }


@router.post("/v1/agents/{stage}/run")
async def run_agent(stage: str, body: dict = Body(...), _claims: dict = Depends(require_internal_admin)) -> dict:
    """Run ONE agent against the live model with a given input (Console runner).

    Builds the same system prompt a graph turn would (guardrail + agent prompt +
    placeholder resolution) and calls the client directly — no graph, no checkpoint."""
    if stage not in STAGE_TO_NODE:
        raise HTTPException(404, f"unknown stage: {stage}")
    from app.graph.guardrails import build_system_prompt
    from app.graph.runtime import get_client, get_registry
    from app.llm.responses_client import model_for as resolve_model

    reg = get_registry()
    node_prompt = reg.get(stage)
    if not node_prompt:
        raise HTTPException(400, f"no prompt authored for {stage}")
    try:
        model = resolve_model(stage, reg.model_for(stage))
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(400, f"no model for {stage}: {exc}")

    user_text = (body.get("text") or "").strip()
    user_context = body.get("user_context") or {}
    history = body.get("history") or None
    coaching_path = body.get("coaching_path") or None

    try:
        system_prompt = build_system_prompt(reg.environment, node_prompt, coaching_path, user_context, None, stage)
        resp = await run_in_threadpool(
            get_client().generate, system_prompt, user_text, model, None, history, stage, "console", "console",
        )
    except Exception as exc:  # noqa: BLE001
        logger.exception("console.run_error")
        raise HTTPException(500, f"agent run failed: {exc}")

    reply, control = _parse_envelope(resp.text)
    return {
        "stage": stage, "model": resp.model or model,
        "reply": reply, "raw": resp.text, "control": control,
        "prompt_tokens": resp.prompt_tokens, "completion_tokens": resp.completion_tokens,
        "cost_usd": resp.cost_usd,
    }
