"""One account must not be able to buy its way past a limit with new addresses.

`test_ratelimit_key.py` pins the *address* key: a caller cannot choose their own
bucket with a forged `X-Forwarded-For`. This file pins the second key added for
WC-89, and the reason it was needed is that the first one was never enough.

Every per-minute cap in this product used to count addresses only, and addresses
are cheap. A mobile connection hands out a new one on request; a VPN sells a
list; a residential proxy pool rents thousands by the hour. So one signed-in
account could hold every cap at arm's length — including the ones guarding
endpoints that spend real money per call, LLM plan generation and STT and TTS —
and no counter anywhere would move. The traffic looks like a thousand ordinary
users having one conversation each.

The account key is stacked *beneath* the address key rather than replacing it,
because the two catch different abusers: one account from many addresses, and
many accounts from one address (a signup farm behind a single host). Neither
bound implies the other, which is why both are applied.
"""

from __future__ import annotations

import uuid
from datetime import timedelta

import pytest
from fastapi import FastAPI, Request
from httpx import ASGITransport, AsyncClient
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware

from app.core.ratelimit import account_key, account_limit, client_ip, limiter
from app.core.security import ACCESS, REFRESH, _create_token, create_access_token

PEER = "203.0.113.7"


class _Request:
    """The three attributes the two key functions actually touch."""

    def __init__(self, *, token: str | None = None, forwarded: str | None = None,
                 peer: str = PEER):
        self.headers = {}
        if token is not None:
            self.headers["authorization"] = f"Bearer {token}"
        if forwarded is not None:
            self.headers["x-forwarded-for"] = forwarded
        self.client = type("C", (), {"host": peer})()


@pytest.fixture(autouse=True)
def _one_proxy(monkeypatch):
    """The `deploy/Caddyfile` topology: exactly one proxy in front of the API."""
    from app.core import ratelimit

    monkeypatch.setattr(ratelimit.settings, "trusted_proxy_hops", 1)


# ── The key itself ───────────────────────────────────────────────────────
class TestTheAccountKey:
    def test_the_same_account_keys_the_same_from_any_address(self):
        """The whole point of the item, stated as one assertion.

        Two requests from addresses with nothing in common, carrying the same
        session: one bucket. Rotating where you call from buys nothing.
        """
        token = create_access_token(str(uuid.uuid4()))
        from_home = _Request(token=token, forwarded="198.51.100.4", peer="198.51.100.4")
        from_a_proxy = _Request(token=token, forwarded="203.0.113.99", peer="203.0.113.99")

        assert account_key(from_home) == account_key(from_a_proxy)
        # ...while the address key, on its own, sees two unrelated callers.
        assert client_ip(from_home) != client_ip(from_a_proxy)

    def test_two_accounts_behind_one_address_key_apart(self):
        """The other half: the account key must not merge unrelated people.

        A university NAT or a carrier's CGNAT puts thousands of strangers behind
        one address. If the account key collapsed them together, the tighter
        bound would fall on whoever happened to type first.
        """
        first = _Request(token=create_access_token(str(uuid.uuid4())))
        second = _Request(token=create_access_token(str(uuid.uuid4())))
        assert account_key(first) != account_key(second)
        assert client_ip(first) == client_ip(second)

    def test_a_token_signed_by_somebody_else_cannot_mint_a_bucket(self):
        """The forgery, which is the same shape as the X-Forwarded-For bug.

        If the subject were read without verifying the signature, a caller could
        put any string in it and get a fresh, empty bucket per request — exactly
        what the spoofed forwarded header used to do, through a different header,
        and looking just as healthy: a wall of 200s.
        """
        from jose import jwt

        forged = jwt.encode(
            {"sub": str(uuid.uuid4()), "type": ACCESS}, "not-the-secret", algorithm="HS256"
        )
        assert account_key(_Request(token=forged)) == PEER

    def test_the_wrong_kind_of_token_falls_back_to_the_address(self):
        """A refresh token is a real credential of the wrong type.

        Accepting one here would let a caller hold two buckets per account —
        one keyed on the access token and one on the refresh token — and double
        every ceiling in this file.
        """
        refresh = _create_token(str(uuid.uuid4()), REFRESH, timedelta(days=1))
        assert account_key(_Request(token=refresh)) == PEER

    @pytest.mark.parametrize(
        "token", [None, "", "not-a-jwt", "   "], ids=["absent", "empty", "garbage", "blank"]
    )
    def test_anything_unusable_falls_back_to_the_address(self, token):
        """Signed out, or holding a token that expired mid-flight.

        Falling back rather than raising matters: the request is about to 401,
        and until it does it must still count against *something*. A key
        function that raised would take the endpoint down instead of limiting
        it.
        """
        assert account_key(_Request(token=token)) == PEER

    def test_an_expired_session_still_counts_against_the_address(self):
        expired = _create_token(str(uuid.uuid4()), ACCESS, timedelta(minutes=-5))
        assert account_key(_Request(token=expired)) == PEER


# ── The limits, actually firing ──────────────────────────────────────────
def _probe_app() -> FastAPI:
    """A minimal app carrying the same pair of decorators the real routes use."""
    app = FastAPI()
    app.state.limiter = limiter
    app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)
    app.add_middleware(SlowAPIMiddleware)

    @app.get("/probe")
    @limiter.limit("3/minute")
    @account_limit("3/minute")
    async def probe(request: Request):  # noqa: ARG001 — slowapi reads it
        return {"ok": True}

    return app


