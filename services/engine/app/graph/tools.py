"""Data-layer tools (no LLM round-trip) + output parsing helpers.

- profile_read: the deck's "User Profile Retrieval (Engineering agent — get info
  from DB)" as a plain function.
- crisis_screen: a rule pre-filter for the first cut (spec open-question #3), so a
  continuing turn keeps exactly ONE critical-path LLM call.
- parse_control: tolerant extraction of the user-facing reply + control fields
  (handoff_ready, coaching_path) from a node's JSON output.
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any, Dict, Optional, Tuple

from app import trace
from app.stores.mongo import read_user_context

logger = logging.getLogger("cerebrozen.tools")

# --- profile_read (tool, no LLM) --------------------------------------------


def profile_read(user_id: str, session_id: str = "") -> Dict[str, Any]:
    """Deterministic Mongo read into user_context. Never raises."""
    ctx = read_user_context(user_id, session_id)
    # Guarantee the repeat-user check-in gate's inputs are ALWAYS defined, even when
    # Mongo is unavailable or has no doc for this user (read_user_context returns {}
    # early in that case). Without these, the {userRepeatFresh}/{previousUserActions}
    # placeholders render as literal tokens and the eligibility gate can't evaluate;
    # defaulting to a fresh/empty user degrades cleanly to NOT_ELIGIBLE → challenge.
    ctx.setdefault("userRepeatFresh", "fresh")
    ctx.setdefault("previousUserActions", [])
    ctx.setdefault("previousUserContext", {})
    # Cross-session memory — verbatim prior-session transcript ("" when first-time
    # or Mongo is down) so the {pastConversation} placeholder never renders literally.
    ctx.setdefault("pastConversation", "")
    # The 7-day scheduler outputs (see stores/mongo.read_user_context). Default to
    # "not due / empty" so the check-in is skipped when there's no agentic doc.
    ctx.setdefault("checkinDue", False)
    ctx.setdefault("checkinEligibleActions", [])
    ctx.setdefault("checkinSessionIds", [])
    # Placeholders that have no data source yet — default to "" so the resolver
    # never surfaces a literal {token} in the system prompt.
    ctx.setdefault("timeAvailable", "")        # captured by challenge_context_agent per session
    ctx.setdefault("session_goal", "")         # captured by challenge_context_agent per session
    ctx.setdefault("presenting_issue_summary", "")  # captured by challenge_context_agent per session
    ctx.setdefault("previousUserInsights", []) # populated from agentic insights store in mongo.py
    # Dynamic vars captured by agents — default to "" for a fresh user who hasn't
    # gone through the relevant agent yet; populated from dynamic_vars on return visits.
    ctx.setdefault("behavioral_intake_responses", "")  # captured by coaching_intake_agent
    # Coaching style chosen at challenge Step 8a — restored from dynamic_vars on return
    # visits (as {selected_style: ...}); MUST default to "" for a fresh user so the
    # {coaching_style_context} token renders empty instead of passing through as a
    # literal "{coaching_style_context}", which the prompt's field-presence gate would
    # read as a non-empty value and wrongly treat a first-timer as a repeat user.
    ctx.setdefault("coaching_style_context", "")  # captured by challenge_context_agent
    ctx.setdefault("committed_action", "")     # captured by CH coaching agent (blank for CIM/CBT)
    ctx.setdefault("committed_by_when", "")    # same as above
    # Populated by org/user config when custom prompts are configured; blank otherwise.
    ctx.setdefault("repeatingUserCustomPrompt", "")
    ctx.setdefault("customCoachingStylePrompt", "")
    ctx.setdefault("customBehavioralQuestionPrompt", "")
    # CH-specific defaults — prevent literal {token} in CH prompt when fields are absent
    ctx.setdefault("idp_competencies", "")        # from users.idp_competencies
    ctx.setdefault("deep_link_skill", "")        # from users.deep_link_skill
    ctx.setdefault("user_thinking_preference", "")  # from NBI (snake_case alias)
    ctx.setdefault("user_role_context", "")      # from intake userRoleContext alias
    ctx.setdefault("user_motivations", "")       # from intake userMotivations alias
    ctx.setdefault("session_continued", "")      # per-turn metadata (phase button press)
    ctx.setdefault("currentPhase", "")           # from CH coaching_progress.current_phase
    ctx.setdefault("competency_source", "")      # CH: org_framework | cerebrozen_framework
    logger.info(
        "profile_read",
        extra={"user_id": user_id, "keys": sorted(ctx.keys()), "llm": False},
    )

    # Log which registry-tracked variables were resolved and what their values
    # are after the full profile merge. This is the primary CloudWatch query
    # point for validating that captured variables survived into the next session.
    # Structured as a single log line so a CloudWatch Insights query like:
    #   filter event = "profile_read.registry_vars" | fields user_id, loaded, missing
    # gives an instant view of coverage per user.
    try:
        from app.request_context import request_id as _req_id_ctx
        from app.stores.variable_capture_registry import VariableCaptureRegistry
        _rid = _req_id_ctx.get("")
        reg = VariableCaptureRegistry.get()
        loaded_vars = {}
        missing_vars = []
        for var_name, cfg in reg.all_vars.items():
            if not cfg.capture_enabled:
                continue  # system-written — not expected via variables_set
            # For dot-notation vars (e.g. coaching_style_context.selected_style),
            # check the nested structure in ctx as well as the flat key.
            parts = var_name.split(".", 1)
            if len(parts) == 2:
                parent, child = parts
                value = (ctx.get(parent) or {}).get(child) if isinstance(ctx.get(parent), dict) else ctx.get(var_name)
            else:
                value = ctx.get(var_name)

            if value not in (None, "", [], {}):
                loaded_vars[var_name] = value
            else:
                missing_vars.append(var_name)

        logger.info(
            "profile_read.registry_vars",
            extra={
                "user_id": user_id,
                "session_id": session_id,
                "request_id": _rid,
                "loaded_count": len(loaded_vars),
                "missing_count": len(missing_vars),
                "loaded": loaded_vars,
                "missing": missing_vars,
            },
        )
    except Exception:  # noqa: BLE001 — diagnostic log must never affect a turn
        pass

    # Full extracted profile (NBI/DISC, identity, continuity) — gated trace so the
    # exact values are inspectable when debugging, off by default (PII/volume).
    trace.io("profile_read.extracted", user_id=user_id, profile_context=ctx)
    return ctx


# --- crisis screen (rule pre-filter, no LLM) --------------------------------
#
# The implementation moved to app/graph/crisis.py when it stopped being English-only —
# it now spans ~20 languages and two matching strategies, which is more than belongs in a
# grab-bag module. Re-exported here because this is the import path every caller and test
# already uses, and a safety function is the last thing that should churn its call sites.
from app.graph.crisis import crisis_screen  # noqa: E402,F401  (re-export)


# --- output parsing ----------------------------------------------------------

_FENCE_RE = re.compile(r"^```(?:json)?\s*|\s*```$", re.IGNORECASE | re.MULTILINE)
# User-facing text keys, most-specific first (first present non-empty wins). The
# bare "question" is LAST so agents using the specific keys are unaffected; it is
# the key CBT_coaching_agent emits its prompt under — without it the CBT coaching
# path returned an empty reply to the user (found in end-to-end testing).
_USER_TEXT_KEYS = ("response_to_user", "next_question", "clarifying_question", "message", "question", "response")
_DONE_STATUSES = {"complete", "done", "final", "handoff"}
# Values that mean "yes" when a control flag arrives as a STRING rather than a bool.
_TRUE_STRINGS = {"true", "yes", "y", "1"}


def _truthy(value: Any) -> bool:
    """Read a control flag that may be a bool OR a string.

    `bool("false")` is True — so a model that emits `"handoff_ready": "false"` was
    read as DONE and the router advanced the stage mid-arc. This is not hypothetical:
    json_repair, which salvages output truncated at the token ceiling, turns a cut-off
    `"handoff_ready": tru` into the STRING "tru" — which bool() also reads as True.
    Strings are parsed by value; anything unrecognised ("tru", "pending") is False,
    because an ambiguous flag must never advance the session."""
    if isinstance(value, str):
        return value.strip().lower() in _TRUE_STRINGS
    return bool(value)


def _repair(snippet: str) -> Optional[Dict[str, Any]]:
    """json_repair a malformed envelope. None when it isn't salvageable as a dict."""
    try:
        from json_repair import repair_json

        obj = json.loads(repair_json(snippet))
        return obj if isinstance(obj, dict) else None
    except Exception:  # noqa: BLE001
        return None


