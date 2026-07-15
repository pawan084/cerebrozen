"""The rest-and-recovery catalog over HTTP: scenes, soundscapes, wind-down,
the keyed media catalogue, and multi-day program enrolment.

    GET    /content?kind=            the scene list for a kind (sleep, soundscape,
                                     wind_down, program)
    GET    /media/catalog            the keyed one-shot/loop catalogue
    GET    /programs/active          the caller's current program, or {program: null}
    POST   /programs/enroll          start a program by content id
    DELETE /programs/active          leave it

The catalog itself is static app configuration (app/catalog.py) — no user content, no
licensed media. Enrolment is the only per-user state here, and it is a preference (which
program, started when), not content: it never holds a word the person wrote.
"""

from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app import catalog
from app.db import get_session
from app.deps import current_user
from app.models import User

router = APIRouter(tags=["content"])


@router.get("/content")
async def content(kind: str = Query(default="", max_length=40), _: User = Depends(current_user)):
    """The scene list for a kind. Auth-gated only because every caller is already
    signed in; the catalog holds nothing private. An unknown kind is an empty list,
    not a 404 — the app renders a friendly 'nothing here yet'."""
    return catalog.content_for(kind)


@router.get("/media/catalog")
async def media_catalog(_: User = Depends(current_user)):
    return catalog.media_catalog()


@router.get("/programs/active")
async def active_program(user: User = Depends(current_user)):
    """The caller's current program (day derived from the enrolment date), or a null
    envelope when they're not enrolled — the shape the app's `optJSONObject("program")`
    expects."""
    return {"program": catalog.program_payload(user.active_program_id, user.program_started_at)}


class EnrollIn(BaseModel):
    content_id: str = Field(max_length=40)


@router.post("/programs/enroll")
async def enroll(
    body: EnrollIn,
    user: User = Depends(current_user),
    session: AsyncSession = Depends(get_session),
):
    if body.content_id not in catalog.PROGRAMS:
        raise HTTPException(404, "no such program")
    user.active_program_id = body.content_id
    user.program_started_at = datetime.now(timezone.utc)
    await session.commit()
    return {"program": catalog.program_payload(user.active_program_id, user.program_started_at)}


@router.delete("/programs/active", status_code=204)
async def leave_program(
    user: User = Depends(current_user), session: AsyncSession = Depends(get_session)
):
    user.active_program_id = ""
    user.program_started_at = None
    await session.commit()
