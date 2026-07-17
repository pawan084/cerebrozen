"""Right to erasure, and right of access.

The failure mode this file exists to prevent is not "delete doesn't work". It is
**delete works, on four of the six places the data lives**, returns success, and the person
is told their coaching history is gone while their transcript sits in another database.

A person's data here is spread across six locations in three databases. The two that get
forgotten are the LangGraph checkpoint tables — they hold the entire conversation state, they
are keyed by `thread_id` rather than `user_id`, so they are not reachable from the person at
all, and no store module writes to them, so nobody thinks of them as "the user database".

Every test below is written to catch a *plausible* incomplete implementation, not an absurd
one.
"""

import json
import pathlib
import re

import pytest

from app import config
from app.privacy import erasure
from tests.conftest import requires_pg


# ── Seed a user into EVERY location, the way the app really does ─────────────

@pytest.fixture
def populated(mongo, user_id):
    """A person with history in all six places. Returns (user_id, session_ids)."""
    sessions = ["s-alpha", "s-beta"]

    conv = mongo[config.MONGO_RASA_DB][config.MONGO_USER_CONVERSATIONS_COLLECTION]
    for s in sessions:
        conv.insert_one({
            "_id": s, "session_id": s, "user_id": user_id,
            "messages": [{"role": "user", "content": "my manager is impossible"}],
        })

    ag = mongo[config.MONGO_BACKEND_DB][config.MONGO_AGENTIC_COLLECTION]
    ag.insert_one({"_id": user_id, "user_id": user_id,
                   "actions": [{"action_id": "a1", "full_text": "book the 1:1"}],
                   "moods": [{"mapped_emotions": ["dread"]}]})

    dv = mongo[config.MONGO_BACKEND_DB][config.MONGO_DYNAMIC_VARS_COLLECTION]
    dv.insert_one({"_id": user_id, "user_id": user_id, "session_goal": "be more direct"})

    esc = mongo[config.MONGO_BACKEND_DB]["crisis_escalations"]
    esc.insert_one({"user_id": user_id, "session_id": "s-alpha", "detected_by": "classifier"})

    # The two nobody remembers: the graph's own state, keyed by thread_id.
    ck = mongo[config.MONGO_CHECKPOINT_DB]["checkpoints"]
    cw = mongo[config.MONGO_CHECKPOINT_DB]["checkpoint_writes"]
    for s in sessions:
        ck.insert_one({"thread_id": s, "checkpoint": {"messages": ["the whole conversation"]}})
        cw.insert_one({"thread_id": s, "task_id": "t1", "value": "a write-ahead entry"})

    return user_id, sessions


# ── ERASURE ──────────────────────────────────────────────────────────────────

def test_erasure_removes_the_data_from_every_location(populated, mongo):
    """THE test. Seed all six, erase, then go looking with fresh eyes.

    Written as an independent scan rather than by trusting the report, because a buggy
    implementation would produce a confident report AND leave the data — which is exactly the
    failure that reaches a regulator.
    """
    uid, sessions = populated

    report = erasure.erase_user(uid)

    assert report["verified"] is True, f"erasure not verified: {report}"

    # Now look for ourselves.
    conv = mongo[config.MONGO_RASA_DB][config.MONGO_USER_CONVERSATIONS_COLLECTION]
    ag = mongo[config.MONGO_BACKEND_DB][config.MONGO_AGENTIC_COLLECTION]
    dv = mongo[config.MONGO_BACKEND_DB][config.MONGO_DYNAMIC_VARS_COLLECTION]
    esc = mongo[config.MONGO_BACKEND_DB]["crisis_escalations"]
    ck = mongo[config.MONGO_CHECKPOINT_DB]["checkpoints"]
    cw = mongo[config.MONGO_CHECKPOINT_DB]["checkpoint_writes"]

    assert conv.count_documents({"user_id": uid}) == 0, "the transcript survived"
    assert ag.count_documents({"user_id": uid}) == 0, "actions/insights/moods survived"
    assert dv.count_documents({"user_id": uid}) == 0, "captured variables survived"
    assert esc.count_documents({"user_id": uid}) == 0, "the crisis record survived"
    assert ck.count_documents({"thread_id": {"$in": sessions}}) == 0, (
        "THE CONVERSATION STATE SURVIVED. The checkpointer holds the full message history and "
        "is keyed by thread_id, not user_id — this is the location an erasure forgets."
    )
    assert cw.count_documents({"thread_id": {"$in": sessions}}) == 0, "the checkpoint WAL survived"


