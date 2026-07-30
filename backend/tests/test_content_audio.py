"""Narrated-audio pipeline: the admin narrate endpoint, the /media mount, and
payload hygiene (the public catalogue exposes audio_url but never the script).

ElevenLabs is stubbed per the voice-test pattern — hermetic, no credits.
"""
import uuid
from contextlib import asynccontextmanager
from datetime import timedelta
from pathlib import Path

from httpx import ASGITransport, AsyncClient
from sqlalchemy import update

from app.core.config import settings
from app.core.database import SessionLocal
from app.main import app
from app.models.user import User
from app.services import voice as voice_service

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
