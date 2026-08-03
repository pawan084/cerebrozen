from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base, utcnow


class DeviceToken(Base):
    """One push token for one install of the native app.

    ``User.push_token`` (a single column) predates this and only ever held one
    iOS token, so a user with a phone and a tablet lost whichever registered
    first, and Android had nowhere to put an FCM token at all. This table is
    per-device: the token itself is globally unique (both APNs and FCM mint
    per-install tokens), so a re-register from a different account adopts the
    row rather than duplicating it — a shared handset only notifies whoever
    signed in last, which mirrors the Web Push rule.

    ``last_seen_at`` is refreshed on every register call so a dispatcher can
    age out installs that stopped checking in; ``failed_at`` is stamped when the
    provider tells us the token is dead (APNs 410 / FCM UNREGISTERED) so the
    next pass skips it instead of retrying forever.
    """

    __tablename__ = "device_tokens"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    token: Mapped[str] = mapped_column(String(512), unique=True, index=True)
    # ios | android — decides which provider the dispatcher hands it to.
    platform: Mapped[str] = mapped_column(String(16), index=True)
    # Free-form build marker (e.g. "0.1.0 (26)") for support triage.
    app_version: Mapped[str] = mapped_column(String(40), default="")
    last_seen_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow
    )
    failed_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