def test_a_partial_erasure_is_reported_as_a_failure_not_a_success(populated, mongo, monkeypatch):
    """The single worst outcome is a partial erasure reported as success: the person believes
    their data is gone, and it is not.

    Simulate one location refusing to delete, and require the report to say so.
    """
    uid, _ = populated
    ck = mongo[config.MONGO_CHECKPOINT_DB]["checkpoints"]

    original = ck.delete_many
    monkeypatch.setattr(ck, "delete_many", lambda *a, **k: original({"thread_id": "__none__"}))

    report = erasure.erase_user(uid)

    assert report["verified"] is False, "a partial erasure reported itself as verified"
    assert report["remaining"]["checkpoints"] > 0
    assert report["remaining"]["transcripts"] == 0   # the others still went


def test_erasure_does_not_touch_anybody_else(populated, mongo):
    """An erasure that over-deletes is a different catastrophe, and just as easy to write."""
    uid, _ = populated
    other = "someone-else"
    conv = mongo[config.MONGO_RASA_DB][config.MONGO_USER_CONVERSATIONS_COLLECTION]
    conv.insert_one({"_id": "s-other", "session_id": "s-other", "user_id": other,
                     "messages": [{"role": "user", "content": "not yours"}]})
    ck = mongo[config.MONGO_CHECKPOINT_DB]["checkpoints"]
    ck.insert_one({"thread_id": "s-other", "checkpoint": {"messages": ["also not yours"]}})

    erasure.erase_user(uid)

    assert conv.count_documents({"user_id": other}) == 1, "erasure deleted another user's transcript"
    assert ck.count_documents({"thread_id": "s-other"}) == 1, "erasure deleted another user's state"


def test_a_user_with_no_sessions_does_not_wipe_the_checkpoint_table(mongo, user_id):
    """The empty-filter bug, which is how a delete path becomes a disaster.

    A user with no sessions yields an empty session list. If that became `{"thread_id":
    {"$in": []}}` — or worse, `{}` — the delete would match EVERY row in the checkpoint table
    and take the whole tenant's conversation state with it.
    """
    ck = mongo[config.MONGO_CHECKPOINT_DB]["checkpoints"]
    ck.insert_one({"thread_id": "somebody-elses-session", "checkpoint": {"messages": ["x"]}})

    report = erasure.erase_user(user_id)     # this user has nothing at all

    assert report["verified"] is True
    assert ck.count_documents({}) == 1, "an erasure for a user with no sessions wiped the table"


def test_the_redis_profile_cache_is_purged(populated, monkeypatch):
    """A cache is a store. A 'deleted' user whose profile is still served from cache is not
    deleted — they are deleted from disk and alive in memory, which is worse, because now
    nobody is looking for them."""
    uid, _ = populated
    deleted_keys = []

    class FakeRedis:
        def delete(self, k):
            deleted_keys.append(k)

    monkeypatch.setattr("app.stores.redis_state.get_redis", lambda: FakeRedis())
    erasure.erase_user(uid)

    assert any(uid in k for k in deleted_keys), "the cached profile was not purged"


# ── EXPORT (right of access) ─────────────────────────────────────────────────

def test_export_returns_everything_we_hold(populated):
    """You cannot export what you have forgotten you kept — so export and erasure read the
    SAME registry. If a location is missing from one it is missing from both, and this test
    plus the erasure test catch it together."""
    uid, sessions = populated

    data = erasure.export_user(uid)

    assert data["user_id"] == uid
    assert sorted(data["sessions"]) == sorted(sessions)
    d = data["data"]
    assert d["transcripts"] and "my manager is impossible" in json.dumps(d["transcripts"])
    assert d["agentic_context"] and "book the 1:1" in json.dumps(d["agentic_context"])
    assert d["dynamic_vars"], "captured variables missing from the export"
    assert d["crisis_escalations"], "the crisis record was hidden from the person it is about"
    assert d["checkpoints"], "the conversation state was omitted from the export"


