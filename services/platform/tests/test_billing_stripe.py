"""The Stripe adapter + webhook behind the billing provider seam. Stripe's SDK isn't a
dependency, so these inject a fake `stripe` module to exercise the real wiring, and
force ImportError to prove the adapter stays INERT (clean 503 / ignored webhook) until
both the SDK and the STRIPE_* keys are present."""

import sys
import types

from app import billing_providers
from app.security import decode_access_token

_PW = "hunter2hunter2"


async def _signup(client, email):
    r = await client.post("/auth/signup", json={"email": email, "password": _PW, "name": "S"})
    assert r.status_code == 201, r.text
    tok = r.json()["access_token"]
    return {"Authorization": f"Bearer {tok}"}, decode_access_token(tok)["org_id"]


def _fake_stripe(monkeypatch, *, session_url="https://checkout.stripe.test/s/abc", construct=None):
    m = types.ModuleType("stripe")
    m.api_key = None

    class _Session:
        url = session_url

        @staticmethod
        def create(**kwargs):
            _Session.last = kwargs
            return _Session()

    m.checkout = types.SimpleNamespace(Session=_Session)

    def _construct(payload, sig, secret):
        if construct is None:
            raise ValueError("bad signature")
        return construct(payload, sig, secret)

    m.Webhook = types.SimpleNamespace(construct_event=staticmethod(_construct))

    class _Sub:
        modified: list = []

        @staticmethod
        def modify(sub_id, **kw):
            _Sub.modified.append((sub_id, kw))

    _Sub.modified = []
    m.Subscription = _Sub
    monkeypatch.setitem(sys.modules, "stripe", m)
    return m


# ── provider selection ───────────────────────────────────────────────────────

def test_provider_defaults_to_mock():
    assert billing_providers.provider_name() == "mock"  # BILLING_MOCK default true


def test_provider_is_none_when_billing_is_off(monkeypatch):
    monkeypatch.setattr("app.billing_providers.config.BILLING_MOCK", False)
    assert billing_providers.provider_name() == "none"


def test_provider_respects_explicit_selection(monkeypatch):
    monkeypatch.setenv("CEREBROZEN_BILLING_PROVIDER", "stripe")
    assert billing_providers.provider_name() == "stripe"


# ── checkout via Stripe ──────────────────────────────────────────────────────

async def test_stripe_checkout_returns_a_redirect_url(client, monkeypatch):
    monkeypatch.setenv("CEREBROZEN_BILLING_PROVIDER", "stripe")
    monkeypatch.setenv("STRIPE_SECRET_KEY", "sk_test_x")
    monkeypatch.setenv("STRIPE_PRICE_YEARLY", "price_x")
    _fake_stripe(monkeypatch)
    h, _ = await _signup(client, "stripe@example.com")
    r = await client.post("/billing/checkout", json={"plan": "plus", "interval": "yearly"}, headers=h)
    assert r.status_code == 201, r.text
    assert r.json()["checkout_url"].startswith("https://checkout.stripe")


async def test_stripe_checkout_is_503_without_keys(client, monkeypatch):
    monkeypatch.setenv("CEREBROZEN_BILLING_PROVIDER", "stripe")  # no STRIPE_SECRET_KEY
    h, _ = await _signup(client, "nokey@example.com")
    r = await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)
    assert r.status_code == 503


async def test_stripe_checkout_is_503_without_the_sdk(client, monkeypatch):
    monkeypatch.setenv("CEREBROZEN_BILLING_PROVIDER", "stripe")
    monkeypatch.setenv("STRIPE_SECRET_KEY", "sk_test_x")
    monkeypatch.setenv("STRIPE_PRICE_YEARLY", "price_x")
    monkeypatch.setitem(sys.modules, "stripe", None)  # force ImportError
    h, _ = await _signup(client, "nosdk@example.com")
    r = await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)
    assert r.status_code == 503


