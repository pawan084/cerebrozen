"""Right to erasure, and right of access. The two things every vendor promises and few build.

## Why this is harder than it looks, and why it is usually done wrong

A person's data in this product is spread across **six locations in three separate
databases**. A `delete_user()` that clears the obvious one looks complete, passes review, and
leaves the transcript sitting in another database:

    backend DB      users_agentic_conversation_context   actions, insights, moods, intake, patterns
    backend DB      dynamic_vars                         captured session variables
    backend DB      crisis_escalations                   a record that they were in crisis
    backend DB      users_wellness                       THEIR JOURNAL, sleep log, check-ins
    rasa DB         user_conversations                   THE TRANSCRIPT
    checkpoint DB   checkpoints                          THE ENTIRE CONVERSATION STATE
    checkpoint DB   checkpoint_writes                    ...and its write-ahead log
    redis           profile cache                        a copy of their profile

**The checkpointer is the one that gets forgotten.** LangGraph persists the whole graph state
— including the message history — keyed by `thread_id`, in a database nobody thinks of as "the
user database" because no store module writes to it. Delete everything else and the
conversation is still there, in full.

So this module works from an **explicit registry** of locations (`_LOCATIONS`), not from
whatever the author remembered on the day. A store added tomorrow that holds user data and is
not registered here is a latent breach — and `test_privacy.py` scans `app/stores/` and fails
the build if one appears.

## Erasure has to be VERIFIABLE, not merely attempted

`erase()` returns a report of what it deleted, per location, and then **re-scans and reports
what is left**. If anything remains, `verified` is False and the caller must not tell the
person their data is gone. "We ran the delete" and "the data is gone" are different claims,
and only one of them is the promise.

This is not paranoia. The Postgres shim's `delete_many` used to count filter *matches* rather
than rows actually removed — it reported deletions it never made. An erasure built on that
would have returned success to a regulator with the data still on disk.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Dict, List

logger = logging.getLogger("cerebrozen.privacy")


@dataclass(frozen=True)
class Location:
    """One place a person's data lives. `key` is the field that identifies them there."""
    label: str
    db: str            # a config attribute name, resolved late so tests can patch config
    collection: str    # ditto, or a literal name
    key: str           # "user_id" | "thread_id"
    note: str = ""


def _locations() -> List[Location]:
    """Resolved late, so a tenant's DB names (and a test's) are honoured."""
    from app import config

    return [
        Location("agentic_context", config.MONGO_BACKEND_DB, config.MONGO_AGENTIC_COLLECTION,
                 "user_id", "actions, insights, moods, intake, patterns"),
        Location("dynamic_vars", config.MONGO_BACKEND_DB, config.MONGO_DYNAMIC_VARS_COLLECTION,
                 "user_id", "captured session variables"),
        Location("crisis_escalations", config.MONGO_BACKEND_DB, "crisis_escalations",
                 "user_id", "the record that they were once in crisis"),
        Location("wellness", config.MONGO_BACKEND_DB, config.MONGO_WELLNESS_COLLECTION,
                 "user_id", "their journal, sleep log, and mood check-ins"),
        Location("transcripts", config.MONGO_RASA_DB, config.MONGO_USER_CONVERSATIONS_COLLECTION,
                 "user_id", "the conversation itself"),
        # The two nobody remembers. LangGraph keys these by thread_id, which IS the
        # session_id — so they cannot be found by user_id at all. They have to be reached
        # through the user's sessions, which is exactly why they get missed.
        Location("checkpoints", config.MONGO_CHECKPOINT_DB, "checkpoints",
                 "thread_id", "the entire graph state, including the message history"),
        Location("checkpoint_writes", config.MONGO_CHECKPOINT_DB, "checkpoint_writes",
                 "thread_id", "the checkpointer's write-ahead log"),
    ]


def _coll(loc: Location):
    from app.stores.mongo import get_client

    client = get_client()
    if client is None:
        return None
    return client[loc.db][loc.collection]


def _session_ids(user_id: str) -> List[str]:
    """Every session this person has. Needed because the checkpoint tables are keyed by
    thread_id, not user_id — the conversation state is not reachable from the person."""
    from app import config

    coll = _coll(Location("t", config.MONGO_RASA_DB,
                          config.MONGO_USER_CONVERSATIONS_COLLECTION, "user_id"))
    if coll is None:
        return []
    try:
        return sorted({
            str(d.get("session_id"))
            for d in coll.find({"user_id": user_id})
            if d.get("session_id")
        })
    except Exception as exc:  # noqa: BLE001
        logger.error("privacy.session_lookup_failed", extra={"error": str(exc)})
        return []


def _filter_for(loc: Location, user_id: str, session_ids: List[str]) -> Dict[str, Any] | None:
    if loc.key == "user_id":
        return {"user_id": user_id}
    if loc.key == "thread_id":
        # No sessions → nothing to match. Returning None (rather than an empty $in) matters:
        # an empty filter would match EVERY row, and this is a delete path.
        return {"thread_id": {"$in": session_ids}} if session_ids else None
    return None


