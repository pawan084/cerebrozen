"""Program enrollment: enroll/replace/day-computation/completion/leave."""
import uuid
from datetime import timedelta

from sqlalchemy import select, update

from app.core.database import SessionLocal, utcnow
from app.models.content import ContentItem
from app.models.program import ProgramEnrollment


async def _signup(client):
    addr = f"programs-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "P"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def _program_ids(client) -> list[str]:
    async with SessionLocal() as s:
        rows = (await s.scalars(select(ContentItem).where(ContentItem.kind == "program"))).all()
        if not rows:   # fresh test DB: seed two programs directly
            rows = [
                ContentItem(title=f"Test journey {uuid.uuid4().hex[:4]}", subtitle="7 days", kind="program"),
                ContentItem(title=f"Second journey {uuid.uuid4().hex[:4]}", subtitle="7 days", kind="program"),
            ]
            s.add_all(rows)
            await s.commit()
            for r in rows:
                await s.refresh(r)
        return [str(r.id) for r in rows[:2]]


async def test_enroll_active_and_replace(client):
    await _signup(client)
    ids = await _program_ids(client)

    assert (await client.get("/programs/active")).json()["program"] is None

    r = await client.post("/programs/enroll", json={"content_id": ids[0]})
    assert r.status_code == 201
    p = r.json()["program"]
    assert p["day"] == 1 and p["days"] == 7 and p["completed"] is False

    # Enrolling in another journey gently replaces the first.
    r = await client.post("/programs/enroll", json={"content_id": ids[1], "days": 10})
    assert r.status_code == 201 and r.json()["program"]["days"] == 10
    active = (await client.get("/programs/active")).json()["program"]
    assert active["content_id"] == ids[1]


async def test_day_is_computed_from_start_date(client):
    await _signup(client)
    ids = await _program_ids(client)
    await client.post("/programs/enroll", json={"content_id": ids[0]})
    async with SessionLocal() as s:   # backdate the start by 2 days → day 3
        await s.execute(update(ProgramEnrollment).values(started_at=utcnow() - timedelta(days=2)))
        await s.commit()
    p = (await client.get("/programs/active")).json()["program"]
    assert p["day"] == 3 and p["completed"] is False

    async with SessionLocal() as s:   # backdate past the length → capped + completed
        await s.execute(update(ProgramEnrollment).values(started_at=utcnow() - timedelta(days=9)))
        await s.commit()
    p = (await client.get("/programs/active")).json()["program"]
    assert p["day"] == 7 and p["completed"] is True


async def test_leave_and_bad_content(client):
    await _signup(client)
    ids = await _program_ids(client)
    await client.post("/programs/enroll", json={"content_id": ids[0]})
    r = await client.delete("/programs/active")
    assert r.status_code == 200 and r.json()["program"] is None
    assert (await client.get("/programs/active")).json()["program"] is None
    # Non-program content refuses.
    async with SessionLocal() as s:
        sound = ContentItem(title=f"Not a program {uuid.uuid4().hex[:4]}", kind="soundscape")
        s.add(sound); await s.commit(); await s.refresh(sound)
    assert (await client.post("/programs/enroll", json={"content_id": str(sound.id)})).status_code == 404
