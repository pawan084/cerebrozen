"""Org lifecycle (internal admin) and org-scoped administration (org admin).

Seat accounting counts active users plus pending invitations — an invitation
IS a seat commitment, or an org could oversubscribe by inviting."""

import logging
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel, EmailStr, Field
from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app import config, emailer
from app.db import get_session
from app.deps import require_internal_admin, require_org_admin
from app.models import (
    DELETED_EMAIL_DOMAIN,
    ROLE_ORG_ADMIN,
    ROLE_USER,
    Invitation,
    Org,
    RefreshToken,
    User,
    is_tombstone,
)
from app.security import new_opaque_token

logger = logging.getLogger("cerebrozen.platform")

router = APIRouter(prefix="/orgs", tags=["orgs"])


async def _seats_used(session: AsyncSession, org_id: str) -> int:
    users = (
        await session.execute(
            select(func.count()).select_from(User).where(User.org_id == org_id, User.is_active)
        )
    ).scalar_one()
    now = datetime.now(timezone.utc)
    pending = 0
    for inv in (
        await session.execute(
            select(Invitation).where(Invitation.org_id == org_id, Invitation.accepted_at.is_(None))
        )
    ).scalars():
        expires_at = inv.expires_at
        if expires_at.tzinfo is None:
            expires_at = expires_at.replace(tzinfo=timezone.utc)
        if expires_at > now:
            pending += 1
    return users + pending


def _org_out(org: Org, seats_used: int) -> dict:
    return {
        "id": org.id,
        "name": org.name,
        "slug": org.slug,
        "seats_total": org.seats_total,
        "seats_used": seats_used,
        "regulated_mode": org.regulated_mode,
        "crisis_region": org.crisis_region,
        "is_active": org.is_active,
    }


# ── internal admin: tenant lifecycle ─────────────────────────────────────────


class OrgCreate(BaseModel):
    name: str = Field(min_length=2, max_length=200)
    slug: str = Field(min_length=2, max_length=80, pattern=r"^[a-z0-9-]+$")
    seats_total: int = Field(default=50, ge=1, le=100000)
    regulated_mode: bool = True
    crisis_region: str = Field(default="IN", max_length=8)


@router.post("", status_code=201, dependencies=[Depends(require_internal_admin)])
async def create_org(body: OrgCreate, session: AsyncSession = Depends(get_session)):
    exists = (
        await session.execute(select(Org).where(Org.slug == body.slug))
    ).scalar_one_or_none()
    if exists is not None:
        raise HTTPException(409, "slug already in use")
    org = Org(**body.model_dump())
    session.add(org)
    await session.commit()
    return _org_out(org, 0)


@router.get("", dependencies=[Depends(require_internal_admin)])
async def list_orgs(session: AsyncSession = Depends(get_session)):
    orgs = (await session.execute(select(Org).order_by(Org.created_at))).scalars().all()
    return [_org_out(o, await _seats_used(session, o.id)) for o in orgs]


class OrgPatch(BaseModel):
    seats_total: int | None = Field(default=None, ge=1, le=100000)
    regulated_mode: bool | None = None
    crisis_region: str | None = Field(default=None, max_length=8)
    is_active: bool | None = None


@router.patch("/{org_id}", dependencies=[Depends(require_internal_admin)])
async def patch_org(
    org_id: str, body: OrgPatch, session: AsyncSession = Depends(get_session)
):
    org = (await session.execute(select(Org).where(Org.id == org_id))).scalar_one_or_none()
    if org is None:
        raise HTTPException(404, "no such org")
    for key, value in body.model_dump(exclude_none=True).items():
        setattr(org, key, value)
    await session.commit()
    return _org_out(org, await _seats_used(session, org.id))


# ── org admin: own-org administration ────────────────────────────────────────


def _own_org_id(user: User) -> str:
    if user.org_id is None:
        raise HTTPException(400, "internal staff have no org of their own")
    return user.org_id


@router.get("/me")
async def my_org(
    user: User = Depends(require_org_admin), session: AsyncSession = Depends(get_session)
):
    org = (
        await session.execute(select(Org).where(Org.id == _own_org_id(user)))
    ).scalar_one()
    return _org_out(org, await _seats_used(session, org.id))


@router.get("/me/people")
async def my_people(
    user: User = Depends(require_org_admin), session: AsyncSession = Depends(get_session)
):
    """Seat roster: identity and status only — never coaching content.

    Excludes deleted accounts. A deletion scrubs the row in place and leaves a tombstone so
    foreign keys stay valid (`users.py::delete_me`) — but a tombstone is not a person, and
    listing one invited an org_admin to press "reactivate" on it, which would have set
    is_active=True and burned a seat forever on an account with no usable password. Found by
    clicking through the roster after an erasure, not by reading it.
    """
    people = (
        await session.execute(
            select(User)
            .where(User.org_id == _own_org_id(user), User.email.notlike(f"%{DELETED_EMAIL_DOMAIN}"))
            .order_by(User.created_at)
        )
    ).scalars().all()
    return [
        {"id": p.id, "email": p.email, "name": p.name, "role": p.role, "is_active": p.is_active}
        for p in people
    ]


