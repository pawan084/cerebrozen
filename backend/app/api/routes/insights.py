from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.nudge import Nudge
from app.models.user import User
from app.schemas.content_data import InsightOut, NudgeOut
from app.services import insights

router = APIRouter(tags=["insights"])


@router.get("/insights/weekly", response_model=InsightOut)
async def weekly_insights(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    return await insights.compute_weekly(db, user)


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
