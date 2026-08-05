from __future__ import annotations

import uuid

from sqlalchemy import ForeignKey, String, Text, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class Insight(Base):
    """A computed weekly insight snapshot for a user."""

    __tablename__ = "insights"
    # Register C53: "idempotent per ISO week" was a SELECT-then-INSERT with no
    # constraint — two dispatcher workers ticking together wrote two rows for
    # the same week. The database now states the invariant the code assumed.
    __table_args__ = (UniqueConstraint("user_id", "period", name="uq_insights_user_period"),)

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    period: Mapped[str] = mapped_column(String(20), default="weekly")
    headline: Mapped[str] = mapped_column(String(160), default="")
    summary: Mapped[str] = mapped_column(Text, default="")
    # [{label, value, progress}]
    metrics: Mapped[list[dict]] = mapped_column(JSONB, default=list)
