"""Narrated-audio pipeline: the admin narrate endpoint, the /media mount, and
payload hygiene (the public catalogue exposes audio_url but never the script).

ElevenLabs is stubbed per the voice-test pattern — hermetic, no credits.
"""
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path

from httpx import ASGITransport, AsyncClient
from sqlalchemy import update

from app.core.config import settings
from app.core.database import SessionLocal
from app.main import app
from app.models.user import User
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

    # …but the bare path the admin API reports is NOT playable on its own.
    assert (await client.get(body["audio_url"])).status_code == 404

    # A client plays the signed URL the catalogue hands out (free item, so an
    # anonymous caller gets one).
    pub = await client.get("/content", params={"q": item["title"]})
    signed = next(c for c in pub.json() if c["id"] == item["id"])["audio_url"]
    assert signed.startswith(f"/media/narration/{item['id']}.mp3?t=")
    served = await client.get(signed)
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


# ── Narration entitlement ────────────────────────────────────────────────
# The regression these pin: /media was a bare StaticFiles mount, so a premium
# story's MP3 was fetchable by anyone who knew its URL, and /content handed
# that URL to every caller including signed-out ones.

async def _narrate(admin_client, monkeypatch, **overrides):
    """Create an item and generate its audio, with ElevenLabs stubbed out."""
    item = await _create_item(admin_client, **overrides)
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: True))

    async def fake_synth(text, timeout=30.0):
        return b"ID3-premium-narration"

    monkeypatch.setattr(voice_service, "synthesize", fake_synth)
    r = await admin_client.post(f"/admin/content/{item['id']}/narrate")
    assert r.status_code == 200
    return item


async def _audio_url_for(http_client, title):
    listing = await http_client.get("/content", params={"q": title})
    assert listing.status_code == 200
    rows = [c for c in listing.json() if c["title"] == title]
    assert rows, f"expected {title!r} in the catalogue"
    return rows[0]["audio_url"]


@asynccontextmanager
async def _separate_client(signed_in: bool = False):
    """An independent client, because the shared fixtures fight over one header.

    ``auth_client`` and ``admin_client`` both set ``Authorization`` on the *same*
    client instance, so a test needing an admin AND a plain caller has to make
    its own rather than request both fixtures.
    """
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        user_id = None
        if signed_in:
            email = f"listener-{uuid.uuid4().hex[:10]}@test.app"
            r = await c.post(
                "/auth/signup",
                json={"email": email, "password": "password123", "name": "Listener"},
            )
            assert r.status_code == 201, r.text
            c.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
            user_id = (await c.get("/users/me")).json()["id"]
        yield c, user_id


async def test_premium_narration_is_withheld_from_anonymous_and_free_callers(
    admin_client, monkeypatch
):
    title = f"Premium story {uuid.uuid4().hex[:6]}"
    item = await _narrate(admin_client, monkeypatch, title=title, premium=True)

    # Signed out, and signed in on the default free tier: no URL at all, which
    # is how clients already fall back to the bundled bed.
    async with _separate_client() as (anon, _):
        assert await _audio_url_for(anon, title) == ""
        # And guessing the path gets nowhere, which was the actual hole.
        assert (await anon.get(f"/media/narration/{item['id']}.mp3")).status_code == 404

    async with _separate_client(signed_in=True) as (free, _):
        assert await _audio_url_for(free, title) == ""


async def test_premium_narration_plays_for_a_paid_caller(admin_client, monkeypatch):
    title = f"Paid story {uuid.uuid4().hex[:6]}"
    await _narrate(admin_client, monkeypatch, title=title, premium=True)

    async with _separate_client(signed_in=True) as (paid, user_id):
        # Tier comes from a verified receipt, never from the client, so set it the
        # way the other tier tests do (test_tier3._set_tier).
        async with SessionLocal() as s:
            await s.execute(
                update(User)
                .where(User.id == uuid.UUID(user_id))
                .values(subscription_tier="premium")
            )
            await s.commit()

        signed = await _audio_url_for(paid, title)
        assert "?t=" in signed
        played = await paid.get(signed)
        assert played.status_code == 200
        assert played.content == b"ID3-premium-narration"


async def test_a_grant_for_one_track_does_not_unlock_another(admin_client, client, monkeypatch):
    """A leaked URL must unlock exactly the track it names."""
    first = f"Track A {uuid.uuid4().hex[:6]}"
    second = f"Track B {uuid.uuid4().hex[:6]}"
    await _narrate(admin_client, monkeypatch, title=first)
    other = await _narrate(admin_client, monkeypatch, title=second)

    token = (await _audio_url_for(client, first)).split("?t=")[1]
    replayed = await client.get(f"/media/narration/{other['id']}.mp3?t={token}")
    assert replayed.status_code == 404


