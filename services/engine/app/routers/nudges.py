"""Check-in nudge ops surface: dispatch + the delivery log.

``POST /v1/nudges/dispatch`` is the cron entry point — it scans every tenant's due
check-ins and sends a content-free nudge for each (see ``app/notifications.py``).
``GET /v1/nudges`` is the observability read: recent nudge deliveries for the
caller's org, signal-only (counts + batch ids, never a commitment body).

Auth required (``require_auth``; dev bypass only when ENV=local / AUTH_DEV_BYPASS).
Role-gating (who may trigger a dispatch) is the platform's contract, upstream.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, Query
from fastapi.concurrency import run_in_threadpool

from app import notifications
from app.auth import require_internal_admin

router = APIRouter()


@router.post("/v1/nudges/dispatch")
async def dispatch(_claims: dict = Depends(require_internal_admin)) -> dict:
    """Scan due check-ins and deliver a nudge for each. Returns counts only."""
    return await run_in_threadpool(notifications.dispatch)


@router.get("/v1/nudges")
async def nudges(
    limit: int = Query(100, ge=1, le=500),
    _claims: dict = Depends(require_internal_admin),
) -> dict:
    """Recent check-in nudge deliveries, newest first, across all tenants.

    Cross-tenant for the same reason as the safety queue (``routers/safety.py``): the
    sweep stamps each nudge with the USER's org, while this route's callers are our own
    operators, whose token carries ``org_id="internal"`` — so scoping to the caller's org
    showed an empty queue no matter how many nudges went out. Signal-only: ids and counts,
    never a body.
    """
    rows = await run_in_threadpool(notifications.list_nudges, limit, True)
    return {"armed": notifications.armed(), "count": len(rows), "nudges": rows}
