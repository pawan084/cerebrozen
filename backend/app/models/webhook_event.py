from __future__ import annotations

from sqlalchemy import String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class ProcessedWebhook(Base):
    """One provider event id we have already acted on.

    Payment webhooks are at-least-once. Stripe retries on any non-2xx, on
    timeouts, and during its own incident recovery — so the same event can and
    does arrive twice. Without this table a replayed
    `customer.subscription.deleted` lands *after* a user has re-subscribed and
    silently downgrades someone who is paying. That is the failure this exists
    to prevent; it is not hypothetical, it is the normal behaviour of the
    protocol.

    Provider-scoped rather than Stripe-only because the App Store's
    `notificationUUID` has exactly the same property, and a second table later
    would be the same code twice.
    """

    __tablename__ = "processed_webhooks"
    __table_args__ = (
        UniqueConstraint("provider", "event_id", name="uq_webhook_provider_event"),
    )

    provider: Mapped[str] = mapped_column(String(24), index=True)
    event_id: Mapped[str] = mapped_column(String(191), index=True)
