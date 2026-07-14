"""Org lifecycle (internal admin) and org-scoped administration (org admin).

Seat accounting counts active users plus pending invitations — an invitation
IS a seat commitment, or an org could oversubscribe by inviting."""

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, EmailStr, Field
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app import config
from app.db import get_session
from app.deps import require_internal_admin, require_org_admin
from app.models import ROLE_ORG_ADMIN, ROLE_USER, Invitation, Org, User
from app.security import new_opaque_token

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
    """Seat roster: identity and status only — never coaching content."""
    people = (
        await session.execute(
            select(User).where(User.org_id == _own_org_id(user)).order_by(User.created_at)
        )
    ).scalars().all()
    return [
        {"id": p.id, "email": p.email, "name": p.name, "role": p.role, "is_active": p.is_active}
        for p in people
    ]


class InvitationCreate(BaseModel):
    email: EmailStr
    role: str = ROLE_USER


@router.post("/me/invitations", status_code=201)
async def invite(
    body: InvitationCreate,
    user: User = Depends(require_org_admin),
    session: AsyncSession = Depends(get_session),
):
    org_id = _own_org_id(user)
    if body.role not in (ROLE_USER, ROLE_ORG_ADMIN):
        raise HTTPException(400, "role must be user or org_admin")
    org = (await session.execute(select(Org).where(Org.id == org_id))).scalar_one()
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
    # The raw token is returned ONCE; email delivery is a Phase 2 wiring task.
    return {"email": email, "role": body.role, "invitation_token": raw}