def test_the_export_is_json_serialisable(populated):
    """An export that cannot be serialised is not an export. Mongo hands back ObjectIds and
    bytes; they get stringified rather than dropped, because silently omitting a field from a
    subject-access response is its own kind of wrong."""
    uid, _ = populated
    json.dumps(erasure.export_user(uid))       # must not raise


# ── The tripwire: a new store must not be able to hide from erasure ──────────

def test_every_store_that_holds_user_data_is_registered_for_erasure():
    """The latent-breach guard.

    A store added tomorrow that writes user data and is not in `_LOCATIONS` is invisible to
    both erasure and export — and nothing would fail. This scans `app/stores/` for collection
    handles and asserts each one is accounted for, so the person who adds the seventh location
    is told by a failing test rather than by a regulator.
    """
    root = pathlib.Path(__file__).resolve().parent.parent
    used = set()
    for path in (root / "app" / "stores").glob("*.py"):
        src = path.read_text(encoding="utf-8")
        # client[config.X][config.Y]  or  client[config.X]["literal"]
        for m in re.finditer(r"client\[config\.(\w+)\]\[(?:config\.(\w+)|\"(\w+)\")\]", src):
            db, coll_cfg, coll_lit = m.groups()
            used.add((db, coll_cfg or coll_lit))

    registered = {
        (_db_attr(loc.db), _coll_attr(loc.collection)) for loc in erasure._locations()
    }
    missing = {u for u in used if u not in registered}

    assert not missing, (
        f"these store locations are NOT registered for erasure or export: {sorted(missing)}. "
        "A store that holds user data and is not in erasure._locations() cannot be deleted "
        "and cannot be exported — it is a latent breach, and nothing else in the suite will "
        "notice it."
    )


def _db_attr(value: str) -> str:
    """Map a resolved db name back to the config attribute the stores reference."""
    for attr in ("MONGO_BACKEND_DB", "MONGO_RASA_DB", "MONGO_CHECKPOINT_DB"):
        if getattr(config, attr) == value:
            return attr
    return value


def _coll_attr(value: str) -> str:
    for attr in ("MONGO_AGENTIC_COLLECTION", "MONGO_DYNAMIC_VARS_COLLECTION",
                 "MONGO_USER_CONVERSATIONS_COLLECTION", "MONGO_WELLNESS_COLLECTION"):
        if getattr(config, attr, None) == value:
            return attr
    return value


# ── The endpoints. The AUTHORISATION is the dangerous part, not the deletion. ──

@pytest.fixture
def client(mongo):
    from fastapi.testclient import TestClient

    from app.main import create_app

    return TestClient(create_app(), raise_server_exceptions=False)


def test_erasure_requires_explicit_confirmation(client, populated):
    """This destroys a person's coaching history across three databases with no undo. A
    DELETE that fires on a mistyped URL is a bad afternoon."""
    uid, _ = populated

    r = client.request("DELETE", f"/v1/privacy/me?user_id={uid}")

    assert r.status_code == 400
    assert "confirm" in r.json()["detail"].lower()


def test_a_confirmed_erasure_reports_verified(client, populated):
    uid, _ = populated

    r = client.request("DELETE", f"/v1/privacy/me?user_id={uid}&confirm=true")

    assert r.status_code == 200
    body = r.json()
    assert body["verified"] is True
    assert body["deleted"]["checkpoints"] >= 1, "the conversation state was not deleted"


def test_an_incomplete_erasure_returns_500_not_200(client, populated, mongo, monkeypatch):
    """A partial erasure reported as success is the worst possible outcome: the person is
    told their data is gone, and it is not. It must be impossible to mistake for success —
    hence a 5xx, not a 200 with a quiet flag."""
    uid, _ = populated
    ck = mongo[config.MONGO_CHECKPOINT_DB]["checkpoints"]
    monkeypatch.setattr(ck, "delete_many", lambda *a, **k: ck.delete_many.__self__.delete_many(
        {"thread_id": "__nothing__"}) if False else type("R", (), {"deleted_count": 0})())

    r = client.request("DELETE", f"/v1/privacy/me?user_id={uid}&confirm=true")

    assert r.status_code == 500
    assert r.json()["verified"] is False
    assert "INCOMPLETE" in r.json()["detail"]


