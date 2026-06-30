from __future__ import annotations

import uuid

from sqlalchemy import ForeignKey, String, Text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class Insight(Base):
    """A computed weekly insight snapshot for a user."""

    __tablename__ = "insights"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    period: Mapped[str] = mapped_column(String(20), default="weekly")
    headline: Mapped[str] = mapped_column(String(160), default="")
    summary: Mapped[str] = mapped_column(Text, default="")
    # [{label, value, progress}]
    metrics: Mapped[list[dict]] = mapped_column(JSONB, default=list)
