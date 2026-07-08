"""Narrated-audio pipeline: the admin narrate endpoint, the /media mount, and
payload hygiene (the public catalogue exposes audio_url but never the script).

ElevenLabs is stubbed per the voice-test pattern — hermetic, no credits.
"""
import uuid
from pathlib import Path

from app.core.config import settings
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

    # …and the public /media mount streams it without auth.
    served = await client.get(body["audio_url"])
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
