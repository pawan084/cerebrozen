"""Stripe web billing: checkout gating, webhook signature, entitlement mapping."""
import hashlib
import hmac
import json
import time
import uuid

from sqlalchemy import update

from app.core.config import settings
from app.core.database import SessionLocal
from app.models.user import User
from app.services import stripe_billing


async def _give_customer_id(auth_client, customer_id: str = "cus_test123") -> None:
    """Put a Stripe customer on the authed user, as a real checkout would.

    The portal reads `user.stripe_customer_id` (stripe-hardening); a user who has
    never checked out has none, which is the 409 case rather than an error.
    """
    me = (await auth_client.get("/users/me")).json()
    async with SessionLocal() as s:
        await s.execute(
            update(User).where(User.id == uuid.UUID(me["id"])).values(stripe_customer_id=customer_id)
        )
        await s.commit()

_SECRET = "whsec_testsecret"


def _sign(payload: bytes, secret: str = _SECRET, ts: int | None = None) -> str:
    ts = int(time.time()) if ts is None else ts
    mac = hmac.new(secret.encode(), f"{ts}.{payload.decode()}".encode(), hashlib.sha256).hexdigest()
    return f"t={ts},v1={mac}"


async def test_checkout_503_when_unconfigured(auth_client):
    r = await auth_client.post("/billing/checkout", json={"tier": "premium"})
    assert r.status_code == 503
    assert "isn't available yet" in r.json()["detail"]


async def test_checkout_returns_url_when_configured(auth_client, monkeypatch):
    monkeypatch.setattr(settings, "stripe_secret_key", "sk_test_x")

    async def fake_session(user_id, tier, annual):
        assert tier == "premium_human" and annual is True
        return "https://checkout.stripe.com/c/pay/test123"

    monkeypatch.setattr(stripe_billing, "create_checkout_session", fake_session)
    r = await auth_client.post("/billing/checkout", json={"tier": "premium_human", "annual": True})
    assert r.status_code == 200
    assert r.json()["url"].startswith("https://checkout.stripe.com/")


async def test_checkout_502_when_stripe_errors(auth_client, monkeypatch):
    monkeypatch.setattr(settings, "stripe_secret_key", "sk_test_x")

    async def boom(*args, **kwargs):
        raise stripe_billing.StripeError("nope")

    monkeypatch.setattr(stripe_billing, "create_checkout_session", boom)
    assert (await auth_client.post("/billing/checkout", json={})).status_code == 502


async def test_webhook_rejects_bad_and_stale_signatures(client, monkeypatch):
    monkeypatch.setattr(settings, "stripe_webhook_secret", _SECRET)
    payload = json.dumps({"type": "checkout.session.completed", "data": {"object": {}}}).encode()

    r = await client.post("/webhooks/stripe", content=payload,
                          headers={"stripe-signature": "t=1,v1=deadbeef"})
    assert r.json()["handled"] is False

    stale = _sign(payload, ts=int(time.time()) - 3600)
    r = await client.post("/webhooks/stripe", content=payload,
                          headers={"stripe-signature": stale})
    assert r.json()["handled"] is False and "stale" in r.json()["reason"]


async def test_webhook_checkout_completed_sets_tier(client, monkeypatch):
    monkeypatch.setattr(settings, "stripe_webhook_secret", _SECRET)
    import uuid as _uuid
    email = f"stripe-{_uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": email, "password": "password123", "name": "S"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    uid = (await client.get("/users/me")).json()["id"]

    payload = json.dumps({
        "type": "checkout.session.completed",
        "data": {"object": {"client_reference_id": uid,
                            "metadata": {"user_id": uid, "tier": "premium"}}},
    }).encode()
    r = await client.post("/webhooks/stripe", content=payload,
                          headers={"stripe-signature": _sign(payload)})
    assert r.json() == {"handled": True, "tier": "premium"}
    assert (await client.get("/users/me")).json()["subscription_tier"] == "premium"

    # The subscription's cancellation reverts to free with the period end kept.
    payload = json.dumps({
        "type": "customer.subscription.deleted",
        "data": {"object": {"metadata": {"user_id": uid}, "status": "canceled",
                            "current_period_end": 1785000000}},
    }).encode()
    r = await client.post("/webhooks/stripe", content=payload,
                          headers={"stripe-signature": _sign(payload)})
    assert r.json() == {"handled": True, "tier": "free"}
    me = (await client.get("/users/me")).json()
    assert me["subscription_tier"] == "free"
    assert me["subscription_expires_at"] is not None


async def test_portal_503_when_unconfigured(auth_client):
    r = await auth_client.post("/billing/portal")
    assert r.status_code == 503
    assert "isn't available yet" in r.json()["detail"]


async def test_portal_returns_url_when_configured(auth_client, monkeypatch):
    monkeypatch.setattr(settings, "stripe_secret_key", "sk_test_x")
    await _give_customer_id(auth_client, "cus_abc")

    async def fake_portal(customer_id):
        # The route passes the stored customer straight through — no lookup.
        assert customer_id == "cus_abc"
        return "https://billing.stripe.com/p/session/test123"

    monkeypatch.setattr(stripe_billing, "create_portal_session", fake_portal)
    r = await auth_client.post("/billing/portal")
    assert r.status_code == 200
    assert r.json()["url"].startswith("https://billing.stripe.com/")


