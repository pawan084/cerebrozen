"""Per-item context memory: CRUD, the consent gate, ownership, suppression.

The point of this table is that the Pattern Dashboard's promise ("you can edit
or delete any of it") stops being a lie, so the tests lean on the promises
rather than the mechanics: a wipe really wipes, an export really exports, and
one account can never see or touch another's row.
"""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.memory import ContextMemory
from app.models.user import User


async def _signup(client, prefix="memory"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "M"}
    )
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    # Opt in to AI memory the way a real client does at the end of onboarding —
    # a fresh account grants nothing, and this whole surface is ai_memory-gated.
    r = await client.patch("/users/me/consent", json={"ai_memory": True})
    assert r.status_code == 200
    return addr


async def _user_id(addr: str) -> uuid.UUID:
    async with SessionLocal() as s:
        return (await s.scalar(select(User).where(User.email == addr))).id


async def test_create_list_edit_delete_roundtrip(client):
    await _signup(client)

    created = await client.post("/users/me/memory", json={"body": "Sleeps badly on Sundays"})
    assert created.status_code == 201
    mid = created.json()["id"]
    assert created.json()["source"] == "manual"

    listed = await client.get("/users/me/memory")
    assert [m["body"] for m in listed.json()] == ["Sleeps badly on Sundays"]

    edited = await client.patch(f"/users/me/memory/{mid}", json={"body": "Sleeps badly on Mondays"})
    assert edited.status_code == 200
    assert edited.json()["body"] == "Sleeps badly on Mondays"
    assert edited.json()["updated_at"] is not None

    assert (await client.delete(f"/users/me/memory/{mid}")).status_code == 204
    assert (await client.get("/users/me/memory")).json() == []


async def test_dismiss_hides_without_deleting(client):
    await _signup(client)
    mid = (await client.post("/users/me/memory", json={"body": "Prefers evenings"})).json()["id"]

    await client.patch(f"/users/me/memory/{mid}", json={"dismissed": True})
    assert (await client.get("/users/me/memory")).json() == []
    # Still there, and recoverable — dismissal is not deletion.
    assert len((await client.get("/users/me/memory?include_dismissed=true")).json()) == 1
    await client.patch(f"/users/me/memory/{mid}", json={"dismissed": False})
    assert len((await client.get("/users/me/memory")).json()) == 1


async def test_source_cannot_be_forged_into_an_inference(client):
    """A client must not be able to post a row in as something the AI decided."""
    await _signup(client)
    r = await client.post(
        "/users/me/memory", json={"body": "x", "source": "suppressed_pattern"}
    )
    assert r.status_code == 201
    assert r.json()["source"] == "manual"


async def test_another_users_memory_is_404_not_403(client):
    """Whether an id exists is not something a stranger should learn."""
    await _signup(client, "owner")
    mid = (await client.post("/users/me/memory", json={"body": "private"})).json()["id"]

    await _signup(client, "stranger")
    assert (await client.get(f"/users/me/memory")).json() == []
    assert (await client.patch(f"/users/me/memory/{mid}", json={"body": "hi"})).status_code == 404
    assert (await client.delete(f"/users/me/memory/{mid}")).status_code == 404


async def test_consent_off_blocks_reads_and_writes_but_never_deletion(client):
    """Switching ai_memory off must not trap data the user wants gone."""
    await _signup(client)
    mid = (await client.post("/users/me/memory", json={"body": "remember me"})).json()["id"]

    await client.patch("/users/me/consent", json={"ai_memory": False})
    assert (await client.get("/users/me/memory")).status_code == 200  # listing stays readable
    assert (await client.post("/users/me/memory", json={"body": "nope"})).status_code == 403
    assert (await client.patch(f"/users/me/memory/{mid}", json={"body": "nope"})).status_code == 403
    # ...but removal always works.
    assert (await client.delete(f"/users/me/memory/{mid}")).status_code == 204


async def test_wipe_all_removes_memories_too(client):
    """DELETE /me/memory is the "Delete all memory" button on three clients."""
    await _signup(client)
    await client.post("/users/me/memory", json={"body": "one"})
    await client.post("/users/me/memory", json={"body": "two"})

    r = await client.delete("/users/me/memory")
    assert r.status_code == 200
    assert r.json()["memories"] == 2
    assert (await client.get("/users/me/memory")).json() == []


async def test_export_carries_memory(client):
    """Portability: anything visible in-app has to be in the export."""
    await _signup(client)
    await client.post("/users/me/memory", json={"body": "exportable"})
    body = (await client.get("/users/me/export")).json()
    assert [m["body"] for m in body["memory"]] == ["exportable"]


async def test_account_delete_cascades_memory(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    await client.post("/users/me/memory", json={"body": "goes with the account"})

    assert (await client.delete("/users/me")).status_code == 204
    async with SessionLocal() as s:
        rows = (await s.scalars(select(ContextMemory).where(ContextMemory.user_id == uid))).all()
    assert rows == []


async def test_suppressing_a_pattern_hides_it_and_is_idempotent(client):
    await _signup(client)
    stmt = "Mornings tend to be your hardest time of day."

    first = await client.post("/users/me/memory/suppress-pattern", json={"statement": stmt})
    assert first.status_code == 204
    second = await client.post("/users/me/memory/suppress-pattern", json={"statement": stmt})
    assert second.status_code == 204

    # One tombstone, and it never shows up as a "memory".
    assert (await client.get("/users/me/memory?include_dismissed=true")).json() == []
    body = (await client.get("/insights/patterns")).json()
    assert body["suppressed"] == 1
    assert all(p["statement"] != stmt for p in body["patterns"])


async def test_a_suppression_tombstone_cannot_be_rewritten(client):
    """Rewriting one would silently re-point which pattern is hidden."""
    addr = await _signup(client)
    uid = await _user_id(addr)
    await client.post("/users/me/memory/suppress-pattern", json={"statement": "hidden thing"})
    async with SessionLocal() as s:
        row = await s.scalar(select(ContextMemory).where(ContextMemory.user_id == uid))
        tid = row.id

    assert (await client.patch(f"/users/me/memory/{tid}", json={"body": "other"})).status_code == 409
    # Deleting it (un-hiding) is allowed.
    assert (await client.delete(f"/users/me/memory/{tid}")).status_code == 204
