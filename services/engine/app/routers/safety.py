"""Safety ops surface: the crisis-escalation queue (signal-only, org-scoped).

Read-only. Every row is a SIGNAL that the crisis screen fired for a user in a
session — never the disclosure (see ``app/safety/escalation.py``). The queue exists
so an operator can see *that* escalations are happening and whether the designated
contact was reached, without ever reading what anyone said. "Counts, never content"
enforced at the storage layer, not by this projection.

Two different audiences, so two different gates — this file is where the distinction is
easiest to get wrong, and the cost of getting it wrong runs in one direction only:

* ``/v1/safety/escalations`` — INTERNAL_ADMIN. It is an operator queue. It is org-scoped,
  but org-scoping is not the point: the rows name which *employees* hit the crisis screen,
  so an org_admin (their HR) must not read it either. That is not "counts, never content" —
  it is worse, because the count is a person.
* ``/v1/safety/helplines`` — ANY authenticated user, deliberately. It backs a crisis
  screen. Gating it behind a role would deny someone in crisis a phone number, which is
  the single worst thing in this codebase. Do not "tidy" this into the line above.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.concurrency import run_in_threadpool

from app.auth import require_auth, require_internal_admin
from app.safety.escalation import acknowledge, health, list_escalations
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
    status: str = Query("open", pattern="^(open|resolved|all)$"),
    _claims: dict = Depends(require_internal_admin),
) -> dict:
    """The crisis-escalation queue, newest first (signal-only), across all tenants.

    `status` defaults to **open**: a queue whose default view includes everything ever
    handled is a queue nobody reads, and this is the one an operator must actually read.

    CROSS-TENANT, deliberately. `escalate` stamps each record with the CUSTOMER's org,
    while this route's callers are CereBroZen's own operators, whose token carries
    `org_id="internal"` because they belong to no customer org. Scoping the read to the
    caller's org therefore matched nothing, ever: a record was written for every crisis and
    the queue showed zero, with the `armed` pill still reading healthy. The operators on
    this route are the people who respond, so they see every tenant's rows; each row carries
    `org_id` so they can tell whose it is.

    That reach is the reason `require_internal_admin` above is load-bearing rather than
    decorative: an org_admin reaching this would now see OTHER customers' employees. It is
    still signal-only — who tripped the screen, never what they said.

    Also returns whether escalation is *armed* (a contact endpoint is configured) and
    whether the crisis classifier is on — a safety feature that is silently off must be
    visible to the operator, not just at ``/health``.
    """
    rows = await run_in_threadpool(list_escalations, limit, status, True)
    h = health()
    return {
        "armed": h["crisis_escalation_armed"],
        "classifier_enabled": h["crisis_classifier_enabled"],
        "status": status,
        "count": len(rows),
        "escalations": rows,
    }


@router.post("/v1/safety/escalations/{record_id}/ack")
async def acknowledge_escalation(
    record_id: str,
    claims: dict = Depends(require_internal_admin),
) -> dict:
    """Mark an escalation handled.

    The queue was read-only, so it never drained: an operator who had already reached
    someone had no way to say so, and the row stayed open forever.

    A STATUS and a NAME. There is deliberately no note, no reason and no outcome field: the
    reference's admin renders an "Excerpt" column with the flagged text, and a free-text
    "what happened" box is that same leak wearing a different hat. What was said stays
    between the person and their coach (CLAUDE.md rule 5).

    Cross-tenant like the read above, and for the same reason: an operator who can see a
    row must be able to clear it, or the queue never drains.

    404 for an unknown record — an operator only ever reaches this route having read an id
    out of the queue, so there is nothing to probe for.
    """
    ok = await run_in_threadpool(
        acknowledge, record_id, actor=(claims or {}).get("sub", "unknown"), all_orgs=True,
    )
    if not ok:
        raise HTTPException(404, "no such escalation")
    return {"status": "acknowledged", "id": record_id}
