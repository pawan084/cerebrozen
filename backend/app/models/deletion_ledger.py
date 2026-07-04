from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class DeletionLedger(Base):
    """DPDP Rule 8(3) sealed record that an account existed and was deleted.

    Content hard-cascades on DELETE /users/me; this row is the minimal
    surviving trace: a sha256 of the email (matchable against a support or
    state request without storing the address), when the account was created,
    and when it was deleted (`created_at` from Base). No name, no content, no
    ids that join to anything else. Ops: purge rows older than 12 months.
    """

    __tablename__ = "deletion_ledger"

    email_hash: Mapped[str] = mapped_column(String(64), index=True)
    account_created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    reason: Mapped[str] = mapped_column(String(30), default="user_request")
