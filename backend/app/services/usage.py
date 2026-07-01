"""Server-side usage quota — enforces the free tier the UI advertises.

Counts a user's own messages sent today (UTC) and blocks further sends with 429
once the free-tier limit is hit. Premium tiers are unlimited. This is real
enforcement (a DB count), independent of the IP rate limiter, so LLM cost/abuse
is capped per account.
"""
from __future__ import annotations

import uuid
from datetime import timedelta

from fastapi import HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import utcnow
from app.models.chat import ChatMessage
from app.models.user import User

_UNLIMITED_TIERS = {"premium", "premium_human"}


async def messages_today(db: AsyncSession, user_id: uuid.UUID) -> int:
    """Count the user's own (role='user') messages since midnight UTC."""
    since = utcnow() - timedelta(days=1)
    count = await db.scalar(
        select(func.count())
        .select_from(ChatMessage)
        .where(
            ChatMessage.user_id == user_id,
            ChatMessage.role == "user",
            ChatMessage.created_at >= since,
        )
    )
    return int(count or 0)


async def enforce_quota(db: AsyncSession, user: User) -> None:
    """Raise 429 when a free-tier user has hit the daily message limit."""
    if user.subscription_tier in _UNLIMITED_TIERS:
        return
    used = await messages_today(db, user.id)
    if used >= settings.free_daily_messages:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=(
                f"Daily free limit reached ({settings.free_daily_messages} messages). "
                "Upgrade to Premium for unlimited conversations."
            ),
        )
