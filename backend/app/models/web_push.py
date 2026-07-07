from __future__ import annotations

import uuid

from sqlalchemy import ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class WebPushSubscription(Base):
    """A browser's Web Push subscription (apps/app).

    One row per browser endpoint; a user may hold several (laptop + phone
    browser). The endpoint URL is globally unique per the Web Push spec, so a
    re-registration from a different account adopts the row — a shared browser
    only ever notifies whoever subscribed last.
    """

    __tablename__ = "web_push_subscriptions"

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    endpoint: Mapped[str] = mapped_column(String(1024), unique=True, index=True)
    # Client keys from PushSubscription.getKey() — used to encrypt payloads
    # end-to-end (RFC 8291); the push service never sees plaintext.
    p256dh: Mapped[str] = mapped_column(String(255))
    auth: Mapped[str] = mapped_column(String(255))
