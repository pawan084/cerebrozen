"""The individual's endpoints: identity, profile, consent, trusted contact,
export, deletion.

Deletion is a product function, not a support ticket: PII is scrubbed in
place, sessions are revoked, and a no-PII ledger row proves it happened.
(The engine holds coaching content and has its own erasure endpoint; the
admin/ops runbook calls both.)

Note what is NOT here: the journal, the sleep log, the check-ins. Those are
content, they live in the engine, and keeping them out of this database is what
makes "counts, never content" a property of the schema instead of a promise —
an HR admin's token reaches this service, and it must find nothing to read."""

import json
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.deps import current_user
from app.models import (
    CONSENT_KEYS,
    DELETED_EMAIL_DOMAIN,
    ActivityEvent,
    DeletionLedger,
    Org,
    RefreshToken,
    User,
)
from app.routers.auth import _issue_pair  # one code path mints tokens, here and at login
from app.security import hash_email

router = APIRouter(prefix="/users", tags=["users"])


def effective_crisis_region(user: User, org: Org | None) -> str:
    """Which region's crisis helplines this person should be shown.

    The platform resolves it because the platform owns both inputs; the engine owns the
    helplines themselves (safety code — see its ``app/safety/helplines.py``) and the
    client asks it for this region's rows. Precedence:

      1. the person's own choice (``User.region``) — they know where they are, and the
         app offers them the picker;
      2. their org's default (``Org.crisis_region``) — an employer deploying in one
         country shouldn't make every employee set this by hand;
      3. "" — meaning *we don't know*, which the engine answers with an international
         directory. That is the honest answer, and it is safe everywhere.

    Never guessed from an IP or a SIM. Getting this wrong hands someone in crisis a
    number that doesn't answer, so an explicit "unknown" beats a confident guess.
    """
    return (user.region or "").strip() or (org.crisis_region if org else "") or ""


def _json_list(raw: str) -> list:
    try:
        value = json.loads(raw or "[]")
    except ValueError:
        return []
    return value if isinstance(value, list) else []


@router.get("/me")
async def me(
    user: User = Depends(current_user), session: AsyncSession = Depends(get_session)
):
    org = None
    if user.org_id:
        org = (
            await session.execute(select(Org).where(Org.id == user.org_id))
        ).scalar_one_or_none()
    return {
        "id": user.id,
        "email": user.email,
        "name": user.name,
        "role": user.role,
        "org_id": user.org_id,
        "org_name": org.name if org else None,
        "companion": user.companion,
        "language": user.language,
        "region": user.region,
        "crisis_region": effective_crisis_region(user, org),
        "goals": _json_list(user.goals),
        "motivations": _json_list(user.motivations),
    }


class ProfileIn(BaseModel):
    """Every field optional — the app PATCHes one at a time (companion, region), and
    onboarding PATCHes goals+motivations together."""

    name: str | None = Field(default=None, max_length=200)
    companion: str | None = Field(default=None, max_length=40)
    language: str | None = Field(default=None, max_length=40)
    region: str | None = Field(default=None, max_length=8)
    goals: list[str] | None = Field(default=None, max_length=20)
    motivations: list[str] | None = Field(default=None, max_length=20)


@router.patch("/me")
async def update_me(
    body: ProfileIn,
    user: User = Depends(current_user),
    session: AsyncSession = Depends(get_session),
):
    for field in ("name", "companion", "language", "region"):
        value = getattr(body, field)
        if value is not None:
            setattr(user, field, value.strip())
    for field in ("goals", "motivations"):
        value = getattr(body, field)
        if value is not None:
            setattr(user, field, json.dumps([str(v)[:200] for v in value]))
    await session.commit()
    return await me(user, session)


# ── consent (DPDP) ───────────────────────────────────────────────────────────


@router.get("/me/consent")
async def get_consent(user: User = Depends(current_user)):
    """The six categories, as they actually stand.

    This endpoint 404'd until now, and the app renders `optBoolean(key)` — so every
    toggle drew as OFF and the screen looked authoritative while saying nothing true.
    A consent surface that displays a default as though it were the person's decision is
    worse than no surface: they may well have consented, and we showed them otherwise."""
    return user.consents()


