from datetime import datetime

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel, ConfigDict, EmailStr, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_admin
from app.core.ratelimit import limiter
from app.models.waitlist import WaitlistEntry

router = APIRouter(tags=["waitlist"])


class WaitlistJoin(BaseModel):
    email: EmailStr
    # Register E36: `source` was any string from a public, unauthenticated
    # endpoint, exported to CSV and rendered in admin. Bounded and restricted
    # to a plain slug so nothing exotic reaches either surface; the CSV writer
    # escapes formulas as well (defence at both ends).
    source: str = Field(default="landing", max_length=40, pattern=r"^[A-Za-z0-9 _.:/-]*$")


class WaitlistOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    email: EmailStr
    source: str
    created_at: datetime


@router.post("/waitlist", status_code=201)
@limiter.limit("10/minute")   # public unauthenticated endpoint — blunt spam floods
async def join_waitlist(request: Request, payload: WaitlistJoin, db: AsyncSession = Depends(get_db)):
    email = payload.email.lower()
    existing = await db.scalar(select(WaitlistEntry).where(WaitlistEntry.email == email))
    if existing:
        return {"status": "already_joined"}
    db.add(WaitlistEntry(email=email, source=payload.source))
    await db.commit()
    return {"status": "joined"}


@router.get("/admin/waitlist", response_model=list[WaitlistOut], dependencies=[Depends(get_current_admin)])
async def list_waitlist(db: AsyncSession = Depends(get_db)):
    rows = await db.scalars(select(WaitlistEntry).order_by(WaitlistEntry.created_at.desc()))
    return rows.all()
