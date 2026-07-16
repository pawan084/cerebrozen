"""Check-in nudge dispatch — the delivery the scheduler never had.

The scheduler decides who's due; these tests pin the delivery half: due check-ins
are found across users, a nudge carries the signal and nothing else, it degrades to
a logged no-op without an endpoint, and the queue is org-scoped.
"""

from datetime import date

from fastapi.testclient import TestClient

from app import config, notifications


def _agentic(mongo):
    return mongo[config.MONGO_BACKEND_DB][config.MONGO_AGENTIC_COLLECTION]


def _nudges(mongo):
    return mongo[config.MONGO_BACKEND_DB]["checkin_nudges"]


TODAY = date(2026, 7, 16)


def _seed_user(mongo, user_id, org_id="default", *, session_date, status="saved", session_id="s1",
               checked_in=()):
    _agentic(mongo).insert_one({
        "user_id": user_id, "org_id": org_id,
        "actions": [{"full_text": "book the 1:1", "session_id": session_id,
                     "session_date": session_date, "status": status}],
        "checkin_complete_sessions": list(checked_in),
    })


# ── the scan ─────────────────────────────────────────────────────────────────

def test_scan_is_empty_without_a_store():
    assert notifications.due_nudges(today=TODAY) == []


def test_scan_finds_only_users_with_a_due_checkin(mongo):
    _seed_user(mongo, "overdue", session_date="2026-07-01")   # 15 days → due
    _seed_user(mongo, "recent", session_date="2026-07-15")    # 1 day → not due
    _seed_user(mongo, "done", session_date="2026-07-01", session_id="s9", checked_in=["s9"])  # R3
    due = notifications.due_nudges(today=TODAY)
    assert {r["user_id"] for r in due} == {"overdue"}
    rec = due[0]
    assert rec["due_count"] == 1 and rec["session_ids"] == ["s1"] and rec["org_id"] == "default"


def test_a_nudge_carries_no_commitment_body(mongo):
    _seed_user(mongo, "u", session_date="2026-07-01")
    rec = notifications.due_nudges(today=TODAY)[0]
    assert "book the 1:1" not in str(rec) and "actions" not in rec


def test_scan_survives_a_store_error(mongo, monkeypatch):
    monkeypatch.setattr("app.stores.mongo.get_client",
                        lambda: (_ for _ in ()).throw(RuntimeError("boom")))
    assert notifications.due_nudges(today=TODAY) == []


# ── delivery ─────────────────────────────────────────────────────────────────

def test_delivery_off_records_the_attempt_and_returns_false(mongo):
    rec = {"user_id": "u", "org_id": "default", "due_count": 2,
           "session_ids": ["s1"], "at": "2026-07-16T00:00:00"}
    assert notifications.deliver_nudge(rec) is False  # no CEREBROZEN_NUDGE_DELIVERY_URL
    stored = _nudges(mongo).find_one({"user_id": "u"})
    assert stored["delivered"] is False and stored["due_count"] == 2


def test_delivery_posts_the_signal_when_armed(mongo, monkeypatch):
    monkeypatch.setenv("CEREBROZEN_NUDGE_DELIVERY_URL", "https://hook.example/nudge")
    sent = {}

    class _Resp:
        status_code = 200
        def raise_for_status(self): return None

    def _post(url, **kw):
        sent["url"] = url
        sent["json"] = kw.get("json")
        return _Resp()

    monkeypatch.setattr("httpx.post", _post)
    rec = {"user_id": "u", "org_id": "acme", "due_count": 1,
           "session_ids": ["s1"], "at": "2026-07-16T00:00:00"}
    assert notifications.deliver_nudge(rec) is True
    assert sent["url"] == "https://hook.example/nudge"
    assert "book" not in str(sent["json"])  # still signal-only over the wire
    assert _nudges(mongo).find_one({"user_id": "u"})["delivered"] is True


