"""Transparent AI memory — the statements, their basis, and when to stay quiet.

This surface makes claims about a person to their face, so the tests are mostly about
RESTRAINT: every rule must refuse to speak below its sample size and below its margin. A
confident sentence built on four check-ins is worse than silence — it teaches someone to
distrust the one surface that is asking them to trust us with a diary.

The other half is the consent contract: a declined category is never READ, so it cannot
reach a statement. That is stronger than filtering the output, and it is what `sources`
reports back.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest

from app.stores import patterns

NOW = datetime(2026, 7, 15, 12, 0, tzinfo=timezone.utc)  # a Wednesday


def mood(days_ago: int = 0, hour: int = 9, *, word: str = "anxious", intensity: int = 4):
    ts = (NOW - timedelta(days=days_ago)).replace(hour=hour)
    return {"ts": ts.isoformat(), "mood": word, "intensity": intensity}


def entry(days_ago: int = 0, hour: int = 20, **extra):
    return {"ts": (NOW - timedelta(days=days_ago)).replace(hour=hour).isoformat(), **extra}


# ── is_difficult ─────────────────────────────────────────────────────────────


@pytest.mark.parametrize("word", ["anxious", "ANXIOUS", " Sad ", "overwhelmed"])
def test_a_negative_word_marks_a_check_in_difficult(word):
    assert patterns.is_difficult({"mood": word, "intensity": 0})


def test_high_intensity_marks_it_difficult_whatever_the_word():
    assert patterns.is_difficult({"mood": "fine", "intensity": 5})


def test_an_ordinary_check_in_is_not_difficult():
    assert not patterns.is_difficult({"mood": "calm", "intensity": 1})


@pytest.mark.parametrize("junk", [{"mood": "calm", "intensity": "loud"}, {"mood": "calm", "intensity": None}, {}])
def test_junk_intensity_is_not_difficult_and_does_not_raise(junk):
    assert not patterns.is_difficult(junk)


# ── timestamps: one corrupt row must not blank the dashboard ─────────────────


@pytest.mark.parametrize("bad", ["", "not-a-date", None, 12345])
def test_an_unparseable_timestamp_is_skipped_not_fatal(bad):
    assert patterns._ts({"ts": bad}) is None


def test_a_naive_timestamp_is_read_as_utc_rather_than_dropped():
    got = patterns._ts({"ts": "2026-07-15T09:00:00"})
    assert got is not None and got.tzinfo is timezone.utc


# ── rule 1: hardest time of day ──────────────────────────────────────────────


def test_it_says_nothing_below_six_difficult_check_ins():
    assert patterns.hardest_time_of_day([mood(i) for i in range(5)]) is None


def test_it_names_the_dominant_bucket_with_its_basis():
    got = patterns.hardest_time_of_day([mood(i, hour=9) for i in range(8)])
    assert got["statement"] == "Mornings tend to be your hardest time of day."
    assert got["basis"] == "8 of your 8 difficult check-ins landed there"


def test_a_three_way_split_says_nothing():
    """Without the majority test, "Mornings" wins at 34% and says nothing true."""
    moods = [mood(i, hour=9) for i in range(3)] + [mood(i + 3, hour=14) for i in range(3)] + \
            [mood(i + 6, hour=20) for i in range(3)]
    assert patterns.hardest_time_of_day(moods) is None


def test_a_bare_majority_speaks():
    moods = [mood(i, hour=20) for i in range(5)] + [mood(i + 5, hour=14) for i in range(3)]
    got = patterns.hardest_time_of_day(moods)
    assert got and got["statement"].startswith("Evenings")


def test_ordinary_check_ins_do_not_count_toward_the_sample():
    calm = [mood(i, hour=9, word="calm", intensity=1) for i in range(20)]
    assert patterns.hardest_time_of_day(calm) is None


def test_undateable_difficult_check_ins_cannot_reach_the_threshold():
    # 6 difficult rows, none placeable in a bucket -> no claim.
    assert patterns.hardest_time_of_day([{"mood": "anxious", "ts": "junk"} for _ in range(6)]) is None


@pytest.mark.parametrize("hour,expected", [(0, "Mornings"), (11, "Mornings"), (12, "Afternoons"),
                                           (17, "Afternoons"), (18, "Evenings"), (23, "Evenings")])
def test_the_day_splits_into_three_at_noon_and_six(hour, expected):
    assert patterns._bucket(hour) == expected


# ── rule 2: journaling ───────────────────────────────────────────────────────


def test_journaling_needs_three_journaling_days():
    moods = [mood(i, word="calm", intensity=1) for i in range(20)]
    assert patterns.journaling_helps(moods, [entry(1), entry(2)]) is None


def test_journaling_speaks_when_the_next_day_is_calmer_by_a_margin():
    # Days 1-3 follow a journal entry and are calm; days 5-7 do not and are difficult.
    journal = [entry(2), entry(3), entry(4)]
    calm = [mood(d, word="calm", intensity=1) for d in (1, 2, 3)]
    hard = [mood(d, word="anxious", intensity=5) for d in (10, 11, 12)]
    got = patterns.journaling_helps(calm + hard, journal)
    assert got["statement"] == "Check-ins run calmer the day after you journal."
    assert "3 journaling days" in got["basis"]


def test_journaling_stays_quiet_when_the_difference_is_small():
    journal = [entry(2), entry(3), entry(4)]
    same = [mood(d, word="calm", intensity=1) for d in (1, 2, 3, 10, 11, 12)]
    assert patterns.journaling_helps(same, journal) is None


def test_journaling_stays_quiet_without_enough_comparison_days():
    journal = [entry(2), entry(3), entry(4)]
    assert patterns.journaling_helps([mood(1)], journal) is None


def test_the_claim_is_correlation_not_causation():
    """The wording matters: we cannot know direction and must not imply it."""
    journal = [entry(2), entry(3), entry(4)]
    calm = [mood(d, word="calm", intensity=1) for d in (1, 2, 3)]
    hard = [mood(d, word="anxious", intensity=5) for d in (10, 11, 12)]
    got = patterns.journaling_helps(calm + hard, journal)
    assert "run calmer" in got["statement"]
    assert "makes you" not in got["statement"]


def test_a_day_share_needs_three_days():
    assert patterns._day_difficult_share([mood(1), mood(1)], lambda _d: True) is None


# ── rule 3: sleep ────────────────────────────────────────────────────────────


def _sleep(days_ago: int, minutes: int):
    return entry(days_ago, duration_min=minutes)


def test_sleep_needs_five_nights():
    assert patterns.sleep_shows_up([mood(i) for i in range(9)], [_sleep(i, 480) for i in range(4)]) is None


def test_sleep_speaks_when_short_nights_read_worse():
    sleep = [_sleep(d, 480) for d in (1, 2, 3)] + [_sleep(d, 300) for d in (4, 5, 6)]
    rested = [mood(d, word="calm", intensity=1) for d in (1, 2, 3)]
    short = [mood(d, word="anxious", intensity=4) for d in (4, 5, 6)]
    got = patterns.sleep_shows_up(rested + short, sleep)
    assert got["statement"].startswith("Mornings after 7+ hours")
    assert got["basis"] == "3 rested vs 3 short-sleep mornings"


def test_sleep_stays_quiet_when_the_gap_is_small():
    sleep = [_sleep(d, 480) for d in (1, 2, 3)] + [_sleep(d, 300) for d in (4, 5, 6)]
    flat = [mood(d, word="calm", intensity=2) for d in (1, 2, 3, 4, 5, 6)]
    assert patterns.sleep_shows_up(flat, sleep) is None


def test_sleep_needs_three_of_each_kind_of_night():
    sleep = [_sleep(d, 480) for d in (1, 2, 3, 4)] + [_sleep(5, 300)]
    moods = [mood(d, word="calm", intensity=1) for d in (1, 2, 3, 4)] + [mood(5, intensity=5)]
    assert patterns.sleep_shows_up(moods, sleep) is None


def test_rows_without_a_duration_are_ignored():
    sleep = [_sleep(d, 480) for d in (1, 2, 3, 4, 5)] + [entry(6)] + [entry(7, duration_min="lots")]
    assert patterns.sleep_shows_up([mood(1)], sleep) is None


def test_the_rested_line_matches_the_weekly_insight():
    # Two surfaces disagreeing about what "rested" means is a bug the user sees.
    assert patterns.RESTED_MIN == 420


# ── rule 4: weekday rhythm ───────────────────────────────────────────────────


def test_rhythm_needs_ten_check_ins():
    assert patterns.weekday_rhythm([mood(i) for i in range(9)]) is None


def test_it_notices_a_weekday_person():
    # 2026-07-15 is a Wednesday; step back in weeks to stay on weekdays.
    moods = [mood(days_ago=7 * i) for i in range(12)]
    got = patterns.weekday_rhythm(moods)
    assert got["statement"] == "You show up most on weekdays — weekends drift."
    assert "12 of 12" in got["basis"]


def test_it_notices_a_weekend_person():
    # 3 days back from Wednesday = Sunday; 4 = Saturday.
    moods = [mood(days_ago=3 + 7 * i) for i in range(6)] + [mood(days_ago=4 + 7 * i) for i in range(6)]
    got = patterns.weekday_rhythm(moods)
    assert got["statement"] == "Weekends are when you make time for this."


def test_a_mixed_rhythm_says_nothing():
    moods = [mood(days_ago=7 * i) for i in range(6)] + [mood(days_ago=3 + 7 * i) for i in range(6)]
    assert patterns.weekday_rhythm(moods) is None


def test_the_weekend_line_does_not_scold():
    """A coach, not a manager — and the employer is paying, which is exactly why this must
    never read as a productivity note."""
    moods = [mood(days_ago=7 * i) for i in range(12)]
    got = patterns.weekday_rhythm(moods)
    for word in ("should", "must", "failed", "missed", "lazy"):
        assert word not in got["statement"].lower()


# ── derive: consent is an input, not a filter ────────────────────────────────


def test_nothing_to_say_is_a_valid_answer():
    got = patterns.derive([], [], [])
    assert got["patterns"] == [] and got["enough_data"] is False


def test_every_statement_carries_a_basis():
    """A statement without one is a horoscope. This is the rule the module exists for."""
    got = patterns.derive([mood(7 * i) for i in range(12)], [], [])
    assert got["patterns"]
    for p in got["patterns"]:
        assert p["basis"].strip(), p


def test_declining_moods_removes_every_mood_derived_statement():
    moods = [mood(7 * i) for i in range(12)]
    got = patterns.derive(moods, [], [], use_moods=False)
    assert got["patterns"] == []
    assert got["sources"]["mood_history"] is False


def test_declining_journal_does_not_silence_the_mood_rules():
    moods = [mood(7 * i) for i in range(12)]
    got = patterns.derive(moods, [entry(1)], [], use_journal=False)
    assert got["enough_data"] is True
    assert got["sources"]["journal_memory"] is False
    assert all("journal" not in p["statement"].lower() for p in got["patterns"])


def test_sources_reports_what_was_consulted():
    got = patterns.derive([], [], [], use_moods=True, use_journal=False, use_sleep=True)
    assert got["sources"] == {"mood_history": True, "journal_memory": False, "sleep_history": True}


def test_sleep_needs_moods_too():
    sleep = [_sleep(d, 480) for d in range(6)]
    got = patterns.derive([mood(d) for d in range(6)], [], sleep, use_moods=False)
    assert got["patterns"] == []


# ── for_user: the store seam ─────────────────────────────────────────────────


def test_for_user_returns_the_empty_answer_without_a_store():
    # No mongo fixture: reads return [] and the dashboard says "not yet", never raises.
    got = patterns.for_user("nobody")
    assert got["enough_data"] is False


def test_for_user_reads_only_the_consented_kinds(monkeypatch):
    asked: list[str] = []

    def fake_recent(user_id, kind, days):
        asked.append(kind)
        return []

    monkeypatch.setattr("app.stores.wellness.recent", fake_recent)
    patterns.for_user("u1", {"mood_history": True, "journal_memory": False, "sleep_history": False})
    assert asked == ["moods"], "a declined category was READ — consent must gate the read, not the output"


def test_for_user_treats_a_missing_consent_claim_as_permission():
    # Absence is not refusal — same rule as routers/wellness.py::_require_consent.
    got = patterns.for_user("u1", None)
    assert got["sources"] == {"mood_history": True, "journal_memory": True, "sleep_history": True}


def test_a_check_in_with_no_date_is_skipped_by_the_sleep_rule():
    # Line 168: an undateable mood cannot be paired with a night's sleep.
    sleep = [_sleep(d, 480) for d in (1, 2, 3)] + [_sleep(d, 300) for d in (4, 5, 6)]
    rested = [mood(d, word="calm", intensity=1) for d in (1, 2, 3)]
    short = [mood(d, word="anxious", intensity=4) for d in (4, 5, 6)]
    undateable = [{"ts": "junk", "mood": "calm", "intensity": 0}]
    got = patterns.sleep_shows_up(rested + short + undateable, sleep)
    assert got and got["basis"] == "3 rested vs 3 short-sleep mornings", "the junk row leaked into a count"


def test_a_junk_intensity_is_skipped_by_the_sleep_rule():
    # Lines 174-175: one corrupt intensity must not blank the rule or skew the mean.
    sleep = [_sleep(d, 480) for d in (1, 2, 3)] + [_sleep(d, 300) for d in (4, 5, 6, 7)]
    rested = [mood(d, word="calm", intensity=1) for d in (1, 2, 3)]
    short = [mood(d, word="anxious", intensity=4) for d in (4, 5, 6)]
    bad = [{"ts": (NOW - timedelta(days=7)).replace(hour=9).isoformat(), "mood": "x", "intensity": "very"}]
    got = patterns.sleep_shows_up(rested + short + bad, sleep)
    assert got and got["basis"] == "3 rested vs 3 short-sleep mornings"


# ── the HTTP surface ─────────────────────────────────────────────────────────


@pytest.fixture
def authed(monkeypatch):
    """Auth ON with a known secret. conftest sets AUTH_DEV_BYPASS for the whole suite,
    which leaves require_auth returning {} — so these routes 400 for want of a subject and
    a test would assert against the wrong failure. Same shape as test_wellness.py."""
    from app import config

    secret = "dGVzdC1zZWNyZXQtZm9yLXBhdHRlcm4tdGVzdHMtb2s="
    monkeypatch.setenv("AUTH_DEV_BYPASS", "")
    monkeypatch.setattr(config, "ENV", "production")
    monkeypatch.setattr(config, "JWT_SECRET", secret)
    return secret


def _client():
    from fastapi.testclient import TestClient

    from app.main import create_app

    return TestClient(create_app(), raise_server_exceptions=False)


def _hdr(secret: str, consent: dict | None = None) -> dict:
    import jwt

    from app import config

    claims = {"sub": "u1", "user": {"username": "u1"}, "org_id": "acme", "role": "user"}
    if consent is not None:
        claims["consent"] = consent
    return {"Authorization": f"Bearer {jwt.encode(claims, secret, algorithm=config.JWT_ALGORITHM)}"}


def test_the_dashboard_endpoint_answers_even_with_nothing_to_say(authed):
    # "Not yet" is a valid dashboard, not an error. Refusing the page because the person is
    # new would make the surface look broken at the exact moment it is being judged.
    r = _client().get("/v1/wellness/patterns", headers=_hdr(authed))
    assert r.status_code == 200
    body = r.json()
    assert body["enough_data"] is False
    assert body["patterns"] == []
    assert set(body["sources"]) == {"mood_history", "journal_memory", "sleep_history"}


def test_the_dashboard_is_not_403_for_someone_who_declined_everything():
    """Declining every category is a valid state that yields enough_data:false — refusing
    the whole page because one box is off would punish the choice."""
    from app.stores import patterns as mod

    got = mod.derive([], [], [], use_moods=False, use_journal=False, use_sleep=False)
    assert got["enough_data"] is False
    assert got["sources"] == {"mood_history": False, "journal_memory": False, "sleep_history": False}


def test_forgetting_requires_confirmation(authed):
    # Same rule as erasure: a DELETE that fires on a mistyped URL is a bad afternoon.
    r = _client().delete("/v1/privacy/me/memory", headers=_hdr(authed))
    assert r.status_code == 400
    assert "confirm" in r.json()["detail"].lower()


def test_forgetting_reports_what_it_kept(authed):
    r = _client().delete("/v1/privacy/me/memory?confirm=true", headers=_hdr(authed))
    assert r.status_code == 200
    body = r.json()
    assert body["verified"] is True
    # The load-bearing half of the promise: their own writing survives.
    assert "wellness" in body["kept"]
    assert "crisis_escalations" in body["kept"]


def test_the_memory_wipe_is_a_strict_subset_of_the_erasure():
    """One registry, two operations. A store added for erasure but forgotten here (or vice
    versa) is exactly the bug the single registry exists to prevent."""
    from app.privacy import erasure

    all_labels = {loc.label for loc in erasure._locations()}
    # `_memory_labels()`, not the raw constant: the checkpoint labels are backend-dependent
    # (the Mongo saver has checkpoints + checkpoint_writes; every other backend has one
    # `checkpoints` location its saver clears whole), so the constant states the intent and
    # the accessor intersects it with the registry that actually exists. This test caught
    # the orphan when the checkpointer path collapsed to one label.
    assert erasure._memory_labels() < all_labels, "memory must be a strict subset of erasure"
    assert erasure._memory_labels(), "the memory wipe targets nothing at all"


def test_the_journal_is_never_part_of_the_memory_wipe():
    """The whole point of the narrower promise: "start the coach fresh" must not burn the
    person's diary."""
    from app.privacy import erasure

    assert "wellness" not in erasure._MEMORY_LABELS


