from __future__ import annotations

from sqlalchemy import String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class ProductEvent(Base):
    """An anonymous, allowlisted product-analytics event (first-party only).

    Deliberately NEVER linked to a user: rows carry only a client-generated
    random install id so funnels can count unique devices per step — no
    account id, no IP, no device fingerprint, no free-form payload (a single
    enumerable ``step`` string instead of a props blob keeps PII out by
    construction). Aggregates leave via /admin/metrics; raw rows never do.
    """

    __tablename__ = "product_events"

    anon_id: Mapped[str] = mapped_column(String(64), index=True)
    name: Mapped[str] = mapped_column(String(60), index=True)
    # Which client sent it: "ios" | "web" | "app".
    source: Mapped[str] = mapped_column(String(10), default="ios")
    # Optional enumerable qualifier (onboarding step name, paywall product id).
    step: Mapped[str] = mapped_column(String(60), default="")
