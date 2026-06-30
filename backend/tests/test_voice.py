"""Voice loop endpoints.

External calls (Deepgram/ElevenLabs) are stubbed so tests stay hermetic — no
network, no credits — while still exercising the route logic for both the
configured and unconfigured cases.
"""
from app.core.config import settings
from app.services import voice as voice_service


async def test_voice_status_requires_auth(client):
    r = await client.get("/voice/status")
    assert r.status_code in (401, 403)


async def test_voice_status_shape(auth_client):
    r = await auth_client.get("/voice/status")
    assert r.status_code == 200
    body = r.json()
    assert set(body) == {"stt", "tts"}
    assert isinstance(body["stt"], bool) and isinstance(body["tts"], bool)


async def test_tts_disabled_returns_503(auth_client, monkeypatch):
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: False))
    r = await auth_client.post("/voice/tts", json={"text": "hello"})
    assert r.status_code == 503


async def test_tts_enabled_returns_audio(auth_client, monkeypatch):
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: True))

    async def fake_synth(text):
        return b"ID3-fake-mp3-bytes"

    monkeypatch.setattr(voice_service, "synthesize", fake_synth)
    r = await auth_client.post("/voice/tts", json={"text": "Take a slow breath."})
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("audio/mpeg")
    assert r.content == b"ID3-fake-mp3-bytes"


async def test_stt_disabled_returns_503(auth_client, monkeypatch):
    monkeypatch.setattr(type(settings), "stt_enabled", property(lambda self: False))
    files = {"audio": ("speech.m4a", b"fakeaudio", "audio/m4a")}
    r = await auth_client.post("/voice/stt", files=files)
    assert r.status_code == 503


async def test_stt_enabled_returns_transcript(auth_client, monkeypatch):
    monkeypatch.setattr(type(settings), "stt_enabled", property(lambda self: True))

    async def fake_tx(audio, content_type="audio/mpeg"):
        return "I feel a little anxious"

    monkeypatch.setattr(voice_service, "transcribe", fake_tx)
    files = {"audio": ("speech.m4a", b"fakeaudio", "audio/m4a")}
    r = await auth_client.post("/voice/stt", files=files)
    assert r.status_code == 200
    assert r.json()["transcript"] == "I feel a little anxious"


async def test_stt_rejects_empty_upload(auth_client, monkeypatch):
    monkeypatch.setattr(type(settings), "stt_enabled", property(lambda self: True))
    files = {"audio": ("speech.m4a", b"", "audio/m4a")}
    r = await auth_client.post("/voice/stt", files=files)
    assert r.status_code == 400
