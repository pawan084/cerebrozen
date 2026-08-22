"""The other side of retention (WC-16), and why it is not called churn.

`quiet_users` counts people who were using CereBro and have stopped. On a
subscription tool that number is churn and it is bad by definition. On a
mental-health companion some of those people **got better**, which is the
outcome the product exists for — so the metric is named for the behaviour, ships
its own caveat, and refuses to report at all on a cohort too small to mean
anything.

Pure function over the activity map retention already builds, so these are
hermetic: no database, no new collection, nothing per-person.
"""

from __future__ import annotations

import uuid
from datetime import date, timedelta

from app.services.metrics import _MIN_QUIET_COHORT, quiet_users

TODAY = date(2026, 8, 22)


def _people(**groups: int) -> dict[uuid.UUID, set[date]]:
    """Build an activity map from plain descriptions.

    `quiet=n`   — active 20 days ago, nothing since.
    `active=n`  — active 20 days ago AND yesterday.
    `new=n`     — only active yesterday (arrived during the recent window).
    `ancient=n` — active 100 days ago and never again.
    """
    out: dict[uuid.UUID, set[date]] = {}
    recipes = {
        "quiet": {TODAY - timedelta(days=20)},
        "active": {TODAY - timedelta(days=20), TODAY - timedelta(days=1)},
        "new": {TODAY - timedelta(days=1)},
        "ancient": {TODAY - timedelta(days=100)},
    }
    for name, count in groups.items():
        for _ in range(count):
            out[uuid.uuid4()] = set(recipes[name])
    return out


class TestWhoCounts:
    def test_someone_who_was_here_and_stopped_is_quiet(self):
        result = quiet_users(_people(quiet=_MIN_QUIET_COHORT), TODAY)
        assert result["cohort"] == _MIN_QUIET_COHORT
        assert result["quiet"] == _MIN_QUIET_COHORT
        assert result["rate"] == 1.0

    def test_someone_still_here_is_in_the_cohort_but_not_quiet(self):
        result = quiet_users(_people(active=_MIN_QUIET_COHORT), TODAY)
        assert result["cohort"] == _MIN_QUIET_COHORT
        assert result["quiet"] == 0
        assert result["rate"] == 0.0

    def test_a_newcomer_is_not_counted_as_retained_or_lost(self):
        # They were not here in the earlier stretch, so they cannot have gone
        # quiet — and counting them would hide an ACTIVATION problem inside a
        # retention number.
        result = quiet_users(_people(active=_MIN_QUIET_COHORT, new=50), TODAY)
        assert result["cohort"] == _MIN_QUIET_COHORT

    def test_someone_from_before_the_window_is_not_resurrected_to_be_lost_again(self):
        # Already gone long before the window opened. Counting them would make
        # the rate worse every month for as long as the data is kept.
        result = quiet_users(_people(quiet=_MIN_QUIET_COHORT, ancient=40), TODAY)
        assert result["cohort"] == _MIN_QUIET_COHORT

    def test_the_rate_is_the_share_of_the_cohort_that_stopped(self):
        result = quiet_users(_people(quiet=10, active=30), TODAY)
        assert result["cohort"] == 40
        assert result["quiet"] == 10
        assert result["rate"] == 0.25


class TestItRefusesToReportNoise:
    def test_a_small_cohort_gets_no_rate_and_says_why(self):
        # A number computed from four people is not a smaller truth; it is a
        # different kind of statement. Same instinct as the trends correlation
        # withholding itself under seven nights.
        result = quiet_users(_people(quiet=2, active=2), TODAY)
        assert result["cohort"] == 4
        assert result["quiet"] is None
        assert result["rate"] is None
        assert result["reason"] == "not_enough_people"

    def test_it_reports_the_moment_the_cohort_is_big_enough(self):
        result = quiet_users(_people(quiet=1, active=_MIN_QUIET_COHORT - 1), TODAY)
        assert result["cohort"] == _MIN_QUIET_COHORT
        assert result["reason"] is None
        assert result["rate"] is not None

    def test_no_activity_at_all_is_not_a_crash(self):
        result = quiet_users({}, TODAY)
        assert result["cohort"] == 0
        assert result["rate"] is None


class TestTheCaveatTravelsWithTheNumber:
    def test_every_answer_carries_what_it_does_not_mean(self):
        # Including the withheld one — a reader who sees `null` still needs to
        # know what the field would have meant.
        for activity in (_people(quiet=_MIN_QUIET_COHORT), _people(quiet=1), {}):
            result = quiet_users(activity, TODAY)
            assert "not the same as churn" in result["means"]

    def test_the_field_is_not_called_churn(self):
        # The name is the design. "Churn" makes recovery look like loss, and a
        # team optimising against it builds the nagging this codebase refuses.
        result = quiet_users(_people(quiet=_MIN_QUIET_COHORT), TODAY)
        assert "churn" not in result
        assert set(result) == {"cohort", "quiet", "rate", "reason", "means"}


class TestWindowBoundaries:
    def test_activity_inside_the_recent_window_keeps_someone_active(self):
        activity = {uuid.uuid4(): {TODAY - timedelta(days=20), TODAY - timedelta(days=13)}}
        activity.update(_people(active=_MIN_QUIET_COHORT))
        result = quiet_users(activity, TODAY)
        assert result["quiet"] == 0

    def test_activity_only_just_outside_it_makes_them_quiet(self):
        activity = {uuid.uuid4(): {TODAY - timedelta(days=20), TODAY - timedelta(days=14)}}
        activity.update(_people(active=_MIN_QUIET_COHORT))
        result = quiet_users(activity, TODAY)
        assert result["quiet"] == 1

    def test_the_windows_are_adjustable_without_touching_the_rules(self):
        activity = _people(quiet=_MIN_QUIET_COHORT)
        wide = quiet_users(activity, TODAY, recent=30, prior=60)
        # With a 30-day recent window, activity 20 days ago is RECENT, so
        # nobody is in the earlier stretch and there is no cohort at all.
        assert wide["cohort"] == 0
