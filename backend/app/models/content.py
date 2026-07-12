from __future__ import annotations

from datetime import datetime

from sqlalchemy import Boolean, DateTime, Integer, String, Text
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class ContentItem(Base):
    """Admin-managed catalogue: sleep stories, meditations, breathwork, etc."""

    __tablename__ = "content_items"

    title: Mapped[str] = mapped_column(String(160), index=True)
    subtitle: Mapped[str] = mapped_column(String(255), default="")
    # sleep | meditation | breath | soundscape | program | wind_down
    kind: Mapped[str] = mapped_column(String(40), index=True)
    symbol: Mapped[str] = mapped_column(String(60), default="sparkles")
    image_url: Mapped[str] = mapped_column(String(1024), default="")
    duration_min: Mapped[int] = mapped_column(Integer, default=0)
    premium: Mapped[bool] = mapped_column(Boolean, default=False)
    published: Mapped[bool] = mapped_column(Boolean, default=True, index=True)
    # Narration pipeline: an admin-authored script read aloud by the TTS voice.
    # audio_url is either the backend-minted relative "/media/narration/{id}.mp3"
    # or an admin-pasted absolute URL; empty = clients fall back to ambient audio.
    narration_script: Mapped[str] = mapped_column(Text, default="")
    audio_url: Mapped[str] = mapped_column(String(1024), default="")
    audio_generated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    # Per-day program structure (W15): an ordered list of {"title", "body"}
    # guides, one per program day — day N of the enrollment reads guide N
    # (clamped to the last). NULL for non-programs and legacy rows; clients
    # that predate the field ignore it (additive contract). none_as_null keeps
    # Python None ↔ SQL NULL (never JSON 'null'), so the seed backfill's
    # IS NULL check sees cleared rows.
    day_guides: Mapped[list[dict] | None] = mapped_column(
        JSONB(none_as_null=True), nullable=True
    )