def test_delivery_failure_is_swallowed_and_recorded(mongo, monkeypatch):
    monkeypatch.setenv("CEREBROZEN_NUDGE_DELIVERY_URL", "https://hook.example/nudge")

    def _boom(url, **kw):
        raise RuntimeError("endpoint on fire")

    monkeypatch.setattr("httpx.post", _boom)
    rec = {"user_id": "u", "org_id": "default", "due_count": 1,
           "session_ids": ["s1"], "at": "2026-07-16T00:00:00"}
    assert notifications.deliver_nudge(rec) is False
    assert _nudges(mongo).find_one({"user_id": "u"})["delivered"] is False


def test_bad_timeout_env_falls_back(monkeypatch):
    monkeypatch.setenv("CEREBROZEN_NUDGE_DELIVERY_TIMEOUT_S", "not-a-number")
    assert notifications._timeout_s() == 5.0


# ── dispatch + list ──────────────────────────────────────────────────────────

def test_dispatch_scans_and_delivers(mongo):
    _seed_user(mongo, "a", session_date="2026-07-01")
    _seed_user(mongo, "b", session_date="2026-07-02")
    _seed_user(mongo, "c", session_date="2026-07-16")  # not due
    summary = notifications.dispatch(today=TODAY)
    assert summary["due"] == 2 and summary["armed"] is False and summary["delivered"] == 0
    assert _nudges(mongo).count_documents({}) == 2  # both recorded as undelivered


def test_list_is_org_scoped(mongo):
    _nudges(mongo).insert_many([
        {"org_id": "acme", "user_id": "a", "at": "2026-07-16T02:00:00", "delivered": True},
        {"org_id": "globex", "user_id": "g", "at": "2026-07-16T03:00:00", "delivered": True},
    ])
    from app.tenancy import ctx_org_id
    tok = ctx_org_id.set("acme")
    try:
        rows = notifications.list_nudges()
    finally:
        ctx_org_id.reset(tok)
    assert [r["user_id"] for r in rows] == ["a"]


# ── HTTP surface ─────────────────────────────────────────────────────────────

def _client():
    from app.main import create_app

    return TestClient(create_app(), raise_server_exceptions=False)


def test_dispatch_endpoint_returns_counts(mongo):
    _seed_user(mongo, "a", session_date="2026-07-01")
    r = _client().post("/v1/nudges/dispatch")
    assert r.status_code == 200
    body = r.json()
    assert body["due"] >= 1 and body["armed"] is False


def test_list_endpoint_is_signal_only(mongo):
    _nudges(mongo).insert_one({"org_id": "default", "user_id": "u", "due_count": 3,
                               "session_ids": ["s1"], "at": "2026-07-16T00:00:00",
                               "delivered": True, "secret": "must not leak"})
    body = _client().get("/v1/nudges").json()
    assert body["count"] == 1 and "secret" not in str(body).lower()
    assert body["nudges"][0]["due_count"] == 3


def test_health_surfaces_the_nudge_channel():
    body = _client().get("/health").json()
    assert body["nudges"]["nudge_delivery_armed"] is False


# ── defensive paths ──────────────────────────────────────────────────────────

def test_scan_uses_today_by_default(mongo):
    # today=None → real clock; a very old action is due under any real date.
    _seed_user(mongo, "ancient", session_date="2020-01-01")
    assert any(r["user_id"] == "ancient" for r in notifications.due_nudges())


def test_scan_skips_a_doc_with_no_user_id(mongo):
    _agentic(mongo).insert_one({"org_id": "default", "actions": [
        {"full_text": "x", "session_id": "s", "session_date": "2020-01-01", "status": "saved"}]})
    assert notifications.due_nudges(today=TODAY) == []


def test_list_is_empty_without_a_store():
    assert notifications.list_nudges() == []


def test_list_survives_a_store_error(mongo, monkeypatch):
    monkeypatch.setattr("app.stores.mongo.get_client",
                        lambda: (_ for _ in ()).throw(RuntimeError("boom")))
    assert notifications.list_nudges() == []


def test_persist_without_a_store_is_a_noop():
    # No mongo fixture → get_client() is None; must not raise.
    notifications._persist({"user_id": "u", "at": "t"}, delivered=False)


def test_persist_survives_a_store_error(monkeypatch):
    monkeypatch.setattr("app.stores.mongo.get_client",
                        lambda: (_ for _ in ()).throw(RuntimeError("boom")))
    notifications._persist({"user_id": "u", "at": "t"}, delivered=True)
