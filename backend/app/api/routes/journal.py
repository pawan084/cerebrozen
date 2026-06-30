import uuid

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.journal import JournalEntry
from app.models.user import User
from app.schemas.content_data import JournalCreate, JournalOut
from app.services import safety

router = APIRouter(prefix="/journal", tags=["journal"])


@router.get("", response_model=list[JournalOut])
async def list_entries(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    rows = await db.scalars(
        select(JournalEntry).where(JournalEntry.user_id == user.id).order_by(JournalEntry.created_at.desc())
    )
    return rows.all()


@router.post("", response_model=JournalOut, status_code=201)
async def create_entry(
    payload: JournalCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    entry = JournalEntry(user_id=user.id, **payload.model_dump())
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
    await db.commit()
    await db.refresh(entry)
    return entry


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
