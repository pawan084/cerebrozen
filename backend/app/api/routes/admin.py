import uuid
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db, utcnow
from app.core.deps import get_current_admin
from app.models.chat import ChatMessage
from app.models.consent import Consent
from app.models.content import ContentItem
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.nudge import Nudge
from app.models.safety import SafetyEvent
from app.models.sleep import SleepLog
from app.models.trusted_contact import TrustedContact
from app.models.user import User
from app.schemas.content_data import (
    ContentCreate,
    ContentOut,
    ContentUpdate,
    SafetyEventOut,
)
from app.schemas.user import UserOut
from app.services import metrics, nudges

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


# ── First-party analytics (aggregates only — see services/metrics.py) ───
@router.get("/metrics/overview")
async def metrics_overview(db: AsyncSession = Depends(get_db)):
    return await metrics.overview(db)


@router.get("/metrics/funnel")
async def metrics_funnel(days: int = 30, db: AsyncSession = Depends(get_db)):
    """Onboarding funnel from anonymous product events (unique installs)."""
    return await metrics.onboarding_funnel(db, days=max(1, min(days, 365)))


# ── Users ───────────────────────────────────────────────────────────────
@router.get("/users", response_model=list[UserOut])
async def list_users(
    q: str | None = None,
    limit: int = 100,
    offset: int = 0,
    db: AsyncSession = Depends(get_db),
):
    """Newest-first accounts. ``q`` filters by email or name (case-insensitive)
    so support can find one account among many without paging through them all."""
    stmt = select(User).order_by(User.created_at.desc())
    if q and (term := q.strip()):
        like = f"%{term}%"
        stmt = stmt.where(User.email.ilike(like) | User.name.ilike(like))
    rows = await db.scalars(stmt.limit(limit).offset(offset))
    return rows.all()


@router.get("/users/{user_id}")
async def user_detail(user_id: uuid.UUID, db: AsyncSession = Depends(get_db)):
    """Support view: account state + activity COUNTS only. Journal, chat, and
    sleep contents deliberately never cross this endpoint — support can act on
    an account without reading a private life."""
    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")

    async def count(model, *extra) -> int:
        return (
            await db.scalar(
                select(func.count()).select_from(model).where(model.user_id == user_id, *extra)
            )
        ) or 0

    last_active = None
    for model in (MoodLog, JournalEntry, ChatMessage, SleepLog):
        latest = await db.scalar(
            select(func.max(model.created_at)).where(model.user_id == user_id)
        )
        if latest and (last_active is None or latest > last_active):
            last_active = latest

    consent = await db.scalar(select(Consent).where(Consent.user_id == user_id))
    has_contact = (
        await db.scalar(select(func.count()).select_from(TrustedContact).where(TrustedContact.user_id == user_id))
    ) or 0

    return {
        "user": {
            "id": str(user.id),
            "email": user.email,
            "name": user.name,
            "language": user.language,
            "companion": user.companion,
            "region": user.region,
            "subscription_tier": user.subscription_tier,
            "is_active": user.is_active,
            "is_admin": user.is_admin,
            "created_at": user.created_at,
        },
        "counts": {
            "moods": await count(MoodLog),
            "journals": await count(JournalEntry),
            "chat_messages": await count(ChatMessage, ChatMessage.role == "user"),
            "sleep_logs": await count(SleepLog),
            "open_safety_events": await count(SafetyEvent, SafetyEvent.resolved.is_(False)),
            "pending_nudges": await count(Nudge, Nudge.status == "scheduled"),
        },
        "consent": None
        if consent is None
        else {
            "mood_history": consent.mood_history,
            "ai_memory": consent.ai_memory,
            "voice_storage": consent.voice_storage,
            "model_training": consent.model_training,
            "journal_memory": consent.journal_memory,
            "sleep_history": consent.sleep_history,
        },
        "trusted_contact": bool(has_contact),
        "last_active": last_active,
    }


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


# ── Nudge authoring + ops ────────────────────────────────────────────────
class NudgeAuthor(BaseModel):
    title: str = Field(min_length=1, max_length=160)
    body: str = Field(min_length=1, max_length=500)
    deeplink: str | None = Field(default=None, max_length=255)
    scheduled_for: datetime | None = None  # None = next dispatch pass
    user_id: uuid.UUID | None = None       # None = every active user


@router.post("/nudges", status_code=201)
async def author_nudge(payload: NudgeAuthor, db: AsyncSession = Depends(get_db)):
    """Create a one-off nudge for one user or (user_id omitted) every active
    user. Delivery stays with the existing scheduler/dispatch pass."""
    when = payload.scheduled_for or utcnow()
    if payload.user_id is not None:
        target = await db.get(User, payload.user_id)
        if target is None:
            raise HTTPException(status_code=404, detail="User not found")
        targets = [target]
    else:
        targets = (await db.scalars(select(User).where(User.is_active.is_(True)))).all()

    for user in targets:
        db.add(
            Nudge(
                user_id=user.id,
                kind="announcement",
                title=payload.title,
                body=payload.body,
                deeplink=payload.deeplink,
                scheduled_for=when,
            )
        )
    await db.commit()
    return {"created": len(targets)}


@router.get("/nudges")
async def list_nudges(
    limit: int = 100, kind: str | None = None, db: AsyncSession = Depends(get_db)
):
    stmt = (
        select(Nudge, User.email)
        .join(User, User.id == Nudge.user_id)
        .order_by(Nudge.scheduled_for.desc())
        .limit(min(limit, 500))
    )
    if kind:
        stmt = stmt.where(Nudge.kind == kind)
    rows = (await db.execute(stmt)).all()
    return [
        {
            "id": str(n.id),
            "email": email,
            "kind": n.kind,
            "title": n.title,
            "body": n.body,
            "status": n.status,
            "scheduled_for": n.scheduled_for,
        }
        for n, email in rows
    ]


# ── Ops: manual dispatch pass (the in-process scheduler in app.main runs
# this automatically every NUDGE_DISPATCH_INTERVAL_MINUTES) ───────────────
@router.post("/nudges/dispatch")
async def dispatch_nudges(db: AsyncSession = Depends(get_db)):
    sent = await nudges.dispatch_due(db)
    return {"sent": sent}