async def test_garbage_and_wrong_type_tokens_are_refused(admin_client, client, monkeypatch):
    from app.core.security import create_access_token

    title = f"Token check {uuid.uuid4().hex[:6]}"
    item = await _narrate(admin_client, monkeypatch, title=title)
    base = f"/media/narration/{item['id']}.mp3"

    assert (await client.get(f"{base}?t=not-a-jwt")).status_code == 404
    assert (await client.get(f"{base}?t=")).status_code == 404
    # A real, valid session token is the wrong *kind* of credential here.
    assert (await client.get(f"{base}?t={create_access_token(str(item['id']))}")).status_code == 404


async def test_absolute_audio_urls_pass_through_unsigned(admin_client):
    """A third-party CDN URL isn't ours to gate — hand it over as-is."""
    title = f"External audio {uuid.uuid4().hex[:6]}"
    external = "https://cdn.example.com/story.mp3"
    await _create_item(admin_client, title=title, audio_url=external, narration_script="")
    assert await _audio_url_for(admin_client, title) == external


async def test_media_token_without_a_subject_is_refused():
    from app.core.security import _create_token, MEDIA
    from app.services import media as media_service

    # Structurally valid, right type, but names no track.
    empty = _create_token("", MEDIA, timedelta(hours=1))
    assert media_service.token_authorizes(empty, "/media/narration/anything.mp3") is False


# ── Optional auth degrades to anonymous, never to a 500 or a 401 ─────────
# /content must stay readable for signed-out visitors, so anything unusable as
# an identity is simply "no user" — but it must never be accepted as one.

async def test_catalogue_treats_an_unusable_token_as_anonymous(admin_client, monkeypatch):
    from app.core.security import _create_token, ACCESS, create_access_token

    title = f"Anon fallback {uuid.uuid4().hex[:6]}"
    await _narrate(admin_client, monkeypatch, title=title, premium=True)

    me = (await admin_client.get("/users/me")).json()

    async with _separate_client() as (c, _):
        # Garbage, a non-uuid subject, and a token for a user that doesn't exist
        # all read as anonymous: premium narration stays withheld.
        for bad in (
            "Bearer not-a-jwt",
            f"Bearer {_create_token('not-a-uuid', ACCESS, timedelta(hours=1), version=0)}",
            f"Bearer {create_access_token(str(uuid.uuid4()))}",
        ):
            c.headers["Authorization"] = bad
            assert await _audio_url_for(c, title) == ""

        # A *revoked* token (token_version bumped past it) is likewise not an
        # identity, even though the user is real and the signature is good.
        async with SessionLocal() as s:
            await s.execute(
                update(User).where(User.id == uuid.UUID(me["id"])).values(token_version=99)
            )
            await s.commit()
        c.headers["Authorization"] = f"Bearer {create_access_token(me['id'], version=0)}"
        assert await _audio_url_for(c, title) == ""


async def test_catalogue_ignores_a_deactivated_users_token(admin_client, monkeypatch):
    from app.core.security import create_access_token

    title = f"Deactivated {uuid.uuid4().hex[:6]}"
    await _narrate(admin_client, monkeypatch, title=title, premium=True)

    async with _separate_client(signed_in=True) as (c, user_id):
        async with SessionLocal() as s:
            await s.execute(
                update(User)
                .where(User.id == uuid.UUID(user_id))
                .values(subscription_tier="premium", is_active=False)
            )
            await s.commit()
        # Paid tier, but the account is switched off — no grant.
        assert await _audio_url_for(c, title) == ""


# ── What the playback grant binds, and what it does not (WC-80) ─────────
#
# Item 80 asks to verify the media token cannot be replayed across users. It
# can. That is not a defect found here, it is the shape of the mechanism: the
# grant rides in the URL precisely BECAUSE the fetcher cannot authenticate —
# AVPlayer, ExoPlayer and <audio> do not attach an Authorization header. A
# bearer URL cannot know who is holding it, so anyone holding it is the bearer.
#
# Which makes the honest deliverable the opposite of the one the item implies:
# pin what the grant really binds (one track, for a bounded window, signed by
# this server), and pin the replay itself, so that it stays a decision somebody
# made rather than a property nobody checked.