# ── Right of access ──────────────────────────────────────────────────────────

def export_user(user_id: str) -> Dict[str, Any]:
    """Everything we hold about a person, in one document.

    Right of access, and the honest test of whether we know what we store: you cannot export
    what you have forgotten you kept. This function and `erase_user` read from the SAME
    registry on purpose — if a location is missing, both are wrong in the same way, and the
    scanning test catches it.
    """
    out: Dict[str, Any] = {
        "user_id": user_id,
        "exported_at": datetime.now(timezone.utc).isoformat(),
        "data": {},
    }
    sessions = _session_ids(user_id)
    out["sessions"] = sessions

    for loc in _locations():
        coll = _coll(loc)
        flt = _filter_for(loc, user_id, sessions)
        if coll is None or flt is None:
            out["data"][loc.label] = []
            continue
        try:
            docs = [_clean(d) for d in coll.find(flt)]
        except Exception as exc:  # noqa: BLE001
            logger.error("privacy.export_failed", extra={"location": loc.label, "error": str(exc)})
            docs = [{"error": "could not read this location"}]
        out["data"][loc.label] = docs
    return out


def _clean(doc: Dict[str, Any]) -> Dict[str, Any]:
    """Mongo's ObjectId and bytes are not JSON. Stringify rather than drop — an export that
    silently omits fields is not an export."""
    def _v(v):
        if isinstance(v, (str, int, float, bool)) or v is None:
            return v
        if isinstance(v, dict):
            return {k: _v(x) for k, x in v.items()}
        if isinstance(v, list):
            return [_v(x) for x in v]
        return str(v)

    return {k: _v(v) for k, v in doc.items()}


# ── Right to erasure ─────────────────────────────────────────────────────────

def erase_user(user_id: str) -> Dict[str, Any]:
    """Delete everything, then CHECK, then report.

    Returns {deleted: {location: n}, remaining: {location: n}, verified: bool}.

    `verified` is the only field that matters. It is True only when a re-scan finds nothing —
    so "we ran the delete" cannot be mistaken for "the data is gone". Do not tell a person
    their data is erased on the strength of anything else in this dict.
    """
    if not user_id:
        return {"error": "no user_id", "verified": False}

    # Resolve sessions BEFORE deleting — the transcripts are what maps a person to their
    # checkpoint threads, and once they are gone the conversation state is unreachable and
    # therefore un-erasable. Delete order is a correctness property here, not a preference.
    sessions = _session_ids(user_id)
    logger.warning(
        "privacy.erasure_started",
        extra={"user_id": user_id, "sessions": len(sessions)},
    )

    deleted: Dict[str, int] = {}
    for loc in _locations():
        coll = _coll(loc)
        flt = _filter_for(loc, user_id, sessions)
        if coll is None or flt is None:
            deleted[loc.label] = 0
            continue
        try:
            res = coll.delete_many(flt)
            deleted[loc.label] = int(getattr(res, "deleted_count", 0) or 0)
        except Exception as exc:  # noqa: BLE001
            logger.error("privacy.erase_failed", extra={"location": loc.label, "error": str(exc)})
            deleted[loc.label] = -1          # -1 = we do not know. Never report it as 0.

    _drop_caches(user_id)

    remaining = _remaining(user_id, sessions)
    verified = all(v == 0 for v in remaining.values()) and all(v >= 0 for v in deleted.values())

    report = {
        "user_id": user_id,
        "erased_at": datetime.now(timezone.utc).isoformat(),
        "sessions": len(sessions),
        "deleted": deleted,
        "remaining": remaining,
        "verified": verified,
    }
    (logger.warning if verified else logger.error)(
        "privacy.erasure_complete" if verified else "privacy.erasure_INCOMPLETE",
        extra=report,
    )
    return report


def _remaining(user_id: str, sessions: List[str]) -> Dict[str, int]:
    """Re-scan. This is the difference between an erasure and a hope."""
    out: Dict[str, int] = {}
    for loc in _locations():
        coll = _coll(loc)
        flt = _filter_for(loc, user_id, sessions)
        if coll is None or flt is None:
            out[loc.label] = 0
            continue
        try:
            out[loc.label] = int(coll.count_documents(flt))
        except Exception as exc:  # noqa: BLE001
            logger.error("privacy.verify_failed", extra={"location": loc.label, "error": str(exc)})
            out[loc.label] = -1
    return out


def _drop_caches(user_id: str) -> None:
    """Redis holds a copy of the profile. A cache is a store — it is just one people forget,
    and a 'deleted' user whose profile is still served from cache is not deleted."""
    try:
        from app.stores.redis_state import get_redis

        client = get_redis()
        if client is None:
            return
        for key in (f"profile:{user_id}", f"profile_read:{user_id}"):
            client.delete(key)
    except Exception as exc:  # noqa: BLE001
        logger.error("privacy.cache_purge_failed", extra={"error": str(exc)})
