"""The repeat-user check-in 7-day scheduler — pure, no I/O, no LLM.

Eligibility is decided in CODE, not in the prompt: this module decides *which* prior-session
actions are due for a check-in, so the graph only invokes the check-in agent when there is
something to close the loop on. Moving the gate out of the prompt is the whole point — an
LLM asked to remember a 7-day window will eventually not.

The rules (R1–R6) are spelled out in full below, which is the durable record. They
originate in the first client's internal spec; that document is theirs and is deliberately
not named here.

Rules implemented:
  R1 — 7-day window: a session's actions are eligible once
       ``session_date + due_days <= today`` (compared as calendar dates, so the
       check-in fires on the day that is `due_days` days AFTER the session — e.g.
       Day-1 actions become eligible on Day-8 for due_days=7, matching BRD Ex. A).
  R2 — batching: ALL overdue sessions are returned together, so the caller runs a
       single consolidated check-in covering every batch whose window has passed.
  R3 — one-time-only: actions whose `session_id` is in `checked_in_sessions` are
       permanently excluded.
  R4 — current-session exclusion: actions from `current_session_id` are excluded.
  R5/R6 — first-session / empty: a user with no eligible prior actions yields an
       empty list (→ caller treats as NOT due → no check-in).

Skipped/deleted actions (status == "skipped" or "deleted") are never eligible.
"""

from __future__ import annotations

from datetime import date, datetime
from typing import Any, Dict, Iterable, List, Optional


def _action_date(action: Dict[str, Any]) -> Optional[date]:
    """The calendar date the action's session was held. Prefers the explicit
    ``session_date`` stamp (YYYY-MM-DD); falls back to the action's write
    timestamp ``ts`` (ISO-8601) for actions stored before session_date existed.
    Returns None when neither parses (such an action is never eligible)."""
    raw = action.get("session_date") or action.get("ts")
    if not raw:
        return None
    text = str(raw).strip()
    # Fast path: a bare YYYY-MM-DD (the session_date stamp).
    try:
        return date.fromisoformat(text[:10])
    except ValueError:
        pass
    # Fall back to a full ISO-8601 datetime (the ts stamp), tolerating a trailing Z.
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00")).date()
    except ValueError:
        return None


def eligible_checkin_actions(
    actions: Iterable[Dict[str, Any]],
    today: date,
    current_session_id: str = "",
    due_days: int = 7,
    checked_in_sessions: Iterable[str] = (),
) -> List[Dict[str, Any]]:
    """Return the prior-session actions that are due for a check-in today.

    The returned list preserves input order and spans every overdue session
    (batching, R2). An empty list means "no check-in is due" — the caller routes
    straight on to coaching/intake.
    """
    checked_in = set(checked_in_sessions or ())
    eligible: List[Dict[str, Any]] = []
    for a in actions or []:
        if not isinstance(a, dict):
            continue
        if a.get("status") in {"skipped", "deleted"}:
            continue
        sid = a.get("session_id") or ""
        if sid and sid == current_session_id:  # R4: not this session's own actions
            continue
        if sid and sid in checked_in:  # R3: already closed, permanently excluded
            continue
        d = _action_date(a)
        if d is None:
            continue
        # R1: eligible once due_days have elapsed since the session date.
        if (today - d).days >= due_days:
            eligible.append(a)
    return eligible


def eligible_session_ids(eligible_actions: Iterable[Dict[str, Any]]) -> List[str]:
    """The distinct session_ids covered by an eligible-actions list, sorted for
    stable output. These are the batches the check-in will mark complete (R3)."""
    return sorted({a.get("session_id", "") for a in eligible_actions if a.get("session_id")})
