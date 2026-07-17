"""Coaching-outcomes read surface.

``GET /v1/outcomes`` returns the caller's own content-free progress snapshot (see
``app/outcomes.py``). Auth required; the user comes only from the JWT, so there is no
``user_id`` parameter to spoof — a caller sees their own progress and no one else's.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from fastapi.concurrency import run_in_threadpool

from app import outcomes
from app.auth import require_auth
from app.session import user_id_from_claims

router = APIRouter()


@router.get("/v1/outcomes")
async def get_outcomes(claims: dict = Depends(require_auth)) -> dict:
    """The caller's coaching progress: counts, Development Areas, follow-through — no bodies."""
    user_id = user_id_from_claims(claims)
    if not user_id:
        raise HTTPException(status_code=400, detail="user_id not found in JWT.")
    return await run_in_threadpool(outcomes.progress, user_id)
