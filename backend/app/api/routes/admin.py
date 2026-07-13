import uuid
from datetime import datetime
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db, utcnow
from app.core.deps import get_current_admin
from app.core.ratelimit import limiter
from app.models.chat import ChatMessage
from app.models.consent import Consent
from app.models.content import ContentItem
from app.models.journal import JournalEntry
from app.models.media import MediaAsset
from app.models.mood import MoodLog
from app.models.nudge import Nudge
from app.models.prompt import PromptTemplate
from app.models.safety import SafetyEvent
from app.models.sleep import SleepLog
from app.models.trusted_contact import TrustedContact
from app.models.user import User
from app.schemas.content_data import (
    AdminContentOut,
    ContentCreate,
    ContentUpdate,
    SafetyEventOut,
)
from app.schemas.media import (
    MEDIA_KINDS,
    MediaAssetCreate,
    MediaAssetOut,
    MediaAssetUpdate,
)
from app.schemas.user import UserOut
from app.services import media, metrics, nudges, voice
from app.services import prompts as prompt_registry

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
@router.get("/content", response_model=list[AdminContentOut])
async def admin_list_content(db: AsyncSession = Depends(get_db)):
    rows = await db.scalars(select(ContentItem).order_by(ContentItem.created_at.desc()))
    return rows.all()


@router.post("/content", response_model=AdminContentOut, status_code=201)
async def create_content(payload: ContentCreate, db: AsyncSession = Depends(get_db)):
    item = ContentItem(**payload.model_dump())
    db.add(item)
    await db.commit()
    await db.refresh(item)
    return item


@router.patch("/content/{item_id}", response_model=AdminContentOut)
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
    if item.audio_url.startswith("/media/narration/"):
        media.delete_narration(item.id)
    await db.delete(item)
    await db.commit()


# Turbo v2.5 accepts ~40k chars per request; guard below that so a too-long
# script gets an actionable error instead of a provider failure.
_MAX_NARRATION_CHARS = 39_000


@router.post("/content/{item_id}/narrate", response_model=AdminContentOut)
@limiter.limit("3/minute")   # provider-cost guard — narration burns real TTS credits
async def narrate_content(
    request: Request,
    item_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
):
    """Generate narration audio for a content item from its script (ElevenLabs).

    Synchronous by design: generation takes seconds-to-a-minute, admin-triggered
    one item at a time. The endpoint is async, so workers keep serving.
    """
    item = await db.get(ContentItem, item_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Content not found")
    if not settings.tts_enabled:
        raise HTTPException(status_code=503, detail="Text-to-speech is not configured")
    script = item.narration_script.strip()
    if not script:
        raise HTTPException(status_code=400, detail="This item has no narration script")
    if len(script) > _MAX_NARRATION_CHARS:
        raise HTTPException(
            status_code=422,
            detail=f"Narration script exceeds {_MAX_NARRATION_CHARS} characters — shorten it",
        )
    audio = await voice.synthesize(script, timeout=300)
    if not audio:
        raise HTTPException(status_code=502, detail="Speech synthesis failed")
    item.audio_url = media.save_narration(item.id, audio)
    item.audio_generated_at = utcnow()
    await db.commit()
    await db.refresh(item)
    return item


# ── Media catalogue (the sounds/videos clients resolve by key) ───────────
# Uploads are held in memory before being written, so cap them. Ambient loops
# are the big ones (~700 KB/minute at our bitrate) and scene videos larger
# still; 25 MB fits a few minutes of either with room to spare.
_MAX_ASSET_BYTES = 25 * 1024 * 1024


@router.get("/media", response_model=list[MediaAssetOut])
async def admin_list_media(db: AsyncSession = Depends(get_db)):
    rows = await db.scalars(select(MediaAsset).order_by(MediaAsset.key))
    return rows.all()


@router.post("/media", response_model=MediaAssetOut, status_code=201)
async def create_media(payload: MediaAssetCreate, db: AsyncSession = Depends(get_db)):
    if not media.valid_key(payload.key):
        raise HTTPException(
            status_code=422,
            detail="Key must be a dotted lowercase slug, e.g. 'ambience.rain'",
        )
    if payload.kind not in MEDIA_KINDS:
        raise HTTPException(status_code=422, detail=f"Kind must be one of {', '.join(MEDIA_KINDS)}")
    existing = await db.scalar(select(MediaAsset).where(MediaAsset.key == payload.key))
    if existing is not None:
        raise HTTPException(status_code=409, detail=f"Key '{payload.key}' already exists")
    asset = MediaAsset(**payload.model_dump())
    db.add(asset)
    await db.commit()
    await db.refresh(asset)
    return asset


@router.patch("/media/{asset_id}", response_model=MediaAssetOut)
async def update_media(asset_id: uuid.UUID, payload: MediaAssetUpdate, db: AsyncSession = Depends(get_db)):
    asset = await db.get(MediaAsset, asset_id)
    if asset is None:
        raise HTTPException(status_code=404, detail="Media asset not found")
    fields = payload.model_dump(exclude_unset=True)
    if "kind" in fields and fields["kind"] not in MEDIA_KINDS:
        raise HTTPException(status_code=422, detail=f"Kind must be one of {', '.join(MEDIA_KINDS)}")
    for field, value in fields.items():
        setattr(asset, field, value)
    await db.commit()
    await db.refresh(asset)
    return asset


@router.delete("/media/{asset_id}", status_code=204)
async def delete_media(asset_id: uuid.UUID, db: AsyncSession = Depends(get_db)):
    asset = await db.get(MediaAsset, asset_id)
    if asset is None:
        raise HTTPException(status_code=404, detail="Media asset not found")
    if asset.url.startswith("/media/assets/"):
        media.delete_asset(asset.key)
    await db.delete(asset)
    await db.commit()


@router.post("/media/{asset_id}/upload", response_model=MediaAssetOut)
async def upload_media(
    asset_id: uuid.UUID,
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
):
    """Attach real bytes to a catalogue key — the whole point of the catalogue.

    Until this runs, the row's `url` is empty and every client plays its bundled
    or synthesized fallback. Uploading swaps the sound for everyone on next launch,
    with no app release. Re-uploading overwrites in place.
    """
    asset = await db.get(MediaAsset, asset_id)
    if asset is None:
        raise HTTPException(status_code=404, detail="Media asset not found")

    ext = Path(file.filename or "").suffix.lower()
    if ext not in media.ASSET_MIME_BY_EXT:
        raise HTTPException(
            status_code=415,
            detail=f"Unsupported format '{ext or 'none'}' — use {', '.join(media.ASSET_MIME_BY_EXT)}",
        )
    data = await file.read(_MAX_ASSET_BYTES + 1)
    if not data:
        raise HTTPException(status_code=400, detail="File is empty")
    if len(data) > _MAX_ASSET_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"File exceeds {_MAX_ASSET_BYTES // (1024 * 1024)} MB",
        )

    asset.url = media.save_asset(asset.key, ext, data)
    asset.mime = media.ASSET_MIME_BY_EXT[ext]
    await db.commit()
    await db.refresh(asset)
    return asset


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


