"""The individual's endpoints: identity, export, deletion.

Deletion is a product function, not a support ticket: PII is scrubbed in
place, sessions are revoked, and a no-PII ledger row proves it happened.
(The engine holds coaching content and has its own erasure endpoint; the
admin/ops runbook calls both.)"""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.deps import current_user
from app.models import DeletionLedger, Org, RefreshToken, User
from app.security import hash_email

router = APIRouter(prefix="/users", tags=["users"])


@router.get("/me")
async def me(
    user: User = Depends(current_user), session: AsyncSession = Depends(get_session)
):
    org_name = None
    if user.org_id:
        org = (
            await session.execute(select(Org).where(Org.id == user.org_id))
        ).scalar_one_or_none()
        org_name = org.name if org else None
    return {
        "id": user.id,
        "email": user.email,
        "name": user.name,
        "role": user.role,
        "org_id": user.org_id,
        "org_name": org_name,
    }


@router.get("/me/export")
async def export(
    user: User = Depends(current_user), session: AsyncSession = Depends(get_session)
):
    """Everything the PLATFORM holds about this person, as one JSON document."""
    sessions_count = len(
        (
            await session.execute(
                select(RefreshToken).where(
                    RefreshToken.user_id == user.id, RefreshToken.revoked.is_(False)
                )
            )
        ).scalars().all()
    )
    return {
        "profile": {
            "id": user.id,
            "email": user.email,
            "name": user.name,
            "role": user.role,
            "org_id": user.org_id,
            "created_at": str(user.created_at),
        },
        "active_sessions": sessions_count,
        "note": "Coaching content is exported from the coaching engine (/v1/privacy/me/export).",
    }


@router.delete("/me", status_code=204)
async def delete_me(
    confirm: bool = Query(default=False),
    user: User = Depends(current_user),
    session: AsyncSession = Depends(get_session),
):
    if not confirm:
        raise HTTPException(400, "pass ?confirm=true to delete your account")
    session.add(
        DeletionLedger(user_id=user.id, org_id=user.org_id, email_hash=hash_email(user.email))
    )
    # Scrub in place: the row survives as a tombstone so foreign keys stay
    # valid, but no PII remains and the account can never log in again.
    user.email = f"deleted-{user.id}@deleted.invalid"
    user.name = ""
    user.password_hash = "deleted"
    user.is_active = False
    await session.execute(
        update(RefreshToken).where(RefreshToken.user_id == user.id).values(revoked=True)
    )
    await session.commit()
