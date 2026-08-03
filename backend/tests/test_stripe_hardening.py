"""Stripe hardening: replay safety, customer mapping, billing portal.

The three gaps PRD checklist #15 named. The first is the dangerous one — payment
webhooks are at-least-once, so a replayed `subscription.deleted` arriving after
a re-subscribe silently downgrades someone who is paying.
"""
import json
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.user import User
from app.models.webhook_event import ProcessedWebhook
from app.services import stripe_billing


async def _signup(client, prefix="stripe"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "S"}
    )
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def _user(addr: str) -> User:
    async with SessionLocal() as s:
        return await s.scalar(select(User).where(User.email == addr))


def _sub_event(event_id: str, user_id: str, *, deleted=False, customer="cus_test123",
               metadata=True) -> dict:
    return {
        "id": event_id,
        "type": "customer.subscription.deleted" if deleted else "customer.subscription.updated",
        "data": {"object": {
            "customer": customer,
            "status": "canceled" if deleted else "active",
            "current_period_end": 1900000000,
            "metadata": {"user_id": user_id} if metadata else {},
            "items": {"data": [{"price": {"id": "price_x"}}]},
        }},
    }


async def _post_event(client, monkeypatch, event: dict):
    """Deliver an event, bypassing signature verification (covered elsewhere)."""
    monkeypatch.setattr(stripe_billing, "verify_webhook", lambda payload, sig: event)
    return await client.post("/webhooks/stripe", content=json.dumps(event).encode(),
                             headers={"stripe-signature": "t=1,v1=x"})


async def test_a_replayed_event_is_ignored(client, monkeypatch):
    """The failure this prevents: a retried cancel landing after a re-subscribe."""
    addr = await _signup(client)
    user = await _user(addr)
    event = _sub_event("evt_replay_1", str(user.id), deleted=True)

    first = await _post_event(client, monkeypatch, event)
    assert first.json() == {"handled": True, "tier": "free"}

    second = await _post_event(client, monkeypatch, event)
    assert second.json()["handled"] is False
    assert second.json()["reason"] == "duplicate"

    async with SessionLocal() as s:
        rows = (await s.scalars(
            select(ProcessedWebhook).where(ProcessedWebhook.event_id == "evt_replay_1")
        )).all()
    assert len(rows) == 1


async def test_a_replay_cannot_undo_a_resubscribe(client, monkeypatch):
    """The concrete harm, end to end: cancel → re-subscribe → cancel REPLAY.
    The paying user must still be premium."""
    addr = await _signup(client)
    user = await _user(addr)
    uid = str(user.id)

    cancel = _sub_event("evt_c", uid, deleted=True)
    await _post_event(client, monkeypatch, cancel)
    await _post_event(client, monkeypatch, _sub_event("evt_r", uid))
    assert (await _user(addr)).subscription_tier == "premium"

    await _post_event(client, monkeypatch, cancel)          # Stripe retries the cancel
    assert (await _user(addr)).subscription_tier == "premium"


async def test_the_customer_id_is_persisted(client, monkeypatch):
    addr = await _signup(client)
    user = await _user(addr)
    await _post_event(client, monkeypatch, _sub_event("evt_cust", str(user.id), customer="cus_abc"))
    assert (await _user(addr)).stripe_customer_id == "cus_abc"


async def test_an_event_without_metadata_maps_by_customer(client, monkeypatch):
    """Subscriptions edited in the Stripe dashboard or the billing portal arrive
    without the metadata we set at checkout — that is why the column exists."""
    addr = await _signup(client)
    user = await _user(addr)
    # First event carries metadata and teaches us the customer id.
    await _post_event(client, monkeypatch, _sub_event("evt_a", str(user.id), customer="cus_zz"))
    # Second carries none, and a bad user reference.
    orphan = _sub_event("evt_b", "", deleted=True, customer="cus_zz", metadata=False)
    r = await _post_event(client, monkeypatch, orphan)

    assert r.json()["handled"] is True
    assert (await _user(addr)).subscription_tier == "free"


async def test_an_unknown_customer_is_still_refused(client, monkeypatch):
    orphan = _sub_event("evt_nobody", "", customer="cus_never_seen", metadata=False)
    r = await _post_event(client, monkeypatch, orphan)
    assert r.json() == {"handled": False, "reason": "unknown user"}


async def test_portal_is_503_without_keys(client):
    """Everything degrades without keys — including the honest message."""
    await _signup(client)
    r = await client.post("/billing/portal")
    assert r.status_code == 503
    assert "isn't available yet" in r.json()["detail"]


async def test_portal_is_409_before_any_subscription(client, monkeypatch):
    """Nothing to manage yet is a state, not a failure."""
    from app.core.config import settings

    monkeypatch.setattr(settings, "stripe_secret_key", "sk_test_x")
    await _signup(client)
    r = await client.post("/billing/portal")
    assert r.status_code == 409
    assert "subscribe first" in r.json()["detail"].lower()


async def test_portal_service_refuses_without_a_customer():
    with_error = None
    try:
        await stripe_billing.create_portal_session("")
    except stripe_billing.StripeError as exc:
        with_error = str(exc)
    assert with_error  # never silently returns a URL-less success
