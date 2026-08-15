"""Oracle tool-call audit — the service logic and the three admin endpoints.

The agent modules themselves are coverage-omitted (they only run meaningfully
against a live model), which is exactly why the logic lives in
`services/oracle_audit.py` and is tested here directly.

Note the fixture shape: `auth_client` and `admin_client` both wrap the SAME
`client`, so a test that asked for both would end up with whichever token was
applied last. Tests here take one or the other, and make their own DB sessions.
"""
import uuid

import pytest_asyncio

from app.core.database import SessionLocal
from app.models.user import User
from app.services import oracle_audit


@pytest_asyncio.fixture
async def db():
    async with SessionLocal() as session:
        yield session


@pytest_asyncio.fixture
async def uid(db) -> uuid.UUID:
    """A real user row — audit rows carry a FK, so they need one."""
    user = User(email=f"oracle-{uuid.uuid4().hex[:10]}@test.app", hashed_password="x", name="T")
    db.add(user)
    await db.commit()
    return user.id


# ── service ─────────────────────────────────────────────────────────────
async def test_read_tools_record_as_auto_and_resolved(db, uid):
    await oracle_audit.record_read(db, user_id=uid, thread_id="t1", tool="get_weekly_insights")
    row = next(r for r in await oracle_audit.recent(db, limit=50) if r.thread_id == "t1")
    assert row.decision == "auto"
    assert row.risk_tier == "read"
    # A read tool has nothing to wait for, so it is born resolved.
    assert row.resolved_at is not None


async def test_pending_then_approved_round_trip(db, uid):
    await oracle_audit.open_pending(
        db, user_id=uid, thread_id="t2", tool="log_mood",
        args={"mood": "anxious", "note": "before a call"},
    )
    assert any(r.thread_id == "t2" for r in await oracle_audit.pending(db))

    await oracle_audit.resolve(db, thread_id="t2", tool="log_mood", approved=True)
    assert not any(r.thread_id == "t2" for r in await oracle_audit.pending(db))
    row = next(r for r in await oracle_audit.recent(db, limit=50) if r.thread_id == "t2")
    assert row.decision == "approved"
    assert row.resolved_at is not None
    assert row.risk_tier == "write"


async def test_declining_is_recorded_not_dropped(db, uid):
    await oracle_audit.open_pending(db, user_id=uid, thread_id="t3", tool="save_journal")
    await oracle_audit.resolve(db, thread_id="t3", tool="save_journal", approved=False)
    row = next(r for r in await oracle_audit.recent(db, limit=50) if r.thread_id == "t3")
    # A refusal is evidence the confirmation gate works — it must be auditable.
    assert row.decision == "declined"


async def test_argument_values_are_never_stored(db, uid):
    secret = "I have been feeling hopeless about everything"
    await oracle_audit.open_pending(
        db, user_id=uid, thread_id="t4", tool="save_journal",
        args={"title": "rough day", "body": secret},
    )
    row = next(r for r in await oracle_audit.recent(db, limit=50) if r.thread_id == "t4")
    # Keys, sorted — and nothing else. The trail must not become a second copy
    # of journal content, outside the consent flags governing the original.
    assert row.arg_keys == ["body", "title"]
    assert secret not in str(row.arg_keys)


async def test_open_pending_is_idempotent_across_a_graph_replay(db, uid):
    # LangGraph re-runs an interrupted node from the top on resume, so
    # everything before interrupt() executes twice. One pending row, not two.
    for _ in range(3):
        await oracle_audit.open_pending(
            db, user_id=uid, thread_id="t5", tool="log_sleep", args={"quality": 4}
        )
    assert len([r for r in await oracle_audit.pending(db) if r.thread_id == "t5"]) == 1


async def test_resolving_an_unknown_call_is_a_no_op(db):
    # Audit is observability: a missing row must never fail a user's approved
    # write. It logs and returns.
    await oracle_audit.resolve(db, thread_id="nope", tool="log_mood", approved=True)


async def test_counts_group_by_decision(db, uid):
    await oracle_audit.record_read(db, user_id=uid, thread_id="c1", tool="suggest_activity")
    await oracle_audit.open_pending(db, user_id=uid, thread_id="c2", tool="log_mood")
    await oracle_audit.open_pending(db, user_id=uid, thread_id="c3", tool="log_mood")
    await oracle_audit.resolve(db, thread_id="c3", tool="log_mood", approved=True)

    counts = await oracle_audit.counts(db)
    assert counts["pending"] >= 1
    assert counts["approved"] >= 1
    assert counts["total"] >= 3


async def test_pending_lists_oldest_first(db, uid):
    for i in range(3):
        await oracle_audit.open_pending(db, user_id=uid, thread_id=f"order{i}", tool="log_mood")
    rows = [r for r in await oracle_audit.pending(db) if r.thread_id.startswith("order")]
    # Oldest first: the point of the list is spotting what has been stuck longest.
    assert [r.thread_id for r in rows] == ["order0", "order1", "order2"]


