"""Agentic daily-plan generation.

Builds a short, personalised plan from the user's goals + recent mood/journal
signals. Uses Claude when available; otherwise composes from curated templates
keyed off the user's primary goal and recent stress level.
"""
from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.consent import consent_allows
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.plan import Plan, PlanStep
from app.models.sleep import SleepLog
from app.models.user import User
from app.services import ai

# Curated step library used by the deterministic fallback.
_STEP_LIBRARY = {
    "Reduce stress": [
        ("Breathing reset", "3 min · box breathing", "wind"),
        ("Name the trigger", "1-line journal note", "book"),
        ("Wind-down", "Evening soundscape", "moon.stars"),
    ],
    "Sleep better": [
        ("Wind-down breathing", "4 min before bed", "wind"),
        ("Sleep story", "18-min Rain over quiet hills", "moon.zzz"),
        ("Screen curfew nudge", "Evening private reminder", "bell"),
    ],
    "Stop overthinking": [
        ("Grounding 5-4-3-2-1", "2 min sensory reset", "leaf"),
        ("CBT reframe", "Untangle one worried thought", "brain"),
        ("Reflective journal", "5-min night reflection", "book"),
    ],
    "Build confidence": [
        ("Affirming breath", "3 min steady breathing", "wind"),
        ("One small win", "Note something that went right", "sparkles"),
        ("Tomorrow's intention", "Set one clear point", "target"),
    ],
    "Feel less alone": [
        ("Voice check-in", "Talk it out for 2 min", "mic"),
        ("Reach out", "Message one trusted person", "person.2"),
        ("Gratitude note", "Name one connection", "heart"),
    ],
}
_DEFAULT_GOAL = "Reduce stress"

_TITLE_BY_GOAL = {
    "Reduce stress": "Ease work stress",
    "Sleep better": "Sleep deeper",
    "Stop overthinking": "Quiet the noise",
    "Build confidence": "Steady confidence",
    "Feel less alone": "Feel more connected",
}

_SYSTEM = (
    "You are CereBro's calm, agentic wellness planner. Given a user's goals and "
    "recent mood/journal signals, design a SHORT daily plan of 3 steps. "
    "Return JSON: {\"title\": str, \"focus\": str, \"rationale\": str, "
    "\"steps\": [{\"title\": str, \"detail\": str, \"symbol\": str}]}. "
    "Keep it gentle, concrete, and non-clinical. Symbols are SF Symbol names "
    "like wind, book, moon.stars, brain, leaf, heart."
)


async def _recent_signals(db: AsyncSession, user: User) -> tuple[list[str], list[str], list[SleepLog]]:
    """Recent personalization signals, each gated by its own consent category
    (DPDP itemization) — a switched-off category simply contributes nothing."""
    moods: list[str] = []
    journals: list[str] = []
    sleep_rows: list[SleepLog] = []
    if consent_allows(user, "mood_history"):
        moods = list((
            await db.scalars(
                select(MoodLog.mood).where(MoodLog.user_id == user.id).order_by(MoodLog.created_at.desc()).limit(7)
            )
        ).all())
    if consent_allows(user, "journal_memory"):
        journals = list((
            await db.scalars(
                select(JournalEntry.title)
                .where(JournalEntry.user_id == user.id)
                .order_by(JournalEntry.created_at.desc())
                .limit(5)
            )
        ).all())
    if consent_allows(user, "sleep_history"):
        sleep_rows = list((
            await db.scalars(
                select(SleepLog).where(SleepLog.user_id == user.id).order_by(SleepLog.date.desc()).limit(7)
            )
        ).all())
    return moods, journals, sleep_rows


def _sleep_note(sleep_rows: list[SleepLog]) -> str | None:
    if not sleep_rows:
        return None
    avg_dur = sum(r.duration_min for r in sleep_rows) // len(sleep_rows)
    avg_q = sum(r.quality for r in sleep_rows) / len(sleep_rows)
    return (
        f"avg {avg_dur // 60}h {avg_dur % 60:02d}m in bed, felt quality {avg_q:.1f}/5 "
        f"over {len(sleep_rows)} night(s)"
    )


def _short_sleep(sleep_rows: list[SleepLog]) -> bool:
    """Two-plus recent nights averaging under 6.5 h or feeling rough."""
    if len(sleep_rows) < 2:
        return False
    avg_dur = sum(r.duration_min for r in sleep_rows) / len(sleep_rows)
    avg_q = sum(r.quality for r in sleep_rows) / len(sleep_rows)
    return avg_dur < 390 or avg_q <= 2.5


def _fallback_plan(user: User, moods: list[str], sleep_rows: list[SleepLog]) -> dict:
    goal = (user.goals or [_DEFAULT_GOAL])[0]
    steps = list(_STEP_LIBRARY.get(goal, _STEP_LIBRARY[_DEFAULT_GOAL]))
    stressed = any(m.lower() in {"anxious", "low", "tired"} for m in moods)
    rationale = (
        "Because recent check-ins show some strain, today leans on a calmer reset first."
        if stressed
        else "Built around your goal and a steady recent baseline."
    )
    if _short_sleep(sleep_rows):
        # The diary says nights have been short or rough — protect tonight first.
        steps = [("Tonight's wind-down", "Lights low + slow breathing, 45 min before bed", "moon.zzz")] + steps[:2]
        rationale = "Your diary shows short or rough nights lately, so today protects the wind-down first."
    return {
        "title": _TITLE_BY_GOAL.get(goal, "Your calm plan"),
        "focus": goal,
        "rationale": rationale,
        "steps": [{"title": t, "detail": d, "symbol": s} for (t, d, s) in steps],
    }


async def generate_plan(db: AsyncSession, user: User) -> Plan:
    """Generate, persist, and return a fresh active plan (deactivating prior)."""
    moods, journals, sleep_rows = await _recent_signals(db, user)

    spec = None
    source = "rule"
    prompt = (
        f"Goals: {user.goals or [_DEFAULT_GOAL]}\n"
        f"Recent moods (newest first): {moods or 'none yet'}\n"
        f"Recent journal titles: {journals or 'none yet'}\n"
        f"Sleep diary (self-reported): {_sleep_note(sleep_rows) or 'none yet'}\n"
        f"Companion style: {user.companion}"
    )
    ai_spec = await ai.complete_json(_SYSTEM, prompt, max_tokens=900)
    if isinstance(ai_spec, dict) and ai_spec.get("steps"):
        spec = ai_spec
        source = "ai"
    if spec is None:
        spec = _fallback_plan(user, moods, sleep_rows)

    # Deactivate any existing active plans.
    existing = (await db.scalars(select(Plan).where(Plan.user_id == user.id, Plan.active.is_(True)))).all()
    for p in existing:
        p.active = False

    plan = Plan(
        user_id=user.id,
        title=str(spec.get("title", "Your calm plan"))[:160],
        focus=str(spec.get("focus", ""))[:120],
        rationale=str(spec.get("rationale", "")),
        active=True,
        source=source,
    )
    for i, step in enumerate(spec.get("steps", [])[:5]):
        plan.steps.append(
            PlanStep(
                title=str(step.get("title", "Step"))[:160],
                detail=str(step.get("detail", ""))[:255],
                symbol=str(step.get("symbol", "sparkles"))[:60],
                order=i,
            )
        )
    db.add(plan)
    await db.flush()
    return plan
