"""Self-reported wellness content: the journal, sleep logs, and mood check-ins.

One document per `user_id` in `cerebrozen.users_wellness`:

    {
      user_id, org_id,
      journal: [ {id, ts, title, body, tags, symbol}, ... ],   # oldest first, capped
      sleep:   [ {id, ts, date, bedtime, wake_time, quality, awakenings,
                  duration_min}, ... ],
      moods:   [ {id, ts, mood, note, symbol, intensity}, ... ],
      updated_at,
    }

The field names are the CLIENT's, not ours: `duration_min`, `wake_time`, `awakenings`
are what apps/android parses (net/Session.kt, ui/screens/SleepScreen.kt), and the read
endpoints hand them back unchanged. A store that invents its own vocabulary here just
moves the translation somewhere less visible.

## Why this is in the engine and not the platform

The platform is the org database — what an HR admin's token reaches — and its schema is
the "counts, never content" firewall (services/platform/app/models.py). A journal row in
that database is a journal a future admin query, join, or support tool can read. Keeping
the content in the engine, behind a token that names the person themselves, makes the
firewall structural rather than a promise about endpoints we remember not to write.

## Self-report is not inference

`agentic.save_mood_capture` refuses to write when emotion capture is off, because that is
the AGENT reading emotions off a worker (config.py, "REGULATED-WORKPLACE MODE"). Nothing
here infers anything: every field arrives because the person typed it or moved a slider.
A regulated tenant keeps their own diary. `SELF_REPORT_WELLNESS_ENABLED` exists for the
tenant who wants none of it on the vendor's disks, and the store is the last gate before
the disk — the same place the emotion guarantee already lives.

Writes are best-effort and never raise: a store hiccup must not break a screen.
"""

from __future__ import annotations

import hashlib
import logging
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from app import config
from app.stores import mongo as _mongo_seam  # late-bound seam (see agentic.py)
from app.tenancy import current_org, scoped

logger = logging.getLogger("cerebrozen.wellness")

# Every list is capped. An unbounded $push is a document that grows until the day it
# exceeds the 16MB limit and every write for that user starts failing — years in, with no
# warning. The cap is generous (a daily entry for ~5 years) and the oldest falls off the
# front, which is what a person expects of a rolling log.
_MAX_ENTRIES = 2000

KINDS = ("journal", "sleep", "moods")


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _entry_id(*parts: Any) -> str:
    """Stable 12-char id — the client keys list rows on it and deletes by it."""
    return hashlib.sha256("|".join(str(p) for p in parts).encode("utf-8")).hexdigest()[:12]


def _collection():
    client = _mongo_seam.get_client()
    if client is None:
        return None
    return client[config.MONGO_BACKEND_DB][config.MONGO_WELLNESS_COLLECTION]


def clock_minutes(value: str) -> Optional[int]:
    """"HH:MM" / "HH:MM:SS" → minutes past midnight. None when unparseable.

    Mirrors the client's own parseClockMinutes (SleepScreen.kt) so a night the phone
    can draw is a night we can measure, and vice versa.
    """
    parts = str(value or "").strip().split(":")
    if len(parts) < 2:
        return None
    try:
        hours, minutes = int(parts[0]), int(parts[1])
    except ValueError:
        return None
    if not (0 <= hours < 24 and 0 <= minutes < 60):
        return None
    return hours * 60 + minutes


def duration_minutes(bedtime: str, wake_time: str) -> int:
    """Minutes asleep, handling the ordinary case of going to bed before midnight.

    23:30 → 07:00 is seven and a half hours, not minus sixteen. Sleep crosses midnight;
    a naive subtraction is negative for most people most nights.
    """
    start, end = clock_minutes(bedtime), clock_minutes(wake_time)
    if start is None or end is None:
        return 0
    span = end - start
    if span <= 0:
        span += 24 * 60
    return span


