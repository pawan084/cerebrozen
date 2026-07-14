"""Request rate limiting on the endpoints that spend money.

Every coaching turn is a paid LLM call against a ~21K-token prompt. Until now nothing
bounded how fast a caller could ask for them, so a loop against
``POST /v1/sessions/{id}/turn`` was a direct line to the OpenAI bill — no bug required,
just a retry storm in a mobile client or a bored person with curl. That is the hole this
closes. It is a *cost* control first and an abuse control second; it deliberately does
not try to be a WAF.

Two decisions worth stating, because both look wrong at a glance:

**The key is the JWT subject, never the body's ``user_id``.** The turn endpoint takes a
``user_id`` in its request body, and it is tempting to limit on that. It is
client-supplied: an attacker rotates it per request and the limit evaporates. The token
subject is signed, so it cannot be. When auth is off (local dev) we fall back to the
peer IP, which is weaker but is all there is — and in that mode nothing is at stake.

**A limiter failure lets the request through.** If Redis is down, this module counts
nothing and returns. The alternative — failing closed — converts a cache outage into a
total coaching outage, which is a strictly worse day than an unbounded bill for the
minutes it takes to notice. The circuit breaker in ``app/llm/resilience.py`` is the
backstop that actually caps spend when things are on fire.

Window semantics: a fixed window, not a sliding one. A caller can therefore burst up to
2× the limit across a window boundary. That is accepted knowingly — the point is to turn
"unbounded" into "bounded", and 2× a bound is still a bound. A sliding-window log costs
a sorted set per caller and buys precision nobody here needs.

Backend follows ``app/stores/redis_state.py``: real Redis when ``REDIS_URL`` is set,
otherwise in-process fakeredis. Note the consequence — **without a shared Redis the limit
is per process**, so N app instances allow N× the configured rate. It still bounds spend;
it just bounds it N times higher. Set ``REDIS_URL`` in any real deployment.
"""

from __future__ import annotations

import logging
import os
from typing import Optional

from fastapi import HTTPException, Request

logger = logging.getLogger("cerebrozen.ratelimit")


def _int_env(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, "").strip() or default)
    except ValueError:
        return default


def _enabled() -> bool:
    # Read at call time, not import time, so tests can toggle it without reimporting.
    return os.environ.get("CEREBROZEN_RATE_LIMIT", "true").strip().lower() in ("1", "true", "yes")


# bucket -> (limit, window_seconds). Held in a dict rather than baked into each
# dependency's closure so the effective limit is readable at runtime and adjustable in a
# test — a limit that cannot be exercised is a limit nobody will notice breaking.
#
# Sized off human behaviour, not off what the server can take: a real coaching turn takes
# 4-20s to read and answer, so a *person* cannot exceed ~6/min. 20/min leaves an order of
# magnitude of headroom for retries and impatience while still capping a runaway client at
# something survivable. Starting a session is heavier (greeting + context load) and rarer.
LIMITS: dict[str, tuple[int, int]] = {
    "turn": (_int_env("CEREBROZEN_RATE_LIMIT_TURNS_PER_MIN", 20), 60),
    "start": (_int_env("CEREBROZEN_RATE_LIMIT_STARTS_PER_HOUR", 30), 3600),
}


def _caller_id(request: Request) -> str:
    """Identify the caller for limiting: signed token subject, else peer IP.

    The Authorization header is decoded here rather than reusing ``require_auth``'s
    claims because a dependency cannot see another dependency's return value. Decoding
    is cheap (an HMAC verify) and it has already happened once this request, so the
    token is known-good by the time we get here — but we still swallow failures: a bad
    token is 401's problem, not the limiter's.
    """
    from app.auth.dependencies import auth_enabled

    if auth_enabled():
        try:
            from app.auth.jwt_validator import decode_token, extract_bearer

            claims = decode_token(extract_bearer(request.headers.get("Authorization")))
            sub = str(claims.get("sub") or claims.get("user_id") or "").strip()
            if sub:
                return f"sub:{sub}"
        except Exception:  # noqa: BLE001 — unauthenticated requests are 401'd elsewhere
            pass
    # Behind a load balancer the peer is the LB, so honour the forwarded chain's first
    # hop. Spoofable if the app is exposed directly — acceptable, because this branch is
    # only reached when auth is off, i.e. local dev.
    fwd = (request.headers.get("X-Forwarded-For") or "").split(",")[0].strip()
    return f"ip:{fwd or (request.client.host if request.client else 'unknown')}"


def _hit(bucket: str, caller: str, limit: int, window_s: int) -> Optional[int]:
    """Count one request. Returns seconds-to-retry if over the limit, else None.

    Fail-open: any backend error returns None (allowed) — see the module docstring.
    """
    try:
        from app.stores.redis_state import get_redis

        client = get_redis()
        if client is None:
            return None
        import time

        # Fixed window: the key embeds the window index, so expiry is implicit and no
        # cleanup pass is needed.
        window = int(time.time()) // window_s
        key = f"rl:{bucket}:{caller}:{window}"
        count = client.incr(key)
        if count == 1:
            # Only the first hit in a window sets the TTL, so the window cannot be
            # extended indefinitely by a caller who keeps hitting it.
            client.expire(key, window_s)
        if count > limit:
            retry_after = window_s - (int(time.time()) % window_s)
            return max(1, retry_after)
        return None
    except Exception as exc:  # noqa: BLE001
        logger.warning("ratelimit.backend_error_allowing", extra={"error": str(exc)})
        return None


def _limiter(bucket: str):
    """Build a FastAPI dependency enforcing this bucket's entry in ``LIMITS``.

    The limit is looked up per request, not captured at import: an unknown bucket, or a
    limit configured to 0 or less, means "no limit" and the request passes.
    """

    async def dependency(request: Request) -> None:
        limit, window_s = LIMITS.get(bucket, (0, 60))
        if not _enabled() or limit <= 0:
            return
        caller = _caller_id(request)
        retry_after = _hit(bucket, caller, limit, window_s)
        if retry_after is None:
            return
        from app.metrics import record_rate_limited

        record_rate_limited(bucket=bucket)
        # Log the bucket and the *kind* of caller, never the raw subject or IP: this
        # line lands in CloudWatch, and a rate-limit event is not a reason to write
        # someone's identity to a log that outlives the request.
        logger.warning(
            "ratelimit.exceeded",
            extra={"bucket": bucket, "caller_kind": caller.split(":", 1)[0], "limit": limit},
        )
        raise HTTPException(
            status_code=429,
            detail=f"Rate limit exceeded: {limit} per {window_s}s. Retry in {retry_after}s.",
            headers={"Retry-After": str(retry_after)},
        )

    return dependency


# The two endpoints that cost money. Everything else is a database read.
limit_turn = _limiter("turn")
limit_start = _limiter("start")
