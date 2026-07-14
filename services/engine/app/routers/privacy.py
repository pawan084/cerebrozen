"""Right of access and right to erasure, over HTTP.

    GET    /v1/privacy/me/export     everything we hold about the caller
    DELETE /v1/privacy/me            erase it, verify, and report

## The authorisation is the dangerous part, not the deletion

An erasure endpoint is a weapon. `DELETE /v1/users/{user_id}` keyed on a path parameter is
one typo — or one enumeration attack — away from deleting somebody else's coaching history,
permanently, with no undo.

So there is **no user id in the path.** The subject is taken from the signed token and
nowhere else. A caller can erase themselves and cannot express the erasure of anyone else;
there is no request they can send that would even mean it.

Operators do need to run an erasure on someone's behalf — a written request arrives by
email, and the person is not around to click a button. That is `CEREBROZEN_ADMIN_SUBJECTS`: an
explicit allowlist of token subjects who may pass `?user_id=`. Not a role claim, not a flag
in a JWT somebody else mints — a list, in this deployment's config, that a human had to
write down. Every admin erasure logs at WARNING with the actor.
"""

from __future__ import annotations

import logging
import os

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

from app.auth import require_auth
from app.privacy import erasure

logger = logging.getLogger("cerebrozen.privacy")
router = APIRouter(prefix="/v1/privacy", tags=["privacy"])


def _admins() -> set[str]:
    return {
        s.strip()
        for s in os.environ.get("CEREBROZEN_ADMIN_SUBJECTS", "").split(",")
        if s.strip()
    }


def _subject(claims: dict, requested: str | None) -> str:
    """Whose data is this? The token's, unless an allowlisted admin says otherwise."""
    from app.auth.dependencies import auth_enabled

    me = str(claims.get("sub") or claims.get("user_id") or "").strip()

    if not requested:
        if not me:
            # Auth is off (local dev) and no subject was named. Refuse rather than guess:
            # guessing wrong on a DELETE is unrecoverable.
            raise HTTPException(status_code=400, detail="No subject in token; pass ?user_id= as an admin.")
        return me

    if requested == me:
        return me

    if not auth_enabled():
        # Local dev, no tokens. Allowed, because otherwise nobody can test this — but say so.
        logger.warning("privacy.subject_override_auth_off", extra={"requested": requested})
        return requested

    if me and me in _admins():
        logger.warning(
            "privacy.admin_acting_for_user",
            extra={"actor": me, "subject": requested,
                   "detail": "an allowlisted admin is acting on another user's data"},
        )
        return requested

    # The important branch. Not "not found", not a silent no-op — a refusal.
    logger.error(
        "privacy.subject_mismatch_refused",
        extra={"actor": me or "(none)", "requested": requested},
    )
    raise HTTPException(status_code=403, detail="You may only access or erase your own data.")


@router.get("/me/export")
async def export_me(
    user_id: str | None = Query(default=None, description="Admins only; defaults to the token subject."),
    claims: dict = Depends(require_auth),
):
    """Right of access. Everything we hold, in one document, as JSON.

    Deliberately the same registry `erase` uses — you cannot export what you have forgotten
    you kept, so if a location is missing here it is missing from the erasure too, and one
    test catches both.
    """
    subject = _subject(claims, user_id)
    data = await run_in_threadpool(erasure.export_user, subject)
    return JSONResponse(
        content=data,
        headers={"Content-Disposition": f'attachment; filename="export-{subject}.json"'},
    )


@router.delete("/me")
async def erase_me(
    user_id: str | None = Query(default=None, description="Admins only; defaults to the token subject."),
    confirm: bool = Query(default=False, description="Must be true. This cannot be undone."),
    claims: dict = Depends(require_auth),
):
    """Right to erasure. Deletes, re-scans, and reports what is left.

    `?confirm=true` is required. Not friction for its own sake: this endpoint destroys a
    person's coaching history across three databases with no undo, and a DELETE that fires on
    a mistyped URL is a bad afternoon.

    Returns 200 only when the re-scan finds nothing. If anything survives, this returns **500
    with `verified: false`** — because a partial erasure reported as success is the single
    worst outcome here. The person believes their data is gone. It is not.
    """
    subject = _subject(claims, user_id)
    if not confirm:
        raise HTTPException(status_code=400, detail="Erasure is permanent. Pass ?confirm=true.")

    report = await run_in_threadpool(erasure.erase_user, subject)

    if not report.get("verified"):
        return JSONResponse(status_code=500, content={
            **report,
            "detail": (
                "ERASURE INCOMPLETE — data remains. Do not tell the user their data is gone. "
                "See `remaining` for what survived."
            ),
        })
    return JSONResponse(content=report)
