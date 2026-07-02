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


limiter = Limiter(key_func=client_ip, enabled=os.getenv("TESTING") != "1")
