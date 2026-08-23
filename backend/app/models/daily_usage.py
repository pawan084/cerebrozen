"""A per-account, per-day counter for the calls that cost money (WC-89 follow-on).

The per-minute limits in `core/ratelimit.py` bound a BURST. They do not bound
spend, and the arithmetic is not close: plan generation at 10/minute allows
14,400 calls a day from one account — roughly thirteen million tokens — and TTS
at 60/minute allows 86,400. An account can sit at the limit indefinitely without
ever tripping it, because the limit refills every minute forever.

`services/usage.py` was the only daily cap in the product and it covers chat
alone, so everything else had no ceiling at all.

**This is an abuse ceiling, not a plan feature.** The numbers are the same for
free and paid on purpose. Making them differ would be a pricing decision — it
would make Premium materially better at voice and planning — and CLAIMS_MAP
records that the backend gates exactly two things on tier (the chat cap and
premium narration), with a banned phrase sitting there specifically for implying
tier gates that do not exist. Bounding what one account can spend is an
engineering safety measure; deciding what a subscription includes is not.

Each ceiling is set roughly five to twenty times a heavy day's real use, so a
genuine user never meets one. Anybody who does is worth knowing about.
"""

from __future__ import annotations

import uuid
from datetime import date

from sqlalchemy import Date, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class DailyUsage(Base):
    """One row per (account, feature, UTC day).

    Rows are created and incremented by a single `INSERT … ON CONFLICT DO
    UPDATE … RETURNING` statement. That is the whole design: a read-then-write
    would let two concurrent requests both see a count below the ceiling and
    both proceed, which on the endpoints this guards is exactly the traffic
    shape an abuser produces. The unique constraint is what makes the upsert
    land on one row, so it is load-bearing rather than hygiene.
    """

    __tablename__ = "daily_usage"
    __table_args__ = (
        UniqueConstraint("user_id", "feature", "day", name="uq_daily_usage_account_feature_day"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    #: A key from `services.usage.CEILINGS`, not a free-form string.
    feature: Mapped[str] = mapped_column(String(40), index=True)
    #: UTC, matching the chat quota's window. A local-midnight reset would need
    #: the user's timezone and would hand anybody who changes it a second
    #: allowance.
    day: Mapped[date] = mapped_column(Date, index=True)
    count: Mapped[int] = mapped_column(Integer, default=0)