def test_the_export_endpoint_returns_a_downloadable_document(client, populated):
    uid, _ = populated

    r = client.get(f"/v1/privacy/me/export?user_id={uid}")

    assert r.status_code == 200
    assert "attachment" in r.headers.get("content-disposition", "")
    assert r.json()["data"]["transcripts"]


def test_there_is_no_user_id_in_the_path(client):
    """The subject comes from the SIGNED TOKEN, never from a path parameter.

    `DELETE /v1/users/{user_id}` is one enumeration attack away from erasing somebody else's
    coaching history. There must be no such route — a caller cannot even express the erasure
    of another person, because the request has nowhere to put their id.
    """
    from app.main import create_app

    paths = [r.path for r in create_app().routes]
    for p in paths:
        if "privacy" in p:
            assert "{user_id}" not in p and "{id}" not in p, (
                f"{p} takes a subject from the path — that is an erasure endpoint pointed at "
                "other people"
            )


# ── FORGET: the narrower promise ─────────────────────────────────────────────
#
# "Forget what you learned about me" is not "delete everything". The distinction is the
# feature, and getting it wrong is a bug in both directions: burning the person's diary on
# a convenience button, or leaving the coach's memory intact when they asked it to forget.


def _wellness_coll(mongo):
    return mongo[config.MONGO_BACKEND_DB][config.MONGO_WELLNESS_COLLECTION]


@pytest.fixture
def with_diary(populated, mongo):
    """The populated person, plus their own writing — the thing forget must NOT touch."""
    user_id, sessions = populated
    _wellness_coll(mongo).insert_one({
        "user_id": user_id,
        "journal": [{"id": "j1", "ts": "2026-07-01T09:00:00+00:00", "body": "my own words"}],
        "moods": [{"id": "m1", "ts": "2026-07-01T09:00:00+00:00", "mood": "anxious"}],
    })
    return user_id, sessions


def test_forget_wipes_the_coach_memory(with_diary, mongo):
    user_id, sessions = with_diary
    report = erasure.forget_user(user_id)
    assert report["verified"] is True

    conv = mongo[config.MONGO_RASA_DB][config.MONGO_USER_CONVERSATIONS_COLLECTION]
    assert conv.count_documents({"user_id": user_id}) == 0, "the conversation survived"
    ag = mongo[config.MONGO_BACKEND_DB][config.MONGO_AGENTIC_COLLECTION]
    assert ag.count_documents({"user_id": user_id}) == 0, "the derived state survived"
    dv = mongo[config.MONGO_BACKEND_DB][config.MONGO_DYNAMIC_VARS_COLLECTION]
    assert dv.count_documents({"user_id": user_id}) == 0, "the captured variables survived"
    for coll in ("checkpoints", "checkpoint_writes"):
        left = mongo[config.MONGO_CHECKPOINT_DB][coll].count_documents({"thread_id": {"$in": sessions}})
        assert left == 0, f"{coll} survived — the graph still remembers the whole conversation"


def test_forget_keeps_the_persons_own_writing(with_diary, mongo):
    """THE point of the narrower promise. "Start the coach fresh" must not burn the diary —
    the journal and mood check-ins are their content, with their own delete buttons."""
    user_id, _ = with_diary
    erasure.forget_user(user_id)
    doc = _wellness_coll(mongo).find_one({"user_id": user_id}) or {}
    assert doc.get("journal"), "the journal was destroyed by a memory wipe"
    assert doc.get("moods"), "the mood check-ins were destroyed by a memory wipe"


def test_forget_keeps_the_safety_record(with_diary, mongo):
    """crisis_escalations records THAT someone was in crisis, never what they said. A
    convenience button must not erase a safety signal; erase_user still does, because that
    is a statutory right rather than a preference."""
    user_id, _ = with_diary
    erasure.forget_user(user_id)
    esc = mongo[config.MONGO_BACKEND_DB]["crisis_escalations"]
    assert esc.count_documents({"user_id": user_id}) == 1


def test_forget_reports_what_it_deleted_and_what_it_kept(with_diary):
    user_id, _ = with_diary
    report = erasure.forget_user(user_id)
    assert report["deleted"]["transcripts"] == 2
    assert report["deleted"]["checkpoints"] == 2
    assert set(report["kept"]) == {"wellness", "crisis_escalations"}


