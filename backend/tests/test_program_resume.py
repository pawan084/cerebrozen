"""Leaving a program must not forfeit the week.

Leaving flips `active` and keeps `started_at`; enrolling used to always mint a
fresh row, so one unconfirmed tap on "Leave this journey" reset day 5 of 7 back
to day 1 — with the real start date still sitting in the table. Nobody who taps
away from a wind-down program on a bad evening means to start over.
"""
import uuid
from datetime import timedelta

from sqlalchemy import select

from app.core.database import SessionLocal, utcnow
from app.models.content import ContentItem
from app.models.program import ProgramEnrollment
from app.models.user import User


async def _signup(client):
    addr = f"resume-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "R"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def _programs(count: int = 1) -> list[str]:
    """Program ids, seeding them if this test DB has none (same trick as
    test_programs.py — the hermetic DB does not run app.seed)."""
    async with SessionLocal() as s:
        rows = (await s.scalars(select(ContentItem).where(ContentItem.kind == "program"))).all()
        while len(rows) < count:
            item = ContentItem(
                title=f"Test journey {uuid.uuid4().hex[:6]}",
                subtitle="7 days", kind="program", symbol="leaf",
            )
            s.add(item)
            await s.commit()
            rows = (await s.scalars(select(ContentItem).where(ContentItem.kind == "program"))).all()
        return [str(r.id) for r in rows[:count]]


async def _a_program() -> str:
    return (await _programs(1))[0]


async def _backdate(email: str, days: int) -> None:
    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == email))
        e = await s.scalar(
            select(ProgramEnrollment)
            .where(ProgramEnrollment.user_id == user.id)
            .order_by(ProgramEnrollment.started_at.desc())
        )
        e.started_at = utcnow() - timedelta(days=days)
        await s.commit()


async def test_rejoining_resumes_the_day_you_were_on(client):
    email = await _signup(client)
    cid = await _a_program()
    await client.post("/programs/enroll", json={"content_id": cid})
    await _backdate(email, 4)
    assert (await client.get("/programs/active")).json()["program"]["day"] == 5

    await client.delete("/programs/active")
    assert (await client.get("/programs/active")).json()["program"] is None

    again = (await client.post("/programs/enroll", json={"content_id": cid})).json()["program"]
    assert again["day"] == 5, "leaving and returning must not forfeit the week"


async def test_a_long_abandoned_program_starts_over(client):
    """Resuming at 'day 92 of 7' — clamped to the last day and instantly
    complete — would be its own lie."""
    email = await _signup(client)
    cid = await _a_program()
    await client.post("/programs/enroll", json={"content_id": cid})
    await _backdate(email, 90)
    await client.delete("/programs/active")

    again = (await client.post("/programs/enroll", json={"content_id": cid})).json()["program"]
    assert again["day"] == 1
    assert again["completed"] is False


async def test_a_different_program_is_always_a_fresh_start(client):
    email = await _signup(client)
    first, second = await _programs(2)

    await client.post("/programs/enroll", json={"content_id": first})
    await _backdate(email, 3)
    await client.delete("/programs/active")

    other = (await client.post("/programs/enroll", json={"content_id": second})).json()["program"]
    assert other["day"] == 1, "another program must not inherit the first one's position"
