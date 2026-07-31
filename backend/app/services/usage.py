"""Server-side usage quota — enforces the free tier the UI advertises.

Counts a user's own messages sent today (UTC) and blocks further sends with 429
once the free-tier limit is hit. Premium tiers are unlimited. This is real
enforcement (a DB count), independent of the IP rate limiter, so LLM cost/abuse
is capped per account.
"""
from __future__ import annotations

import uuid
from datetime import datetime, timedelta

from fastapi import HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import utcnow
from app.models.chat import ChatMessage
from app.models.user import User

_UNLIMITED_TIERS = {"premium", "premium_human"}

# The machine-readable marker clients branch on. Kept as a constant because it
# is a cross-stack contract: iOS, Android and web all match this string.
FREE_LIMIT_CODE = "free_daily_limit"


async def messages_today(db: AsyncSession, user_id: uuid.UUID) -> int:
    """Count the user's own (role='user') messages since midnight UTC."""
    since = utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
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


def next_reset(now: datetime | None = None) -> datetime:
    """When the counter clears — the next UTC midnight.

    Sent to clients so they can render the reset in the user's OWN timezone.
    Copy that says "resets at midnight" is wrong for most of the world: the
    window is UTC, so in India it clears at 05:30 local.
    """
    now = now or utcnow()
    return (now + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)


async def enforce_quota(db: AsyncSession, user: User) -> None:
    """Raise 429 when a free-tier user has hit the daily message limit.

    The detail is a STRUCTURED object, not a sentence, because a client cannot
    otherwise tell this apart from the IP rate limiter — which also returns 429
    (slowapi, `{"error": ...}`) and means something completely different.
    Showing an upgrade prompt to someone who merely typed too fast would be
    both wrong and manipulative, so the distinction is explicit rather than
    inferred from which JSON key happens to be present.
    """
    if user.subscription_tier in _UNLIMITED_TIERS:
        return
    used = await messages_today(db, user.id)
    if used >= settings.free_daily_messages:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail={
                "code": FREE_LIMIT_CODE,
                "message": (
                    f"Daily free limit reached ({settings.free_daily_messages} messages). "
                    "Upgrade to Premium for unlimited conversations."
                ),
                "limit": settings.free_daily_messages,
                "used": used,
                "resets_at": next_reset().isoformat(),
            },
        )