class PersonPatch(BaseModel):
    """Deliberately one field. A roster row is identity + status, and status is the only
    thing an employer gets to change about a person here — not their name, not their role,
    and certainly nothing about their coaching."""

    is_active: bool


@router.patch("/me/people/{user_id}")
async def set_person_active(
    user_id: str,
    body: PersonPatch,
    user: User = Depends(require_org_admin),
    session: AsyncSession = Depends(get_session),
):
    """Offboard a leaver, or bring someone back.

    The core B2B operation, and it was missing: this roster was GET-only, so an org_admin
    could see a leaver and do nothing about them, and the seat they no longer use stayed
    counted against the org (`_seats_used` gates invitations).

    Deactivating cuts access everywhere it can:
      * login and refresh both check `is_active` (`routers/auth.py`), and so does every
        authenticated request (`deps.current_user`);
      * their refresh tokens are revoked here, so nothing survives on the old session.

    The one gap is honest and bounded: the ENGINE validates the signed token by itself and
    never calls this service, so an access token already in flight keeps working against
    the engine until it expires (ACCESS_TTL_MIN, 15 minutes). Same trade the consent claim
    documents, for the same reason — a per-request callback would put an outage between a
    person and their own coach. If a tenant needs instant engine cutoff, that is a token-TTL
    decision, not a reason to make this route lie.

    Nothing here touches coaching content: deactivation is not deletion. A leaver's own
    export and erasure stay theirs (`DELETE /users/me`), because their diary is not the
    employer's to destroy.
    """
    org_id = _own_org_id(user)
    target = (
        await session.execute(select(User).where(User.id == user_id, User.org_id == org_id))
    ).scalar_one_or_none()
    # 404 rather than 403 for someone else's employee: an org_admin should not be able to
    # probe another tenant's user ids by watching which ones answer differently.
    if target is None or is_tombstone(target):
        # A tombstone answers 404 like anyone else who is not there: they are not a person,
        # and reactivating one would consume a seat that nobody could ever use.
        raise HTTPException(404, "no such person in your organisation")

    if target.id == user.id and not body.is_active:
        # Locking yourself out of the console you administer helps nobody, and with one
        # org_admin it locks the whole tenant out with no self-service way back.
        raise HTTPException(400, "you cannot deactivate your own account")

    target.is_active = body.is_active
    if not body.is_active:
        # Without this they keep a working refresh token: auth.py would refuse it, but
        # revoking is the belt to that braces — the session is over, say so in the data.
        await session.execute(
            update(RefreshToken).where(RefreshToken.user_id == target.id).values(revoked=True)
        )
    await session.commit()
    logger.info(
        "org.person_active_changed",
        extra={"actor": user.id, "target": target.id, "org_id": org_id, "is_active": body.is_active},
    )
    return {
        "id": target.id, "email": target.email, "name": target.name,
        "role": target.role, "is_active": target.is_active,
        # The seat count is the reason this route exists — hand it back so the UI does not
        # have to refetch the org to show the effect of the click.
        "seats_used": await _seats_used(session, org_id),
    }


class InvitationCreate(BaseModel):
    email: EmailStr
    role: str = ROLE_USER


async def _create_invitation(
    session: AsyncSession, org_id: str, body: InvitationCreate
) -> dict:
    if body.role not in (ROLE_USER, ROLE_ORG_ADMIN):
        raise HTTPException(400, "role must be user or org_admin")
    org = (
        await session.execute(select(Org).where(Org.id == org_id))
    ).scalar_one_or_none()
    if org is None:
        raise HTTPException(404, "no such org")
    if await _seats_used(session, org_id) >= org.seats_total:
        raise HTTPException(409, "no seats left")
    email = body.email.lower().strip()
    existing = (
        await session.execute(select(User).where(User.email == email))
    ).scalar_one_or_none()
    if existing is not None:
        raise HTTPException(409, "an account with this email already exists")
    raw, token_hash = new_opaque_token()
    session.add(
        Invitation(
            org_id=org_id,
            email=email,
            role=body.role,
            token_hash=token_hash,
            expires_at=datetime.now(timezone.utc)
            + timedelta(days=config.INVITATION_TTL_DAYS),
        )
    )
    await session.commit()
    # The raw token/link is revealed ONCE. Email delivery is best-effort:
    # `emailed` tells the UI whether manual sharing is still needed.
    link = f"{config.ADMIN_BASE_URL}/accept?token={raw}"
    emailed = await run_in_threadpool(
        emailer.send_invitation, email, org.name, body.role, link
    )
    return {
        "email": email,
        "role": body.role,
        "invitation_token": raw,
        "invite_link": link,
        "emailed": emailed,
    }


@router.post("/me/invitations", status_code=201)
async def invite(
    body: InvitationCreate,
    user: User = Depends(require_org_admin),
    session: AsyncSession = Depends(get_session),
):
    return await _create_invitation(session, _own_org_id(user), body)


@router.post(
    "/{org_id}/invitations",
    status_code=201,
    dependencies=[Depends(require_internal_admin)],
)
async def invite_into_org(
    org_id: str, body: InvitationCreate, session: AsyncSession = Depends(get_session)
):
    """Ops path: how a freshly-created tenant gets its FIRST org admin —
    an org-less internal admin cannot use /orgs/me/invitations."""
    return await _create_invitation(session, org_id, body)
