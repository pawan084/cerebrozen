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


#: Hour → the phrase the coach greets with. The ENGINE owns this vocabulary, not the
#: clients: the web app and the phone would otherwise each invent their own wording and
#: `coaching_intake_agent` would greet two people differently at the same hour. Clients send
#: a number; the words are ours.
#:
#: Bands cover the whole clock. The prompt names only three ("early morning, late afternoon,
#: or late evening") as EXAMPLES of the register, so the rest are filled in to match — a
#: 3am greeting has to say something, and "early morning" is not it.
_TIME_BANDS = (
    (5, "the middle of the night"),   # 0-4
    (8, "early morning"),             # 5-7
    (12, "the morning"),              # 8-11
    (14, "midday"),                   # 12-13
    (17, "the afternoon"),            # 14-16
    (19, "late afternoon"),           # 17-18
    (22, "the evening"),              # 19-21
    (24, "late evening"),             # 22-23
)

#: What `{time}` resolves to when the client did not send an hour — an OLD client, or one
#: that chooses not to. It must not blank: the prompt says "greet the user based on {time}"
#: and a blank there is what made the model invent an hour in the first place. The value
#: carries its own instruction, because the sentence around it belongs to the coach.
TIME_UNKNOWN = "an unknown time of day — greet warmly without naming one"


def time_of_day(local_hour: Optional[int]) -> str:
    """The greeting phrase for a caller's local hour, or TIME_UNKNOWN.

    Defensive about the input on purpose. The schema bounds it (0-23), but this also runs
    for turns replayed from stored state and for anything a future caller passes, and the
    failure mode of a bad hour must be "greet neutrally", never a KeyError inside a turn.
    """
    if not isinstance(local_hour, int) or isinstance(local_hour, bool):
        return TIME_UNKNOWN
    if not 0 <= local_hour <= 23:
        return TIME_UNKNOWN
    for below, phrase in _TIME_BANDS:
        if local_hour < below:
            return phrase
    return TIME_UNKNOWN  # unreachable: the bands cover 0-23


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
    # {time} — always set, never blank. coaching_intake says "Greet the user based on
    # {time}" five times; before 2026-07-17 nothing resolved it, so the model was told to
    # vary by time of day and handed an empty string, and it guessed. A wrong "Good
    # evening" at 9am is small and avoidable. The hour comes from the CLIENT (the only
    # party that knows it — no timezone on the platform, and `region` is multi-zone for
    # US/CA/AU/EU); an absent one degrades to a phrase that tells the coach not to guess.
    if "time" not in merged:
        hour = merged.get("local_hour")
        if hour is None:
            hour = qc.get("local_hour")
        merged["time"] = time_of_day(hour)
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
