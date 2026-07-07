"""Web Push (VAPID): subscription CRUD, dispatch preference order, degradation."""
import uuid
from datetime import timedelta
from types import SimpleNamespace

import pywebpush
from sqlalchemy import select

from app.core.config import settings
from app.core.database import SessionLocal, utcnow
from app.models.nudge import Nudge
from app.models.user import User
from app.models.web_push import WebPushSubscription
from app.services import email as email_service
from app.services import nudges, webpush

SUB = {"endpoint": "https://push.example.com/reg/abc123", "p256dh": "BKey", "auth": "authsecret"}


async def _signup(client, prefix="webpush"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "W"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def _make_due_nudge(addr: str, title="Browser nudge"):
    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        s.add(Nudge(user_id=user.id, kind="announcement", title=title,
                    body="One small step today.", scheduled_for=utcnow() - timedelta(minutes=1)))
        await s.commit()


async def test_status_reports_keys_and_count(client):
    await _signup(client)
    r = await client.get("/users/me/push-subscriptions")
    assert r.status_code == 200
    body = r.json()
    # Hermetic CI runs keyless — the client must see an honest "disabled".
    assert body["enabled"] is settings.webpush_enabled
    assert body["subscriptions"] == 0

    r = await client.post("/users/me/push-subscriptions", json=SUB | {"endpoint": f"https://push.example.com/{uuid.uuid4().hex}"})
    assert r.status_code == 200
    r = await client.get("/users/me/push-subscriptions")
    assert r.json()["subscriptions"] == 1


async def test_register_upserts_by_endpoint(client):
    await _signup(client)
    endpoint = f"https://push.example.com/reg/{uuid.uuid4().hex}"
    first = await client.post("/users/me/push-subscriptions", json=SUB | {"endpoint": endpoint})
    again = await client.post("/users/me/push-subscriptions", json={"endpoint": endpoint, "p256dh": "BNewKey", "auth": "rotated"})
    assert first.status_code == again.status_code == 200
    assert first.json()["id"] == again.json()["id"]  # same row, refreshed keys
    async with SessionLocal() as s:
        row = await s.scalar(select(WebPushSubscription).where(WebPushSubscription.endpoint == endpoint))
        assert row.p256dh == "BNewKey" and row.auth == "rotated"


async def test_shared_browser_endpoint_adopted_by_last_account(client):
    endpoint = f"https://push.example.com/reg/{uuid.uuid4().hex}"
    first_addr = await _signup(client, "shared-a")
    await client.post("/users/me/push-subscriptions", json=SUB | {"endpoint": endpoint})
    await _signup(client, "shared-b")  # re-auths the client as a second user
    r = await client.post("/users/me/push-subscriptions", json=SUB | {"endpoint": endpoint})
    assert r.status_code == 200
    async with SessionLocal() as s:
        row = await s.scalar(select(WebPushSubscription).where(WebPushSubscription.endpoint == endpoint))
        owner = await s.get(User, row.user_id)
        assert owner.email != first_addr


async def test_unregister_deletes_own_subscription(client):
    await _signup(client)
    endpoint = f"https://push.example.com/reg/{uuid.uuid4().hex}"
    await client.post("/users/me/push-subscriptions", json=SUB | {"endpoint": endpoint})
    r = await client.delete("/users/me/push-subscriptions", params={"endpoint": endpoint})
    assert r.status_code == 204
    r = await client.get("/users/me/push-subscriptions")
    assert r.json()["subscriptions"] == 0


async def test_dispatch_prefers_web_push_over_email(client, monkeypatch):
    addr = await _signup(client)
    await client.patch("/users/me", json={"email_nudges": True})
    await client.post("/users/me/push-subscriptions", json=SUB | {"endpoint": f"https://push.example.com/{uuid.uuid4().hex}"})
    await _make_due_nudge(addr, title="Prefers browser")
    email_service.sent_outbox.clear()

    calls: list[str] = []

    async def fake_send(db, user, nudge):
        calls.append(user.email)
        return True

    monkeypatch.setattr(nudges.webpush, "send_web_push", fake_send)
    async with SessionLocal() as s:
        sent = await nudges.dispatch_due(s)
    assert sent >= 1 and addr in calls
    assert all(m["to"] != addr for m in email_service.sent_outbox)  # email untouched

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        row = await s.scalar(select(Nudge).where(Nudge.user_id == user.id, Nudge.title == "Prefers browser"))
        assert row.status == "sent" and row.sent_at is not None


async def test_dispatch_falls_back_to_email_without_subscription(client):
    addr = await _signup(client)
    await client.patch("/users/me", json={"email_nudges": True})
    await _make_due_nudge(addr, title="Email fallback")
    email_service.sent_outbox.clear()

    async with SessionLocal() as s:
        await nudges.dispatch_due(s)
    delivered = [m for m in email_service.sent_outbox if m["to"] == addr]
    assert delivered and delivered[-1]["subject"] == "Email fallback"


async def test_send_web_push_logs_when_keyless(client):
    """No VAPID keys (hermetic CI): a subscribed user still resolves True via
    the log-only path — mirrors the APNs dev fallback contract."""
    addr = await _signup(client)
    await client.post("/users/me/push-subscriptions", json=SUB | {"endpoint": f"https://push.example.com/{uuid.uuid4().hex}"})
    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        nudge = Nudge(user_id=user.id, kind="checkin", title="t", body="b", scheduled_for=utcnow())
        assert settings.webpush_enabled is False
        assert await webpush.send_web_push(s, user, nudge) is True


async def test_send_web_push_false_without_subscriptions(client):
    addr = await _signup(client)
    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        nudge = Nudge(user_id=user.id, kind="checkin", title="t", body="b", scheduled_for=utcnow())
        assert await webpush.send_web_push(s, user, nudge) is False


async def test_send_web_push_delivers_with_keys(client, monkeypatch):
    """Keys configured: the encrypted payload goes to every subscription and
    the call resolves True (pywebpush itself is stubbed — no network)."""
    addr = await _signup(client)
    await client.post("/users/me/push-subscriptions", json=SUB | {"endpoint": f"https://push.example.com/{uuid.uuid4().hex}"})

    monkeypatch.setattr(settings, "vapid_public_key", "pub")
    monkeypatch.setattr(settings, "vapid_private_key", "priv")
    sent_payloads: list[dict] = []
    monkeypatch.setattr(webpush, "webpush", lambda **kw: sent_payloads.append(kw))

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        nudge = Nudge(user_id=user.id, kind="checkin", title="Hello", body="b",
                      deeplink="cerebro://mood", scheduled_for=utcnow())
        assert await webpush.send_web_push(s, user, nudge) is True
    assert len(sent_payloads) == 1
    assert '"deeplink": "cerebro://mood"' in sent_payloads[0]["data"]
    assert sent_payloads[0]["vapid_claims"]["sub"] == settings.vapid_subject


async def test_send_web_push_prunes_dead_endpoints(client, monkeypatch):
    """A 410 from the push service deletes the dropped subscription row."""
    addr = await _signup(client)
    endpoint = f"https://push.example.com/reg/{uuid.uuid4().hex}"
    await client.post("/users/me/push-subscriptions", json=SUB | {"endpoint": endpoint})

    monkeypatch.setattr(settings, "vapid_public_key", "pub")
    monkeypatch.setattr(settings, "vapid_private_key", "priv")

    def gone(*args, **kwargs):
        raise pywebpush.WebPushException("gone", response=SimpleNamespace(status_code=410))

    monkeypatch.setattr(webpush, "webpush", gone)
    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        nudge = Nudge(user_id=user.id, kind="checkin", title="t", body="b", scheduled_for=utcnow())
        assert await webpush.send_web_push(s, user, nudge) is False
        await s.commit()
    async with SessionLocal() as s:
        assert await s.scalar(select(WebPushSubscription).where(WebPushSubscription.endpoint == endpoint)) is None


async def test_export_includes_push_subscriptions(client):
    await _signup(client)
    endpoint = f"https://push.example.com/reg/{uuid.uuid4().hex}"
    await client.post("/users/me/push-subscriptions", json=SUB | {"endpoint": endpoint})
    r = await client.get("/users/me/export")
    assert r.status_code == 200
    assert any(s["endpoint"] == endpoint for s in r.json()["push_subscriptions"])
