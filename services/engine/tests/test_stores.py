"""The five document stores — the app's entire persistence layer, and its only
untested one.

Everything durable about a user lives behind these modules: the transcript the UI
renders, the actions the check-in scheduler fires on, the intake answers that decide
whether a returning user is re-onboarded, the dynamic variables the prompts render.
Every one of them is *best-effort by contract* — they swallow exceptions and return
an empty result so a Mongo hiccup never breaks a coaching turn. That contract is also
their danger: when a store silently degrades, nothing anywhere raises. A repeat user
just quietly reads back as "fresh", a saved action quietly disappears, a set-once
intake answer quietly gets overwritten with null. The failure is invisible in prod and
invisible in CI.

So these tests run the REAL query and update expressions ($set / $setOnInsert / $inc /
$push+$each / $addToSet / dotted paths / projections / aggregation) against mongomock —
a real pymongo API over an in-process store — and assert on the resulting DOCUMENT
STATE. Nothing here asserts "a mock was called"; a test that did would pass against a
store that wrote nothing at all.
"""
from datetime import datetime, timezone

import pytest
from bson import ObjectId
from pymongo.errors import ConnectionFailure

from app import config
from app.stores import agentic, conversation, dynamic_vars, org
from app.stores import mongo as mongo_store


# ── Test doubles for the infrastructure (never for the module under test) ─────


class _BrokenMongo:
    """A Mongo server that hands out a collection handle and then fails every
    operation on it — what a dropped connection, a failed-over replica set or an
    auth expiry actually looks like from the driver's side.

    `client[db][coll]` does no I/O in pymongo, so the failure lands *inside* each
    store function, on the first real call. That is exactly the path every store's
    "never raises" contract exists for, and it is the path that is never exercised.
    """

    def __getitem__(self, _name):  # client[db] -> db[collection] -> collection
        return self

    def __getattr__(self, name):
        def _fail(*_a, **_kw):
            raise ConnectionFailure(f"connection lost during {name}()")

        return _fail


def _advancing_clock(*stamps):
    """A `datetime` whose `now()` returns each stamp in turn — so a two-write test can
    tell "written on insert" apart from "rewritten on every update"."""
    it = iter(stamps)

    class _Clock(datetime):
        @classmethod
        def now(cls, tz=None):
            return next(it)

    return _Clock


# ── Fixtures ─────────────────────────────────────────────────────────────────


@pytest.fixture(autouse=True)
def _fresh_index_flag():
    """`conversation._index_ready` is a module global that latches True forever after
    the first call. Reset it around every test so each one meets a pristine store and
    index creation is not silently skipped depending on test ORDER."""
    conversation._index_ready = False
    yield
    conversation._index_ready = False


@pytest.fixture
def no_mongo(monkeypatch):
    """Mongo is unavailable — the dev box, and every prod incident. Each store must
    degrade to an empty result, not raise: these functions sit on the request path."""
    monkeypatch.setattr(config, "MONGO_DB_URL", "")
    monkeypatch.setattr(mongo_store, "_client", False)
    monkeypatch.setattr(mongo_store, "get_client", lambda: None)


@pytest.fixture
def broken_mongo(monkeypatch):
    """Mongo answers the handle and then dies mid-operation."""
    client = _BrokenMongo()
    monkeypatch.setattr(config, "MONGO_DB_URL", "mongodb://fake")
    monkeypatch.setattr(mongo_store, "_client", client)
    monkeypatch.setattr(mongo_store, "get_client", lambda: client)
    return client


@pytest.fixture
def conv_coll(mongo):
    """The transcript collection (rasa.user_conversations), pre-resolved."""
    return mongo[config.MONGO_RASA_DB][config.MONGO_USER_CONVERSATIONS_COLLECTION]


@pytest.fixture
def users_coll(mongo):
    """The live-profile collection (cerebrozen.users), pre-resolved."""
    return mongo[config.MONGO_BACKEND_DB][config.MONGO_USERS_COLLECTION]


@pytest.fixture
def oid_user():
    """A user id that is a real ObjectId string — the only kind the `users` collection
    can be joined on."""
    return str(ObjectId())


# ═════════════════════════════════════════════════════════════════════════════
#  app/stores/mongo.py — the connection seam
# ═════════════════════════════════════════════════════════════════════════════


def test_no_mongo_url_disables_the_client_and_the_decision_is_cached(monkeypatch):
    """Dev boxes and CI run with MONGO_DB_URL unset. The client must resolve to None
    AND cache that — an uncached miss would re-enter the connect path on every read,
    paying the 4s server-selection timeout on the request path each time."""
    monkeypatch.setattr(mongo_store, "_client", None)
    monkeypatch.setattr(config, "MONGO_DB_URL", "")

    assert mongo_store._get_client() is None
    assert mongo_store._client is False, "the negative result must be cached, not recomputed"
    assert mongo_store._get_client() is None  # served from the cached miss


def test_mongo_connects_once_pings_and_reuses_the_client(monkeypatch):
    """One MongoClient, reused. A client per read would build a new connection pool per
    read; and without the `ping` the constructor succeeds lazily, so an unreachable
    Mongo would only blow up later, inside a coaching turn, instead of degrading here."""
    import mongomock

    built = []

    class _Counting(mongomock.MongoClient):
        def __init__(self, *args, **kwargs):
            built.append(kwargs)
            super().__init__()

    monkeypatch.setattr("pymongo.MongoClient", _Counting)
    monkeypatch.setattr(mongo_store, "_client", None)
    monkeypatch.setattr(config, "MONGO_DB_URL", "mongodb://host:27017")
    monkeypatch.setattr(config, "MONGO_TIMEOUT_MS", 1234)

    first = mongo_store._get_client()
    second = mongo_store._get_client()

    assert first is not None
    assert first is second, "the connected client must be cached, not rebuilt per call"
    assert len(built) == 1
    assert built[0]["serverSelectionTimeoutMS"] == 1234, (
        "the fail-fast timeout must reach the driver — without it a dead Mongo hangs "
        "the request for the driver's 30s default"
    )


def test_unreachable_mongo_degrades_to_none_instead_of_raising(monkeypatch):
    """An unreachable Mongo must leave the graph runnable (it is optional context, not
    a hard dependency) — and must not be retried on every subsequent read."""
    from pymongo.errors import ServerSelectionTimeoutError

    def _boom(*_a, **_kw):
        raise ServerSelectionTimeoutError("no reachable servers")

    monkeypatch.setattr("pymongo.MongoClient", _boom)
    monkeypatch.setattr(mongo_store, "_client", None)
    monkeypatch.setattr(config, "MONGO_DB_URL", "mongodb://host:27017")

    assert mongo_store._get_client() is None
    assert mongo_store._client is False


def test_get_client_is_the_single_postgres_seam(monkeypatch):
    """get_client() is THE switch between Mongo and the Postgres shim. If it stopped
    preferring Postgres, a Postgres deployment would silently read/write Mongo (or
    nothing at all) while every store module looked healthy."""
    sentinel = object()
    monkeypatch.setattr("app.stores.pg.client", lambda: sentinel)
    assert mongo_store.get_client() is sentinel


def test_get_client_falls_back_to_mongo_when_postgres_is_off(monkeypatch):
    import mongomock

    client = mongomock.MongoClient()
    monkeypatch.setattr("app.stores.pg.client", lambda: None)
    monkeypatch.setattr(mongo_store, "_client", client)

    assert mongo_store.get_client() is client


def test_non_objectid_user_ids_are_tolerated():
    """UI-minted user ids are not ObjectIds. Coercion must return None rather than
    raise — bson raises InvalidId, and an uncaught one would 500 the first turn of
    every non-Mongo user."""
    oid = ObjectId()
    assert mongo_store._to_object_id(str(oid)) == oid
    assert mongo_store._to_object_id("test-abc123") is None
    assert mongo_store._to_object_id("") is None


# ── NBI / DISC formatting ────────────────────────────────────────────────────


def test_nbi_scores_are_parsed_from_strings_and_the_dominant_quadrant_is_the_max():
    """The DB stores quadrant scores as STRINGS. The dominant quadrant drives what the
    coach says about how the user thinks; picking it off unparsed strings would compare
    lexicographically ("9" > "10") and hand the user the wrong profile."""
    text, scores = mongo_store._format_nbi({"L1": "9", "L2": "10", "R1": "40.0", "R2": "3"})

    assert scores == {"l1": 9, "l2": 10, "r1": 40, "r2": 3}
    assert "Dominant quadrant: r1." in text
    assert "l1: 9; l2: 10; r1: 40; r2: 3" in text


def test_nbi_skips_unparseable_quadrants_and_yields_nothing_when_all_are_missing():
    """A half-filled report must still render the quadrants it has; a report with no
    numbers at all must produce NO placeholder value, so the prompt falls back rather
    than telling the user their dominant quadrant is 'None'."""
    text, scores = mongo_store._format_nbi({"L1": "5", "L2": None, "R1": "n/a"})
    assert scores == {"l1": 5}
    assert "l1: 5" in text and "l2" not in text

    assert mongo_store._format_nbi({}) == (None, {})
    assert mongo_store._format_nbi({"L1": "", "L2": "x"}) == (None, {})


def test_disc_renders_high_and_low_traits_and_empties_to_nothing():
    text, choices = mongo_store._format_disc(
        {"disc_scores": {"HIGH_TRAITS": ["Dominance", "Influence"], "LOW_TRAITS": ["Steadiness"]}}
    )
    assert text == "DISC behavioral profile — High: Dominance, Influence; Low: Steadiness."
    assert choices == "Dominance, Influence"

    # High-only is a valid report.
    text, choices = mongo_store._format_disc({"disc_scores": {"HIGH_TRAITS": ["Conscientiousness"]}})
    assert text == "DISC behavioral profile — High: Conscientiousness."
    assert choices == "Conscientiousness"

    # An empty/blank-only report must yield no placeholder at all.
    assert mongo_store._format_disc({}) == (None, "")
    assert mongo_store._format_disc({"disc_scores": {"HIGH_TRAITS": [""], "LOW_TRAITS": []}}) == (None, "")


def test_a_disc_report_with_only_low_traits_publishes_no_behavioural_choices(mongo, user_id):
    """{userBehavioralChoices} is the HIGH traits. A low-traits-only report still has a
    readable summary, but no choices — publishing an empty string there would render
    "your behavioural choices are:" followed by nothing."""
    mongo[config.MONGO_BACKEND_DB][config.MONGO_DISC_COLLECTION].insert_one(
        {"userId": user_id, "timestamp": "2026-01-01", "disc_scores": {"LOW_TRAITS": ["Steadiness"]}}
    )

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert ctx["userBehavioralPreference"] == "DISC behavioral profile — Low: Steadiness."
    assert "userBehavioralChoices" not in ctx


# ── get_greeting_profile ─────────────────────────────────────────────────────


def test_greeting_profile_maps_the_users_collection_onto_the_home_screen_fields(
    users_coll, oid_user
):
    """The home screen greets by name in the user's language/timezone. `username` is the
    DB's spelling; the greeting generator reads `name` — if that rename regressed, every
    user would be greeted as a stranger."""
    users_coll.insert_one({
        "_id": ObjectId(oid_user),
        "username": "Ada",
        "localTimeZone": "Europe/London",
        "language": "en",
        "country": "UK",
        "level": "senior",  # not projected — must not leak into the greeting payload
    })

    assert mongo_store.get_greeting_profile(oid_user) == {
        "name": "Ada", "timezone": "Europe/London", "language": "en", "country": "UK",
    }


