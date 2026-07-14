"""The always-on guardrail layer (environment_system_agent).

Implemented as a *wrapper*, not an extra LLM call: the environment/guardrail
prompt + a path-specific identity line are prepended to every node's prompt,
then placeholders are resolved from user_context. This is how "safety · privacy
· ethics · tone · path-specific identity" stay always-on for free.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, Optional

from app import config
from app.rag.placeholders import PLACEHOLDER_RE, PlaceholderResolver
from app.request_context import request_id as _ctx_request_id

logger = logging.getLogger("cerebrozen.guardrails")

# Path-specific identity (deck: "applies CIM/CH/CBT-specific identity based on
# the path"). coaching_path is None until challenge_context sets it, so the
# entry stages use the neutral identity automatically.
# The coach's name comes from config so a second client is a config change, not a fork.
_B = config.BRAND_NAME

IDENTITY: Dict[str, str] = {
    "default": f"You are {_B}, a warm, incisive professional coach.",
    "CIM": f"You are {_B} in Coaching-in-the-Moment mode: surface the real "
    "issue, widen perspective, and move the user toward one concrete action.",
    "CBT": f"You are {_B} in CBT mode: fast, lever-based reframing of unhelpful "
    "thinking.",
    "CH": f"You are {_B} in Capability mode: goals, commitments, and "
    "development-area mapping.",
}


# Deterministic-flow note. This is graph *structure*, so it lives in code (not in
# the editable workbook): no orchestrator, no loop-back, advance via handoff_ready.
FLOW_NOTE = (
    "EXECUTION FLOW: You run inside a fixed top-to-bottom pipeline with NO "
    "orchestrator and no loop-back. When your stage's task is complete, output "
    '"handoff_ready": true (and "agent_complete": true) — the engine then advances '
    "to the next stage automatically; you never choose it. Ignore any "
    '"next_agent" field and any instruction to return to / hand back to an '
    "orchestrator."
)


def _rag_query_context(
    user_context: Dict[str, Any],
    coaching_path: Optional[str],
    query_context: Optional[Dict[str, Any]],
) -> Dict[str, Any]:
    """Flatten everything the placeholder resolver needs into one context dict:
    the profile fields (for {userName} etc.) PLUS the RAG query params the
    extraction layer reads (org_id, user_level, user_role, userRoleContext,
    user_message, conversation/conversation_history, session_goal,
    confirmed_competency, coaching_shift_summary, user_id, session_id). Derived
    from user_context where the names differ (orgId→org_id, level→user_level,
    userPosition→user_role, userRoleContext/user_role_context→userRoleContext)."""
    qc = query_context or {}
    merged: Dict[str, Any] = dict(user_context or {})
    merged.setdefault("org_id", user_context.get("orgId") or user_context.get("org_id", ""))
    merged.setdefault("user_level", user_context.get("level") or user_context.get("user_level", ""))
    merged.setdefault("user_role", user_context.get("userPosition") or user_context.get("user_role", ""))
    # {user_name} uses snake_case; context stores "name"/"userName" (camelCase).
    # Case-insensitive lookup won't match: "user_name" != "username", so alias explicitly.
    merged.setdefault("user_name", user_context.get("name") or user_context.get("userName", ""))
    # userRoleContext: intake/profile field (camelCase from the API, or snake_case
    # user_role_context from Mongo — see stores/mongo.py), falling back to user_role.
    merged.setdefault(
        "userRoleContext",
        user_context.get("userRoleContext") or user_context.get("user_role_context") or merged.get("user_role", ""),
    )
    if coaching_path:
        merged["coaching_path"] = coaching_path
    # Live turn signals: the current message + a flat conversation string for
    # queries, the CH user-stated outcome (session_goal), and skill/competency.
    for key in (
        "user_message", "conversation", "conversation_history", "history_text", "user_id", "session_id",
        "session_goal", "skill_to_develop", "confirmed_competency",
        "coaching_shift_summary",
    ):
        if qc.get(key):
            merged[key] = qc[key]
    # The user's current challenge defaults to the current message when unset.
    # (session_goal's own fallback chain in extractors._PARAM_ALIASES already
    # reaches user_goal_challenge/user_message, so no separate default needed here.)
    merged.setdefault("user_goal_challenge", qc.get("user_message", ""))
    merged.setdefault("user_challenge", qc.get("user_message", ""))
    return merged


def build_system_prompt(
    environment_prompt: str,
    node_prompt: str,
    coaching_path: Optional[str],
    user_context: Dict[str, Any],
    query_context: Optional[Dict[str, Any]] = None,
    invoking_agent: str = "",
) -> str:
    """Compose guardrail constraints + flow note + the node's role prompt.

    The environment prompt is framed as *constraints*, not identity, and the node
    prompt is the authoritative role — otherwise the model can answer AS the
    environment agent (its prompt says "You are environment_system_agent") instead
    of the actual stage. Placeholders (context + RAG) are then resolved in one
    parallel pass; `query_context` carries the live turn signals RAG queries need.
    """
    identity = IDENTITY.get(coaching_path or "default", IDENTITY["default"])
    sections = []
    if environment_prompt:
        sections.append(
            "# OPERATING CONSTRAINTS (guardrails — apply these; this is context, "
            "NOT your identity)\n" + environment_prompt
        )
    sections.append("# EXECUTION FLOW\n" + FLOW_NOTE)
    sections.append(
        "# YOUR ROLE — you ARE the agent defined below. Respond ONLY as this agent, "
        "in its exact JSON schema. Do NOT respond as 'environment_system_agent' or "
        "emit an environment envelope.\n" + identity + "\n" + node_prompt
    )
    composed = "\n\n".join(sections)

    context = _rag_query_context(user_context or {}, coaching_path, query_context)
    if invoking_agent:
        context["invoking_agent"] = invoking_agent  # for RAG attribution logging
    # The active path lives in state, not user_context, so the env prompt's
    # {coachingPath} placeholder would otherwise render literally. _rag_query_context
    # already sets snake_case coaching_path; also expose the camelCase spelling.
    if coaching_path:
        context.setdefault("coachingPath", coaching_path)
    # Confirm which language will be sent to the LLM for this agent/turn.
    logger.info(
        "prompt.language_to_llm",
        extra={
            "invoking_agent": invoking_agent,
            "language_in_context": context.get("language"),
            "user_id": context.get("user_id", ""),
            "session_id": context.get("session_id", ""),
            "request_id": _ctx_request_id.get(""),
        },
    )
    resolver = PlaceholderResolver(context=context)
    try:
        return resolver.resolve_text(composed)
    except Exception:  # noqa: BLE001 — never let placeholder resolution break a turn
        # Last-resort sanitization: resolution itself blew up, so blank every
        # remaining {token} rather than hand the model (and thus possibly the
        # user) a prompt full of raw placeholders.
        logger.exception("guardrails.placeholder_resolution_failed")
        return PLACEHOLDER_RE.sub("", composed)
