"""The person's own wellness records, over HTTP.

    GET    /v1/wellness/journal            their entries, newest first
    POST   /v1/wellness/journal            write one
    GET    /v1/wellness/sleep              their nights, newest first
    POST   /v1/wellness/sleep              log one
    GET    /v1/wellness/sleep/summary      the "Your week" card
    GET    /v1/wellness/moods              their check-ins
    POST   /v1/wellness/moods              record one
    GET    /v1/wellness/insights/weekly    their own week, counted
    DELETE /v1/wellness/{kind}/{entry_id}  remove one entry

## There is no user id in any of these

Same rule as the privacy router, for the same reason: the subject comes from the signed
token and nowhere else. A journal is the most private thing this product holds, and a
`?user_id=` on a GET is one enumeration away from reading somebody else's diary. There is
no request a caller can send that would even MEAN "somebody else's journal".

That also means these routes do NOT use `resolve_user_id` (which lets a payload `user_id`
win, for service-to-service coaching calls). No service coaches on someone's behalf here;
there is nothing to support and everything to lose.

## What a 200 means

The store refuses writes when a tenant has self-report storage switched off
(`CEREBROZEN_SELF_REPORT_WELLNESS=false`). A refusal is a **409**, never a cheerful 200:
the app must be able to tell "we saved it" from "we did not", or it ends up printing
"Saved to your journal" over a write that never happened.
"""

from __future__ import annotations

import logging

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel, Field

from app.auth import require_auth, require_plus
from app.session import user_id_from_claims
from app.stores import patterns, wellness

logger = logging.getLogger("cerebrozen.wellness")
router = APIRouter(prefix="/v1/wellness", tags=["wellness"])

_REFUSED = "self-reported wellness storage is disabled for this tenant"

# Which DPDP consent each kind of writing needs (platform models.CONSENT_KEYS). The
# platform mints these into the signed token, so the engine can honour a person's choice
# without a per-request call back to a service that might be down between them and their
# own journal.
_CONSENT_FOR_KIND = {
    "journal": "journal_memory",
    "sleep": "sleep_history",
    "moods": "mood_history",
}


def _subject(claims: dict) -> str:
    user_id = user_id_from_claims(claims)
    if not user_id:
        raise HTTPException(status_code=400, detail="user_id not found in JWT.")
    return user_id


def _require_consent(claims: dict, kind: str) -> None:
    """403 unless the person has consented to us keeping this category.

    A token with NO `consent` claim is allowed through: absence is not refusal, and an
    engine that hard-denied every token minted before this claim existed would break every
    client with no way for the user to fix it. Putting the truth in the claim is the
    PLATFORM's job (it always mints all six), and a user who has never consented gets six
    explicit falses — which this does refuse.
    """
    consent = claims.get("consent")
    if not isinstance(consent, dict):
        return
    key = _CONSENT_FOR_KIND[kind]
    if consent.get(key) is False:
        logger.info("wellness.consent_refused", extra={"kind": kind, "consent_key": key})
        raise HTTPException(
            status_code=403,
            detail=(
                f"You haven't consented to CereBroZen keeping this ({key.replace('_', ' ')}). "
                "You can turn it on in Privacy & memory."
            ),
        )


class JournalIn(BaseModel):
    body: str = Field(max_length=20000)
    title: str = Field(default="", max_length=200)
    tags: list[str] = Field(default_factory=list, max_length=20)
    symbol: str = Field(default="", max_length=40)


class SleepIn(BaseModel):
    date: str = Field(default="", max_length=20)
    bedtime: str = Field(default="", max_length=8)
    wake_time: str = Field(default="", max_length=8)
    quality: int = 0
    awakenings: int = 0


class MoodIn(BaseModel):
    mood: str = Field(max_length=40)
    note: str = Field(default="", max_length=500)
    symbol: str = Field(default="", max_length=40)
    intensity: int = 0


# ── journal ──────────────────────────────────────────────────────────────────


@router.get("/journal")
async def get_journal(
    limit: int = Query(50, ge=1, le=200), claims: dict = Depends(require_auth)
) -> list:
    return await run_in_threadpool(wellness.list_journal, _subject(claims), limit)


