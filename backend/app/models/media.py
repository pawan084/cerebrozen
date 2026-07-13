from __future__ import annotations

from sqlalchemy import Boolean, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base


class MediaAsset(Base):
    """The media catalogue: every sound and video a client can play, addressed by
    a stable dotted key ("ambience.rain", "game.pad.0", "scene.night_lake").

    The key is the cross-stack contract; the URL is swappable. An empty `url` is a
    first-class state meaning "no server asset yet" — clients fall back to their
    bundled loop or synthesized tone (exactly how `content_items.audio_url` already
    behaves). That is what lets the catalogue ship before the assets exist, and lets
    an admin hot-swap any sound later without an app release.
    """

    __tablename__ = "media_assets"

    key: Mapped[str] = mapped_column(String(120), unique=True, index=True)
    # ambience | breathe | game | chime | scene   (scene = looping video)
    kind: Mapped[str] = mapped_column(String(40), index=True)
    title: Mapped[str] = mapped_column(String(160), default="")
    # Relative "/media/assets/{key}.{ext}" (backend-held) or an absolute CDN URL.
    # Empty = the client uses its bundled/synthesized fallback.
    url: Mapped[str] = mapped_column(String(1024), default="")
    mime: Mapped[str] = mapped_column(String(80), default="")
    duration_ms: Mapped[int] = mapped_column(Integer, default=0)
    # Whether the client should loop this asset (beds and scenes) or fire it once
    # (taps, chimes, breath cues).
    loop: Mapped[bool] = mapped_column(Boolean, default=False)
    published: Mapped[bool] = mapped_column(Boolean, default=True, index=True)
