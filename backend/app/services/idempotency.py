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
    return record.status_code, record.response_body


async def record(
    db: AsyncSession,
    user_id: uuid.UUID,
    key: str | None,
    endpoint: str,
    payload: object,
    status_code: int,
    body: dict,
) -> None:
    """Store a completed write's response. Never raises — the write already
    happened, and failing the request now would tell the client to retry
    something that succeeded."""
    if key is None:
        return
    db.add(
        IdempotencyRecord(
            user_id=user_id,
            key=key,
            endpoint=endpoint,
            request_hash=fingerprint(payload),
            status_code=status_code,
            response_body=body,
        )
    )
    try:
        await db.commit()
    except IntegrityError:
        # Two devices raced the same key. The other one won and stored an
        # identical response; nothing to reconcile.
        await db.rollback()


async def purge_expired(db: AsyncSession) -> int:
    """Drop records past [RETENTION]. Returns the number removed."""
    cutoff = utcnow() - RETENTION
    result = await db.execute(
        delete(IdempotencyRecord).where(IdempotencyRecord.created_at < cutoff)
    )
    await db.commit()
    return int(result.rowcount or 0)
