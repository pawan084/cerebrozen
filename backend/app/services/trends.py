"""Day-by-day mood and sleep series for the Trends screen.

The rule this module exists under is the one the 2026-07-31 insights audit was
written after: *a reading was invented from no check-ins*. So every number here
is gated by the data that produced it, and the gates are part of the response
rather than a client-side convention:

* a day with no check-in is **absent** from the series, never a zero — a flat
  line at the bottom of a chart reads as "I felt terrible", not "I didn't open
  the app";
* `enough_data` is false until the minimums below are met, and the client shows
  the empty state instead of a chart;
* the mood↔sleep correlation is withheld, with a machine-readable `reason`,
  until there are enough overlapping days for the number to mean anything. Two
  points always correlate perfectly, which is exactly why two points must never
  be shown as a finding.

Consent is honoured the same way the pattern dashboard honours it: a user who
turned off mood or sleep memory gets an empty series for that half, not a
silently-still-computed one.
"""
from __future__ import annotations

import statistics
from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.consent import consent_allows
from app.models.mood import MoodLog
from app.models.sleep import SleepLog
from app.models.user import User

# Below these, the series is shown but not summarised.
MIN_MOOD_DAYS = 3
MIN_SLEEP_NIGHTS = 3
# Correlation needs enough paired nights to be worth a sentence.
MIN_CORRELATION_PAIRS = 7

# Same taxonomy the pattern dashboard uses (app.services.insights._NEG_MOODS),
# duplicated deliberately: this module must not import from that one just to
# borrow a set, and the two lists are asserted equal in the tests.
NEG_MOODS = {"anxious", "low", "tired", "stressed", "sad", "heavy", "overwhelmed"}


def ease_score(mood: str, intensity: int) -> float:
    """One check-in as a 1–5 "how easy did that feel" number.

    A mood label is not a magnitude — "Anxious at 5" and "Good at 5" are not
    the same 5 — so intensity alone cannot be charted. Difficult moods invert:
    intensity 5 of a hard feeling is the roughest end of the scale, intensity 5
    of a settled one is the easiest. Unknown labels (a future mood, another
    client's vocabulary) are treated as neutral rather than guessed at.
    """
    clamped = max(1, min(int(intensity or 3), 5))
    if (mood or "").lower() in NEG_MOODS:
        return float(6 - clamped)
    return float(clamped)


def _pearson(xs: list[float], ys: list[float]) -> float | None:
    """Correlation coefficient, or None when either side never varies.

    A constant series has zero standard deviation; the formula divides by it.
    Someone who logged "Good" every day genuinely has no correlation to report,
    and reporting 0.0 would read as "no link found" rather than "not knowable".
    """
    if len(xs) < 2:
        return None
    sx, sy = statistics.pstdev(xs), statistics.pstdev(ys)
    if sx == 0 or sy == 0:
        return None
    mx, my = statistics.mean(xs), statistics.mean(ys)
    cov = sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / len(xs)
    return round(cov / (sx * sy), 2)


def _direction(coefficient: float) -> str:
    if coefficient >= 0.3:
        return "better_sleep_easier_days"
    if coefficient <= -0.3:
        return "better_sleep_harder_days"
    return "no_clear_link"


async def compute(
    db: AsyncSession, user: User, days: int = 30, today: date | None = None
) -> dict:
    """Build the trends payload for the last `days` days (inclusive of today)."""
    days = max(7, min(days, 180))
    end = today or utcnow().date()
    start = end - timedelta(days=days - 1)

    mood_points: list[dict] = []
    mood_days = 0
    if consent_allows(user, "mood_history"):
        rows = (
            await db.scalars(
                select(MoodLog)
                .where(MoodLog.user_id == user.id, MoodLog.created_at >= _as_dt(start))
                .order_by(MoodLog.created_at.asc())
            )
        ).all()
        by_day: dict[date, list[float]] = {}
        labels: dict[date, str] = {}
        for row in rows:
            day = row.created_at.date()
            by_day.setdefault(day, []).append(ease_score(row.mood, row.intensity))
            labels.setdefault(day, row.mood)
        mood_days = len(by_day)
        mood_points = [
            {
                "date": day.isoformat(),
                "ease": round(statistics.mean(scores), 2),
                "checkins": len(scores),
                "mood": labels[day],
            }
            for day, scores in sorted(by_day.items())
        ]

    sleep_points: list[dict] = []
    nights = 0
    if consent_allows(user, "sleep_history"):
        logs = (
            await db.scalars(
                select(SleepLog)
                .where(SleepLog.user_id == user.id, SleepLog.date >= start, SleepLog.date <= end)
                .order_by(SleepLog.date.asc())
            )
        ).all()
        nights = len(logs)
        sleep_points = [
            {
                "date": log.date.isoformat(),
                "duration_min": log.duration_min,
                "quality": log.quality,
            }
            for log in logs
        ]

    mood_enough = mood_days >= MIN_MOOD_DAYS
    sleep_enough = nights >= MIN_SLEEP_NIGHTS

    # Pair each night with the *following* day's check-ins: the question a user
    # asks is "does a good night make the next day easier", not the reverse.
    ease_by_day = {p["date"]: p["ease"] for p in mood_points}
    pairs = [
        (float(sp["duration_min"]), ease_by_day[_next_day(sp["date"])])
        for sp in sleep_points
        if _next_day(sp["date"]) in ease_by_day
    ]
    correlation: dict = {
        "available": False,
        "pairs": len(pairs),
        "coefficient": None,
        "direction": None,
        "reason": "needs_more_days",
    }
    if len(pairs) >= MIN_CORRELATION_PAIRS:
        coefficient = _pearson([p[0] for p in pairs], [p[1] for p in pairs])
        if coefficient is None:
            correlation["reason"] = "no_variation"
        else:
            correlation = {
                "available": True,
                "pairs": len(pairs),
                "coefficient": coefficient,
                "direction": _direction(coefficient),
                "reason": None,
            }

    return {
        "days": days,
        "start": start.isoformat(),
        "end": end.isoformat(),
        "mood": {
            "points": mood_points,
            "days_logged": mood_days,
            "enough_data": mood_enough,
            "average_ease": round(
                statistics.mean([p["ease"] for p in mood_points]), 2
            ) if mood_enough else None,
        },
        "sleep": {
            "points": sleep_points,
            "nights": nights,
            "enough_data": sleep_enough,
            "avg_duration_min": round(
                statistics.mean([p["duration_min"] for p in sleep_points])
            ) if sleep_enough else None,
        },
        "correlation": correlation,
    }


def _as_dt(day: date):
    from datetime import datetime, time, timezone

    return datetime.combine(day, time.min, tzinfo=timezone.utc)


def _next_day(iso: str) -> str:
    return (date.fromisoformat(iso) + timedelta(days=1)).isoformat()
