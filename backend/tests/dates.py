"""What day is it *for the account under test*.

`date.today()` is the container's day — UTC in compose and in CI. Every endpoint
that has an opinion about "today" (streak, habit dots, sleep windows, trend
windows, intervention signals, programme day) reads the user's own calendar via
`app.core.localtime`, and the default account timezone is `Asia/Kolkata`. The
two are different days for five and a half hours out of every twenty-four.

Tests that seeded fixtures from `date.today()` and asserted against those
endpoints therefore passed all morning and failed all evening. Three of them
did exactly that until 2026-08-13, and they had been that way since the
localtime fix landed — the app was corrected and the tests were not.

Use these helpers instead. For a test that holds a `User` row with a timezone of
its own, prefer `local_today(user.timezone)` directly; this module is for the
common case where the account was created with the defaults.
"""
from __future__ import annotations

from datetime import date, timedelta

from app.core.localtime import local_today

#: The `User.timezone` column default. Pinned against the model by
#: `test_local_days.test_the_account_timezone_default_is_what_the_helpers_assume`,
#: so changing the default cannot quietly rot every fixture in the suite.
ACCOUNT_TZ = "Asia/Kolkata"


def account_today() -> date:
    """Today, as the account under test experiences it."""
    return local_today(ACCOUNT_TZ)


def account_day(days_ago: int) -> date:
    return account_today() - timedelta(days=days_ago)


def account_iso(days_ago: int = 0) -> str:
    return account_day(days_ago).isoformat()
