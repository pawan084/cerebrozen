import datetime as dt
import uuid

from fastapi import APIRouter, Depends, Header, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.mood import MoodLog
from app.models.user import User
from app.schemas.content_data import MoodCreate, MoodOut
from app.services import idempotency, nudges

router = APIRouter(prefix="/moods", tags=["moods"])


@router.get("", response_model=list[MoodOut])
async def list_moods(
    limit: int = 50,
    since: dt.datetime | None = None,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Recent check-ins, newest first.

    `since` is the sync cursor: pass the newest `created_at` you already hold
    and only later rows come back. A client that has been offline for a week
    then costs one small request instead of re-downloading the whole history
    (and re-rendering it) on every reconnect.
    """
    query = select(MoodLog).where(MoodLog.user_id == user.id)
    if since is not None:
        if since.tzinfo is None:
            since = since.replace(tzinfo=dt.timezone.utc)
        query = query.where(MoodLog.created_at > since)
    rows = await db.scalars(query.order_by(MoodLog.created_at.desc()).limit(min(limit, 500)))
    return rows.all()


@router.post("", response_model=MoodOut, status_code=201)
async def create_mood(
    payload: MoodCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
):
    """Log one check-in.

    Optionally carries an `Idempotency-Key`, which the Android offline queue
    sets so a write that was retried after a crash lands once, not twice.
    """
    key = idempotency.normalise_key(idempotency_key)
    body = payload.model_dump()
    cached = await idempotency.replay(db, user.id, key, "POST /moods", body)
    if cached is not None:
        return cached[1]

    log = MoodLog(user_id=user.id, **body)
    db.add(log)
    await db.flush()
    # Proactive: a rough mood may queue a supportive nudge.
    await nudges.schedule_contextual(db, user)
    await db.commit()
    await db.refresh(log)
    stored = MoodOut.model_validate(log)
    await idempotency.record(db, user.id, key, "POST /moods", body, 201, stored.model_dump(mode="json"))
    return stored


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
