"""Every prior version of a prompt, so a bad save is recoverable.

## Why this exists

The workbook is an .xlsx edited in place. A save overwrites the sheet, `reg.version` ticks a
counter, and `_audit` writes one line to stdout naming the actor and the version numbers —
but **not the text**. So the console could destroy a 39,000-character coaching prompt with
one PUT and offer no way back. The only copy was in git, which an operator on a running
deployment does not have, and in s3 mode a timestamped backup of the WHOLE workbook, which
nobody can read a single agent out of without downloading and opening it.

## What a version is

The text as it stood BEFORE an edit, captured at write time. That ordering is the whole
design: snapshot-then-write means the thing you want back is always already saved. Snapshot
after, and the one edit you most need to undo — the one that just destroyed the text — is
the one that never got recorded.

## Not org-scoped, deliberately

The workbook is global: one arc, one set of prompts, every tenant. `scoped()` is absent here
on purpose, unlike every other store in this package — a per-org filter would silently hide
history from the operator who needs it, and there is no per-org prompt to scope to. The
route is `internal_admin` only (`routers/prompts.py`), which is the real control.

## Bounded

`KEEP` versions per stage. A prompt author iterating for an afternoon should not be able to
fill the disk, and nobody has ever needed the 61st revision of one agent.
"""

from __future__ import annotations

import hashlib
import logging
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

logger = logging.getLogger("cerebrozen.prompt_versions")

COLLECTION = "prompt_versions"

#: Per stage. Enough to walk back an afternoon's editing; not enough to be a storage story.
KEEP = 60


def _collection():
    """The versions collection, or None.

    Swallows its own failures rather than raising: every caller here is best-effort by
    design, and a client that throws on construction would otherwise escape `snapshot` and
    block a prompt edit — leaving an operator unable to fix a bad prompt *because* the undo
    log was unavailable. That inverts the point of the undo log. (Found by the test that
    asserts exactly this; the try/except only wrapped the write.)
    """
    try:
        from app import config
        from app.stores.mongo import get_client

        client = get_client()
        if client is None:
            return None
        return client[config.MONGO_BACKEND_DB][COLLECTION]
    except Exception as exc:  # noqa: BLE001
        logger.error("prompt_versions.collection_unavailable", extra={"error": str(exc)})
        return None


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def content_hash(text: str) -> str:
    """Short, stable id for a body — the same idea the registry's own version uses."""
    return hashlib.sha256((text or "").encode("utf-8")).hexdigest()[:12]


def snapshot(stage: str, text: str, *, actor: str = "", reason: str = "edit") -> Optional[str]:
    """Record the text as it stands now. Returns the version id, or None if not stored.

    Never raises. A store that is down must not block a prompt edit: the alternative is an
    operator unable to fix a bad prompt because the undo log is unavailable, which inverts
    the point of the undo log.
    """
    coll = _collection()
    if coll is None or not stage:
        return None
    version_id = content_hash(text) + "-" + _now()[11:19].replace(":", "")
    try:
        coll.insert_one({
            "_id": version_id,
            "version_id": version_id,
            "stage": stage,
            "text": text or "",
            "size": len(text or ""),
            "hash": content_hash(text),
            "actor": actor or "unknown",
            "reason": reason,
            "at": _now(),
        })
    except Exception as exc:  # noqa: BLE001
        logger.error("prompt_versions.snapshot_failed", extra={"stage": stage, "error": str(exc)})
        return None
    _trim(stage)
    return version_id


def _trim(stage: str) -> None:
    coll = _collection()
    if coll is None:
        return
    try:
        rows = list(coll.find({"stage": stage}, {"_id": 1, "at": 1}))
        if len(rows) <= KEEP:
            return
        rows.sort(key=lambda r: r.get("at") or "", reverse=True)
        for row in rows[KEEP:]:
            coll.delete_one({"_id": row["_id"]})
    except Exception as exc:  # noqa: BLE001
        logger.warning("prompt_versions.trim_failed", extra={"stage": stage, "error": str(exc)})


def history(stage: str, limit: int = KEEP) -> List[Dict[str, Any]]:
    """Newest first, WITHOUT the bodies.

    A list endpoint that shipped 60 × 39,000 characters to render a table of dates would be
    its own bug. The body is fetched one at a time, by `get`.
    """
    coll = _collection()
    if coll is None or not stage:
        return []
    try:
        rows = list(coll.find({"stage": stage}, {"text": 0}))
    except Exception as exc:  # noqa: BLE001
        logger.error("prompt_versions.history_failed", extra={"stage": stage, "error": str(exc)})
        return []
    rows.sort(key=lambda r: r.get("at") or "", reverse=True)
    return [{k: v for k, v in r.items() if k != "_id"} for r in rows[: max(0, limit)]]


def get(stage: str, version_id: str) -> Optional[Dict[str, Any]]:
    """One version, body included. Scoped by stage as well as id so a mistyped id cannot
    hand back another agent's prompt."""
    coll = _collection()
    if coll is None or not stage or not version_id:
        return None
    try:
        row = coll.find_one({"_id": version_id, "stage": stage})
    except Exception as exc:  # noqa: BLE001
        logger.error("prompt_versions.get_failed", extra={"stage": stage, "error": str(exc)})
        return None
    if not row:
        return None
    return {k: v for k, v in row.items() if k != "_id"}
