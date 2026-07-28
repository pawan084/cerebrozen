"""Narrated-audio pipeline: the admin narrate endpoint, the /media mount, and
payload hygiene (the public catalogue exposes audio_url but never the script).

ElevenLabs is stubbed per the voice-test pattern — hermetic, no credits.
"""
import uuid
from pathlib import Path

from app.core.config import settings
from app.services import media as media_service
from app.services import voice as voice_service

# ── MP3 fixtures ────────────────────────────────────────────────────────
# Real MPEG audio frame headers, built by hand so the duration probe can be
# tested without shipping a binary fixture or burning TTS credits.
# 0xFF 0xFB = sync + MPEG1 + Layer III + no CRC; 0x90 = 128 kbps @ 44.1 kHz,
# no padding; 0x00 = stereo. Layer III frame length = 144*128000//44100 = 417.
_HEADER = bytes([0xFF, 0xFB, 0x90, 0x00])
_FRAME_LEN = 417
_SAMPLES_PER_FRAME = 1152
_SAMPLE_RATE = 44100


def _cbr_mp3(frames: int, *, id3: bool = False) -> bytes:
    """A constant-bitrate MP3 with no VBR header — timed from its byte length."""
    body = (_HEADER + b"\x00" * (_FRAME_LEN - 4)) * frames
    if not id3:
        return body
    # ID3v2 header with a syncsafe size of 64 bytes of padding.
    return b"ID3\x04\x00\x00" + bytes([0, 0, 0, 64]) + b"\x00" * 64 + body


def _xing_mp3(frames: int) -> bytes:
    """A VBR MP3 whose Xing header declares `frames` — the only correct way to
    time a VBR file, and exact rather than estimated."""
    tag = b"Xing" + (1).to_bytes(4, "big") + frames.to_bytes(4, "big")
    first = _HEADER + b"\x00" * 32 + tag           # 32 = MPEG1 stereo side info
    return first + b"\x00" * (_FRAME_LEN - len(first))

_ITEM = {
    "title": "Narration test story",
    "subtitle": "calm",
    "kind": "sleep",
    "symbol": "moon.stars",
    "duration_min": 8,
    "premium": False,
    "published": True,
    "narration_script": "Settle in. Let the shoulders soften as the night grows quiet.",
}


async def _create_item(admin_client, **overrides):
    payload = {**_ITEM, **overrides}
    r = await admin_client.post("/admin/content", json=payload)
    assert r.status_code == 201
    return r.json()


async def test_narrate_requires_admin(auth_client):
    r = await auth_client.post(f"/admin/content/{uuid.uuid4()}/narrate")
    assert r.status_code == 403


async def test_narrate_keyless_returns_503(admin_client, monkeypatch):
    item = await _create_item(admin_client)
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: False))
    r = await admin_client.post(f"/admin/content/{item['id']}/narrate")
    assert r.status_code == 503


async def test_narrate_unknown_item_404(admin_client, monkeypatch):
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: True))
    r = await admin_client.post(f"/admin/content/{uuid.uuid4()}/narrate")
    assert r.status_code == 404


async def test_narrate_blank_script_400(admin_client, monkeypatch):
    item = await _create_item(admin_client, narration_script="")
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: True))
    r = await admin_client.post(f"/admin/content/{item['id']}/narrate")
    assert r.status_code == 400


async def test_narrate_over_length_script_422(admin_client, monkeypatch):
    item = await _create_item(admin_client, narration_script="breathe " * 5000)
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: True))
    r = await admin_client.post(f"/admin/content/{item['id']}/narrate")
    assert r.status_code == 422


async def test_narrate_provider_failure_502(admin_client, monkeypatch):
    item = await _create_item(admin_client)
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: True))

    async def failing_synth(text, timeout=30.0):
        return None

    monkeypatch.setattr(voice_service, "synthesize", failing_synth)
    r = await admin_client.post(f"/admin/content/{item['id']}/narrate")
    assert r.status_code == 502


