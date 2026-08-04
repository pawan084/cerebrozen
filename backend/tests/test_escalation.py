"""Crisis escalation + trusted-contact CRUD."""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.safety import SafetyEvent
from app.services import email as email_service
from app.services import sms as sms_service


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


async def test_trusted_contact_value_must_match_its_method(auth_client):
    """The one contact the product may use in someone's worst moment cannot be
    a typo: found on-device 2026-08-03, when an adb-mangled
    "sister%40example.com" saved without complaint and would have failed
    silently at escalation time."""
    # A mangled email is refused, with the reason in the response.
    r = await auth_client.put("/users/me/trusted-contact", json={
        "name": "Sis", "method": "email", "value": "sister%40example.com",
        "relationship": "Sister", "notify_consent": True})
    assert r.status_code == 422
    assert "email" in str(r.json()["detail"]).lower()

    # A non-numeric phone value is refused.
    r = await auth_client.put("/users/me/trusted-contact", json={
        "name": "Pat", "method": "sms", "value": "call me maybe",
        "relationship": "Parent", "notify_consent": True})
    assert r.status_code == 422

    # An unknown method is refused (was free text up to 20 chars).
    r = await auth_client.put("/users/me/trusted-contact", json={
        "name": "X", "method": "carrier-pigeon", "value": "coop 7",
        "relationship": "", "notify_consent": False})
    assert r.status_code == 422

    # Consent to contact nobody cannot be switched on.
    r = await auth_client.put("/users/me/trusted-contact", json={
        "name": "Draft", "method": "email", "value": "",
        "relationship": "", "notify_consent": True})
    assert r.status_code == 422

    # But an empty draft with consent off is fine, and valid values still save.
    r = await auth_client.put("/users/me/trusted-contact", json={
        "name": "Draft", "method": "email", "value": "",
        "relationship": "", "notify_consent": False})
    assert r.status_code == 200
    r = await auth_client.put("/users/me/trusted-contact", json={
        "name": "Pat", "method": "phone", "value": "+91 (555) 000-1111",
        "relationship": "Parent", "notify_consent": True})
    assert r.status_code == 200
    assert r.json()["value"] == "+91 (555) 000-1111"


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


async def test_crisis_notifies_sms_contact(auth_client):
    await auth_client.put("/users/me/trusted-contact", json={
        "name": "Pat", "method": "sms", "value": "+15550001111",
        "relationship": "Parent", "notify_consent": True})
    sms_service.sent_outbox.clear()

    r = await _crisis_message(auth_client)
    assert r.status_code == 201
    assert any(m["to"] == "+15550001111" for m in sms_service.sent_outbox)


async def test_crisis_without_consent_does_not_notify(auth_client):
    await auth_client.put("/users/me/trusted-contact", json={
        "name": "Nope", "method": "email", "value": "nope@example.org",
        "relationship": "Friend", "notify_consent": False})
    email_service.sent_outbox.clear()

    await _crisis_message(auth_client)
    assert not any(m["to"] == "nope@example.org" for m in email_service.sent_outbox)
