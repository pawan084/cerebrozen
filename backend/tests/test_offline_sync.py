"""What the Android offline queue needs from the server: writes that land once,
and reads that only send what changed.

The queue keeps writes on disk while there is no network and drains them when
there is. Any queue like that can send the same write twice — the request
succeeded but the process died before the row was marked done. Without a key,
the second send silently creates a duplicate check-in, and the user's history
grows entries they never wrote.
"""
import uuid
from datetime import timedelta

from sqlalchemy import select

from app.core.database import SessionLocal, utcnow
from app.models.idempotency import IdempotencyRecord
from app.models.user import User
from app.services import idempotency


async def _signup(client, prefix="sync"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "S"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


MOOD = {"mood": "Good", "note": "clear", "symbol": "sparkles", "intensity": 3}


async def test_a_replayed_check_in_lands_once(client):
    await _signup(client)
    key = str(uuid.uuid4())

    first = await client.post("/moods", json=MOOD, headers={"Idempotency-Key": key})
    second = await client.post("/moods", json=MOOD, headers={"Idempotency-Key": key})

    assert first.status_code == 201
    assert second.status_code == 201
    assert first.json()["id"] == second.json()["id"], "the replay must return the original row"
    assert len((await client.get("/moods")).json()) == 1, "one tap, one check-in"


async def test_without_a_key_nothing_changes(client):
    """The header is optional — older clients (and iOS) keep the old behaviour."""
    await _signup(client)
    await client.post("/moods", json=MOOD)
    await client.post("/moods", json=MOOD)
    assert len((await client.get("/moods")).json()) == 2


async def test_reusing_a_key_for_a_different_write_is_refused(client):
    await _signup(client)
    key = str(uuid.uuid4())
    await client.post("/moods", json=MOOD, headers={"Idempotency-Key": key})

    clash = await client.post(
        "/moods", json={**MOOD, "mood": "Anxious"}, headers={"Idempotency-Key": key}
    )
    assert clash.status_code == 409, "guessing which write the user meant is worse than saying so"
    assert len((await client.get("/moods")).json()) == 1


async def test_the_same_key_from_two_accounts_does_not_collide(client):
    """Keys are client-generated; two devices must not share a key space."""
    key = str(uuid.uuid4())
    await _signup(client, "one")
    first = await client.post("/moods", json=MOOD, headers={"Idempotency-Key": key})

    await _signup(client, "two")
    second = await client.post("/moods", json=MOOD, headers={"Idempotency-Key": key})

    assert first.status_code == 201 and second.status_code == 201
    assert first.json()["id"] != second.json()["id"]
    assert len((await client.get("/moods")).json()) == 1, "the second account has its own single entry"


async def test_journal_replays_too(client):
    await _signup(client)
    key = str(uuid.uuid4())
    entry = {"title": "Tonight", "body": "quiet", "tags": ["evening"], "symbol": "book"}

    first = await client.post("/journal", json=entry, headers={"Idempotency-Key": key})
    second = await client.post("/journal", json=entry, headers={"Idempotency-Key": key})

    assert first.json()["id"] == second.json()["id"]
    assert len((await client.get("/journal")).json()) == 1
    # The safety scan ran on the first write and its verdict rides the replay.
    assert second.json()["risk_level"] == first.json()["risk_level"]


async def test_a_nonsense_key_is_ignored_rather_than_breaking_the_write(client):
    """An over-long key must not blow up *after* the row was already written."""
    await _signup(client)
    r = await client.post("/moods", json=MOOD, headers={"Idempotency-Key": "x" * 400})
    assert r.status_code == 201
    assert len((await client.get("/moods")).json()) == 1


def test_the_fingerprint_ignores_key_order_and_spacing():
    assert idempotency.fingerprint({"a": 1, "b": 2}) == idempotency.fingerprint({"b": 2, "a": 1})
    assert idempotency.fingerprint({"a": 1}) != idempotency.fingerprint({"a": 2})


def test_key_normalisation():
    assert idempotency.normalise_key("  abc  ") == "abc"
    assert idempotency.normalise_key("") is None
    assert idempotency.normalise_key(None) is None
    assert idempotency.normalise_key("x" * (idempotency.MAX_KEY_LENGTH + 1)) is None


async def test_expired_records_are_purged(client):
    email = await _signup(client)
    await client.post("/moods", json=MOOD, headers={"Idempotency-Key": str(uuid.uuid4())})

    async with SessionLocal() as db:
        user = await db.scalar(select(User).where(User.email == email))
        row = await db.scalar(
            select(IdempotencyRecord).where(IdempotencyRecord.user_id == user.id)
        )
        assert row is not None
        row.created_at = utcnow() - idempotency.RETENTION - timedelta(hours=1)
        await db.commit()

        assert await idempotency.purge_expired(db) >= 1
        assert await db.scalar(
            select(IdempotencyRecord).where(IdempotencyRecord.user_id == user.id)
        ) is None


async def test_since_only_returns_what_changed(client):
    await _signup(client)
    await client.post("/moods", json=MOOD)
    everything = (await client.get("/moods")).json()
    cursor = everything[0]["created_at"]

    assert (await client.get("/moods", params={"since": cursor})).json() == [], (
        "a client that is already up to date must not re-download its own history"
    )

    await client.post("/moods", json={**MOOD, "mood": "Tired"})
    fresh = (await client.get("/moods", params={"since": cursor})).json()
    assert [m["mood"] for m in fresh] == ["Tired"]


async def test_journal_since_cursor(client):
    await _signup(client)
    await client.post("/journal", json={"title": "First", "body": "a", "tags": [], "symbol": "book"})
    cursor = (await client.get("/journal")).json()[0]["created_at"]

    await client.post("/journal", json={"title": "Second", "body": "b", "tags": [], "symbol": "book"})
    fresh = (await client.get("/journal", params={"since": cursor})).json()
    assert [e["title"] for e in fresh] == ["Second"]