def _safe_json(text: str) -> Optional[Dict[str, Any]]:
    if not text:
        return None
    cleaned = _FENCE_RE.sub("", text).strip()
    try:
        obj = json.loads(cleaned)
        return obj if isinstance(obj, dict) else None
    except Exception:  # noqa: BLE001
        pass
    # Best-effort: grab the outermost {...} block.
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if 0 <= start < end:
        try:
            obj = json.loads(cleaned[start : end + 1])
            return obj if isinstance(obj, dict) else None
        except Exception:  # noqa: BLE001
            return _repair(cleaned[start : end + 1])
    # An envelope with NO closing brace: the model hit its token ceiling mid-JSON.
    # Without this, `end` is -1, the repair above never runs, and parse_control's
    # prose fallback hands the user the raw fragment — `{"response_to_user": "I hear`
    # rendered verbatim in the chat bubble. json_repair closes the truncation, so the
    # partial reply is delivered as text instead.
    # Guarded to text that IS an envelope (starts with "{"): prose that merely
    # contains a stray "{" repairs to `{}`, which would replace a real prose reply
    # with a blank bubble — worse than the leak. An empty repair falls through to
    # the prose fallback.
    if start == 0:
        obj = _repair(cleaned)
        return obj or None
    return None


def extract_variables(text: str) -> Dict[str, Any]:
    """Pull the node's `variables_set` block (intake captures role/coachability/
    style/etc.) so it can be merged into user_context for downstream placeholders."""
    obj = _safe_json(text)
    if not obj:
        return {}
    v = obj.get("variables_set")
    return v if isinstance(v, dict) else {}


