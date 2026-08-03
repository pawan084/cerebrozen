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


# ── MP3 duration probe (stdlib only) ────────────────────────────────────
# Every client displays `duration_min`. For a narrated item the audio IS the
# content, so a hand-authored duration that disagrees with the generated file
# is a small lie in a product that sells honesty — and it was the shipped
# behaviour until now (narrate minted the MP3 and never touched the number).
#
# Deliberately dependency-free. The obvious library (mutagen) is GPL-2.0 and
# is not worth linking into a commercial backend for one integer; tinytag is
# MIT but is still a dependency for ~60 lines of header parsing. Everything
# below reads MPEG audio frame headers, which are a fixed, public format.

# Layer III bitrates in kbps, indexed by the header's 4-bit bitrate field.
_BITRATES_MPEG1 = (0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
_BITRATES_MPEG2 = (0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
# Sample rates by the header's 2-bit version field.
_SAMPLE_RATES = {
    3: (44100, 48000, 32000),   # MPEG 1
    2: (22050, 24000, 16000),   # MPEG 2
    0: (11025, 12000, 8000),    # MPEG 2.5
}
# How far into the file we will hunt for the first frame before giving up.
_SYNC_SCAN_BYTES = 64 * 1024


def _id3_offset(data: bytes) -> int:
    """Bytes to skip past an ID3v2 tag (0 when there isn't one)."""
    if len(data) < 10 or data[:3] != b"ID3":
        return 0
    size = 0
    for byte in data[6:10]:
        if byte & 0x80:          # not a syncsafe integer — don't trust the tag
            return 0
        size = (size << 7) | byte
    return 10 + size


def _parse_frame(data: bytes, i: int) -> tuple[int, int, int, int, bool] | None:
    """Decode a frame header at `i` → (version, sample_rate, bitrate_kbps,
    frame_len, is_mono), or None when the bytes aren't a valid Layer III frame.

    Caller invariant: `i + 4 <= len(data)`. The only caller bounds its scan at
    `len(data) - 4`, so a length guard here would be unreachable code.
    """
    b1, b2, b3 = data[i + 1], data[i + 2], data[i + 3]
    version = (b1 >> 3) & 0b11          # 3=MPEG1, 2=MPEG2, 0=MPEG2.5, 1=reserved
    layer = (b1 >> 1) & 0b11            # 1 = Layer III
    if version == 1 or layer != 1:
        return None
    bitrate_idx, rate_idx = (b2 >> 4) & 0b1111, (b2 >> 2) & 0b11
    if bitrate_idx in (0, 15) or rate_idx == 3:
        return None                      # "free" / "bad" — not a real frame
    table = _BITRATES_MPEG1 if version == 3 else _BITRATES_MPEG2
    bitrate = table[bitrate_idx]
    sample_rate = _SAMPLE_RATES[version][rate_idx]
    padding = (b2 >> 1) & 1
    coef = 144 if version == 3 else 72   # Layer III bytes-per-frame coefficient
    # Always ≥ 24 bytes: the smallest legal combination is MPEG2 at 8 kbps and
    # 24 kHz, so there is no degenerate frame length to defend against here.
    frame_len = (coef * bitrate * 1000) // sample_rate + padding
    return version, sample_rate, bitrate, frame_len, ((b3 >> 6) & 0b11) == 3


def _xing_frame_count(data: bytes, i: int, version: int, is_mono: bool) -> int | None:
    """Total frame count from a Xing/Info VBR header, when the file has one.

    VBR files cannot be timed from the bitrate of a single frame, so this is the
    only correct source for them — and it is also exact (not an estimate) for
    CBR files that happen to carry the tag.
    """
    side_info = (17 if is_mono else 32) if version == 3 else (9 if is_mono else 17)
    at = i + 4 + side_info
    if data[at:at + 4] not in (b"Xing", b"Info"):
        return None
    flags = int.from_bytes(data[at + 4:at + 8], "big")
    if not flags & 0x01:                 # no frame-count field present
        return None
    return int.from_bytes(data[at + 8:at + 12], "big") or None


def mp3_duration_seconds(data: bytes) -> float | None:
    """Playing time of MP3 `data`, or None if it can't be read confidently.

    None is a normal outcome, not an error: callers keep whatever duration they
    already had. Guessing would be worse than leaving an admin-authored number
    in place.
    """
    if not data:
        return None
    start = _id3_offset(data)
    # `end` can land BELOW `start` — a malformed tag may declare a size past the
    # end of the file (b"ID3-fake…" parses as a ~200 MB tag). The range is then
    # empty and we return None, which is the right answer for a file that isn't
    # one. Getting this wrong indexed past the buffer and 500'd the endpoint.
    end = min(len(data) - 4, start + _SYNC_SCAN_BYTES)
    for i in range(start, end + 1):
        if data[i] != 0xFF or (data[i + 1] & 0xE0) != 0xE0:
            continue
        frame = _parse_frame(data, i)
        if frame is None:
            continue
        version, sample_rate, bitrate, frame_len, is_mono = frame
        samples = 1152 if version == 3 else 576
        count = _xing_frame_count(data, i, version, is_mono)
        if count:
            return count * samples / sample_rate
        # CBR: the audio payload divided by its constant bitrate. Assumes the
        # stream keeps this frame's bitrate, which is what "constant" means;
        # a VBR file without a Xing header is unreadable either way.
        return (len(data) - i) * 8 / (bitrate * 1000)
    return None


def duration_minutes(seconds: float) -> int:
    """Seconds → the whole minutes clients display. Half-up (not Python's
    banker's rounding, which would show a 150 s track as "2 min" but a 210 s
    one as "4 min"), and never zero — a track that exists lasts at least
    "1 min" on a label.
    """
    return max(1, int(seconds / 60 + 0.5))


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
