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


def _today_guide(item: ContentItem | None, day: int) -> dict | None:
    """Per-day program structure (W15): the current day's {title, body} guide.

    The day index clamps into the guide list — an enrollment longer than the
    list keeps showing the final guide, never an error. Additive: programs
    without day_guides simply omit the field, and older clients ignore it.
    """
    guides = (item.day_guides if item is not None else None) or []
    if not guides:
        return None
    g = guides[min(max(day, 1), len(guides)) - 1]
    return {"title": str(g.get("title", "")), "body": str(g.get("body", ""))}


def _all_guides(item: ContentItem | None) -> list[dict] | None:
    """Every day of the program, in order — what the journey path draws.

    `today_guide` alone made the clients day-blind in the other direction: a
    user could read the day they are on and nothing else, so a "7-day wind-down"
    was seven surprises. Nothing here is gated; a program is a suggested order,
    not a syllabus, and a person who wants to read Friday on Monday may.
    Additive, like `today_guide`: absent for programs with no day structure.
    """
    guides = (item.day_guides if item is not None else None) or []
    if not guides:
        return None
    return [
        {"title": str(g.get("title", "")), "body": str(g.get("body", ""))}
        for g in guides
    ]


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
    if e is None:
        return {"program": None}
    view = _view(e)
    item = await db.get(ContentItem, e.content_id)
    guide = _today_guide(item, view["day"])
    if guide is not None:
        view["today_guide"] = guide
    guides = _all_guides(item)
    if guides is not None:
        view["guides"] = guides
    return {"program": view}


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

    # Coming back to a program you left RESUMES it, rather than restarting.
    #
    # Leaving only flips `active`, so the original `started_at` survives — but
    # enrolling always minted a fresh row, so one unconfirmed tap on "Leave this
    # journey" silently reset day 5 of 7 back to day 1, with the real start date
    # still sitting in the table. Nobody who taps away from a wind-down program
    # on a bad evening means to forfeit the week.
    #
    # Only while the old window is still running. Rejoining a program abandoned
    # months ago must start over, not drop the user at "day 92" — clamped to the
    # last day and instantly complete, which would be its own lie.
    prior = await db.scalar(
        select(ProgramEnrollment)
        .where(
            ProgramEnrollment.user_id == user.id,
            ProgramEnrollment.content_id == item.id,
            ProgramEnrollment.active.is_(False),
        )
        .order_by(ProgramEnrollment.started_at.desc())
    )
    if prior is not None and (utcnow().date() - prior.started_at.date()).days < prior.days:
        prior.active = True
        await db.commit()
        await db.refresh(prior)
        return {"program": _view(prior)}

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
