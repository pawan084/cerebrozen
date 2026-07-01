from __future__ import annotations

import uuid

from sqlalchemy import Boolean, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class TrustedContact(Base):
    """A person the user consents to notify if a crisis is detected.

    One per user (upserted). ``notify_consent`` must be true for any automatic
    escalation to fire — consent is a hard gate.
    """

    __tablename__ = "trusted_contacts"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), unique=True, index=True
    )
    name: Mapped[str] = mapped_column(String(120), default="")
    # "email" | "sms" | "phone"
    method: Mapped[str] = mapped_column(String(20), default="email")
    value: Mapped[str] = mapped_column(String(255), default="")
    relationship: Mapped[str] = mapped_column(String(60), default="")
    notify_consent: Mapped[bool] = mapped_column(Boolean, default=False)
