"""Shared rate limiter (slowapi).

Applied to auth + expensive AI/voice endpoints to blunt brute force and cost
abuse. Disabled under the test suite, where many sign-ups come from a single
client and would otherwise trip the limit.
"""
import os

from slowapi import Limiter
from slowapi.util import get_remote_address

from app.core.config import settings


def client_ip(request) -> str:
    """The address every ``@limiter.limit`` in the app is keyed on.

    Counted from the **end** of ``X-Forwarded-For``, never the start. That is the
    entire security property of this function, so it is worth writing down why.

    Caddy — like every conforming proxy — **appends** the peer it actually saw to
    whatever ``X-Forwarded-For`` already arrived; it does not replace it. So the
    first entry is a string the caller typed, and only the trailing entries were
    vouched for by infrastructure we run. Reading the first hop meant any caller
    could mint a fresh rate-limit bucket per request with one header, which
    defeated every limit in the product at once: login brute-force, OTP request,
    password reset, and the LLM/TTS cost guards on chat, oracle, habits and admin
    narration. Measured 2026-08-13 against the running API — 26 logins carrying
    one spoofed header hit 429 at the cap; 30 logins rotating the spoofed value
    never tripped a limit that fires at 20.

    ``trusted_proxy_hops`` is how many proxies we run in front of the API, and so
    how many trailing entries are ours rather than the caller's. It is explicit
    because the bug it replaces was an *implicit* trust assumption — the old
    docstring said the header was "set by the Caddy reverse proxy", and appended
    is not set. Put a CDN in front of Caddy and this becomes 2, or the limiter
    starts keying every visitor onto the CDN's edge address.
    """
    hops = settings.trusted_proxy_hops
    if hops > 0:
        forwarded = request.headers.get("x-forwarded-for", "")
        parts = [p.strip() for p in forwarded.split(",") if p.strip()]
        # Fewer entries than we trust means the request did not come through the
        # chain we expect — a direct hit on the container, a health check, a
        # misconfigured proxy. Trust the socket over a header we cannot place.
        if len(parts) >= hops:
            return parts[-hops]
    return get_remote_address(request)


# Two ways to switch this off, both for test suites and neither for production:
#
#   TESTING=1            pytest — many sign-ups from one client.
#   RATE_LIMIT_ENABLED=0 the Playwright stack, which has the same problem: every
#                        browser test shares one IP, so a suite that signs up a
#                        dozen accounts trips a 10/minute signup limit and goes
#                        flaky. It surfaced as exactly that (2026-08-13).
#
# `Settings._guard_production` refuses to boot with RATE_LIMIT_ENABLED=0 when
# ENV=production, so this cannot be left off by accident on a real deploy.
_enabled = os.getenv("TESTING") != "1" and os.getenv("RATE_LIMIT_ENABLED", "1") not in ("0", "false", "False")

limiter = Limiter(key_func=client_ip, enabled=_enabled)
