"""Redis hot-state tier (Phase 9) — per-session lock + profile cache.

Config-driven and optional (mirrors the optional-Mongo pattern):
  - `REDIS_URL` set      → connect to that Redis (Docker locally / managed in prod).
  - unset OR unreachable → fall back to an in-process **fakeredis**, so local dev
    needs no Redis server.

The Mongo checkpointer stays the durable source of truth (Art. 8.1). Redis only
holds hot, regenerable state:

  - **Per-session turn lock** (Art. 8.4): one in-flight turn per `session_id`, so
    concurrent turns can't race the checkpoint. A second turn blocks up to
    `REDIS_LOCK_WAIT_MS`, then is rejected (never interleaved).
  - **profile_read cache**: short-TTL cache of the per-user profile so a user who
    opens several sessions quickly skips repeat Mongo reads.

Everything degrades safe: a Redis hiccup never raises into a turn — the lock falls
open (turn proceeds) and the cache misses (reads Mongo), so the worst case is the
pre-Redis behaviour.
"""

from __future__ import annotations

import json
import logging
import time
import uuid
from contextlib import contextmanager
from typing import Any, Dict, Iterator, Optional

from app import config
from app.tenancy import current_org

logger = logging.getLogger("cerebrozen.redis")

_client = None  # lazily created singleton (real redis or fakeredis)
_backend = "uninitialized"


def get_redis():
    """Return the shared Redis client (real or fakeredis), creating it once."""
    global _client, _backend
    if _client is not None:
        return _client
    if config.REDIS_URL:
        try:
            import redis  # redis-py

            client = redis.Redis.from_url(
                config.REDIS_URL, socket_timeout=2, socket_connect_timeout=2,
                decode_responses=True,
            )
            client.ping()
            _client, _backend = client, "redis"
            logger.info("redis.connected", extra={"backend": "redis"})
            return _client
        except Exception as exc:  # noqa: BLE001 — fall back, never fail startup
            logger.warning("redis.connect_failed_fallback_fakeredis", extra={"error": str(exc)})
    try:
        import fakeredis

        _client, _backend = fakeredis.FakeRedis(decode_responses=True), "fakeredis"
        logger.info("redis.connected", extra={"backend": "fakeredis"})
    except Exception as exc:  # noqa: BLE001 — no redis at all → caching/lock no-op
        logger.warning("redis.unavailable", extra={"error": str(exc)})
        _client, _backend = None, "none"
    return _client


def backend() -> str:
    """Which backend is active ('redis' | 'fakeredis' | 'none')."""
    get_redis()
    return _backend


# --- per-session turn lock (Art. 8.4) ---------------------------------------


class SessionBusyError(Exception):
    """Raised when another turn holds the session lock and the wait elapsed."""


@contextmanager
def session_lock(session_id: str) -> Iterator[None]:
    """Serialize turns on one `session_id`. Blocks up to REDIS_LOCK_WAIT_MS for the
    lock, then raises SessionBusyError. No-ops (lock falls open) if Redis is down."""
    client = get_redis()
    if client is None or not session_id:
        yield  # degrade open — no lock available
        return

    key = f"cerebrozen:lock:{current_org()}:{session_id}"
    token = uuid.uuid4().hex
    ttl_ms = config.REDIS_LOCK_TTL_MS
    deadline = time.monotonic() + config.REDIS_LOCK_WAIT_MS / 1000.0
    acquired = False
    try:
        while True:
            try:
                acquired = bool(client.set(key, token, nx=True, px=ttl_ms))
            except Exception as exc:  # noqa: BLE001 — Redis hiccup → degrade open
                logger.warning("redis.lock_error_degrade_open", extra={"error": str(exc)})
                yield
                return
            if acquired:
                break
            if time.monotonic() >= deadline:
                raise SessionBusyError(
                    f"Another turn is in progress for session {session_id}."
                )
            time.sleep(0.05)
        yield
    finally:
        if acquired:
            # Release only our own lock (compare-and-delete) so we never drop a lock
            # a later turn acquired after ours expired.
            try:
                _release_lua(client, key, token)
            except Exception:  # noqa: BLE001 — best effort; the TTL will expire it
                pass


