from __future__ import annotations

import uuid

from sqlalchemy import ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class IdempotencyRecord(Base):
    """The stored result of one client-keyed write, so a replay is free.

    The Android offline queue keeps writes on disk while there is no network
    and drains them when there is. Any queue like that can send the same write
    twice — the request succeeded but the process died before the row was
    marked done, or the user force-quit mid-flush. Without a key, the second
    send silently creates a duplicate check-in.

    So a write may carry an ``Idempotency-Key``. The first one runs and its
    response body is stored here; an identical replay returns that same body
    without touching the database again. A replay of the *same key* with a
    *different payload* is a client bug, not a retry, and gets a 409 rather
    than quietly overwriting anything.

    Rows are scoped per user (two devices never share a key space) and purged
    after a week — long past any queue's useful retry window.
    """

    __tablename__ = "idempotency_records"
    __table_args__ = (
        UniqueConstraint("user_id", "key", name="uq_idempotency_user_key"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    key: Mapped[str] = mapped_column(String(120), index=True)
    # "POST /moods" — the same key against a different route is a different write.
    endpoint: Mapped[str] = mapped_column(String(120))
    # sha256 of the canonical request body; guards against key reuse.
    request_hash: Mapped[str] = mapped_column(String(64))
    status_code: Mapped[int] = mapped_column(Integer, default=200)
    response_body: Mapped[dict] = mapped_column(JSONB, default=dict)
