"""/work — the corporate-employee coaching surface.

Talk through a work problem, then turn the conversation into a plan with a task
list. See ``services/workcoach.py`` for the design and its boundaries (sponsored
members only; the organisation sees nothing; stateless turns; safety never
blocks; degrades without keys).

NO `from __future__ import annotations` here, on purpose: slowapi's limiter
wrapper cannot resolve stringified annotations, and with it present FastAPI
reads the request body as a missing QUERY param (the exact gotcha the wave-16
ledger entry records for habits.py — and which this file then reproduced).
"""
from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.deps import get_current_user
from app.core.ratelimit import limiter
from app.models.user import User
from app.schemas.plan import PlanOut
from app.services import crisis, entitlements, safety, usage, workcoach

router = APIRouter(prefix="/work", tags=["work"])


class WorkTurn(BaseModel):
    role: str = Field(default="user", max_length=16)
    text: str = Field(max_length=workcoach.MAX_MESSAGE_CHARS)


class WorkChatIn(BaseModel):
    message: str = Field(min_length=1, max_length=workcoach.MAX_MESSAGE_CHARS)
    #: Client-held history (see the service docstring for why the server keeps
    #: none). Capped: history beyond the cap is silently ignored, matching the
    #: service's own window.
    history: list[WorkTurn] = Field(default_factory=list, max_length=workcoach.MAX_TURNS)


class WorkChatOut(BaseModel):
    reply: str
    risk_level: str


class WorkPlanIn(BaseModel):
    history: list[WorkTurn] = Field(default_factory=list, max_length=workcoach.MAX_TURNS)


async def _require_sponsored(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
) -> User:
    """403 unless an organisation sponsors this account.

    The check is `entitlements.resolve` — the same resolution the chat quota and
    premium narration use — so "corporate member" cannot drift from what the
    rest of the product enforces. A personally-paid premium account is NOT
    enough: this surface exists because an employer sponsors it, and the detail
    says so honestly instead of a bare 403.
    """
    ent = await entitlements.resolve(db, user)
    if not ent.sponsored:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Work coaching is part of organisation-sponsored access.",
        )
    return user


@router.post("/chat", response_model=WorkChatOut)
@limiter.limit("30/minute")
async def work_chat(
    request: Request,
    payload: WorkChatIn,
    user: User = Depends(_require_sponsored),
    db: AsyncSession = Depends(get_db),
):
    # Same quota call as the wellness chat — sponsored members resolve to a paid
    # tier so it never trips for them, but the gate stays in one place.
    await usage.enforce_quota(db, user)
    # Safety scans the turn and records; it NEVER blocks (hard rule). No
    # source_id: work turns are deliberately not rows (see the service
    # docstring), so the event stands alone — the excerpt still gives the
    # reviewer the context.
    risk = await safety.scan_and_record(
        db, user_id=user.id, source="work", source_id=None, text=payload.message
    )
    turns = [t.model_dump() for t in payload.history] + [{"role": "user", "text": payload.message}]
    reply_text = await workcoach.reply(db, user, turns)
    if risk == "crisis":
        # Region-correct lines, appended by the platform — never by the model.
        reply_text = f"{reply_text}{crisis.reply_suffix(user.region)}"
    await db.commit()   # the safety event, if any
    return WorkChatOut(reply=reply_text, risk_level=risk)


@router.post("/plan", response_model=PlanOut, status_code=201)
@limiter.limit("10/minute")
async def work_plan(
    request: Request,
    payload: WorkPlanIn,
    user: User = Depends(_require_sponsored),
    db: AsyncSession = Depends(get_db),
):
    """Turn the conversation into a Plan + task list (the existing model —
    the plan screen and Today hero already know how to render it)."""
    return await workcoach.propose_plan(db, user, [t.model_dump() for t in payload.history])