def test_erasure_still_removes_everything_forget_spares(with_diary, mongo):
    """The two operations must not drift: what forget keeps, erase must still take."""
    user_id, _ = with_diary
    report = erasure.erase_user(user_id)
    assert report["verified"] is True
    assert _wellness_coll(mongo).count_documents({"user_id": user_id}) == 0
    assert mongo[config.MONGO_BACKEND_DB]["crisis_escalations"].count_documents({"user_id": user_id}) == 0


def test_forget_survives_a_store_that_falls_over(with_diary, monkeypatch):
    """Mid-incident, a failed wipe must report verified:false — never claim the coach
    forgot when it did not."""
    user_id, _ = with_diary

    class Boom:
        def delete_many(self, *_a, **_k):
            raise RuntimeError("mongo is on fire")

        def count_documents(self, *_a, **_k):
            return 1

    monkeypatch.setattr(erasure, "_coll", lambda _loc: Boom())
    report = erasure.forget_user(user_id)
    assert report["verified"] is False
    assert report["remaining"], "a failed wipe reported nothing left behind"


# ── The checkpointer as it ACTUALLY ships ────────────────────────────────────
#
# Everything above models the MongoDB saver: it inserts documents into mongomock
# collections named `checkpoints`/`checkpoint_writes`. That saver is a FALLBACK. Postgres
# is the default (ARCHITECTURE.md §Storage consolidation), and there LangGraph owns its own
# tables with its own schema — which is why this whole area was broken while the suite was
# green.


def test_erasure_searches_for_the_thread_id_the_engine_actually_writes(mongo, user_id):
    """The bug that made every erasure a lie, pinned as a contract between two modules.

    The checkpointer writes `"<org>:<session_id>"`. Erasure searched for the bare
    `session_id`, matched nothing, deleted nothing, re-scanned with the same wrong filter,
    found nothing remaining, and reported `verified: True`. Measured against the live
    database, where the same id appeared in both columns:

        checkpoints.thread_id            6da49ab55dac…:157f8ae79fcc…
        user_conversations.session_id    157f8ae79fcc…

    Both sides now build the key with `tenancy.thread_id_for`, so this asserts they agree
    rather than trusting that they do.
    """
    from app.tenancy import ctx_org_id, thread_id_for

    token = ctx_org_id.set("acme")
    try:
        loc = next(l for l in erasure._locations() if l.key == "thread_id")
        flt = erasure._filter_for(loc, user_id, ["s1"])
        wanted = flt["thread_id"]["$in"]

        assert thread_id_for("s1") == "acme:s1"
        assert "acme:s1" in wanted, (
            "erasure is not looking for the key the checkpointer wrote — it will delete "
            "nothing and report verified"
        )
        # ...and still reaches threads written before the org prefix existed.
        assert "s1" in wanted
    finally:
        ctx_org_id.reset(token)


def test_no_sessions_never_becomes_a_match_everything_filter(mongo, user_id):
    """An empty $in matches every row, and this is a delete path."""
    loc = next(l for l in erasure._locations() if l.key == "thread_id")
    assert erasure._filter_for(loc, user_id, []) is None


def test_the_registry_follows_the_checkpointer_that_will_be_built(monkeypatch):
    """Only the Mongo saver stores checkpoints as documents we can read. On Postgres they
    are LangGraph's tables (`SELECT doc FROM checkpoints` → UndefinedColumn → export 500);
    on SQLite they are a file our client cannot see at all, which is worse, because the
    delete and the re-scan both find nothing and erasure reports success."""
    monkeypatch.setattr(config, "MONGO_DB_URL", "mongodb://fake")
    monkeypatch.delenv("POSTGRES_URL", raising=False)
    monkeypatch.setattr(config, "POSTGRES_URL", "", raising=False)
    assert erasure._checkpoint_backend() == "document"
    assert {l.label for l in erasure._locations() if l.key == "thread_id"} == {
        "checkpoints", "checkpoint_writes"}

    monkeypatch.setenv("POSTGRES_URL", "postgresql://x/y")
    assert erasure._checkpoint_backend() == "checkpointer"
    ckpt = [l for l in erasure._locations() if l.key == "thread_id"]
    assert [l.via for l in ckpt] == ["checkpointer"], "Postgres would be read as documents"

    # SQLite/memory: no Postgres, no Mongo. The silent-false-success case.
    monkeypatch.delenv("POSTGRES_URL", raising=False)
    monkeypatch.setattr(config, "MONGO_DB_URL", "")
    assert erasure._checkpoint_backend() == "checkpointer"