# ── webhook ──────────────────────────────────────────────────────────────────

async def test_webhook_activates_a_subscription(client, monkeypatch):
    h, org_id = await _signup(client, "wh@example.com")
    assert (await client.get("/billing/me", headers=h)).json()["plan"] == "free"
    monkeypatch.setenv("STRIPE_WEBHOOK_SECRET", "whsec_x")
    _fake_stripe(monkeypatch, construct=lambda p, s, sec: {
        "type": "checkout.session.completed",
        "data": {"object": {"client_reference_id": org_id}},
    })
    r = await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "t=1,v1=x"})
    assert r.status_code == 200
    assert (await client.get("/billing/me", headers=h)).json()["plan"] == "plus"


async def test_webhook_cancels_a_subscription(client, monkeypatch):
    h, org_id = await _signup(client, "whc@example.com")
    await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)  # mock-activate first
    assert (await client.get("/billing/me", headers=h)).json()["plan"] == "plus"
    monkeypatch.setenv("STRIPE_WEBHOOK_SECRET", "whsec_x")
    _fake_stripe(monkeypatch, construct=lambda p, s, sec: {
        "type": "customer.subscription.deleted",
        "data": {"object": {"metadata": {"org_id": org_id}}},
    })
    r = await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "x"})
    assert r.status_code == 200
    assert (await client.get("/billing/me", headers=h)).json()["plan"] == "free"


async def test_webhook_ignores_events_when_no_secret_is_set(client):
    # No STRIPE_WEBHOOK_SECRET → unverifiable → ignored (200), never a 500.
    r = await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "x"})
    assert r.status_code == 200 and r.json() == {"ok": True}


async def test_webhook_ignores_a_bad_signature(client, monkeypatch):
    monkeypatch.setenv("STRIPE_WEBHOOK_SECRET", "whsec_x")
    _fake_stripe(monkeypatch)  # construct=None → construct_event raises → ignored
    r = await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "bad"})
    assert r.status_code == 200 and r.json() == {"ok": True}


async def test_webhook_ignores_when_sdk_absent(client, monkeypatch):
    monkeypatch.setenv("STRIPE_WEBHOOK_SECRET", "whsec_x")
    monkeypatch.setitem(sys.modules, "stripe", None)  # ImportError
    r = await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "x"})
    assert r.status_code == 200


async def test_webhook_ignores_an_event_without_an_org(client, monkeypatch):
    monkeypatch.setenv("STRIPE_WEBHOOK_SECRET", "whsec_x")
    _fake_stripe(monkeypatch, construct=lambda p, s, sec: {
        "type": "checkout.session.completed", "data": {"object": {}},
    })
    r = await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "x"})
    assert r.status_code == 200


async def test_webhook_ignores_unrelated_event_types(client, monkeypatch):
    monkeypatch.setenv("STRIPE_WEBHOOK_SECRET", "whsec_x")
    _fake_stripe(monkeypatch, construct=lambda p, s, sec: {
        "type": "invoice.paid", "data": {"object": {"client_reference_id": "org1"}},
    })
    r = await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "x"})
    assert r.status_code == 200


async def test_webhook_stores_the_stripe_subscription_id(client, monkeypatch):
    """#6: capture the Stripe subscription id on activation so cancel can reach Stripe."""
    h, org_id = await _signup(client, "subid@example.com")
    monkeypatch.setenv("STRIPE_WEBHOOK_SECRET", "whsec_x")
    _fake_stripe(monkeypatch, construct=lambda p, s, sec: {
        "type": "checkout.session.completed",
        "data": {"object": {"client_reference_id": org_id, "subscription": "sub_123"}},
    })
    await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "x"})
    from sqlalchemy import select
    from app.db import SessionLocal
    from app.models import Subscription
    async with SessionLocal() as sdb:
        row = (await sdb.execute(select(Subscription).where(Subscription.org_id == org_id))).scalar_one()
        assert row.provider == "stripe" and row.provider_ref == "sub_123"


