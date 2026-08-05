"""Wave 20 pins: every write path scans, and risk always answers with a path.

Register C68-C71, C75 — the safety pipeline reaches the writes it used to
skip, the admin queue can point at the message behind a flag, a risky journal
POST carries hotlines like /chat and /oracle always did, and the export is as
complete as it claims.
"""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.safety import SafetyEvent

CRISIS_TEXT = "I want to end my life tonight"


async def _user_id(client) -> str:
    return (await client.get("/users/me")).json()["id"]


async def test_risky_journal_post_carries_resources(auth_client):
    r = await auth_client.post("/journal", json={"title": "dark", "body": CRISIS_TEXT})
    assert r.status_code == 201
    body = r.json()
    assert body["risk_level"] in {"elevated", "crisis"}
    assert body["resources"], "a risky journal write must answer with hotlines"
    assert body["resources"].get("lines"), body["resources"]
    # A calm entry carries none — the field is risk-only.
    calm = await auth_client.post("/journal", json={"title": "walk", "body": "a nice walk today"})
    assert calm.json()["resources"] is None


async def test_chat_safety_event_points_at_its_message(auth_client):
    uid = await _user_id(auth_client)
    r = await auth_client.post("/chat/messages", json={"text": CRISIS_TEXT})
    assert r.status_code == 201
    msg_id = r.json()["user_message"]["id"]
    async with SessionLocal() as s:
        event = await s.scalar(
            select(SafetyEvent)
            .where(SafetyEvent.user_id == uuid.UUID(uid), SafetyEvent.source == "chat")
            .order_by(SafetyEvent.created_at.desc())
        )
    assert event is not None
    assert str(event.source_id) == msg_id


async def test_goal_why_is_scanned(auth_client):
    uid = await _user_id(auth_client)
    r = await auth_client.post("/goals", json={"title": "hold on", "why": CRISIS_TEXT})
    assert r.status_code == 201, r.text
    async with SessionLocal() as s:
        event = await s.scalar(
            select(SafetyEvent).where(
                SafetyEvent.user_id == uuid.UUID(uid), SafetyEvent.source == "goal"
            )
        )
    assert event is not None and event.source_id is not None


async def test_memory_body_is_scanned(auth_client):
    uid = await _user_id(auth_client)
    r = await auth_client.post("/users/me/memory", json={"body": CRISIS_TEXT})
    assert r.status_code == 201, r.text
    async with SessionLocal() as s:
        event = await s.scalar(
            select(SafetyEvent).where(
                SafetyEvent.user_id == uuid.UUID(uid), SafetyEvent.source == "memory"
            )
        )
    assert event is not None


async def test_export_is_as_complete_as_it_claims(auth_client):
    # Seed one row in each newly-exported table, then look for it.
    h = await auth_client.post("/habits", json={"title": "evening walk"})
    assert h.status_code == 201, h.text
    await auth_client.post(f"/habits/{h.json()['id']}/complete")
    await auth_client.post("/goals", json={"title": "sleep more", "why": "rest"})
    await auth_client.put(
        "/users/me/trusted-contact",
        json={"name": "Sis", "method": "email", "value": "sis@example.com"},
    )
    export = (await auth_client.get("/users/me/export")).json()
    for key in (
        "habits", "habit_completions", "program_enrollments",
        "intervention_recommendations", "recommendations", "devices",
        "trusted_contact", "safety_events",
    ):
        assert key in export, f"export missing {key}"
    assert any(row["title"] == "evening walk" for row in export["habits"])
    assert export["habit_completions"], "the completion should export"
    assert export["trusted_contact"]["name"] == "Sis"