def test_greeting_profile_omits_missing_fields_rather_than_emitting_blanks(users_coll, oid_user):
    """`language` does not exist in the DB yet. Absent keys must be ABSENT — emitting
    "" would defeat the caller's `or "en"` fallback and greet the user in no language."""
    users_coll.insert_one({"_id": ObjectId(oid_user), "username": "Ada", "localTimeZone": ""})

    assert mongo_store.get_greeting_profile(oid_user) == {"name": "Ada"}

    # A row with no username at all still yields whatever it does have.
    nameless = str(ObjectId())
    users_coll.insert_one({"_id": ObjectId(nameless), "country": "UK"})
    assert mongo_store.get_greeting_profile(nameless) == {"country": "UK"}


def test_greeting_profile_degrades_to_empty_on_every_bad_input(
    mongo, users_coll, oid_user, no_mongo
):
    """Never raises: no Mongo, no user, a UI-minted (non-ObjectId) id, and an id with no
    row all return {} — the greeting falls back to a generic one instead of 500ing."""
    assert mongo_store.get_greeting_profile(oid_user) == {}  # no client (no_mongo wins)


def test_greeting_profile_returns_empty_for_unknown_and_non_objectid_users(mongo, oid_user):
    assert mongo_store.get_greeting_profile("") == {}
    assert mongo_store.get_greeting_profile("ui-minted-id") == {}   # not an ObjectId
    assert mongo_store.get_greeting_profile(oid_user) == {}          # no such user


def test_greeting_profile_survives_mongo_dying_mid_read(broken_mongo, oid_user):
    assert mongo_store.get_greeting_profile(oid_user) == {}


# ═════════════════════════════════════════════════════════════════════════════
#  app/stores/mongo.py — read_user_context (the context package)
# ═════════════════════════════════════════════════════════════════════════════


def test_read_user_context_is_empty_without_mongo_or_a_user(no_mongo, user_id):
    assert mongo_store.read_user_context(user_id, "s-1") == {}


def test_read_user_context_needs_a_user_id(mongo):
    assert mongo_store.read_user_context("", "s-1") == {}


def test_a_user_with_nothing_stored_is_fresh_with_no_past_conversation(mongo, user_id):
    """The two fields the graph ROUTES on must be defined even with zero documents —
    an undefined `userRepeatFresh` is how a first-timer got routed to the repeat-user
    check-in and asked about actions they never committed to."""
    ctx = mongo_store.read_user_context(user_id, "s-1")
    assert ctx["userRepeatFresh"] == "fresh"
    assert ctx["pastConversation"] == ""


def test_dynamic_vars_are_merged_with_bidirectional_case_aliases(mongo, user_id):
    """Intake emits snake_case or camelCase depending on the prompt version, and the
    prompts read whichever spelling their author used. Publishing only one spelling
    leaves a live {placeholder} unresolved for the other."""
    mongo[config.MONGO_BACKEND_DB][config.MONGO_DYNAMIC_VARS_COLLECTION].insert_one({
        "user_id": user_id,
        "coachability_score": 7,
        "coachabilityDetail": "asks for feedback",
        "user_role_context": "Engineering manager",
        "userMotivations": "impact",
        "_provenance": {"coachability_score": {"session_id": "s-0"}},
        "updated_at": "2026-07-01T00:00:00+00:00",
    })

    ctx = mongo_store.read_user_context(user_id, "s-1")

    # Both spellings resolve, whichever way round the value was written.
    assert ctx["coachability_score"] == 7 and ctx["coachabilityScore"] == 7
    assert ctx["coachabilityDetail"] == "asks for feedback"
    assert ctx["coachability_detail"] == "asks for feedback"
    assert ctx["user_role_context"] == "Engineering manager"
    assert ctx["userRoleContext"] == "Engineering manager"
    assert ctx["userMotivations"] == ctx["user_motivations"] == "impact"
    # Internal bookkeeping must never become a prompt placeholder.
    assert "_provenance" not in ctx and "updated_at" not in ctx
    assert "_id" not in ctx and "user_id" not in ctx


def test_the_case_aliases_work_in_the_mirrored_direction_too(mongo, user_id):
    """Same contract, opposite spellings on disk — the alias must be bidirectional or a
    prompt written against the other convention renders a literal {coachabilityScore}."""
    mongo[config.MONGO_BACKEND_DB][config.MONGO_DYNAMIC_VARS_COLLECTION].insert_one({
        "user_id": user_id,
        "coachabilityScore": 4,             # camelCase on disk this time
        "coachability_detail": "reflective",  # snake_case on disk this time
        "userThinkingPreference": "R1 dominant",
    })

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert ctx["coachabilityScore"] == 4 and ctx["coachability_score"] == 4
    assert ctx["coachability_detail"] == ctx["coachabilityDetail"] == "reflective"
    assert ctx["user_thinking_preference"] == "R1 dominant"


def test_agentic_intake_vars_override_the_dynamic_collection(mongo, agentic_coll, user_id):
    """The legacy intake_vars path stays authoritative where both collections carry the
    same key (read order is documented and load-bearing) — and empty values must NOT
    override a real one, or a null echo from the intake agent wipes the context."""
    mongo[config.MONGO_BACKEND_DB][config.MONGO_DYNAMIC_VARS_COLLECTION].insert_one({
        "user_id": user_id, "coachingHistory": "from dynamic vars", "coachingNeeds": "keep me",
    })
    agentic_coll.insert_one({
        "user_id": user_id,
        "intake_vars": {"coachingHistory": "from intake_vars", "coachingNeeds": "",
                        "userMotivations": None, "extras": []},
    })

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert ctx["coachingHistory"] == "from intake_vars"
    assert ctx["coachingNeeds"] == "keep me", "an empty intake var must not clobber a stored one"
    assert "userMotivations" not in ctx and "extras" not in ctx


def test_a_dynamic_vars_failure_does_not_take_the_whole_context_down(
    mongo, agentic_coll, monkeypatch, user_id
):
    """Each source is independently guarded: one broken collection must cost only its
    own keys, not the entire context package (which would silently re-onboard the user)."""
    agentic_coll.insert_one({"user_id": user_id, "sessions_completed": 2})
    monkeypatch.setattr(config, "MONGO_DYNAMIC_VARS_COLLECTION", "")  # InvalidName on access

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert ctx["userRepeatFresh"] == "repeat", "the agentic read must still have run"


def test_previous_actions_exclude_the_ones_the_user_skipped_or_deleted(
    mongo, agentic_coll, user_id
):
    """A dismissed action must never be fed back to the coach as something the user
    committed to — the LLM would treat it as done and would never suggest it again."""
    agentic_coll.insert_one({
        "user_id": user_id,
        "actions": [
            {"full_text": "Book a 1:1", "status": "saved"},
            {"full_text": "Legacy action", "status": "active"},   # pre-change data
            {"full_text": "Dismissed", "status": "deleted"},
            {"full_text": "Skipped", "status": "skipped"},
            {"full_text": "", "status": "saved"},                 # no recap text
        ],
        "insights": [
            {"insight_title": "Avoids conflict", "insight_body": "defers hard talks"},
            {"insight_body": "orphan body with no title"},
        ],
        "user_context_model": {"dimension_1": "value"},
        "ic_profile": "| pattern | evidence |",
    })

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert ctx["previousUserActions"] == ["Book a 1:1", "Legacy action"]
    assert ctx["previousUserInsights"] == ["Avoids conflict: defers hard talks"]
    assert ctx["previousUserContext"] == {"dimension_1": "value"}
    # The pattern table is published under all three names the prompts reference.
    assert ctx["ic_profile"] == ctx["prev_pattern_table"] == ctx["pattern"] == "| pattern | evidence |"


def test_a_user_who_abandoned_a_session_stays_fresh(mongo, agentic_coll, conv_coll, user_id):
    """Mid-session artefacts (actions, intake vars) are NOT completion. A user who
    walked away before close must be re-onboarded through intake, not greeted as a
    returning client and asked how last time's actions went."""
    agentic_coll.insert_one({
        "user_id": user_id,
        "actions": [{"full_text": "half-finished", "status": "active"}],
        "intake_vars": {"coachingHistory": "some"},
    })
    conv_coll.insert_one({"session_id": "s-abandoned", "user_id": user_id,
                          "messages": [{"role": "user", "text": "hi"}], "ended": False})

    ctx = mongo_store.read_user_context(user_id, "s-now")

    assert ctx["userRepeatFresh"] == "fresh"
    assert "hi" in ctx["pastConversation"], "an abandoned session is still remembered verbatim"


def test_a_completed_session_makes_the_user_a_repeat_even_with_no_agentic_doc(
    mongo, conv_coll, user_id
):
    """The close-time builders can fail. The transcript store is written every turn, so
    an ended session there is the reliable repeat signal — without this fallback a user
    whose builder crashed is re-onboarded forever."""
    conv_coll.insert_one({"session_id": "s-done", "user_id": user_id, "ended": True,
                          "messages": [{"role": "user", "text": "thanks"}]})

    ctx = mongo_store.read_user_context(user_id, "s-now")

    assert ctx["userRepeatFresh"] == "repeat"


def test_session_counters_are_published_even_when_zero(mongo, agentic_coll, user_id):
    """`sessions_completed: 0` is a real value, not a missing one. Gating publication on
    truthiness would leave {session_count} unresolved for a user mid-first-session."""
    agentic_coll.insert_one({
        "user_id": user_id, "sessions_completed": 0, "last_session_at": "2026-07-01T09:00:00+00:00",
    })

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert ctx["session_count"] == 0 and ctx["sessions_completed"] == 0
    assert ctx["last_session_at"] == "2026-07-01T09:00:00+00:00"
    assert ctx["userRepeatFresh"] == "fresh", "zero completed sessions is not a repeat user"


def test_checkin_is_due_only_for_overdue_prior_sessions_not_this_one(
    mongo, agentic_coll, frozen_now, monkeypatch, user_id
):
    """The 7-day window (BRD R1-R4) is decided in CODE. Every exclusion here is a way
    the user gets asked about the wrong thing: this session's own actions (asked about
    something they committed to 30 seconds ago), an already-closed batch (asked twice),
    a skipped action (asked about something they refused)."""
    fixed, frozen = frozen_now  # 2026-07-14
    monkeypatch.setattr(mongo_store, "datetime", frozen)
    agentic_coll.insert_one({
        "user_id": user_id,
        "checkin_complete_sessions": ["s-closed"],
        "actions": [
            {"full_text": "Overdue: run the 1:1", "session_date": "2026-07-01",
             "session_id": "s-old", "status": "saved"},
            {"full_text": "Too recent", "session_date": "2026-07-12",
             "session_id": "s-recent", "status": "saved"},
            {"full_text": "Already checked in", "session_date": "2026-06-01",
             "session_id": "s-closed", "status": "saved"},
            {"full_text": "Refused", "session_date": "2026-06-01",
             "session_id": "s-skip", "status": "skipped"},
            {"full_text": "Committed 30s ago", "session_date": "2026-06-01",
             "session_id": "s-now", "status": "saved"},
        ],
    })

    ctx = mongo_store.read_user_context(user_id, "s-now")

    assert ctx["checkinEligibleActions"] == ["Overdue: run the 1:1"]
    assert ctx["checkinSessionIds"] == ["s-old"]
    assert ctx["checkinDue"] is True


