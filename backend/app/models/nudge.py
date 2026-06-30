from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, Text
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

    scheduled_for: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    sent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    # scheduled | sent | cancelled
    status: Mapped[str] = mapped_column(String(20), default="scheduled", index=True)
