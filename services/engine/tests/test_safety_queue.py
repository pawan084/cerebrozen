"""The safety queue — the read side of crisis escalation.

Escalation writes a signal (never the disclosure). These tests pin the two properties
the admin surface depends on: the queue is org-scoped, and it carries the signal fields
and nothing that could identify what a person said.
"""

from fastapi.testclient import TestClient

from app import config
from app.safety import escalation
from app.tenancy import ctx_org_id


def _esc_coll(mongo):
    return mongo[config.MONGO_BACKEND_DB]["crisis_escalations"]


# ── the store helper ─────────────────────────────────────────────────────────

def test_list_is_empty_without_a_store():
    # No `mongo` fixture: get_client() sees a blank MONGO_DB_URL and returns None.
    assert escalation.list_escalations() == []


def test_a_store_error_returns_an_empty_queue_not_an_exception(mongo, monkeypatch):
    monkeypatch.setattr(
        "app.stores.mongo.get_client",
        lambda: (_ for _ in ()).throw(RuntimeError("mongo is on fire")),
    )
    # Never raises into the admin surface, even mid-incident.
    assert escalation.list_escalations() == []


def test_escalate_stamps_the_active_org(mongo):
    tok = ctx_org_id.set("acme")
    try:
        # No endpoint configured → not armed → still persisted (delivered=False).
        escalation.escalate(user_id="u1", session_id="s1", detected_by="lexicon")
    finally:
        ctx_org_id.reset(tok)
    rec = _esc_coll(mongo).find_one({"user_id": "u1"})
    assert rec["org_id"] == "acme"
    assert rec["delivered"] is False
    assert rec["detected_by"] == "lexicon"


def test_org_stamp_falls_back_when_tenancy_is_unavailable(mongo, monkeypatch):
    def boom():
        raise RuntimeError("no tenancy")

    monkeypatch.setattr("app.tenancy.current_org", boom)
    # A tenancy failure must not lose the signal — it lands under the default org.
    escalation.escalate(user_id="u2", session_id="s2")
    assert _esc_coll(mongo).find_one({"user_id": "u2"})["org_id"] == "default"


def test_the_queue_is_scoped_to_the_active_org(mongo):
    _esc_coll(mongo).insert_many([
        {"org_id": "acme", "user_id": "a", "session_id": "sa", "at": "2026-07-16T01:00:00"},
        {"org_id": "globex", "user_id": "g", "session_id": "sg", "at": "2026-07-16T02:00:00"},
    ])
    tok = ctx_org_id.set("acme")
    try:
        rows = escalation.list_escalations()
    finally:
        ctx_org_id.reset(tok)
    assert [r["user_id"] for r in rows] == ["a"]  # globex's escalation is invisible to acme


def test_the_default_org_also_sees_legacy_records_without_an_org(mongo):
    _esc_coll(mongo).insert_many([
        {"user_id": "legacy", "session_id": "sl", "at": "2026-07-16T00:00:00"},  # pre-tenancy
        {"org_id": "default", "user_id": "new", "session_id": "sn", "at": "2026-07-16T00:30:00"},
        {"org_id": "acme", "user_id": "other", "session_id": "so", "at": "2026-07-16T00:45:00"},
    ])
    rows = escalation.list_escalations()  # runs under DEFAULT_ORG
    users = {r["user_id"] for r in rows}
    assert users == {"legacy", "new"}  # legacy + default, never acme's


def test_newest_first_and_limit(mongo):
    _esc_coll(mongo).insert_many([
        {"org_id": "default", "user_id": f"u{i}", "session_id": f"s{i}", "at": f"2026-07-16T0{i}:00:00"}
        for i in range(1, 5)
    ])
    rows = escalation.list_escalations(limit=2)
    assert [r["user_id"] for r in rows] == ["u4", "u3"]  # sorted desc by `at`, capped at 2


# ── the HTTP surface ─────────────────────────────────────────────────────────

def _client():
    from app.main import create_app

    return TestClient(create_app(), raise_server_exceptions=False)


def test_endpoint_returns_the_signal_and_the_armed_flags(mongo):
    _esc_coll(mongo).insert_one({
        "org_id": "default", "user_id": "u9", "session_id": "s9",
        "detected_by": "classifier", "delivered": True, "at": "2026-07-16T03:00:00",
    })
    r = _client().get("/v1/safety/escalations")
    assert r.status_code == 200
    body = r.json()
    assert body["count"] == 1
    assert body["armed"] is False  # no CEREBROZEN_CRISIS_ESCALATION_URL in the test env
    assert "classifier_enabled" in body
    row = body["escalations"][0]
    assert row["user_id"] == "u9" and row["detected_by"] == "classifier"


def test_endpoint_never_leaks_content(mongo):
    # Even if a bad writer stashed content on the record, the projection must not return it.
    _esc_coll(mongo).insert_one({
        "org_id": "default", "user_id": "u", "session_id": "s", "at": "2026-07-16T03:00:00",
        "message": "the disclosure that must never reach an operator",
    })
    body = _client().get("/v1/safety/escalations").json()
    blob = str(body).lower()
    assert "disclosure" not in blob and "message" not in blob


def test_endpoint_rejects_an_out_of_range_limit():
    r = _client().get("/v1/safety/escalations?limit=9999")
    assert r.status_code == 422  # Query(le=500) guards it