def test_an_overdue_action_with_no_recap_text_does_not_trigger_a_checkin(
    mongo, agentic_coll, frozen_now, monkeypatch, user_id
):
    """An overdue action with an empty `full_text` gives the check-in nothing to recap.
    Gating on mere existence would open the session with an empty "last time you said
    to…" — so the gate is the recap LIST, not the eligibility list."""
    _, frozen = frozen_now
    monkeypatch.setattr(mongo_store, "datetime", frozen)
    agentic_coll.insert_one({
        "user_id": user_id,
        "actions": [{"full_text": "", "session_date": "2026-07-01",
                     "session_id": "s-old", "status": "saved"}],
    })

    ctx = mongo_store.read_user_context(user_id, "s-now")

    assert ctx["checkinEligibleActions"] == []
    assert ctx["checkinDue"] is False


def test_a_broken_agentic_doc_still_leaves_the_context_usable(mongo, monkeypatch, user_id):
    """The agentic read is guarded on its own: if it blows up, the graph must still get
    a context package (the live profile / NBI / DISC blocks below it still run)."""
    monkeypatch.setattr(config, "MONGO_AGENTIC_COLLECTION", "")  # InvalidName on access

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert "userRepeatFresh" not in ctx, "the failing block wrote nothing"
    assert ctx["pastConversation"] == "", "the blocks around it still ran"


def test_a_transcript_store_failure_leaves_past_conversation_empty(mongo, monkeypatch, user_id):
    """Cross-session memory is optional context. If the transcript store is broken the
    user loses their history — they must not lose their session."""
    monkeypatch.setattr(config, "MONGO_USER_CONVERSATIONS_COLLECTION", "")

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert ctx["pastConversation"] == ""
    assert ctx["userRepeatFresh"] == "fresh"


def test_live_profile_fills_the_name_and_org_placeholders(mongo, users_coll, oid_user):
    """{userName} is resolved from `userName`, the DB column is `username`. The org name
    needs a second lookup keyed on the user's orgId — a white-label session that renders
    a raw orgId to the user is a visible, embarrassing bug."""
    users_coll.insert_one({
        "_id": ObjectId(oid_user), "username": "Ada", "localTimeZone": "Europe/London",
        "level": "senior", "orgId": "org-42",
        "idp_competencies": ["Delegation"], "deep_link_skill": "Feedback",
    })
    mongo[config.MONGO_BACKEND_DB][config.MONGO_ORG_COLLECTION].insert_one(
        {"orgId": "org-42", "name": "  Acme Corp  "}
    )

    ctx = mongo_store.read_user_context(oid_user, "s-1")

    assert ctx["name"] == "Ada" and ctx["userName"] == "Ada"
    assert ctx["timezone"] == "Europe/London"
    assert ctx["level"] == "senior" and ctx["orgId"] == "org-42"
    assert ctx["idp_competencies"] == ["Delegation"]
    assert ctx["deep_link_skill"] == "Feedback"
    assert ctx["organizationName"] == "Acme Corp", "the org name must be trimmed"


def test_a_stored_name_wins_over_the_live_profile_username(mongo, users_coll, oid_user):
    """`name` is set with setdefault, so a name the user gave the coach (captured into
    dynamic vars) is not overwritten by the HR-system username."""
    mongo[config.MONGO_BACKEND_DB][config.MONGO_DYNAMIC_VARS_COLLECTION].insert_one(
        {"user_id": oid_user, "name": "Ada Lovelace"}
    )
    users_coll.insert_one({"_id": ObjectId(oid_user), "username": "alovelace"})

    ctx = mongo_store.read_user_context(oid_user, "s-1")

    assert ctx["name"] == "Ada Lovelace"
    assert ctx["userName"] == "alovelace"


def test_org_name_probes_every_field_spelling_and_stays_absent_when_unknown(
    mongo, users_coll, oid_user
):
    """The org collection's schema is not pinned; the read probes name/orgName/
    organization_name. An unknown org must leave {organizationName} ABSENT rather than
    resolving to an empty string that a prompt's presence-gate would read as set."""
    users_coll.insert_one({"_id": ObjectId(oid_user), "orgId": "org-x"})
    org_coll = mongo[config.MONGO_BACKEND_DB][config.MONGO_ORG_COLLECTION]

    assert "organizationName" not in mongo_store.read_user_context(oid_user, "s-1")

    org_coll.insert_one({"orgId": "org-x", "organization_name": "Third Spelling"})
    assert mongo_store.read_user_context(oid_user, "s-1")["organizationName"] == "Third Spelling"

    org_coll.delete_many({})
    org_coll.insert_one({"orgId": "org-x", "orgName": "Second Spelling"})
    assert mongo_store.read_user_context(oid_user, "s-1")["organizationName"] == "Second Spelling"

    org_coll.delete_many({})
    org_coll.insert_one({"orgId": "org-x", "name": ""})  # present but blank
    assert "organizationName" not in mongo_store.read_user_context(oid_user, "s-1")


def test_an_objectid_user_with_no_profile_row_still_gets_a_context(mongo, agentic_coll, oid_user):
    """A well-formed ObjectId that simply has no `users` row (deleted account, a user in
    another tenant's DB) must not stop the agentic/assessment blocks from filling in."""
    agentic_coll.insert_one({"user_id": oid_user, "sessions_completed": 1})

    ctx = mongo_store.read_user_context(oid_user, "s-1")

    assert ctx["userRepeatFresh"] == "repeat"
    assert "name" not in ctx and "organizationName" not in ctx


def test_a_failing_profile_or_org_read_does_not_break_the_context(
    mongo, users_coll, agentic_coll, monkeypatch, oid_user
):
    agentic_coll.insert_one({"user_id": oid_user, "orgId": "org-x", "sessions_completed": 1})
    monkeypatch.setattr(config, "MONGO_USERS_COLLECTION", "")
    monkeypatch.setattr(config, "MONGO_ORG_COLLECTION", "")

    ctx = mongo_store.read_user_context(oid_user, "s-1")

    assert ctx["userRepeatFresh"] == "repeat"
    assert "organizationName" not in ctx


def test_nbi_and_disc_take_the_latest_report(mongo, user_id):
    """Assessments are re-taken. Sorting is what makes "latest wins" true; without it
    Mongo returns natural order and the coach describes a profile the user has grown
    out of."""
    nbi = mongo[config.MONGO_BACKEND_DB][config.MONGO_NBI_COLLECTION]
    nbi.insert_many([
        {"userId": user_id, "insertedDateUTC": "2024-01-01", "L1": "40", "L2": "1", "R1": "1", "R2": "1"},
        {"userId": user_id, "insertedDateUTC": "2026-01-01", "L1": "1", "L2": "1", "R1": "1", "R2": "50"},
    ])
    disc = mongo[config.MONGO_BACKEND_DB][config.MONGO_DISC_COLLECTION]
    disc.insert_many([
        {"userId": user_id, "timestamp": "2024-01-01", "disc_scores": {"HIGH_TRAITS": ["Old"]}},
        {"userId": user_id, "timestamp": "2026-01-01", "disc_scores": {"HIGH_TRAITS": ["Dominance"]}},
    ])

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert "Dominant quadrant: r2." in ctx["userThinkingPreference"]
    assert ctx["user_thinking_preference"] == ctx["userThinkingPreference"]  # CH snake_case alias
    assert ctx["nbi_scores"] == {"l1": 1, "l2": 1, "r1": 1, "r2": 50}
    assert ctx["userBehavioralChoices"] == "Dominance"
    assert "High: Dominance" in ctx["userBehavioralPreference"]


def test_an_empty_assessment_report_publishes_no_placeholder(mongo, user_id):
    """A report row that exists but scores nothing must not publish an empty
    {userThinkingPreference} — the prompt would render "your thinking preference is"
    followed by nothing."""
    mongo[config.MONGO_BACKEND_DB][config.MONGO_NBI_COLLECTION].insert_one(
        {"userId": user_id, "insertedDateUTC": "2026-01-01", "L1": None}
    )
    mongo[config.MONGO_BACKEND_DB][config.MONGO_DISC_COLLECTION].insert_one(
        {"userId": user_id, "timestamp": "2026-01-01", "disc_scores": {"HIGH_TRAITS": []}}
    )

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert "userThinkingPreference" not in ctx and "nbi_scores" not in ctx
    assert "userBehavioralPreference" not in ctx and "userBehavioralChoices" not in ctx


def test_failing_assessment_reads_are_survivable(mongo, monkeypatch, user_id):
    monkeypatch.setattr(config, "MONGO_NBI_COLLECTION", "")
    monkeypatch.setattr(config, "MONGO_DISC_COLLECTION", "")

    ctx = mongo_store.read_user_context(user_id, "s-1")

    assert ctx["userRepeatFresh"] == "fresh"
    assert "userThinkingPreference" not in ctx


# ═════════════════════════════════════════════════════════════════════════════
#  app/stores/conversation.py — the transcript store
# ═════════════════════════════════════════════════════════════════════════════


def test_the_legacy_unique_sender_id_index_is_dropped(conv_coll):
    """The OLD per-(user+bot) schema left a UNIQUE `sender_id` index behind. New docs
    carry no `sender_id`, so Mongo reads them all as `{sender_id: null}` and rejects
    every one after the first as a duplicate — i.e. the transcript store stops writing
    entirely. Dropping it on first use is what makes the new schema writable at all."""
    conv_coll.create_index("sender_id", name="sender_id_1", unique=True)

    conversation._collection()

    indexes = conv_coll.index_information()
    assert "sender_id_1" not in indexes
    # ...and the new business-key index is in place — (org_id, session_id) since
    # app-layer tenancy — so one session can never fork into two transcript
    # documents, and two tenants can never contend on a session id.
    assert indexes["org_id_1_session_id_1"]["unique"] is True
    assert indexes["org_id_1_session_id_1"]["partialFilterExpression"] == {"session_id": {"$exists": True}}
    assert "org_id_1_user_id_1" in indexes


def test_an_index_failure_does_not_block_the_store(broken_mongo):
    """Indexes are an optimisation. If index creation fails (no privileges, a duplicate
    in legacy data) the store must still hand back a usable collection."""
    assert conversation._collection() is not None


def test_a_turn_records_the_user_message_and_the_bot_reply(conv_coll, user_id):
    """This document IS the chat the user sees in history, and the transcript that gets
    replayed into a returning user's context. Losing a field here loses it forever."""
    conversation.record_turn(
        session_id="s-1", user_id=user_id, user_message="I avoid conflict",
        bot_text="What does avoiding cost you?", agent_name="core_coaching_agent",
        active_phase="explore", phase_buttons=[{"label": "Continue"}],
    )

    doc = conv_coll.find_one({"session_id": "s-1"})
    user_msg, bot_msg = doc["messages"]
    assert doc["user_id"] == user_id and doc["ended"] is False
    assert user_msg == {"role": "user", "text": "I avoid conflict", "message_num": 1,
                        "timestamp": doc["updated_at"], "request_id": ""}
    assert bot_msg["role"] == "bot" and bot_msg["message_num"] == 2
    # bot_name and agent_name must BOTH carry the producing agent — the UI reads one,
    # the analytics pipeline reads the other.
    assert bot_msg["bot_name"] == bot_msg["agent_name"] == "core_coaching_agent"
    assert bot_msg["active_phase"] == "explore"
    assert bot_msg["phase_buttons"] == [{"label": "Continue"}]
    assert bot_msg["buttons"] == []


