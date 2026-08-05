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

from app.core.database import get_db
from app.core.deps import get_current_user
from app.core.localtime import local_date, local_today
from app.models.content import ContentItem
from app.models.program import ProgramEnrollment
from app.models.user import User

router = APIRouter(prefix="/programs", tags=["programs"])


class EnrollIn(BaseModel):
    content_id: uuid.UUID
    days: int = Field(default=7, ge=1, le=60)


def _view(e: ProgramEnrollment, tz: str = "") -> dict:
    # The user's calendar (register C65): a UTC date difference rolled an IST
    # user to "day 3" at 05:30 local.
    elapsed = (local_today(tz) - local_date(e.started_at, tz)).days
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


async def _full_view(db: AsyncSession, e: ProgramEnrollment, tz: str = "") -> dict:
    """The enrollment WITH its day guides.

    Both /active and /enroll return "the program", so both must return the same
    program. They did not: enroll returned the bare `_view`, so a client that
    rendered straight from the enroll response — the web app does — saw no
    guides at all until something else refetched. The journey path simply did not
    appear after enrolling, which an e2e run caught and code review had not.
    """
    view = _view(e, tz)
    item = await db.get(ContentItem, e.content_id)
    guide = _today_guide(item, view["day"])
    if guide is not None:
        view["today_guide"] = guide
    guides = _all_guides(item)
    if guides is not None:
        view["guides"] = guides
    return view


@router.get("/active")
async def active_program(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    e = await _active(db, user)
    if e is None:
        return {"program": None}
    return {"program": await _full_view(db, e, user.timezone)}


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
    if prior is not None and (local_today(user.timezone) - local_date(prior.started_at, user.timezone)).days < prior.days:
        prior.active = True
        await db.commit()
        await db.refresh(prior)
        return {"program": await _full_view(db, prior, user.timezone)}

    e = ProgramEnrollment(
        user_id=user.id, content_id=item.id, title=item.title, days=payload.days
    )
    db.add(e)
    await db.commit()
    await db.refresh(e)
    return {"program": await _full_view(db, e, user.timezone)}


@router.delete("/active", status_code=200)
async def leave_program(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    e = await _active(db, user)
    if e is not None:
        e.active = False
        await db.commit()
    return {"program": None}
