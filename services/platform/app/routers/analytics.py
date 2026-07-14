"""Coaching-activity ingest + HR aggregates with the k-anonymity floor.

Ingest: org members report their own beats (kind only) with their own JWT —
first-party, no third-party SDK, no content. Aggregates: every behavioral
metric is suppressed (null) when fewer than COHORT_FLOOR distinct people
contributed — enforced HERE, so no UI can render a small-cohort number."""

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import distinct, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app import config
from app.db import get_session
from app.deps import current_user, require_org_admin
from app.models import EVENT_KINDS, ActivityEvent, Org, User

router = APIRouter(tags=["analytics"])


class EventIn(BaseModel):
    kind: str


@router.post("/events/coaching", status_code=202)
async def report_event(
    body: EventIn,
    user: User = Depends(current_user),
    session: AsyncSession = Depends(get_session),
):
    if user.org_id is None:
        raise HTTPException(400, "internal staff produce no coaching activity")
    if body.kind not in EVENT_KINDS:
        raise HTTPException(400, f"kind must be one of {sorted(EVENT_KINDS)}")
    session.add(ActivityEvent(org_id=user.org_id, user_id=user.id, kind=body.kind))
    await session.commit()
    return {"ok": True}


async def _kind_stats(
    session: AsyncSession, org_id: str, kind: str, since: datetime
) -> tuple[int, int]:
    """(event count, distinct contributors) for one kind in the window."""
    row = (
        await session.execute(
            select(func.count(), func.count(distinct(ActivityEvent.user_id)))
            .select_from(ActivityEvent)
            .where(
                ActivityEvent.org_id == org_id,
                ActivityEvent.kind == kind,
                ActivityEvent.created_at >= since,
            )
        )
    ).one()
    return int(row[0]), int(row[1])


def _floored(value, contributors: int) -> dict:
    """A metric plus its suppression verdict. The floor compares CONTRIBUTORS
    (distinct people), not event counts — 200 events from 3 people is still
    3 people's behavior."""
    if contributors < config.COHORT_FLOOR:
        return {"value": None, "suppressed": True}
    return {"value": value, "suppressed": False}


@router.get("/orgs/me/analytics")
async def org_analytics(
    days: int = 30,
    user: User = Depends(require_org_admin),
    session: AsyncSession = Depends(get_session),
):
    if user.org_id is None:
        raise HTTPException(400, "internal staff have no org of their own")
    days = max(1, min(days, 365))
    since = datetime.now(timezone.utc) - timedelta(days=days)
    org = (
        await session.execute(select(Org).where(Org.id == user.org_id))
    ).scalar_one()

    started_n, started_u = await _kind_stats(session, org.id, "session_started", since)
    completed_n, completed_u = await _kind_stats(session, org.id, "session_completed", since)
    saved_n, saved_u = await _kind_stats(session, org.id, "action_saved", since)
    done_n, done_u = await _kind_stats(session, org.id, "action_completed", since)

    active_users = (
        await session.execute(
            select(func.count(distinct(ActivityEvent.user_id))).where(
                ActivityEvent.org_id == org.id, ActivityEvent.created_at >= since
            )
        )
    ).scalar_one()

    # Rates inherit the WEAKEST cohort of their components: a rate over a
    # suppressed numerator or denominator would leak what suppression hid.
    session_rate_cohort = min(started_u, completed_u)
    action_rate_cohort = min(saved_u, done_u)

    # Seat counts are administrative facts (the People roster already shows
    # them person-by-person) — the floor governs BEHAVIOR, not administration.
    seats_active = (
        await session.execute(
            select(func.count())
            .select_from(User)
            .where(User.org_id == org.id, User.is_active)
        )
    ).scalar_one()

    return {
        "window_days": days,
        "cohort_floor": config.COHORT_FLOOR,
        "seats": {"total": org.seats_total, "active_members": seats_active},
        "metrics": {
            "active_coaching_users": _floored(active_users, active_users),
            "sessions_started": _floored(started_n, started_u),
            "sessions_completed": _floored(completed_n, completed_u),
            "session_completion_rate": _floored(
                round(completed_n / started_n, 3) if started_n else 0.0,
                session_rate_cohort,
            ),
            "actions_saved": _floored(saved_n, saved_u),
            "actions_completed": _floored(done_n, done_u),
            "action_completion_rate": _floored(
                round(done_n / saved_n, 3) if saved_n else 0.0,
                action_rate_cohort,
            ),
        },
    }
