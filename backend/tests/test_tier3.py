"""Tier 3: free-tier quota, compliance attestation, consent enforcement."""
import uuid

from sqlalchemy import update

from app.core.database import SessionLocal
from app.models.user import User
from app.services import usage


async def _set_tier(user_id: str, tier: str):
    async with SessionLocal() as s:
        await s.execute(update(User).where(User.id == uuid.UUID(user_id)).values(subscription_tier=tier))
        await s.commit()


async def test_free_quota_blocks_after_limit(auth_client, monkeypatch):
    monkeypatch.setattr(usage.settings, "free_daily_messages", 2)
    for _ in range(2):
        r = await auth_client.post("/chat/messages", json={"text": "hello"})
        assert r.status_code == 201, r.text
    blocked = await auth_client.post("/chat/messages", json={"text": "again"})
    assert blocked.status_code == 429
    assert "limit" in blocked.json()["detail"].lower()


async def test_premium_tier_is_unlimited(auth_client, monkeypatch):
    monkeypatch.setattr(usage.settings, "free_daily_messages", 1)
    me = (await auth_client.get("/users/me")).json()
    await _set_tier(me["id"], "premium")
    for _ in range(3):
        r = await auth_client.post("/chat/messages", json={"text": "hi"})
        assert r.status_code == 201, r.text


async def test_attest_stamps_compliance(auth_client):
    me = (await auth_client.get("/users/me")).json()
    assert me["age_confirmed_at"] is None
    assert me["ai_disclosure_ack_at"] is None
    r = await auth_client.post("/users/me/attest")
    assert r.status_code == 200
    body = r.json()
    assert body["age_confirmed_at"] is not None
    assert body["ai_disclosure_ack_at"] is not None
    # Idempotent: a second call keeps the original timestamp.
    first = body["age_confirmed_at"]
    again = (await auth_client.post("/users/me/attest")).json()
    assert again["age_confirmed_at"] == first


async def test_attest_honors_past_client_time_and_caps_future(client):
    import datetime as dt

    async def fresh():
        e = f"attest-{uuid.uuid4().hex[:10]}@test.app"
        r = await client.post("/auth/signup", json={"email": e, "password": "password123", "name": "T"})
        return {"Authorization": f"Bearer {r.json()['access_token']}"}

    # The on-device confirmation predates the first connect → trusted verbatim.
    headers = await fresh()
    past = "2026-01-02T03:04:05Z"
    body = (await client.post("/users/me/attest",
                              json={"age_confirmed_at": past}, headers=headers)).json()
    assert body["age_confirmed_at"].startswith("2026-01-02T03:04:05")

    # A future client clock never becomes the record — the server caps it at now.
    headers = await fresh()
    future_dt = dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=7)
    body = (await client.post("/users/me/attest",
                              json={"age_confirmed_at": future_dt.isoformat()}, headers=headers)).json()
    recorded = dt.datetime.fromisoformat(body["age_confirmed_at"].replace("Z", "+00:00"))
    assert recorded < future_dt


async def test_chat_works_with_memory_off(auth_client):
    r = await auth_client.patch("/users/me/consent", json={"ai_memory": False})
    assert r.status_code == 200
    assert r.json()["ai_memory"] is False
    # With memory off the pipeline drops long-term history but still replies.
    r = await auth_client.post("/chat/messages", json={"text": "I feel anxious"})
    assert r.status_code == 201
    assert r.json()["reply"]["text"]


async def test_new_user_defaults_to_free_tier(auth_client):
    me = (await auth_client.get("/users/me")).json()
    assert me["subscription_tier"] == "free"
