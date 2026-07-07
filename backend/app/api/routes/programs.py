"""Program enrollment — the ref "PROGRAM · DAY 3 OF 7" journey card.

Enrolling starts a multi-day journey; the current day derives from the start
date (no advance/fail mechanics — showing up IS the program). One active
enrollment at a time; enrolling in another gently replaces it.
"""
import uuid

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db, utcnow
from app.core.deps import get_current_user
from app.models.content import ContentItem
from app.models.program import ProgramEnrollment
from app.models.user import User

router = APIRouter(prefix="/programs", tags=["programs"])


class EnrollIn(BaseModel):
    content_id: uuid.UUID
    days: int = Field(default=7, ge=1, le=60)


def _view(e: ProgramEnrollment) -> dict:
    elapsed = (utcnow().date() - e.started_at.date()).days
    day = min(e.days, elapsed + 1)
    return {
        "content_id": str(e.content_id),
        "title": e.title,
        "day": day,
        "days": e.days,
        "started_at": e.started_at,
        "completed": elapsed >= e.days,
    }


async def _active(db: AsyncSession, user: User) -> ProgramEnrollment | None:
    return await db.scalar(
        select(ProgramEnrollment)
        .where(ProgramEnrollment.user_id == user.id, ProgramEnrollment.active.is_(True))
        .order_by(ProgramEnrollment.started_at.desc())
    )


@router.get("/active")
async def active_program(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    e = await _active(db, user)
    return {"program": _view(e) if e else None}


@router.post("/enroll", status_code=201)
async def enroll(
    payload: EnrollIn,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    item = await db.get(ContentItem, payload.content_id)
    if item is None or item.kind != "program":
        raise HTTPException(status_code=404, detail="Program not found")
    rows = (
        await db.scalars(
            select(ProgramEnrollment).where(
                ProgramEnrollment.user_id == user.id, ProgramEnrollment.active.is_(True)
            )
        )
    ).all()
    for row in rows:
        row.active = False
    e = ProgramEnrollment(
        user_id=user.id, content_id=item.id, title=item.title, days=payload.days
    )
    db.add(e)
    await db.commit()
    await db.refresh(e)
    return {"program": _view(e)}


@router.delete("/active", status_code=200)
async def leave_program(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    e = await _active(db, user)
    if e is not None:
        e.active = False
        await db.commit()
    return {"program": None}
