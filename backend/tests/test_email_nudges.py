"""Email delivery of due nudges for web-only users (users.email_nudges)."""
import uuid
from datetime import timedelta

from sqlalchemy import select

from app.core.database import SessionLocal, utcnow
from app.models.nudge import Nudge
from app.models.user import User
from app.services import email as email_service
from app.services import nudges


async def _web_user(client, *, email_nudges: bool):
    addr = f"webnudge-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "W"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    r = await client.patch("/users/me", json={"email_nudges": email_nudges})
    assert r.status_code == 200 and r.json()["email_nudges"] is email_nudges
    return addr


async def _make_due_nudge(addr: str):
    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        s.add(Nudge(user_id=user.id, kind="announcement", title="Round-4 email nudge",
                    body="One small step today.", scheduled_for=utcnow() - timedelta(minutes=1)))
        await s.commit()


async def test_due_nudge_emailed_when_opted_in(client):
    addr = await _web_user(client, email_nudges=True)
    await _make_due_nudge(addr)
    email_service.sent_outbox.clear()

    async with SessionLocal() as s:
        sent = await nudges.dispatch_due(s)
    assert sent >= 1
    delivered = [m for m in email_service.sent_outbox if m["to"] == addr]
    assert delivered and delivered[-1]["subject"] == "Round-4 email nudge"

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        row = await s.scalar(select(Nudge).where(Nudge.user_id == user.id,
                                                 Nudge.title == "Round-4 email nudge"))
        assert row.status == "sent" and row.sent_at is not None


async def test_due_nudge_skipped_without_opt_in(client):
    addr = await _web_user(client, email_nudges=False)
    await _make_due_nudge(addr)
    email_service.sent_outbox.clear()

    async with SessionLocal() as s:
        await nudges.dispatch_due(s)
    assert all(m["to"] != addr for m in email_service.sent_outbox)

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        row = await s.scalar(select(Nudge).where(Nudge.user_id == user.id,
                                                 Nudge.title == "Round-4 email nudge"))
        assert row.status == "skipped"
