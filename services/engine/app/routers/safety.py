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
from app.safety.helplines import for_region, regions

router = APIRouter()


@router.get("/v1/safety/helplines")
async def helplines(
    region: str = Query("", max_length=8),
    _claims: dict = Depends(require_auth),
) -> dict:
    """The crisis helplines to show, for ``region``.

    The engine owns this list because it is safety code (ARCHITECTURE.md §Cross-stack
    contracts: "never hardcoded in clients"). Clients render what this returns; they do
    not carry a country's numbers of their own.

    ``region`` is the caller's own preference (``User.region``, falling back to their
    org's ``Org.crisis_region``) — both resolved on the platform, which owns those
    fields. It is deliberately NOT a signed claim: this is a public directory lookup,
    not an authorisation decision. Nothing is disclosed by asking for another country's
    numbers, and someone travelling has a real reason to.

    Never empty and never 4xx on an unknown region — see ``app/safety/helplines.py``.
    A client that asks for nonsense still gets something dialable.
    """
    resolved = (region or "").strip().upper()
    rows = for_region(resolved)
    return {
        "requested": resolved,
        # Whether we had anything local, so a client can say "showing international"
        # rather than implying these numbers are the person's own country's.
        "localized": resolved in regions(),
        "regions": regions(),
        "helplines": rows,
    }


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