class ConsentIn(BaseModel):
    """A partial patch: Settings sends ONE key, onboarding sends all six."""

    mood_history: bool | None = None
    ai_memory: bool | None = None
    journal_memory: bool | None = None
    sleep_history: bool | None = None
    voice_storage: bool | None = None
    model_training: bool | None = None


@router.patch("/me/consent")
async def update_consent(
    body: ConsentIn,
    user: User = Depends(current_user),
    session: AsyncSession = Depends(get_session),
):
    """Change consent, and hand back a token that already tells the truth.

    Withdrawal has to BITE, not merely be recorded. The engine enforces from the signed
    `consent` claim, so the token in the caller's hand still says yes for up to
    ACCESS_TTL_MIN — fifteen minutes in which we would go on storing what they just told
    us to stop storing.

    So a consent change ROTATES the session: every old refresh token is revoked and a
    fresh pair is issued here, carrying the new claim. The withdrawal is effective on the
    caller's very next request rather than a quarter of an hour later, and the person is
    not logged out for having touched a switch — the app simply adopts the new tokens.
    (Their other devices do get signed out, which is right: a consent is the person's, not
    the handset's.)
    """
    patch = {k: v for k, v in body.model_dump().items() if v is not None}
    if not patch:
        raise HTTPException(400, "nothing to update")
    for key, value in patch.items():
        setattr(user, f"consent_{key}", value)
    user.consent_updated_at = datetime.now(timezone.utc)
    await session.execute(
        update(RefreshToken)
        .where(RefreshToken.user_id == user.id, RefreshToken.revoked.is_(False))
        .values(revoked=True)
    )
    await session.commit()

    pair = await _issue_pair(session, user)
    return {
        **user.consents(),
        "access_token": pair.access_token,
        "refresh_token": pair.refresh_token,
        "token_type": "bearer",
    }


# ── age attestation ──────────────────────────────────────────────────────────


class AttestIn(BaseModel):
    adult: bool = True


@router.post("/me/attest")
async def attest(
    body: AttestIn,
    user: User = Depends(current_user),
    session: AsyncSession = Depends(get_session),
):
    """"I am 18 or over", from the onboarding gate.

    A date, not a birthday: we record WHEN they told us, never how old they are. The
    product is not for children and DPDP treats a child's data differently — so the
    attestation is worth keeping, and the date of birth is not worth asking for.
    """
    user.adult_attested_at = datetime.now(timezone.utc) if body.adult else None
    await session.commit()
    return {"adult": bool(user.adult_attested_at)}


# ── trusted contact (crisis) ─────────────────────────────────────────────────


@router.get("/me/trusted-contact")
async def get_trusted_contact(user: User = Depends(current_user)):
    """Null when unset — the app renders "Not set" from that, and a 404 would have been
    an error state instead of an honest empty one."""
    if not user.trusted_contact_value:
        return None
    return {
        "name": user.trusted_contact_name,
        "method": user.trusted_contact_method,
        "value": user.trusted_contact_value,
        "notify_consent": user.trusted_contact_consent,
    }


class TrustedContactIn(BaseModel):
    name: str = Field(max_length=120)
    method: str = Field(default="sms", max_length=20)
    value: str = Field(max_length=200)
    notify_consent: bool = False


@router.put("/me/trusted-contact")
async def put_trusted_contact(
    body: TrustedContactIn,
    user: User = Depends(current_user),
    session: AsyncSession = Depends(get_session),
):
    user.trusted_contact_name = body.name.strip()
    user.trusted_contact_method = body.method.strip()
    user.trusted_contact_value = body.value.strip()
    user.trusted_contact_consent = body.notify_consent
    await session.commit()
    return await get_trusted_contact(user)


