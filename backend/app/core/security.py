from datetime import timedelta
from typing import Any

import bcrypt
from jose import JWTError, jwt

from app.core.config import settings
from app.core.database import utcnow

ACCESS = "access"
REFRESH = "refresh"
VERIFY = "verify"      # email-verification link token
RESET = "reset"        # password-reset link token


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()


def verify_password(password: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode(), hashed.encode())
    except ValueError:
        return False


def _create_token(subject: str, token_type: str, expires_delta: timedelta,
                  version: int | None = None) -> str:
    now = utcnow()
    payload: dict[str, Any] = {
        "sub": subject,
        "type": token_type,
        "iat": now,
        "exp": now + expires_delta,
    }
    if version is not None:
        payload["ver"] = version   # token generation, for revocation on logout/reset
    return jwt.encode(payload, settings.secret_key, algorithm=settings.algorithm)


def create_access_token(subject: str, version: int = 0) -> str:
    return _create_token(subject, ACCESS, timedelta(minutes=settings.access_token_expire_minutes), version)


def create_refresh_token(subject: str, version: int = 0) -> str:
    return _create_token(subject, REFRESH, timedelta(days=settings.refresh_token_expire_days), version)


def create_verify_token(subject: str) -> str:
    return _create_token(subject, VERIFY, timedelta(hours=24))


def create_reset_token(subject: str) -> str:
    return _create_token(subject, RESET, timedelta(hours=1))


def decode_token(token: str, expected_type: str | None = None) -> dict[str, Any] | None:
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=[settings.algorithm])
    except JWTError:
        return None
    if expected_type and payload.get("type") != expected_type:
        return None
    return payload
