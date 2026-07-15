"""Self-reported wellness: the journal, sleep, and mood check-ins.

Three things are being defended here, and only one of them is "the feature works":

1. **It is theirs.** The subject comes from the token and nowhere else — there is no
   request that means "somebody else's journal".
2. **Self-report is not inference.** Regulated mode turns off the AGENT reading emotions
   off a worker. It does not confiscate the worker's own diary. A test pins the
   distinction, because prose in a config file is not a guarantee.
3. **"Counts, never content."** This content lives in the engine, and no HR/admin surface
   can reach it. The platform (the org database) never sees a word of it.
"""

import jwt
import pytest

from app import config
from app.stores import wellness
from app.tenancy import ctx_org_id


@pytest.fixture
def as_org():
    tokens = []

    def _switch(org: str) -> str:
        tokens.append(ctx_org_id.set(org))
        return org

    yield _switch
    for tok in reversed(tokens):
        ctx_org_id.reset(tok)


@pytest.fixture
def authed(monkeypatch):
    """The app with auth ENFORCED, plus a token factory (subject == the person)."""
    from fastapi.testclient import TestClient

    from app.main import create_app

    monkeypatch.setenv("AUTH_DEV_BYPASS", "")
    monkeypatch.setattr(config, "ENV", "production")
    monkeypatch.setattr(config, "JWT_SECRET", "s3cret")

    def token_for(user_id: str, org: str = "acme") -> dict:
        # The shape the PLATFORM actually mints (sub + user.username + org_id) — the
        # privacy endpoints read `sub`, the coaching ones read `user.username`, and a
        # fixture that carries only one of them tests a token no client ever sends.
        raw = jwt.encode(
            {"sub": user_id, "user": {"username": user_id}, "org_id": org, "role": "user"},
            "s3cret",
            algorithm=config.JWT_ALGORITHM,
        )
        return {"Authorization": f"Bearer {raw}"}

    return TestClient(create_app(), raise_server_exceptions=False), token_for


NIGHT = {"date": "2026-07-14", "bedtime": "23:30", "wake_time": "07:00", "quality": 4,
         "awakenings": 1}


# ── it is theirs, and only theirs ────────────────────────────────────────────


def test_a_journal_is_readable_only_by_the_person_who_wrote_it(authed, mongo):
    """The whole design in one test: two people, one endpoint, no parameter that could
    ever name the other person."""
    client, token_for = authed

    client.post("/v1/wellness/journal", json={"body": "the meeting went badly"},
                headers=token_for("alice"))

    mine = client.get("/v1/wellness/journal", headers=token_for("alice")).json()
    theirs = client.get("/v1/wellness/journal", headers=token_for("bob")).json()

    assert [e["body"] for e in mine] == ["the meeting went badly"]
    assert theirs == [], "another user read the journal"


def test_there_is_no_way_to_ask_for_someone_elses_entries(authed, mongo):
    """A `?user_id=` must not be honoured — not on a diary. (The coaching turn endpoints
    DO accept one, for service-to-service callers; this is why these routes deliberately
    do not use resolve_user_id.)"""
    client, token_for = authed
    client.post("/v1/wellness/journal", json={"body": "private"}, headers=token_for("alice"))

    body = client.get("/v1/wellness/journal?user_id=alice", headers=token_for("bob")).json()

    assert body == [], "a query parameter overrode the token's subject"


def test_a_token_naming_no_user_reads_nothing(authed, mongo):
    client, _ = authed
    userless = jwt.encode({"org_id": "acme"}, "s3cret", algorithm=config.JWT_ALGORITHM)

    r = client.get("/v1/wellness/journal", headers={"Authorization": f"Bearer {userless}"})

    assert r.status_code == 400
    assert "JWT" in r.json()["detail"]


def test_another_tenant_cannot_read_the_same_users_entries(authed, mongo):
    """Same username, different org: the org claim scopes the store, so a colliding user
    id in another tenant is a different person."""
    client, token_for = authed
    client.post("/v1/wellness/journal", json={"body": "acme's"}, headers=token_for("u1", "acme"))

    other = client.get("/v1/wellness/journal", headers=token_for("u1", "globex")).json()

    assert other == []


def test_deleting_someone_elses_entry_is_a_404(authed, mongo):
    client, token_for = authed
    entry = client.post("/v1/wellness/journal", json={"body": "mine"},
                        headers=token_for("alice")).json()

    r = client.delete(f"/v1/wellness/journal/{entry['id']}", headers=token_for("bob"))

    assert r.status_code == 404
    assert client.get("/v1/wellness/journal", headers=token_for("alice")).json(), "it was deleted"


