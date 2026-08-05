"""Input caps and cost guards (register C16-C18, C77-C79).

The LLM-facing text fields are bounded server-side (clients cap their
composers, but the server stops trusting them to), and the provider-billed
voice loop draws on the same free-tier quota as the chat it voices.
"""
import uuid

from app.core.config import settings
from app.services import usage
from app.services import voice as voice_service


async def _signup(client, prefix="guard"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "G"}
    )
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


# ── Bounded text fields ─────────────────────────────────────────────────

async def test_chat_text_is_capped(auth_client):
    r = await auth_client.post("/chat/messages", json={"text": "x" * 4001})
    assert r.status_code == 422


async def test_oracle_text_is_capped(auth_client):
    # Validation runs before the availability check, so this pins the cap
    # whether or not the agent is configured.
    r = await auth_client.post("/oracle/messages", json={"text": "x" * 4001})
    assert r.status_code == 422


async def test_journal_body_and_tags_are_capped(auth_client):
    assert (await auth_client.post(
        "/journal", json={"title": "t", "body": "x" * 50_001}
    )).status_code == 422
    assert (await auth_client.post(
        "/journal", json={"title": "t", "body": "ok", "tags": [f"t{i}" for i in range(21)]}
    )).status_code == 422
    assert (await auth_client.post(
        "/journal", json={"title": "t", "body": "ok", "tags": ["x" * 61]}
    )).status_code == 422
    # A real entry with real tags still lands.
    ok = await auth_client.post(
        "/journal", json={"title": "t", "body": "a good day", "tags": ["gratitude"]}
    )
    assert ok.status_code == 201


# ── Voice draws on the chat quota ───────────────────────────────────────

async def test_tts_respects_free_tier_quota(client, monkeypatch):
    monkeypatch.setattr(type(settings), "tts_enabled", property(lambda self: True))
    monkeypatch.setattr(settings, "free_daily_messages", 0)

    async def fake_synth(text):  # must never be reached
        raise AssertionError("provider called past the quota")

    monkeypatch.setattr(voice_service, "synthesize", fake_synth)
    await _signup(client, "tts-quota")
    r = await client.post("/voice/tts", json={"text": "hello"})
    assert r.status_code == 429
    assert r.json()["detail"]["code"] == usage.FREE_LIMIT_CODE