def test_message_numbers_keep_counting_across_turns_and_created_at_is_written_once(
    conv_coll, monkeypatch, user_id
):
    """`message_num` is the transcript's ordering key and `created_at` is what the
    Recents list and the prior-transcript replay sort on. If $setOnInsert leaked into
    $set, `created_at` would be rewritten on every turn and session ordering would
    scramble on every message."""
    monkeypatch.setattr(
        conversation, "datetime",
        _advancing_clock(
            datetime(2026, 7, 14, 9, 0, tzinfo=timezone.utc),
            datetime(2026, 7, 14, 9, 5, tzinfo=timezone.utc),
        ),
    )

    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="one", bot_text="a")
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="two", bot_text="b")

    doc = conv_coll.find_one({"session_id": "s-1"})
    assert [m["message_num"] for m in doc["messages"]] == [1, 2, 3, 4]
    assert [m["text"] for m in doc["messages"]] == ["one", "a", "two", "b"]
    assert doc["created_at"] == "2026-07-14T09:00:00+00:00", "$setOnInsert must fire only on insert"
    assert doc["updated_at"] == "2026-07-14T09:05:00+00:00"
    assert conv_coll.count_documents({"session_id": "s-1"}) == 1, "the upsert must not fork the doc"


def test_a_restart_records_only_a_system_marker(conv_coll, user_id):
    """/restart is a control command, not something the user said. Recording it as a
    user message would replay "/restart" back into the next session's context as if it
    were a coaching statement."""
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="  /RESTART ",
                             bot_text="ignored")

    messages = conv_coll.find_one({"session_id": "s-1"})["messages"]
    assert messages == [{"role": "system", "text": "User restarted the chat.",
                         "message_num": 1, "timestamp": messages[0]["timestamp"]}]


def test_ending_a_conversation_marks_the_session_ended(conv_coll, user_id):
    """`ended` is the repeat-user signal AND the "resumable" flag. Both the explicit
    /endconversation marker and the graph reaching close must set it — a session that
    ends but is never marked stays forever resumable on the home screen."""
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="hi", bot_text="hey")
    conversation.record_turn(session_id="s-1", user_id=user_id,
                             user_message="/endconversation", bot_text="Take care.")
    assert conv_coll.find_one({"session_id": "s-1"})["ended"] is True

    conversation.record_turn(session_id="s-2", user_id=user_id, user_message="bye",
                             bot_text="bye", ended=True)
    assert conv_coll.find_one({"session_id": "s-2"})["ended"] is True


def test_a_hidden_turn_is_stamped_and_a_bot_less_turn_records_only_the_user(
    conv_coll, user_id
):
    """The action-ack turn ("saved|skipped") is sent by the UI, not typed by the user —
    it must be flagged so /history omits it from the rendered chat, while the coach
    still sees it. And a turn with no bot reply must not fabricate an empty bot bubble."""
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="saved|skipped",
                             bot_text=None, hidden=True)

    messages = conv_coll.find_one({"session_id": "s-1"})["messages"]
    assert len(messages) == 1
    assert messages[0]["hidden"] is True and messages[0]["role"] == "user"


def test_record_turn_is_a_no_op_without_a_session_or_mongo(conv_coll, user_id, no_mongo):
    conversation.record_turn(session_id="", user_id=user_id, user_message="x", bot_text="y")
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="x", bot_text="y")
    assert conv_coll.count_documents({}) == 0


def test_record_turn_swallows_a_malformed_transcript(conv_coll, user_id):
    """A doc whose `messages` is not an array (legacy/corrupt data) must not take the
    coaching turn down with it — persistence is best-effort, the reply already streamed."""
    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id, "messages": 7})

    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="x", bot_text="y")

    assert conv_coll.find_one({"session_id": "s-1"})["messages"] == 7  # unchanged, no crash


def test_prior_and_completed_session_flags_exclude_the_current_session(conv_coll, user_id):
    """Without the current-session exclusion a first-timer's OWN in-flight session counts
    as prior history and they get greeted as a returning client on turn two."""
    conv_coll.insert_one({"session_id": "s-now", "user_id": user_id, "ended": True})

    assert conversation.has_prior_sessions(user_id, "s-now") is False
    assert conversation.has_completed_session(user_id, "s-now") is False

    conv_coll.insert_one({"session_id": "s-old", "user_id": user_id, "ended": False})
    assert conversation.has_prior_sessions(user_id, "s-now") is True
    assert conversation.has_completed_session(user_id, "s-now") is False, (
        "an abandoned prior session is not a COMPLETED one"
    )

    conv_coll.insert_one({"session_id": "s-older", "user_id": user_id, "ended": True})
    assert conversation.has_completed_session(user_id, "s-now") is True
    # Another user's completed session must never make this user a repeat.
    assert conversation.has_completed_session("someone-else", "s-now") is False


def test_session_flags_degrade_to_fresh_when_mongo_is_gone(broken_mongo, no_mongo, user_id):
    assert conversation.has_prior_sessions(user_id) is False
    assert conversation.has_completed_session(user_id) is False
    assert conversation.has_prior_sessions("") is False
    assert conversation.has_completed_session("") is False


def test_session_flags_degrade_when_mongo_dies_mid_query(broken_mongo, user_id):
    assert conversation.has_prior_sessions(user_id, "s-1") is False
    assert conversation.has_completed_session(user_id, "s-1") is False


def test_prior_transcripts_are_verbatim_oldest_first_and_exclude_this_session(
    conv_coll, user_id
):
    """{pastConversation} is the user's cross-session memory. Roles are relabelled for
    the model (User/Coach/System); getting the order or the speaker wrong makes the coach
    quote the user's words back as its own."""
    conv_coll.insert_many([
        {"session_id": "s-2", "user_id": user_id, "created_at": "2026-07-02T00:00:00+00:00",
         "messages": [{"role": "user", "text": "second session"},
                      {"role": "bot", "text": "and my reply"}]},
        {"session_id": "s-1", "user_id": user_id, "created_at": "2026-07-01T00:00:00+00:00",
         "messages": [{"role": "system", "text": "User restarted the chat."},
                      {"role": "user", "text": "  first session  "},
                      {"role": "bot", "text": "   "}]},          # blank replies are dropped
        {"session_id": "s-now", "user_id": user_id, "created_at": "2026-07-04T00:00:00+00:00",
         "messages": [{"role": "user", "text": "current"}]},
        {"session_id": "s-empty", "user_id": user_id, "created_at": "2026-07-03T00:00:00+00:00",
         "messages": []},                                        # contributes no block
    ])

    text = conversation.get_prior_transcripts(user_id, "s-now")

    assert text == (
        "--- Session 1 (2026-07-01) ---\n"
        "System: User restarted the chat.\n"
        "User: first session\n"
        "\n"
        "--- Session 2 (2026-07-02) ---\n"
        "User: second session\n"
        "Coach: and my reply"
    )
    assert "current" not in text


def test_prior_transcripts_are_trimmed_from_the_OLDEST_end(conv_coll, user_id):
    """The cap protects the context window. It must keep the RECENT end — trimming the
    tail instead would feed the model the user's ancient history and drop last week's."""
    conv_coll.insert_one({
        "session_id": "s-1", "user_id": user_id, "created_at": "2026-07-01T00:00:00+00:00",
        "messages": [{"role": "user", "text": "A" * 500}, {"role": "user", "text": "RECENT"}],
    })

    text = conversation.get_prior_transcripts(user_id, "s-now", max_chars=50)

    assert text.startswith("[older history trimmed]\n\n")
    assert text.endswith("RECENT")
    assert len(text) == 50 + len("[older history trimmed]\n\n")


def test_prior_transcripts_are_empty_when_there_is_no_history(mongo, no_mongo, user_id):
    assert conversation.get_prior_transcripts(user_id, "s-now") == ""


def test_history_reads_with_no_current_session_see_every_session(conv_coll, user_id):
    """The background builders and the check-in scheduler call these with no current
    session at all. The `$ne` clause must then be OMITTED — a `{"session_id": {"$ne": ""}}`
    filter would exclude legacy docs that carry no session_id and quietly hide history.

    A user whose only sessions are empty shells (started, never spoke) has no transcript:
    that must be "" and not a header block with nothing under it."""
    conv_coll.insert_many([
        {"session_id": "s-1", "user_id": user_id, "ended": True,
         "created_at": "2026-07-01", "messages": []},
        {"session_id": "s-2", "user_id": user_id, "ended": False,
         "created_at": "2026-07-02", "messages": [{"role": "bot", "text": "   "}]},
    ])

    assert conversation.has_prior_sessions(user_id) is True
    assert conversation.has_completed_session(user_id) is True
    assert conversation.get_prior_transcripts(user_id) == "", (
        "sessions with no real messages must contribute no transcript at all"
    )


def test_prior_transcripts_survive_a_corrupt_message_array(conv_coll, user_id):
    """Corrupt history must cost the user their memory, not their session."""
    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id,
                          "created_at": "2026-07-01", "messages": ["not-a-dict"]})

    assert conversation.get_prior_transcripts(user_id, "s-now") == ""
    assert conversation.get_prior_transcripts("", "s-now") == ""


def test_get_session_returns_the_whole_document(conv_coll, user_id, no_mongo):
    assert conversation.get_session("s-1") is None   # no Mongo
    assert conversation.get_session("") is None


def test_get_session_reads_back_what_record_turn_wrote(conv_coll, user_id):
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="hi", bot_text="hey")

    doc = conversation.get_session("s-1")
    assert doc["session_id"] == "s-1" and len(doc["messages"]) == 2
    assert conversation.get_session("s-unknown") is None


def test_latest_bot_message_reads_only_a_bounded_tail(conv_coll, user_id):
    """This is on the resume path and must stay O(1) in conversation length: the
    aggregation $slices the last 10 messages instead of dragging a 500-message
    transcript across the wire. It must still report the TRUE total."""
    messages = []
    for i in range(1, 31):
        messages.append({"role": "user", "text": f"u{i}", "message_num": i})
        messages.append({"role": "bot", "text": f"b{i}", "message_num": i})
    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id, "ended": False,
                          "messages": messages})

    doc = conversation.get_latest_bot_message("s-1")

    assert doc["total"] == 60, "the total must count the whole transcript, not the slice"
    assert doc["last_bot_message"]["text"] == "b30"
    assert "tail" not in doc and "messages" not in doc, "the tail is an implementation detail"
    assert "_id" not in doc


def test_latest_bot_message_is_none_when_the_session_has_no_bot_reply(conv_coll, user_id):
    """A session that crashed before the first reply has no bot message. Returning a
    stale/None-typed message here is what makes a resumed session replay someone else's
    text."""
    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id,
                          "messages": [{"role": "user", "text": "hi"}]})

    assert conversation.get_latest_bot_message("s-1")["last_bot_message"] is None
    assert conversation.get_latest_bot_message("s-unknown") is None


def test_latest_bot_message_degrades(conv_coll, user_id, no_mongo):
    assert conversation.get_latest_bot_message("s-1") is None
    assert conversation.get_latest_bot_message("") is None


def test_latest_bot_message_survives_a_corrupt_tail(conv_coll, user_id):
    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id, "messages": ["not-a-dict"]})
    assert conversation.get_latest_bot_message("s-1") is None


def test_list_sessions_is_recent_first_paginated_and_never_ships_the_transcript(
    conv_coll, user_id
):
    """The Recents list would drag every message of every session over the wire if the
    projection regressed. It must ship at most a 4-message head slice as a title
    fallback — and be ordered most-recently-updated first, or Recents isn't recent."""
    for i in range(1, 4):
        conv_coll.insert_one({
            "session_id": f"s-{i}", "user_id": user_id, "ended": False,
            "created_at": f"2026-07-0{i}", "updated_at": f"2026-07-0{i}",
            "title": f"Session {i}",
            "messages": [{"role": "user", "text": f"m{n}"} for n in range(10)],
        })

    rows = conversation.list_sessions(user_id, limit=2)

    assert [r["session_id"] for r in rows] == ["s-3", "s-2"]
    assert len(rows[0]["messages"]) == 4, "only the head slice may be shipped"
    assert rows[0]["title"] == "Session 3"

    # Offset pages past the newest.
    assert [r["session_id"] for r in conversation.list_sessions(user_id, limit=2, offset=1)] == \
        ["s-2", "s-1"]
    assert conversation.list_sessions("nobody") == []


