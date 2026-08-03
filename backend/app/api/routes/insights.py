from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.insight import Insight
from app.models.nudge import Nudge
from app.models.user import User
from app.schemas.content_data import InsightOut, NudgeOut
from app.services import insights
from app.services import trends as trends_service

router = APIRouter(tags=["insights"])


@router.get("/insights/weekly", response_model=InsightOut)
async def weekly_insights(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    return await insights.compute_weekly(db, user)


@router.get("/insights/trends")
async def trends(
    days: int = 30,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Day-by-day mood and sleep series for the Trends screen.

    Days with no data are absent rather than zero, `enough_data` gates every
    summary number, and the mood↔sleep link stays withheld (with a reason)
    until enough overlapping nights exist to mean something. See
    `app.services.trends` for why each of those is not a client concern.
    """
    return await trends_service.compute(db, user, days=days)


@router.get("/insights/digest", response_model=InsightOut | None)
async def latest_digest(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """The most recent weekly snapshot, or null before the first one exists.

    Distinct from `/insights/weekly`, which recomputes live: this is what the
    user was actually told, frozen at the time it was sent.
    """
    return await db.scalar(
        select(Insight)
        .where(Insight.user_id == user.id)
        .order_by(Insight.period.desc())
    )


@router.get("/insights/digest/history", response_model=list[InsightOut])
async def digest_history(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Every weekly snapshot, newest first."""
    return (
        await db.scalars(
            select(Insight)
            .where(Insight.user_id == user.id)
            .order_by(Insight.period.desc())
        )
    ).all()


@router.get("/insights/patterns")
async def pattern_dashboard(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Transparent AI memory: every learned statement, derived only from the
    user's own data with the supporting counts attached — visible, honest,
    and deletable via DELETE /users/me/memory."""
    return await insights.compute_patterns(db, user)


@router.get("/nudges", response_model=list[NudgeOut])
async def upcoming_nudges(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    rows = await db.scalars(
        select(Nudge)
        .where(Nudge.user_id == user.id, Nudge.status == "scheduled")
        .order_by(Nudge.scheduled_for.asc())
    )
    return rows.all()
