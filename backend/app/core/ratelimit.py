"""Shared rate limiter (slowapi).

Applied to auth + expensive AI/voice endpoints to blunt brute force and cost
abuse. Disabled under the test suite, where many sign-ups come from a single
client and would otherwise trip the limit.
"""
import os

from slowapi import Limiter
from slowapi.util import get_remote_address


def client_ip(request) -> str:
    """Real client IP: first X-Forwarded-For hop when present (set by the Caddy
    reverse proxy in prod — without this every request would key on the proxy's
    IP and share one bucket), else the socket peer."""
    fwd = request.headers.get("x-forwarded-for", "")
    if fwd:
        return fwd.split(",")[0].strip()
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