def test_the_owner_can_delete_their_own_entry(authed, mongo):
    client, token_for = authed
    entry = client.post("/v1/wellness/journal", json={"body": "mine"},
                        headers=token_for("alice")).json()

    r = client.delete(f"/v1/wellness/journal/{entry['id']}", headers=token_for("alice"))

    assert r.status_code == 200
    assert client.get("/v1/wellness/journal", headers=token_for("alice")).json() == []


# ── self-report is not inference (the regulated-mode line) ───────────────────


def test_a_regulated_tenant_keeps_their_own_journal(authed, mongo, monkeypatch):
    """Regulated mode switches off the AGENT inferring emotions about a worker. It does
    not take away the worker's diary — they wrote it, about themselves, for themselves.
    Conflating the two would mean a regulated tenant loses features for no gain in
    anyone's rights."""
    monkeypatch.setattr(config, "EMOTION_CAPTURE_ENABLED", False)
    monkeypatch.setattr(config, "PERSON_SCORING_ENABLED", False)
    client, token_for = authed

    r = client.post("/v1/wellness/moods", json={"mood": "wired", "intensity": 4},
                    headers=token_for("alice"))

    assert r.status_code == 201, "regulated mode confiscated the user's own check-in"
    assert r.json()["mood"] == "wired"


def test_agent_inference_stays_refused_while_self_report_is_allowed(mongo, monkeypatch, as_org):
    """The two paths, side by side, in the one test — so nobody 'simplifies' them into
    one flag later. Same tenant, same moment: the agent may not write a mood it inferred;
    the person may write the mood they typed."""
    from app.stores import agentic

    monkeypatch.setattr(config, "EMOTION_CAPTURE_ENABLED", False)
    as_org("acme")

    inferred = agentic.save_mood_capture("u1", "s1", {"mapped_emotions": ["anxious"]})
    self_reported = wellness.add_mood("u1", "anxious", note="I typed this myself")

    assert inferred is False, "the agent inferred an emotion in a regulated tenant"
    assert self_reported is not None, "the person's own check-in was refused"


def test_a_tenant_can_switch_self_report_storage_off_entirely(authed, mongo, monkeypatch):
    """For the tenant whose counsel wants none of it on our disks. The refusal is a 409,
    never a cheerful 200 — the app has to be able to tell a save from a non-save, or it
    prints "Saved" over a write that never happened."""
    monkeypatch.setattr(config, "SELF_REPORT_WELLNESS_ENABLED", False)
    client, token_for = authed

    r = client.post("/v1/wellness/journal", json={"body": "anything"},
                    headers=token_for("alice"))

    assert r.status_code == 409
    assert client.get("/v1/wellness/journal", headers=token_for("alice")).json() == []


# ── consent is enforced, not merely recorded ─────────────────────────────────


def _token(user_id: str, consent: dict | None = None, org: str = "acme") -> dict:
    payload = {"sub": user_id, "user": {"username": user_id}, "org_id": org, "role": "user"}
    if consent is not None:
        payload["consent"] = consent
    raw = jwt.encode(payload, "s3cret", algorithm=config.JWT_ALGORITHM)
    return {"Authorization": f"Bearer {raw}"}


ALL_CONSENTS = {
    "mood_history": True, "ai_memory": True, "journal_memory": True,
    "sleep_history": True, "voice_storage": True, "model_training": True,
}


@pytest.mark.parametrize(
    "path,payload,key",
    [
        ("/v1/wellness/journal", {"body": "private"}, "journal_memory"),
        ("/v1/wellness/sleep", NIGHT, "sleep_history"),
        ("/v1/wellness/moods", {"mood": "tired"}, "mood_history"),
    ],
)
def test_a_category_the_person_refused_is_not_written_down(authed, mongo, path, payload, key):
    """The toggle in Privacy & memory has to BITE. A consent switch that persists a
    preference and changes no behaviour is theatre — worse than none, because the person
    believes it did something. The platform signs the six flags into the token; the engine
    refuses to keep what they said no to."""
    client, _ = authed
    denied = {**ALL_CONSENTS, key: False}

    r = client.post(path, json=payload, headers=_token("alice", denied))

    assert r.status_code == 403
    assert key.replace("_", " ") in r.json()["detail"]
    listing = path.rsplit("/", 1)[0] if path.endswith("summary") else path
    assert client.get(listing, headers=_token("alice", ALL_CONSENTS)).json() == [], (
        "the entry was stored despite the refusal"
    )


