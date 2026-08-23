from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, String, Text
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
    # Who closed this flag, when, and why. A crisis flag closing with no
    # attribution is not an audit trail — the reviewer is required to say
    # something, so a resolved row can always answer "who decided, and why".
    resolved_by: Mapped[uuid.UUID | None] = mapped_column(
        ForeignKey("users.id", ondelete="SET NULL"), nullable=True, index=True
    )
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    resolution_note: Mapped[str] = mapped_column(String(500), default="", server_default="")
    # Set when a crisis event triggered a trusted-contact notification.
    escalated: Mapped[bool] = mapped_column(Boolean, default=False, server_default="false")
    escalated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    #: What actually happened when this was escalated, as short tokens
    #: ("ops_alerted,contact_notify_failed"). `escalated` alone cannot say
    #: it: the senders swallow their own failures by design, so a contact
    #: who was never reached used to be recorded exactly like one who was.
    escalation_note: Mapped[str] = mapped_column(String(120), default="", server_default="")

    @property
    def excerpt_chars(self) -> int:
        """How much text is behind this flag, without disclosing any of it.

        Lets the review queue show "217 characters, hidden" so a reviewer knows
        there is something to read before choosing to read it.
        """
        return len(self.excerpt or "")
