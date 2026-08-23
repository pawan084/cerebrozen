"""Which features need a confirmed email address, and which never can (WC-90 follow-on).

`email_verified` has existed on `User` since the beginning, set by the Apple and
Google flows and by `POST /auth/verify`, and until now **nothing read it**. A
brand-new account created with any string that parses as an address drew its
full free allowance of real LLM completion immediately. That is the half of bot
protection `services/botcheck.py` cannot do: a challenge proves a human was
present for thirty seconds, while a delivered email proves somebody controls a
mailbox — which is the thing that actually costs a farm money per account.

Four rules decide who this applies to, and each exists because the obvious
version of the gate breaks something real.

**Accounts that predate it are exempt.** `email_verified` defaulted to false
and signup sent nothing to confirm until this release, so every existing
account carries false — not because anyone failed a check but because there
was no check to fail. Migration b2e9f47c1a08 marks them, and it marks them in
their own column rather than backfilling `email_verified`, which would have
made that flag assert something untrue for every reader after it.

**It is inert unless we can send email at all.** With no SMTP configured
`services.email.send_email` logs and returns, so a user genuinely cannot verify
— there is no message to act on. Gating on a proof we are incapable of
delivering would not be strict, it would be a product that cannot be used. Same
shape as the bot challenge: the capability is the switch.

**Paying accounts are exempt.** A card is a stronger proof of a person than an
email, and dunning an existing subscriber into a verification wall for a feature
they have paid for is indefensible.

**It never closes the safety path.** Nothing here gates `/chat` outright. An
unverified account gets a *smaller* daily allowance rather than a locked door,
and `routes/chat.py` waives even that when the free keyword floor flags the
message — see `safety.keyword_floor`. Someone typing the worst sentence of their
life must not meet a billing rule, and "safety never blocks" is not a slogan
that gets an exception for cost control.
"""

from __future__ import annotations

from fastapi import HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.models.user import User
from app.services import entitlements

#: The marker clients branch on. A 403 with this code means "confirm your email
#: and this works" — a different remedy from every other 403 in the product, and
#: not one a status code can express on its own.
UNVERIFIED_CODE = "email_unverified"


def gate_active() -> bool:
    """Whether the product is capable of asking for a verification at all."""
    return bool(settings.smtp_host)


async def is_exempt(db: AsyncSession, user: User) -> bool:
    """Whether this user is past the gate, for any of the three reasons."""
    if user.email_verified:
        return True
    # Signed up before the gate existed, when nothing was ever sent to confirm.
    # Charging somebody for a requirement that did not exist when they joined is
    # not enforcement, it is a regression with a policy attached.
    if user.verification_grandfathered:
        return True
    if not gate_active():
        return True
    return (await entitlements.resolve(db, user)).is_paid


async def require_verified_email(db: AsyncSession, user: User, *, feature: str) -> None:
    """Raise 403 unless this user may use a provider-backed feature.

    `feature` is named in the payload so a client can say which thing is waiting
    rather than showing a generic wall — the difference between "confirm your
    email to use voice" and "something went wrong".
    """
    if await is_exempt(db, user):
        return
    raise HTTPException(
        status_code=status.HTTP_403_FORBIDDEN,
        detail={
            "code": UNVERIFIED_CODE,
            "feature": feature,
            "message": (
                "Confirm your email address to use this. Check your inbox for the "
                "link we sent when you signed up, or ask for a new one."
            ),
        },
    )


async def daily_message_allowance(db: AsyncSession, user: User) -> int:
    """How many chat messages a day this account gets before the free-tier 429.

    Unverified accounts get a smaller number rather than zero. The point is to
    bound what an unattended signup can spend, not to make the companion
    unreachable to somebody who has not opened their email yet — and the first
    conversation is exactly when a person is deciding whether this product is
    worth trusting.
    """
    if await is_exempt(db, user):
        return settings.free_daily_messages
    return min(settings.unverified_daily_messages, settings.free_daily_messages)