async def test_narrate_generates_and_serves_audio(admin_client, client, monkeypatch):
    item = await _create_item(admin_client)
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: True))

    async def fake_synth(text, timeout=30.0):
        assert "Settle in" in text
        assert timeout == 300
        return b"ID3-fake-narration-mp3"

    monkeypatch.setattr(voice_service, "synthesize", fake_synth)
    r = await admin_client.post(f"/admin/content/{item['id']}/narrate")
    assert r.status_code == 200
    body = r.json()
    assert body["audio_url"] == f"/media/narration/{item['id']}.mp3"
    assert body["audio_generated_at"] is not None

    # The MP3 landed under MEDIA_ROOT…
    disk = Path(settings.media_root) / "narration" / f"{item['id']}.mp3"
    assert disk.read_bytes() == b"ID3-fake-narration-mp3"

    # …and the public /media mount streams it without auth.
    served = await client.get(body["audio_url"])
    assert served.status_code == 200
    assert served.content == b"ID3-fake-narration-mp3"

    # Deleting the item cleans the minted file up.
    assert (await admin_client.delete(f"/admin/content/{item['id']}")).status_code == 204
    assert not disk.exists()


# ── duration probe ──────────────────────────────────────────────────────
def test_duration_probe_reads_a_cbr_stream():
    seconds = media_service.mp3_duration_seconds(_cbr_mp3(100))
    # 100 frames × 417 bytes at 128 kbps ≈ 2.6 s.
    assert seconds is not None
    assert abs(seconds - (100 * _FRAME_LEN * 8) / 128_000) < 0.01


def test_duration_probe_skips_an_id3_tag():
    plain = media_service.mp3_duration_seconds(_cbr_mp3(100))
    tagged = media_service.mp3_duration_seconds(_cbr_mp3(100, id3=True))
    assert plain is not None and tagged is not None
    # The tag's bytes are metadata, not audio: they must not add playing time.
    assert abs(tagged - plain) < 0.01


def test_duration_probe_prefers_the_xing_frame_count():
    # A VBR file is *shorter on disk* than its playing time implies — timing it
    # by byte length would report a fraction of a second instead of 3 minutes.
    seconds = media_service.mp3_duration_seconds(_xing_mp3(7656))
    assert seconds is not None
    assert abs(seconds - 7656 * _SAMPLES_PER_FRAME / _SAMPLE_RATE) < 0.01
    assert seconds > 199


def test_duration_probe_handles_mpeg2_and_mono():
    # MPEG 2 (half the sample rate, 576 samples/frame) and mono (a shorter
    # side-info block, so the Xing tag sits at a different offset). Neither is
    # what ElevenLabs returns today, but both are ordinary MP3s that an admin
    # could upload, and the offsets differ enough to be worth pinning.
    # 0xF3 = MPEG2 + Layer III; 0x80 = 64 kbps @ 22.05 kHz.
    mpeg2 = (bytes([0xFF, 0xF3, 0x80, 0x00]) + b"\x00" * (208 - 4)) * 50
    seconds = media_service.mp3_duration_seconds(mpeg2)
    assert seconds is not None
    assert abs(seconds - (50 * 208 * 8) / 64_000) < 0.01

    # MPEG1 mono: side info is 17 bytes, not 32.
    tag = b"Xing" + (1).to_bytes(4, "big") + (2000).to_bytes(4, "big")
    first = bytes([0xFF, 0xFB, 0x90, 0xC0]) + b"\x00" * 17 + tag
    mono = first + b"\x00" * (_FRAME_LEN - len(first))
    seconds = media_service.mp3_duration_seconds(mono)
    assert seconds is not None
    assert abs(seconds - 2000 * _SAMPLES_PER_FRAME / _SAMPLE_RATE) < 0.01


def test_duration_probe_ignores_a_xing_header_with_no_frame_count():
    # Flags bit 0 clear = the frame-count field is absent. Reading it anyway
    # would time the file from four bytes of padding.
    tag = b"Xing" + (0).to_bytes(4, "big") + (9999).to_bytes(4, "big")
    first = _HEADER + b"\x00" * 32 + tag
    frame = first + b"\x00" * (_FRAME_LEN - len(first))
    seconds = media_service.mp3_duration_seconds(frame * 40)
    assert seconds is not None
    # Falls back to the CBR calculation, not the bogus 9999-frame count.
    assert abs(seconds - (40 * _FRAME_LEN * 8) / 128_000) < 0.01