#: Built once on purpose. slowapi keys its registry on the endpoint's qualified
#: name, so building this per test would stack another identical pair of limits
#: onto the same name — and since every registration decrements the same bucket,
#: a single request would count once per registration. That failure reads as
#: "the limit is far too tight" rather than as a test bug, which is how it was
#: found: [200, 429, 429, 429] where [200, 200, 200, 429] was expected.
_PROBE_APP = _probe_app()


@pytest.fixture
def live_limiter():
    """The limiter is off under TESTING=1; these tests are the reason it exists.

    Restored in a finally so an assertion failure cannot leave it on and start
    429-ing the rest of the suite, which shares this process.
    """
    was = limiter.enabled
    limiter.enabled = True
    limiter.reset()
    try:
        yield
    finally:
        limiter.reset()
        limiter.enabled = was


async def _hit(app, *, token: str | None, ip: str) -> int:
    headers = {"x-forwarded-for": ip}
    if token:
        headers["authorization"] = f"Bearer {token}"
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        return (await c.get("/probe", headers=headers)).status_code


class TestBothBoundsApply:
    @pytest.mark.asyncio
    async def test_rotating_addresses_does_not_get_a_fourth_call(self, live_limiter):
        """The attack the item was written about, run end to end.

        Three calls from three unrelated addresses, all on one session. Under
        address-only limiting every one of them is the first request from a new
        caller and the cap never fires. The fourth must be refused.
        """
        app = _PROBE_APP
        token = create_access_token(str(uuid.uuid4()))
        for i in range(3):
            assert await _hit(app, token=token, ip=f"198.51.100.{i}") == 200

        assert await _hit(app, token=token, ip="198.51.100.200") == 429

    @pytest.mark.asyncio
    async def test_rotating_accounts_does_not_get_a_fourth_call_either(self, live_limiter):
        """The address bound is still there — the account key did not replace it.

        Three fresh accounts from one host, which is what a signup farm looks
        like. Each has an empty account bucket, so only the address bound can
        refuse the fourth.
        """
        app = _PROBE_APP
        for i in range(3):
            fresh = create_access_token(str(uuid.uuid4()))
            assert await _hit(app, token=fresh, ip="203.0.113.50") == 200

        assert await _hit(app, token=create_access_token(str(uuid.uuid4())),
                          ip="203.0.113.50") == 429

    @pytest.mark.asyncio
    async def test_an_ordinary_caller_is_not_charged_twice(self, live_limiter):
        """Two bounds must not halve the ceiling for somebody using one device.

        The account and address buckets fill in step for a normal session, so
        the effective limit is 3 — not 3 shared between them, and not 1.5.
        """
        app = _PROBE_APP
        token = create_access_token(str(uuid.uuid4()))
        codes = [await _hit(app, token=token, ip=PEER) for _ in range(4)]
        assert codes == [200, 200, 200, 429]


class TestEveryCostlyRouteCarriesBoth:
    """The wiring, asserted against the real app rather than trusted.

    A decorator is easy to drop in a refactor and nothing fails when one goes:
    the endpoint keeps working, faster. So the contract is named here, endpoint
    by endpoint, with what each call actually spends.
    """

    #: qualified endpoint name → what one call costs somebody.
    COSTLY = {
        "app.api.routes.chat.send_message": "an LLM completion",
        "app.api.routes.plans.regenerate_plan": "a ~900-token LLM plan",
        "app.api.routes.habits.decompose_goal": "a ~900-token LLM plan",
        "app.api.routes.assessment.topics": "LLM-backed generation",
        "app.api.routes.voice.speech_to_text": "an STT call",
        "app.api.routes.voice.text_to_speech": "several TTS calls",
        "app.api.routes.admin.narrate_content": "real TTS credits",
        "app.api.routes.auth.request_verification": "an outbound email",
    }

    def test_each_one_is_bounded_by_account_and_by_address(self):
        import app.main  # noqa: F401 — importing registers every route's limits

        registered = limiter._route_limits
        missing = []
        for endpoint, cost in self.COSTLY.items():
            limits = registered.get(endpoint)
            if not limits:
                missing.append(f"{endpoint}: no rate limit at all — one call is {cost}")
                continue
            keys = {limit.key_func.__name__ for limit in limits}
            if "account_key" not in keys:
                missing.append(
                    f"{endpoint}: address-limited only, so one account can spend "
                    f"{cost} without bound by changing where it calls from"
                )
            if "client_ip" not in keys:
                missing.append(f"{endpoint}: lost its address limit")

        assert not missing, "\n".join(missing)

    def test_the_oracle_turns_are_bounded_too(self):
        """Named separately because both live under one module and are easy to
        half-cover — a limit on the streaming turn and not the plain one bounds
        nothing."""
        import app.main  # noqa: F401

        oracle = {
            name: limits
            for name, limits in limiter._route_limits.items()
            if name.startswith("app.api.routes.oracle.")
        }
        assert oracle, "the oracle routes carry no limits at all"
        for name, limits in oracle.items():
            keys = {limit.key_func.__name__ for limit in limits}
            assert "account_key" in keys, f"{name} is address-limited only"
