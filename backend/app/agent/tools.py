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

from app.agent.context import current_db, current_risk, current_thread_id, current_user_id, emitted_widgets
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.sleep import SleepLog
from app.models.user import User
from app.services import activities, insights, oracle_audit, safety


def write_suppressed(risk: str) -> bool:
    """Whether write tools must stand down for this turn. During an elevated or
    crisis turn the agent runs READ-ONLY: proposing "Log your mood?" between a
    disclosure and its support is the interleaving the reference app's clinical
    review flagged (finding C1). Pure — pinned in tests."""
    return risk in {"elevated", "crisis"}


_WRITE_SUPPRESSED_REPLY = (
    "Not now — the user needs support in this moment, not record-keeping. "
    "Stay with them; do not propose saving or logging anything this turn."
)


async def _audit_read(tool: str, args: dict | None = None) -> None:
    """Record a read tool. Never let auditing break a tool: observability is
    not worth failing the user's turn over."""
    try:
        await oracle_audit.record_read(
            current_db.get(), user_id=current_user_id.get(),
            thread_id=current_thread_id.get(""), tool=tool, args=args,
        )
    except Exception:  # noqa: BLE001 - pragma: no cover
        pass


async def _audit_open(tool: str, args: dict | None = None) -> None:
    """Mark a write tool pending, BEFORE its interrupt() suspends the graph."""
    try:
        await oracle_audit.open_pending(
            current_db.get(), user_id=current_user_id.get(),
            thread_id=current_thread_id.get(""), tool=tool, args=args,
        )
    except Exception:  # noqa: BLE001 - pragma: no cover
        pass


async def _audit_resolve(tool: str, approved: bool) -> None:
    try:
        await oracle_audit.resolve(
            current_db.get(), thread_id=current_thread_id.get(""),
            tool=tool, approved=approved,
        )
    except Exception:  # noqa: BLE001 - pragma: no cover
        pass


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
    await _audit_read("suggest_activity", {"kind": kind})
    return f"Offered the '{spec.title}' activity card to the user."


@tool
async def get_weekly_insights() -> str:
    """Summarize the user's wellbeing trend over the past week."""
    db = current_db.get()
    user = await db.get(User, current_user_id.get())
    if user is None:
        return "No insights available."
    data = await insights.compute_weekly(db, user)
    await _audit_read("get_weekly_insights")
    return f"{data['headline']}: {data['summary']}"


@tool
async def log_mood(mood: str, note: str = "") -> str:
    """Record how the user is feeling right now. Confirms with the user first."""
    if write_suppressed(current_risk.get()):
        return _WRITE_SUPPRESSED_REPLY
    await _audit_open("log_mood", {"mood": mood, "note": note})
    decision = interrupt({
        "tool": "log_mood",
        "summary": f"Log your mood as “{mood}”?",
        "args": {"mood": mood, "note": note},
    })
    approved = isinstance(decision, dict) and bool(decision.get("approved"))
    await _audit_resolve("log_mood", approved)
    if not approved:
        return "The user declined to log their mood."
    db = current_db.get()
    uid = current_user_id.get()
    log = MoodLog(user_id=uid, mood=mood[:60], note=note[:255], symbol="sparkles", intensity=3)
    db.add(log)
    await db.flush()
    # Register C68: this is the one write path where the MODEL chose the note
    # text, and it skipped the safety pipeline while /moods scans (wave 11).
    # Safety never blocks — the mood is logged either way; a crisis-worded
    # note now produces the same event/escalation it would anywhere else.
    if note.strip():
        await safety.scan_and_record(
            db, user_id=uid, source="mood", source_id=log.id,
            text=note, excerpt=note[:200],
        )
    await db.commit()
    return f"Logged the user's mood as '{mood}'."


@tool
async def save_journal(title: str, body: str = "") -> str:
    """Save a short journal entry for the user. Confirms with the user first."""
    if write_suppressed(current_risk.get()):
        return _WRITE_SUPPRESSED_REPLY
    await _audit_open("save_journal", {"title": title, "body": body})
    decision = interrupt({
        "tool": "save_journal",
        "summary": f"Save a journal entry titled “{title}”?",
        "args": {"title": title, "body": body},
    })
    approved = isinstance(decision, dict) and bool(decision.get("approved"))
    await _audit_resolve("save_journal", approved)
    if not approved:
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
    if write_suppressed(current_risk.get()):
        return _WRITE_SUPPRESSED_REPLY
    _args = {"quality": quality, "bedtime": bedtime, "wake_time": wake_time, "awakenings": awakenings}
    await _audit_open("log_sleep", _args)
    decision = interrupt({
        "tool": "log_sleep",
        "summary": f"Log last night's sleep as {quality}/5?",
        "args": _args,
    })
    approved = isinstance(decision, dict) and bool(decision.get("approved"))
    await _audit_resolve("log_sleep", approved)
    if not approved:
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
