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


# ── Pattern dashboard (transparent AI memory) ────────────────────────────
# Each statement is derived from the user's own last-60-days data and only
# emitted when that data actually supports it (counts + thresholds shown in
# `basis`). No LLM, no guesses — this is the "everything CereBro has learned
# about you" surface, so it must be embarrassingly honest.

_NEG_MOODS = {"anxious", "low", "tired", "stressed", "sad", "heavy", "overwhelmed"}


def _local_hour(dt, tzname: str) -> int:
    try:
        from zoneinfo import ZoneInfo
        return dt.astimezone(ZoneInfo(tzname or "UTC")).hour
    except Exception:
        return dt.hour


async def compute_patterns(db: AsyncSession, user: User) -> dict:
    since = utcnow() - timedelta(days=60)
    use_moods = consent_allows(user, "mood_history")
    use_journal = consent_allows(user, "journal_memory")
    use_sleep = consent_allows(user, "sleep_history")
    patterns: list[dict] = []

    moods = []
    if use_moods:
        moods = (
            await db.scalars(
                select(MoodLog).where(MoodLog.user_id == user.id, MoodLog.created_at >= since)
            )
        ).all()

    # 1) Hardest time of day — dominant bucket among difficult check-ins.
    neg = [m for m in moods if (m.mood or "").lower() in _NEG_MOODS or m.intensity >= 4]
    if len(neg) >= 6:
        buckets = {"Mornings": 0, "Afternoons": 0, "Evenings": 0}
        for m in neg:
            h = _local_hour(m.created_at, user.timezone)
            key = "Mornings" if h < 12 else "Afternoons" if h < 18 else "Evenings"
            buckets[key] += 1
        top, count = max(buckets.items(), key=lambda kv: kv[1])
        if count / len(neg) >= 0.5:
            patterns.append({
                "statement": f"{top} tend to be your hardest time of day.",
                "basis": f"{count} of your {len(neg)} difficult check-ins landed there",
            })

    # 2) Journaling → calmer next day (day-level share of difficult check-ins).
    if use_journal and moods:
        journal_dates = set(
            (
                await db.scalars(
                    select(JournalEntry.created_at).where(
                        JournalEntry.user_id == user.id, JournalEntry.created_at >= since
                    )
                )
            ).all()
        )
        journal_days = {d.date() for d in journal_dates}
        if len(journal_days) >= 3:
            def day_neg_share(day_filter):
                days: dict = {}
                for m in moods:
                    d = m.created_at.date()
                    if not day_filter(d):
                        continue
                    tot, bad = days.get(d, (0, 0))
                    days[d] = (tot + 1, bad + (1 if m in neg else 0))
                if len(days) < 3:
                    return None
                return sum(b / t for t, b in days.values()) / len(days)

            after = day_neg_share(lambda d: (d - timedelta(days=1)) in journal_days)
            other = day_neg_share(lambda d: (d - timedelta(days=1)) not in journal_days)
            if after is not None and other is not None and other - after >= 0.2:
                patterns.append({
                    "statement": "Check-ins run calmer the day after you journal.",
                    "basis": f"across {len(journal_days)} journaling days in the last 60",
                })

    # 3) Sleep → mood (same rested/short pairing as the weekly insight, wider window).
    if use_sleep and use_moods and moods:
        sleep_rows = (
            await db.scalars(
                select(SleepLog).where(
                    SleepLog.user_id == user.id, SleepLog.date >= (utcnow() - timedelta(days=60)).date()
                )
            )
        ).all()
        if len(sleep_rows) >= 5:
            by_date = {r.date: r.duration_min for r in sleep_rows}
            rested = [m.intensity for m in moods if by_date.get(m.created_at.date(), 0) >= 420]
            short = [m.intensity for m in moods if 0 < by_date.get(m.created_at.date(), 0) < 420]
            if len(rested) >= 3 and len(short) >= 3:
                gap = sum(short) / len(short) - sum(rested) / len(rested)
                if gap >= 0.5:
                    patterns.append({
                        "statement": "Mornings after 7+ hours in bed read calmer in your check-ins.",
                        "basis": f"{len(rested)} rested vs {len(short)} short-sleep mornings",
                    })

    # 4) Weekday rhythm — where the showing-up actually happens.
    if len(moods) >= 10:
        weekday = sum(1 for m in moods if m.created_at.weekday() < 5)
        share = weekday / len(moods)
        if share >= 0.8:
            patterns.append({
                "statement": "You show up most on weekdays — weekends drift.",
                "basis": f"{weekday} of {len(moods)} check-ins were Mon–Fri",
            })
        elif share <= 0.35:
            patterns.append({
                "statement": "Weekends are when you make time for this.",
                "basis": f"{len(moods) - weekday} of {len(moods)} check-ins were Sat–Sun",
            })

    return {
        "patterns": patterns,
        "enough_data": bool(patterns),
        "sources": {"mood_history": use_moods, "journal_memory": use_journal, "sleep_history": use_sleep},
    }
