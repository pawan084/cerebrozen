"""First-party, privacy-preserving product metrics (admin analytics).

The "no third-party trackers" promise holds: everything here is aggregate SQL
over our own tables — counts, cohorts, and rates computed server-side. No
events leave the platform and no message/journal content is read
(docs/INVESTOR_READINESS.md gap #1).
"""
from __future__ import annotations

import uuid
from datetime import date, timedelta

from sqlalchemy import func, select, union_all
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.chat import ChatMessage
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.plan import Plan, PlanStep
from app.models.sleep import SleepLog
from app.models.user import User

#: "Active" = wrote any of: mood, journal, sleep entry, or a user chat message.
_WINDOW_DAYS = 35


async def _activity_days(db: AsyncSession) -> dict[uuid.UUID, set[date]]:
    """Distinct (user, day) activity pairs over the retention window."""
    since = utcnow() - timedelta(days=_WINDOW_DAYS)
    selects = [
        select(m.user_id, func.date(m.created_at)).where(m.created_at >= since)
        for m in (MoodLog, JournalEntry, SleepLog)
    ]
    selects.append(
        select(ChatMessage.user_id, func.date(ChatMessage.created_at)).where(
            ChatMessage.created_at >= since, ChatMessage.role == "user"
        )
    )
    rows = (await db.execute(union_all(*selects))).all()
    days: dict[uuid.UUID, set[date]] = {}
    for user_id, day in rows:
        days.setdefault(user_id, set()).add(day)
    return days


async def overview(db: AsyncSession) -> dict:
    now = utcnow()
    today = now.date()
    activity = await _activity_days(db)

    def actives(window: int) -> int:
        floor = today - timedelta(days=window - 1)
        return sum(1 for days in activity.values() if any(d >= floor for d in days))

    # Signups.
    async def signups_since(days: int | None) -> int:
        stmt = select(func.count()).select_from(User)
        if days is not None:
            stmt = stmt.where(User.created_at >= now - timedelta(days=days))
        return (await db.scalar(stmt)) or 0

    # Classic Dn retention: of users old enough to have a day-n, how many were
    # active exactly n days after signup.
    signup_rows = (
        await db.execute(
            select(User.id, func.date(User.created_at)).where(
                User.created_at >= now - timedelta(days=_WINDOW_DAYS)
            )
        )
    ).all()

    def retention(n: int) -> dict:
        cohort = [(uid, d) for uid, d in signup_rows if d <= today - timedelta(days=n)]
        retained = sum(1 for uid, d in cohort if d + timedelta(days=n) in activity.get(uid, set()))
        return {
            "cohort": len(cohort),
            "retained": retained,
            "rate": round(retained / len(cohort), 3) if cohort else None,
        }

    # Engagement volume, trailing 7 days.
    week_ago = now - timedelta(days=7)

    async def count_since(model, *extra) -> int:
        stmt = select(func.count()).select_from(model).where(model.created_at >= week_ago, *extra)
        return (await db.scalar(stmt)) or 0

    steps_done_7d = (
        await db.scalar(
            select(func.count())
            .select_from(PlanStep)
            .join(Plan, Plan.id == PlanStep.plan_id)
            .where(PlanStep.done.is_(True), PlanStep.done_at >= week_ago)
        )
    ) or 0

    # Lifetime activation funnel (distinct users who ever did each thing).
    async def ever(model) -> int:
        return (await db.scalar(select(func.count(func.distinct(model.user_id))))) or 0

    premium = (
        await db.scalar(
            select(func.count()).select_from(User).where(
                User.subscription_tier.in_(["premium", "premium_human"])
            )
        )
    ) or 0

    return {
        "actives": {"dau": actives(1), "wau": actives(7), "mau": actives(30)},
        "signups": {
            "d7": await signups_since(7),
            "d30": await signups_since(30),
            "total": await signups_since(None),
        },
        "retention": {"d1": retention(1), "d7": retention(7), "d30": retention(30)},
        "engagement_7d": {
            "mood_logs": await count_since(MoodLog),
            "journal_entries": await count_since(JournalEntry),
            "chat_messages": await count_since(ChatMessage, ChatMessage.role == "user"),
            "sleep_logs": await count_since(SleepLog),
            "plan_steps_done": steps_done_7d,
        },
        "funnel": {
            "signups": await signups_since(None),
            "mood": await ever(MoodLog),
            "journal": await ever(JournalEntry),
            "sleep": await ever(SleepLog),
            "premium": premium,
        },
    }
