"""Remaining route coverage: subscription success, nudges, admin, webhook edges."""
from app.services import appstore


async def test_subscription_verify_success(auth_client, monkeypatch):
    monkeypatch.setattr(appstore, "verify_transaction", lambda s: {"productId": "com.cerebrozen.premium.monthly"})
    monkeypatch.setattr(appstore, "tier_for", lambda d: ("premium", None))
    r = await auth_client.post("/users/me/subscription/verify", json={"signed_transaction": "x.y.z"})
    assert r.status_code == 200
    assert (await auth_client.get("/users/me")).json().get("subscription_tier") == "premium"


async def test_upcoming_nudges(auth_client):
    # Creating a low mood queues a contextual nudge; the list should return.
    await auth_client.post("/moods", json={"mood": "Low", "note": "hard day", "intensity": 1})
    r = await auth_client.get("/nudges")
    assert r.status_code == 200 and isinstance(r.json(), list)
