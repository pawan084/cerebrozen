import uuid

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db, utcnow
from app.core.deps import get_current_user
from app.core.ratelimit import account_limit, limiter
from app.models.plan import Plan, PlanStep
from app.models.user import User
from app.schemas.plan import PlanOut, StepToggle
from app.services import agentic

router = APIRouter(prefix="/plans", tags=["plans"])


async def _active_plan(db: AsyncSession, user: User) -> Plan | None:
    return await db.scalar(
        select(Plan).where(Plan.user_id == user.id, Plan.active.is_(True)).order_by(Plan.created_at.desc())
    )


@router.get("/active", response_model=PlanOut)
async def get_active_plan(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    plan = await _active_plan(db, user)
    if plan is None:
        # Generate one on first access so the app always has a plan — but do it
        # ONE AT A TIME per user.
        #
        # This is a GET that writes, and concurrent callers all saw "no active
        # plan" and all generated: five parallel requests made five plans, and
        # since `generate_plan` deactivates whatever it finds, the last commit
        # won and the other four were orphaned. That is not merely untidy. A
        # client that had already rendered one of the losers went on ticking
        # steps against a plan the server no longer considered active, so the
        # tick landed on a dead row and vanished on the next load — which is
        # exactly what the browser write-path test caught (PATCH 200, then
        # /plans/active reporting nothing done).
        #
        # An advisory lock rather than a partial unique index: it needs no
        # migration, it is released with the transaction, and re-checking under
        # it makes late arrivals adopt the winner instead of racing it.
        if db.bind is not None and db.bind.dialect.name == "postgresql":
            await db.execute(
                text("SELECT pg_advisory_xact_lock(hashtext(:k))"), {"k": str(user.id)}
            )
            plan = await _active_plan(db, user)
        if plan is None:
            plan = await agentic.generate_plan(db, user)
        await db.commit()
        await db.refresh(plan)
    return plan


@router.post("/generate", response_model=PlanOut, status_code=201)
@limiter.limit("10/minute")   # each call is a ~900-token LLM plan (register C76)
@account_limit("10/minute")   # …and the same ceiling per account (~900-token LLM plan)
async def regenerate_plan(
    request: Request,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    plan = await agentic.generate_plan(db, user)
    await db.commit()
    await db.refresh(plan)
    return plan


@router.patch("/steps/{step_id}", response_model=PlanOut)
async def toggle_step(
    step_id: uuid.UUID,
    payload: StepToggle,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    step = await db.get(PlanStep, step_id)
    if step is None:
        raise HTTPException(status_code=404, detail="Step not found")
    plan = await db.get(Plan, step.plan_id)
    if plan is None or plan.user_id != user.id:
        raise HTTPException(status_code=404, detail="Step not found")
    step.done = payload.done
    step.done_at = utcnow() if payload.done else None
    await db.commit()
    await db.refresh(plan)
    return plan
