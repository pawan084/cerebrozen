from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, Float, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base

# What produced a row. The distinction is the whole privacy argument, so it is
# enforced here rather than left to callers:
#
#   manual            — the user typed it. Theirs; editable; kept until deleted.
#   confirmed         — the AI proposed it and the user explicitly approved
#                       (same confirm-before-write path the Oracle tools use).
#   onboarding        — captured during the funnel (e.g. a stated goal).
#   suppressed_pattern— NOT a memory. A tombstone saying "stop showing me this
#                       computed pattern"; `body` holds the statement it hides.
#
# Deliberately absent: any source meaning "the AI inferred this and stored it".
# Mined patterns stay computed in `services/insights.compute_patterns` so the
# product never asserts a guess back to the user as a remembered fact.
SOURCES = ("manual", "confirmed", "onboarding", "suppressed_pattern")

# Only these are user prose, so only these can be edited. A suppression
# tombstone is delete-only ("un-hide"), never rewritten.
EDITABLE_SOURCES = ("manual", "confirmed", "onboarding")


class ContextMemory(Base):
    """One thing CereBro remembers about a user — addressable, so it can be
    edited or deleted individually.

    Before this table the Pattern Dashboard could only offer an all-or-nothing
    wipe: patterns were computed on the fly and had no row to point at.
    """

    __tablename__ = "context_memories"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    body: Mapped[str] = mapped_column(Text)
    # 0–1 nudge on how strongly this should weigh in recall. Not surfaced as a
    # number to the user; ordering only.
    salience: Mapped[float] = mapped_column(Float, default=0.5)
    source: Mapped[str] = mapped_column(String(24), default="manual", index=True)
    # NULL = keep until the user deletes it (the norm for what they wrote).
    # Set on machine-originated rows so they decay instead of accruing forever.
    expires_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True, index=True
    )
    # Soft-hide. Set on every suppression tombstone; may also be set on a
    # memory the user hid without deleting.
    dismissed_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    updated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
