"""Crisis escalation + trusted-contact CRUD."""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.safety import SafetyEvent
from app.services import email as email_service


async def _crisis_message(client):
    return await client.post("/chat/messages", json={"text": "I want to kill myself tonight."})


async def test_trusted_contact_crud(auth_client):
    # None initially.
    assert (await auth_client.get("/users/me/trusted-contact")).json() is None
    # Upsert.
    r = await auth_client.put("/users/me/trusted-contact", json={
        "name": "Alex", "method": "email", "value": "alex@example.org",
        "relationship": "Sister", "notify_consent": True})
    assert r.status_code == 200
    body = r.json()
    assert body["name"] == "Alex" and body["notify_consent"] is True
    # Update in place (still one row).
    r = await auth_client.put("/users/me/trusted-contact", json={
        "name": "Alex R", "method": "email", "value": "alex@example.org",
        "relationship": "Sister", "notify_consent": False})
    assert r.json()["name"] == "Alex R" and r.json()["notify_consent"] is False
    # Delete.
    assert (await auth_client.delete("/users/me/trusted-contact")).status_code == 204
    assert (await auth_client.get("/users/me/trusted-contact")).json() is None


async def test_crisis_escalates_to_consented_contact(auth_client):
    await auth_client.put("/users/me/trusted-contact", json={
        "name": "Sam", "method": "email", "value": "sam@example.org",
        "relationship": "Friend", "notify_consent": True})
    email_service.sent_outbox.clear()

    r = await _crisis_message(auth_client)
    assert r.status_code == 201

    # The contact was emailed and the event marked escalated.
    assert any(m["to"] == "sam@example.org" for m in email_service.sent_outbox)
    async with SessionLocal() as s:
        events = (await s.scalars(select(SafetyEvent).where(SafetyEvent.risk_level == "crisis"))).all()
    assert any(e.escalated for e in events)


async def test_crisis_without_consent_does_not_notify(auth_client):
    await auth_client.put("/users/me/trusted-contact", json={
        "name": "Nope", "method": "email", "value": "nope@example.org",
        "relationship": "Friend", "notify_consent": False})
    email_service.sent_outbox.clear()

    await _crisis_message(auth_client)
    assert not any(m["to"] == "nope@example.org" for m in email_service.sent_outbox)
