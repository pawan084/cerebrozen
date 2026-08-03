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

    # EVERY category defaults to False — private by default.
    #
    # A row is created at signup (all four auth paths), but the client does not
    # PATCH the user's actual choices until the END of onboarding. Four of these
    # used to default True, so between signup and finish — and permanently for
    # anyone who abandoned onboarding after creating an account — the server held
    # mood_history / ai_memory / journal_memory / sleep_history as GRANTED by
    # someone who had not been asked yet. Insights, the daily plan and the
    # interventions engine all read those flags, so the grant had teeth.
    #
    # All three clients already ship these switches off and send an explicit
    # six-key PATCH, so a real opt-in still arrives as `true`. Consent is an act,
    # not a starting position (DPDP "specific and informed").
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), unique=True)
    mood_history: Mapped[bool] = mapped_column(Boolean, default=False)
    ai_memory: Mapped[bool] = mapped_column(Boolean, default=False)
    voice_storage: Mapped[bool] = mapped_column(Boolean, default=False)
    model_training: Mapped[bool] = mapped_column(Boolean, default=False)
    journal_memory: Mapped[bool] = mapped_column(Boolean, default=False, server_default="false")
    sleep_history: Mapped[bool] = mapped_column(Boolean, default=False, server_default="false")

    user: Mapped["User"] = relationship(back_populates="consent")


def consent_allows(user, flag: str) -> bool:
    """Whether this category may be read. False unless the user switched it on.

    A missing consent row reads as NOT allowed: absence of a recorded decision is
    not a decision. It used to mean default-allowed, which put the one case where
    we know least about what the user wanted on the permissive side.
    """
    consent = getattr(user, "consent", None)
    return consent is not None and bool(getattr(consent, flag))