async def test_two_tools_on_one_thread_resolve_independently(db, uid):
    # A single conversation can queue more than one write; resolving one must
    # not close the other out.
    await oracle_audit.open_pending(db, user_id=uid, thread_id="multi", tool="log_mood")
    await oracle_audit.open_pending(db, user_id=uid, thread_id="multi", tool="save_journal")
    await oracle_audit.resolve(db, thread_id="multi", tool="log_mood", approved=True)

    still = [r for r in await oracle_audit.pending(db) if r.thread_id == "multi"]
    assert [r.tool for r in still] == ["save_journal"]


# ── admin endpoints ─────────────────────────────────────────────────────
async def test_oracle_endpoints_require_admin(auth_client):
    for path in ("/admin/oracle/status", "/admin/oracle/pending", "/admin/oracle/audit"):
        assert (await auth_client.get(path)).status_code == 403


async def test_oracle_status_reports_posture(admin_client):
    r = await admin_client.get("/admin/oracle/status")
    assert r.status_code == 200
    body = r.json()
    assert set(body) == {"enabled", "checkpointer", "counts"}
    # "none" while the graph has never been built (no LLM key in tests) — the
    # honest answer, not a claim of durability.
    assert body["checkpointer"] in {"none", "memory", "postgres"}
    assert set(body["counts"]) == {"pending", "approved", "declined", "total"}


async def test_admin_sees_pending_and_audit(admin_client, db, uid):
    await oracle_audit.open_pending(
        db, user_id=uid, thread_id="admin1", tool="log_mood",
        args={"mood": "tired", "note": "long week"},
    )

    pending = await admin_client.get("/admin/oracle/pending")
    assert pending.status_code == 200
    row = next(r for r in pending.json() if r["thread_id"] == "admin1")
    assert row["decision"] == "pending"
    assert row["arg_keys"] == ["mood", "note"]
    assert "long week" not in str(row)      # values stay out of the API too

    trail = await admin_client.get("/admin/oracle/audit", params={"limit": 5})
    assert trail.status_code == 200
    assert len(trail.json()) <= 5


async def test_audit_limit_is_clamped(admin_client):
    # Guard the listing against an accidental full-table dump.
    assert (await admin_client.get("/admin/oracle/audit", params={"limit": 9999})).status_code == 200
    assert (await admin_client.get("/admin/oracle/audit", params={"limit": 0})).status_code == 200


async def test_audit_rows_die_with_the_user(auth_client, db):
    """DPDP: deleting an account must not leave the agent's trail behind."""
    me = await auth_client.get("/users/me")
    assert me.status_code == 200
    user_id = uuid.UUID(me.json()["id"])

    await oracle_audit.open_pending(db, user_id=user_id, thread_id="gone", tool="log_mood")
    assert any(r.thread_id == "gone" for r in await oracle_audit.recent(db, limit=50))

    assert (await auth_client.delete("/users/me")).status_code == 204
    db.expire_all()
    assert not any(r.thread_id == "gone" for r in await oracle_audit.recent(db, limit=50))


async def test_an_operator_can_close_a_stuck_confirmation_without_approving_it(admin_client, db):
    """Register E57: the Oracle tab could list stuck confirmations and do nothing.

    The dangerous version of this feature is an "Approve" button — an operator
    writing to a member's journal on their behalf. What ships closes the RECORD
    only, and the decision it leaves behind is `expired`, not `approved` and not
    `declined`: the member decided nothing, and a trail claiming otherwise would
    be a false record of someone's choice about their own data.
    """
    me = await admin_client.get("/users/me")
    uid = uuid.UUID(me.json()["id"])
    await oracle_audit.open_pending(db, user_id=uid, thread_id="stuck-1", tool="save_journal")

    row = next(
        r for r in (await admin_client.get("/admin/oracle/pending")).json()
        if r["thread_id"] == "stuck-1"
    )

    r = await admin_client.post(f"/admin/oracle/pending/{row['id']}/expire")
    assert r.status_code == 200, r.text
    assert r.json()["decision"] == "expired"

    # Gone from the queue it was clogging…
    assert not any(
        p["thread_id"] == "stuck-1"
        for p in (await admin_client.get("/admin/oracle/pending")).json()
    )
    # …still in the trail, so the history is not rewritten.
    assert any(
        t["thread_id"] == "stuck-1" and t["decision"] == "expired"
        for t in (await admin_client.get("/admin/oracle/audit", params={"limit": 50})).json()
    )


async def test_expiring_twice_cannot_rewrite_a_real_decision(admin_client, db):
    """A second click must not turn an approve/decline into "expired"."""
    me = await admin_client.get("/users/me")
    uid = uuid.UUID(me.json()["id"])
    await oracle_audit.open_pending(db, user_id=uid, thread_id="stuck-2", tool="log_mood")
    row = next(
        r for r in (await admin_client.get("/admin/oracle/pending")).json()
        if r["thread_id"] == "stuck-2"
    )

    assert (await admin_client.post(f"/admin/oracle/pending/{row['id']}/expire")).status_code == 200
    # Already resolved — refused rather than silently re-stamped.
    assert (await admin_client.post(f"/admin/oracle/pending/{row['id']}/expire")).status_code == 404


async def test_expiring_an_unknown_id_is_a_404(admin_client):
    assert (await admin_client.post(f"/admin/oracle/pending/{uuid.uuid4()}/expire")).status_code == 404
