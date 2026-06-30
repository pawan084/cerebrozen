import uuid

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db, utcnow
from app.core.deps import get_current_user
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
        # Generate one on first access so the app always has a plan.
        plan = await agentic.generate_plan(db, user)
        await db.commit()
        await db.refresh(plan)
    return plan


@router.post("/generate", response_model=PlanOut, status_code=201)
async def regenerate_plan(
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
