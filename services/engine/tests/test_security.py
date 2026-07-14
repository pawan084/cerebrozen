"""Security gates: the dev auth bypass, and the rate limit on the paid endpoints.

Both of these protect against a *configuration* mistake rather than a coding one, which
is precisely why they need tests: nothing in the app fails visibly when they regress. An
auth bypass that leaks into prod produces a perfectly healthy-looking service that anyone
can talk to, and a missing rate limit produces a perfectly healthy-looking service with
an unbounded bill.
"""

import pytest
from fastapi import HTTPException

from app import config
from app.auth import dependencies as auth_deps

# Every limiter test uses a long window (3600s), never the production 60s. The limiter
# uses a FIXED window, so its counter resets on the wall-clock boundary — and the HTTP
# test's first request runs a real coaching turn that takes ~20s. With a 60s window, that
# turn straddles the boundary roughly a third of the time, the counter resets, and the
# second request is legitimately allowed. The test would then fail ~37% of the time in CI
# for a reason that has nothing to do with the code. A long window makes the boundary
# unreachable inside a test, so these assertions are about the LIMIT and not about what
# second of the minute the suite happened to start.
_LONG_WINDOW = 3600


# ── the dev bypass must not be honourable in production ──────────────────────

@pytest.fixture
def env(monkeypatch):
    """Set ENV + AUTH_DEV_BYPASS together; config.ENV is read at import so patch it."""

    def _set(env_name: str, bypass: str = ""):
        monkeypatch.setattr(config, "ENV", env_name)
        monkeypatch.setattr(config, "JWT_SECRET", "s3cret")
        monkeypatch.setenv("AUTH_DEV_BYPASS", bypass)

    return _set


@pytest.mark.parametrize("dev_env", ["local", "dev", "development", "test", "ci"])
def test_bypass_works_in_development(env, dev_env):
    """The bypass exists for a reason — the tester UI calls the API with no token."""
    env(dev_env, "true")
    assert auth_deps.auth_enabled() is False


@pytest.mark.parametrize("prod_env", ["production", "prod", "qa", "staging", "uat"])
def test_bypass_is_refused_outside_development(env, prod_env, caplog):
    """THE test. A stray AUTH_DEV_BYPASS=true in a deployed env — a copied .env, an
    inherited task definition, a helm value nobody re-read — used to turn authentication
    off on every session endpoint. Silently, with a 200 on every request.

    The flag must not be able to do that. Anywhere that is not development-class, it is
    refused and auth stays ON, so a misconfiguration locks people out (loud, immediate,
    fixable) instead of letting everyone in (silent, indefinite, a breach).
    """
    env(prod_env, "true")
    assert auth_deps.auth_enabled() is True, (
        f"AUTH_DEV_BYPASS was honoured in ENV={prod_env} — auth is off in production"
    )
    assert "auth.dev_bypass_refused" in caplog.text, "a refused bypass must be discoverable"


def test_no_secret_still_only_opens_up_locally(monkeypatch):
    """The other bypass: local + no JWT_SECRET. Also must not generalise to prod."""
    monkeypatch.setenv("AUTH_DEV_BYPASS", "")
    monkeypatch.setattr(config, "JWT_SECRET", "")

    monkeypatch.setattr(config, "ENV", "local")
    assert auth_deps.auth_enabled() is False

    monkeypatch.setattr(config, "ENV", "production")
    assert auth_deps.auth_enabled() is True, "missing JWT_SECRET must not disable auth in prod"


# ── rate limiting on the endpoints that spend money ──────────────────────────

@pytest.fixture
def limiter(monkeypatch):
    """A limiter with a clean counter store and env-independent settings."""
    import fakeredis

    import app.ratelimit as rl
    import app.stores.redis_state as rs

    monkeypatch.setenv("CEREBROZEN_RATE_LIMIT", "true")
    # Isolate the counters: the module-level Redis singleton is shared across tests, and
    # a leaked count from one test silently changes another's threshold.
    monkeypatch.setattr(rs, "_client", fakeredis.FakeRedis(decode_responses=True))
    return rl


class _Req:
    """Minimal stand-in for a Starlette Request (headers + client)."""

    def __init__(self, ip="1.2.3.4", auth=None):
        self.headers = {"Authorization": auth} if auth else {}
        self.client = type("C", (), {"host": ip})()