def test_list_sessions_degrades_to_an_empty_list(conv_coll, user_id, no_mongo):
    assert conversation.list_sessions(user_id) == []
    assert conversation.list_sessions("") == []


def test_list_sessions_degrades_when_mongo_dies(broken_mongo, user_id):
    assert conversation.list_sessions(user_id) == []


def test_popping_the_last_exchange_removes_the_reply_it_regenerates(conv_coll, user_id):
    """"Edit last message" regenerates the graph state. If the stored transcript kept
    the OLD exchange, history would show the question the user retracted and the answer
    they never saw."""
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="one", bot_text="a")
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="typo", bot_text="b")

    assert conversation.pop_last_exchange("s-1") is True

    messages = conv_coll.find_one({"session_id": "s-1"})["messages"]
    assert [m["text"] for m in messages] == ["one", "a"]

    # Popping again removes the first exchange; a third pop has nothing to remove.
    assert conversation.pop_last_exchange("s-1") is True
    assert conv_coll.find_one({"session_id": "s-1"})["messages"] == []
    assert conversation.pop_last_exchange("s-1") is False


def test_pop_last_exchange_degrades(conv_coll, user_id, no_mongo):
    assert conversation.pop_last_exchange("s-1") is False
    assert conversation.pop_last_exchange("") is False


def test_pop_last_exchange_survives_a_corrupt_transcript(conv_coll, user_id):
    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id, "messages": ["not-a-dict"]})
    assert conversation.pop_last_exchange("s-1") is False


def test_a_title_can_land_before_the_first_turn_is_recorded(conv_coll, user_id, monkeypatch):
    """The UI fires POST /title concurrently with the first turn. This upserts, so the
    title call may arrive first — if it didn't, the title would be silently dropped and
    the session would show up in Recents as "Untitled"."""
    monkeypatch.setattr(
        conversation, "datetime",
        _advancing_clock(
            datetime(2026, 7, 14, 9, 0, tzinfo=timezone.utc),
            datetime(2026, 7, 14, 9, 1, tzinfo=timezone.utc),
        ),
    )

    assert conversation.set_session_title("s-1", user_id, "  Managing a difficult peer  ") is True
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="hi", bot_text="hey")

    doc = conv_coll.find_one({"session_id": "s-1"})
    assert doc["title"] == "Managing a difficult peer", "the title must be trimmed"
    assert doc["ended"] is False
    assert doc["created_at"] == "2026-07-14T09:00:00+00:00", "the title insert stamped created_at"
    assert len(doc["messages"]) == 2, "the turn appended into the SAME doc, not a new one"
    assert conv_coll.count_documents({}) == 1


def test_a_blank_title_is_rejected(conv_coll, user_id):
    """An empty title would overwrite a good one with nothing."""
    assert conversation.set_session_title("s-1", user_id, "   ") is False
    assert conversation.set_session_title("", user_id, "Title") is False
    assert conv_coll.count_documents({}) == 0


def test_set_session_title_degrades(broken_mongo, no_mongo, user_id):
    assert conversation.set_session_title("s-1", user_id, "Title") is False


def test_set_session_title_degrades_when_mongo_dies(broken_mongo, user_id):
    assert conversation.set_session_title("s-1", user_id, "Title") is False


def test_the_session_title_falls_back_to_the_first_user_message(conv_coll, user_id):
    """Actions are grouped by session NAME on the Actions Screen. Legacy docs predate the
    `title` field, so the fallback is what stops those actions rendering under a blank
    heading."""
    conv_coll.insert_one({
        "session_id": "s-1", "user_id": user_id,
        "messages": [{"role": "bot", "text": "Hello there"},
                     {"role": "user", "text": "  Dealing with my manager  "}],
    })
    assert conversation.get_session_title("s-1") == "Dealing with my manager"

    conversation.set_session_title("s-1", user_id, "Real title")
    assert conversation.get_session_title("s-1") == "Real title", "a stored title wins"

    assert conversation.get_session_title("s-unknown") == ""
    assert conversation.get_session_title("") == ""


def test_the_session_title_is_empty_when_there_is_no_user_message(conv_coll, user_id):
    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id,
                          "messages": [{"role": "bot", "text": "Hello"},
                                       {"role": "user", "text": "   "}]})
    assert conversation.get_session_title("s-1") == ""


def test_get_session_title_degrades(conv_coll, user_id, no_mongo):
    assert conversation.get_session_title("s-1") == ""


def test_get_session_title_survives_a_corrupt_transcript(conv_coll, user_id):
    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id, "messages": ["not-a-dict"]})
    assert conversation.get_session_title("s-1") == ""


def test_a_phase_selection_is_stamped_on_the_last_bot_message(conv_coll, user_id):
    """On Save & Exit no further turn arrives, so this write is the ONLY record of which
    phase button the user pressed. It has to land on the LAST bot message (dotted-path
    positional $set) — writing it anywhere else loses the phase transition."""
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="hi", bot_text="first")
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="ok", bot_text="second")

    assert conversation.record_phase_selection("s-1", "Save & Exit") is True

    messages = conv_coll.find_one({"session_id": "s-1"})["messages"]
    assert messages[3]["phase_user_selection"] == "Save & Exit"
    assert "phase_user_selection" not in messages[1], "only the LAST bot message is stamped"


def test_a_phase_selection_needs_a_bot_message_to_land_on(conv_coll, user_id, no_mongo):
    assert conversation.record_phase_selection("s-1", "Continue") is False  # no Mongo
    assert conversation.record_phase_selection("", "Continue") is False
    assert conversation.record_phase_selection("s-1", "") is False


def test_a_phase_selection_on_a_bot_less_session_is_rejected(conv_coll, user_id):
    assert conversation.record_phase_selection("s-unknown", "Continue") is False

    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id,
                          "messages": [{"role": "user", "text": "hi"}]})
    assert conversation.record_phase_selection("s-1", "Continue") is False


def test_a_phase_selection_survives_a_corrupt_transcript(conv_coll, user_id):
    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id, "messages": ["not-a-dict"]})
    assert conversation.record_phase_selection("s-1", "Continue") is False


def test_a_session_can_only_be_deleted_by_its_owner(conv_coll, user_id):
    """The delete is scoped by user_id as well as session_id. Scoping on session_id alone
    would let any authenticated caller delete any user's history by guessing a UUID."""
    conv_coll.insert_one({"session_id": "s-1", "user_id": user_id, "messages": []})

    assert conversation.delete_session("s-1", "attacker") is False
    assert conv_coll.count_documents({"session_id": "s-1"}) == 1, "the doc must still be there"

    assert conversation.delete_session("s-1", user_id) is True
    assert conv_coll.count_documents({"session_id": "s-1"}) == 0
    assert conversation.delete_session("s-1", user_id) is False, "a second delete matches nothing"


def test_delete_session_degrades(conv_coll, user_id, no_mongo):
    assert conversation.delete_session("s-1", user_id) is False
    assert conversation.delete_session("", user_id) is False
    assert conversation.delete_session("s-1", "") is False


def test_delete_session_degrades_when_mongo_dies(broken_mongo, user_id):
    assert conversation.delete_session("s-1", user_id) is False


def test_the_resume_pill_offers_the_newest_unfinished_session(conv_coll, user_id):
    """A session is resumable until it ends. Offering an ENDED session to resume drops
    the user back into a closed coaching conversation with no way forward."""
    conv_coll.insert_many([
        {"session_id": "s-old", "user_id": user_id, "ended": False,
         "updated_at": "2026-07-01", "title": "Old", "messages": [{"role": "user", "text": "m"}]},
        {"session_id": "s-new", "user_id": user_id, "ended": False,
         "updated_at": "2026-07-05", "title": "New", "messages": []},
        {"session_id": "s-done", "user_id": user_id, "ended": True, "updated_at": "2026-07-09"},
    ])

    doc = conversation.latest_resumable_session(user_id)
    assert doc["session_id"] == "s-new"

    conv_coll.update_one({"session_id": "s-new"}, {"$set": {"ended": True}})
    assert conversation.latest_resumable_session(user_id)["session_id"] == "s-old"

    conv_coll.update_one({"session_id": "s-old"}, {"$set": {"ended": True}})
    assert conversation.latest_resumable_session(user_id) is None


def test_latest_resumable_session_degrades(conv_coll, user_id, no_mongo):
    assert conversation.latest_resumable_session(user_id) is None
    assert conversation.latest_resumable_session("") is None


def test_latest_resumable_session_degrades_when_mongo_dies(broken_mongo, user_id):
    assert conversation.latest_resumable_session(user_id) is None


def test_every_conversation_read_survives_a_dying_mongo(broken_mongo, user_id):
    """The whole module's contract in one place: a Mongo hiccup NEVER surfaces on a
    coaching turn. If any of these raised, the user's turn would 500 because a log write
    failed."""
    assert conversation.get_session("s-1") is None
    assert conversation.get_latest_bot_message("s-1") is None
    assert conversation.get_session_title("s-1") == ""
    assert conversation.get_prior_transcripts(user_id, "s-1") == ""
    assert conversation.pop_last_exchange("s-1") is False
    assert conversation.record_phase_selection("s-1", "Continue") is False
    conversation.record_turn(session_id="s-1", user_id=user_id, user_message="x", bot_text="y")


# ═════════════════════════════════════════════════════════════════════════════
#  app/stores/agentic.py — actions, insights, intake vars, moods
# ═════════════════════════════════════════════════════════════════════════════


def test_the_action_id_is_a_stable_hash_of_the_normalised_text():
    """The UI keys cards on `action_id` and the save/delete API resolves by it. It must
    survive the whitespace/casing drift an LLM introduces between two extractions of the
    same action — otherwise the same action comes back as a brand-new card."""
    assert agentic.stable_id("Book a 1:1 with Sam") == agentic.stable_id("  book a 1:1   with sam ")
    assert agentic.stable_id("a") != agentic.stable_id("b")
    assert len(agentic.stable_id("anything")) == 12
    assert agentic.stable_id(None) == agentic.stable_id("")


def test_load_returns_the_doc_without_its_mongo_id(agentic_coll, user_id, mongo):
    agentic_coll.insert_one({"user_id": user_id, "sessions_completed": 3})

    doc = agentic.load(user_id)
    assert doc == {"user_id": user_id, "sessions_completed": 3}
    assert "_id" not in doc, "the ObjectId is not JSON-serialisable and must not leak"
    assert agentic.load("nobody") == {}


def test_load_degrades(no_mongo, user_id):
    assert agentic.load(user_id) == {}
    assert agentic.load("") == {}


def test_load_degrades_when_mongo_dies(broken_mongo, user_id):
    assert agentic.load(user_id) == {}


