"""A check-in is one tap, so a mis-tap is one tap too.

Until this existed there was no way back: a journal entry or a remembered note
could be deleted, but a mood logged by accident was permanent — and it stayed in
the 60-day window that patterns and the weekly read compute from, quietly
skewing both.
"""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.mood import MoodLog
from app.models.user import User


async def _signup(client, prefix="mood"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "M"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def test_a_mistapped_check_in_can_be_taken_back(client):
    await _signup(client)
    created = (await client.post(
        "/moods", json={"mood": "Anxious", "note": "", "symbol": "x", "intensity": 4}
    )).json()

    r = await client.delete(f"/moods/{created['id']}")
    assert r.status_code == 204
    assert (await client.get("/moods")).json() == []


async def test_deleting_twice_is_not_a_server_error(client):
    await _signup(client)
    created = (await client.post(
        "/moods", json={"mood": "Good", "note": "", "symbol": "x", "intensity": 2}
    )).json()
    assert (await client.delete(f"/moods/{created['id']}")).status_code == 204
    assert (await client.delete(f"/moods/{created['id']}")).status_code == 404


async def test_nobody_can_delete_someone_elses_check_in(client):
    """The row is addressable, so ownership has to be enforced, not assumed."""
    await _signup(client, "owner")
    mine = (await client.post(
        "/moods", json={"mood": "Low", "note": "", "symbol": "x", "intensity": 4}
    )).json()

    await _signup(client, "attacker")
    assert (await client.delete(f"/moods/{mine['id']}")).status_code == 404

    async with SessionLocal() as s:
        assert await s.scalar(select(MoodLog).where(MoodLog.id == uuid.UUID(mine["id"]))) is not None
