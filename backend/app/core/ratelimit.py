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

def account_key(request) -> str:
    """Key by the signed-in ACCOUNT when there is one, else fall back to the IP.

    `client_ip` above bounds how fast one *address* may call. That is the wrong
    unit for an authenticated caller: addresses are cheap. A mobile connection
    hands out a new one on request, a VPN sells a list of them, and a residential
    proxy pool rents thousands by the hour — so a single account can hold every
    per-minute cap in this file at arm's length by rotating where it calls from,
    and no counter anywhere would move (WC-89). The endpoints that matters most
    on are the ones that spend money per call: LLM plan generation, STT, TTS.

    Stacked *beneath* the IP limit rather than replacing it, because the two
    catch different abusers — one account from many addresses, and many accounts
    from one address (a signup farm behind a single host). Neither bound implies
    the other, so both are applied and a caller must satisfy both.

    **The subject is read from a signature-verified token, never from the wire.**
    An unverified `sub` would be a caller-chosen bucket, which is precisely the
    bug `client_ip` documents above: the old X-Forwarded-For read let anyone mint
    a fresh bucket per request with one header. Trusting an unsigned JWT claim
    here would reintroduce it with a different header, and it would look just as
    healthy — a wall of 200s. An expired or malformed token falls back to the IP
    key; the request is about to 401 anyway, and until it does it should still
    count against *something*.
    """
    header = request.headers.get("authorization", "") or ""
    scheme, _, token = header.partition(" ")
    if scheme.lower() == "bearer" and token:
        from app.core.security import ACCESS, decode_token  # local: import cycle

        payload = decode_token(token, expected_type=ACCESS)
        subject = (payload or {}).get("sub")
        if subject:
            return f"account:{subject}"
    return client_ip(request)


def account_limit(limit_value: str):
    """A second limit on an endpoint, counted per account instead of per address.

    Written as a helper so the call site reads as what it is — two bounds, not
    one confusing decorator — and so the key function cannot be passed wrongly
    at any of the places that use it.
    """
    return limiter.limit(limit_value, key_func=account_key)

