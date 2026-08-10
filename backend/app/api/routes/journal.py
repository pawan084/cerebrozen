import datetime as dt
import uuid

from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.journal import JournalEntry
from app.models.user import User
from app.schemas.content_data import JournalCreate, JournalOut
from app.services import crisis, idempotency, safety
from app.services.textsearch import escape_like

router = APIRouter(prefix="/journal", tags=["journal"])


# Escaping lives in services.textsearch now, shared with /content and
# /admin/users so the three search boxes cannot drift apart again.
_escape_like = escape_like


@router.get("", response_model=list[JournalOut])
async def list_entries(
    q: str | None = None,
    tag: str | None = None,
    since: dt.datetime | None = None,
    limit: int = 200,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Entries, newest first, with the three filters a journal actually needs.

    * `q` — case-insensitive substring over title and body. Searching happens
      here rather than on-device because the client holds only the pages it has
      fetched, and "I wrote about this months ago" is exactly the search that
      would miss.
    * `tag` — exact match within the entry's `tags` array.
    * `since` — sync cursor, same contract as `/moods`.

    Filtering is scoped to the caller before anything else, so no query shape
    can widen past one user's own entries.
    """
    query = select(JournalEntry).where(JournalEntry.user_id == user.id)
    if q:
        needle = f"%{_escape_like(q.strip().lower())}%"
        query = query.where(
            or_(
                func.lower(JournalEntry.title).like(needle, escape="\\"),
                func.lower(JournalEntry.body).like(needle, escape="\\"),
            )
        )
    if tag:
        # JSONB containment: the array holds this exact tag.
        query = query.where(JournalEntry.tags.contains([tag]))
    if since is not None:
        if since.tzinfo is None:
            since = since.replace(tzinfo=dt.timezone.utc)
        query = query.where(JournalEntry.created_at > since)
    rows = await db.scalars(
        # max() floors a negative ?limit= (register C32).
        query.order_by(JournalEntry.created_at.desc()).limit(max(1, min(limit, 500)))
    )
    return rows.all()


@router.get("/tags", response_model=list[str])
async def list_tags(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Every tag this user has actually used, alphabetically.

    The composer offers these as chips instead of a free-text box that quietly
    creates "work", "Work" and "work " as three different tags.
    """
    rows = (
        await db.scalars(
            select(JournalEntry.tags).where(JournalEntry.user_id == user.id)
        )
    ).all()
    seen: set[str] = set()
    for tags in rows:
        for tag in tags or []:
            cleaned = str(tag).strip()
            if cleaned:
                seen.add(cleaned)
    return sorted(seen, key=str.lower)


@router.post("", response_model=JournalOut, status_code=201)
async def create_entry(
    payload: JournalCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
):
    key = idempotency.normalise_key(idempotency_key)
    body = payload.model_dump()
    cached = await idempotency.replay(db, user.id, key, "POST /journal", body)
    if cached is not None:
        return cached[1]

    # Register C4 - reserve in the same transaction as the write (see moods).
    reservation = await idempotency.reserve(db, user.id, key, "POST /journal", body)

    entry = JournalEntry(user_id=user.id, **body)
    db.add(entry)
    await db.flush()
    # Proactive: crisis/safety scan on the written body.
    entry.risk_level = await safety.scan_and_record(
        db,
        user_id=user.id,
        source="journal",
        source_id=entry.id,
        text=f"{entry.title}\n{entry.body}",
        excerpt=entry.body or entry.title,
    )
    stored = JournalOut.model_validate(entry)
    if entry.risk_level in {"elevated", "crisis"}:
        # Register C70: /chat appends a region-aware suffix and /oracle sends
        # a crisis frame, but a risky journal POST answered with only the
        # label — every client hand-mirrored the hotline directory for this
        # one path. Same directory, same region logic, in the response.
        stored.resources = crisis.resources_for(user.region)
    idempotency.complete(reservation, 201, stored.model_dump(mode="json"))
    await db.commit()
    return stored


@router.put("/{entry_id}", response_model=JournalOut)
async def update_entry(
    entry_id: uuid.UUID,
    payload: JournalCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Replace an owned journal entry and re-run its safety scan."""
    entry = await db.get(JournalEntry, entry_id)
    if entry is None or entry.user_id != user.id:
        raise HTTPException(status_code=404, detail="Entry not found")
    for field, value in payload.model_dump().items():
        setattr(entry, field, value)
    entry.risk_level = await safety.scan_and_record(
        db,
        user_id=user.id,
        source="journal",
        source_id=entry.id,
        text=f"{entry.title}\n{entry.body}",
        excerpt=entry.body or entry.title,
    )
    stored = JournalOut.model_validate(entry)
    if entry.risk_level in {"elevated", "crisis"}:
        stored.resources = crisis.resources_for(user.region)
    await db.commit()
    return stored


@router.delete("/{entry_id}", status_code=204)
async def delete_entry(
    entry_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    entry = await db.get(JournalEntry, entry_id)
    if entry is None or entry.user_id != user.id:
        raise HTTPException(status_code=404, detail="Entry not found")
    await db.delete(entry)
    await db.commit()
