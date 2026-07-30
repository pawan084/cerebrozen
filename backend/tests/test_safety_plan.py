"""Personal safety plan: user-authored, versioned, and never a gate.

These tests are written against the three promises in the router docstring,
because those are the things that would actually hurt someone if they broke.
"""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.safety import SafetyEvent
from app.models.safety_plan import SafetyPlan
from app.models.user import User


async def _signup(client, prefix="plan"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "S"}
    )
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def _user_id(addr: str) -> uuid.UUID:
    async with SessionLocal() as s:
        return (await s.scalar(select(User).where(User.email == addr))).id


async def test_no_plan_is_null_not_an_error(client):
    """Having no plan is the normal starting state; clients must stay usable."""
    await _signup(client)
    r = await client.get("/safety-plan/me")
    assert r.status_code == 200
    assert r.json() is None


async def test_first_save_creates_version_one(client):
    await _signup(client)
    r = await client.put(
        "/safety-plan/me",
        json={"warning_signs": "Skipping meals, snapping at people"},
    )
    assert r.status_code == 200
    assert r.json()["version"] == 1
    assert r.json()["warning_signs"] == "Skipping meals, snapping at people"
    # Untouched sections come back empty, not missing.
    assert r.json()["means_safety"] == ""


async def test_partial_saves_merge_instead_of_blanking(client):
    """The guided flow saves one section at a time — an unset field must not
    wipe what the user already wrote."""
    await _signup(client)
    await client.put("/safety-plan/me", json={"warning_signs": "Not sleeping"})
    r = await client.put("/safety-plan/me", json={"internal_coping": "Walk, shower, cold water"})

    assert r.json()["warning_signs"] == "Not sleeping"
    assert r.json()["internal_coping"] == "Walk, shower, cold water"


async def test_editing_archives_rather_than_overwrites(client):
    """Someone editing while distressed must not lose what they wrote while well."""
    await _signup(client)
    await client.put("/safety-plan/me", json={"social_support": "Call Meera"})
    await client.put("/safety-plan/me", json={"social_support": "Call Meera or Sam"})

    live = (await client.get("/safety-plan/me")).json()
    assert live["version"] == 2
    assert live["social_support"] == "Call Meera or Sam"

    history = (await client.get("/safety-plan/me/history")).json()
    assert [h["version"] for h in history] == [2, 1]
    assert history[1]["social_support"] == "Call Meera"
    assert history[1]["archived_at"] is not None
    assert history[0]["archived_at"] is None


async def test_identical_save_does_not_spawn_a_version(client):
    """A guided flow re-saves often; version numbers should mean something."""
    await _signup(client)
    await client.put("/safety-plan/me", json={"professionals": "Tele-MANAS 14416"})
    again = await client.put("/safety-plan/me", json={"professionals": "Tele-MANAS 14416"})

    assert again.json()["version"] == 1
    assert len((await client.get("/safety-plan/me/history")).json()) == 1


async def test_delete_removes_every_version(client):
    """This is the user's own crisis material — "delete" has to mean it."""
    addr = await _signup(client)
    uid = await _user_id(addr)
    await client.put("/safety-plan/me", json={"notes": "v1"})
    await client.put("/safety-plan/me", json={"notes": "v2"})

    assert (await client.delete("/safety-plan/me")).status_code == 204
    assert (await client.get("/safety-plan/me")).json() is None
    assert (await client.get("/safety-plan/me/history")).json() == []
    async with SessionLocal() as s:
        rows = (await s.scalars(select(SafetyPlan).where(SafetyPlan.user_id == uid))).all()
    assert rows == []
    # Idempotent.
    assert (await client.delete("/safety-plan/me")).status_code == 204


async def test_one_account_cannot_see_anothers_plan(client):
    await _signup(client, "owner")
    await client.put("/safety-plan/me", json={"means_safety": "Gave the spare keys to Ravi"})

    await _signup(client, "stranger")
    assert (await client.get("/safety-plan/me")).json() is None
    assert (await client.get("/safety-plan/me/history")).json() == []


async def test_crisis_reply_is_unchanged_with_and_without_a_plan(client):
    """Rule: safety never blocks. A plan is an aid the user prepared, never a
    precondition for the crisis path doing its job."""
    addr = await _signup(client)
    uid = await _user_id(addr)
    msg = {"text": "I want to kill myself tonight."}

    async def crisis_events() -> int:
        async with SessionLocal() as s:
            rows = (await s.scalars(
                select(SafetyEvent).where(
                    SafetyEvent.user_id == uid, SafetyEvent.risk_level == "crisis"
                )
            )).all()
            return len(rows)

    without = await client.post("/chat/messages", json=msg)
    assert without.status_code == 201
    baseline_reply = without.json()["reply"]["text"]
    assert await crisis_events() == 1
    # The reply carries real, external help — not a referral to the user's own
    # document. (Region-agnostic: an account with no region gets the
    # international fallback, so assert on the shape, not on one country's line.)
    assert "helpline" in baseline_reply.lower() or "emergency" in baseline_reply.lower()

    await client.put("/safety-plan/me", json={"warning_signs": "anything"})
    with_plan = await client.post("/chat/messages", json=msg)
    assert with_plan.status_code == 201

    # Same flagging, same resources: having a plan neither suppresses the
    # crisis path nor substitutes for it.
    assert await crisis_events() == 2
    assert with_plan.json()["reply"]["text"] == baseline_reply


async def test_an_empty_plan_is_treated_as_no_plan(client):
    """A half-started plan must not read as "this user has a plan" anywhere."""
    await _signup(client)
    r = await client.put("/safety-plan/me", json={"notes": "   "})
    assert r.status_code == 200

    async with SessionLocal() as s:
        row = await s.scalar(select(SafetyPlan).where(SafetyPlan.id == uuid.UUID(r.json()["id"])))
        assert row.is_empty is True


async def test_export_carries_every_version(client):
    await _signup(client)
    await client.put("/safety-plan/me", json={"notes": "first"})
    await client.put("/safety-plan/me", json={"notes": "second"})

    body = (await client.get("/users/me/export")).json()
    assert [p["notes"] for p in body["safety_plans"]] == ["first", "second"]


async def test_account_delete_cascades_the_plan(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    await client.put("/safety-plan/me", json={"notes": "goes with the account"})

    assert (await client.delete("/users/me")).status_code == 204
    async with SessionLocal() as s:
        rows = (await s.scalars(select(SafetyPlan).where(SafetyPlan.user_id == uid))).all()
    assert rows == []
