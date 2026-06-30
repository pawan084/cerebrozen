import secrets
import uuid

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.ratelimit import limiter
from app.core.deps import get_current_user
from app.core.security import (
    REFRESH,
    create_access_token,
    create_refresh_token,
    decode_token,
    hash_password,
    verify_password,
)
from app.models.consent import Consent
from app.models.user import User
from app.schemas.auth import (
    AppleSignInRequest,
    GoogleSignInRequest,
    RefreshRequest,
    SignupRequest,
    TokenPair,
)
from app.schemas.user import UserOut
from app.services import apple, google, nudges

router = APIRouter(prefix="/auth", tags=["auth"])


def _tokens(user: User) -> TokenPair:
    return TokenPair(
        access_token=create_access_token(str(user.id)),
        refresh_token=create_refresh_token(str(user.id)),
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
    if not user or not verify_password(form.password, user.hashed_password):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Incorrect email or password")
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Account disabled")
    return _tokens(user)


@router.post("/apple", response_model=TokenPair)
@limiter.limit("20/minute")
async def apple_sign_in(
    request: Request, payload: AppleSignInRequest, db: AsyncSession = Depends(get_db)
):
    """Sign in with Apple: verify the identity token, then find-or-create the
    user by their verified Apple email and issue our own token pair."""
    claims = await apple.verify_identity_token(payload.identity_token)
    if not claims:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid Apple token")
    email = (claims.get("email") or "").lower()
    if not email:
        # A relayed/private Apple address is still returned as `email`; absence
        # means the token didn't carry one (rare) — we can't key an account.
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Apple did not provide an email")

    user = await db.scalar(select(User).where(User.email == email))
    if user is None:
        # New Apple user — no password, so store an unusable random hash.
        user = User(
            email=email,
            hashed_password=hash_password(secrets.token_urlsafe(32)),
            name=payload.name,
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


@router.post("/refresh", response_model=TokenPair)
async def refresh(payload: RefreshRequest, db: AsyncSession = Depends(get_db)):
    data = decode_token(payload.refresh_token, expected_type=REFRESH)
    if not data or "sub" not in data:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid refresh token")
    user = await db.get(User, uuid.UUID(data["sub"]))
    if not user or not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid refresh token")
    return _tokens(user)


@router.get("/me", response_model=UserOut)
async def me(user: User = Depends(get_current_user)):
    return user