def test_new_actions_and_insights_are_stamped_with_everything_the_ui_needs(
    agentic_coll, conv_coll, monkeypatch, frozen_now, user_id
):
    """Every stamp here is load-bearing: `session_date` anchors the 7-day check-in window,
    `chat_title` is how the Actions Screen groups cards, `action_id` is the card key,
    `status: active` is what makes the card visible at all."""
    fixed, frozen = frozen_now
    monkeypatch.setattr(agentic, "datetime", frozen)
    conversation.set_session_title("s-1", user_id, "Managing a difficult peer")

    added_actions, added_insights = agentic.append_actions_insights(
        user_id,
        [{"full_text": "Book a 1:1 with Sam", "verb": "Book"}],
        [{"insight_title": "Avoids conflict", "insight_body": "defers hard talks"}],
        session_id="s-1",
        agent_name="dynamic_actions_insights_agent",
    )

    assert [a["full_text"] for a in added_actions] == ["Book a 1:1 with Sam"]
    stored = agentic_coll.find_one({"user_id": user_id})
    action = stored["actions"][0]
    assert action["status"] == "active"
    assert action["action_id"] == agentic.stable_id("Book a 1:1 with Sam")
    assert action["session_id"] == "s-1"
    assert action["chat_title"] == "Managing a difficult peer"
    assert action["session_date"] == "2026-07-14", "the calendar day the check-in window counts from"
    assert action["ts"] == fixed.isoformat()
    assert action["agent_name"] == action["bot_name"] == "dynamic_actions_insights_agent"
    assert action["verb"] == "Book", "the agent's own fields must survive the stamping"

    insight = stored["insights"][0]
    assert insight["insight_id"] == agentic.stable_id("Avoids conflict")
    assert insight["status"] == "active"
    assert [i["insight_title"] for i in added_insights] == ["Avoids conflict"]


def test_a_later_beat_in_the_same_session_cannot_re_add_an_action(agentic_coll, user_id):
    """QA 2026-07-11: a post-simulation beat re-extracted the same action word-for-word
    and, because action_id is a content hash, the doc ended up holding one id twice —
    stored as both `deleted` and `active`, and re-shipped as a duplicate card. Same
    session + same text = already delivered, whatever its status."""
    agentic.append_actions_insights(
        user_id, [{"full_text": "Book a 1:1"}], [], session_id="s-1")
    agentic.set_action_status(user_id, agentic.stable_id("Book a 1:1"), "deleted")

    added, _ = agentic.append_actions_insights(
        user_id, [{"full_text": "  BOOK A 1:1  "}], [], session_id="s-1")

    assert added == [], "the same session must never re-deliver an action it already sent"
    assert len(agentic_coll.find_one({"user_id": user_id})["actions"]) == 1


def test_a_new_session_may_resurface_an_action_the_user_never_saved(agentic_coll, user_id):
    """Cross-session dedup is deliberately narrower: only actions the user CONFIRMED are
    permanently off the table. One they ignored is still worth suggesting again."""
    agentic.append_actions_insights(
        user_id,
        [{"full_text": "Ignored action"}, {"full_text": "Saved action"}],
        [{"insight_title": "Avoids conflict"}],
        session_id="s-1",
    )
    agentic.set_action_status(user_id, agentic.stable_id("Saved action"), "saved")

    added_actions, added_insights = agentic.append_actions_insights(
        user_id,
        [{"full_text": "Ignored action"}, {"full_text": "Saved action"}],
        [{"insight_title": "avoids   CONFLICT"}],
        session_id="s-2",
    )

    assert [a["full_text"] for a in added_actions] == ["Ignored action"]
    assert added_insights == [], "insights dedup by title across ALL sessions"
    stored = agentic_coll.find_one({"user_id": user_id})
    assert len(stored["actions"]) == 3
    assert len(stored["insights"]) == 1


def test_one_payload_cannot_store_the_same_action_id_twice(agentic_coll, user_id):
    """REGRESSION (bug found by this suite, fixed in append_actions_insights): dedup was
    only run against what was ALREADY STORED, never against the batch being built. One
    extraction that emitted the same action twice — which the generator does, it re-reads
    the same history and is non-deterministic — wrote the SAME action_id into the doc
    twice.

    That is not cosmetic. `action_id` is the card key AND the primary key the save/delete
    API resolves on: `set_action_statuses` maps action_id -> action, so it could only ever
    reach the LAST of the two. Delete the card and one copy stays `active` — and an action
    the user explicitly dismissed comes back next session in `previousUserActions` as
    something they committed to.
    """
    added_actions, added_insights = agentic.append_actions_insights(
        user_id,
        [{"full_text": "Book a 1:1 with Sam"}, {"full_text": "  book a 1:1   WITH SAM "}],
        [{"insight_title": "Avoids conflict"}, {"insight_title": "AVOIDS  CONFLICT"}],
        session_id="s-1",
    )

    assert len(added_actions) == 1, "a duplicate within ONE payload is still a duplicate"
    assert len(added_insights) == 1

    doc = agentic_coll.find_one({"user_id": user_id})
    action_ids = [a["action_id"] for a in doc["actions"]]
    assert len(action_ids) == len(set(action_ids)) == 1, "action_id is a primary key"
    assert len({i["insight_id"] for i in doc["insights"]}) == len(doc["insights"]) == 1

    # And the id the user's delete resolves on now reaches the one and only copy.
    assert agentic.set_action_status(user_id, action_ids[0], "deleted") is True
    assert all(a["status"] == "deleted"
               for a in agentic_coll.find_one({"user_id": user_id})["actions"])


def test_an_insight_only_turn_pushes_only_the_insights_array(agentic_coll, user_id):
    """Most turns produce an insight and no action (or vice versa). The $push payload is
    built per-array: pushing an empty `actions` $each would still create the key, and a
    doc whose `actions` exists-but-is-empty reads differently downstream from one where
    it is absent."""
    added_actions, added_insights = agentic.append_actions_insights(
        user_id, [], [{"insight_title": "Avoids conflict"}], session_id="s-1")

    assert added_actions == [] and len(added_insights) == 1
    doc = agentic_coll.find_one({"user_id": user_id})
    assert "actions" not in doc, "an insight-only turn must not create an actions array"
    assert len(doc["insights"]) == 1


def test_actions_and_insights_without_text_are_dropped(agentic_coll, user_id):
    """An action with no `full_text` is a card with no body — it would render as an empty
    box and give the check-in nothing to recap."""
    added_actions, added_insights = agentic.append_actions_insights(
        user_id,
        [{"verb": "Do", "full_text": ""}],
        [{"insight_body": "no title"}],
        session_id="s-1",
    )

    assert (added_actions, added_insights) == ([], [])
    assert agentic_coll.find_one({"user_id": user_id}) is None, "nothing to store = no doc"


def test_append_is_a_no_op_with_nothing_to_add(no_mongo, mongo, user_id):
    assert agentic.append_actions_insights(user_id, [], [], "s-1") == ([], [])
    assert agentic.append_actions_insights("", [{"full_text": "x"}], [], "s-1") == ([], [])


def test_append_degrades_when_mongo_dies(broken_mongo, user_id):
    assert agentic.append_actions_insights(
        user_id, [{"full_text": "x"}], [{"insight_title": "y"}], "s-1") == ([], [])


def test_saving_an_action_applies_the_users_inline_edits(agentic_coll, user_id):
    """The card is editable. The action must keep its ORIGINAL action_id even when the
    user rewrites the text — the id is a content hash, so recomputing it would orphan the
    card the UI is still holding and the next save/delete would resolve nothing."""
    agentic.append_actions_insights(
        user_id, [{"full_text": "Book a 1:1", "roi_metric": "legacy"}], [], session_id="s-1")
    action_id = agentic.stable_id("Book a 1:1")

    ok = agentic.set_action_status(
        user_id, action_id, "saved",
        roi_metrics=["decision making", "Inspiration"],
        full_text="Book a 1:1 with Sam this Thursday",
        action_body="  ",                      # blank edits are ignored
        expected_outcome="Sam knows where he stands",
    )

    assert ok is True
    action = agentic_coll.find_one({"user_id": user_id})["actions"][0]
    assert action["status"] == "saved"
    assert action["action_id"] == action_id, "the id must be pinned across a text edit"
    assert action["full_text"] == "Book a 1:1 with Sam this Thursday"
    assert action["expected_outcome"] == "Sam knows where he stands"
    assert "action_body" not in action, "a blank edit must not write an empty field"
    assert action["roi_metrics"] == ["Decision making", "Inspiration"], "canonical catalogue casing"
    assert "roi_metric" not in action, "the legacy single-value field must be removed"


def test_an_action_stored_before_ids_existed_still_resolves_by_its_text(agentic_coll, user_id):
    """Legacy docs have no `action_id`. Falling back to the stable id recomputed from
    full_text is the only thing that lets a user save or delete those cards at all."""
    agentic_coll.insert_one({
        "user_id": user_id, "actions": [{"full_text": "Legacy action"}],  # no action_id
    })

    assert agentic.set_action_status(user_id, agentic.stable_id("Legacy action"), "deleted") is True

    action = agentic_coll.find_one({"user_id": user_id})["actions"][0]
    assert action["status"] == "deleted"
    assert action["action_id"] == agentic.stable_id("Legacy action"), "the id is back-filled"


def test_set_action_status_rejects_unknown_ids_statuses_and_users(agentic_coll, user_id, mongo):
    """An unrecognised status must be REJECTED, not written: a typo'd status would make
    the action invisible to every read filter (which match on the known values) and the
    user's saved commitment would silently vanish."""
    agentic.append_actions_insights(user_id, [{"full_text": "Book a 1:1"}], [], session_id="s-1")
    action_id = agentic.stable_id("Book a 1:1")

    assert agentic.set_action_status(user_id, action_id, "done") is False   # not a known status
    assert agentic.set_action_status(user_id, "", "saved") is False
    assert agentic.set_action_status(user_id, "no-such-id", "saved") is False
    assert agentic.set_action_status("nobody", action_id, "saved") is False  # no doc

    assert agentic_coll.find_one({"user_id": user_id})["actions"][0]["status"] == "active"


def test_set_action_status_degrades(no_mongo, user_id):
    assert agentic.set_action_status(user_id, "id", "saved") is False


def test_set_action_status_survives_a_corrupt_actions_array(agentic_coll, user_id):
    agentic_coll.insert_one({"user_id": user_id, "actions": ["not-a-dict"]})
    assert agentic.set_action_status(user_id, "id", "saved") is False


def test_the_batch_status_update_applies_every_valid_row_and_reports_the_rest(
    agentic_coll, user_id
):
    """The final carousel saves/skips several cards at once. A partial failure must not
    lose the good rows — and the per-row `ok` flags are what the API turns into the
    user-visible result, so a row that silently reported ok while writing nothing would
    tell the user their action was saved when it wasn't."""
    agentic.append_actions_insights(
        user_id,
        [{"full_text": "One", "roi_metric": "legacy"}, {"full_text": "Two"}, {"full_text": "Three"}],
        [], session_id="s-1",
    )
    ids = {t: agentic.stable_id(t) for t in ("One", "Two", "Three")}

    results = agentic.set_action_statuses(user_id, [
        {"action_id": ids["One"], "status": "saved", "roi_metrics": "inspiration",
         "full_text": "One, revised"},
        {"action_id": ids["Two"], "status": "bogus"},          # unknown status -> not ok
        {"action_id": "missing", "status": "saved"},           # unknown id -> not ok
        {"action_id": ids["Three"], "status": "skipped"},
        {"status": "saved"},                                   # no id at all -> not ok
    ])

    assert [r["ok"] for r in results] == [True, False, False, True, False]
    assert results[0]["roi_metrics"] == ["Inspiration"], "a bare string is coerced to a list"

    by_id = {a["action_id"]: a for a in agentic_coll.find_one({"user_id": user_id})["actions"]}
    assert by_id[ids["One"]]["status"] == "saved"
    assert by_id[ids["One"]]["full_text"] == "One, revised"
    assert "roi_metric" not in by_id[ids["One"]], "the legacy field must be dropped"
    assert by_id[ids["Two"]]["status"] == "active", "a rejected row must not be written"
    assert by_id[ids["Three"]]["status"] == "skipped"