def test_consent_given_lets_the_write_through(authed, mongo):
    client, _ = authed

    r = client.post("/v1/wellness/journal", json={"body": "kept"},
                    headers=_token("alice", ALL_CONSENTS))

    assert r.status_code == 201


def test_a_token_with_no_consent_claim_is_not_treated_as_a_refusal(authed, mongo):
    """Absence is not refusal. An engine that hard-denied every token minted before this
    claim existed would break every client, and the person could do nothing about it.
    Putting the truth in the claim is the platform's job — it always mints all six."""
    client, _ = authed

    r = client.post("/v1/wellness/journal", json={"body": "legacy token"},
                    headers=_token("alice", consent=None))

    assert r.status_code == 201


def test_a_client_cannot_forge_its_own_consent(authed, mongo):
    """The claim is inside a signature we control. A phone that would rather not be told
    'no' cannot simply say yes: an unsigned or wrongly-signed token is a 401, and the
    consent inside a VALID token was written by the platform, from its own database."""
    client, _ = authed
    forged = jwt.encode(
        {"sub": "alice", "user": {"username": "alice"}, "org_id": "acme",
         "consent": ALL_CONSENTS},
        "not-the-real-secret",
        algorithm=config.JWT_ALGORITHM,
    )

    r = client.post("/v1/wellness/journal", json={"body": "x"},
                    headers={"Authorization": f"Bearer {forged}"})

    assert r.status_code == 401


def test_reading_back_what_they_wrote_is_never_blocked_by_consent(authed, mongo):
    """Withdrawing consent stops us KEEPING more. It must not lock a person out of the
    entries they already wrote — that would be holding their diary hostage to a switch."""
    client, _ = authed
    client.post("/v1/wellness/journal", json={"body": "written while consented"},
                headers=_token("alice", ALL_CONSENTS))

    withdrawn = {**ALL_CONSENTS, "journal_memory": False}
    entries = client.get("/v1/wellness/journal", headers=_token("alice", withdrawn)).json()

    assert [e["body"] for e in entries] == ["written while consented"]


# ── "counts, never content" ──────────────────────────────────────────────────


def test_the_engine_exposes_no_org_wide_view_of_wellness_content(authed):
    """The structural half of the promise. HR analytics reads a different database in a
    different service; this asserts the engine grows no aggregate of its own — no route
    that returns more than one person's wellness data, however it is authenticated."""
    client, _ = authed
    wellness_routes = [
        r.path for r in client.app.routes if getattr(r, "path", "").startswith("/v1/wellness")
    ]

    assert wellness_routes, "the router is not mounted"
    for path in wellness_routes:
        assert "{user_id}" not in path, f"{path} names a user — a diary is not addressable"
        for word in ("org", "admin", "aggregate", "team", "everyone", "all"):
            assert word not in path.lower(), f"{path} looks like an org-wide wellness view"


def test_wellness_is_erased_with_the_person(authed, mongo):
    """Registered in erasure._locations() — the store scan in test_privacy.py fails the
    build if it is not, but the delete is what the person was actually promised."""
    from app.privacy import erasure

    client, token_for = authed
    client.post("/v1/wellness/journal", json={"body": "delete me"}, headers=token_for("alice"))
    client.post("/v1/wellness/sleep", json=NIGHT, headers=token_for("alice"))

    response = client.request(
        "DELETE", "/v1/privacy/me?confirm=true", headers=token_for("alice")
    )
    report = response.json()

    assert response.status_code == 200, "erasure did not verify — wellness data survived it"
    assert report["verified"] is True
    assert report["deleted"]["wellness"] >= 1, "the journal was not among what was deleted"
    assert any(loc.label == "wellness" for loc in erasure._locations())
    assert client.get("/v1/wellness/journal", headers=token_for("alice")).json() == []
    assert client.get("/v1/wellness/sleep", headers=token_for("alice")).json() == []


def test_wellness_is_included_in_the_data_export(authed, mongo):
    """Right of access: what we hold about them includes what they wrote."""
    client, token_for = authed
    client.post("/v1/wellness/journal", json={"body": "in my export"},
                headers=token_for("alice"))

    export = client.get("/v1/privacy/me/export", headers=token_for("alice")).json()

    dumped = str(export)
    assert "in my export" in dumped, "the journal was not in the person's own export"


# ── the sleep contract (the client's field names, not ours) ──────────────────