async def test_cancel_tells_stripe_to_stop_billing(client, monkeypatch):
    """#6: a local cancel must call Stripe (cancel_at_period_end) or the card keeps
    getting charged after the customer cancels."""
    h, org_id = await _signup(client, "stripe-cancel@example.com")
    monkeypatch.setenv("STRIPE_WEBHOOK_SECRET", "whsec_x")
    monkeypatch.setenv("STRIPE_SECRET_KEY", "sk_test_x")
    fake = _fake_stripe(monkeypatch, construct=lambda p, s, sec: {
        "type": "checkout.session.completed",
        "data": {"object": {"client_reference_id": org_id, "subscription": "sub_777"}},
    })
    # Activate via webhook so the sub is provider=stripe with provider_ref=sub_777.
    await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "x"})
    # Now cancel through the app.
    r = await client.post("/billing/cancel", headers=h)
    assert r.status_code == 200
    assert fake.Subscription.modified == [("sub_777", {"cancel_at_period_end": True})]


async def test_webhook_monthly_grants_a_month_not_a_year(client, monkeypatch):
    """#2: a monthly Stripe subscription must not be granted a full year (which the
    period-end cancel grace would then hand out for free)."""
    from datetime import datetime, timedelta, timezone
    from sqlalchemy import select
    from app.db import SessionLocal
    from app.models import Subscription

    h, org_id = await _signup(client, "monthly@example.com")
    monkeypatch.setenv("STRIPE_WEBHOOK_SECRET", "whsec_x")
    _fake_stripe(monkeypatch, construct=lambda p, s, sec: {
        "type": "checkout.session.completed",
        "data": {"object": {
            "client_reference_id": org_id, "subscription": "sub_m",
            "metadata": {"interval": "monthly"},
        }},
    })
    await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "x"})
    async with SessionLocal() as sdb:
        row = (await sdb.execute(select(Subscription).where(Subscription.org_id == org_id))).scalar_one()
        pe = row.current_period_end
        if pe.tzinfo is None:
            pe = pe.replace(tzinfo=timezone.utc)
        assert pe < datetime.now(timezone.utc) + timedelta(days=60), "monthly should be ~30d, not a year"


async def test_cancel_is_a_noop_at_stripe_for_the_mock_provider(client, monkeypatch):
    # A mock-provider sub has no Stripe id → cancel_subscription must not call Stripe.
    fake = _fake_stripe(monkeypatch)
    h = (await _signup(client, "mock-cancel@example.com"))[0]
    await client.post("/billing/checkout", json={"plan": "plus"}, headers=h)  # mock activate
    assert (await client.post("/billing/cancel", headers=h)).status_code == 200
    assert fake.Subscription.modified == []


async def test_cancel_surfaces_a_stripe_failure_and_keeps_the_sub_active(client, monkeypatch):
    """#5: when the Stripe cancel call fails we must NOT show the user 'canceled' while the
    card keeps being charged — surface 502 and leave the subscription active."""
    h, org_id = await _signup(client, "stripe-fail@example.com")
    monkeypatch.setenv("STRIPE_WEBHOOK_SECRET", "whsec_x")
    monkeypatch.setenv("STRIPE_SECRET_KEY", "sk_test_x")
    fake = _fake_stripe(monkeypatch, construct=lambda p, s, sec: {
        "type": "checkout.session.completed",
        "data": {"object": {"client_reference_id": org_id, "subscription": "sub_boom"}},
    })

    def _boom(sub_id, **kw):
        raise RuntimeError("stripe down")

    fake.Subscription.modify = staticmethod(_boom)
    await client.post("/billing/webhook", content=b"{}", headers={"stripe-signature": "x"})
    r = await client.post("/billing/cancel", headers=h)
    assert r.status_code == 502
    assert (await client.get("/billing/me", headers=h)).json()["plan"] == "plus", "still active"
