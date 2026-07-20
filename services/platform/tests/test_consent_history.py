"""Consent-change history (DPDP transparency): /users/me/consent/history returns the
person's own audit trail of consent toggles — content-free (category + boolean), newest
first, and private to them."""

_PW = "hunter2hunter2"


async def _signup(client, email):
    r = await client.post("/auth/signup", json={"email": email, "password": _PW, "name": "C"})
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


async def test_history_starts_empty_and_records_each_change(client):
    h = await _signup(client, "hist@example.com")
    assert (await client.get("/users/me/consent/history", headers=h)).json() == []

    # (The old access token stays valid until expiry; consent PATCH only revokes the
    #  refresh token, so reusing `h` for the reads is fine.)
    await client.patch("/users/me/consent", json={"journal_memory": True}, headers=h)
    await client.patch("/users/me/consent", json={"sleep_history": True}, headers=h)

    hist = (await client.get("/users/me/consent/history", headers=h)).json()
    assert len(hist) == 2
    assert hist[0]["key"] == "sleep_history" and hist[0]["value"] is True  # newest first
    assert hist[1]["key"] == "journal_memory" and hist[1]["value"] is True
    assert hist[0]["at"]


async def test_history_records_a_withdrawal_too(client):
    h = await _signup(client, "withdraw@example.com")
    await client.patch("/users/me/consent", json={"mood_history": True}, headers=h)
    await client.patch("/users/me/consent", json={"mood_history": False}, headers=h)
    hist = (await client.get("/users/me/consent/history", headers=h)).json()
    assert len(hist) == 2
    assert hist[0]["key"] == "mood_history" and hist[0]["value"] is False


async def test_history_is_private_to_the_user(client):
    a = await _signup(client, "a-hist@example.com")
    b = await _signup(client, "b-hist@example.com")
    await client.patch("/users/me/consent", json={"ai_memory": True}, headers=a)
    assert (await client.get("/users/me/consent/history", headers=b)).json() == []
    assert len((await client.get("/users/me/consent/history", headers=a)).json()) == 1


async def test_history_requires_auth(client):
    assert (await client.get("/users/me/consent/history")).status_code == 401


async def test_consent_status_never_set_then_fresh(client):
    h = await _signup(client, "fresh@example.com")
    r0 = (await client.get("/users/me/consent/status", headers=h)).json()
    assert r0["ever_set"] is False and r0["stale"] is False and r0["age_days"] is None
    assert r0["updated_at"] is None

    await client.patch("/users/me/consent", json={"mood_history": True}, headers=h)
    r1 = (await client.get("/users/me/consent/status", headers=h)).json()
    assert r1["ever_set"] is True and r1["age_days"] == 0 and r1["stale"] is False
    assert r1["consents"]["mood_history"] is True and r1["updated_at"]


async def test_consent_becomes_stale_past_the_threshold(client, monkeypatch):
    from datetime import datetime, timedelta, timezone
    from sqlalchemy import update
    from app.db import SessionLocal
    from app.models import User

    monkeypatch.setattr("app.config.CONSENT_STALE_DAYS", 30)
    h = await _signup(client, "stale@example.com")
    await client.patch("/users/me/consent", json={"mood_history": True}, headers=h)
    # Backdate the consent so it's older than the (patched) 30-day threshold.
    async with SessionLocal() as s:
        await s.execute(
            update(User)
            .where(User.email == "stale@example.com")
            .values(consent_updated_at=datetime.now(timezone.utc) - timedelta(days=60))
        )
        await s.commit()
    r = (await client.get("/users/me/consent/status", headers=h)).json()
    assert r["stale"] is True and r["age_days"] >= 60
