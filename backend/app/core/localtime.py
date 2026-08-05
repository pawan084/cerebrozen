"""The user's own clock (register C59-C65).

"Today" was three different things across the codebase: ``utcnow().date()``
(habits, streak), ``date.today()`` (the CONTAINER's zone — sleep summary,
intervention signals), and the user's timezone (nudge scheduling, pattern
hour-buckets). For anyone east of UTC that shifted habit ticks, day-dots and
weekly windows by a day. Every "what day is it for this user" question now
goes through here.

``UserUpdate`` validates timezones against IANA on write (wave 17), but
legacy rows may hold junk — ``tz_for`` falls back to UTC rather than raising,
matching every existing consumer's behavior.
"""
from __future__ import annotations

from datetime import date, datetime, timezone, tzinfo
from zoneinfo import ZoneInfo


def tz_for(name: str | None) -> tzinfo:
    try:
        return ZoneInfo(name or "UTC")
    except Exception:
        return timezone.utc


def local_now(tz_name: str | None) -> datetime:
    return datetime.now(timezone.utc).astimezone(tz_for(tz_name))


def local_today(tz_name: str | None) -> date:
    return local_now(tz_name).date()


def local_date(dt: datetime, tz_name: str | None) -> date:
    """The calendar day a UTC timestamp fell on for this user."""
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(tz_for(tz_name)).date()
