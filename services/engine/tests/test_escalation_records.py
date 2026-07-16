"""Escalation records are one-per-crisis, and they can be acknowledged.

## The record must not collapse

`escalate` carries a scar from the Postgres shim's missing `insert_one`:

    coll.insert_one(...) if hasattr(coll, "insert_one") else coll.update_one(
        {"_id": f"{record['session_id']}:{record['at']}"}, ..., upsert=True)

The fallback carefully builds a UNIQUE `_id`. So when `insert_one` was added to the shim,
the `hasattr` branch flipped — and a naive `insert_one` that reused the shim's key GUESS
(`_id` → `user_id` → `session_id`) would have keyed every escalation on the USER and
silently overwritten a person's previous crisis with their next one. The record that "we
tried to reach someone" is exactly what an incident review needs, and it would have been
the thing destroyed.

## Acknowledging is a status, not content

The queue was read-only, so it never drained: an operator who had handled an escalation had
no way to say so, and the row stayed open forever. Ack records WHO handled it and WHEN.
It records nothing about what the person said — the reference's admin renders an "Excerpt"
column with the flagged text, and that is the one thing from it we must never port
(CLAUDE.md rule 5).
"""

from __future__ import annotations

import pytest

from app.safety import escalation
from tests.conftest import requires_pg


# ── one crisis, one record ───────────────────────────────────────────────────


def test_two_escalations_for_one_person_are_two_records(mongo):
    """The regression the shim's insert_one nearly introduced."""
    escalation.escalate(user_id="u1", session_id="s1", detected_by="lexicon")
    escalation.escalate(user_id="u1", session_id="s2", detected_by="classifier")
    rows = escalation.list_escalations()
    assert len(rows) == 2, "one person's second crisis overwrote their first"


def test_two_escalations_in_one_session_are_two_records(mongo):
    """Same session, different moments. The old fallback keyed on session_id:at precisely
    so these stayed apart."""
    escalation.escalate(user_id="u1", session_id="s1", detected_by="lexicon")
    escalation.escalate(user_id="u1", session_id="s1", detected_by="classifier")
    assert len(escalation.list_escalations()) == 2


def test_the_key_guess_would_have_collided_on_the_user():
    """Names the hazard `insert_one` must not reuse, so the fix has a reason attached.

    `_key` exists for update_one, where the filter names the row. Reaching for it from
    insert_one resolves an escalation to its `user_id` — which is why the two tests below
    exist at all.
    """
    from app.stores.pg import PgCollection

    rec = {"event": "crisis_escalation", "user_id": "u1", "session_id": "s1", "at": "t1"}
    assert PgCollection._key({}, rec) == "u1"


@requires_pg
def test_insert_means_insert_on_postgres(pgdb):
    """Two crises for one person are two rows — on the DEFAULT backend.

    This runs against real Postgres rather than mongomock deliberately. Three bugs today
    (`find_one(sort=)`, the missing `insert_one`, `_project` dropping `_id`) each passed a
    full mongomock suite while being broken on the store that actually ships. A source-grep
    assertion was the first draft of this test; it failed against a *comment* saying "No
    ON CONFLICT", which is all the evidence needed that it was testing the wrong thing.
    """
    coll = pgdb.collection("esc_insert")
    r1 = coll.insert_one({"event": "crisis", "user_id": "u1", "session_id": "s1", "at": "t1"})
    r2 = coll.insert_one({"event": "crisis", "user_id": "u1", "session_id": "s2", "at": "t2"})

    assert len(list(coll.find({}, {"_id": 1}))) == 2, \
        "one person's second crisis overwrote their first"
    assert r1.inserted_id != r2.inserted_id, "a doc without an _id must get a FRESH one"


@requires_pg
def test_a_duplicate_id_raises_rather_than_overwriting(pgdb):
    """`insert_one` must not upsert. A duplicate `_id` is a caller error, and papering over
    it with ON CONFLICT DO UPDATE is how a safety record gets destroyed quietly."""
    import psycopg

    coll = pgdb.collection("esc_dupe")
    coll.insert_one({"_id": "s1:t1", "user_id": "u1"})
    with pytest.raises(psycopg.errors.UniqueViolation):
        coll.insert_one({"_id": "s1:t1", "user_id": "u9"})


