from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class ProgramEnrollment(Base):
    """A user's active multi-day program (ref "PROGRAM · DAY 3 OF 7" card).

    One active enrollment at a time (enrolling deactivates the previous one).
    The current day is COMPUTED from ``started_at`` — showing up daily is the
    program; there is nothing to "advance" and nothing to fail.
    """

    __tablename__ = "program_enrollments"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    content_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("content_items.id", ondelete="CASCADE")
    )
    # Denormalised so the Home card renders even if the catalogue text shifts.
    title: Mapped[str] = mapped_column(String(160))
    days: Mapped[int] = mapped_column(Integer, default=7)
    started_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
    active: Mapped[bool] = mapped_column(Boolean, default=True)
