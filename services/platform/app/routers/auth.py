"""Login, refresh rotation, logout, invitation acceptance.

Refresh tokens are opaque, stored hashed, and SINGLE-USE: each refresh revokes
the presented token and issues a new one. Presenting an already-revoked token
is treated as theft — every session for that user is revoked (reuse detection,
the reference clients' rotation contract)."""

import re
import secrets
import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Form, HTTPException
from pydantic import BaseModel
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession
from starlette.concurrency import run_in_threadpool

from app import config, emailer
from app.db import get_session
from app.ratelimit import limit_auth
from app.models import (
    PERSONAL_ORG_SLUG_PREFIX,
    PLAN_ENTERPRISE,
    ROLE_USER,
    ROLES,
    Invitation,
    Org,
    OtpCode,
    PasswordReset,
    RefreshToken,
    Subscription,
    User,
    resolve_plan,
)
from app.security import (
    hash_opaque,
    hash_password,
    issue_access_token,
    new_opaque_token,
    verify_password,
)

router = APIRouter(prefix="/auth", tags=["auth"])

_EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


async def _resolve_plan(session: AsyncSession, user: User) -> str:
    """The user's effective consumer plan for the JWT claim. B2B seats / internal
    staff resolve to enterprise; a personal account to free or its active plan."""
    if not user.org_id:
        return resolve_plan(None, None)
    org = await session.get(Org, user.org_id)
    sub = (
        await session.execute(
            select(Subscription).where(Subscription.org_id == user.org_id)
        )
    ).scalar_one_or_none()
    return resolve_plan(org, sub)


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
    plan = await _resolve_plan(session, user)
    # 18+ gate: a B2B seat / internal staffer is adult by contract (they never do consumer
    # onboarding); a personal account is adult only once it has attested. The engine refuses
    # coaching to a token whose `adult` is explicitly False.
    adult = plan == PLAN_ENTERPRISE or user.adult_attested_at is not None
    await session.commit()
    return TokenPair(
        access_token=issue_access_token(user, plan=plan, adult=adult), refresh_token=raw
    )


@router.post("/login", response_model=TokenPair, dependencies=[Depends(limit_auth)])
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


class SignupIn(BaseModel):
    email: str
    password: str
    name: str = ""


@router.post(
    "/signup", response_model=TokenPair, status_code=201, dependencies=[Depends(limit_auth)]
)
async def signup(body: SignupIn, session: AsyncSession = Depends(get_session)):
    """Consumer self-serve signup (B2C).

    Unlike accept_invitation there is no inviting org: the account stands alone,
    so we mint a PERSONAL org-of-one and its sole member, then issue a session.
    Everything downstream — the engine's org_id tenancy, the signed consent
    claims, seat accounting — works unchanged, because a personal org is a real
    org with seats_total=1 (see models.is_personal_org). regulated_mode and the
    crisis defaults come from the Org model, so safety-as-code protects a solo
    user exactly as it protects an enterprise seat."""
    email = body.email.lower().strip()
    # Non-empty local part, a dot-bearing domain, no whitespace on either side.
    # Deliberately permissive (real validation is the eventual verification mail),
    # but tight enough to reject the obvious junk a fat-fingered field produces.
    if len(email) > 320 or not _EMAIL_RE.match(email):
        raise HTTPException(400, "a valid email is required")
    if len(body.password) < 10:
        raise HTTPException(400, "password must be at least 10 characters")
    existing = (
        await session.execute(select(User).where(User.email == email))
    ).scalar_one_or_none()
    if existing is not None:
        # NOTE: confirming the address exists is a mild enumeration vector the
        # rest of /auth avoids. Accepted here because the client expects a
        # synchronous token pair; the hardening path is verified (double-opt-in)
        # signup, which is non-enumerating. Tracked for the consumer-auth phase.
        raise HTTPException(409, "an account with this email already exists")
    name = body.name.strip()[:200]
    # A personal workspace name the person would actually recognise: "<Name>'s space" when
    # we have a name, but a warm generic when we don't — never "<email-local-part>'s space",
    # which turns "free1784@…" into "free1784's space" (backlog #42).
    org = Org(
        name=(f"{name}'s space" if name else "My CereBro space"),
        slug=f"{PERSONAL_ORG_SLUG_PREFIX}{uuid.uuid4().hex}",
        seats_total=1,
    )
    session.add(org)
    await session.flush()  # assigns org.id before the user references it
    user = User(
        org_id=org.id,
        email=email,
        name=name,
        password_hash=hash_password(body.password),
        role=ROLE_USER,
    )
    session.add(user)
    try:
        await session.commit()
    except IntegrityError:
        # Lost a race on the unique email (two concurrent signups). Same answer.
        await session.rollback()
        raise HTTPException(409, "an account with this email already exists")
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


class OtpRequestIn(BaseModel):
    email: str


