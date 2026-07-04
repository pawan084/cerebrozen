"""DPDP: itemized consent enforcement + Rule 8(3) deletion ledger + export parity."""
import hashlib
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.deletion_ledger import DeletionLedger
from app.models.user import User
from app.services import agentic, insights


async def _signup(client):
    email = f"dpdp-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": email, "password": "password123", "name": "T"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return email


async def _seed_all_categories(client):
    assert (await client.post("/moods", json={
        "mood": "Anxious", "note": "", "symbol": "wind", "intensity": 4})).status_code == 201
    assert (await client.post("/journal", json={
        "title": "Meeting worry", "body": "private thoughts", "tags": [], "symbol": "book"})).status_code == 201
    assert (await client.post("/sleep", json={
        "date": "2026-07-03", "bedtime": "23:00", "wake_time": "07:00",
        "quality": 4, "awakenings": 0})).status_code in (200, 201)


async def _load_user(email: str, db) -> User:
    user = await db.scalar(select(User).where(User.email == email))
    assert user is not None
    return user


async def test_consent_new_flags_roundtrip(client):
    await _signup(client)
    body = (await client.get("/users/me/consent")).json()
    assert body["journal_memory"] is True and body["sleep_history"] is True
    r = await client.patch("/users/me/consent",
                           json={"journal_memory": False, "sleep_history": False})
    assert r.status_code == 200
    body = r.json()
    assert body["journal_memory"] is False and body["sleep_history"] is False
    # Untouched flags keep their values.
    assert body["ai_memory"] is True


async def test_plan_signals_respect_itemized_consent(client):
    email = await _signup(client)
    await _seed_all_categories(client)

    async with SessionLocal() as db:
        user = await _load_user(email, db)
        moods, journals, sleep_rows = await agentic._recent_signals(db, user)
        assert moods and journals and sleep_rows

    r = await client.patch("/users/me/consent", json={
        "mood_history": False, "journal_memory": False, "sleep_history": False})
    assert r.status_code == 200

    async with SessionLocal() as db:
        user = await _load_user(email, db)
        moods, journals, sleep_rows = await agentic._recent_signals(db, user)
        assert moods == [] and journals == [] and sleep_rows == []


async def test_weekly_insights_respect_itemized_consent(client):
    email = await _signup(client)
    await _seed_all_categories(client)
    await client.patch("/users/me/consent", json={
        "mood_history": False, "journal_memory": False, "sleep_history": False})

    async with SessionLocal() as db:
        user = await _load_user(email, db)
        weekly = await insights.compute_weekly(db, user)
    by_label = {m["label"]: m["value"] for m in weekly["metrics"]}
    assert by_label["Journal entries"] == "0"
    assert by_label["Sleep"] == "No diary yet"
    assert by_label["Mood stability"] == "Steady"   # neutral default, not derived


async def test_delete_account_writes_minimal_ledger(client):
    email = await _signup(client)
    assert (await client.delete("/users/me")).status_code == 204

    expected_hash = hashlib.sha256(email.lower().encode()).hexdigest()
    async with SessionLocal() as db:
        row = await db.scalar(select(DeletionLedger).where(DeletionLedger.email_hash == expected_hash))
        assert row is not None
        assert row.reason == "user_request"
        assert row.account_created_at is not None
        # The account itself is gone — only the hashed trace survives.
        assert (await db.scalar(select(User).where(User.email == email))) is None


async def test_export_includes_sleep_diary(client):
    await _signup(client)
    await _seed_all_categories(client)
    export = (await client.get("/users/me/export")).json()
    assert "sleep" in export and len(export["sleep"]) == 1
    assert export["sleep"][0]["quality"] == 4
