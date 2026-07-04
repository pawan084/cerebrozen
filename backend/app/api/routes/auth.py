import secrets
import uuid
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db, utcnow
from app.core.ratelimit import limiter
from app.core.deps import get_current_user
from app.core.security import (
    REFRESH,
    RESET,
    VERIFY,
    create_access_token,
    create_refresh_token,
    create_reset_token,
    create_verify_token,
    decode_token,
    hash_otp,
    hash_password,
    verify_otp,
    verify_password,
)
from app.models.consent import Consent
from app.models.login_code import LoginCode
from app.models.user import User
from app.schemas.auth import (
    AppleSignInRequest,
    ForgotPasswordRequest,
    GoogleSignInRequest,
    OtpRequestBody,
    OtpVerifyBody,
    RefreshRequest,
    ResetPasswordRequest,
    SignupRequest,
    TokenBody,
    TokenPair,
)
from app.schemas.user import UserOut
from app.services import apple, email, google, nudges

router = APIRouter(prefix="/auth", tags=["auth"])

_MAX_FAILED_LOGINS = 5
_LOCKOUT_MINUTES = 15
_OTP_TTL_MINUTES = 10
_OTP_MAX_ATTEMPTS = 5


def _tokens(user: User) -> TokenPair:
    return TokenPair(
        access_token=create_access_token(str(user.id), user.token_version),
        refresh_token=create_refresh_token(str(user.id), user.token_version),
    )


@router.post("/signup", response_model=TokenPair, status_code=status.HTTP_201_CREATED)
@limiter.limit("10/minute")
async def signup(request: Request, payload: SignupRequest, db: AsyncSession = Depends(get_db)):
    existing = await db.scalar(select(User).where(User.email == payload.email.lower()))
    if existing:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered")
    user = User(
        email=payload.email.lower(),
        hashed_password=hash_password(payload.password),
        name=payload.name,
    )
    user.consent = Consent()
    db.add(user)
    await db.flush()
    # Proactive: schedule the user's first gentle nudges.
    await nudges.schedule_default_nudges(db, user)
    await db.commit()
    await db.refresh(user)
    return _tokens(user)


@router.post("/login", response_model=TokenPair)
@limiter.limit("20/minute")
async def login(request: Request, form: OAuth2PasswordRequestForm = Depends(), db: AsyncSession = Depends(get_db)):
    # OAuth2 form uses `username` for the email.
    user = await db.scalar(select(User).where(User.email == form.username.lower()))
    _bad = HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Incorrect email or password")
    if user is None:
        raise _bad

    # Account lockout after repeated failures blunts online brute force.
    if user.locked_until is not None and user.locked_until > utcnow():
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account temporarily locked after repeated failed logins. Try again shortly.",
        )

    if not verify_password(form.password, user.hashed_password):
        user.failed_login_count += 1
        if user.failed_login_count >= _MAX_FAILED_LOGINS:
            user.locked_until = utcnow() + timedelta(minutes=_LOCKOUT_MINUTES)
            user.failed_login_count = 0
        await db.commit()
        raise _bad

    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Account disabled")

    if user.failed_login_count or user.locked_until:
        user.failed_login_count = 0
        user.locked_until = None
        await db.commit()
    return _tokens(user)


@router.post("/apple", response_model=TokenPair)
@limiter.limit("20/minute")
async def apple_sign_in(
    request: Request, payload: AppleSignInRequest, db: AsyncSession = Depends(get_db)
):
    """Sign in with Apple: verify the identity token, then find-or-create the
    user — by the stable Apple id (`sub`) first, falling back to the verified
    email — and issue our own token pair."""
    claims = await apple.verify_identity_token(payload.identity_token)
    if not claims:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid Apple token")
    sub = claims.get("sub") or ""
    email = (claims.get("email") or "").lower()
    if not sub and not email:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail="Apple token carried no user identity")

    user = None
    if sub:
        user = await db.scalar(select(User).where(User.apple_sub == sub))
    if user is None and email:
        user = await db.scalar(select(User).where(User.email == email))

    if user is None:
        # New Apple user — no password, so store an unusable random hash.
        # Apple only sends `email` on the FIRST authorization (and some tokens
        # carry none at all); the stable `sub` still keys the account, with a
        # synthesized address standing in when Apple withheld the real one.
        user = User(
            email=email or f"apple_{sub}@siwa.cerebrozen.in",
            hashed_password=hash_password(secrets.token_urlsafe(32)),
            name=payload.name,
            apple_sub=sub or None,
        )
        user.consent = Consent()
        db.add(user)
        await db.flush()
        await nudges.schedule_default_nudges(db, user)
        await db.commit()
        await db.refresh(user)
    else:
        if not user.is_active:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Account disabled")
        if sub and not user.apple_sub:
            # Legacy email-keyed Apple account — adopt the stable id.
            user.apple_sub = sub
            await db.commit()
    return _tokens(user)


@router.post("/google", response_model=TokenPair)
@limiter.limit("20/minute")
async def google_sign_in(
    request: Request, payload: GoogleSignInRequest, db: AsyncSession = Depends(get_db)
):
    """Sign in with Google: verify the ID token, then find-or-create the user by
    their verified Google email and issue our own token pair."""
    claims = await google.verify_id_token(payload.id_token)
    if not claims:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid Google token")
    email = (claims.get("email") or "").lower()
    if not email:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Google did not provide an email")

    user = await db.scalar(select(User).where(User.email == email))
    if user is None:
        # New Google user — no password, so store an unusable random hash.
        user = User(
            email=email,
            hashed_password=hash_password(secrets.token_urlsafe(32)),
            name=payload.name or claims.get("name", ""),
        )
        user.consent = Consent()
        db.add(user)
        await db.flush()
        await nudges.schedule_default_nudges(db, user)
        await db.commit()
        await db.refresh(user)
    elif not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Account disabled")
    return _tokens(user)


