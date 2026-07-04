from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class LoginCode(Base):
    """A pending email one-time sign-in code (passwordless auth).

    Keyed by email — not user — because verifying the code is what creates the
    account for a new address. One live code per email (a re-request replaces
    it); the hash keeps the DB from being a codebook if it leaks.
    """

    __tablename__ = "login_codes"

    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    code_hash: Mapped[str] = mapped_column(String(64))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    attempts: Mapped[int] = mapped_column(Integer, default=0, server_default="0")
