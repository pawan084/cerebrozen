"""The free-tier 429 has to be something a client can act on.

Before this, every client showed a generic failure when someone hit the cap —
the server's message never reached the user. The copy now promises "50 messages
a day", so the moment of hitting it has to be legible.
"""
import uuid
from datetime import timezone

from app.services import usage


async def _signup(client, prefix="quota"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "Q"}
    )
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


def test_next_reset_is_the_next_utc_midnight():
    """Copy saying "resets at midnight" is wrong for most of the world — the
    window is UTC, so clients get a timestamp to localise instead."""
    reset = usage.next_reset()
    assert reset.hour == 0 and reset.minute == 0 and reset.second == 0
    assert reset.tzinfo is not None
    assert reset > usage.utcnow()


async def test_hitting_the_cap_returns_an_actionable_payload(client, monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "free_daily_messages", 2)
    await _signup(client)

    for _ in range(2):
        assert (await client.post("/chat/messages", json={"text": "hello"})).status_code == 201

    blocked = await client.post("/chat/messages", json={"text": "one more"})
    assert blocked.status_code == 429
    detail = blocked.json()["detail"]
    # Structured, not a sentence — a client must be able to branch on it.
    assert detail["code"] == usage.FREE_LIMIT_CODE
    assert detail["limit"] == 2
    assert detail["used"] >= 2
    assert detail["resets_at"]
    assert "50" not in detail["message"]  # the number tracks the setting


async def test_premium_is_never_capped(client, monkeypatch):
    from app.core.config import settings
    from sqlalchemy import select

    from app.core.database import SessionLocal
    from app.models.user import User

    monkeypatch.setattr(settings, "free_daily_messages", 1)
    addr = await _signup(client, "premium")
    async with SessionLocal() as s:
        u = await s.scalar(select(User).where(User.email == addr))
        u.subscription_tier = "premium"
        await s.commit()

    for _ in range(3):
        assert (await client.post("/chat/messages", json={"text": "hi"})).status_code == 201