@requires_pg
def test_a_cold_start_survives_concurrent_first_use(pgdb):
    """Two threads reaching a brand-new collection at once must not crash.

    `CREATE TABLE IF NOT EXISTS` is not atomic in Postgres, so the loser of the race used
    to die with `UniqueViolation: duplicate key value violates unique constraint
    "pg_type_typname_nsp_index"` — an error that names the catalog rather than the table,
    which is why it reads as a mystery. It can only happen on a table's FIRST use, so it
    is invisible against any database that already has its schema: it arrives on a cold
    start against an empty one, under the first concurrent requests.

    Found by wiping this project's dev volume and sending one crisis turn — a 500 on the
    turn of someone disclosing self-harm. A first production deploy is that same shape.
    """
    import threading

    from app.stores import pg

    # The table must genuinely NOT exist: `pgdb.collection()` creates it, and
    # CREATE TABLE IF NOT EXISTS against an existing table is a no-op that cannot race.
    # Dropping it (while keeping the name registered for teardown) is what makes this a
    # cold start rather than a test that passes for the wrong reason.
    name = pgdb.collection("cold_start").name
    with pgdb.pool.connection() as conn:  # not pgdb.sql(): DROP produces no rows to fetch
        conn.execute(f'DROP TABLE IF EXISTS "{name}"')
    pg._ensured.discard(name)
    pg._collections.pop(name, None)

    errors: list[BaseException] = []
    barrier = threading.Barrier(6)

    def race():
        try:
            barrier.wait()  # maximise the overlap rather than hoping for it
            pg.PgCollection(name).insert_one({"hello": "world"})
        except BaseException as exc:  # noqa: BLE001
            errors.append(exc)

    threads = [threading.Thread(target=race) for _ in range(6)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    assert not errors, f"a cold start crashed under concurrent first use: {errors[0]!r}"
    assert len(list(pg.PgCollection(name).find({}, {"_id": 1}))) == 6


def test_a_real_ddl_failure_still_raises(pgdb, monkeypatch):
    """The concurrency tolerance must not swallow a genuine DDL problem — a first query
    failing with something illegible is exactly what it would cost."""
    from app.stores import pg

    class _Boom(Exception):
        sqlstate = "42501"  # insufficient_privilege

    class _Conn:
        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

        def execute(self, *a, **k):
            raise _Boom("permission denied for schema public")

    monkeypatch.setattr(pg, "get_pool", lambda: type("P", (), {"connection": lambda self: _Conn()})())
    pg._ensured.discard("never_created")
    with pytest.raises(_Boom):
        pg.PgCollection("never_created")


def test_the_shim_returns_id_when_the_queue_asks_for_it():
    """The console has to NAME a record to acknowledge it, so the projection asks for `_id`.

    `_project` used to filter `_id` out of the projection and never put it back: mongomock
    returns it, the shim did not, so every row would have carried an empty id on Postgres —
    the DEFAULT backend — and the Resolve button would have had nothing to send. Third time
    this mongomock-vs-shim divergence has bitten today (see also `find_one(sort=)` and the
    missing `insert_one`), which is why it is pinned here rather than trusted.
    """
    from app.stores.pg import _project

    doc = {"_id": "s1:t1", "user_id": "u1", "at": "t1", "secret": "not asked for"}
    got = _project(doc, {"_id": 1, "user_id": 1, "at": 1})
    assert got["_id"] == "s1:t1", "a caller asked for _id and did not get one"
    assert "secret" not in got, "the projection stopped excluding what it was not asked for"


def test_the_shim_still_excludes_id_when_told_to():
    from app.stores.pg import _project

    got = _project({"_id": "x", "user_id": "u1"}, {"_id": 0, "user_id": 1})
    assert "_id" not in got
    assert got["user_id"] == "u1"


# ── acknowledging ────────────────────────────────────────────────────────────


def test_a_new_escalation_is_open(mongo):
    escalation.escalate(user_id="u1", session_id="s1", detected_by="lexicon")
    row = escalation.list_escalations()[0]
    assert row["acknowledged_at"] is None
    assert row["acknowledged_by"] == ""


def test_acknowledging_drains_the_open_queue(mongo):
    """The whole point: the queue was read-only, so it never drained.

    Note the acked row is fetched with status="resolved" — reading it back via the DEFAULT
    view returns nothing, because acknowledging is exactly what removes it from `open`.
    """
    escalation.escalate(user_id="u1", session_id="s1", detected_by="lexicon")
    rid = escalation.list_escalations()[0]["id"]
    assert escalation.acknowledge(rid, actor="ops-1") is True

    assert escalation.list_escalations() == [], "a handled escalation stayed in the queue"
    row = escalation.list_escalations(status="resolved")[0]
    assert row["acknowledged_by"] == "ops-1"
    assert row["acknowledged_at"]


def test_acknowledging_is_idempotent(mongo):
    """Two operators clicking Resolve must not fight, and the FIRST responder is the true
    one — an incident review asks who actually handled it."""
    escalation.escalate(user_id="u1", session_id="s1", detected_by="lexicon")
    rid = escalation.list_escalations()[0]["id"]
    escalation.acknowledge(rid, actor="first")
    first_at = escalation.list_escalations(status="resolved")[0]["acknowledged_at"]
    escalation.acknowledge(rid, actor="second")
    row = escalation.list_escalations(status="resolved")[0]
    assert row["acknowledged_by"] == "first", "a later click rewrote who handled it"
    assert row["acknowledged_at"] == first_at


def test_a_legacy_objectid_record_can_still_be_resolved(mongo):
    """Escalations written before `_persist` named its own `_id` carry a Mongo ObjectId.

    Two ways that used to break: the route 500s on a non-serialisable id, and — subtler —
    the queue renders but Resolve 404s forever, because the console sends the id back as a
    STRING and `{"_id": "<hex>"}` never matches an ObjectId. The row an operator most needs
    to clear is the oldest one.
    """
    from app import config

    coll = mongo[config.MONGO_BACKEND_DB]["crisis_escalations"]
    coll.insert_one({  # no _id: mongo mints an ObjectId, exactly as the old code did
        "org_id": "default", "user_id": "u1", "session_id": "s1",
        "detected_by": "lexicon", "at": "2026-07-16T03:00:00", "delivered": True,
    })

    rid = escalation.list_escalations()[0]["id"]
    assert isinstance(rid, str), "a non-serialisable id would 500 the whole queue"
    assert escalation.acknowledge(rid, actor="ops") is True, \
        "the oldest rows in the queue could never be cleared"
    assert escalation.list_escalations() == []


def test_acknowledging_an_unknown_record_is_false_not_an_exception(mongo):
    assert escalation.acknowledge("no-such-id", actor="ops") is False


def test_acknowledging_an_empty_id_is_false(mongo):
    assert escalation.acknowledge("", actor="ops") is False


def test_a_store_error_never_raises_into_the_admin_surface(mongo, monkeypatch):
    """Same contract as the read side: acking happens mid-incident, and a 500 in the
    operator's console is the last thing anyone needs at that moment."""
    monkeypatch.setattr(
        "app.stores.mongo.get_client",
        lambda: (_ for _ in ()).throw(RuntimeError("mongo is on fire")),
    )
    assert escalation.acknowledge("any-id", actor="ops") is False


def test_a_malformed_24_char_id_is_not_an_objectid(mongo):
    """`_record_key` only *tries* the ObjectId reading. A 24-character id that is not hex
    must fall through as a plain string rather than raising — the length check narrows the
    candidates, it does not prove the parse."""
    from app.safety.escalation import _record_key

    assert _record_key("z" * 24) == "z" * 24
    assert escalation.acknowledge("z" * 24, actor="ops") is False


def test_acknowledging_without_a_store_is_false():
    """No `mongo` fixture at all: get_client() returns None. Never raises into the admin
    surface.

    The parameter is ABSENT rather than defaulted — pytest injects fixtures by parameter
    NAME, so `(mongo=None)` would still have handed this a real store and passed for the
    wrong reason, proving nothing about the path it names.
    """
    assert escalation.acknowledge("any", actor="ops") is False


def test_open_and_resolved_can_be_listed_apart(mongo):
    escalation.escalate(user_id="u1", session_id="s1", detected_by="lexicon")
    escalation.escalate(user_id="u2", session_id="s2", detected_by="lexicon")
    rid = escalation.list_escalations()[0]["id"]
    escalation.acknowledge(rid, actor="ops")

    assert len(escalation.list_escalations(status="open")) == 1
    assert len(escalation.list_escalations(status="resolved")) == 1
    assert len(escalation.list_escalations(status="all")) == 2


def test_the_queue_still_carries_no_disclosure(mongo):
    """Acknowledging adds a status and a name. It must not become a foothold for content —
    the reference's admin has an "Excerpt" column and that is the trap (rule 5)."""
    escalation.escalate(user_id="u1", session_id="s1", detected_by="lexicon")
    rid = escalation.list_escalations()[0]["id"]
    escalation.acknowledge(rid, actor="ops")
    row = escalation.list_escalations(status="resolved")[0]
    allowed = {"id", "org_id", "user_id", "session_id", "detected_by", "at", "delivered",
               "acknowledged_at", "acknowledged_by"}
    assert set(row) <= allowed, f"the queue grew a field: {set(row) - allowed}"


def test_acknowledging_cannot_cross_a_tenant(mongo):
    """An operator's ack is org-scoped like the read — the queue is per-tenant."""
    from app.tenancy import ctx_org_id

    token = ctx_org_id.set("acme")
    try:
        escalation.escalate(user_id="u1", session_id="s1", detected_by="lexicon")
        rid = escalation.list_escalations()[0]["id"]
    finally:
        ctx_org_id.reset(token)

    other = ctx_org_id.set("other-co")
    try:
        assert escalation.acknowledge(rid, actor="them") is False, \
            "one tenant's operator resolved another tenant's escalation"
    finally:
        ctx_org_id.reset(other)
