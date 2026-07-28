"""Interventions — "here's something that might help, and here's why".

Evaluation is **lazy**: the client asks for its active offer and the engine
decides then. No background job invents suggestions between visits, so what a
user sees is always derived from data they can see too.
"""
from __future__ import annotations

import uuid
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.intervention import InterventionRecommendation
from app.models.user import User
from app.services import interventions

router = APIRouter(prefix="/interventions", tags=["interventions"])


class RecommendationOut(BaseModel):
    id: uuid.UUID
    rule_slug: str
    tier: str
    reason: str
    action_kind: str
    action_target: str
    state_snapshot: dict
    created_at: datetime
    accepted_at: datetime | None
    dismissed_at: datetime | None
    completed_at: datetime | None

    model_config = {"from_attributes": True}


@router.get("/active", response_model=RecommendationOut | None)
async def active(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    """The offer currently on the table, evaluating one if none is open.

    Returns null — not 404 — when nothing fires. Having no suggestion is the
    normal, healthy case, not a missing resource.
    """
    return await interventions.evaluate(db, user)


@router.get("", response_model=list[RecommendationOut])
async def history(
    limit: int = 20, user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """Past offers, newest first — what the app suggested and what you did with
    it. Part of the same transparency posture as the Pattern Dashboard."""
    rows = await db.scalars(
        select(InterventionRecommendation)
        .where(InterventionRecommendation.user_id == user.id)
        .order_by(InterventionRecommendation.created_at.desc())
        .limit(max(1, min(100, limit)))
    )
    return list(rows.all())


async def _resolve(action: str, rec_id: uuid.UUID, user: User, db: AsyncSession):
    rec = await interventions.resolve(db, user, rec_id, action=action)
    if rec is None:
        raise HTTPException(status_code=404, detail="Recommendation not found")
    return rec


@router.post("/{rec_id}/accept", response_model=RecommendationOut)
async def accept(rec_id: uuid.UUID, user: User = Depends(get_current_user),
                 db: AsyncSession = Depends(get_db)):
    return await _resolve("accept", rec_id, user, db)


@router.post("/{rec_id}/dismiss", response_model=RecommendationOut)
async def dismiss(rec_id: uuid.UUID, user: User = Depends(get_current_user),
                  db: AsyncSession = Depends(get_db)):
    """Dismissing is final for this offer and starts the rule's cooldown — a
    suggestion that returns as soon as it's waved away is nagging."""
    return await _resolve("dismiss", rec_id, user, db)


@router.post("/{rec_id}/complete", response_model=RecommendationOut)
async def complete(rec_id: uuid.UUID, user: User = Depends(get_current_user),
                   db: AsyncSession = Depends(get_db)):
    return await _resolve("complete", rec_id, user, db)
