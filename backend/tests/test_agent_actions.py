"""Audit trail for what the Oracle proposed writing, and what the user decided.

Before this the only durable trace was the LangGraph checkpoint: a blob keyed by
thread, not queryable, and wiped by "delete all memory". For an agent that can
create journal entries on someone's behalf, "did the assistant write that, or
did I?" has to be answerable.
"""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal, utcnow
from app.models.agent_action import AgentAction
from app.models.user import User


async def _signup(client, prefix="agent"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "A"}
    )
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


async def _user_id(addr: str) -> uuid.UUID:
    async with SessionLocal() as s:
        return (await s.scalar(select(User).where(User.email == addr))).id


async def _proposal(uid: uuid.UUID, thread: str = "t1", tool: str = "save_journal") -> uuid.UUID:
    async with SessionLocal() as s:
        row = AgentAction(user_id=uid, thread_id=thread, tool=tool,
                          summary="Save a journal entry", status="proposed")
        s.add(row)
        await s.commit()
        return row.id


async def test_history_is_empty_and_readable_for_a_new_account(client):
    await _signup(client)
    r = await client.get("/oracle/actions")
    assert r.status_code == 200 and r.json() == []


async def test_history_shows_the_proposal_and_never_tool_arguments(client):
    """`save_journal` carries the journal body — copying it here would put
    private text in a second table with its own retention story."""
    addr = await _signup(client)
    await _proposal(await _user_id(addr))

    rows = (await client.get("/oracle/actions")).json()
    assert len(rows) == 1
    assert rows[0]["tool"] == "save_journal"
    assert rows[0]["status"] == "proposed"
    assert rows[0]["decided_at"] is None
    assert "args" not in rows[0] and "arguments" not in rows[0]


async def test_approving_stamps_the_newest_proposal(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    await _proposal(uid, thread="t1")

    # Oracle is disabled without a key, so drive the recorder directly — the
    # route wraps exactly this call.
    from app.api.routes.oracle import _record_decision

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.id == uid))
        await _record_decision(s, user, "t1", approved=True)

    row = (await client.get("/oracle/actions")).json()[0]
    assert row["status"] == "approved"
    assert row["decided_at"] is not None


async def test_declining_is_recorded_not_discarded(client):
    """A refused proposal is the signal that the agent is misreading someone —
    dropping it would erase exactly the interesting case."""
    addr = await _signup(client)
    uid = await _user_id(addr)
    await _proposal(uid, thread="t2", tool="log_mood")

    from app.api.routes.oracle import _record_decision

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.id == uid))
        await _record_decision(s, user, "t2", approved=False)

    assert (await client.get("/oracle/actions")).json()[0]["status"] == "declined"


async def test_a_decision_cannot_touch_another_users_proposal(client):
    """thread_id is client-supplied, so ownership is enforced in the query."""
    owner = await _signup(client, "owner")
    owner_id = await _user_id(owner)
    await _proposal(owner_id, thread="shared-thread")

    stranger = await _signup(client, "stranger")
    stranger_id = await _user_id(stranger)

    from app.api.routes.oracle import _record_decision

    async with SessionLocal() as s:
        intruder = await s.scalar(select(User).where(User.id == stranger_id))
        await _record_decision(s, intruder, "shared-thread", approved=True)

    async with SessionLocal() as s:
        row = await s.scalar(select(AgentAction).where(AgentAction.user_id == owner_id))
        assert row.status == "proposed"   # untouched


async def test_a_decision_with_no_proposal_is_a_no_op(client):
    """The stream is the source of truth for what runs; the audit trail must
    never be able to block it."""
    addr = await _signup(client)
    uid = await _user_id(addr)

    from app.api.routes.oracle import _record_decision

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.id == uid))
        await _record_decision(s, user, "no-such-thread", approved=True)  # must not raise


async def test_wipe_and_export_cover_the_trail(client):
    addr = await _signup(client)
    await _proposal(await _user_id(addr))

    body = (await client.get("/users/me/export")).json()
    assert len(body["agent_actions"]) == 1

    wiped = await client.delete("/users/me/memory")
    assert wiped.json()["agent_actions"] == 1
    assert (await client.get("/oracle/actions")).json() == []


async def test_account_delete_cascades(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    await _proposal(uid)

    assert (await client.delete("/users/me")).status_code == 204
    async with SessionLocal() as s:
        assert (await s.scalars(select(AgentAction).where(AgentAction.user_id == uid))).all() == []


async def test_admin_sees_counts_never_summaries(admin_client):
    """A summary quotes the user's own words back — that is content."""
    me = (await admin_client.get("/auth/me")).json()
    secret = "Save a journal entry about the argument with my father"
    async with SessionLocal() as s:
        s.add(AgentAction(user_id=uuid.UUID(me["id"]), thread_id="t", tool="save_journal",
                          summary=secret, status="approved", decided_at=utcnow()))
        await s.commit()

    r = await admin_client.get("/admin/agent-actions")
    assert r.status_code == 200
    assert secret not in r.text
    entry = next(e for e in r.json() if e["tool"] == "save_journal")
    assert set(entry) == {"tool", "proposed", "approved", "declined"}
