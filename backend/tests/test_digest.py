"""Weekly digest: snapshot, schedule, and the cases where it stays quiet."""
import uuid
from datetime import datetime, timedelta, timezone

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.insight import Insight
from app.models.nudge import Nudge
from app.models.user import User
from app.services import digest


async def _signup(client, prefix="digest"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "D"}
    )
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    # Opt in to the data categories (fresh accounts grant nothing) — the digest
    # reads the same consent-gated sources the weekly insights do, so a user who
    # granted nothing is a quiet week by definition.
    r = await client.patch("/users/me/consent", json={
        "mood_history": True, "journal_memory": True, "sleep_history": True})
    assert r.status_code == 200
    return addr


async def _user(addr: str) -> User:
    async with SessionLocal() as s:
        return await s.scalar(select(User).where(User.email == addr))


def test_week_key_is_iso_year_week():
    assert digest.week_key(datetime(2026, 1, 1, tzinfo=timezone.utc)) == "2026-W01"
    # An ISO week can belong to the neighbouring calendar year.
    assert digest.week_key(datetime(2026, 12, 31, tzinfo=timezone.utc)).endswith("W53")


async def test_quiet_week_is_not_sent(client):
    """A user with no activity gets silence — a digest of nothing is a nag."""
    addr = await _signup(client)
    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        queued = await digest.schedule_digest(s, user)
        await s.commit()
    assert queued is None


async def test_active_week_snapshots_and_schedules(client):
    addr = await _signup(client)
    await client.post("/moods", json={"mood": "Anxious", "intensity": 4})
    await client.post("/journal", json={"title": "t", "body": "a bit stressed", "tags": []})

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        nudge = await digest.schedule_digest(s, user)
        await s.commit()
        assert nudge is not None
        assert nudge.kind == "weekly_digest"
        assert nudge.deeplink == "cerebro://insights"
        # Delivery lands in the future, never immediately on generation.
        assert nudge.scheduled_for > datetime.now(timezone.utc)

        rows = (await s.scalars(select(Insight).where(Insight.user_id == user.id))).all()
        assert len(rows) == 1
        assert rows[0].period == digest.week_key(datetime.now(timezone.utc))


async def test_snapshot_is_idempotent_per_week(client):
    """Running the pass repeatedly (every dispatcher tick) must not duplicate."""
    addr = await _signup(client)
    await client.post("/moods", json={"mood": "Low", "intensity": 3})

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        for _ in range(3):
            await digest.schedule_digest(s, user)
        await s.commit()
        insights = (await s.scalars(select(Insight).where(Insight.user_id == user.id))).all()
        nudges = (await s.scalars(
            select(Nudge).where(Nudge.user_id == user.id, Nudge.kind == "weekly_digest")
        )).all()
    assert len(insights) == 1
    assert len(nudges) == 1


async def test_snapshot_is_frozen_not_recomputed(client):
    """History has to say what the user was told, not what is true now."""
    addr = await _signup(client)
    await client.post("/moods", json={"mood": "Anxious", "intensity": 4})

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        first = await digest.snapshot_week(s, user)
        original = first.headline
        await s.commit()

    # More activity would change a live computation.
    for _ in range(3):
        await client.post("/moods", json={"mood": "Calm", "intensity": 1})

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        again = await digest.snapshot_week(s, user)
        assert again.headline == original


async def test_digest_endpoints_expose_latest_and_history(client):
    addr = await _signup(client)
    await client.post("/moods", json={"mood": "Tired", "intensity": 3})

    # Nothing until a pass has run.
    assert (await client.get("/insights/digest")).json() is None

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        await digest.snapshot_week(s, user)
        await s.commit()

    latest = (await client.get("/insights/digest")).json()
    assert latest is not None and latest["headline"]
    history = (await client.get("/insights/digest/history")).json()
    assert len(history) == 1


async def test_weekly_pass_skips_quiet_users_and_counts_the_rest(client):
    quiet = await _signup(client, "quiet")
    active = await _signup(client, "active")
    await client.post("/moods", json={"mood": "Anxious", "intensity": 4})

    async with SessionLocal() as s:
        queued = await digest.run_weekly_pass(s)
    assert queued >= 1

    async with SessionLocal() as s:
        quiet_user = await s.scalar(select(User).where(User.email == quiet))
        rows = (await s.scalars(
            select(Nudge).where(Nudge.user_id == quiet_user.id, Nudge.kind == "weekly_digest")
        )).all()
    assert rows == []


async def test_next_digest_time_is_a_monday_morning(client):
    addr = await _signup(client)
    user = await _user(addr)
    # A Wednesday.
    now = datetime(2026, 7, 29, 15, 0, tzinfo=timezone.utc)
    when = digest._next_digest_time(user, now)
    assert when.weekday() == digest.DIGEST_WEEKDAY
    assert when > now
    assert when - now < timedelta(days=7)


async def test_wipe_and_export_cover_digest_snapshots(client):
    """Snapshots are `Insight` rows — already wired into both, but a digest
    makes them real for the first time, so pin the behaviour."""
    addr = await _signup(client)
    await client.post("/moods", json={"mood": "Low", "intensity": 4})
    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        await digest.snapshot_week(s, user)
        await s.commit()

    assert len((await client.get("/users/me/export")).json()["insights"]) == 1
    assert (await client.delete("/users/me/memory")).json()["insights"] == 1
    assert (await client.get("/insights/digest")).json() is None