def test_a_night_that_crosses_midnight_is_not_negative(mongo, as_org):
    """23:30 → 07:00 is seven and a half hours. A naive subtraction makes it minus
    sixteen, which is most people, most nights."""
    as_org("acme")

    assert wellness.duration_minutes("23:30", "07:00") == 450
    assert wellness.duration_minutes("00:15", "06:45") == 390
    assert wellness.duration_minutes("22:00", "22:00") == 24 * 60
    assert wellness.duration_minutes("", "07:00") == 0
    assert wellness.duration_minutes("bedtime", "07:00") == 0
    assert wellness.duration_minutes("25:00", "07:00") == 0, "an impossible clock is not a night"


def test_a_logged_night_comes_back_in_the_shape_the_phone_parses(authed, mongo):
    """`date` is REQUIRED by the client's parser — a missing one throws and kills the
    whole list, so every stored night carries one even when the caller sent none."""
    client, token_for = authed

    client.post("/v1/wellness/sleep", json=NIGHT, headers=token_for("alice"))
    client.post("/v1/wellness/sleep", json={**NIGHT, "date": ""}, headers=token_for("alice"))
    nights = client.get("/v1/wellness/sleep", headers=token_for("alice")).json()

    assert len(nights) == 2
    for night in nights:
        assert night["date"], "a night with no date breaks the phone's whole sleep list"
        assert set(night) >= {"date", "duration_min", "quality", "bedtime", "wake_time"}
    assert nights[0]["duration_min"] == 450


def test_an_unparseable_night_is_refused_rather_than_stored_as_zero(authed, mongo):
    client, token_for = authed

    r = client.post("/v1/wellness/sleep", json={**NIGHT, "bedtime": "", "wake_time": ""},
                    headers=token_for("alice"))

    assert r.status_code == 400
    assert client.get("/v1/wellness/sleep", headers=token_for("alice")).json() == []


def test_the_week_card_stays_silent_until_it_has_something_to_say(authed, mongo):
    """`enough_data` gates the card. With one or two nights an "average" and a "trend" are
    noise dressed as insight."""
    client, token_for = authed

    empty = client.get("/v1/wellness/sleep/summary", headers=token_for("alice")).json()
    assert empty["enough_data"] is False
    assert empty["avg_duration_min"] == 0

    for day in ("11", "12", "13"):
        client.post("/v1/wellness/sleep", json={**NIGHT, "date": f"2026-07-{day}"},
                    headers=token_for("alice"))

    summary = client.get("/v1/wellness/sleep/summary", headers=token_for("alice")).json()
    assert summary["enough_data"] is True
    assert summary["avg_duration_min"] == 450
    assert summary["avg_quality"] == 4.0
    assert summary["trend"] == "steady"


def test_a_trend_needs_a_real_difference_not_a_rounding_error(authed, mongo):
    """A 30-minute dead band. Six minutes is not a trend, and telling someone it is, is a
    lie with a chart on it."""
    client, token_for = authed
    # Newest-first: these four go in oldest → newest, so the LAST two are the "recent" half.
    for date, bed in (("11", "23:30"), ("12", "23:30"), ("13", "22:00"), ("14", "22:00")):
        client.post("/v1/wellness/sleep", json={**NIGHT, "date": f"2026-07-{date}", "bedtime": bed},
                    headers=token_for("alice"))

    summary = client.get("/v1/wellness/sleep/summary", headers=token_for("alice")).json()

    assert summary["trend"] == "improving", "90 more minutes a night is a trend"


# ── the person's own week ────────────────────────────────────────────────────


def test_the_weekly_insights_are_counts_of_their_own_records(authed, mongo):
    client, token_for = authed
    client.post("/v1/wellness/journal", json={"body": "one"}, headers=token_for("alice"))
    client.post("/v1/wellness/sleep", json=NIGHT, headers=token_for("alice"))
    client.post("/v1/wellness/moods", json={"mood": "steady"}, headers=token_for("alice"))

    body = client.get("/v1/wellness/insights/weekly", headers=token_for("alice")).json()

    assert body["headline"]
    metrics = {m["label"]: m for m in body["metrics"]}
    assert metrics["Journal entries"]["value"] == "1"
    assert metrics["Nights logged"]["value"] == "1"
    assert metrics["Check-ins"]["value"] == "1"
    assert metrics["Average sleep"]["value"] == "7h 30m"
    for metric in body["metrics"]:
        assert isinstance(metric["value"], str), "the client renders value as a string"
        assert 0.0 <= metric["progress"] <= 1.0, "the bar fill is a fraction"


def test_an_empty_week_says_so_kindly_and_does_not_pretend(authed, mongo):
    client, token_for = authed

    body = client.get("/v1/wellness/insights/weekly", headers=token_for("alice")).json()

    assert "Nothing noted yet" in body["summary"]
    assert all(m["progress"] == 0.0 for m in body["metrics"])
    assert {m["label"]: m["value"] for m in body["metrics"]}["Average sleep"] == "—"