@pytest.mark.asyncio
async def test_a_caller_over_the_limit_gets_429_with_retry_after(limiter, monkeypatch):
    monkeypatch.setitem(limiter.LIMITS, "turn", (3, 3600))
    monkeypatch.setattr(limiter, "_caller_id", lambda r: "ip:burst")
    dep = limiter._limiter("turn")

    for _ in range(3):
        await dep(_Req())  # within budget → no raise

    with pytest.raises(HTTPException) as exc:
        await dep(_Req())
    assert exc.value.status_code == 429
    assert "Retry-After" in exc.value.headers, "a 429 without Retry-After is a client-side guess"


@pytest.mark.asyncio
async def test_one_caller_cannot_exhaust_anothers_budget(limiter, monkeypatch):
    """The limit is per caller. If it were global, a single abusive client would deny
    service to every legitimate user — a rate limiter that becomes the outage."""
    monkeypatch.setitem(limiter.LIMITS, "turn", (2, 3600))
    dep = limiter._limiter("turn")

    monkeypatch.setattr(limiter, "_caller_id", lambda r: "sub:noisy")
    for _ in range(2):
        await dep(_Req())
    with pytest.raises(HTTPException):
        await dep(_Req())

    monkeypatch.setattr(limiter, "_caller_id", lambda r: "sub:quiet")
    await dep(_Req())  # unaffected by the noisy neighbour


def test_limiter_fails_open_when_the_backend_is_down(limiter, monkeypatch):
    """A Redis outage must not become a coaching outage. Failing closed here converts a
    cache problem into a total loss of service — strictly worse than an unbounded bill
    for the minutes it takes to notice. The LLM circuit breaker is the real spend cap."""
    def _boom():
        raise RuntimeError("redis down")

    monkeypatch.setattr("app.stores.redis_state.get_redis", _boom)
    assert limiter._hit("turn", "sub:x", limit=1, window_s=60) is None, (
        "a limiter backend error must allow the request through, not reject it"
    )


@pytest.mark.asyncio
async def test_an_unlimited_bucket_passes_everything(limiter, monkeypatch):
    """0 (or an unknown bucket) means off — the escape hatch for an operator who needs
    the limit gone at 3am without a deploy."""
    monkeypatch.setitem(limiter.LIMITS, "turn", (0, 3600))
    dep = limiter._limiter("turn")
    for _ in range(50):
        await dep(_Req())


def test_the_turn_endpoint_really_returns_429_over_http(limiter, monkeypatch):
    """End to end through the real route. The dependency working in isolation proves
    nothing if it isn't wired to the endpoint that spends the money — so burst the actual
    HTTP path and demand a real 429 off the wire.

    The first request's *handler* outcome is irrelevant (it may 404 on an unknown
    session): route dependencies run before the handler, which is exactly the property
    being asserted — an over-limit caller is rejected without ever reaching the LLM.
    """
    from fastapi.testclient import TestClient

    from app.main import create_app

    monkeypatch.setitem(limiter.LIMITS, "turn", (1, 3600))
    monkeypatch.setattr(limiter, "_caller_id", lambda r: "ip:http-burst")
    client = TestClient(create_app(), raise_server_exceptions=False)

    body = {"user_id": "u1", "text": "hello"}
    first = client.post("/v1/sessions/s1/turn", json=body)
    assert first.status_code != 429, "the first request was within budget"

    second = client.post("/v1/sessions/s1/turn", json=body)
    assert second.status_code == 429, "an over-limit turn reached the LLM instead of being rejected"
    assert second.headers.get("Retry-After")


def test_the_paid_endpoints_are_the_ones_that_are_limited():
    """The limiter existing is worthless if it isn't attached. Pin it to both routes that
    cost money — a turn is a paid LLM call against a ~21K-token prompt."""
    from app.main import create_app

    limited = {
        r.path
        for r in create_app().routes
        if any(
            getattr(d.call, "__module__", "") == "app.ratelimit"
            for d in getattr(getattr(r, "dependant", None), "dependencies", [])
        )
    }
    assert "/v1/sessions/{session_id}/turn" in limited, "the turn endpoint is unlimited"
    assert "/v1/sessions/start" in limited, "session start is unlimited"


def test_the_limit_key_is_not_the_client_supplied_user_id(limiter):
    """The turn body carries a `user_id`, and limiting on it would be the obvious move.
    It is also client-supplied: an attacker rotates it per request and the limit
    evaporates. The key must come from the signed token (or, with auth off, the peer)."""
    import inspect

    src = inspect.getsource(limiter._caller_id)
    assert "decode_token" in src and "sub" in src
    assert "request.json" not in src, (
        "the limiter must never key on the request body — it is attacker-controlled"
    )
