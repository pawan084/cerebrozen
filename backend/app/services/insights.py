"""Weekly insight computation from a user's recent activity."""
from __future__ import annotations

from datetime import timedelta

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.plan import Plan, PlanStep
from app.models.user import User


async def _count(db: AsyncSession, model, user_id, since) -> int:
    return (
        await db.scalar(
            select(func.count()).select_from(model).where(model.user_id == user_id, model.created_at >= since)
        )
    ) or 0


async def compute_weekly(db: AsyncSession, user: User) -> dict:
    since = utcnow() - timedelta(days=7)

    mood_count = await _count(db, MoodLog, user.id, since)
    journal_count = await _count(db, JournalEntry, user.id, since)

    steps_done = (
        await db.scalar(
            select(func.count())
            .select_from(PlanStep)
            .join(Plan, Plan.id == PlanStep.plan_id)
            .where(Plan.user_id == user.id, PlanStep.done.is_(True), PlanStep.done_at >= since)
        )
    ) or 0

    # Average intensity → mood stability (lower intensity of difficult moods = steadier).
    avg_intensity = await db.scalar(
        select(func.avg(MoodLog.intensity)).where(MoodLog.user_id == user.id, MoodLog.created_at >= since)
    )
    stability = 0.7 if avg_intensity is None else max(0.2, min(1.0, 1.2 - float(avg_intensity) / 5))

    sessions = mood_count + steps_done

    metrics = [
        {"label": "Calm sessions", "value": str(sessions), "progress": min(1.0, sessions / 12)},
        {"label": "Journal entries", "value": str(journal_count), "progress": min(1.0, journal_count / 8)},
        {"label": "Mood stability", "value": "Steady" if stability >= 0.6 else "Variable", "progress": stability},
        {
            "label": "Plan follow-through",
            "value": str(steps_done),
            "progress": min(1.0, steps_done / 9),
        },
    ]

    if journal_count and sessions:
        headline = "Calmer evenings"
        summary = "Your stress eased on days you checked in and journaled before bed."
    elif sessions:
        headline = "Building a rhythm"
        summary = "A few calm sessions this week — consistency is starting to form."
    else:
        headline = "A fresh start"
        summary = "No activity logged yet this week. One small check-in is a good first step."

    return {"period": "weekly", "headline": headline, "summary": summary, "metrics": metrics}