def test_a_convenience_button_cannot_erase_a_safety_record():
    """crisis_escalations records THAT someone was in crisis (never what they said). The
    statutory erase_user still removes it — that is a right. This is a preference."""
    from app.privacy import erasure

    assert "crisis_escalations" not in erasure._MEMORY_LABELS


def test_forget_refuses_an_empty_subject():
    from app.privacy import erasure

    assert erasure.forget_user("")["verified"] is False


def test_the_endpoint_honours_a_declined_category(authed):
    """End to end: the signed consent claim reaches the reads, so a declined category is
    never consulted — not merely filtered out of the answer."""
    r = _client().get(
        "/v1/wellness/patterns",
        headers=_hdr(authed, {"mood_history": False, "journal_memory": True, "sleep_history": False}),
    )
    assert r.status_code == 200
    assert r.json()["sources"] == {"mood_history": False, "journal_memory": True, "sleep_history": False}


def test_the_endpoint_requires_a_token(authed):
    assert _client().get("/v1/wellness/patterns").status_code == 401


def test_forgetting_requires_a_token(authed):
    assert _client().delete("/v1/privacy/me/memory?confirm=true").status_code == 401


def test_an_incomplete_wipe_is_a_500_not_a_cheerful_200(authed, monkeypatch):
    """The most important line in the route. If the re-scan finds anything left, the person
    must not be told the coach forgot — a partial wipe reported as success is the failure
    the whole verify step exists to prevent."""
    from app.privacy import erasure

    monkeypatch.setattr(
        erasure, "forget_user",
        lambda _uid: {"deleted": {}, "remaining": {"checkpoints": 3}, "kept": [], "verified": False},
    )
    r = _client().delete("/v1/privacy/me/memory?confirm=true", headers=_hdr(authed))
    assert r.status_code == 500
    body = r.json()
    assert body["verified"] is False
    assert body["remaining"] == {"checkpoints": 3}
    assert "INCOMPLETE" in body["detail"]
