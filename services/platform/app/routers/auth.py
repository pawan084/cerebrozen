"""Login, refresh rotation, logout, invitation acceptance.

Refresh tokens are opaque, stored hashed, and SINGLE-USE: each refresh revokes
the presented token and issues a new one. Presenting an already-revoked token
is treated as theft — every session for that user is revoked (reuse detection,
the reference clients' rotation contract)."""

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Form, HTTPException
from pydantic import BaseModel
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app import config
from app.db import get_session
from app.models import ROLE_USER, ROLES, Invitation, Org, RefreshToken, User
from app.security import (
    hash_opaque,
    hash_password,
    issue_access_token,
    new_opaque_token,
    verify_password,
)

router = APIRouter(prefix="/auth", tags=["auth"])


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


async def _issue_pair(session: AsyncSession, user: User) -> TokenPair:
    raw, token_hash = new_opaque_token()
    session.add(
        RefreshToken(
            user_id=user.id,
            token_hash=token_hash,
            expires_at=datetime.now(timezone.utc)
            + timedelta(days=config.REFRESH_TTL_DAYS),
        )
    )
    await session.commit()
    return TokenPair(access_token=issue_access_token(user), refresh_token=raw)


@router.post("/login", response_model=TokenPair)
async def login(
    username: str = Form(...),
    password: str = Form(...),
    session: AsyncSession = Depends(get_session),
):
    user = (
        await session.execute(select(User).where(User.email == username.lower().strip()))
    ).scalar_one_or_none()
    # Same error for wrong email and wrong password: no account enumeration.
    if user is None or not user.is_active or not verify_password(password, user.password_hash):
        raise HTTPException(401, "invalid credentials")
    return await _issue_pair(session, user)


class RefreshIn(BaseModel):
    refresh_token: str


@router.post("/refresh", response_model=TokenPair)
async def refresh(body: RefreshIn, session: AsyncSession = Depends(get_session)):
    token_hash = hash_opaque(body.refresh_token)
    row = (
        await session.execute(
            select(RefreshToken).where(RefreshToken.token_hash == token_hash)
        )
    ).scalar_one_or_none()
    if row is None:
        raise HTTPException(401, "unknown refresh token")
    if row.revoked:
        # Reuse of a rotated token = a stolen token being replayed. Kill everything.
        await session.execute(
            update(RefreshToken)
            .where(RefreshToken.user_id == row.user_id)
            .values(revoked=True)
        )
        await session.commit()
        raise HTTPException(401, "refresh token reuse detected; all sessions revoked")
    expires_at = row.expires_at
    if expires_at.tzinfo is None:  # SQLite loses tzinfo
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at < datetime.now(timezone.utc):
        raise HTTPException(401, "refresh token expired")
    user = (
        await session.execute(select(User).where(User.id == row.user_id))
    ).scalar_one_or_none()
    if user is None or not user.is_active:
        raise HTTPException(401, "unknown or disabled user")
    row.revoked = True
    return await _issue_pair(session, user)


@router.post("/logout", status_code=204)
async def logout(body: RefreshIn, session: AsyncSession = Depends(get_session)):
    await session.execute(
        update(RefreshToken)
        .where(RefreshToken.token_hash == hash_opaque(body.refresh_token))
        .values(revoked=True)
    )
    await session.commit()


class AcceptInvitationIn(BaseModel):
    token: str
    name: str
    password: str


@router.post("/accept-invitation", response_model=TokenPair, status_code=201)
async def accept_invitation(
    body: AcceptInvitationIn, session: AsyncSession = Depends(get_session)
):
    inv = (
        await session.execute(
            select(Invitation).where(Invitation.token_hash == hash_opaque(body.token))
        )
    ).scalar_one_or_none()
    if inv is None or inv.accepted_at is not None:
        raise HTTPException(400, "invalid or already-used invitation")
    expires_at = inv.expires_at
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at < datetime.now(timezone.utc):
        raise HTTPException(400, "invitation expired")
    org = (
        await session.execute(select(Org).where(Org.id == inv.org_id, Org.is_active))
    ).scalar_one_or_none()
    if org is None:
        raise HTTPException(400, "organization is not active")
    if len(body.password) < 10:
        raise HTTPException(400, "password must be at least 10 characters")
    existing = (
        await session.execute(select(User).where(User.email == inv.email))
    ).scalar_one_or_none()
    if existing is not None:
        raise HTTPException(409, "an account with this email already exists")
    role = inv.role if inv.role in ROLES else ROLE_USER
    user = User(
        org_id=inv.org_id,
        email=inv.email,
        name=body.name.strip()[:200],
        password_hash=hash_password(body.password),
        role=role,
    )
    inv.accepted_at = datetime.now(timezone.utc)
    session.add(user)
    await session.commit()
    return await _issue_pair(session, user)
