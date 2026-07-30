"""Turning mined patterns into something the user can act on.

PRD §3 listed "an interventions engine that acts on mined patterns" as
deliberately deferred — patterns were display-only. This is the smallest honest
version of that: a hand-authored practice catalogue, a rule mapping a pattern to
a practice, and an accept/dismiss loop.

Three deliberate limits, because this is advice about someone's mental health:

1. **Nothing is generated.** Practices are curated rows an admin can edit or
   retire. A model never writes the suggestion text.
2. **Every suggestion carries its reason** — the pattern statement that produced
   it, verbatim. No suggestion appears without a visible basis.
3. **It only fires on patterns the miner already vouched for.** `compute_patterns`
   thresholds its rules and returns nothing below them, so a thin-data user gets
   no recommendations rather than generic filler.
"""
from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.recommendation import PracticeCatalog, Recommendation
from app.models.user import User
from app.services import insights as insights_service

# Pattern statement (as produced by compute_patterns) → practice slug.
# Matching is on a distinctive fragment rather than the whole sentence so
# rewording the copy doesn't silently switch the engine off.
_RULES: list[tuple[str, str]] = [
    ("hardest time of day", "anchor-the-hard-hour"),
    ("calmer the day after you journal", "keep-the-journal-streak"),
    ("7+ hours in bed", "protect-the-wind-down"),
    ("weekends drift", "one-weekend-anchor"),
    ("weekends are when you make time", "carry-one-into-the-week"),
]


def practice_for(statement: str) -> str | None:
    lowered = statement.lower()
    for fragment, slug in _RULES:
        if fragment in lowered:
            return slug
    return None


async def seed_for(db: AsyncSession, user: User) -> list[Recommendation]:
    """Create pending recommendations for this user's current patterns.

    Idempotent in the way that matters: a practice already pending, accepted or
    dismissed is not offered again. Dismissing therefore sticks — the opposite
    would make "not for me" mean "ask me tomorrow".
    """
    mined = await insights_service.compute_patterns(db, user)
    if not mined["patterns"]:
        return []

    existing = {
        row.practice_slug
        for row in (
            await db.scalars(
                select(Recommendation).where(Recommendation.user_id == user.id)
            )
        ).all()
    }
    available = {
        row.slug
        for row in (
            await db.scalars(
                select(PracticeCatalog).where(PracticeCatalog.active.is_(True))
            )
        ).all()
    }

    created: list[Recommendation] = []
    for pattern in mined["patterns"]:
        slug = practice_for(pattern["statement"])
        if slug is None or slug in existing or slug not in available:
            continue
        row = Recommendation(
            user_id=user.id,
            practice_slug=slug,
            reason=pattern["statement"],
        )
        db.add(row)
        created.append(row)
        existing.add(slug)
    if created:
        await db.flush()
    return created


async def resolve(
    db: AsyncSession, user: User, rec_id, status: str
) -> Recommendation | None:
    """Accept or dismiss. Returns None when the row isn't this user's."""
    row = await db.get(Recommendation, rec_id)
    if row is None or row.user_id != user.id:
        return None
    row.status = status
    row.resolved_at = utcnow()
    await db.commit()
    await db.refresh(row)
    return row


async def acceptance_stats(db: AsyncSession) -> list[dict]:
    """Per-practice accept/dismiss counts for admin.

    Counts only — which practice lands, never who was offered what.
    """
    rows = (await db.scalars(select(Recommendation))).all()
    by_slug: dict[str, dict] = {}
    for row in rows:
        entry = by_slug.setdefault(
            row.practice_slug,
            {"slug": row.practice_slug, "offered": 0, "accepted": 0, "dismissed": 0},
        )
        entry["offered"] += 1
        if row.status == "accepted":
            entry["accepted"] += 1
        elif row.status == "dismissed":
            entry["dismissed"] += 1
    return sorted(by_slug.values(), key=lambda e: -e["offered"])
