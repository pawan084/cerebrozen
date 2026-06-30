from __future__ import annotations

import uuid

from sqlalchemy import ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class MoodLog(Base):
    __tablename__ = "mood_logs"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    mood: Mapped[str] = mapped_column(String(60))
    note: Mapped[str] = mapped_column(String(255), default="")
    symbol: Mapped[str] = mapped_column(String(60), default="sparkles")
    # 1–5 felt intensity; optional trigger free-text.
    intensity: Mapped[int] = mapped_column(Integer, default=3)
    trigger: Mapped[str | None] = mapped_column(String(255), nullable=True)
