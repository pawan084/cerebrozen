from __future__ import annotations

import uuid
from typing import TYPE_CHECKING

from sqlalchemy import Boolean, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base

if TYPE_CHECKING:
    from app.models.user import User


class Consent(Base):
    __tablename__ = "consents"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), unique=True)
    mood_history: Mapped[bool] = mapped_column(Boolean, default=True)
    ai_memory: Mapped[bool] = mapped_column(Boolean, default=True)
    voice_storage: Mapped[bool] = mapped_column(Boolean, default=False)
    model_training: Mapped[bool] = mapped_column(Boolean, default=False)

    user: Mapped["User"] = relationship(back_populates="consent")