def _append(user_id: str, kind: str, entry: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """Push one entry onto a user's list. Returns the stored entry, or None if refused."""
    if not config.SELF_REPORT_WELLNESS_ENABLED:
        logger.info(
            "wellness.write_disabled",
            extra={"kind": kind, "reason": "self-reported wellness storage is off for this tenant"},
        )
        return None
    coll = _collection()
    if coll is None or not user_id or kind not in KINDS:
        return None
    try:
        coll.update_one(
            scoped({"user_id": user_id}),
            {
                "$set": {"user_id": user_id, "org_id": current_org(), "updated_at": _now()},
                # A NEGATIVE $slice keeps the newest N: it trims from the front, so the
                # oldest entry is the one that falls off.
                "$push": {kind: {"$each": [entry], "$slice": -_MAX_ENTRIES}},
            },
            upsert=True,
        )
        logger.info("wellness.saved", extra={"kind": kind, "user_id": user_id})
        return entry
    except Exception as exc:  # noqa: BLE001 — a screen must not break on a store hiccup
        logger.warning(
            "wellness.save_failed", extra={"kind": kind, "user_id": user_id, "error": str(exc)}
        )
        return None


def _read(user_id: str, kind: str) -> List[Dict[str, Any]]:
    coll = _collection()
    if coll is None or not user_id or kind not in KINDS:
        return []
    try:
        doc = coll.find_one(scoped({"user_id": user_id})) or {}
    except Exception as exc:  # noqa: BLE001
        logger.warning("wellness.read_failed", extra={"kind": kind, "error": str(exc)})
        return []
    return [e for e in (doc.get(kind) or []) if isinstance(e, dict)]


def _newest_first(entries: List[Dict[str, Any]], limit: int) -> List[Dict[str, Any]]:
    """The order every list screen renders — and the order the sleep chart assumes."""
    return list(reversed(entries))[: max(1, min(limit, 200))]


# ── journal ──────────────────────────────────────────────────────────────────


def add_journal(
    user_id: str, body: str, title: str = "", tags: Optional[List[str]] = None, symbol: str = ""
) -> Optional[Dict[str, Any]]:
    body = (body or "").strip()
    if not body:
        return None
    ts = _now()
    return _append(user_id, "journal", {
        "id": _entry_id(user_id, ts, body[:80]),
        "ts": ts,
        "title": (title or "").strip()[:200],
        "body": body,
        "tags": [str(t)[:40] for t in (tags or [])][:20],
        "symbol": (symbol or "").strip()[:40],
    })


def list_journal(user_id: str, limit: int = 50) -> List[Dict[str, Any]]:
    return _newest_first(_read(user_id, "journal"), limit)


# ── sleep ────────────────────────────────────────────────────────────────────


def add_sleep(
    user_id: str,
    date: str = "",
    bedtime: str = "",
    wake_time: str = "",
    quality: int = 0,
    awakenings: int = 0,
) -> Optional[Dict[str, Any]]:
    minutes = duration_minutes(bedtime, wake_time)
    if minutes <= 0:
        return None  # an unparseable night is not a night
    ts = _now()
    return _append(user_id, "sleep", {
        "id": _entry_id(user_id, ts, date),
        "ts": ts,
        # `date` is REQUIRED by the client's parser (a missing one throws and kills the
        # whole list), so it is never absent here — today's date if the caller sent none.
        "date": (date or datetime.now(timezone.utc).strftime("%Y-%m-%d")).strip(),
        "bedtime": str(bedtime).strip(),
        "wake_time": str(wake_time).strip(),
        "quality": max(0, min(int(quality or 0), 5)),
        "awakenings": max(0, min(int(awakenings or 0), 50)),
        "duration_min": minutes,
    })


def list_sleep(user_id: str, limit: int = 30) -> List[Dict[str, Any]]:
    return _newest_first(_read(user_id, "sleep"), limit)


def _avg(values: List[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def sleep_summary(user_id: str, days: int = 7) -> Dict[str, Any]:
    """The "Your week" card: averages and a direction, over the person's own nights.

    Deliberately not a score. A number rating how well a worker sleeps is a judgement
    about them, and this product does not make those (config.py, person-scoring). It is
    their data, handed back.

    `enough_data` is the client's gate: with one or two nights an "average" and a "trend"
    are noise dressed as insight, and the card says so by not appearing.
    """
    logs = list_sleep(user_id, limit=max(1, min(days, 90)))
    durations = [float(e.get("duration_min") or 0) for e in logs if e.get("duration_min")]
    qualities = [float(e.get("quality") or 0) for e in logs if e.get("quality")]

    trend = "steady"
    if len(durations) >= 4:
        # Newest-first: the recent half vs the older half. A 30-minute dead-band, because
        # a 6-minute difference is not a trend and telling someone it is, is a lie.
        half = len(durations) // 2
        recent, older = _avg(durations[:half]), _avg(durations[half:])
        if recent - older > 30:
            trend = "improving"
        elif older - recent > 30:
            trend = "dipping"

    return {
        "enough_data": len(logs) >= 3,
        "avg_duration_min": int(round(_avg(durations))),
        "avg_quality": round(_avg(qualities), 1),
        "trend": trend,
        "nights": len(logs),
    }


# ── mood check-in (SELF-REPORTED — see the module docstring) ─────────────────


def add_mood(
    user_id: str, mood: str, note: str = "", symbol: str = "", intensity: int = 0
) -> Optional[Dict[str, Any]]:
    mood = (mood or "").strip()
    if not mood:
        return None
    ts = _now()
    return _append(user_id, "moods", {
        "id": _entry_id(user_id, ts, mood),
        "ts": ts,
        "mood": mood[:40],
        "note": (note or "").strip()[:500],
        "symbol": (symbol or "").strip()[:40],
        "intensity": max(0, min(int(intensity or 0), 5)),
    })


def list_moods(user_id: str, limit: int = 30) -> List[Dict[str, Any]]:
    return _newest_first(_read(user_id, "moods"), limit)


# ── delete one entry ─────────────────────────────────────────────────────────


def delete_entry(user_id: str, kind: str, entry_id: str) -> bool:
    """Remove a single entry the person deleted in the app.

    Read-modify-write with `$set`, not `$pull`: the Postgres shim implements only the
    operators the stores actually use, and `$pull` is not among them (stores/pg.py). A
    `$pull` here would work on Mongo and silently no-op on Postgres — the exact class of
    bug that made a dropped write look like a successful one.
    """
    coll = _collection()
    if coll is None or not user_id or kind not in KINDS or not entry_id:
        return False
    entries = _read(user_id, kind)
    remaining = [e for e in entries if e.get("id") != entry_id]
    if len(remaining) == len(entries):
        return False
    try:
        coll.update_one(
            scoped({"user_id": user_id}),
            {"$set": {kind: remaining, "updated_at": _now()}},
        )
        return True
    except Exception as exc:  # noqa: BLE001
        logger.warning("wellness.delete_failed", extra={"kind": kind, "error": str(exc)})
        return False


# ── the person's own week ────────────────────────────────────────────────────


def recent(user_id: str, kind: str, days: int) -> List[Dict[str, Any]]:
    """One kind of the person's own entries, within `days`. The public read other
    engine modules use, so nobody has to reach for the privates below (stores/patterns.py
    is the first caller). Returns [] for an unknown kind or an unreachable store."""
    return _within(_read(user_id, kind), days)


def _within(entries: List[Dict[str, Any]], days: int) -> List[Dict[str, Any]]:
    cutoff = datetime.now(timezone.utc).timestamp() - days * 86400
    fresh = []
    for entry in entries:
        try:
            ts = datetime.fromisoformat(str(entry.get("ts", ""))).timestamp()
        except ValueError:
            continue
        if ts >= cutoff:
            fresh.append(entry)
    return fresh


def weekly_insights(user_id: str, days: int = 7) -> Dict[str, Any]:
    """What the person did this week, counted from their own records.

    Shown to THEM and nobody else. There is no org aggregate of any of this and no
    endpoint that could build one: HR analytics is a different service reading a different
    database (services/platform), and it cannot see this collection.

    The metric shape (`label`/`value`/`progress`) is the client's — Extras.kt renders
    `value` as a STRING and `progress` as a 0..1 bar fill.
    """
    days = max(1, min(days, 90))
    journal = _within(_read(user_id, "journal"), days)
    sleep = _within(_read(user_id, "sleep"), days)
    moods = _within(_read(user_id, "moods"), days)
    durations = [float(e.get("duration_min") or 0) for e in sleep if e.get("duration_min")]
    avg_min = int(round(_avg(durations)))

    logged_anything = bool(journal or sleep or moods)
    metrics = [
        {
            "label": "Journal entries",
            "value": str(len(journal)),
            # A "target" of one entry a day is a gentle horizon, not a quota — the bar
            # fills, it never scolds, and nobody but the person sees it.
            "progress": min(1.0, len(journal) / days),
        },
        {
            "label": "Nights logged",
            "value": str(len(sleep)),
            "progress": min(1.0, len(sleep) / days),
        },
        {
            "label": "Average sleep",
            "value": f"{avg_min // 60}h {avg_min % 60:02d}m" if avg_min else "—",
            # Against 8h. Not a grade: a full bar is a full night, and an empty one means
            # we have nothing to show, not that the person failed at sleeping.
            "progress": min(1.0, avg_min / 480) if avg_min else 0.0,
        },
        {
            "label": "Check-ins",
            "value": str(len(moods)),
            "progress": min(1.0, len(moods) / days),
        },
    ]

    return {
        "headline": "Your week",
        "summary": (
            "Here's what you noted this week — yours alone, and nobody else's to read."
            if logged_anything
            else "Nothing noted yet this week. Whenever you're ready."
        ),
        "window_days": days,
        "metrics": metrics,
    }