def test_duration_probe_returns_none_for_unreadable_bytes():
    # Callers keep their existing duration on None, so this is the safe path,
    # not an error path.
    assert media_service.mp3_duration_seconds(b"") is None
    assert media_service.mp3_duration_seconds(b"ID3-fake-narration-mp3") is None
    assert media_service.mp3_duration_seconds(b"\x00" * 4096) is None
    # Sync bits present but the layer field is reserved.
    assert media_service.mp3_duration_seconds(bytes([0xFF, 0xFF, 0xFF, 0xFF]) * 64) is None
    # Valid MPEG1 Layer III header, but the bitrate index is "bad" (15) — a
    # frame we must refuse rather than divide by.
    assert media_service.mp3_duration_seconds(bytes([0xFF, 0xFB, 0xF0, 0x00]) * 64) is None
    # …and the "free" bitrate index (0), which declares no rate at all.
    assert media_service.mp3_duration_seconds(bytes([0xFF, 0xFB, 0x00, 0x00]) * 64) is None
    # ID3 size field that isn't syncsafe: the tag is untrustworthy, so it is
    # ignored rather than used to skip an arbitrary distance into the file.
    assert media_service.mp3_duration_seconds(b"ID3\x04\x00\x00\xff\xff\xff\xff" + b"\x00" * 64) is None


def test_duration_minutes_rounds_half_up_and_never_reports_zero():
    assert media_service.duration_minutes(0.4) == 1      # exists ⇒ at least "1 min"
    assert media_service.duration_minutes(89) == 1
    assert media_service.duration_minutes(90) == 2       # banker's rounding would say 2
    assert media_service.duration_minutes(210) == 4      # …and 4 here, hence half-up
    assert media_service.duration_minutes(200) == 3


async def test_narrate_sets_duration_from_the_generated_audio(admin_client, monkeypatch):
    item = await _create_item(admin_client)
    assert item["duration_min"] == 8          # the authored guess
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: True))

    async def fake_synth(text, timeout=30.0):
        return _xing_mp3(7656)                # ≈ 200 s of audio

    monkeypatch.setattr(voice_service, "synthesize", fake_synth)
    r = await admin_client.post(f"/admin/content/{item['id']}/narrate")
    assert r.status_code == 200
    # The catalogue now advertises the length of the file it actually serves.
    assert r.json()["duration_min"] == 3

    # …and the public catalogue agrees (this is the number clients render).
    pub = await admin_client.get("/content", params={"q": item["title"]})
    assert next(c for c in pub.json() if c["id"] == item["id"])["duration_min"] == 3


async def test_narrate_keeps_the_authored_duration_when_audio_is_unreadable(
    admin_client, monkeypatch
):
    item = await _create_item(admin_client)
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: True))

    async def fake_synth(text, timeout=30.0):
        return b"not-really-an-mp3"

    monkeypatch.setattr(voice_service, "synthesize", fake_synth)
    r = await admin_client.post(f"/admin/content/{item['id']}/narrate")
    assert r.status_code == 200
    # Never replace a human's number with a guess.
    assert r.json()["duration_min"] == 8


async def test_public_catalogue_exposes_audio_url_but_not_script(admin_client):
    item = await _create_item(admin_client, title=f"Hygiene check {uuid.uuid4().hex[:6]}")
    pub = await admin_client.get("/content", params={"q": item["title"]})
    assert pub.status_code == 200
    match = next(c for c in pub.json() if c["id"] == item["id"])
    assert "audio_url" in match
    assert "narration_script" not in match

    # The admin listing does carry the script (CMS edits it).
    listing = await admin_client.get("/admin/content")
    row = next(c for c in listing.json() if c["id"] == item["id"])
    assert row["narration_script"] == _ITEM["narration_script"]
