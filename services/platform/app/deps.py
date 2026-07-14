"""FastAPI dependencies: DB session, current user, role gates.

Roles are enforced server-side in dependencies — a 403 by default, never a UI
convention (the reference admin's rule)."""

import jwt as pyjwt
from fastapi import Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models import ROLE_INTERNAL_ADMIN, ROLE_ORG_ADMIN, User
from app.security import decode_access_token


async def current_user(
    request: Request, session: AsyncSession = Depends(get_session)
) -> User:
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        raise HTTPException(401, "missing bearer token")
    try:
        claims = decode_access_token(auth[7:])
    except pyjwt.PyJWTError as exc:
        raise HTTPException(401, f"invalid token: {exc}")
    user = (
        await session.execute(select(User).where(User.id == claims.get("sub", "")))
    ).scalar_one_or_none()
    if user is None or not user.is_active:
        raise HTTPException(401, "unknown or disabled user")
    return user


async def require_org_admin(user: User = Depends(current_user)) -> User:
    if user.role not in (ROLE_ORG_ADMIN, ROLE_INTERNAL_ADMIN):
        raise HTTPException(403, "org admin required")
    return user


async def require_internal_admin(user: User = Depends(current_user)) -> User:
    if user.role != ROLE_INTERNAL_ADMIN:
        raise HTTPException(403, "internal admin required")
    return user
