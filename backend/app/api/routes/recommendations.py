"""Suggestions derived from the user's own mined patterns.

See services/recommendations.py for why this is a curated catalogue rather than
generated advice, and why every suggestion carries its reason.
"""
from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.recommendation import PracticeCatalog, Recommendation
from app.models.user import User
from app.schemas.content_data import RecommendationOut
from app.services import recommendations as service

router = APIRouter(prefix="/recommendations", tags=["insights"])


async def _hydrate(db: AsyncSession, rows: list[Recommendation]) -> list[dict]:
    """Join the catalogue text onto each row. A recommendation whose practice
    was retired is dropped rather than rendered as a bare slug."""
    if not rows:
        return []
    slugs = {r.practice_slug for r in rows}
    catalogue = {
        c.slug: c
        for c in (
            await db.scalars(
                select(PracticeCatalog).where(PracticeCatalog.slug.in_(slugs))
            )
        ).all()
    }
    out = []
    for row in rows:
        practice = catalogue.get(row.practice_slug)
        if practice is None:
            continue
        out.append({
            "id": row.id,
            "slug": row.practice_slug,
            "title": practice.title,
            "body": practice.body,
            "action": practice.action,
            "reason": row.reason,
            "status": row.status,
        })
    return out


@router.get("/mine", response_model=list[RecommendationOut])
async def my_recommendations(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """Pending suggestions, seeding from current patterns on the way.

    Seeding on read keeps the client simple and costs one pattern computation —
    the same one the Pattern Dashboard already does.
    """
    await service.seed_for(db, user)
    await db.commit()
    rows = (
        await db.scalars(
            select(Recommendation)
            .where(Recommendation.user_id == user.id, Recommendation.status == "pending")
            .order_by(Recommendation.created_at.desc())
        )
    ).all()
    return await _hydrate(db, list(rows))


@router.post("/{rec_id}/accept", response_model=RecommendationOut)
async def accept(
    rec_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    row = await service.resolve(db, user, rec_id, "accepted")
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    return (await _hydrate(db, [row]))[0]


@router.post("/{rec_id}/dismiss", response_model=RecommendationOut)
async def dismiss(
    rec_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Dismissing is a real signal — the practice is not offered again."""
    row = await service.resolve(db, user, rec_id, "dismissed")
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    return (await _hydrate(db, [row]))[0]