def test_the_batch_update_writes_nothing_when_no_row_matches(agentic_coll, user_id, mongo):
    agentic.append_actions_insights(user_id, [{"full_text": "One"}], [], session_id="s-1")
    before = agentic_coll.find_one({"user_id": user_id})["updated_at"]

    results = agentic.set_action_statuses(user_id, [{"action_id": "missing", "status": "saved"}])

    assert results == [{"action_id": "missing", "ok": False, "roi_metrics": None}]
    assert agentic_coll.find_one({"user_id": user_id})["updated_at"] == before, "no pointless write"


def test_the_batch_update_degrades(no_mongo, mongo, user_id):
    updates = [{"action_id": "a", "status": "saved"}]
    assert agentic.set_action_statuses(user_id, updates) == \
        [{"action_id": "a", "ok": False, "roi_metrics": None}]
    assert agentic.set_action_statuses("nobody", updates) == \
        [{"action_id": "a", "ok": False, "roi_metrics": None}]
    assert agentic.set_action_statuses(user_id, []) == []


def test_the_batch_update_degrades_when_mongo_dies(broken_mongo, user_id):
    assert agentic.set_action_statuses(user_id, [{"action_id": "a", "status": "saved"}]) == \
        [{"action_id": "a", "ok": False, "roi_metrics": None}]


def test_get_action_resolves_a_tapped_card_even_after_it_was_deleted(agentic_coll, user_id):
    """The standalone check-in agent is invoked BY tapping one action's card, so it must
    resolve that action's text even when the user has since dismissed it — the check-in
    is explicitly about the action they chose."""
    agentic_coll.insert_one({"user_id": user_id, "actions": [
        "corrupt-entry",                                        # must be skipped, not crash
        {"full_text": "Book a 1:1", "status": "deleted"},       # no stored action_id
    ]})

    action = agentic.get_action(user_id, agentic.stable_id("Book a 1:1"))
    assert action["full_text"] == "Book a 1:1"

    assert agentic.get_action(user_id, "no-such-id") is None
    assert agentic.get_action("nobody", "id") is None
    assert agentic.get_action(user_id, "") is None


def test_get_action_degrades(no_mongo, broken_mongo, user_id):
    assert agentic.get_action(user_id, "id") is None


def test_marking_a_checkin_complete_is_idempotent(agentic_coll, user_id):
    """The check-in must never fire twice for the same batch. $addToSet is what makes a
    harness retry a no-op — with $push a retry would double the entry and (worse) any
    later bug that re-ran the check-in would re-surface actions the user already closed."""
    assert agentic.mark_checkin_complete(user_id, ["s-1", "s-2"]) is True
    assert agentic.mark_checkin_complete(user_id, ["s-2", "s-3", ""]) is True

    doc = agentic_coll.find_one({"user_id": user_id})
    assert sorted(doc["checkin_complete_sessions"]) == ["s-1", "s-2", "s-3"]

    assert agentic.mark_checkin_complete(user_id, []) is False
    assert agentic.mark_checkin_complete(user_id, [""]) is False, "blank ids are not a batch"
    assert agentic.mark_checkin_complete("", ["s-1"]) is False


def test_mark_checkin_complete_degrades(no_mongo, broken_mongo, user_id):
    assert agentic.mark_checkin_complete(user_id, ["s-1"]) is False


def test_the_ic_profile_and_pattern_mirror_are_persisted_for_the_next_session(
    agentic_coll, user_id
):
    """The IC profile is the cumulative pattern table the NEXT session's pattern_agent
    reads as {prev_pattern_table}. Persisting a blank one would wipe the user's
    accumulated pattern history with an empty string."""
    assert agentic.save_ic_profile(user_id, "| pattern | evidence |") is True
    assert agentic.save_pattern_mirror(user_id, "You defer hard conversations.") is True

    doc = agentic_coll.find_one({"user_id": user_id})
    assert doc["ic_profile"] == "| pattern | evidence |"
    assert doc["pattern_mirror"] == "You defer hard conversations."

    for blank in ("", "   "):
        assert agentic.save_ic_profile(user_id, blank) is False
        assert agentic.save_pattern_mirror(user_id, blank) is False
    assert agentic.save_ic_profile("", "x") is False
    assert agentic.save_pattern_mirror("", "x") is False
    assert agentic_coll.find_one({"user_id": user_id})["ic_profile"] == "| pattern | evidence |"


def test_ic_profile_and_pattern_mirror_degrade(no_mongo, broken_mongo, user_id):
    assert agentic.save_ic_profile(user_id, "x") is False
    assert agentic.save_pattern_mirror(user_id, "x") is False


def test_intake_vars_are_merged_field_by_field(agentic_coll, user_id):
    """The intake agent echoes the FULL variables_set every turn. A whole-object $set
    would clobber everything captured earlier in the session with the current turn's
    mostly-null echo — so each key is written on its own dotted path."""
    assert agentic.save_intake_vars(user_id, {"userRoleContext": "Engineering manager"}) is True
    assert agentic.save_intake_vars(user_id, {"coachingNeeds": "delegation"}) is True

    doc = agentic_coll.find_one({"user_id": user_id})
    assert doc["intake_vars"] == {"userRoleContext": "Engineering manager",
                                  "coachingNeeds": "delegation"}


def test_a_once_in_lifetime_intake_var_is_never_overwritten(agentic_coll, user_id):
    """`userRoleContext` is once_in_lifetime in the registry. The intake agent re-emits
    the whole schema each turn with nulls for everything it didn't just capture — without
    the set-once guard, turn 2 would overwrite the answer captured on turn 1 with null."""
    agentic.save_intake_vars(user_id, {"userRoleContext": "Engineering manager"})

    assert agentic.save_intake_vars(
        user_id, {"userRoleContext": None, "coachingNeeds": "delegation"}) is True

    doc = agentic_coll.find_one({"user_id": user_id})
    assert doc["intake_vars"]["userRoleContext"] == "Engineering manager", (
        "a once_in_lifetime var must survive the agent's null echo"
    )
    assert doc["intake_vars"]["coachingNeeds"] == "delegation"

    # Every key skipped => nothing to write, but still a success (not an error).
    before = agentic_coll.find_one({"user_id": user_id})["updated_at"]
    assert agentic.save_intake_vars(user_id, {"userRoleContext": "Overwrite me"}) is True
    doc = agentic_coll.find_one({"user_id": user_id})
    assert doc["intake_vars"]["userRoleContext"] == "Engineering manager"
    assert doc["updated_at"] == before, "a no-op must not touch the document"


def test_an_empty_once_in_lifetime_value_can_still_be_filled_in(agentic_coll, user_id):
    """Set-once means set once to something REAL. A stored empty string must not lock the
    variable out forever."""
    agentic_coll.insert_one({"user_id": user_id, "intake_vars": {"coachingHistory": ""}})

    agentic.save_intake_vars(user_id, {"coachingHistory": "Two years with a mentor"})

    doc = agentic_coll.find_one({"user_id": user_id})
    assert doc["intake_vars"]["coachingHistory"] == "Two years with a mentor"


def test_save_intake_vars_degrades(no_mongo, mongo, broken_mongo, user_id):
    assert agentic.save_intake_vars(user_id, {"k": "v"}) is False
    assert agentic.save_intake_vars(user_id, {}) is False
    assert agentic.save_intake_vars("", {"k": "v"}) is False


def test_a_mood_capture_is_appended_with_its_thumbnails_and_polarity(
    agentic_coll, monkeypatch, frozen_now, user_id
):
    """The mood wheel renders from `thumbnail_url` + `polarity`. An unknown emotion must
    degrade to a blank thumbnail rather than a broken image URL, and the entry must be
    APPENDED (one per session) — a $set would leave the user with only their last mood
    ever recorded."""
    fixed, frozen = frozen_now
    monkeypatch.setattr(agentic, "datetime", frozen)
    monkeypatch.setattr(config, "STRAPI_MEDIA_URL", "https://cdn.example/media")
    conversation.set_session_title("s-1", user_id, "Tough week")

    ok = agentic.save_mood_capture(user_id, "s-1", {
        "mapped_emotions": ["Happy", "Anxious", "Nonexistent"],
        "positive_emotions": ["Happy"],
        "negative_emotions": ["Anxious"],
        "mood_capture_complete": True,
        "responses": [{"q": "How was the week?", "a": "Mixed"}],
        "not_a_mood_field": "must not be persisted",
    })
    assert ok is True

    entry = agentic_coll.find_one({"user_id": user_id})["moods"][0]
    assert entry["session_id"] == "s-1" and entry["chat_title"] == "Tough week"
    assert entry["date"] == "14/07/2026" and entry["ts"] == fixed.isoformat()
    assert entry["mood_capture_complete"] is True
    assert entry["positive_emotions"] == ["Happy"] and entry["negative_emotions"] == ["Anxious"]
    assert "not_a_mood_field" not in entry, "only the whitelisted mood fields are persisted"

    moods = entry["responses"][0]["moods"]
    assert moods[0] == {"name": "Happy", "polarity": "Positive",
                        "thumbnail_url": "https://cdn.example/media/Happy_3fe029a86d.svg"}
    assert moods[1]["polarity"] == "Negative"
    assert moods[2] == {"name": "Nonexistent", "polarity": "Unknown", "thumbnail_url": ""}
    assert entry["responses"][0]["responses"] == [{"q": "How was the week?", "a": "Mixed"}]

    agentic.save_mood_capture(user_id, "s-2", {"mapped_emotions": ["Proud"]})
    assert len(agentic_coll.find_one({"user_id": user_id})["moods"]) == 2, "moods accumulate"


def test_a_mood_capture_without_a_media_host_emits_no_thumbnail_url(
    agentic_coll, monkeypatch, user_id
):
    """STRAPI_MEDIA_URL is unset in some environments. Concatenating anyway would ship
    "/Happy_x.svg" — a relative path the app would resolve against its OWN host."""
    monkeypatch.setattr(config, "STRAPI_MEDIA_URL", "")

    agentic.save_mood_capture(user_id, "s-1", {"mapped_emotions": ["Happy"]})

    entry = agentic_coll.find_one({"user_id": user_id})["moods"][0]
    assert entry["responses"][0]["moods"][0]["thumbnail_url"] == ""


def test_an_empty_mood_capture_records_no_responses_block(agentic_coll, user_id):
    agentic.save_mood_capture(user_id, "s-1", {"mood_capture_complete": False})

    entry = agentic_coll.find_one({"user_id": user_id})["moods"][0]
    assert entry["responses"] == []


def test_save_mood_capture_degrades(no_mongo, mongo, broken_mongo, user_id):
    assert agentic.save_mood_capture(user_id, "s-1", {"mapped_emotions": ["Happy"]}) is False
    assert agentic.save_mood_capture(user_id, "s-1", {}) is False
    assert agentic.save_mood_capture("", "s-1", {"mapped_emotions": ["Happy"]}) is False


def test_closing_a_session_bumps_the_completed_counter(agentic_coll, monkeypatch, frozen_now, user_id):
    """`sessions_completed` is THE repeat-user gate and `last_session_at` is the
    session-close timestamp (distinct from `updated_at`, which any write touches). $inc
    is what makes the counter survive two builders racing on the same user."""
    fixed, frozen = frozen_now
    monkeypatch.setattr(agentic, "datetime", frozen)

    assert agentic.save_user_context_model(user_id, {"dimension_1": "value"}) is True
    assert agentic.save_user_context_model(user_id, {"dimension_1": "revised"}) is True

    doc = agentic_coll.find_one({"user_id": user_id})
    assert doc["sessions_completed"] == 2, "$inc must accumulate, not overwrite"
    assert doc["user_context_model"] == {"dimension_1": "revised"}
    assert doc["last_session_at"] == fixed.isoformat()

    assert agentic.save_user_context_model(user_id, {}) is False, "an empty model is not a close"
    assert agentic.save_user_context_model("", {"d": 1}) is False


