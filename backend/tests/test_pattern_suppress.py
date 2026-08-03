"""Hiding a pattern has to retract what it justified.

The Pattern Dashboard promises "hide one and it stops being shown or used".
`compute_patterns` honours the tombstone, so no NEW recommendation is seeded from
a hidden pattern — but one seeded earlier keeps `reason` set to the statement
verbatim, and the dashboard renders it as "Because: …". Found on a device: the
pattern vanished from the top of the screen and went on justifying a live
suggestion halfway down it.
"""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.recommendation import Recommendation
from app.models.user import User

STATEMENT = "Mornings tend to be your hardest time of day."


async def _signup(client):
    addr = f"suppress-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "S"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    # Opt in to the data categories (fresh accounts grant nothing) — the same
    # PATCH a real client sends at the end of onboarding.
    r = await client.patch("/users/me/consent", json={
        "mood_history": True, "journal_memory": True, "sleep_history": True})
    assert r.status_code == 200
    async with SessionLocal() as s:
        return await s.scalar(select(User).where(User.email == addr))


async def _add_rec(user_id, reason: str, status: str) -> uuid.UUID:
    async with SessionLocal() as s:
        row = Recommendation(
            user_id=user_id, practice_slug=f"p-{uuid.uuid4().hex[:6]}",
            reason=reason, status=status,
        )
        s.add(row)
        await s.commit()
        return row.id


async def _status(rec_id) -> str:
    async with SessionLocal() as s:
        return (await s.get(Recommendation, rec_id)).status


async def test_hiding_a_pattern_retracts_the_suggestion_it_justified(client):
    user = await _signup(client)
    pending = await _add_rec(user.id, STATEMENT, "pending")

    r = await client.post("/users/me/memory/suppress-pattern", json={"statement": STATEMENT})
    assert r.status_code == 204
    assert await _status(pending) == "dismissed", "a hidden pattern must not go on justifying a live offer"


async def test_an_accepted_practice_survives_hiding_its_pattern(client):
    """The user chose it. Tidying away the observation behind it is not a reason
    to take it back."""
    user = await _signup(client)
    accepted = await _add_rec(user.id, STATEMENT, "accepted")

    await client.post("/users/me/memory/suppress-pattern", json={"statement": STATEMENT})
    assert await _status(accepted) == "accepted"


async def test_suggestions_from_other_patterns_are_untouched(client):
    user = await _signup(client)
    other = await _add_rec(user.id, "Evenings tend to be your hardest time of day.", "pending")

    await client.post("/users/me/memory/suppress-pattern", json={"statement": STATEMENT})
    assert await _status(other) == "pending"


async def test_suppressing_twice_is_still_not_an_error(client):
    user = await _signup(client)
    await _add_rec(user.id, STATEMENT, "pending")
    for _ in range(2):
        r = await client.post("/users/me/memory/suppress-pattern", json={"statement": STATEMENT})
        assert r.status_code == 204
