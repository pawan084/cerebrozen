from __future__ import annotations

import uuid

from sqlalchemy import Boolean, ForeignKey, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class SafetyEvent(Base):
    """A flagged signal from journal/chat for the admin review queue."""

    __tablename__ = "safety_events"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    # journal | chat
    source: Mapped[str] = mapped_column(String(20))
    source_id: Mapped[uuid.UUID | None] = mapped_column(nullable=True)
    # low | elevated | crisis
    risk_level: Mapped[str] = mapped_column(String(20), index=True)
    reason: Mapped[str] = mapped_column(String(255), default="")
    excerpt: Mapped[str] = mapped_column(Text, default="")
    resolved: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
