import uuid

from fastapi import APIRouter, Depends, HTTPException, Response, status
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


@router.delete("/{mood_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_mood(
    mood_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Remove one check-in.

    A check-in is one tap, so a mis-tap is one tap too, and until now there was
    no way back — you could delete a journal entry or a remembered note, but a
    mood you logged by accident was permanent. That also made honest insights
    worse: a stray "Anxious" sits in the 60-day window that patterns and the
    weekly read are computed from.

    Scoped to the caller, and a 404 for anyone else's row.
    """
    row = await db.scalar(
        select(MoodLog).where(MoodLog.id == mood_id, MoodLog.user_id == user.id)
    )
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    await db.delete(row)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
