"""Sleep-diary aggregates (docs/SLEEP_TRACKING.md).

Awareness-framing rule: these are trends over self-reported diary entries, never
measurements — every consumer must respect `enough_data` before showing numbers.
"""
from __future__ import annotations

import statistics
from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.sleep import SleepLog
from app.models.user import User

MIN_NIGHTS = 3


def _minutes_since_noon(t) -> int:
    """Bedtimes straddle midnight; anchoring at noon keeps 23:00 and 01:00 contiguous."""
    return (t.hour * 60 + t.minute - 12 * 60) % (24 * 60)


async def weekly_summary(db: AsyncSession, user: User, days: int = 7, today: date | None = None) -> dict:
    since = (today or date.today()) - timedelta(days=days - 1)
    rows = (
        await db.scalars(
            select(SleepLog)
            .where(SleepLog.user_id == user.id, SleepLog.date >= since)
            .order_by(SleepLog.date.asc())
        )
    ).all()

    nights = len(rows)
    if nights < MIN_NIGHTS:
        return {
            "nights": nights,
            "enough_data": False,
            "avg_duration_min": 0,
            "avg_quality": 0.0,
            "bedtime_consistency_min": 0,
            "trend": "not_enough_data",
        }

    durations = [r.duration_min for r in rows]
    qualities = [r.quality for r in rows]
    bedtimes = [_minutes_since_noon(r.bedtime) for r in rows]

    half = nights // 2
    early, late = qualities[:half], qualities[nights - half :]
    delta = statistics.mean(late) - statistics.mean(early)
    trend = "improving" if delta > 0.5 else "declining" if delta < -0.5 else "steady"

    return {
        "nights": nights,
        "enough_data": True,
        "avg_duration_min": round(statistics.mean(durations)),
        "avg_quality": round(statistics.mean(qualities), 1),
        "bedtime_consistency_min": round(statistics.pstdev(bedtimes)),
        "trend": trend,
    }
