from __future__ import annotations

from sqlalchemy import Boolean, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class ContentItem(Base):
    """Admin-managed catalogue: sleep stories, meditations, breathwork, etc."""

    __tablename__ = "content_items"

    title: Mapped[str] = mapped_column(String(160), index=True)
    subtitle: Mapped[str] = mapped_column(String(255), default="")
    # sleep | meditation | breath | soundscape | program
    kind: Mapped[str] = mapped_column(String(40), index=True)
    symbol: Mapped[str] = mapped_column(String(60), default="sparkles")
    image_url: Mapped[str] = mapped_column(String(1024), default="")
    duration_min: Mapped[int] = mapped_column(Integer, default=0)
    premium: Mapped[bool] = mapped_column(Boolean, default=False)
    published: Mapped[bool] = mapped_column(Boolean, default=True, index=True)
