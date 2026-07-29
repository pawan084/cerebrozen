"""Media catalogue schemas — the sounds and videos clients resolve by key."""
import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

# Kinds a client knows how to play. `scene` is video; the rest are audio.
MEDIA_KINDS = ("ambience", "breathe", "game", "chime", "scene")


class MediaAssetBase(BaseModel):
    key: str = Field(max_length=120, min_length=1)
    kind: str = Field(max_length=40)
    title: str = Field(default="", max_length=160)
    # Relative "/media/assets/..." or an absolute URL; empty = client fallback.
    url: str = Field(default="", max_length=1024)
    mime: str = Field(default="", max_length=80)
    duration_ms: int = Field(default=0, ge=0)
    loop: bool = False
    published: bool = True


class MediaAssetCreate(MediaAssetBase):
    pass


class MediaAssetUpdate(BaseModel):
    kind: str | None = None
    title: str | None = None
    url: str | None = None
    mime: str | None = None
    duration_ms: int | None = None
    loop: bool | None = None
    published: bool | None = None


class MediaAssetOut(MediaAssetBase):
    model_config = ConfigDict(from_attributes=True)
    id: uuid.UUID
    created_at: datetime
