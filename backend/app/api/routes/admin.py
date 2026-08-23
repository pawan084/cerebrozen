import hashlib
import logging
import uuid
from datetime import datetime
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile, status
from pydantic import BaseModel, ConfigDict, Field, field_validator
from sqlalchemy import String, cast, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db, utcnow
from app.core.deps import get_current_admin
from app.models.organization import ROLE_BENEFITS_OWNER, Organization, OrgAdmin
from app.schemas.organization import OrgOut, OrgProvision
from app.core.ratelimit import account_limit, limiter
from app.services import admin_audit
from app.models.admin_audit import AdminAuditLog
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
    SafetyExcerptOut,
)
from app.schemas.media import (
    MEDIA_KINDS,
    MediaAssetCreate,
    MediaAssetOut,
    MediaAssetUpdate,
)
from app.schemas.user import UserOut
from app.models.agent_action import AgentAction
from app.services import digest, media, metrics, nudges, oracle_audit, voice
from app.services import prompts as prompt_registry
from app.services.textsearch import escape_like

logger = logging.getLogger("cerebro.admin")

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


@router.get("/metrics/ceilings")
async def metrics_ceilings(db: AsyncSession = Depends(get_db)):
    """Pressure against the daily abuse ceilings, today.

    The ceilings refuse calls quietly by design; this is the only place anyone
    can see whether they ever have. Note `alerting: false` in the payload —
    nobody is paged, this is a surface someone has to open.
    """
    return await metrics.ceiling_pressure(db)


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
    so support can find one account among many without paging through them all.

    ``subscription_tier`` here is the STORED column, unlike ``/users/me`` — the
    staff view should show what this account bought, not what an organisation
    currently sponsors for it (``services/entitlements``). Resolving it per row
    would also be a query per user. ``sponsored`` is therefore always false in
    this listing rather than unresolved-and-wrong."""
    stmt = select(User).order_by(User.created_at.desc())
    if q and (term := q.strip()):
        # Escaped like journal search always was (register C88). Also matches
        # the user id, so a UUID pasted from the Safety queue finds its
        # account (register E53).
        like = f"%{escape_like(term)}%"
        stmt = stmt.where(
            User.email.ilike(like, escape="\\")
            | User.name.ilike(like, escape="\\")
            | cast(User.id, String).ilike(like, escape="\\")
        )
    # Clamped (register C33): ?limit=10000000 serialised the whole table.
    rows = await db.scalars(stmt.limit(max(1, min(limit, 500))).offset(max(0, offset)))
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


class UserActiveChange(BaseModel):
    """Why an account was disabled or restored.

    Register E33: the admin panel required a reason and PATCHed it in the
    body, but this route declared only the `active` QUERY param — so FastAPI
    discarded the body and no record existed of who disabled which account or
    why. (The frontend comment admitted it.) The reason is now accepted and
    written to the audit log; it stays optional so re-enabling does not
    demand an explanation the operator may not have.
    """

    reason: str = Field(default="", max_length=500)


@router.patch("/users/{user_id}/active", response_model=UserOut)
async def set_user_active(
    user_id: uuid.UUID,
    active: bool,
    payload: UserActiveChange | None = None,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")
    user.is_active = active
    await admin_audit.record(
        db, admin,
        "user.enable" if active else "user.disable",
        target_type="user", target_id=user.id,
        reason=(payload.reason if payload else ""),
        detail={"email": user.email},
    )
    await db.commit()
    await db.refresh(user)
    return user


# ── Operator audit trail ────────────────────────────────────────────────
class AdminAuditOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    admin_email: str
    action: str
    target_type: str
    target_id: str
    reason: str
    detail: dict
    created_at: datetime


@router.get("/audit", response_model=list[AdminAuditOut])
async def admin_audit_trail(limit: int = 100, db: AsyncSession = Depends(get_db)):
    """What operators have done, newest first (register E34).

    Read-only by design: there is no route that edits or deletes these rows,
    because a trail the trailed party can rewrite is not a trail.
    """
    limit = max(1, min(limit, 500))
    rows = await db.scalars(
        select(AdminAuditLog).order_by(AdminAuditLog.created_at.desc()).limit(limit)
    )
    return rows.all()


# ── Content CRUD ────────────────────────────────────────────────────────
@router.get("/content", response_model=list[AdminContentOut])
async def admin_list_content(limit: int = 500, db: AsyncSession = Depends(get_db)):
    # Bounded (register E43): the flat table stops growing without limit.
    rows = await db.scalars(
        select(ContentItem).order_by(ContentItem.created_at.desc()).limit(max(1, min(limit, 1000)))
    )
    return rows.all()


@router.post("/content", response_model=AdminContentOut, status_code=201)
async def create_content(
    payload: ContentCreate,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    item = ContentItem(**payload.model_dump())
    db.add(item)
    await db.flush()
    await admin_audit.record(
        db, admin, "content.create",
        target_type="content", target_id=item.id,
        detail={"title": item.title, "kind": item.kind},
    )
    await db.commit()
    await db.refresh(item)
    return item


@router.patch("/content/{item_id}", response_model=AdminContentOut)
async def update_content(
    item_id: uuid.UUID,
    payload: ContentUpdate,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    item = await db.get(ContentItem, item_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Content not found")
    changed = payload.model_dump(exclude_unset=True)
    for field, value in changed.items():
        setattr(item, field, value)
    await admin_audit.record(
        db, admin, "content.update",
        target_type="content", target_id=item.id,
        detail={"title": item.title, "fields": sorted(changed.keys())},
    )
    await db.commit()
    await db.refresh(item)
    return item


@router.delete("/content/{item_id}", status_code=204)
async def delete_content(
    item_id: uuid.UUID,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    item = await db.get(ContentItem, item_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Content not found")
    if item.audio_url.startswith("/media/narration/"):
        media.delete_narration(item.id)
    await admin_audit.record(
        db, admin, "content.delete",
        target_type="content", target_id=item.id,
        detail={"title": item.title, "kind": item.kind},
    )
    await db.delete(item)
    await db.commit()


# Turbo v2.5 accepts ~40k chars per request; guard below that so a too-long
# script gets an actionable error instead of a provider failure.
_MAX_NARRATION_CHARS = 39_000


@router.post("/content/{item_id}/narrate", response_model=AdminContentOut)
@limiter.limit("3/minute")   # provider-cost guard — narration burns real TTS credits
@account_limit("3/minute")   # …and the same ceiling per account (narration burns real TTS credits)
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
    # For a narrated item the audio IS the content, so the displayed length
    # should be the length of the file we just minted — not whatever minute
    # count someone typed when the item was drafted. Only overwrite when the
    # probe actually read the file: an unreadable MP3 leaves the authored
    # number alone rather than replacing it with a guess.
    seconds = media.mp3_duration_seconds(audio)
    if seconds:
        minutes = media.duration_minutes(seconds)
        if minutes != item.duration_min:
            logger.info(
                "Narration for %s ran %.1fs — duration_min %d → %d",
                item.id, seconds, item.duration_min, minutes,
            )
        item.duration_min = minutes
    else:
        logger.warning(
            "Could not read a duration from the narration MP3 for %s — "
            "leaving duration_min at %d", item.id, item.duration_min,
        )
    await db.commit()
    await db.refresh(item)
    return item


# ── Oracle ops (the agent's audit trail + what it's waiting on) ─────────
# The Oracle writes user data (mood, journal, sleep) behind an interrupt()
# confirmation. These three endpoints are the operator's only view of that:
# which tools ran, which writes were approved, and which confirmations are
# stuck. Argument VALUES are never exposed — only their names (see the
# OracleToolCall docstring for why).


class OracleToolCallOut(BaseModel):
    id: uuid.UUID
    thread_id: str
    tool: str
    risk_tier: str
    decision: str
    arg_keys: list[str]
    created_at: datetime
    resolved_at: datetime | None

    model_config = {"from_attributes": True}


@router.get("/oracle/status")
async def oracle_status(db: AsyncSession = Depends(get_db)):
    """Live agent posture. `checkpointer` is the one worth watching: a
    "memory" value in production means paused confirmations die on restart
    and don't cross gunicorn workers — previously visible only in boot logs.
    """
    from app.agent.graph import checkpointer_kind

    return {
        "enabled": settings.oracle_available,
        "checkpointer": checkpointer_kind(),
        "counts": await oracle_audit.counts(db),
    }


@router.get("/oracle/pending", response_model=list[OracleToolCallOut])
async def oracle_pending(db: AsyncSession = Depends(get_db)):
    return list(await oracle_audit.pending(db))


@router.get("/oracle/audit", response_model=list[OracleToolCallOut])
async def oracle_audit_trail(limit: int = 20, db: AsyncSession = Depends(get_db)):
    return list(await oracle_audit.recent(db, limit=max(1, min(200, limit))))


@router.post("/oracle/pending/{call_id}/expire", response_model=OracleToolCallOut)
async def expire_oracle_pending(
    call_id: uuid.UUID,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    """Close a confirmation that can no longer be answered (register E57).

    The Oracle tab could list stuck confirmations with their age and do nothing
    about them — it diagnosed the exact condition it warned about (a MemorySaver
    restart drops the graph state and strands the row) and had no way to resolve
    it, so the queue only ever grew.

    This closes the *record*. It does not approve, execute, or resume anything:
    the write it describes needs the user's own confirmation inside their own
    thread, and an operator must never stand in for that. Logged like every
    other operator action, because "who cleared this" is exactly the question
    someone will ask later.
    """
    row = await oracle_audit.expire(db, call_id=call_id)
    if row is None:
        raise HTTPException(status_code=404, detail="No pending confirmation with that id")
    await admin_audit.record(
        db,
        admin,
        "oracle.pending.expire",
        target_type="oracle_tool_call",
        target_id=call_id,
        detail={"tool": row.tool, "thread_id": row.thread_id},
    )
    await db.commit()
    return row


# ── Media catalogue (the sounds/videos clients resolve by key) ───────────
# Uploads are held in memory before being written, so cap them. Ambient loops
# are the big ones (~700 KB/minute at our bitrate) and scene videos larger
# still; 25 MB fits a few minutes of either with room to spare.
_MAX_ASSET_BYTES = 25 * 1024 * 1024


@router.get("/media", response_model=list[MediaAssetOut])
async def admin_list_media(limit: int = 500, db: AsyncSession = Depends(get_db)):
    rows = await db.scalars(
        select(MediaAsset).order_by(MediaAsset.key).limit(max(1, min(limit, 1000)))
    )
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
    # Register E51: the admin's "Clear" button PATCHes `url: ""`, and the bytes
    # stayed on disk forever — `delete_asset` ran only on row DELETE, which the
    # UI never calls (clearing is deliberately not a schema change). Every
    # cleared upload leaked a file nothing could ever reach again.
    #
    # Keyed on the URL moving AWAY from our own assets dir, not just on being
    # emptied, because repointing an asset at a CDN orphans the local copy just
    # as completely. The file exists only to serve that URL; once the row stops
    # naming it, it is unreachable by construction.
    # Both are captured BEFORE the update: a PATCH may rename the key in the
    # same call, and `delete_asset` removes by key — deleting the new key's file
    # would destroy a live asset while still leaking the old one.
    previous_url = asset.url or ""
    previous_key = asset.key
    for field, value in fields.items():
        setattr(asset, field, value)
    if (
        previous_url.startswith("/media/assets/")
        and (asset.url or "") != previous_url
    ):
        # Best-effort and after the DB is the source of truth: an unlinked file
        # that the row no longer references is tidy-up, never correctness.
        media.delete_asset(previous_key)
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
    limit: int = 200,
    offset: int = 0,
    db: AsyncSession = Depends(get_db),
):
    # Bounded (register E42): the one admin list that grows fastest returned
    # every event ever recorded on each tab open.
    stmt = select(SafetyEvent).order_by(SafetyEvent.created_at.desc())
    if resolved is not None:
        stmt = stmt.where(SafetyEvent.resolved.is_(resolved))
    rows = (await db.scalars(stmt.limit(max(1, min(limit, 1000))).offset(max(0, offset)))).all()
    # Register E54: `resolved_by` is an admin UUID the UI printed verbatim —
    # the one attribution the system records was unreadable to the humans
    # it's for. Resolve ids to emails in one query and ride them along.
    resolver_ids = {r.resolved_by for r in rows if r.resolved_by is not None}
    if resolver_ids:
        admins = (await db.scalars(select(User).where(User.id.in_(resolver_ids)))).all()
        emails = {a.id: a.email for a in admins}
        for r in rows:
            r.resolved_by_email = emails.get(r.resolved_by)
    return rows


@router.get("/safety/{event_id}/excerpt", response_model=SafetyExcerptOut)
async def read_safety_excerpt(
    event_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    """Serve the verbatim text behind one flagged event.

    Separate from the list route on purpose: reading a person's private words is
    a deliberate act, so it takes a deliberate request, and it leaves a log line
    naming the admin who made it.
    """
    event = await db.get(SafetyEvent, event_id)
    if event is None:
        raise HTTPException(status_code=404, detail="Event not found")
    logger.info(
        "admin.safety.excerpt_read admin_id=%s event_id=%s risk=%s",
        admin.id,
        event.id,
        event.risk_level,
    )
    # Register E35: the log line above is erased by rotation, while the UI
    # tells reviewers "the reveal is noted on the row / the server logged it"
    # and CLAIMS_MAP leans on "a separate, logged, per-row GET". The claim is
    # now true: the reveal is a durable row. It records THAT the excerpt was
    # opened and by whom — never a word of what was read.
    await admin_audit.record(
        db, admin, "safety.excerpt_read",
        target_type="safety_event", target_id=event.id,
        detail={"risk_level": event.risk_level, "source": event.source},
    )
    await db.commit()
    return event


class SafetyResolve(BaseModel):
    # Required: an unattributed, unexplained close is not an audit trail.
    note: str = Field(min_length=1, max_length=500)

    @field_validator("note")
    @classmethod
    def _note_has_substance(cls, v: str) -> str:
        # A note of spaces satisfies min_length but says nothing.
        if not v.strip():
            raise ValueError("note cannot be blank")
        return v


@router.patch("/safety/{event_id}/resolve", response_model=SafetyEventOut)
async def resolve_safety_event(
    event_id: uuid.UUID,
    body: SafetyResolve,
    db: AsyncSession = Depends(get_db),
    admin: User = Depends(get_current_admin),
):
    event = await db.get(SafetyEvent, event_id)
    if event is None:
        raise HTTPException(status_code=404, detail="Event not found")
    event.resolved = True
    event.resolved_by = admin.id
    event.resolved_at = utcnow()
    event.resolution_note = body.note.strip()
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
async def author_nudge(
    payload: NudgeAuthor,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
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
    # Register E31/E34: a broadcast to every active user was a single
    # unconfirmed click and left no record of who sent it.
    await admin_audit.record(
        db, admin, "nudge.broadcast" if payload.user_id is None else "nudge.author",
        target_type="nudge", target_id=payload.user_id or "",
        detail={"recipients": len(targets), "title": payload.title},
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
    # All FIVE tallies, not just `sent` (register E58) and no longer just three
    # (2026-08-22): the retry/lateness work gave the dispatcher two more endings
    # and this endpoint kept reporting the old set, so an operator reading the
    # dashboard saw a pass that "did nothing" when it had in fact dropped stale
    # nudges or deferred blipped ones.
    #
    # They are never summed. Each answers a different question:
    #   sent      — delivered.
    #   skipped   — nobody was reachable at all. A reach question.
    #   failed    — a device we hold a token for refused, after every retry.
    #               The one worth chasing.
    #   expired   — too late to mean anything, so deliberately not delivered.
    #               Rising `expired` means WE were down, not that users are gone.
    #   deferred  — a delivery blipped and will be retried. Not an ending.
    outcome = await nudges.dispatch_due(db)
    return {
        "sent": outcome.sent,
        "skipped": outcome.skipped,
        "failed": outcome.failed,
        "expired": outcome.expired,
        "deferred": outcome.deferred,
    }


@router.get("/agent-actions")
async def agent_action_stats(db: AsyncSession = Depends(get_db)):
    """Per-tool proposal/approval counts — how often users accept what the
    agent wants to write.

    Counts only, never summaries: a summary can quote the user's own words
    back ("Save a journal entry about your argument with…"), which is content,
    not metadata. A persistently declined tool is the signal worth surfacing.
    """
    rows = (await db.scalars(select(AgentAction))).all()
    by_tool: dict[str, dict] = {}
    for row in rows:
        entry = by_tool.setdefault(
            row.tool, {"tool": row.tool, "proposed": 0, "approved": 0, "declined": 0}
        )
        entry["proposed"] += 1
        if row.status == "approved":
            entry["approved"] += 1
        elif row.status == "declined":
            entry["declined"] += 1
    return sorted(by_tool.values(), key=lambda e: -e["proposed"])


@router.post("/digest/run")
async def run_weekly_digest(db: AsyncSession = Depends(get_db)):
    """Snapshot + queue this week's digest for every active user.

    The in-process loop does this on its own; this is the manual pass for
    deployments running the dispatcher on an external cron
    (NUDGE_DISPATCH_INTERVAL_MINUTES=0), and for verifying a week by hand.
    Idempotent per ISO week.
    """
    queued = await digest.run_weekly_pass(db)
    return {"queued": queued}


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
        live = active.template if active else prompt_registry.default_for(name)
        out.append({
            "name": name,
            "source": "registry" if active else "code_default",
            "active_version": active.version if active else None,
            "template": live,
            "default_template": prompt_registry.default_for(name),
            # Audit J#4: a short content hash of what production is actually
            # serving, so "does prod match the reviewed prompt?" is a glance at
            # two hex strings instead of a diff of two paragraphs. Doubles as a
            # stable prompt-cache key for providers that support one.
            "content_hash": hashlib.sha256(live.encode("utf-8")).hexdigest()[:12],
            "versions": [_prompt_row(v) for v in versions],
        })
    return out


@router.post("/prompts/{name}", status_code=201)
async def save_prompt(
    name: str,
    payload: PromptSave,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    """Save a new immutable version and activate it. Names are curated: only
    prompts the code registered (or that already have rows) are editable."""
    known = name in prompt_registry.registered()
    versions = (await db.scalars(select(PromptTemplate).where(PromptTemplate.name == name))).all()
    if not known and not versions:
        raise HTTPException(status_code=404, detail="Unknown prompt")
    # Audit J#4 (the sibling registry's save-blocking validation): activating a
    # BLANK template silently replaces a live system prompt with nothing — the
    # LLM call still "works", it just runs unguided, which for the safety
    # classifier or the Oracle persona is a production incident with no error
    # anywhere. The safety_classifier acknowledgement flow guards *which* prompt
    # you edit; this guards emptiness for all of them. Reverting to the code
    # default is the supported way to clear an override.
    if not payload.template.strip():
        raise HTTPException(
            status_code=422,
            detail="An empty template cannot be activated — use Revert to return to the code default.",
        )
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
    await db.flush()
    await admin_audit.record(
        db, admin, "prompt.save",
        target_type="prompt", target_id=name,
        reason=payload.notes or "",
        detail={"version": new.version},
    )
    await db.commit()
    await db.refresh(new)
    return _prompt_row(new) | {"name": name}


@router.post("/prompts/{name}/versions/{version}/activate")
async def activate_prompt_version(
    name: str,
    version: int,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    """Roll back/forward by activating an existing version."""
    versions = (await db.scalars(select(PromptTemplate).where(PromptTemplate.name == name))).all()
    target = next((v for v in versions if v.version == version), None)
    if target is None:
        raise HTTPException(status_code=404, detail="Version not found")
    for row in versions:
        row.active = row.version == version
    # Register E32: activating an old version bypasses every guard the save
    # path builds (acknowledgement + two-step confirm for safety_classifier).
    # It cannot bypass the record of who did it.
    await admin_audit.record(
        db, admin, "prompt.activate",
        target_type="prompt", target_id=name,
        detail={"version": version},
    )
    await db.commit()
    return _prompt_row(target) | {"name": name}


@router.post("/prompts/{name}/revert")
async def revert_prompt(
    name: str,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db),
):
    """Deactivate every stored version — the code default serves again.
    History is kept, so any version can be re-activated later."""
    versions = (await db.scalars(select(PromptTemplate).where(PromptTemplate.name == name))).all()
    if not versions and name not in prompt_registry.registered():
        raise HTTPException(status_code=404, detail="Unknown prompt")
    for row in versions:
        row.active = False
    await db.commit()
    return {"name": name, "source": "code_default", "template": prompt_registry.default_for(name)}


# ---------------------------------------------------------------- organisations


@router.post("/organizations", response_model=OrgOut, status_code=status.HTTP_201_CREATED)
async def provision_organization(
    body: OrgProvision,
    db: AsyncSession = Depends(get_db),
    admin: User = Depends(get_current_admin),
) -> Organization:
    """Create an organisation and attach its first Benefits owner.

    The only way an organisation comes into existence. Before this, the first
    row had to be written by hand in psql, which is not an onboarding path and
    left nothing able to set up the state a test needs.

    Audited like every other administrative action: the row records who
    provisioned which organisation, never anything about its members.
    """
    owner = await db.scalar(select(User).where(User.email == str(body.admin_email).lower()))
    if owner is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No CereBro account for that address — the administrator signs up first",
        )
    if await db.scalar(select(Organization).where(Organization.name == body.name)):
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="An organisation with that name exists")

    org = Organization(
        name=body.name,
        legal_entity=body.legal_entity,
        region=body.region,
        seats_licensed=body.seats_licensed,
        contract_start=body.contract_start,
        contract_end=body.contract_end,
    )
    db.add(org)
    await db.flush()
    db.add(OrgAdmin(org_id=org.id, user_id=owner.id, role=ROLE_BENEFITS_OWNER))
    await admin_audit.record(
        db, admin, "organization.provision",
        target_type="organization", target_id=org.id,
        detail={"name": org.name, "region": org.region},
    )
    await db.commit()
    await db.refresh(org)
    return org
