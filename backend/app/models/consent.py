from __future__ import annotations

import uuid
from typing import TYPE_CHECKING

from sqlalchemy import Boolean, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base

if TYPE_CHECKING:
    from app.models.user import User


class Consent(Base):
    """Per-category consent (DPDP "specific and informed": one flag per data
    category × purpose, not a blanket wellness toggle). Enforcement map:
    mood_history → insights/plan signals from check-ins; ai_memory → chat
    long-term recall; journal_memory → journal titles/counts in AI prompts and
    insights; sleep_history → sleep diary in plans/insights; voice_storage →
    audio retention; model_training → training opt-in (no pipeline yet)."""

    __tablename__ = "consents"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), unique=True)
    mood_history: Mapped[bool] = mapped_column(Boolean, default=True)
    ai_memory: Mapped[bool] = mapped_column(Boolean, default=True)
    voice_storage: Mapped[bool] = mapped_column(Boolean, default=False)
    model_training: Mapped[bool] = mapped_column(Boolean, default=False)
    journal_memory: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")
    sleep_history: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")

    user: Mapped["User"] = relationship(back_populates="consent")


def consent_allows(user, flag: str) -> bool:
    """True unless the user explicitly switched this category off. A missing
    consent row means default-allowed — same rule as the /chat gate."""
    consent = getattr(user, "consent", None)
    return consent is None or bool(getattr(consent, flag))
