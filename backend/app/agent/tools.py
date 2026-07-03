"""Oracle tools.

Read tools run immediately. Write tools call ``interrupt()`` first so the client
can approve/decline (the ToolConfirmCard pattern); on approval they commit using
the request-scoped DB session from ``context``.
"""
from __future__ import annotations

import datetime as dt
from zoneinfo import ZoneInfo

from langchain_core.tools import tool
from langgraph.types import interrupt
from sqlalchemy import select

from app.agent.context import current_db, current_user_id, emitted_widgets
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.sleep import SleepLog
from app.models.user import User
from app.services import activities, insights, safety


@tool
async def suggest_activity(kind: str) -> str:
    """Offer the user a calming activity inline in the chat.

    kind must be one of: 'breathing', 'grounding', 'mood_check', 'mini_journal',
    'one_good_thing', 'intention_set', 'dbt_skill', 'sleep_checkin'. Use when the
    user seems anxious, overwhelmed, ruminating, low, grateful, hit by an intense
    urge (dbt_skill), or describing a rough night (sleep_checkin).
    """
    spec = activities.widget_for(kind)
    if spec is None:
        return (
            f"Unknown activity '{kind}'. Valid: breathing, grounding, mood_check, "
            "mini_journal, one_good_thing, intention_set, dbt_skill, sleep_checkin."
        )
    try:
        emitted_widgets.get().append(spec.model_dump())
    except LookupError:
        pass
    return f"Offered the '{spec.title}' activity card to the user."


@tool
async def get_weekly_insights() -> str:
    """Summarize the user's wellbeing trend over the past week."""
    db = current_db.get()
    user = await db.get(User, current_user_id.get())
    if user is None:
        return "No insights available."
    data = await insights.compute_weekly(db, user)
    return f"{data['headline']}: {data['summary']}"


@tool
async def log_mood(mood: str, note: str = "") -> str:
    """Record how the user is feeling right now. Confirms with the user first."""
    decision = interrupt({
        "tool": "log_mood",
        "summary": f"Log your mood as “{mood}”?",
        "args": {"mood": mood, "note": note},
    })
    if not (isinstance(decision, dict) and decision.get("approved")):
        return "The user declined to log their mood."
    db = current_db.get()
    uid = current_user_id.get()
    db.add(MoodLog(user_id=uid, mood=mood[:60], note=note[:255], symbol="sparkles", intensity=3))
    await db.commit()
    return f"Logged the user's mood as '{mood}'."


@tool
async def save_journal(title: str, body: str = "") -> str:
    """Save a short journal entry for the user. Confirms with the user first."""
    decision = interrupt({
        "tool": "save_journal",
        "summary": f"Save a journal entry titled “{title}”?",
        "args": {"title": title, "body": body},
    })
    if not (isinstance(decision, dict) and decision.get("approved")):
        return "The user declined to save the journal entry."
    db = current_db.get()
    uid = current_user_id.get()
    risk = await safety.scan_and_record(db, user_id=uid, source="journal", source_id=None, text=f"{title}\n{body}")
    db.add(JournalEntry(user_id=uid, title=title[:120], body=body, tags=[], symbol="book", risk_level=risk))
    await db.commit()
    return f"Saved the journal entry '{title}'."


@tool
async def log_sleep(quality: int, bedtime: str = "23:00", wake_time: str = "07:00", awakenings: int = 0) -> str:
    """Record last night's sleep diary for the user (felt quality 1–5, times as
    HH:MM). Use when the user describes how they slept. Confirms with the user
    first. One entry per morning — logging again edits today's entry."""
    decision = interrupt({
        "tool": "log_sleep",
        "summary": f"Log last night's sleep as {quality}/5?",
        "args": {"quality": quality, "bedtime": bedtime, "wake_time": wake_time, "awakenings": awakenings},
    })
    if not (isinstance(decision, dict) and decision.get("approved")):
        return "The user declined to log their sleep."

    def _parse(value: str, fallback: dt.time) -> dt.time:
        try:
            h, m = value.strip().split(":")[:2]
            return dt.time(int(h) % 24, int(m) % 60)
        except Exception:
            return fallback

    db = current_db.get()
    uid = current_user_id.get()
    user = await db.get(User, uid)
    try:
        tz = ZoneInfo((user.timezone if user else "") or "UTC")
    except Exception:
        tz = dt.timezone.utc
    today = dt.datetime.now(tz).date()
    q = max(1, min(5, int(quality)))
    bed = _parse(bedtime, dt.time(23, 0))
    wake = _parse(wake_time, dt.time(7, 0))
    woke = max(0, int(awakenings))

    existing = await db.scalar(select(SleepLog).where(SleepLog.user_id == uid, SleepLog.date == today))
    if existing:
        existing.quality, existing.bedtime, existing.wake_time, existing.awakenings = q, bed, wake, woke
    else:
        db.add(SleepLog(user_id=uid, date=today, bedtime=bed, wake_time=wake,
                        quality=q, awakenings=woke, source="manual"))
    await db.commit()
    return f"Logged last night's sleep ({q}/5)."


TOOLS = [suggest_activity, get_weekly_insights, log_mood, save_journal, log_sleep]
