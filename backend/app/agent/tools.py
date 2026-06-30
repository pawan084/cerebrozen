"""Oracle tools.

Read tools run immediately. Write tools call ``interrupt()`` first so the client
can approve/decline (the ToolConfirmCard pattern); on approval they commit using
the request-scoped DB session from ``context``.
"""
from __future__ import annotations

from langchain_core.tools import tool
from langgraph.types import interrupt

from app.agent.context import current_db, current_user_id, emitted_widgets
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.user import User
from app.services import activities, insights, safety


@tool
async def suggest_activity(kind: str) -> str:
    """Offer the user a calming activity inline in the chat.

    kind must be one of: 'breathing', 'grounding', 'mood_check', 'mini_journal',
    'one_good_thing', 'intention_set', 'dbt_skill'. Use when the user seems
    anxious, overwhelmed, ruminating, low, grateful, or hit by an intense urge
    (dbt_skill).
    """
    spec = activities.widget_for(kind)
    if spec is None:
        return (
            f"Unknown activity '{kind}'. Valid: breathing, grounding, mood_check, "
            "mini_journal, one_good_thing, intention_set, dbt_skill."
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


TOOLS = [suggest_activity, get_weekly_insights, log_mood, save_journal]
