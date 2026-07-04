"""Weekly insight computation from a user's recent activity."""
from __future__ import annotations

from datetime import timedelta

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.consent import consent_allows
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.plan import Plan, PlanStep
from app.models.sleep import SleepLog
from app.models.user import User


async def _count(db: AsyncSession, model, user_id, since) -> int:
    return (
        await db.scalar(
            select(func.count()).select_from(model).where(model.user_id == user_id, model.created_at >= since)
        )
    ) or 0


async def compute_weekly(db: AsyncSession, user: User) -> dict:
    since = utcnow() - timedelta(days=7)

    # DPDP itemization: each data category only feeds insights while its own
    # consent flag is on — switched off, the category reads as "no data".
    use_moods = consent_allows(user, "mood_history")
    use_journal = consent_allows(user, "journal_memory")
    use_sleep = consent_allows(user, "sleep_history")

    mood_count = await _count(db, MoodLog, user.id, since) if use_moods else 0
    journal_count = await _count(db, JournalEntry, user.id, since) if use_journal else 0

    steps_done = (
        await db.scalar(
            select(func.count())
            .select_from(PlanStep)
            .join(Plan, Plan.id == PlanStep.plan_id)
            .where(Plan.user_id == user.id, PlanStep.done.is_(True), PlanStep.done_at >= since)
        )
    ) or 0

    # Average intensity → mood stability (lower intensity of difficult moods = steadier).
    avg_intensity = None
    if use_moods:
        avg_intensity = await db.scalar(
            select(func.avg(MoodLog.intensity)).where(MoodLog.user_id == user.id, MoodLog.created_at >= since)
        )
    stability = 0.7 if avg_intensity is None else max(0.2, min(1.0, 1.2 - float(avg_intensity) / 5))

    sessions = mood_count + steps_done

    # Sleep: real diary aggregates (replaces the old illustrative strings).
    sleep_rows = []
    if use_sleep:
        sleep_rows = (
            await db.scalars(
                select(SleepLog).where(SleepLog.user_id == user.id, SleepLog.date >= (utcnow() - timedelta(days=6)).date())
            )
        ).all()
    if sleep_rows:
        avg_sleep = sum(r.duration_min for r in sleep_rows) // len(sleep_rows)
        sleep_value = f"{avg_sleep // 60}h {avg_sleep % 60:02d}m avg"
        sleep_progress = min(1.0, avg_sleep / 480)
    else:
        sleep_value, sleep_progress = "No diary yet", 0.0

    metrics = [
        {"label": "Calm sessions", "value": str(sessions), "progress": min(1.0, sessions / 12)},
        {"label": "Journal entries", "value": str(journal_count), "progress": min(1.0, journal_count / 8)},
        {"label": "Sleep", "value": sleep_value, "progress": sleep_progress},
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

    # Sleep × mood: only claim a link when this week's own data supports it
    # (both buckets populated and a real gap). UTC-date matching is a stated
    # approximation — mood "today" pairs with the wake-morning diary entry.
    if len(sleep_rows) >= 3 and use_moods:
        mood_rows = (
            await db.scalars(
                select(MoodLog).where(MoodLog.user_id == user.id, MoodLog.created_at >= since)
            )
        ).all()
        by_date = {r.date: r.duration_min for r in sleep_rows}
        rested = [m.intensity for m in mood_rows if by_date.get(m.created_at.date(), 0) >= 420]
        short = [m.intensity for m in mood_rows if 0 < by_date.get(m.created_at.date(), 0) < 420]
        if len(rested) >= 2 and len(short) >= 2:
            gap = sum(short) / len(short) - sum(rested) / len(rested)
            if gap >= 0.5:
                summary += (
                    " Mood also ran steadier on mornings after 7+ hours in bed — "
                    "worth protecting the wind-down."
                )

    return {"period": "weekly", "headline": headline, "summary": summary, "metrics": metrics}
