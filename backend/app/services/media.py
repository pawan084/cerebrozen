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


# ── Playback authorization ───────────────────────────────────────────────
# The files sit behind a StaticFiles mount so native players get Range and
# ETag handling for free. That mount cannot ask "is this person allowed?", so
# authorization rides in the URL as a signed, expiring, single-item grant:
# minted here (where the entitlement is known) and checked in app.main's
# media guard (where the file is about to be served).

_ENTITLED_TIERS = {"premium", "premium_human"}


def is_entitled(user, item) -> bool:
    """Whether this person may play this item's narration.

    Free items are open to everyone, signed in or not. Premium narration needs
    a paid tier — the same tier set that unlocks unlimited chat.
    """
    if not item.premium:
        return True
    return user is not None and user.subscription_tier in _ENTITLED_TIERS


def playback_url(item, user) -> str:
    """The ``audio_url`` to hand a client for this item, or "" if it gets none.

    Returning "" for un-entitled premium narration is deliberate and matches how
    every client already behaves when an item has no audio: it falls back to the
    bundled ambient bed rather than erroring. A paywall is a product decision for
    the screen to make; this function's job is only to not hand out the file.
    """
    stored = (item.audio_url or "").strip()
    if not stored:
        return ""
    # Absolute URLs point at someone else's CDN — not ours to sign or gate.
    if stored.startswith("http://") or stored.startswith("https://"):
        return stored
    if not is_entitled(user, item):
        return ""
    from app.core.security import create_media_token  # local: avoids an import cycle

    sep = "&" if "?" in stored else "?"
    return f"{stored}{sep}t={create_media_token(str(item.id))}"


def token_authorizes(token: str | None, request_path: str) -> bool:
    """Whether ``token`` grants the narration file named in ``request_path``.

    Compares the token's subject against the id in the path, so a grant for one
    track cannot be replayed against another.
    """
    if not token:
        return False
    from app.core.security import MEDIA, decode_token  # local: avoids an import cycle

    payload = decode_token(token, expected_type=MEDIA)
    if not payload:
        return False
    subject = payload.get("sub")
    if not subject:
        return False
    return Path(request_path).stem == subject
