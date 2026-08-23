from datetime import datetime

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel, ConfigDict, EmailStr, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_admin
from app.core.ratelimit import client_ip, limiter
from app.models.waitlist import WaitlistEntry
from app.services import botcheck

router = APIRouter(tags=["waitlist"])


class WaitlistJoin(BaseModel):
    email: EmailStr
    # Register E36: `source` was any string from a public, unauthenticated
    # endpoint, exported to CSV and rendered in admin. Bounded and restricted
    # to a plain slug so nothing exotic reaches either surface; the CSV writer
    # escapes formulas as well (defence at both ends).
    source: str = Field(default="landing", max_length=40, pattern=r"^[A-Za-z0-9 _.:/-]*$")
    #: See SignupRequest — optional until a challenge secret is configured.
    challenge_token: str | None = Field(default=None, max_length=4096)


class WaitlistOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    email: EmailStr
    source: str
    created_at: datetime


@router.post("/waitlist", status_code=201)
@limiter.limit("10/minute")   # public unauthenticated endpoint — blunt spam floods
async def join_waitlist(request: Request, payload: WaitlistJoin, db: AsyncSession = Depends(get_db)):
    # The other public write. A flooded waitlist is not a spend problem like
    # signup is, but it is the number the landing page reports and an investor
    # reads, so a bot-inflated one is a false claim about traction.
    await botcheck.guard(payload.email, payload.challenge_token, client_ip(request))
    email = payload.email.lower()
    existing = await db.scalar(select(WaitlistEntry).where(WaitlistEntry.email == email))
    if existing is None:
        db.add(WaitlistEntry(email=email, source=payload.source))
        try:
            await db.commit()
        except IntegrityError:
            # Register C52: a concurrent join of the same address is still
            # "joined", not a 500.
            await db.rollback()
    # One answer either way (register C11): a distinct "already_joined" on an
    # unauthenticated endpoint was a public membership oracle for any email
    # address — the same leak /auth/otp/request and /auth/password/forgot go
    # out of their way to avoid. Joining is idempotent; the caller learns only
    # that the address is on the list now.
    return {"status": "joined"}


@router.get("/admin/waitlist", response_model=list[WaitlistOut], dependencies=[Depends(get_current_admin)])
async def list_waitlist(limit: int = 1000, offset: int = 0, db: AsyncSession = Depends(get_db)):
    # Bounded (register E44); the admin UI states when a page is full so the
    # CSV export can't silently pass off a truncated list as everything.
    rows = await db.scalars(
        select(WaitlistEntry)
        .order_by(WaitlistEntry.created_at.desc())
        .limit(max(1, min(limit, 5000)))
        .offset(max(0, offset))
    )
    return rows.all()
