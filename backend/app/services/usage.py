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
from sqlalchemy import func, select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import utcnow
from app.models.chat import ChatMessage
from app.models.user import User
from app.services import entitlements, verification

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


async def enforce_quota(db: AsyncSession, user: User, *, exempt: bool = False) -> None:
    """Raise 429 when a free-tier user has hit the daily message limit.

    The detail is a STRUCTURED object, not a sentence, because a client cannot
    otherwise tell this apart from the IP rate limiter — which also returns 429
    (slowapi, `{"error": ...}`) and means something completely different.
    Showing an upgrade prompt to someone who merely typed too fast would be
    both wrong and manipulative, so the distinction is explicit rather than
    inferred from which JSON key happens to be present.
    """
    # Resolved, not read off the column: an organisation-sponsored member is
    # unlimited too, and reading `user.subscription_tier` here was the gate
    # that made sponsorship grant nothing.
    # `exempt` is the safety waiver, passed by routes/chat.py when the free
    # keyword floor flags the message. A daily allowance is a billing rule, and
    # a billing rule must not be the thing standing between somebody and the
    # sentence they are trying to type.
    if exempt:
        return
    if (await entitlements.resolve(db, user)).is_paid:
        return
    limit = await verification.daily_message_allowance(db, user)
    used = await messages_today(db, user.id)
    if used >= limit:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail={
                "code": FREE_LIMIT_CODE,
                "message": (
                    f"Daily free limit reached ({limit} messages). "
                    "Upgrade to Premium for unlimited conversations."
                ),
                "limit": limit,
                "used": used,
                "resets_at": next_reset().isoformat(),
            },
        )


# ── Daily ceilings on the calls that cost money (WC-89 follow-on) ────────
#
# The chat quota above is a PRODUCT limit: it is what the free tier means, it is
# advertised, and paid accounts are exempt from it. Everything below is the
# opposite kind of thing — an abuse ceiling, identical for every tier, set far
# above any real day. See app/models/daily_usage.py for why the two must not be
# confused, and why making these differ by tier would be a pricing decision
# rather than an engineering one.

#: Feature key → calls per account per UTC day.
#:
#: Sized against a heavy day of genuine use, then multiplied. TTS is called
#: sentence by sentence, so one spoken reply is several calls and a heavy voice
#: day is a few hundred; plan regeneration is a deliberate act and a heavy day is
#: ten. Compare with what the per-minute limits allow today and the gap is the
#: point: TTS at 60/minute permits 86,400 calls a day, planning at 10/minute
#: permits 14,400.
CEILINGS: dict[str, int] = {
    "voice_tts": 2000,
    "voice_stt": 500,
    "plan_generate": 100,
    "goal_decompose": 100,
    "assessment_topics": 200,
    "oracle_turn": 500,
}

#: Distinguishable from FREE_LIMIT_CODE on purpose. That one means "upgrade and
#: this goes away"; this one means "come back tomorrow", and showing an upgrade
#: prompt for it would be selling a fix that is not for sale.
DAILY_CEILING_CODE = "daily_ceiling"


async def consume(db: AsyncSession, user: User, feature: str) -> int:
    """Count one call and raise 429 if it takes the account past the ceiling.

    Increments with a single `INSERT … ON CONFLICT DO UPDATE … RETURNING`. A
    read-then-write would let two concurrent requests both see a count below the
    ceiling and both proceed — and concurrent requests are precisely the traffic
    an abuser generates, so a check that only holds under sequential load is not
    a ceiling at all.

    The increment happens before the verdict, so a refused call still counts.
    That is deliberate: an attempt is an attempt, and it means hammering a
    refused endpoint cannot be used to keep the counter artificially low.
    """
    ceiling = CEILINGS.get(feature)
    if ceiling is None:
        raise ValueError(f"unknown metered feature {feature!r}")

    today = utcnow().date()
    # The id is supplied rather than left to a default, because the two ways
    # this schema gets built do not agree: the Alembic revision gives the column
    # a `gen_random_uuid()` server default, while `Base.metadata.create_all` —
    # which is how the test database is made — carries only the Python-side
    # default that an ORM insert would apply and a raw statement never sees.
    # Passing it explicitly means this works the same on both, instead of
    # passing in production and failing in CI.
    used = await db.scalar(
        text(
            "INSERT INTO daily_usage (id, user_id, feature, day, count) "
            "VALUES (:id, :uid, :feature, :day, 1) "
            "ON CONFLICT (user_id, feature, day) "
            "DO UPDATE SET count = daily_usage.count + 1 "
            "RETURNING count"
        ),
        {"id": uuid.uuid4(), "uid": user.id, "feature": feature, "day": today},
    )
    await db.commit()

    if used > ceiling:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail={
                "code": DAILY_CEILING_CODE,
                "feature": feature,
                "message": (
                    "You've hit the daily limit for this. It resets at midnight UTC."
                ),
                "limit": ceiling,
                "resets_at": next_reset().isoformat(),
            },
        )
    return used