async def test_a_grant_minted_for_one_listener_plays_for_anyone_holding_it(
    admin_client, monkeypatch
):
    """The cross-user replay, run end to end and asserted to SUCCEED.

    A paid listener's signed URL is handed to a free account and to a signed-out
    stranger. Both play the file. Nothing in the token names a user, and the
    guard has no session to compare it against.

    This test passes on the behaviour as shipped. If somebody later binds the
    grant to an account (WC-81), this is the test that fails — and it should,
    loudly, because that change breaks every already-issued URL and the failure
    is the reminder to think about it.
    """
    title = f"Bearer story {uuid.uuid4().hex[:6]}"
    await _narrate(admin_client, monkeypatch, title=title, premium=True)

    async with _separate_client(signed_in=True) as (paid, user_id):
        async with SessionLocal() as s:
            await s.execute(
                update(User)
                .where(User.id == uuid.UUID(user_id))
                .values(subscription_tier="premium")
            )
            await s.commit()
        signed = await _audio_url_for(paid, title)
        assert "?t=" in signed

    # A different account, on the free tier, which was refused a URL of its own.
    async with _separate_client(signed_in=True) as (free, _):
        assert await _audio_url_for(free, title) == "", "free tier is not entitled"
        replayed = await free.get(signed)
        assert replayed.status_code == 200, "documented: the grant is a bearer URL"
        assert replayed.content == b"ID3-premium-narration"

    # And nobody at all.
    async with _separate_client() as (anon, _):
        assert (await anon.get(signed)).status_code == 200


async def test_the_grant_stops_working_when_it_expires():
    """The one bound the bearer URL really has: time.

    Since possession is authorization, the TTL is not a nicety — it is the only
    thing that ever revokes a leaked URL. `create_media_token` says the window
    is why a leak "stops working long before it can be shared around"; this is
    the mechanism behind that sentence.
    """
    from app.core.security import MEDIA, _create_token

    item_id = str(uuid.uuid4())
    path = f"/media/narration/{item_id}.mp3"

    live = _create_token(item_id, MEDIA, timedelta(hours=1))
    assert media_service.token_authorizes(live, path) is True

    expired = _create_token(item_id, MEDIA, timedelta(hours=-1))
    assert media_service.token_authorizes(expired, path) is False


async def test_a_grant_signed_by_somebody_else_is_refused():
    """Possession authorizes, so minting must not be possible off-server.

    Without this, "possession is authorization" degrades into "anyone who can
    write a JWT is authorized", and the item scope and TTL both stop meaning
    anything.
    """
    from jose import jwt

    from app.core.security import MEDIA

    item_id = str(uuid.uuid4())
    forged = jwt.encode(
        {
            "sub": item_id,
            "type": MEDIA,
            "exp": datetime.now(timezone.utc) + timedelta(hours=1),
        },
        "not-the-servers-secret",
        algorithm=settings.algorithm,
    )
    assert media_service.token_authorizes(
        forged, f"/media/narration/{item_id}.mp3"
    ) is False


async def test_catalogue_assets_are_served_with_no_grant_at_all(client):
    """Recorded because WC-81 turns on it: the guard covers narration ONLY.

    `media_guard` matches `/media/narration/`. Everything under `/media/assets/`
    — the admin-uploaded catalogue — is public bytes, deliberately, because
    today it holds decorative ambience the clients need before sign-in.

    WC-81 proposes putting licensed content in that same catalogue. It would be
    served to anyone who asks, with no token, no entitlement check and no
    expiry: not a replay window, no window at all. This test states the current
    boundary so that change cannot be made without meeting it.
    """
    key = f"test.replay.{uuid.uuid4().hex[:8]}"
    media_service.save_asset(key, ".m4a", b"ambience-bytes")
    try:
        r = await client.get(f"/media/assets/{key}.m4a")
        assert r.status_code == 200
        assert r.content == b"ambience-bytes"
    finally:
        media_service.delete_asset(key)


async def test_a_grant_is_checked_on_HEAD_as_well_as_GET(admin_client, monkeypatch):
    """A range-seeking player HEADs first; an unguarded HEAD leaks existence.

    The guard names both methods. Without HEAD, "does this premium track exist"
    is answerable without a grant — which is the exact question the 404-instead-
    of-403 choice elsewhere in the guard exists to refuse.
    """
    title = f"Head check {uuid.uuid4().hex[:6]}"
    item = await _narrate(admin_client, monkeypatch, title=title, premium=True)

    async with _separate_client() as (anon, _):
        assert (await anon.head(f"/media/narration/{item['id']}.mp3")).status_code == 404
