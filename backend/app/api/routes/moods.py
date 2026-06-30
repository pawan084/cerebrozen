from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.mood import MoodLog
from app.models.user import User
from app.schemas.content_data import MoodCreate, MoodOut
from app.services import nudges

router = APIRouter(prefix="/moods", tags=["moods"])


@router.get("", response_model=list[MoodOut])
async def list_moods(
    limit: int = 50,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    rows = await db.scalars(
        select(MoodLog).where(MoodLog.user_id == user.id).order_by(MoodLog.created_at.desc()).limit(limit)
    )
    return rows.all()


@router.post("", response_model=MoodOut, status_code=201)
async def create_mood(
    payload: MoodCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    log = MoodLog(user_id=user.id, **payload.model_dump())
    db.add(log)
    await db.flush()
    # Proactive: a rough mood may queue a supportive nudge.
    await nudges.schedule_contextual(db, user)
    await db.commit()
    await db.refresh(log)
    return log
