from __future__ import annotations

from typing import TYPE_CHECKING

from sqlalchemy import Boolean, String
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base

if TYPE_CHECKING:
    from app.models.consent import Consent


class User(Base):
    __tablename__ = "users"

    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    hashed_password: Mapped[str] = mapped_column(String(255))
    name: Mapped[str] = mapped_column(String(120), default="")

    # Onboarding choices (mirror the iOS app).
    language: Mapped[str] = mapped_column(String(120), default="English")
    companion: Mapped[str] = mapped_column(String(60), default="Calm Guide")
    goals: Mapped[list[str]] = mapped_column(JSONB, default=list)
    # Self-reflection assessment: psychological drivers (category-level).
    motivations: Mapped[list[str]] = mapped_column(JSONB, default=list, server_default="[]")
    timezone: Mapped[str] = mapped_column(String(60), default="Asia/Kolkata")
    # Effective crisis region (ISO code, e.g. "US"); "" = automatic/unknown.
    # Drives locale-correct hotlines in AI crisis replies (see services/crisis).
    region: Mapped[str] = mapped_column(String(8), default="", server_default="")

    # Proactive: device push token (APNs/FCM).
    push_token: Mapped[str | None] = mapped_column(String(512), nullable=True)

    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    is_admin: Mapped[bool] = mapped_column(Boolean, default=False)

    consent: Mapped["Consent"] = relationship(
        back_populates="user", uselist=False, cascade="all, delete-orphan", lazy="selectin"
    )
