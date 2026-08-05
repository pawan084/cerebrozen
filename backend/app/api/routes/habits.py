"""Goals and habits — the things the user defines, rather than the app.

Everything else in this product is generated for the user: the daily plan, the
patterns, the recommendations. These two are the opposite, and that is the
point. Onboarding has always captured goals as profile strings that never became
anything trackable; a `Goal` row is addressable, and `decompose` feeds it into
the planner that already exists.

Habits are deliberately gentle. Completions are dated rows, not a chain: the
schema cannot express "your streak broke", and the copy shouldn't either.
"""
# NOTE: no `from __future__ import annotations` here — slowapi's limit wrapper
# re-inspects the endpoint signature, and stringified annotations leave
# `uuid.UUID` an unresolvable ForwardRef inside its namespace (pydantic
# "class-not-fully-defined" at request time). Runtime annotations are fine on
# this Python; keep them evaluated.
import uuid
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db, utcnow
from app.core.deps import get_current_user
from app.core.ratelimit import limiter
from app.models.habit import Goal, Habit, HabitCompletion
from app.models.user import User
from app.schemas.content_data import (
    GoalCreate,
    GoalOut,
    GoalUpdate,
    HabitCreate,
    HabitOut,
    HabitUpdate,
)
from app.schemas.plan import PlanOut
from app.services import agentic

router = APIRouter(tags=["goals"])

GOAL_STATUSES = ("active", "achieved", "released")


# ── Goals ───────────────────────────────────────────────────────────────

async def _owned_goal(db: AsyncSession, user: User, goal_id: uuid.UUID) -> Goal:
    row = await db.get(Goal, goal_id)
    if row is None or row.user_id != user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    return row


