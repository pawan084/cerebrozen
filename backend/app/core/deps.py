import uuid

from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import ACCESS, decode_token
from app.models.user import User

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login", auto_error=True)

_credentials_error = HTTPException(
    status_code=status.HTTP_401_UNAUTHORIZED,
    detail="Could not validate credentials",
    headers={"WWW-Authenticate": "Bearer"},
)


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_db),
) -> User:
    payload = decode_token(token, expected_type=ACCESS)
    if not payload or "sub" not in payload:
        raise _credentials_error
    try:
        user_id = uuid.UUID(payload["sub"])
    except (ValueError, TypeError):
        raise _credentials_error
    user = await db.get(User, user_id)
    if user is None or not user.is_active:
        raise _credentials_error
    # Token revocation: logout / password reset bumps token_version, so any token
    # minted before that (missing or lower `ver`) is rejected.
    if payload.get("ver", 0) != user.token_version:
        raise _credentials_error
    return user


async def get_current_admin(user: User = Depends(get_current_user)) -> User:
    if not user.is_admin:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin access required")
    return user
