"""Replay protection for client-keyed writes.

Used by the append-style write routes (`POST /moods`, `POST /journal`), the two
a mobile offline queue can genuinely duplicate. `POST /sleep` needs nothing: it
upserts on the night's date, so sending it twice is already the same night.

Contract, from the client's side:

* Send `Idempotency-Key: <uuid>` with the write. Keep the key with the queued
  item, so a retry after a crash reuses it.
* No header → nothing is recorded and the write behaves exactly as before.
* Same key, same body → the stored response comes back; nothing is written twice.
* Same key, different body → 409. That is a client bug (a key got reused for a
  different write), and guessing which one the user meant is worse than saying so.

Ordering matters, and this is the register's C4 (finding 51). The record used
to be written *after* the write had already committed, in a separate
transaction - so two concurrent retries of the same key both found no record,
both inserted a row, and the loser's IntegrityError was swallowed: the exact
duplicate this exists to prevent still landed. The key is now RESERVED in the
same transaction as the write, before it. The unique constraint settles the
race at insert time, so the loser never writes anything at all.
"""
from __future__ import annotations

import hashlib
import json
import uuid
from datetime import timedelta

from fastapi import HTTPException, status
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import utcnow
from app.models.idempotency import IdempotencyRecord

# Long past any queue's useful retry window; short enough that the table stays small.
RETENTION = timedelta(days=7)

MAX_KEY_LENGTH = 120


def fingerprint(payload: object) -> str:
    """Stable sha256 of a request body — key order and spacing must not matter."""
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"), default=str)
    return hashlib.sha256(canonical.encode()).hexdigest()


def normalise_key(raw: str | None) -> str | None:
    """Trim and reject nonsense keys rather than storing them.

    An over-long key would raise a database error on insert *after* the write
    had already happened, which is the one outcome worse than no idempotency.
    """
    if raw is None:
        return None
    key = raw.strip()
    if not key or len(key) > MAX_KEY_LENGTH:
        return None
    return key


async def replay(
    db: AsyncSession,
    user_id: uuid.UUID,
    key: str | None,
    endpoint: str,
    payload: object,
) -> tuple[int, dict] | None:
    """Return the stored `(status_code, body)` for an exact replay, else None.

    Raises 409 when the key was already used for a different body.
    """
    if key is None:
        return None
    record = await db.scalar(
        select(IdempotencyRecord).where(
            IdempotencyRecord.user_id == user_id,
            IdempotencyRecord.key == key,
        )
    )
    if record is None:
        return None
    if record.endpoint != endpoint or record.request_hash != fingerprint(payload):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Idempotency-Key was already used for a different request",
        )
    if record.status_code == PENDING:
        # Reserved but never completed: either the first request is still in
        # flight, or it died between reserving and committing (in which case
        # its write rolled back with it - nothing was stored, and the key is
        # stale until purge). "In flight" is honest either way; inventing a
        # success body would not be.
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A request with this Idempotency-Key is already in flight",
        )
    return record.status_code, record.response_body


# A reserved-but-not-yet-completed record. Never returned to a client: a row
# still holding it means the first request is mid-flight (or died before
# completing), which [replay] reports as a retryable conflict.
PENDING = 0


async def reserve(
    db: AsyncSession,
    user_id: uuid.UUID,
    key: str | None,
    endpoint: str,
    payload: object,
) -> IdempotencyRecord | None:
    """Claim [key] for this write *before* it happens, in the caller's own
    transaction. Returns the row to complete later, or None when unkeyed.

    Raises 409 when a concurrent request already holds the key - the loser of
    the race stops here, having written nothing. That is the whole point: the
    uniqueness is the database's, and it is decided before any row exists
    rather than after both already do.
    """
    if key is None:
        return None
    row = IdempotencyRecord(
        user_id=user_id,
        key=key,
        endpoint=endpoint,
        request_hash=fingerprint(payload),
        status_code=PENDING,
        response_body={},
    )
    db.add(row)
    try:
        await db.flush()
    except IntegrityError:
        await db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A request with this Idempotency-Key is already in flight",
        )
    return row


def complete(row: IdempotencyRecord | None, status_code: int, body: dict) -> None:
    """Stamp the reserved row with the response. Committed by the caller in
    the SAME transaction as the write it describes, so the record and the row
    it protects can never disagree."""
    if row is None:
        return
    row.status_code = status_code
    row.response_body = body


async def purge_expired(db: AsyncSession) -> int:
    """Drop records past [RETENTION]. Returns the number removed."""
    cutoff = utcnow() - RETENTION
    result = await db.execute(
        delete(IdempotencyRecord).where(IdempotencyRecord.created_at < cutoff)
    )
    await db.commit()
    return int(result.rowcount or 0)
