"""The sleep-date plausibility bound has to hold in every timezone.

`SleepLogCreate._plausible_night` is a pydantic validator, so it has no user and
cannot ask `core/localtime` whose day this is — all it has is the container's UTC
date. Local dates run from UTC-12 to UTC+14, so a member's "today" can sit a full
day either side of UTC's.

It survived the C59-C65 sweep that moved every other "what day is it" question
onto the user's clock, and the seam showed twice: a real Asia/Kolkata member had
their tomorrow rejected for the 5.5 hours a day when IST is already on the next
date, and `test_input_bounds::test_sleep_rejects_implausible_dates` failed in that
same window because the suite asks the *account* what day it is (`tests/dates.py`)
while this validator asked the container.

These tests are written against UTC offsets rather than wall-clock time, so they
assert the same thing at 03:00 and at 23:00 — which the test they backstop could
not.
"""
from datetime import date, datetime, timedelta
from datetime import timezone as dt_timezone

import pytest
from pydantic import ValidationError

from app.schemas.content_data import SleepLogCreate


def _utc_today() -> date:
    return datetime.now(dt_timezone.utc).date()


def _night(day: date) -> SleepLogCreate:
    return SleepLogCreate(date=day, bedtime="23:00:00", wake_time="07:00:00")


def test_a_member_a_day_ahead_of_utc_can_log_their_tomorrow():
    """UTC+14 exists, and its tomorrow is UTC's day-after-tomorrow.

    This is the case that was rejected in production for anyone east of UTC.
    """
    _night(_utc_today() + timedelta(days=2))


def test_a_member_a_day_behind_utc_can_still_backfill_two_years():
    """The mirror image: UTC-12's oldest allowed night is a day past UTC's."""
    _night(_utc_today() - timedelta(days=731))


def test_the_check_still_rejects_a_clearly_impossible_future():
    """Widening the bound must not amount to deleting it (register C26)."""
    with pytest.raises(ValidationError, match="future"):
        _night(_utc_today() + timedelta(days=3))
    with pytest.raises(ValidationError, match="future"):
        _night(date(2099, 1, 1))


def test_the_check_still_rejects_a_clearly_impossible_past():
    with pytest.raises(ValidationError, match="too far in the past"):
        _night(_utc_today() - timedelta(days=732))
    with pytest.raises(ValidationError, match="too far in the past"):
        _night(date(1970, 1, 1))


def test_todays_night_is_always_fine_whatever_the_hour():
    """The overwhelmingly common case, pinned so no future widening breaks it."""
    _night(_utc_today())
