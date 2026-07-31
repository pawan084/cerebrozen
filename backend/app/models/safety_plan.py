from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class SafetyPlan(Base):
    """A personal safety plan, in the user's own words.

    Structure follows the Stanley-Brown Safety Planning Intervention: warning
    signs → things I can do alone → people and places that distract me →
    people I can ask for help → professionals and agencies → making my
    environment safer. Sections are plain text the user writes; the app never
    interprets, scores or diagnoses them.

    **Authored by the user, never by the model.** The AI may offer a suggestion
    into a field, but nothing is stored without an explicit confirm — the same
    rule the Oracle's write tools follow. A means-restriction section composed
    by a language model is exactly what the safety posture here exists to
    prevent.

    Versioned rather than overwritten: a plan is a record of what someone
    decided when they were well, and losing an earlier version to an edit made
    in distress would defeat the point. Superseding sets ``archived_at``; the
    live plan is the row where it is NULL.
    """

    __tablename__ = "safety_plans"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    version: Mapped[int] = mapped_column(Integer, default=1)

    # The six sections. All optional — a half-written plan is still worth
    # having, and nothing here may ever gate a crisis response.
    warning_signs: Mapped[str] = mapped_column(Text, default="")
    internal_coping: Mapped[str] = mapped_column(Text, default="")
    social_distractors: Mapped[str] = mapped_column(Text, default="")
    social_support: Mapped[str] = mapped_column(Text, default="")
    professionals: Mapped[str] = mapped_column(Text, default="")
    means_safety: Mapped[str] = mapped_column(Text, default="")
    notes: Mapped[str] = mapped_column(Text, default="")

    archived_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True, index=True
    )

    # Ordered as the user meets them in the guided flow.
    SECTIONS = (
        "warning_signs",
        "internal_coping",
        "social_distractors",
        "social_support",
        "professionals",
        "means_safety",
    )

    @property
    def is_empty(self) -> bool:
        return not any(getattr(self, f).strip() for f in self.SECTIONS)