def test_save_user_context_model_degrades(no_mongo, broken_mongo, user_id):
    assert agentic.save_user_context_model(user_id, {"d": 1}) is False


# ═════════════════════════════════════════════════════════════════════════════
#  app/stores/org.py — client values (Extract3)
# ═════════════════════════════════════════════════════════════════════════════


def test_org_values_are_found_by_every_plausible_key(mongo):
    """The org doc's key field is not pinned (FIELD-MAPPING-TODO). Each probe is a real
    deployment's schema; losing one means that client's values silently resolve to NULL
    and the coach stops referencing them."""
    coll = mongo[config.MONGO_BACKEND_DB][config.MONGO_ORG_COLLECTION]
    oid = ObjectId()
    coll.insert_many([
        {"orgId": "by-org-id", "values": ["Integrity"]},
        {"org_id": "by-snake-id", "values": ["Ownership"]},
        {"_id": "by-string-id", "values": ["Candour"]},
        {"_id": oid, "values": ["Craft"]},
    ])

    assert read_names("by-org-id") == ["Integrity"]
    assert read_names("by-snake-id") == ["Ownership"]
    assert read_names("by-string-id") == ["Candour"]
    assert read_names(str(oid)) == ["Craft"], "an ObjectId org id must be coerced"


def read_names(org_id):
    return [v["name"] for v in org.read_org_values(org_id)["values"]]


def test_org_values_are_extracted_from_every_shape(mongo):
    """The values list arrives as strings, as {name,description}, as {value,desc}, as
    {title,detail}, or as a plain mapping, depending on who loaded the client's data."""
    coll = mongo[config.MONGO_BACKEND_DB][config.MONGO_ORG_COLLECTION]

    coll.insert_one({"orgId": "o1", "values": ["  Integrity  ", {"name": "Ownership",
                     "description": "we own outcomes"}], "valuesSourceLink": "  https://x  "})
    result = org.read_org_values("o1")
    assert result["values"] == [
        {"name": "Integrity", "description": ""},
        {"name": "Ownership", "description": "we own outcomes"},
    ]
    assert result["source_link"] == "https://x"

    coll.insert_one({"orgId": "o2", "coreValues": [{"value": "Candour", "desc": "say it"},
                                                   {"title": "Craft", "detail": "do it well"},
                                                   {"description": "nameless"},   # dropped
                                                   42]})                          # dropped
    assert org.read_org_values("o2")["values"] == [
        {"name": "Candour", "description": "say it"},
        {"name": "Craft", "description": "do it well"},
    ]

    coll.insert_one({"orgId": "o3", "organizationValues": {"Integrity": "we tell the truth"},
                     "source_link": "https://legacy"})
    result = org.read_org_values("o3")
    assert result["values"] == [{"name": "Integrity", "description": "we tell the truth"}]
    assert result["source_link"] == "https://legacy"

    coll.insert_one({"orgId": "o4", "orgValues": ["Speed"]})
    assert read_names("o4") == ["Speed"]


def test_an_org_with_no_values_resolves_to_an_empty_extract(mongo, no_mongo):
    """Extract3 resolves to NULL on an empty list — the shape must always be
    {"values": [], "source_link": ""}, never None, or the caller unpacks a None."""
    assert org.read_org_values("o1") == {"values": [], "source_link": ""}   # no Mongo
    assert org.read_org_values("") == {"values": [], "source_link": ""}


def test_an_unknown_org_resolves_to_an_empty_extract(mongo):
    coll = mongo[config.MONGO_BACKEND_DB][config.MONGO_ORG_COLLECTION]
    coll.insert_one({"orgId": "known", "values": ["Integrity"]})

    assert org.read_org_values("unknown-org") == {"values": [], "source_link": ""}
    # An org doc with no values field at all is not an error either.
    coll.insert_one({"orgId": "empty", "name": "Acme"})
    assert org.read_org_values("empty") == {"values": [], "source_link": ""}


def test_org_values_degrade_when_mongo_dies(broken_mongo):
    assert org.read_org_values("o1") == {"values": [], "source_link": ""}


def test_org_id_coercion_tolerates_non_objectid_ids():
    assert org._to_object_id("not-an-objectid") is None
    oid = ObjectId()
    assert org._to_object_id(str(oid)) == oid


# ═════════════════════════════════════════════════════════════════════════════
#  app/stores/dynamic_vars.py — captured session variables
# ═════════════════════════════════════════════════════════════════════════════


@pytest.fixture
def dyn_coll(mongo):
    return mongo[config.MONGO_BACKEND_DB][config.MONGO_DYNAMIC_VARS_COLLECTION]


def test_a_dotted_variable_becomes_a_nested_document_with_flattened_provenance(
    dyn_coll, monkeypatch, frozen_now, user_id
):
    """Mongo reads "." as a path, so "coaching_style_context.selected_style" MUST create
    a nested doc — while its provenance key must flatten the dot (a literal "." in a
    field name is what Mongo would otherwise turn into a second level of nesting, so the
    provenance for two sibling vars would overwrite each other)."""
    _, frozen = frozen_now
    monkeypatch.setattr(dynamic_vars, "datetime", frozen)

    ok = dynamic_vars.save_session_dynamic_vars(
        user_id, "s-1",
        {"coaching_style_context.selected_style": "Socratic", "session_goal": "delegate more"},
        stage="coaching_intake_agent", turn_seq=3, request_id="req-7",
    )
    assert ok is True

    doc = dyn_coll.find_one({"user_id": user_id})
    assert doc["coaching_style_context"] == {"selected_style": "Socratic"}
    assert doc["session_goal"] == "delegate more"

    prov = doc["_provenance"]["coaching_style_context__selected_style"]
    assert prov == {
        "original_key": "coaching_style_context.selected_style",
        "session_id": "s-1", "stage": "coaching_intake_agent", "turn_seq": 3,
        "request_id": "req-7", "generated_at": "2026-07-14T12:00:00+00:00",
    }


def test_an_every_session_var_is_written_once_per_session_then_overwritten_next_session(
    dyn_coll, user_id
):
    """Guard against the agent re-emitting the same value every turn: the second write in
    the SAME session is skipped (no pointless churn), but a NEW session must overwrite —
    otherwise the value freezes at whatever the user said the first time, forever."""
    dynamic_vars.save_session_dynamic_vars(user_id, "s-1", {"session_goal": "delegate more"})
    dynamic_vars.save_session_dynamic_vars(user_id, "s-1", {"session_goal": "CHANGED MID-SESSION"})

    doc = dyn_coll.find_one({"user_id": user_id})
    assert doc["session_goal"] == "delegate more", "the same session must not rewrite it"

    dynamic_vars.save_session_dynamic_vars(user_id, "s-2", {"session_goal": "hire a deputy"})
    doc = dyn_coll.find_one({"user_id": user_id})
    assert doc["session_goal"] == "hire a deputy", "a new session must overwrite"
    assert doc["_provenance"]["session_goal"]["session_id"] == "s-2"


def test_a_once_in_lifetime_var_survives_the_agents_null_echo(dyn_coll, user_id):
    """`coachability_score` is once_in_lifetime. The intake agent re-emits the whole
    variables_set every turn with nulls for what it didn't capture — the set-once guard
    is the only thing standing between a captured score and a null that erases it."""
    dynamic_vars.save_session_dynamic_vars(user_id, "s-1", {"coachability_score": 8})

    # Same session, later turn, and a different session too: neither may overwrite.
    assert dynamic_vars.save_session_dynamic_vars(user_id, "s-1", {"coachability_score": 3}) is True
    assert dynamic_vars.save_session_dynamic_vars(user_id, "s-9", {"coachability_score": 1}) is True

    assert dyn_coll.find_one({"user_id": user_id})["coachability_score"] == 8


def test_an_empty_stored_value_does_not_lock_a_once_in_lifetime_var(dyn_coll, user_id):
    """Set-once means set once to something REAL — an empty string/list stored by a
    half-finished capture must still be fillable."""
    dyn_coll.insert_one({"user_id": user_id, "coachingNeeds": "", "userMotivations": []})

    dynamic_vars.save_session_dynamic_vars(
        user_id, "s-1", {"coachingNeeds": "delegation", "userMotivations": ["impact"]})

    doc = dyn_coll.find_one({"user_id": user_id})
    assert doc["coachingNeeds"] == "delegation"
    assert doc["userMotivations"] == ["impact"]


def test_a_variable_disabled_in_the_registry_is_never_written(dyn_coll, user_id):
    """capture_enabled=FALSE in the workbook is how a non-technical editor kills a
    variable without a deploy. If it still got written, that switch would be a lie."""
    ok = dynamic_vars.save_session_dynamic_vars(
        user_id, "s-1", {"session_count": 99, "session_goal": "delegate more"})

    assert ok is True
    doc = dyn_coll.find_one({"user_id": user_id})
    assert "session_count" not in doc, "session_count is capture_enabled=FALSE in the registry"
    assert doc["session_goal"] == "delegate more"


def test_an_all_disabled_payload_is_a_clean_no_op_even_without_mongo(no_mongo, user_id):
    """Nothing survives the registry filter => nothing to persist => success, and no
    pointless connection attempt (which on a dead Mongo costs a 4s timeout per turn)."""
    assert dynamic_vars.save_session_dynamic_vars(
        user_id, "s-1", {"session_count": 1, "last_session_at": "x"}) is True


def test_saving_dynamic_vars_degrades(no_mongo, user_id):
    assert dynamic_vars.save_session_dynamic_vars(user_id, "s-1", {"session_goal": "x"}) is False
    assert dynamic_vars.save_session_dynamic_vars(user_id, "s-1", {}) is False
    assert dynamic_vars.save_session_dynamic_vars("", "s-1", {"session_goal": "x"}) is False


def test_saving_dynamic_vars_degrades_when_mongo_dies(broken_mongo, user_id):
    assert dynamic_vars.save_session_dynamic_vars(user_id, "s-1", {"session_goal": "x"}) is False


def test_reading_dynamic_vars_strips_internal_bookkeeping(dyn_coll, user_id):
    """Everything this returns is merged into user_context and becomes a prompt
    placeholder. Leaking `_provenance` or the ObjectId `_id` would put internal
    bookkeeping (and an unserialisable type) into the model's prompt."""
    dyn_coll.insert_one({
        "user_id": user_id,
        "session_goal": "delegate more",
        "coaching_style_context": {"selected_style": "Socratic"},
        "_provenance": {"session_goal": {"session_id": "s-1"}},
        "updated_at": "2026-07-14T12:00:00+00:00",
    })

    assert dynamic_vars.read_dynamic_vars(user_id) == {
        "session_goal": "delegate more",
        "coaching_style_context": {"selected_style": "Socratic"},
    }


def test_reading_dynamic_vars_degrades_to_empty(dyn_coll, user_id, no_mongo):
    assert dynamic_vars.read_dynamic_vars(user_id) == {}
    assert dynamic_vars.read_dynamic_vars("") == {}


def test_reading_dynamic_vars_returns_empty_for_an_unknown_user(mongo, user_id):
    assert dynamic_vars.read_dynamic_vars(user_id) == {}


def test_reading_dynamic_vars_degrades_when_mongo_dies(broken_mongo, user_id):
    assert dynamic_vars.read_dynamic_vars(user_id) == {}
