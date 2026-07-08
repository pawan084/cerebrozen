"""Generated-media storage: narration MP3s written under MEDIA_ROOT and served
read-only by the /media StaticFiles mount in app.main.

Filenames are deterministic per content item, so regeneration overwrites in
place (clients revalidate via the mount's ETag/Last-Modified) and deletion is
a simple unlink.
"""
from __future__ import annotations

import uuid
from pathlib import Path

from app.core.config import settings

_NARRATION_DIR = "narration"


def narration_rel_url(item_id: uuid.UUID) -> str:
    """The public, API-relative URL clients resolve against their API base."""
    return f"/media/{_NARRATION_DIR}/{item_id}.mp3"


def _narration_path(item_id: uuid.UUID) -> Path:
    return Path(settings.media_root) / _NARRATION_DIR / f"{item_id}.mp3"


def save_narration(item_id: uuid.UUID, data: bytes) -> str:
    """Persist generated narration audio; returns the relative URL to store."""
    path = _narration_path(item_id)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return narration_rel_url(item_id)


def delete_narration(item_id: uuid.UUID) -> None:
    """Best-effort cleanup when a narrated item is deleted."""
    _narration_path(item_id).unlink(missing_ok=True)