def extract_progress(text: str) -> Dict[str, Any]:
    """Pull the coaching node's arc-progress out of its output so it can be
    persisted to state and re-injected next turn (survives history truncation).

    The core_coaching contract carries arc position in a `context_update` block
    (behavioral_intake_complete, current_question_number, selected_model,
    behavioral_context, …) plus a top-level `current_step`. Returns a flat dict of
    whatever is present; {} when the node emits no such block."""
    obj = _safe_json(text)
    if not obj:
        return {}
    progress: Dict[str, Any] = {}
    cu = obj.get("context_update")
    if isinstance(cu, dict):
        progress.update(cu)
    step = obj.get("current_step")
    if isinstance(step, str) and step.strip():
        progress["current_step"] = step.strip()
    return progress


def parse_control(text: str) -> Tuple[str, bool, Optional[str]]:
    """(reply_text, handoff_ready, coaching_path) from a node's output.

    Falls back to the raw text as the reply if it isn't JSON (so a prompt that
    emits prose still surfaces something to the user)."""
    obj = _safe_json(text)
    if obj is None:
        # Non-JSON prose — surface it as-is.
        return text.strip(), False, None

    reply = ""
    for key in _USER_TEXT_KEYS:
        val = obj.get(key)
        if isinstance(val, str) and val.strip():
            reply = val.strip()
            break

    handoff = _truthy(obj.get("handoff_ready")) or _truthy(obj.get("agent_complete"))
    status = str(obj.get("status", "")).strip().lower()
    if status in _DONE_STATUSES:
        handoff = True

    # The coaching path can arrive under either spelling (snake_case coaching_path
    # or camelCase coachingPath — different prompt revisions use different ones) and
    # either at the top level OR nested in context_update (the challenge_context_agent
    # prompt emits `context_update.coachingPath`). Check all four so a user-edited
    # prompt's key casing can never silently drop the routing decision (→ CIM).
    def _pick_path(d):
        if isinstance(d, dict):
            return d.get("coaching_path") or d.get("coachingPath")
        return None

    path = _pick_path(obj) or _pick_path(obj.get("context_update"))
    path = str(path).strip().upper() if path else None
    if path not in {"CIM", "CBT", "CH"}:
        path = None

    # Note: when the JSON is a pure control envelope (no user-text key), reply is
    # "" — we deliberately do NOT dump the raw JSON to the user. The graph chains
    # to the next stage on a control-only handoff so the user gets a real reply.
    return reply, handoff, path
