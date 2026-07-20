"""Google Play Billing server-side verification (#50). Play is client-buys/server-verifies:
the app sends a purchase token, we confirm it with Google, then activate. The google client
libs aren't a dependency, so these stub the actual API call (`_play_purchase_state`) and
force its absence to prove the adapter stays inert (503) until Play is configured."""

from app import billing_providers

_PW = "hunter2hunter2"


async def _signup(client, email):
    r = await client.post("/auth/signup", json={"email": email, "password": _PW, "name": "P"})
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


def _configure_play(monkeypatch):
    monkeypatch.setenv("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON", '{"type":"service_account"}')
    monkeypatch.setenv("GOOGLE_PLAY_PACKAGE_NAME", "com.cerebrozen.app")


_REQ = {"purchase_token": "tok-abc", "product_id": "plus_yearly"}


async def test_verify_is_503_when_play_is_not_configured(client):
    h = await _signup(client, "play-off@example.com")
    r = await client.post("/billing/play/verify", json=_REQ, headers=h)
    assert r.status_code == 503


async def test_verify_is_503_when_the_google_sdk_is_absent(client, monkeypatch):
    # Configured, but _play_purchase_state does the real google import (not installed) →
    # verify_play_purchase catches and returns None → the endpoint 503s.
    _configure_play(monkeypatch)
    h = await _signup(client, "play-nosdk@example.com")
    r = await client.post("/billing/play/verify", json=_REQ, headers=h)
    assert r.status_code == 503


async def test_a_valid_purchase_activates_plus(client, monkeypatch):
    _configure_play(monkeypatch)
    monkeypatch.setattr(
        billing_providers, "_play_purchase_state",
        lambda c, p, pr, t: {"valid": True, "expiry_ms": None},
    )
    h = await _signup(client, "play-ok@example.com")
    r = await client.post("/billing/play/verify", json=_REQ, headers=h)
    assert r.status_code == 201 and r.json()["plan"] == "plus"
    assert (await client.get("/billing/me", headers=h)).json()["plan"] == "plus"


async def test_a_valid_purchase_uses_googles_expiry(client, monkeypatch):
    _configure_play(monkeypatch)
    monkeypatch.setattr(
        billing_providers, "_play_purchase_state",
        lambda *a: {"valid": True, "expiry_ms": 2_000_000_000_000},
    )
    h = await _signup(client, "play-exp@example.com")
    r = await client.post("/billing/play/verify", json=_REQ, headers=h)
    assert r.status_code == 201
    assert r.json()["current_period_end"] is not None


async def test_an_invalid_purchase_is_402(client, monkeypatch):
    _configure_play(monkeypatch)
    monkeypatch.setattr(
        billing_providers, "_play_purchase_state",
        lambda *a: {"valid": False, "expiry_ms": None},
    )
    h = await _signup(client, "play-bad@example.com")
    r = await client.post("/billing/play/verify", json=_REQ, headers=h)
    assert r.status_code == 402


async def test_a_b2b_seat_cannot_use_play_verify(client, org_with_admin):
    r = await client.post("/billing/play/verify", json=_REQ, headers=org_with_admin["admin_headers"])
    assert r.status_code == 409


async def test_a_purchase_token_cannot_be_replayed_across_accounts(client, monkeypatch):
    """A single real purchase yields a token Google keeps calling valid; without binding it
    to one account, anyone could replay it for free Plus."""
    _configure_play(monkeypatch)
    monkeypatch.setattr(
        billing_providers, "_play_purchase_state", lambda *a: {"valid": True, "expiry_ms": None}
    )
    h1 = await _signup(client, "player1@example.com")
    h2 = await _signup(client, "player2@example.com")
    assert (await client.post("/billing/play/verify", json=_REQ, headers=h1)).status_code == 201
    # a DIFFERENT account replaying the same token → rejected
    assert (await client.post("/billing/play/verify", json=_REQ, headers=h2)).status_code == 409
    # the SAME account re-verifying (e.g. a refresh) is fine
    assert (await client.post("/billing/play/verify", json=_REQ, headers=h1)).status_code == 201
