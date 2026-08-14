"""A caller must not be able to choose their own rate-limit bucket.

`core/ratelimit.client_ip` is the key function behind every `@limiter.limit` in
the product — login brute-force, OTP request, password reset, and the LLM/TTS
cost guards on chat, oracle, habits and admin narration. It used to read the
FIRST `X-Forwarded-For` hop, on the stated belief that Caddy "set" the header.
Caddy appends to it. So the first entry was whatever the caller typed, and one
header rotated per request minted a fresh bucket every time — measured against
the running API on 2026-08-13: 26 logins with one spoofed value hit 429 at the
cap, 30 logins with a rotating spoofed value never tripped it at all.

These tests pin the direction of the count, because reading this header from the
wrong end is silent: nothing errors, nothing logs, and the limiter keeps
returning 200s that look like health.
"""
import pytest

from app.core.ratelimit import client_ip


class _Request:
    """The two attributes slowapi's key function actually touches."""

    def __init__(self, forwarded: str | None, peer: str = "203.0.113.7"):
        self.headers = {} if forwarded is None else {"x-forwarded-for": forwarded}
        self.client = type("C", (), {"host": peer})()


@pytest.fixture(autouse=True)
def _one_proxy(monkeypatch):
    """The `deploy/Caddyfile` topology: exactly one proxy in front of the API."""
    from app.core import ratelimit

    monkeypatch.setattr(ratelimit.settings, "trusted_proxy_hops", 1)


def test_a_forged_prefix_cannot_move_the_bucket():
    """The attack, directly: everything before our proxy's hop is caller-typed."""
    real = "198.51.100.4"
    assert client_ip(_Request(f"1.2.3.4, {real}")) == real
    assert client_ip(_Request(f"9.9.9.9, 8.8.8.8, evil, {real}")) == real
    # Two requests a rotating attacker would send land on the SAME key, which is
    # the whole point — the limiter must see one caller, not two.
    assert client_ip(_Request(f"10.0.0.1, {real}")) == client_ip(_Request(f"10.0.0.2, {real}"))


def test_the_honest_single_hop_case_still_resolves_the_client():
    """No forged prefix: Caddy's one appended entry IS the client."""
    assert client_ip(_Request("198.51.100.4")) == "198.51.100.4"


def test_a_request_that_skipped_the_proxy_falls_back_to_the_socket():
    """Health checks and direct container hits carry no header we can place."""
    assert client_ip(_Request(None, peer="203.0.113.7")) == "203.0.113.7"
    assert client_ip(_Request("", peer="203.0.113.7")) == "203.0.113.7"
    # Whitespace-only entries are not hops; they must not shift the count.
    assert client_ip(_Request("  ,  ", peer="203.0.113.7")) == "203.0.113.7"


def test_fewer_hops_than_we_trust_falls_back_rather_than_reading_the_caller(monkeypatch):
    """Claiming two proxies while one answers must not re-expose the first hop.

    The dangerous failure would be silently sliding to `parts[0]` — the caller's
    own value — the moment the chain is shorter than configured.
    """
    from app.core import ratelimit

    monkeypatch.setattr(ratelimit.settings, "trusted_proxy_hops", 2)
    assert client_ip(_Request("1.2.3.4", peer="203.0.113.7")) == "203.0.113.7"


def test_two_proxies_count_back_two(monkeypatch):
    """A CDN in front of Caddy: the client is what the CDN appended."""
    from app.core import ratelimit

    monkeypatch.setattr(ratelimit.settings, "trusted_proxy_hops", 2)
    # caller-forged, real client, cdn-edge  →  Caddy appended the edge, CDN the client
    assert client_ip(_Request("1.2.3.4, 198.51.100.4, 192.0.2.9")) == "198.51.100.4"


def test_zero_hops_ignores_the_header_entirely(monkeypatch):
    """Directly-exposed deployment: the socket is the only thing worth trusting."""
    from app.core import ratelimit

    monkeypatch.setattr(ratelimit.settings, "trusted_proxy_hops", 0)
    assert client_ip(_Request("1.2.3.4, 198.51.100.4", peer="203.0.113.7")) == "203.0.113.7"


def test_production_refuses_to_boot_with_header_parsing_disabled():
    """0 hops behind Caddy keys the whole internet onto one bucket."""
    from app.core.config import Settings

    with pytest.raises(ValueError, match="TRUSTED_PROXY_HOPS"):
        Settings(
            env="production",
            secret_key="x" * 40,
            admin_password="a-real-admin-password",
            seed_demo_data=False,
            trusted_proxy_hops=0,
        )
