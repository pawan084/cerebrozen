"""Media storage: narration MP3s and admin-uploaded catalogue assets, written
under MEDIA_ROOT and served read-only by the /media StaticFiles mount in app.main.

Filenames are deterministic (per content item, per asset key), so re-uploading
overwrites in place (clients revalidate via the mount's ETag/Last-Modified) and
deletion is a simple unlink.
"""
from __future__ import annotations

import re
import uuid
from pathlib import Path

from app.core.config import settings

_NARRATION_DIR = "narration"
_ASSET_DIR = "assets"

# Catalogue keys are dotted slugs ("ambience.rain", "game.pad.0"). They become
# filenames verbatim, so the charset is the path-traversal guard: no slashes, no
# "..", nothing but lowercase alphanumerics, dots, dashes and underscores.
_KEY_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{0,119}$")

# Upload formats we accept, and the MIME we serve them as. Anything else is
# rejected — an unplayable asset in the catalogue is worse than an empty one,
# because the empty state has a working client fallback and a bad file does not.
ASSET_MIME_BY_EXT = {
    ".mp3": "audio/mpeg",
    ".m4a": "audio/mp4",
    ".ogg": "audio/ogg",
    ".wav": "audio/wav",
    ".mp4": "video/mp4",
    ".webm": "video/webm",
}


def valid_key(key: str) -> bool:
    """A key safe to use as a filename (and therefore safe in a URL path)."""
    return bool(_KEY_RE.match(key)) and ".." not in key


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


# ── Catalogue assets (admin uploads) ────────────────────────────────────
def asset_rel_url(key: str, ext: str) -> str:
    return f"/media/{_ASSET_DIR}/{key}{ext}"


def save_asset(key: str, ext: str, data: bytes) -> str:
    """Persist an uploaded catalogue asset; returns the relative URL to store.

    Re-uploading the same key with a *different* extension leaves the old file
    behind, so drop it first — otherwise MEDIA_ROOT accretes orphans that nothing
    references and nothing cleans up.
    """
    delete_asset(key)
    path = Path(settings.media_root) / _ASSET_DIR / f"{key}{ext}"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return asset_rel_url(key, ext)


def delete_asset(key: str) -> None:
    """Best-effort cleanup: remove whichever extension this key was stored under."""
    base = Path(settings.media_root) / _ASSET_DIR
    for ext in ASSET_MIME_BY_EXT:
        (base / f"{key}{ext}").unlink(missing_ok=True)