@requires_pg
def test_erasure_actually_clears_the_real_postgres_checkpointer(pgdb, monkeypatch, mongo,
                                                                user_id):
    """The whole point, against the store that ships.

    Writes a real checkpoint through a real PostgresSaver, then erases the person and reads
    LangGraph's tables DIRECTLY — never through the code under test — to prove the message
    history is gone. Covers `checkpoint_blobs`, which the old registry never listed and
    which is where Postgres actually puts the state: an erasure that only fixed the
    UndefinedColumn crash would still have left the conversation on disk.
    """
    from langgraph.checkpoint.postgres import PostgresSaver
    from psycopg_pool import ConnectionPool

    from app.tenancy import ctx_org_id, thread_id_for
    from tests.conftest import PG_URL

    monkeypatch.setenv("POSTGRES_URL", PG_URL)
    pool = ConnectionPool(conninfo=PG_URL, kwargs={"autocommit": True, "prepare_threshold": 0},
                          open=True)
    saver = PostgresSaver(pool)
    saver.setup()
    monkeypatch.setattr(erasure, "_SAVER", saver)  # the cached saver the module would build

    token = ctx_org_id.set("acme")
    try:
        session_id = "sess-erasure-probe"
        thread_id = thread_id_for(session_id)

        # The person's transcript — how erasure finds their sessions at all.
        conv = mongo[config.MONGO_RASA_DB][config.MONGO_USER_CONVERSATIONS_COLLECTION]
        conv.insert_one({"user_id": user_id, "session_id": session_id})

        cfg = {"configurable": {"thread_id": thread_id, "checkpoint_ns": ""}}
        # new_versions must name the channel, or the saver writes NO blob — and the blob is
        # where Postgres puts the message history. (First draft passed {} here; the guard
        # assertion below caught it, which is the only reason the blob check means anything.)
        saver.put(cfg, {"id": "c1", "ts": "2026-07-17T00:00:00+00:00", "v": 1,
                        "channel_values": {"messages": ["I want to kill myself"]},
                        "channel_versions": {"messages": "00000000000000000000000000000001."},
                        "versions_seen": {}},
                  {"source": "input", "step": 1},
                  {"messages": "00000000000000000000000000000001."})

        def rows(table):
            with pool.connection() as conn:
                return conn.execute(
                    f'SELECT count(*) FROM {table} WHERE thread_id = %s', (thread_id,)
                ).fetchone()[0]

        assert rows("checkpoints") > 0, "the fixture wrote nothing — this would pass vacuously"
        assert rows("checkpoint_blobs") > 0, "no blob written; the blob assertion below is empty"

        # Right of access must not 500 (it did: SELECT doc FROM checkpoints).
        bundle = erasure.export_user(user_id)
        assert "error" not in str(bundle["data"]["checkpoints"]), (
            f"export could not read the checkpointer: {bundle['data']['checkpoints']}"
        )

        report = erasure.erase_user(user_id)

        assert rows("checkpoints") == 0, "THE CONVERSATION STATE SURVIVED ERASURE"
        assert rows("checkpoint_blobs") == 0, (
            "the message history survived in checkpoint_blobs — the table the old registry "
            "never listed"
        )
        assert rows("checkpoint_writes") == 0, "the write-ahead log survived"
        assert report["verified"] is True, f"erasure could not verify itself: {report}"
        assert report["deleted"]["checkpoints"] == 1
    finally:
        ctx_org_id.reset(token)
        with pool.connection() as conn:
            for t in ("checkpoints", "checkpoint_blobs", "checkpoint_writes"):
                conn.execute(f'DELETE FROM {t} WHERE thread_id LIKE %s', ("acme:sess-erasure-probe%",))
        pool.close()
