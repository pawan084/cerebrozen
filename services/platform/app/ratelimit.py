"""Per-IP rate limiting for the abuse-prone auth endpoints.

Signup/login are credential-guessing and enumeration surfaces; the OTP-request and
password-forgot endpoints send email, so an unbounded caller can spam somebody's inbox
(or ours) with reset codes. This bounds all of them per client IP.

A fixed-window counter held in-process — the platform has no Redis dependency, and per
process is enough to turn "unbounded" into "bounded". Behind N instances the effective
rate is N×, which still bounds abuse; a shared limiter is a deployment concern, not a
correctness one. Fail-open is deliberate: the limiter never becomes the reason auth is
down. Config is read at call time so a test can tune it without reimporting."""

from __future__ import annotations

import os
import time

from fastapi import HTTPException, Request

# One window's counts, replaced wholesale when the window rolls over — so old windows
# self-evict and the map can't grow without bound.
_state: dict = {"window": -1, "counts": {}}


def _cfg() -> tuple[bool, int, int]:
    enabled = os.environ.get("CEREBROZEN_RATE_LIMIT", "true").strip().lower() in ("1", "true", "yes")
    limit = int(os.environ.get("CEREBROZEN_AUTH_RL_MAX", "20") or 20)
    window = int(os.environ.get("CEREBROZEN_AUTH_RL_WINDOW", "60") or 60)
    return enabled, limit, window


def _client_ip(request: Request) -> str:
    """The client IP, safe against X-Forwarded-For spoofing.

    The LEFTMOST XFF hop is client-appended and forgeable — trusting it lets an attacker
    rotate a fake IP per request and evade the limit entirely (even behind a proxy). So we
    use the real peer (`request.client.host`) by default, and only consult XFF when
    CEREBROZEN_TRUSTED_PROXIES says how many proxies sit in front — then the real client is
    that many hops from the RIGHT, and everything to its left is ignored."""
    peer = request.client.host if request.client else "unknown"
    trusted = int(os.environ.get("CEREBROZEN_TRUSTED_PROXIES", "0") or 0)
    if trusted > 0:
        chain = [h.strip() for h in (request.headers.get("X-Forwarded-For") or "").split(",") if h.strip()]
        # Each trusted proxy appended one hop on the right; the real client is the
        # leftmost of that trusted tail. Anything further left is attacker-appended.
        idx = len(chain) - trusted
        if 0 <= idx < len(chain):
            return chain[idx]
    return peer


def reset_for_test() -> None:
    _state["window"] = -1
    _state["counts"] = {}


async def limit_auth(request: Request) -> None:
    enabled, limit, window_s = _cfg()
    if not enabled or limit <= 0:
        return
    window = int(time.time()) // window_s
    if _state["window"] != window:
        _state["window"] = window
        _state["counts"] = {}
    ip = _client_ip(request)
    count = _state["counts"].get(ip, 0) + 1
    _state["counts"][ip] = count
    if count > limit:
        retry_after = window_s - (int(time.time()) % window_s)
        raise HTTPException(
            status_code=429,
            detail="Too many attempts. Please wait a moment and try again.",
            headers={"Retry-After": str(max(1, retry_after))},
        )