_RELEASE_LUA = (
    "if redis.call('get', KEYS[1]) == ARGV[1] then "
    "return redis.call('del', KEYS[1]) else return 0 end"
)


def _release_lua(client, key: str, token: str) -> None:
    try:
        client.eval(_RELEASE_LUA, 1, key, token)
    except Exception:  # noqa: BLE001 — fakeredis/older servers: fall back to get+del
        if client.get(key) == token:
            client.delete(key)


# --- profile_read cache ------------------------------------------------------


def get_cached_profile(user_id: str) -> Optional[Dict[str, Any]]:
    """Return a cached profile dict for the user, or None on miss / no cache."""
    if not config.REDIS_PROFILE_TTL_S or not user_id:
        return None
    client = get_redis()
    if client is None:
        return None
    try:
        raw = client.get(f"cerebrozen:profile:{current_org()}:{user_id}")
        return json.loads(raw) if raw else None
    except Exception as exc:  # noqa: BLE001 — cache miss on any error
        logger.warning("redis.profile_get_failed", extra={"error": str(exc)})
        return None


def set_cached_profile(user_id: str, profile: Dict[str, Any]) -> None:
    """Cache the user's profile with the configured TTL. Best-effort."""
    if not config.REDIS_PROFILE_TTL_S or not user_id:
        return
    client = get_redis()
    if client is None:
        return
    try:
        client.set(
            f"cerebrozen:profile:{current_org()}:{user_id}",
            json.dumps(profile, ensure_ascii=False, default=str),
            ex=config.REDIS_PROFILE_TTL_S,
        )
    except Exception as exc:  # noqa: BLE001 — caching is best-effort
        logger.warning("redis.profile_set_failed", extra={"error": str(exc)})


# --- generic JSON cache (RAG results, etc.) ----------------------------------


def cache_get_json(key: str) -> Optional[Any]:
    """Return a cached JSON value for `key`, or None on miss / no cache / error."""
    if not key:
        return None
    client = get_redis()
    if client is None:
        return None
    try:
        raw = client.get(key)
        return json.loads(raw) if raw else None
    except Exception as exc:  # noqa: BLE001 — treat any error as a miss
        logger.warning("redis.cache_get_failed", extra={"error": str(exc)})
        return None


def cache_set_json(key: str, value: Any, ttl_s: int) -> None:
    """Cache a JSON-serialisable value under `key` for ttl_s seconds. Best-effort."""
    if not key or ttl_s <= 0:
        return
    client = get_redis()
    if client is None:
        return
    try:
        client.set(key, json.dumps(value, ensure_ascii=False, default=str), ex=ttl_s)
    except Exception as exc:  # noqa: BLE001 — caching is best-effort
        logger.warning("redis.cache_set_failed", extra={"error": str(exc)})


# --- session-seen marker (skip the pre-turn Mongo get_state on continuing turns) ---

# A session stays "seen" for this long; longer than any realistic session. If it
# expires (or Redis is down), is_first_turn falls back to the authoritative
# get_state, so this is a pure fast-path, never a correctness risk.
_SEEN_TTL_S = 21600  # 6 hours


def is_session_seen(session_id: str) -> bool:
    """True if this session has already run a turn (per the Redis marker). False on
    no marker / no cache — the caller then falls back to the durable get_state."""
    if not session_id:
        return False
    client = get_redis()
    if client is None:
        return False
    try:
        return bool(client.exists(f"cerebrozen:seen:{current_org()}:{session_id}"))
    except Exception as exc:  # noqa: BLE001 — unknown → fall back to get_state
        logger.warning("redis.seen_get_failed", extra={"error": str(exc)})
        return False


def mark_session_seen(session_id: str) -> None:
    """Mark a session as having run a turn, so later turns skip the get_state probe."""
    if not session_id:
        return
    client = get_redis()
    if client is None:
        return
    try:
        client.set(f"cerebrozen:seen:{current_org()}:{session_id}", "1", ex=_SEEN_TTL_S)
    except Exception as exc:  # noqa: BLE001 — best-effort fast-path
        logger.warning("redis.seen_set_failed", extra={"error": str(exc)})