@router.delete("/me/trusted-contact", status_code=204)
async def delete_trusted_contact(
    user: User = Depends(current_user), session: AsyncSession = Depends(get_session)
):
    user.trusted_contact_name = ""
    user.trusted_contact_method = ""
    user.trusted_contact_value = ""
    user.trusted_contact_consent = False
    await session.commit()


# ── streak ───────────────────────────────────────────────────────────────────


@router.get("/me/streak")
async def streak(
    user: User = Depends(current_user), session: AsyncSession = Depends(get_session)
):
    """Consecutive days, up to today, on which they did something.

    Counted from THEIR OWN activity rows — the same kind-only beats HR sees in aggregate,
    never the content. A streak that breaks does not nag and is not reported to anyone;
    it is a number the person can look at.
    """
    since = datetime.now(timezone.utc) - timedelta(days=400)
    rows = (
        await session.execute(
            select(ActivityEvent.created_at).where(
                ActivityEvent.user_id == user.id, ActivityEvent.created_at >= since
            )
        )
    ).scalars().all()

    days = {ts.date() for ts in rows if ts}
    if not days:
        return {"current": 0, "longest": 0}

    today = datetime.now(timezone.utc).date()
    # A streak that includes YESTERDAY but not today is still alive — the day is not over.
    # Starting the walk at today would zero it out every morning until they opened the app.
    cursor = today if today in days else today - timedelta(days=1)
    current = 0
    while cursor in days:
        current += 1
        cursor -= timedelta(days=1)

    longest, run, previous = 0, 0, None
    for day in sorted(days):
        run = run + 1 if previous and (day - previous).days == 1 else 1
        longest = max(longest, run)
        previous = day

    return {"current": current, "longest": longest}


@router.get("/me/export")
async def export(
    user: User = Depends(current_user), session: AsyncSession = Depends(get_session)
):
    """Everything the PLATFORM holds about this person, as one JSON document."""
    sessions_count = len(
        (
            await session.execute(
                select(RefreshToken).where(
                    RefreshToken.user_id == user.id, RefreshToken.revoked.is_(False)
                )
            )
        ).scalars().all()
    )
    return {
        "profile": {
            "id": user.id,
            "email": user.email,
            "name": user.name,
            "role": user.role,
            "org_id": user.org_id,
            "created_at": str(user.created_at),
            "companion": user.companion,
            "language": user.language,
            "region": user.region,
            "goals": _json_list(user.goals),
            "motivations": _json_list(user.motivations),
        },
        "consent": {
            **user.consents(),
            "updated_at": str(user.consent_updated_at) if user.consent_updated_at else None,
        },
        "trusted_contact": await get_trusted_contact(user),
        "active_sessions": sessions_count,
        "note": "Coaching content is exported from the coaching engine (/v1/privacy/me/export).",
    }


@router.delete("/me", status_code=204)
async def delete_me(
    confirm: bool = Query(default=False),
    user: User = Depends(current_user),
    session: AsyncSession = Depends(get_session),
):
    if not confirm:
        raise HTTPException(400, "pass ?confirm=true to delete your account")
    session.add(
        DeletionLedger(user_id=user.id, org_id=user.org_id, email_hash=hash_email(user.email))
    )
    # Scrub in place: the row survives as a tombstone so foreign keys stay
    # valid, but no PII remains and the account can never log in again.
    user.email = f"deleted-{user.id}{DELETED_EMAIL_DOMAIN}"
    user.name = ""
    user.password_hash = "deleted"
    user.is_active = False
    # The trusted contact is somebody ELSE's name and number. It has no business
    # outliving the account it was given to, and nobody is left to ask for its removal.
    user.trusted_contact_name = ""
    user.trusted_contact_method = ""
    user.trusted_contact_value = ""
    user.trusted_contact_consent = False
    for key in CONSENT_KEYS:
        setattr(user, f"consent_{key}", False)
    user.companion = user.language = user.region = ""
    user.goals = user.motivations = ""
    user.active_program_id = ""
    user.program_started_at = None
    await session.execute(
        update(RefreshToken).where(RefreshToken.user_id == user.id).values(revoked=True)
    )
    await session.commit()
