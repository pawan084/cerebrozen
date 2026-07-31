"""Patterns become actionable — with the limits that make that safe."""
import uuid
from datetime import timedelta

from sqlalchemy import select

from app.core.database import SessionLocal, utcnow
from app.models.mood import MoodLog
from app.models.recommendation import PracticeCatalog, Recommendation
from app.models.user import User
from app.services import recommendations as service


async def _signup(client, prefix="rec"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "R"}
    )
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    await client.patch("/users/me/consent", json={"mood_history": True})
    return addr


async def _user_id(addr: str) -> uuid.UUID:
    async with SessionLocal() as s:
        return (await s.scalar(select(User).where(User.email == addr))).id


async def _catalogue():
    """The seeder runs at boot; tests get their own copy of the rows."""
    async with SessionLocal() as s:
        have = set((await s.scalars(select(PracticeCatalog.slug))).all())
        for slug in ("anchor-the-hard-hour", "keep-the-journal-streak",
                     "protect-the-wind-down", "one-weekend-anchor"):
            if slug not in have:
                s.add(PracticeCatalog(slug=slug, title=f"T {slug}", body="B", action="plan"))
        await s.commit()


async def _seed_morning_pattern(uid: uuid.UUID):
    """Enough difficult morning check-ins to trip the hardest-time-of-day rule."""
    async with SessionLocal() as s:
        base = utcnow() - timedelta(days=5)
        for i in range(8):
            s.add(MoodLog(
                user_id=uid, mood="anxious", note="", symbol="cloud", intensity=5,
                created_at=base.replace(hour=8) + timedelta(days=i % 5),
            ))
        await s.commit()


def test_rule_matching_is_on_a_fragment_not_the_whole_sentence():
    """Rewording the pattern copy must not silently switch the engine off."""
    assert service.practice_for("Mornings tend to be your hardest time of day.") == "anchor-the-hard-hour"
    assert service.practice_for("EVENINGS TEND TO BE YOUR HARDEST TIME OF DAY!") == "anchor-the-hard-hour"
    assert service.practice_for("Something else entirely.") is None


async def test_thin_data_gets_no_recommendations(client):
    """The miner returns nothing below its thresholds, so this must too —
    generic filler is exactly what a thin-data user should not get."""
    await _catalogue()
    await _signup(client)
    assert (await client.get("/recommendations/mine")).json() == []


async def test_a_real_pattern_produces_a_suggestion_with_its_reason(client):
    await _catalogue()
    addr = await _signup(client)
    await _seed_morning_pattern(await _user_id(addr))

    rows = (await client.get("/recommendations/mine")).json()
    assert len(rows) >= 1
    rec = rows[0]
    assert rec["title"]
    # The reason is the pattern statement, verbatim — never omitted.
    assert "hardest time of day" in rec["reason"].lower()
    assert rec["status"] == "pending"


async def test_dismissing_sticks(client):
    """"Not for me" must not mean "ask me tomorrow"."""
    await _catalogue()
    addr = await _signup(client)
    await _seed_morning_pattern(await _user_id(addr))

    rec = (await client.get("/recommendations/mine")).json()[0]
    dismissed = await client.post(f"/recommendations/{rec['id']}/dismiss")
    assert dismissed.status_code == 200
    assert dismissed.json()["status"] == "dismissed"

    # Re-reading re-seeds; the dismissed practice must not come back.
    again = (await client.get("/recommendations/mine")).json()
    assert all(r["slug"] != rec["slug"] for r in again)


async def test_accepting_removes_it_from_pending(client):
    await _catalogue()
    addr = await _signup(client)
    await _seed_morning_pattern(await _user_id(addr))

    rec = (await client.get("/recommendations/mine")).json()[0]
    accepted = await client.post(f"/recommendations/{rec['id']}/accept")
    assert accepted.json()["status"] == "accepted"
    assert all(r["id"] != rec["id"] for r in (await client.get("/recommendations/mine")).json())


async def test_another_users_recommendation_is_404(client):
    await _catalogue()
    addr = await _signup(client, "owner")
    await _seed_morning_pattern(await _user_id(addr))
    rec = (await client.get("/recommendations/mine")).json()[0]

    await _signup(client, "stranger")
    assert (await client.post(f"/recommendations/{rec['id']}/accept")).status_code == 404
    assert (await client.post(f"/recommendations/{rec['id']}/dismiss")).status_code == 404


async def test_a_retired_practice_is_dropped_not_shown_as_a_slug(client):
    await _catalogue()
    addr = await _signup(client)
    uid = await _user_id(addr)
    await _seed_morning_pattern(uid)
    await client.get("/recommendations/mine")

    async with SessionLocal() as s:
        row = await s.scalar(
            select(PracticeCatalog).where(PracticeCatalog.slug == "anchor-the-hard-hour")
        )
        await s.delete(row)
        await s.commit()

    rows = (await client.get("/recommendations/mine")).json()
    assert all(r["slug"] != "anchor-the-hard-hour" for r in rows)


async def test_account_delete_cascades_recommendations(client):
    await _catalogue()
    addr = await _signup(client)
    uid = await _user_id(addr)
    await _seed_morning_pattern(uid)
    await client.get("/recommendations/mine")

    assert (await client.delete("/users/me")).status_code == 204
    async with SessionLocal() as s:
        rows = (await s.scalars(select(Recommendation).where(Recommendation.user_id == uid))).all()
    assert rows == []


async def test_acceptance_stats_are_counts_only(client):
    await _catalogue()
    addr = await _signup(client)
    await _seed_morning_pattern(await _user_id(addr))
    rec = (await client.get("/recommendations/mine")).json()[0]
    await client.post(f"/recommendations/{rec['id']}/accept")

    async with SessionLocal() as s:
        stats = await service.acceptance_stats(s)
    entry = next(e for e in stats if e["slug"] == rec["slug"])
    assert entry["offered"] >= 1 and entry["accepted"] >= 1
    # No user identifiers anywhere in the payload.
    assert set(entry) == {"slug", "offered", "accepted", "dismissed"}
