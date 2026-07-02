import uuid

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_admin
from app.models.content import ContentItem
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.safety import SafetyEvent
from app.models.user import User
from app.schemas.content_data import (
    ContentCreate,
    ContentOut,
    ContentUpdate,
    SafetyEventOut,
)
from app.schemas.user import UserOut
from app.services import nudges

router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(get_current_admin)])


# ── Stats ───────────────────────────────────────────────────────────────
@router.get("/stats")
async def stats(db: AsyncSession = Depends(get_db)):
    async def count(model) -> int:
        return (await db.scalar(select(func.count()).select_from(model))) or 0

    return {
        "users": await count(User),
        "mood_logs": await count(MoodLog),
        "journal_entries": await count(JournalEntry),
        "content_items": await count(ContentItem),
        "open_safety_events": (
            await db.scalar(
                select(func.count()).select_from(SafetyEvent).where(SafetyEvent.resolved.is_(False))
            )
        )
        or 0,
    }


# ── Users ───────────────────────────────────────────────────────────────
@router.get("/users", response_model=list[UserOut])
async def list_users(limit: int = 100, offset: int = 0, db: AsyncSession = Depends(get_db)):
    rows = await db.scalars(select(User).order_by(User.created_at.desc()).limit(limit).offset(offset))
    return rows.all()


@router.patch("/users/{user_id}/active", response_model=UserOut)
async def set_user_active(user_id: uuid.UUID, active: bool, db: AsyncSession = Depends(get_db)):
    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")
    user.is_active = active
    await db.commit()
    await db.refresh(user)
    return user


# ── Content CRUD ────────────────────────────────────────────────────────
@router.get("/content", response_model=list[ContentOut])
async def admin_list_content(db: AsyncSession = Depends(get_db)):
    rows = await db.scalars(select(ContentItem).order_by(ContentItem.created_at.desc()))
    return rows.all()


@router.post("/content", response_model=ContentOut, status_code=201)
async def create_content(payload: ContentCreate, db: AsyncSession = Depends(get_db)):
    item = ContentItem(**payload.model_dump())
    db.add(item)
    await db.commit()
    await db.refresh(item)
    return item


@router.patch("/content/{item_id}", response_model=ContentOut)
async def update_content(item_id: uuid.UUID, payload: ContentUpdate, db: AsyncSession = Depends(get_db)):
    item = await db.get(ContentItem, item_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Content not found")
    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(item, field, value)
    await db.commit()
    await db.refresh(item)
    return item


@router.delete("/content/{item_id}", status_code=204)
async def delete_content(item_id: uuid.UUID, db: AsyncSession = Depends(get_db)):
    item = await db.get(ContentItem, item_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Content not found")
    await db.delete(item)
    await db.commit()


# ── Safety review queue ─────────────────────────────────────────────────
@router.get("/safety", response_model=list[SafetyEventOut])
async def list_safety_events(
    resolved: bool | None = None,
    db: AsyncSession = Depends(get_db),
):
    stmt = select(SafetyEvent).order_by(SafetyEvent.created_at.desc())
    if resolved is not None:
        stmt = stmt.where(SafetyEvent.resolved.is_(resolved))
    rows = await db.scalars(stmt)
    return rows.all()


@router.patch("/safety/{event_id}/resolve", response_model=SafetyEventOut)
async def resolve_safety_event(event_id: uuid.UUID, db: AsyncSession = Depends(get_db)):
    event = await db.get(SafetyEvent, event_id)
    if event is None:
        raise HTTPException(status_code=404, detail="Event not found")
    event.resolved = True
    await db.commit()
    await db.refresh(event)
    return event


# ── Ops: manual dispatch pass (the in-process scheduler in app.main runs
# this automatically every NUDGE_DISPATCH_INTERVAL_MINUTES) ───────────────
@router.post("/nudges/dispatch")
async def dispatch_nudges(db: AsyncSession = Depends(get_db)):
    sent = await nudges.dispatch_due(db)
    return {"sent": sent}
