from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class Nudge(Base):
    """A scheduled proactive push (reminder / context-aware nudge)."""

    __tablename__ = "nudges"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    # reminder | checkin | reset | insight | safety
    kind: Mapped[str] = mapped_column(String(40), index=True)
    title: Mapped[str] = mapped_column(String(160))
    body: Mapped[str] = mapped_column(Text, default="")
    deeplink: Mapped[str | None] = mapped_column(String(255), nullable=True)

    #: When this was MEANT to arrive. Never moved by a retry — lateness is
    #: measured against it, and a wind-down nudge that means "ease into the
    #: evening" is wrong at 11am however many times we tried to send it.
    scheduled_for: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    sent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    # scheduled | sent | cancelled | skipped | failed | expired
    status: Mapped[str] = mapped_column(String(20), default="scheduled", index=True)
    #: How many delivery attempts this has had. Bounded — see MAX_ATTEMPTS.
    attempts: Mapped[int] = mapped_column(Integer, default=0, server_default="0")
    #: When to try next, after a transient failure. Null while it has not
    #: failed: the ordinary path never touches it.
    next_attempt_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), index=True, nullable=True
    )
