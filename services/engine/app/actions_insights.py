"""Actions & Insights read path — powers the right-panel tabs and resume render.

Separate from the builders that WRITE actions/insights (``app/graph/builders.py``
→ ``app/stores/agentic.py``) and from the coaching turn path: this module only
READS the per-user agentic doc and shapes it into the render model the frontend
consumes — scoped to ONE session so a new session doesn't surface a prior
session's items.

Data model: actions/insights accumulate per *user* (append-only, deduped) so
``profile_read`` keeps full cross-session continuity; each item is stamped with
the originating ``session_id``. This endpoint filters to ``session_id`` while the
store stays per-user. (A future "all sessions" read just drops the filter.)

Response shape:
    { session_id, data_present, version, actions, insights, [message] }

with a stable ``id`` on every item and a ``version`` (item count, monotonic
since the store only appends) driving a weak ETag for cheap 304 polling.
"""

from __future__ import annotations

import hashlib
import logging
import re
from typing import Any, Dict, List, Optional

from app.roi_metrics import get_roi_metrics
from app.stores import agentic

logger = logging.getLogger("cerebrozen.actions_insights")

# Returned to the UI when a session has produced no actions/insights yet.
NO_DATA_MESSAGE = "no data present"


def _norm_key(text: Any) -> str:
    """Normalise an action full_text / insight title for dedup: trim, lowercase,
    collapse internal whitespace."""
    return re.sub(r"\s+", " ", str(text or "").strip().lower())


def _stable_id(text: Any) -> str:
    """Deterministic 12-char id from the normalised dedup key — stable across
    calls so the UI keys on it and never renders the same item twice."""
    return hashlib.sha1(_norm_key(text).encode("utf-8")).hexdigest()[:12]


def _shape(
    items: List[Dict[str, Any]],
    key: str,
    session_id: str,
    id_field: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """Keep items belonging to ``session_id`` with a non-empty ``key``, skipping
    ones flagged ``status == "deleted"``; surface the stored ``id_field`` (falling
    back to a stable id derived from ``key``) as ``id`` and dedup by it."""
    out: List[Dict[str, Any]] = []
    seen: set = set()
    for it in items or []:
        if not isinstance(it, dict) or not it.get(key):
            continue
        if it.get("session_id") != session_id:
            continue
        if it.get("status") == "deleted":
            continue
        _id = (id_field and it.get(id_field)) or _stable_id(it.get(key))
        if _id in seen:
            continue
        seen.add(_id)
        item_out: Dict[str, Any] = {"id": _id, **it}
        # Coerce legacy roi_metric (single string from old Mongo docs) to
        # roi_metrics (list) so the UI always receives a consistent list shape.
        if "roi_metric" in item_out and "roi_metrics" not in item_out:
            legacy = item_out.pop("roi_metric")
            item_out["roi_metrics"] = [legacy] if legacy else []
        elif "roi_metric" in item_out:
            del item_out["roi_metric"]
        out.append(item_out)
    return out


class ActionsInsightsService:
    """Reads + shapes a user's actions/insights for ONE session's panel UI."""

    def get(self, user_id: str, session_id: str) -> Dict[str, Any]:
        """Return the render model for ``(user_id, session_id)``.

        Never raises for missing data — an unknown user/session simply has no
        data (``data_present=False``), which the panel renders as the empty state.
        """
        doc = agentic.load(user_id)

        actions = _shape(doc.get("actions", []), "full_text", session_id, id_field="action_id")
        insights = _shape(doc.get("insights", []), "insight_title", session_id, id_field="insight_id")
        present = bool(actions or insights)
        version = len(actions) + len(insights)

        payload: Dict[str, Any] = {
            "session_id": session_id,
            "data_present": present,
            "version": version,
            "actions": actions,
            "insights": insights,
        }
        # Only include the Development-Area catalogue when there are actions whose
        # roi_metric picker needs it (parsed from the prompt).
        if actions:
            payload["available_roi_metrics"] = get_roi_metrics()
        if not present:
            payload["message"] = NO_DATA_MESSAGE

        logger.info(
            "actions_insights.served",
            extra={
                "session_id": session_id,
                "user_id": user_id,
                "actions": len(actions),
                "insights": len(insights),
                "version": version,
            },
        )
        return payload


_service: Optional[ActionsInsightsService] = None


def get_actions_insights_service() -> ActionsInsightsService:
    global _service
    if _service is None:
        _service = ActionsInsightsService()
    return _service
