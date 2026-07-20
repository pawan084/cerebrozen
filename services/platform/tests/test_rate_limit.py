"""Per-IP auth rate limiting. The suite runs with it OFF (conftest) so hundreds of
tests sharing one client IP aren't throttled; these tests re-enable it explicitly and
prove it bounds the abuse-prone endpoints (signup/login/OTP/forgot)."""

from app import ratelimit


async def test_disabled_by_default_allows_many(client):
    # conftest pins CEREBROZEN_RATE_LIMIT=false — the limiter is a no-op.
    for _ in range(30):
        r = await client.post("/auth/password/forgot", json={"email": "x@example.com"})
        assert r.status_code == 200


async def test_blocks_after_the_cap(client, monkeypatch):
    monkeypatch.setenv("CEREBROZEN_RATE_LIMIT", "true")
    monkeypatch.setenv("CEREBROZEN_AUTH_RL_MAX", "2")
    ratelimit.reset_for_test()
    body = {"email": "a@example.com"}
    assert (await client.post("/auth/password/forgot", json=body)).status_code == 200
    assert (await client.post("/auth/password/forgot", json=body)).status_code == 200
    r = await client.post("/auth/password/forgot", json=body)
    assert r.status_code == 429
    assert r.headers.get("Retry-After")
    ratelimit.reset_for_test()


async def test_spoofed_forwarded_for_is_ignored_by_default(client, monkeypatch):
    """Security fix: with no trusted proxies configured, X-Forwarded-For is NOT trusted,
    so an attacker rotating a fake XFF per request can't mint fresh buckets — the real
    peer stays throttled."""
    monkeypatch.setenv("CEREBROZEN_RATE_LIMIT", "true")
    monkeypatch.setenv("CEREBROZEN_AUTH_RL_MAX", "1")
    ratelimit.reset_for_test()
    body = {"email": "a@example.com"}
    r1 = await client.post("/auth/password/forgot", json=body, headers={"X-Forwarded-For": "1.1.1.1"})
    assert r1.status_code == 200
    # A different spoofed IP, same real peer → still throttled.
    r2 = await client.post("/auth/password/forgot", json=body, headers={"X-Forwarded-For": "9.9.9.9"})
    assert r2.status_code == 429
    ratelimit.reset_for_test()


async def test_each_ip_has_its_own_bucket(client, monkeypatch):
    monkeypatch.setenv("CEREBROZEN_RATE_LIMIT", "true")
    monkeypatch.setenv("CEREBROZEN_AUTH_RL_MAX", "1")
    # With one trusted proxy in front, XFF IS consulted (the real client is its last hop).
    monkeypatch.setenv("CEREBROZEN_TRUSTED_PROXIES", "1")
    ratelimit.reset_for_test()
    body = {"email": "a@example.com"}
    one = {"X-Forwarded-For": "1.1.1.1"}
    two = {"X-Forwarded-For": "2.2.2.2"}
    assert (await client.post("/auth/password/forgot", json=body, headers=one)).status_code == 200
    # a different IP gets its own allowance
    assert (await client.post("/auth/password/forgot", json=body, headers=two)).status_code == 200
    # the first IP is now over its cap
    assert (await client.post("/auth/password/forgot", json=body, headers=one)).status_code == 429
    ratelimit.reset_for_test()


async def test_zero_max_means_no_limit(client, monkeypatch):
    monkeypatch.setenv("CEREBROZEN_RATE_LIMIT", "true")
    monkeypatch.setenv("CEREBROZEN_AUTH_RL_MAX", "0")  # <=0 disables the cap
    ratelimit.reset_for_test()
    for _ in range(5):
        r = await client.post("/auth/login", data={"username": "x@x.com", "password": "nope"})
        assert r.status_code == 401  # limiter passes through; login just rejects the creds
    ratelimit.reset_for_test()