# ── Prompt registry (versioned LLM prompts; services/prompts.py) ─────────
class PromptSave(BaseModel):
    template: str = Field(min_length=1, max_length=8000)
    notes: str = Field(default="", max_length=255)


def _prompt_row(p: PromptTemplate) -> dict:
    return {
        "version": p.version,
        "active": p.active,
        "notes": p.notes,
        "created_at": p.created_at,
        "template": p.template,
    }


@router.get("/prompts")
async def list_prompts(db: AsyncSession = Depends(get_db)):
    """Every registered prompt with its code default, live template, and full
    version history. `source` says what production is actually serving."""
    rows = (
        await db.scalars(select(PromptTemplate).order_by(PromptTemplate.name, PromptTemplate.version.desc()))
    ).all()
    by_name: dict[str, list[PromptTemplate]] = {}
    for row in rows:
        by_name.setdefault(row.name, []).append(row)

    out = []
    for name in sorted(set(prompt_registry.registered()) | set(by_name)):
        versions = by_name.get(name, [])
        active = next((v for v in versions if v.active), None)
        out.append({
            "name": name,
            "source": "registry" if active else "code_default",
            "active_version": active.version if active else None,
            "template": active.template if active else prompt_registry.default_for(name),
            "default_template": prompt_registry.default_for(name),
            "versions": [_prompt_row(v) for v in versions],
        })
    return out


@router.post("/prompts/{name}", status_code=201)
async def save_prompt(name: str, payload: PromptSave, db: AsyncSession = Depends(get_db)):
    """Save a new immutable version and activate it. Names are curated: only
    prompts the code registered (or that already have rows) are editable."""
    known = name in prompt_registry.registered()
    versions = (await db.scalars(select(PromptTemplate).where(PromptTemplate.name == name))).all()
    if not known and not versions:
        raise HTTPException(status_code=404, detail="Unknown prompt")
    for row in versions:
        row.active = False
    new = PromptTemplate(
        name=name,
        version=max((v.version for v in versions), default=0) + 1,
        template=payload.template,
        notes=payload.notes,
        active=True,
    )
    db.add(new)
    await db.commit()
    await db.refresh(new)
    return _prompt_row(new) | {"name": name}


@router.post("/prompts/{name}/versions/{version}/activate")
async def activate_prompt_version(name: str, version: int, db: AsyncSession = Depends(get_db)):
    """Roll back/forward by activating an existing version."""
    versions = (await db.scalars(select(PromptTemplate).where(PromptTemplate.name == name))).all()
    target = next((v for v in versions if v.version == version), None)
    if target is None:
        raise HTTPException(status_code=404, detail="Version not found")
    for row in versions:
        row.active = row.version == version
    await db.commit()
    return _prompt_row(target) | {"name": name}


@router.post("/prompts/{name}/revert")
async def revert_prompt(name: str, db: AsyncSession = Depends(get_db)):
    """Deactivate every stored version — the code default serves again.
    History is kept, so any version can be re-activated later."""
    versions = (await db.scalars(select(PromptTemplate).where(PromptTemplate.name == name))).all()
    if not versions and name not in prompt_registry.registered():
        raise HTTPException(status_code=404, detail="Unknown prompt")
    for row in versions:
        row.active = False
    await db.commit()
    return {"name": name, "source": "code_default", "template": prompt_registry.default_for(name)}
