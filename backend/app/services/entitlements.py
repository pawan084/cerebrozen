"""One place that answers "what may this account use today?".

Two different things can pay for premium, and until this module existed only
one of them worked. The person can buy it (App Store / Play / Stripe, all of
which land on ``users.subscription_tier``), or their employer can sponsor it
(an active ``OrgMembership`` in an organisation whose ``grants_premium`` is
set). ``services.organizations.is_sponsored`` computed the second correctly and
*nothing called it*, so a sponsored member got a row in the database and no
benefit from it anywhere in the product.

Two rules hold this together:

1. **The resolution is computed, never written back.** It would be one line to
   set ``user.subscription_tier = "premium"`` when a sponsorship starts, and it
   would be wrong: sponsorships end — a contract lapses, someone leaves the
   company — and a stored tier would leave that account premium forever with
   nobody paying for it. The column stays the record of what the *person*
   bought; this module answers what they may currently *use*. The two are
   deliberately different values, and ``test_entitlements`` pins that the
   column is untouched by sponsorship.
2. **Every gate resolves through here.** The tier sets used to be duplicated as
   private constants in ``services/usage.py`` and ``services/media.py``, which
   is exactly how a third gate would have been written that sponsorship again
   did not reach. They now import :data:`PAID_TIERS` from this module and take
   the resolved tier as an argument rather than reading the column themselves.

Nothing here reads or exposes anything about the member to their organisation.
Entitlement flows one way: the employer pays, and learns nothing back.
"""
from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.user import User
from app.schemas.user import UserOut
from app.services import organizations as org_service

#: Tiers that unlock unlimited chat and premium narration. The canonical set —
#: cross-stack contract with iOS/Android/web, and with ``billing.py``'s pattern.
PAID_TIERS: frozenset[str] = frozenset({"premium", "premium_human"})

FREE_TIER = "free"

#: What sponsorship grants. Deliberately an existing tier rather than a new
#: "sponsored" value: every client already branches on these three strings, and
#: inventing a fourth would silently render a paywall to someone entitled to
#: skip it until all four clients shipped an update.
SPONSORED_TIER = "premium"


@dataclass(frozen=True)
class Entitlement:
    """What the caller may use, and why."""

    #: The tier to enforce and to report. Never persisted.
    tier: str
    #: True when the tier above comes from an organisation rather than a
    #: purchase. Clients need this to avoid offering a "cancel subscription"
    #: link for something the member cannot cancel.
    sponsored: bool

    @property
    def is_paid(self) -> bool:
        return self.tier in PAID_TIERS


FREE = Entitlement(tier=FREE_TIER, sponsored=False)


async def resolve(db: AsyncSession, user: User | None) -> Entitlement:
    """The effective entitlement for ``user`` (anonymous callers are free).

    A paid tier the person bought wins without a query — it is already at least
    as good as sponsorship, and this runs on the catalogue route that serves
    signed-out browsers.
    """
    if user is None:
        return FREE
    stored = user.subscription_tier or FREE_TIER
    if stored in PAID_TIERS:
        return Entitlement(tier=stored, sponsored=False)
    if await org_service.is_sponsored(db, user.id):
        return Entitlement(tier=SPONSORED_TIER, sponsored=True)
    return Entitlement(tier=stored, sponsored=False)


async def effective_tier(db: AsyncSession, user: User | None) -> str:
    """Shorthand for :func:`resolve`'s tier — what a gate should check."""
    return (await resolve(db, user)).tier


async def user_out(db: AsyncSession, user: User) -> UserOut:
    """Serialize the caller's own profile with the tier the server enforces.

    Reporting the stored column here would put the client and the server into
    disagreement for exactly one group of people: a sponsored member would be
    shown a paywall for chat the backend would then happily let them use. The
    override is applied to the response model, never to the ORM row.
    """
    ent = await resolve(db, user)
    return UserOut.model_validate(user).model_copy(
        update={"subscription_tier": ent.tier, "sponsored": ent.sponsored}
    )