@router.post("/journal", status_code=201)
async def post_journal(body: JournalIn, claims: dict = Depends(require_auth)) -> dict:
    _require_consent(claims, "journal")
    entry = await run_in_threadpool(
        wellness.add_journal, _subject(claims), body.body, body.title, body.tags, body.symbol
    )
    if entry is None:
        # Empty body → 400 (the caller sent nothing); storage off → 409 (we refused).
        if not body.body.strip():
            raise HTTPException(status_code=400, detail="A journal entry needs something in it.")
        raise HTTPException(status_code=409, detail=_REFUSED)
    return entry


# ── sleep ────────────────────────────────────────────────────────────────────


@router.get("/sleep", dependencies=[Depends(require_plus)])
async def get_sleep(
    limit: int = Query(30, ge=1, le=200), claims: dict = Depends(require_auth)
) -> list:
    return await run_in_threadpool(wellness.list_sleep, _subject(claims), limit)


@router.get("/sleep/summary", dependencies=[Depends(require_plus)])
async def get_sleep_summary(
    days: int = Query(7, ge=1, le=90), claims: dict = Depends(require_auth)
) -> dict:
    return await run_in_threadpool(wellness.sleep_summary, _subject(claims), days)


@router.post("/sleep", status_code=201, dependencies=[Depends(require_plus)])
async def post_sleep(body: SleepIn, claims: dict = Depends(require_auth)) -> dict:
    _require_consent(claims, "sleep")
    entry = await run_in_threadpool(
        wellness.add_sleep,
        _subject(claims),
        body.date,
        body.bedtime,
        body.wake_time,
        body.quality,
        body.awakenings,
    )
    if entry is None:
        if wellness.duration_minutes(body.bedtime, body.wake_time) <= 0:
            raise HTTPException(
                status_code=400, detail="A night needs a bedtime and a wake time (HH:MM)."
            )
        raise HTTPException(status_code=409, detail=_REFUSED)
    return entry


# ── mood check-ins ───────────────────────────────────────────────────────────


@router.get("/moods")
async def get_moods(
    limit: int = Query(30, ge=1, le=200), claims: dict = Depends(require_auth)
) -> list:
    return await run_in_threadpool(wellness.list_moods, _subject(claims), limit)


@router.post("/moods", status_code=201)
async def post_mood(body: MoodIn, claims: dict = Depends(require_auth)) -> dict:
    _require_consent(claims, "moods")
    entry = await run_in_threadpool(
        wellness.add_mood, _subject(claims), body.mood, body.note, body.symbol, body.intensity
    )
    if entry is None:
        if not body.mood.strip():
            raise HTTPException(status_code=400, detail="A check-in needs a mood.")
        raise HTTPException(status_code=409, detail=_REFUSED)
    return entry


# ── the person's own week ────────────────────────────────────────────────────


@router.get("/insights/weekly", dependencies=[Depends(require_plus)])
async def get_weekly_insights(
    days: int = Query(7, ge=1, le=90), claims: dict = Depends(require_auth)
) -> dict:
    return await run_in_threadpool(wellness.weekly_insights, _subject(claims), days)


@router.get("/patterns", dependencies=[Depends(require_plus)])
async def get_patterns(claims: dict = Depends(require_auth)) -> dict:
    """Transparent AI memory: every statement the coach has learned, with its basis.

    Theirs and nobody else's — same firewall as the rest of this router. There is no org
    aggregate of this and no endpoint that could build one.

    Consent is an INPUT, not a filter applied afterwards: a category the person declined is
    never read, so it cannot reach a statement. `sources` reports what was consulted, which
    is the honest complement to the delete button (`DELETE /v1/privacy/me/memory`). No 403
    here — declining every category is a valid state that yields `enough_data: false`, not
    an error; refusing the whole page because one box is off would punish the choice.
    """
    return await run_in_threadpool(patterns.for_user, _subject(claims), claims.get("consent"))


# ── delete one entry ─────────────────────────────────────────────────────────


@router.delete("/{kind}/{entry_id}")
async def delete_entry(kind: str, entry_id: str, claims: dict = Depends(require_auth)) -> dict:
    if kind not in wellness.KINDS:
        raise HTTPException(status_code=404, detail="Not found.")
    removed = await run_in_threadpool(wellness.delete_entry, _subject(claims), kind, entry_id)
    if not removed:
        # Theirs-or-nothing: an id that is not in THEIR document is a 404, whether it
        # never existed or belongs to somebody else. The distinction is not the caller's
        # to learn.
        raise HTTPException(status_code=404, detail="Not found.")
    return {"deleted": entry_id, "kind": kind}
