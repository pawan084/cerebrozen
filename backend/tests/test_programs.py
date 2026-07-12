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
    # Programs without per-day guides simply omit the field (additive contract).
    assert "today_guide" not in active


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


async def test_seeded_sleep_reset_program(client, monkeypatch):
    """The CBT-I-informed "Sleep Reset" 7-day program is seeded (idempotently),
    listed in the public catalogue, and enrollable through the normal flow."""
    from app import seed as seed_mod

    monkeypatch.setattr(seed_mod.settings, "seed_demo_data", True)
    async with SessionLocal() as s:
        await seed_mod.seed(s)
    async with SessionLocal() as s:   # re-boot: additive-by-title, no duplicates
        await seed_mod.seed(s)

    r = await client.get("/content", params={"kind": "program"})
    assert r.status_code == 200
    matches = [row for row in r.json() if row["title"] == "Sleep Reset"]
    assert len(matches) == 1
    reset = matches[0]
    assert reset["premium"] is False and "7-day" in reset["subtitle"]
    # The week guide lives server-side in the narration script (the schema has
    # no per-day program structure); the public catalogue rightly omits it.
    assert "narration_script" not in reset
    async with SessionLocal() as s:
        item = await s.get(ContentItem, uuid.UUID(reset["id"]))
        assert "Day one" in item.narration_script and "Day seven" in item.narration_script

    await _signup(client)
    r = await client.post("/programs/enroll", json={"content_id": reset["id"]})
    assert r.status_code == 201
    p = r.json()["program"]
    assert p["title"] == "Sleep Reset" and p["day"] == 1 and p["days"] == 7


async def test_day_guides_served_per_day_and_clamped(client, monkeypatch):
    """W15 per-day program structure: /programs/active carries `today_guide`
    ({title, body}) for the enrollment's current day, and the day index clamps
    to the guide list when the enrollment runs longer than the guides."""
    from app import seed as seed_mod

    monkeypatch.setattr(seed_mod.settings, "seed_demo_data", True)
    async with SessionLocal() as s:
        await seed_mod.seed(s)

    r = await client.get("/content", params={"kind": "program"})
    reset = next(row for row in r.json() if row["title"] == "Sleep Reset")
    # Public catalogue shape stays lean — guides ride the enrollment payload.
    assert "day_guides" not in reset

    await _signup(client)
    # 10-day enrollment against 7 guides exercises the clamp below.
    await client.post("/programs/enroll", json={"content_id": reset["id"], "days": 10})
    p = (await client.get("/programs/active")).json()["program"]
    assert p["day"] == 1
    assert p["today_guide"]["title"] == "A steady wake time"
    assert "wake time" in p["today_guide"]["body"]

    async with SessionLocal() as s:   # backdate 3 days → day 4 → fourth guide
        await s.execute(update(ProgramEnrollment).values(started_at=utcnow() - timedelta(days=3)))
        await s.commit()
    p = (await client.get("/programs/active")).json()["program"]
    assert p["day"] == 4 and p["today_guide"]["title"] == "The twenty-minute rule"

    async with SessionLocal() as s:   # day 10 of 10, only 7 guides → the last one
        await s.execute(update(ProgramEnrollment).values(started_at=utcnow() - timedelta(days=9)))
        await s.commit()
    p = (await client.get("/programs/active")).json()["program"]
    assert p["day"] == 10 and p["today_guide"]["title"] == "Keeping it"


async def test_day_guides_reseed_backfills_only_null(client, monkeypatch):
    """Re-seeding restores guides where the column is still NULL (pre-existing
    rows) but never clobbers an admin-curated list."""
    from app import seed as seed_mod

    monkeypatch.setattr(seed_mod.settings, "seed_demo_data", True)
    async with SessionLocal() as s:
        await seed_mod.seed(s)

    custom = [{"title": "Admin day", "body": "Curated."}]
    async with SessionLocal() as s:
        row = await s.scalar(select(ContentItem).where(ContentItem.title == "Sleep Reset"))
        row.day_guides = custom
        await s.commit()
    async with SessionLocal() as s:   # re-boot: non-null guides stay untouched
        await seed_mod.seed(s)
    async with SessionLocal() as s:
        row = await s.scalar(select(ContentItem).where(ContentItem.title == "Sleep Reset"))
        assert row.day_guides == custom
        row.day_guides = None          # legacy/NULL row → next boot backfills
        await s.commit()
    async with SessionLocal() as s:
        await seed_mod.seed(s)
    async with SessionLocal() as s:
        row = await s.scalar(select(ContentItem).where(ContentItem.title == "Sleep Reset"))
        assert row.day_guides and len(row.day_guides) == 7
        assert row.day_guides[0]["title"] == "A steady wake time"
