"""Safety ops surface: the crisis-escalation queue (signal-only, org-scoped).

Read-only. Every row is a SIGNAL that the crisis screen fired for a user in a
session — never the disclosure (see ``app/safety/escalation.py``). The queue exists
so an operator can see *that* escalations are happening and whether the designated
contact was reached, without ever reading what anyone said. "Counts, never content"
enforced at the storage layer, not by this projection.

Auth is required (``require_auth``; dev bypass only when ENV=local / AUTH_DEV_BYPASS,
same as every other engine route). The queue is scoped to the caller's org.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from fastapi.concurrency import run_in_threadpool

from app.auth import require_auth
from app.safety.escalation import health, list_escalations

router = APIRouter()


@router.get("/v1/safety/escalations")
async def escalations(
    limit: int = Query(100, ge=1, le=500),
    _claims: dict = Depends(require_auth),
) -> dict:
    """The crisis-escalation queue for the caller's org, newest first (signal-only).

    Also returns whether escalation is *armed* (a contact endpoint is configured) and
    whether the crisis classifier is on — a safety feature that is silently off must be
    visible to the operator, not just at ``/health``.
    """
    rows = await run_in_threadpool(list_escalations, limit)
    h = health()
    return {
        "armed": h["crisis_escalation_armed"],
        "classifier_enabled": h["crisis_classifier_enabled"],
        "count": len(rows),
        "escalations": rows,
    }