@router.get("/goals", response_model=list[GoalOut])
async def list_goals(
    include_resolved: bool = False,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(Goal).where(Goal.user_id == user.id)
    if not include_resolved:
        stmt = stmt.where(Goal.status == "active")
    return (await db.scalars(stmt.order_by(Goal.created_at.desc()))).all()


@router.post("/goals", response_model=GoalOut, status_code=status.HTTP_201_CREATED)
async def create_goal(
    payload: GoalCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    goal = Goal(user_id=user.id, title=payload.title.strip(), why=payload.why.strip())
    db.add(goal)
    await db.commit()
    await db.refresh(goal)
    return goal


@router.patch("/goals/{goal_id}", response_model=GoalOut)
async def update_goal(
    goal_id: uuid.UUID,
    payload: GoalUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    goal = await _owned_goal(db, user, goal_id)
    if payload.title is not None:
        goal.title = payload.title.strip()
    if payload.why is not None:
        goal.why = payload.why.strip()
    if payload.status is not None:
        if payload.status not in GOAL_STATUSES:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Unknown status"
            )
        goal.status = payload.status
        # "Released" is a real outcome, not a failure — it gets the same
        # timestamp treatment as "achieved".
        goal.resolved_at = None if payload.status == "active" else utcnow()
    await db.commit()
    await db.refresh(goal)
    return goal


@router.delete("/goals/{goal_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_goal(
    goal_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    goal = await _owned_goal(db, user, goal_id)
    await db.delete(goal)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/goals/{goal_id}/decompose", response_model=PlanOut, status_code=201)
@limiter.limit("10/minute")   # each call is a ~900-token LLM plan (register C76)
async def decompose_goal(
    request: Request,
    goal_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Turn a goal into today's plan.

    This replaces the active plan rather than creating a second one — the
    product has exactly one plan at a time, and two competing lists is how a
    calm app becomes a task manager.
    """
    goal = await _owned_goal(db, user, goal_id)
    plan = await agentic.generate_plan(db, user, focus_goal=goal.title)
    await db.commit()
    await db.refresh(plan)
    return plan


# ── Habits ──────────────────────────────────────────────────────────────

async def _owned_habit(db: AsyncSession, user: User, habit_id: uuid.UUID) -> Habit:
    row = await db.get(Habit, habit_id)
    if row is None or row.user_id != user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Not found")
    return row


async def _with_completions(db: AsyncSession, habits: list[Habit]) -> list[dict]:
    """Attach the last 7 days of completions to each habit.

    A window, not a streak: the client shows which days happened, and a gap is
    just a gap.
    """
    if not habits:
        return []
    today = utcnow().date()
    since = today - timedelta(days=6)
    rows = (
        await db.scalars(
            select(HabitCompletion).where(
                HabitCompletion.habit_id.in_([h.id for h in habits]),
                HabitCompletion.day >= since,
            )
        )
    ).all()
    by_habit: dict[uuid.UUID, list[str]] = {}
    for row in rows:
        by_habit.setdefault(row.habit_id, []).append(row.day.isoformat())
    return [
        {
            "id": h.id,
            "title": h.title,
            "cue": h.cue,
            "target_per_week": h.target_per_week,
            "archived": h.archived,
            "recent_days": sorted(by_habit.get(h.id, [])),
            "done_today": today.isoformat() in by_habit.get(h.id, []),
        }
        for h in habits
    ]


@router.get("/habits", response_model=list[HabitOut])
async def list_habits(
    include_archived: bool = False,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    stmt = select(Habit).where(Habit.user_id == user.id)
    if not include_archived:
        stmt = stmt.where(Habit.archived.is_(False))
    habits = (await db.scalars(stmt.order_by(Habit.created_at))).all()
    return await _with_completions(db, list(habits))


@router.post("/habits", response_model=HabitOut, status_code=status.HTTP_201_CREATED)
async def create_habit(
    payload: HabitCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    habit = Habit(
        user_id=user.id,
        title=payload.title.strip(),
        cue=payload.cue.strip(),
        target_per_week=payload.target_per_week,
    )
    db.add(habit)
    await db.commit()
    await db.refresh(habit)
    return (await _with_completions(db, [habit]))[0]


@router.patch("/habits/{habit_id}", response_model=HabitOut)
async def update_habit(
    habit_id: uuid.UUID,
    payload: HabitUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    habit = await _owned_habit(db, user, habit_id)
    if payload.title is not None:
        habit.title = payload.title.strip()
    if payload.cue is not None:
        habit.cue = payload.cue.strip()
    if payload.target_per_week is not None:
        habit.target_per_week = payload.target_per_week
    if payload.archived is not None:
        habit.archived = payload.archived
    await db.commit()
    await db.refresh(habit)
    return (await _with_completions(db, [habit]))[0]


@router.delete("/habits/{habit_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_habit(
    habit_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    habit = await _owned_habit(db, user, habit_id)
    await db.delete(habit)
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/habits/{habit_id}/complete", response_model=HabitOut)
async def complete_habit(
    habit_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Mark today done. Idempotent — tapping twice is not two days."""
    habit = await _owned_habit(db, user, habit_id)
    today = utcnow().date()
    existing = await db.scalar(
        select(HabitCompletion).where(
            HabitCompletion.habit_id == habit.id, HabitCompletion.day == today
        )
    )
    if existing is None:
        db.add(HabitCompletion(habit_id=habit.id, user_id=user.id, day=today))
        try:
            await db.commit()
        except IntegrityError:
            # Register C52: an impatient double-tap raced past the existence
            # check and 500'd on `uq_habit_day`. The day is done either way.
            await db.rollback()
    return (await _with_completions(db, [habit]))[0]


@router.delete("/habits/{habit_id}/complete", response_model=HabitOut)
async def uncomplete_habit(
    habit_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Undo today. A mis-tap should never be permanent."""
    habit = await _owned_habit(db, user, habit_id)
    await db.execute(
        delete(HabitCompletion).where(
            HabitCompletion.habit_id == habit.id,
            HabitCompletion.day == utcnow().date(),
        )
    )
    await db.commit()
    return (await _with_completions(db, [habit]))[0]
