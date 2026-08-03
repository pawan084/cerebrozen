from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class PracticeCatalog(Base):
    """A curated thing a user could try, addressed by a stable slug.

    Deliberately a small, hand-authored catalogue rather than generated text:
    a recommendation the product makes about someone's mental health should be
    something a human wrote and an admin can edit or retire, not something a
    model improvised.
    """

    __tablename__ = "practice_catalog"

    slug: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    title: Mapped[str] = mapped_column(String(160))
    body: Mapped[str] = mapped_column(Text, default="")
    # Where it leads — mirrors the widget/deeplink vocabulary the clients use.
    action: Mapped[str] = mapped_column(String(64), default="")
    active: Mapped[bool] = mapped_column(Boolean, default=True, index=True)


class Recommendation(Base):
    """One practice offered to one user, with the reason it was offered.

    `reason` is the pattern statement that triggered it, stored verbatim so the
    user can always be told *why* — a suggestion with no visible basis is the
    thing this product's Pattern Dashboard exists to avoid.

    Accept/dismiss is the feedback loop: dismissing is a real signal, not a
    snooze, and the same practice is not offered again while it is resolved.
    """

    __tablename__ = "recommendations"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    practice_slug: Mapped[str] = mapped_column(String(64), index=True)
    reason: Mapped[str] = mapped_column(Text, default="")
    # pending | accepted | dismissed
    status: Mapped[str] = mapped_column(String(16), default="pending", index=True)
    resolved_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