def test_old_entries_fall_out_of_the_week(authed, mongo, monkeypatch):
    """The window is real: an entry from last month is not part of this week."""
    client, token_for = authed
    client.post("/v1/wellness/journal", json={"body": "recent"}, headers=token_for("alice"))

    body = client.get("/v1/wellness/insights/weekly?days=1", headers=token_for("alice")).json()
    assert {m["label"]: m["value"] for m in body["metrics"]}["Journal entries"] == "1"

    # An entry whose timestamp cannot be read is dropped from the window rather than
    # crashing the screen (a corrupt row must not cost someone their insights page).
    monkeypatch.setattr(wellness, "_read", lambda *_a, **_k: [{"ts": "not-a-date", "body": "x"}])
    body = client.get("/v1/wellness/insights/weekly", headers=token_for("alice")).json()
    assert {m["label"]: m["value"] for m in body["metrics"]}["Journal entries"] == "0"


# ── the store's own edges ────────────────────────────────────────────────────


def test_an_empty_entry_is_not_an_entry(authed, mongo):
    client, token_for = authed

    assert client.post("/v1/wellness/journal", json={"body": "   "},
                       headers=token_for("alice")).status_code == 400
    assert client.post("/v1/wellness/moods", json={"mood": ""},
                       headers=token_for("alice")).status_code == 400


def test_the_lists_are_newest_first(authed, mongo):
    """The order every screen renders, and the order the sleep chart assumes."""
    client, token_for = authed
    for body in ("first", "second", "third"):
        client.post("/v1/wellness/journal", json={"body": body}, headers=token_for("alice"))

    entries = client.get("/v1/wellness/journal", headers=token_for("alice")).json()

    assert [e["body"] for e in entries] == ["third", "second", "first"]


def test_the_lists_are_capped_so_a_document_cannot_grow_forever(mongo, as_org, monkeypatch):
    """An unbounded $push is a document that exceeds 16MB years later and starts failing
    every write for that user, silently."""
    monkeypatch.setattr(wellness, "_MAX_ENTRIES", 3)
    as_org("acme")

    for i in range(5):
        wellness.add_journal("u1", f"entry {i}")

    kept = [e["body"] for e in wellness.list_journal("u1")]
    assert kept == ["entry 4", "entry 3", "entry 2"], "the OLDEST entries must fall off"


def test_a_store_outage_empties_the_screen_rather_than_breaking_it(mongo, as_org, monkeypatch):
    as_org("acme")
    wellness.add_journal("u1", "written before the outage")

    class _Broken:
        def find_one(self, *_a, **_k):
            raise RuntimeError("store down")

        def update_one(self, *_a, **_k):
            raise RuntimeError("store down")

    monkeypatch.setattr(wellness, "_collection", lambda: _Broken())

    assert wellness.list_journal("u1") == []
    assert wellness.add_journal("u1", "during the outage") is None
    assert wellness.delete_entry("u1", "journal", "whatever") is False


def test_a_missing_store_is_not_a_crash(mongo, as_org, monkeypatch):
    as_org("acme")
    monkeypatch.setattr(wellness, "_collection", lambda: None)

    assert wellness.add_journal("u1", "x") is None
    assert wellness.list_journal("u1") == []
    assert wellness.sleep_summary("u1")["enough_data"] is False
    assert wellness.delete_entry("u1", "journal", "x") is False


def test_an_unknown_kind_is_refused_everywhere(authed, mongo, as_org):
    client, token_for = authed

    assert client.delete("/v1/wellness/patterns/abc",
                         headers=token_for("alice")).status_code == 404
    as_org("acme")
    assert wellness.add_journal("", "no user") is None
    assert wellness._append("u1", "not_a_kind", {}) is None
    assert wellness._read("u1", "not_a_kind") == []


def test_deleting_an_entry_that_is_not_there_is_a_404(authed, mongo):
    client, token_for = authed
    client.post("/v1/wellness/journal", json={"body": "one"}, headers=token_for("alice"))

    r = client.delete("/v1/wellness/journal/nosuchid", headers=token_for("alice"))

    assert r.status_code == 404


def test_entries_are_bounded_so_one_person_cannot_fill_the_disk(authed, mongo):
    client, token_for = authed

    too_long = client.post("/v1/wellness/journal", json={"body": "x" * 20001},
                           headers=token_for("alice"))

    assert too_long.status_code == 422, "the size cap is the schema's job, not the store's"
