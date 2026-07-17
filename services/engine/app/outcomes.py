"""Coaching outcomes — a content-free progress read from a user's own action history.

Every competitor sells "measurable outcomes," and the data to do it honestly already sits in
the agentic doc: actions tagged to a Development Area (an ROI-metric catalogue label), their
status, and which sessions' check-ins were completed. This turns that into a progress view
WITHOUT inventing a psychometric score or exposing a body — counts, Development-Area labels,
and a follow-through rate, for the user's own eyes.

Two deliberate boundaries:

- **Counts, never content.** Nothing here reads an action's text, commitment body, or
  transcript. Development-Area labels come from the ROI catalogue, not from the user.
- **Follow-through, not a rating.** "Progress" is whether committed actions belong to a
  session whose check-in was completed — an engagement signal, not a durable per-person
  score. The kind of standing rating that WOULD be a score (`ic_profile`) is exactly what
  regulated-workplace mode gates off; this does not resurrect it.

`agentic.load` is org-scoped, and the endpoint is user-scoped from the JWT, so a caller only
ever sees their own progress.
"""

from __future__ import annotations

from typing import Any, Dict, List

SPEC = "cerebrozen.outcomes/v1"


def _roi_labels(action: Dict[str, Any]) -> List[str]:
    """The Development Area(s) on an action, tolerant of the legacy single-value field."""
    roi = action.get("roi_metrics")
    if isinstance(roi, list):
        return [str(r) for r in roi if r]
    if roi:
        return [str(roi)]
    legacy = action.get("roi_metric")
    return [str(legacy)] if legacy else []


def progress(user_id: str) -> Dict[str, Any]:
    """A content-free progress snapshot for one user, from their own agentic doc."""
    from app.stores.agentic import load

    doc = load(user_id)
    actions = doc.get("actions", []) or []
    completed_sessions = set(doc.get("checkin_complete_sessions", []) or [])

    by_status: Dict[str, int] = {}
    by_area: Dict[str, Dict[str, int]] = {}
    committed = 0   # actions the user chose to keep ("saved")
    followed = 0    # ...that belong to a session whose check-in was completed

    for a in actions:
        status = str(a.get("status", "active"))
        by_status[status] = by_status.get(status, 0) + 1
        if status == "deleted":
            continue
        for area in _roi_labels(a):
            slot = by_area.setdefault(area, {"actions": 0, "committed": 0})
            slot["actions"] += 1
            if status == "saved":
                slot["committed"] += 1
        if status == "saved":
            committed += 1
            if a.get("session_id") in completed_sessions:
                followed += 1

    return {
        "spec": SPEC,
        "sessions_completed": int(doc.get("sessions_completed", 0) or 0),
        "actions_total": len(actions),
        "by_status": by_status,
        "development_areas": [
            {"area": area, **counts}
            for area, counts in sorted(by_area.items())
        ],
        "follow_through": {
            "committed": committed,
            "checked_in": followed,
            # None, not 0, when nothing is committed yet — an honest "no data" vs a real zero.
            "rate": round(followed / committed, 3) if committed else None,
        },
    }
