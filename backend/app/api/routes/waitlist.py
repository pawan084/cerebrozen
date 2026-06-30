from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict, EmailStr
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_admin
from app.models.waitlist import WaitlistEntry

router = APIRouter(tags=["waitlist"])


class WaitlistJoin(BaseModel):
    email: EmailStr
    source: str = "landing"


class WaitlistOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    email: EmailStr
    source: str


@router.post("/waitlist", status_code=201)
async def join_waitlist(payload: WaitlistJoin, db: AsyncSession = Depends(get_db)):
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
