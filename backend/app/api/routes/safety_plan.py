"""Personal safety plan — user-authored, versioned, never a gate.

Three rules this module exists to keep:

1. **The user writes it.** There is no path here for the model to persist a
   plan. A client may pre-fill a suggestion, but what arrives in a PUT is what
   the user chose to keep.
2. **Nothing here blocks anything.** A missing, empty or stale plan must never
   change what a crisis reply does. This is an aid the user prepared for
   themselves, not a precondition.
3. **Editing never destroys the last version.** Superseding archives; history
   stays readable. Someone editing while distressed must not be able to lose
   what they wrote while well.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db, utcnow
from app.core.deps import get_current_user
from app.models.safety_plan import SafetyPlan
from app.models.user import User
from app.schemas.content_data import SafetyPlanOut, SafetyPlanUpdate

router = APIRouter(prefix="/safety-plan", tags=["safety"])


async def _live_plan(db: AsyncSession, user: User) -> SafetyPlan | None:
    return await db.scalar(
        select(SafetyPlan)
        .where(SafetyPlan.user_id == user.id, SafetyPlan.archived_at.is_(None))
        .order_by(SafetyPlan.version.desc())
    )


@router.get("/me", response_model=SafetyPlanOut | None)
async def get_my_plan(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """The live plan, or null when there isn't one yet.

    Null is a normal state, not an error — clients render the invitation to
    write one and must stay usable either way.
    """
    return await _live_plan(db, user)


@router.put("/me", response_model=SafetyPlanOut)
async def upsert_my_plan(
    payload: SafetyPlanUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Save the plan. The first save creates version 1; each later save
    archives the previous version and writes the next one.

    Fields left unset carry over from the live plan, so a client can save one
    section at a time through the guided flow without blanking the rest.
    """
    current = await _live_plan(db, user)
    changes = payload.model_dump(exclude_unset=True)

    merged = {
        field: changes.get(field, getattr(current, field, "") if current else "")
        for field in (*SafetyPlan.SECTIONS, "notes")
    }

    if current is not None:
        # No-op saves must not spawn versions — a guided flow re-saves often.
        if all(merged[f] == getattr(current, f) for f in merged):
            return current
        current.archived_at = utcnow()

    plan = SafetyPlan(
        user_id=user.id,
        version=(current.version + 1) if current else 1,
        **merged,
    )
    db.add(plan)
    await db.commit()
    await db.refresh(plan)
    return plan


@router.get("/me/history", response_model=list[SafetyPlanOut])
async def my_plan_history(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """Every version, newest first — the live one included."""
    return (
        await db.scalars(
            select(SafetyPlan)
            .where(SafetyPlan.user_id == user.id)
            .order_by(SafetyPlan.version.desc())
        )
    ).all()


@router.delete("/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_my_plan(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """Delete the plan and its whole history.

    A real delete, not an archive: this is the user's own crisis material and
    "delete" has to mean it. Idempotent.
    """
    rows = (
        await db.scalars(select(SafetyPlan).where(SafetyPlan.user_id == user.id))
    ).all()
    for row in rows:
        await db.delete(row)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