@router.post("/otp/request", dependencies=[Depends(limit_auth)])
async def otp_request(body: OtpRequestIn, session: AsyncSession = Depends(get_session)):
    """Email a one-time sign-in code (passwordless). ALWAYS 200 (non-enumerating); a
    code is only minted + sent when the email has an active account. Requesting a new
    code invalidates any prior unused one for that address."""
    email = body.email.lower().strip()
    user = (
        await session.execute(select(User).where(User.email == email))
    ).scalar_one_or_none()
    if user is not None and user.is_active:
        now = datetime.now(timezone.utc)
        await session.execute(
            update(OtpCode)
            .where(OtpCode.email == email, OtpCode.used_at.is_(None))
            .values(used_at=now)
        )
        code = f"{secrets.randbelow(1_000_000):06d}"
        session.add(
            OtpCode(
                email=email,
                code_hash=hash_opaque(code),
                expires_at=now + timedelta(minutes=config.OTP_TTL_MINUTES),
            )
        )
        await session.commit()
        await run_in_threadpool(emailer.send_otp_code, email, code)
    return {"ok": True}


class OtpVerifyIn(BaseModel):
    email: str
    code: str


@router.post("/otp/verify", response_model=TokenPair, dependencies=[Depends(limit_auth)])
async def otp_verify(body: OtpVerifyIn, session: AsyncSession = Depends(get_session)):
    """Exchange a valid code for a session. Wrong/expired/too-many-attempts all give the
    same 400 (non-enumerating). A wrong guess burns an attempt; the code self-destructs
    after OTP_MAX_ATTEMPTS so a 6-digit secret can't be brute-forced."""
    email = body.email.lower().strip()
    now = datetime.now(timezone.utc)
    bad = HTTPException(400, "invalid or expired code")
    row = (
        await session.execute(
            select(OtpCode)
            .where(OtpCode.email == email, OtpCode.used_at.is_(None))
            .order_by(OtpCode.created_at.desc())
        )
    ).scalars().first()
    if row is None:
        raise bad
    expires_at = row.expires_at
    if expires_at.tzinfo is None:  # SQLite drops tzinfo
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at < now:
        raise bad
    if not secrets.compare_digest(row.code_hash, hash_opaque(body.code.strip())):
        row.attempts += 1
        if row.attempts >= config.OTP_MAX_ATTEMPTS:
            row.used_at = now  # too many tries — burn it
        await session.commit()
        raise bad
    user = (
        await session.execute(select(User).where(User.email == email))
    ).scalar_one_or_none()
    if user is None or not user.is_active:
        raise bad
    row.used_at = now
    await session.commit()
    return await _issue_pair(session, user)


class ForgotIn(BaseModel):
    email: str


@router.post("/password/forgot", dependencies=[Depends(limit_auth)])
async def password_forgot(body: ForgotIn, session: AsyncSession = Depends(get_session)):
    """Start a password reset. ALWAYS answers 200 with the same body — it never reveals
    whether an account exists (matching /login's non-enumeration posture). If the email
    does have an active account, a single-use reset token is minted and best-effort
    emailed (unconfigured SMTP just logs it, exactly like invitations)."""
    email = body.email.lower().strip()
    user = (
        await session.execute(select(User).where(User.email == email))
    ).scalar_one_or_none()
    if user is not None and user.is_active:
        raw, token_hash = new_opaque_token()
        session.add(
            PasswordReset(
                user_id=user.id,
                token_hash=token_hash,
                expires_at=datetime.now(timezone.utc)
                + timedelta(hours=config.RESET_TTL_HOURS),
            )
        )
        await session.commit()
        link = f"{config.APP_BASE_URL}/reset?token={raw}"
        await run_in_threadpool(emailer.send_password_reset, email, link)
    return {"ok": True}


class ResetIn(BaseModel):
    token: str
    password: str


@router.post("/password/reset", dependencies=[Depends(limit_auth)])
async def password_reset(body: ResetIn, session: AsyncSession = Depends(get_session)):
    """Complete a reset with the emailed token. Single-use + expiring; a successful
    reset revokes every existing session (a password change is a security event)."""
    row = (
        await session.execute(
            select(PasswordReset).where(PasswordReset.token_hash == hash_opaque(body.token))
        )
    ).scalar_one_or_none()
    if row is None or row.used_at is not None:
        raise HTTPException(400, "invalid or already-used reset link")
    expires_at = row.expires_at
    if expires_at.tzinfo is None:  # SQLite drops tzinfo
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at < datetime.now(timezone.utc):
        raise HTTPException(400, "reset link has expired")
    if len(body.password) < 10:
        raise HTTPException(400, "password must be at least 10 characters")
    user = (
        await session.execute(select(User).where(User.id == row.user_id))
    ).scalar_one_or_none()
    if user is None or not user.is_active:
        raise HTTPException(400, "this account is unavailable")
    user.password_hash = hash_password(body.password)
    row.used_at = datetime.now(timezone.utc)
    # A password reset invalidates every existing session for this user.
    await session.execute(
        update(RefreshToken).where(RefreshToken.user_id == user.id).values(revoked=True)
    )
    await session.commit()
    return {"ok": True}