async def test_portal_409_when_never_checked_out(auth_client, monkeypatch):
    """No Stripe customer is a state, not a failure — and App Store subscribers
    land here too, so the copy must not report a gateway error at them."""
    monkeypatch.setattr(settings, "stripe_secret_key", "sk_test_x")

    async def unreachable(customer_id):  # pragma: no cover - must not be called
        raise AssertionError("Stripe must not be called without a customer")

    monkeypatch.setattr(stripe_billing, "create_portal_session", unreachable)
    r = await auth_client.post("/billing/portal")
    assert r.status_code == 409
    assert "subscribe first" in r.json()["detail"].lower()


async def test_portal_502_when_stripe_errors(auth_client, monkeypatch):
    monkeypatch.setattr(settings, "stripe_secret_key", "sk_test_x")
    await _give_customer_id(auth_client)

    async def boom(customer_id):
        raise stripe_billing.StripeError("portal session creation failed")

    monkeypatch.setattr(stripe_billing, "create_portal_session", boom)
    r = await auth_client.post("/billing/portal")
    assert r.status_code == 502
    assert "try again shortly" in r.json()["detail"]


class _FakeResponse:
    def __init__(self, status_code, body):
        self.status_code = status_code
        self._body = body
        self.text = json.dumps(body)

    def json(self):
        return self._body


class _FakeClient:
    """Stands in for httpx.AsyncClient; captures the form payload."""
    last_data = None
    response: _FakeResponse = _FakeResponse(200, {"url": "https://checkout.stripe.com/c/ok"})
    get_response: _FakeResponse = _FakeResponse(200, {"data": []})

    def __init__(self, *args, **kwargs): ...
    async def __aenter__(self):
        return self
    async def __aexit__(self, *exc):
        return False
    async def post(self, url, data=None, auth=None):
        _FakeClient.last_data = data
        return _FakeClient.response
    async def get(self, url, params=None, auth=None):
        _FakeClient.last_params = params
        return _FakeClient.get_response


async def test_create_checkout_session_builds_stripe_request(monkeypatch):
    import httpx
    monkeypatch.setattr(settings, "stripe_secret_key", "sk_test_x")
    monkeypatch.setattr(settings, "stripe_price_premium_annual", "price_p_a")
    monkeypatch.setattr(httpx, "AsyncClient", _FakeClient)

    _FakeClient.response = _FakeResponse(200, {"url": "https://checkout.stripe.com/c/ok"})
    url = await stripe_billing.create_checkout_session("user-123", "premium", True)
    assert url == "https://checkout.stripe.com/c/ok"
    sent = _FakeClient.last_data
    assert sent["line_items[0][price]"] == "price_p_a"
    assert sent["client_reference_id"] == "user-123"
    assert sent["subscription_data[metadata][user_id]"] == "user-123"
    assert sent["mode"] == "subscription"

    # Stripe-side failure surfaces as StripeError (route maps it to 502).
    _FakeClient.response = _FakeResponse(400, {"error": {"message": "bad key"}})
    try:
        await stripe_billing.create_checkout_session("user-123", "premium", True)
        raise AssertionError("expected StripeError")
    except stripe_billing.StripeError:
        pass

    # Missing price configuration is a StripeError before any network call.
    try:
        stripe_billing.price_for("premium_human", False)
        raise AssertionError("expected StripeError")
    except stripe_billing.StripeError:
        pass


async def test_create_portal_session_posts_the_stored_customer(monkeypatch):
    import httpx
    monkeypatch.setattr(settings, "stripe_secret_key", "sk_test_x")
    monkeypatch.setattr(httpx, "AsyncClient", _FakeClient)

    # Happy path: the stored customer goes straight to the portal endpoint.
    # There is deliberately no subscription search any more — the customer
    # mapping is persisted on the user, so the round-trip (and its "no
    # subscription" failure mode for a customer who has one) is gone.
    _FakeClient.response = _FakeResponse(200, {"url": "https://billing.stripe.com/p/ok"})
    url = await stripe_billing.create_portal_session("cus_9")
    assert url == "https://billing.stripe.com/p/ok"
    assert _FakeClient.last_data["customer"] == "cus_9"
    assert _FakeClient.last_data["return_url"] == settings.stripe_return_url

    # An empty customer never reaches the network.
    _FakeClient.last_data = None
    try:
        await stripe_billing.create_portal_session("")
        raise AssertionError("expected StripeError")
    except stripe_billing.StripeError as exc:
        assert "no billing account" in str(exc)
    assert _FakeClient.last_data is None

    # Stripe-side failures surface as StripeError (route maps them to 502).
    _FakeClient.response = _FakeResponse(400, {"error": {"message": "no portal config"}})
    try:
        await stripe_billing.create_portal_session("cus_9")
        raise AssertionError("expected StripeError")
    except stripe_billing.StripeError:
        pass


def test_entitlement_maps_prices_to_tiers(monkeypatch):
    monkeypatch.setattr(settings, "stripe_price_premium_human_monthly", "price_ph_m")
    event = {
        "type": "customer.subscription.updated",
        "data": {"object": {
            "metadata": {"user_id": "u-1"}, "status": "active",
            "current_period_end": 1785000000,
            "items": {"data": [{"price": {"id": "price_ph_m"}}]},
        }},
    }
    user_id, tier, expires = stripe_billing.entitlement_from_event(event)
    assert (user_id, tier) == ("u-1", "premium_human")
    assert expires is not None
    # Unknown event types are ignored, not errors.
    assert stripe_billing.entitlement_from_event({"type": "invoice.paid", "data": {"object": {}}}) is None
