"""Transparent AI memory: what the coach has learned about you, and why it thinks so.

Every statement here is derived from the person's OWN records (their check-ins, their
journal, their sleep log) and ships with the `basis` that produced it — the counts, not a
vibe. Shown to them and nobody else: there is no org aggregate of any of this and no
endpoint that could build one (see `stores/wellness.py` for why that is structural).

## Why the basis is not decoration

"Mornings tend to be your hardest time of day" is a claim a product makes about a person.
Unattributed, it is indistinguishable from a horoscope, and an employee has no way to judge
whether the coach actually knows something or is pattern-matching noise. `basis` — "6 of
your 11 difficult check-ins landed there" — is what makes the claim auditable by the only
person entitled to audit it. A statement without one must not be added to this module.

## Why the thresholds are conservative

Every rule below needs a minimum sample AND a margin before it will speak. A confident
sentence built on four check-ins is worse than silence: it teaches someone to distrust the
whole surface, and this surface is the one asking them to trust us with a diary. When in
doubt, say nothing — `enough_data: false` is a good answer.

## Consent is an input, not a filter applied afterwards

A category the person declined is never READ, so it cannot reach a statement. `sources`
reports which categories were consulted, so the UI can say "this is what I looked at" —
the honest complement to letting them delete it.

Pure functions over entry lists: the statistics are the interesting part and they are
testable without a database.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Any, Callable, Dict, Iterable, List, Optional

logger = logging.getLogger("cerebrozen.patterns")

#: 60 days — long enough for a weekday/weekend rhythm to exist, short enough that it
#: describes who someone is now rather than who they were last quarter.
WINDOW_DAYS = 60

#: What counts as a difficult check-in. Intensity is the client's 0..5 scale; 4+ is the
#: top third. Mood words are matched case-insensitively against what the client sends.
_NEG_MOODS = frozenset({
    "anxious", "angry", "sad", "stressed", "low", "overwhelmed", "frustrated",
    "tired", "lonely", "afraid", "hopeless", "numb",
})
_NEG_INTENSITY = 4

#: "7+ hours in bed" — the line the weekly insight already uses. Kept identical on
#: purpose: two surfaces disagreeing about what "rested" means is a bug the user sees.
RESTED_MIN = 420


def _ts(entry: Dict[str, Any]) -> Optional[datetime]:
    """The entry's timestamp, or None if it is unparseable. Never raises: one corrupt row
    must not blank the whole dashboard."""
    try:
        parsed = datetime.fromisoformat(str(entry.get("ts", "")))
    except (TypeError, ValueError):
        return None
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


def is_difficult(entry: Dict[str, Any]) -> bool:
    """A check-in the person marked as hard — by word or by intensity."""
    if str(entry.get("mood", "")).strip().lower() in _NEG_MOODS:
        return True
    try:
        return int(entry.get("intensity") or 0) >= _NEG_INTENSITY
    except (TypeError, ValueError):
        return False


def _bucket(hour: int) -> str:
    return "Mornings" if hour < 12 else "Afternoons" if hour < 18 else "Evenings"


def hardest_time_of_day(moods: List[Dict[str, Any]]) -> Optional[Dict[str, str]]:
    """Rule 1. Which third of the day the difficult check-ins cluster in.

    Needs 6 difficult check-ins and a majority in one bucket: without the majority test
    "Mornings" wins a three-way split at 34% and says nothing true.
    """
    hard = [m for m in moods if is_difficult(m)]
    if len(hard) < 6:
        return None
    buckets = {"Mornings": 0, "Afternoons": 0, "Evenings": 0}
    for m in hard:
        stamp = _ts(m)
        if stamp is None:
            continue
        buckets[_bucket(stamp.hour)] += 1
    placed = sum(buckets.values())
    if placed < 6:
        return None
    top, count = max(buckets.items(), key=lambda kv: kv[1])
    if count / placed < 0.5:
        return None
    return {
        "statement": f"{top} tend to be your hardest time of day.",
        "basis": f"{count} of your {placed} difficult check-ins landed there",
    }


def _day_difficult_share(moods: List[Dict[str, Any]], keep: Callable[[Any], bool]) -> Optional[float]:
    """Mean per-DAY share of difficult check-ins, over days matching `keep`.

    Per-day rather than per-check-in on purpose: one bad Tuesday with nine check-ins
    should not outvote nine ordinary days with one each.
    """
    days: Dict[Any, List[int]] = {}
    for m in moods:
        stamp = _ts(m)
        if stamp is None or not keep(stamp.date()):
            continue
        row = days.setdefault(stamp.date(), [0, 0])
        row[0] += 1
        if is_difficult(m):
            row[1] += 1
    if len(days) < 3:
        return None
    return sum(bad / total for total, bad in days.values()) / len(days)


def journaling_helps(moods: List[Dict[str, Any]], journal: List[Dict[str, Any]]) -> Optional[Dict[str, str]]:
    """Rule 2. Do check-ins run calmer the day AFTER a journal entry?

    Correlation, and the statement says "run calmer", not "journaling makes you calmer" —
    we cannot know direction and must not imply it.
    """
    journal_days = {stamp.date() for stamp in map(_ts, journal) if stamp is not None}
    if len(journal_days) < 3:
        return None
    after = _day_difficult_share(moods, lambda d: (d - timedelta(days=1)) in journal_days)
    other = _day_difficult_share(moods, lambda d: (d - timedelta(days=1)) not in journal_days)
    if after is None or other is None or other - after < 0.2:
        return None
    return {
        "statement": "Check-ins run calmer the day after you journal.",
        "basis": f"across {len(journal_days)} journaling days in the last {WINDOW_DAYS}",
    }


def sleep_shows_up(moods: List[Dict[str, Any]], sleep: List[Dict[str, Any]]) -> Optional[Dict[str, str]]:
    """Rule 3. Do mornings after 7+ hours read calmer than after short nights?"""
    by_date: Dict[Any, float] = {}
    for row in sleep:
        stamp = _ts(row)
        minutes = row.get("duration_min")
        if stamp is None or not minutes:
            continue
        try:
            by_date[stamp.date()] = float(minutes)
        except (TypeError, ValueError):
            continue
    if len(by_date) < 5:
        return None

    def intensities(pick: Callable[[float], bool]) -> List[float]:
        out = []
        for m in moods:
            stamp = _ts(m)
            if stamp is None:
                continue
            minutes = by_date.get(stamp.date())
            if minutes is None or not pick(minutes):
                continue
            try:
                out.append(float(m.get("intensity") or 0))
            except (TypeError, ValueError):
                continue
        return out

    rested = intensities(lambda mins: mins >= RESTED_MIN)
    short = intensities(lambda mins: 0 < mins < RESTED_MIN)
    if len(rested) < 3 or len(short) < 3:
        return None
    gap = sum(short) / len(short) - sum(rested) / len(rested)
    if gap < 0.5:
        return None
    return {
        "statement": "Mornings after 7+ hours in bed read calmer in your check-ins.",
        "basis": f"{len(rested)} rested vs {len(short)} short-sleep mornings",
    }


def weekday_rhythm(moods: List[Dict[str, Any]]) -> Optional[Dict[str, str]]:
    """Rule 4. Where the showing-up actually happens.

    Both directions are stated neutrally: "weekends drift" is an observation, not a
    scolding. This is a coach, not a manager, and the person's employer is paying —
    which is exactly why it must never read as a productivity note.
    """
    stamped = [s for s in map(_ts, moods) if s is not None]
    if len(stamped) < 10:
        return None
    weekday = sum(1 for s in stamped if s.weekday() < 5)
    share = weekday / len(stamped)
    if share >= 0.8:
        return {
            "statement": "You show up most on weekdays — weekends drift.",
            "basis": f"{weekday} of {len(stamped)} check-ins were Mon–Fri",
        }
    if share <= 0.35:
        return {
            "statement": "Weekends are when you make time for this.",
            "basis": f"{len(stamped) - weekday} of {len(stamped)} check-ins were Sat–Sun",
        }
    return None


def derive(
    moods: List[Dict[str, Any]],
    journal: List[Dict[str, Any]],
    sleep: List[Dict[str, Any]],
    *,
    use_moods: bool = True,
    use_journal: bool = True,
    use_sleep: bool = True,
) -> Dict[str, Any]:
    """Every statement the data supports, with its basis. Pure — no store, no clock.

    A declined category is never read, so it cannot reach a statement.
    """
    moods = moods if use_moods else []
    journal = journal if use_journal else []
    sleep = sleep if use_sleep else []

    candidates: Iterable[Optional[Dict[str, str]]] = (
        hardest_time_of_day(moods),
        journaling_helps(moods, journal) if use_journal else None,
        sleep_shows_up(moods, sleep) if (use_sleep and use_moods) else None,
        weekday_rhythm(moods),
    )
    found = [c for c in candidates if c]
    return {
        "patterns": found,
        "enough_data": bool(found),
        "sources": {
            "mood_history": use_moods,
            "journal_memory": use_journal,
            "sleep_history": use_sleep,
        },
    }


def for_user(user_id: str, consent: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """The dashboard for one person, read from their own wellness document."""
    from app.stores import wellness

    def allow(key: str) -> bool:
        # Absence is not refusal — same rule as routers/wellness.py::_require_consent.
        return not (isinstance(consent, dict) and consent.get(key) is False)

    use_moods, use_journal, use_sleep = (
        allow("mood_history"), allow("journal_memory"), allow("sleep_history"),
    )
    return derive(
        wellness.recent(user_id, "moods", WINDOW_DAYS) if use_moods else [],
        wellness.recent(user_id, "journal", WINDOW_DAYS) if use_journal else [],
        wellness.recent(user_id, "sleep", WINDOW_DAYS) if use_sleep else [],
        use_moods=use_moods,
        use_journal=use_journal,
        use_sleep=use_sleep,
    )