@router.post("/otp/request")
@limiter.limit("5/minute")
async def otp_request(request: Request, payload: OtpRequestBody, db: AsyncSession = Depends(get_db)):
    """Email a 6-digit one-time sign-in code (passwordless auth). New and
    existing addresses are treated identically — the account is only created
    at verify — so there is nothing to enumerate. A re-request replaces any
    earlier code for the address."""
    addr = payload.email.lower()
    code = f"{secrets.randbelow(1_000_000):06d}"
    row = await db.scalar(select(LoginCode).where(LoginCode.email == addr))
    if row is None:
        row = LoginCode(email=addr)
        db.add(row)
    row.code_hash = hash_otp(addr, code)
    row.expires_at = utcnow() + timedelta(minutes=_OTP_TTL_MINUTES)
    row.attempts = 0
    await db.commit()
    await email.send_email(addr, "Your CereBro sign-in code",
                           f"Your sign-in code is {code}. It expires in {_OTP_TTL_MINUTES} minutes.\n\n"
                           "If you didn't request it, you can ignore this email.")
    return {"sent": True}


@router.post("/otp/verify", response_model=TokenPair)
@limiter.limit("10/minute")
async def otp_verify(request: Request, payload: OtpVerifyBody, db: AsyncSession = Depends(get_db)):
    """Exchange an emailed one-time code for a token pair. Possession of the
    inbox is the credential, so the user is found-or-created (like Apple /
    Google sign-in) and the address marked verified. Codes are single-use and
    burn after ``_OTP_MAX_ATTEMPTS`` wrong tries."""
    addr = payload.email.lower()
    _bad = HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired code")
    row = await db.scalar(select(LoginCode).where(LoginCode.email == addr))
    if row is None:
        raise _bad
    if row.expires_at <= utcnow():
        await db.delete(row)
        await db.commit()
        raise _bad
    if not verify_otp(addr, payload.code, row.code_hash):
        row.attempts += 1
        if row.attempts >= _OTP_MAX_ATTEMPTS:
            await db.delete(row)
        await db.commit()
        raise _bad
    await db.delete(row)   # single-use

    user = await db.scalar(select(User).where(User.email == addr))
    if user is None:
        # New address — passwordless account; store an unusable random hash
        # ("forgot password" mints one later if they ever want a password).
        user = User(
            email=addr,
            hashed_password=hash_password(secrets.token_urlsafe(32)),
            email_verified=True,
        )
        user.consent = Consent()
        db.add(user)
        await db.flush()
        await nudges.schedule_default_nudges(db, user)
    else:
        if not user.is_active:
            await db.commit()   # still burn the code
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Account disabled")
        # The inbox just proved itself; also clear any password-lockout state.
        user.email_verified = True
        user.failed_login_count = 0
        user.locked_until = None
    await db.commit()
    await db.refresh(user)
    return _tokens(user)


@router.post("/refresh", response_model=TokenPair)
@limiter.limit("30/minute")
async def refresh(request: Request, payload: RefreshRequest, db: AsyncSession = Depends(get_db)):
    data = decode_token(payload.refresh_token, expected_type=REFRESH)
    if not data or "sub" not in data:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid refresh token")
    user = await db.get(User, uuid.UUID(data["sub"]))
    # Reject tokens revoked by a later logout / password reset.
    if not user or not user.is_active or data.get("ver", 0) != user.token_version:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid refresh token")
    return _tokens(user)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    """Revoke every outstanding access + refresh token for this user by bumping
    the token version (server-side revocation, not just client-side discard)."""
    user.token_version += 1
    await db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/verify/request")
async def request_verification(user: User = Depends(get_current_user)):
    """Email the signed-in user a verification link."""
    token = create_verify_token(str(user.id))
    link = f"{settings.app_base_url}/verify?token={token}"
    await email.send_email(user.email, "Verify your CereBro email",
                           f"Confirm your email address:\n\n{link}\n\nThis link expires in 24 hours.")
    return {"sent": True}


@router.post("/verify")
async def verify_email(payload: TokenBody, db: AsyncSession = Depends(get_db)):
    data = decode_token(payload.token, expected_type=VERIFY)
    if not data or "sub" not in data:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired link")
    user = await db.get(User, uuid.UUID(data["sub"]))
    if user is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired link")
    user.email_verified = True
    await db.commit()
    return {"verified": True}


@router.post("/password/forgot")
@limiter.limit("5/minute")
async def forgot_password(request: Request, payload: ForgotPasswordRequest, db: AsyncSession = Depends(get_db)):
    """Email a reset link. Always returns 200 (no account enumeration)."""
    user = await db.scalar(select(User).where(User.email == payload.email.lower()))
    if user is not None:
        token = create_reset_token(str(user.id))
        link = f"{settings.app_base_url}/reset?token={token}"
        await email.send_email(user.email, "Reset your CereBro password",
                               f"Reset your password:\n\n{link}\n\nThis link expires in 1 hour. "
                               "If you didn't request this, you can ignore it.")
    return {"sent": True}


@router.post("/password/reset")
async def reset_password(payload: ResetPasswordRequest, db: AsyncSession = Depends(get_db)):
    data = decode_token(payload.token, expected_type=RESET)
    if not data or "sub" not in data:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired link")
    user = await db.get(User, uuid.UUID(data["sub"]))
    if user is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired link")
    user.hashed_password = hash_password(payload.new_password)
    user.token_version += 1        # invalidate all existing sessions
    user.failed_login_count = 0
    user.locked_until = None
    await db.commit()
    return {"reset": True}


@router.get("/me", response_model=UserOut)
async def me(user: User = Depends(get_current_user)):
    return user
