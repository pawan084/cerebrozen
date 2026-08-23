"""Bot protection on the public write endpoints (WC-90).

Two layers with opposite failure modes, so the tests are organised by which
failure they pin rather than by which function they call:

* the throwaway-address check, which must not refuse people it was never aimed
  at — and the person it would most easily refuse by accident is a Sign in with
  Apple user, whose address genuinely looks like a burner;
* the challenge, which must be inert without a key, decisive with one, and must
  tell "the provider says no" apart from "the provider is down".

The second distinction is the one worth having tests for. Collapsing those two
into a single boolean is how a defence becomes an outage: a Cloudflare incident
would stop every new account in a mental-health product, including somebody
signing up at a bad moment.
"""

from __future__ import annotations

import uuid

import httpx
import pytest
from fastapi import HTTPException

from app.services import botcheck


def _configured(monkeypatch, *, secret: str = "test-secret", provider: str = "turnstile"):
    monkeypatch.setattr(botcheck.settings, "bot_challenge_secret", secret, raising=False)
    monkeypatch.setattr(botcheck.settings, "bot_challenge_provider", provider, raising=False)


class _Reply:
    """The three bits of an httpx response `verify_challenge` touches."""

    def __init__(self, payload, status_code: int = 200):
        self._payload = payload
        self.status_code = status_code

    def raise_for_status(self):
        if self.status_code >= 400:
            raise httpx.HTTPStatusError("boom", request=None, response=None)

    def json(self):
        if isinstance(self._payload, Exception):
            raise self._payload
        return self._payload


def _provider_answers(monkeypatch, payload, status_code: int = 200):
    """Stand in for the provider without a network call — CI runs with no keys."""
    sent = {}

    class _Client:
        def __init__(self, *a, **kw):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *a):
            return False

        async def post(self, url, data=None):
            sent["url"] = url
            sent["data"] = data
            return _Reply(payload, status_code)

    monkeypatch.setattr(botcheck.httpx, "AsyncClient", _Client)
    return sent


# ── The throwaway check ──────────────────────────────────────────────────
class TestTheThrowawayCheck:
    @pytest.mark.parametrize(
        "email",
        ["a@mailinator.com", "b@YOPMAIL.COM", "c@guerrillamail.com", "d@10minutemail.com"],
    )
    def test_a_service_that_exists_to_be_discarded_is_refused(self, email):
        assert botcheck.is_throwaway(email) is True

    @pytest.mark.parametrize(
        "email",
        [
            "someone@privaterelay.appleid.com",
            "someone@icloud.com",
            "someone@duck.com",
            "someone@mozmail.com",
        ],
    )
    def test_a_privacy_relay_is_never_refused(self, email):
        """The trap this whole check has to avoid.

        `privaterelay.appleid.com` is what Sign in with Apple hands us when
        somebody picks "Hide My Email" — a sign-in path this product supports.
        A blocklist written from "looks like a burner" would refuse exactly the
        users who care most about privacy, which is who this product is for.
        """
        assert botcheck.is_throwaway(email) is False

    def test_a_relay_wins_even_if_it_lands_on_both_lists(self, monkeypatch):
        """Order is a guarantee, not a coincidence.

        Somebody adding a domain to the blocklist without noticing it is also a
        relay must not be able to lock those accounts out. The relay set is
        consulted first, so the mistake is inert.
        """
        monkeypatch.setattr(
            botcheck, "_THROWAWAY", frozenset(botcheck._THROWAWAY | {"privaterelay.appleid.com"})
        )
        assert botcheck.is_throwaway("someone@privaterelay.appleid.com") is False

    @pytest.mark.parametrize(
        "email", ["someone@gmail.com", "person@a-company.co.in", "x@outlook.com"]
    )
    def test_ordinary_addresses_pass(self, email):
        assert botcheck.is_throwaway(email) is False

    @pytest.mark.parametrize("email", ["", "no-at-sign", "@", "trailing@"])
    def test_a_malformed_address_is_not_treated_as_a_burner(self, email):
        """Pydantic's EmailStr rejects these long before this code sees them.

        Returning False rather than raising matters anyway: this function must
        never be the thing that 500s a signup, and "I cannot tell" is not
        evidence of a burner.
        """
        assert botcheck.is_throwaway(email) is False


# ── The challenge ────────────────────────────────────────────────────────
class TestTheChallengeDegradesWithoutAKey:
    @pytest.mark.asyncio
    async def test_no_secret_means_nobody_is_refused(self, monkeypatch):
        """The project rule: everything degrades without keys.

        An unconfigured seam that refused callers would take signup down the
        moment this file shipped.
        """
        monkeypatch.setattr(botcheck.settings, "bot_challenge_secret", "", raising=False)
        assert await botcheck.verify_challenge(None) is True
        assert await botcheck.verify_challenge("anything") is True

    @pytest.mark.asyncio
    async def test_no_secret_means_no_outbound_request_either(self, monkeypatch):
        monkeypatch.setattr(botcheck.settings, "bot_challenge_secret", "", raising=False)

        def _explode(*a, **kw):
            raise AssertionError("called the provider with nothing configured")

        monkeypatch.setattr(botcheck.httpx, "AsyncClient", _explode)
        assert await botcheck.verify_challenge("token") is True


class TestTheChallengeWithAKey:
    @pytest.mark.asyncio
    async def test_a_good_token_passes_and_the_address_is_sent_along(self, monkeypatch):
        _configured(monkeypatch)
        sent = _provider_answers(monkeypatch, {"success": True})

        assert await botcheck.verify_challenge("good-token", "198.51.100.4") is True
        assert sent["url"] == botcheck.VERIFY_URLS["turnstile"]
        assert sent["data"]["response"] == "good-token"
        assert sent["data"]["secret"] == "test-secret"
        assert sent["data"]["remoteip"] == "198.51.100.4"

    @pytest.mark.asyncio
    async def test_a_rejected_token_is_refused(self, monkeypatch):
        _configured(monkeypatch)
        _provider_answers(monkeypatch, {"success": False, "error-codes": ["invalid-input-response"]})
        assert await botcheck.verify_challenge("forged") is False

    @pytest.mark.asyncio
    async def test_a_missing_token_is_a_verdict_once_configured(self, monkeypatch):
        """Configured and the client sent nothing: either an un-updated client
        or something that never loaded the widget. Both are refusals."""
        _configured(monkeypatch)
        assert await botcheck.verify_challenge(None) is False
        assert await botcheck.verify_challenge("") is False

    @pytest.mark.asyncio
    async def test_hcaptcha_is_asked_at_its_own_address(self, monkeypatch):
        _configured(monkeypatch, provider="hcaptcha")
        sent = _provider_answers(monkeypatch, {"success": True})
        assert await botcheck.verify_challenge("t") is True
        assert sent["url"] == botcheck.VERIFY_URLS["hcaptcha"]

    @pytest.mark.asyncio
    async def test_a_provider_nobody_recognises_does_not_refuse_everyone(self, monkeypatch):
        """A typo in one env var must not be able to close registration."""
        _configured(monkeypatch, provider="captcha-9000")
        assert await botcheck.verify_challenge("t") is True


class TestDownIsNotTheSameAsNo:
    """The distinction the module was written around.

    A provider that says "invalid" has decided something. A provider that
    times out has decided nothing, and treating silence as a refusal turns
    somebody else's outage into ours.
    """

    @pytest.mark.asyncio
    @pytest.mark.parametrize(
        "failure",
        [httpx.ConnectTimeout("timeout"), httpx.ReadTimeout("slow"), httpx.ConnectError("refused")],
        ids=["connect-timeout", "read-timeout", "connection-refused"],
    )
    async def test_an_unreachable_provider_lets_the_signup_through(self, monkeypatch, failure):
        _configured(monkeypatch)

        class _Client:
            def __init__(self, *a, **kw):
                pass

            async def __aenter__(self):
                return self

            async def __aexit__(self, *a):
                return False

            async def post(self, url, data=None):
                raise failure

        monkeypatch.setattr(botcheck.httpx, "AsyncClient", _Client)
        assert await botcheck.verify_challenge("t") is True

    @pytest.mark.asyncio
    async def test_a_5xx_from_the_provider_lets_the_signup_through(self, monkeypatch):
        _configured(monkeypatch)
        _provider_answers(monkeypatch, {"success": False}, status_code=503)
        assert await botcheck.verify_challenge("t") is True

    @pytest.mark.asyncio
    async def test_a_reply_that_is_not_json_lets_the_signup_through(self, monkeypatch):
        """An HTML error page from a proxy is an outage wearing a 200."""
        _configured(monkeypatch)
        _provider_answers(monkeypatch, ValueError("not json"))
        assert await botcheck.verify_challenge("t") is True


# ── The guard the routes actually call ───────────────────────────────────
class TestTheGuard:
    @pytest.mark.asyncio
    async def test_a_throwaway_address_is_refused_with_a_code_clients_can_read(self):
        with pytest.raises(HTTPException) as info:
            await botcheck.guard("bot@mailinator.com", None)
        assert info.value.status_code == 400
        assert info.value.detail["code"] == botcheck.THROWAWAY_EMAIL_CODE

    @pytest.mark.asyncio
    async def test_a_failed_challenge_is_a_different_code(self, monkeypatch):
        """Two refusals, two remedies: one re-renders the widget, the other
        focuses the email field. A client cannot pick without being told."""
        _configured(monkeypatch)
        _provider_answers(monkeypatch, {"success": False})
        with pytest.raises(HTTPException) as info:
            await botcheck.guard("real@gmail.com", "forged")
        assert info.value.detail["code"] == botcheck.CHALLENGE_FAILED_CODE
        assert botcheck.THROWAWAY_EMAIL_CODE != botcheck.CHALLENGE_FAILED_CODE

    @pytest.mark.asyncio
    async def test_a_throwaway_address_costs_no_call_to_the_paid_provider(self, monkeypatch):
        _configured(monkeypatch)

        def _explode(*a, **kw):
            raise AssertionError("asked the provider about an address we already refused")

        monkeypatch.setattr(botcheck.httpx, "AsyncClient", _explode)
        with pytest.raises(HTTPException):
            await botcheck.guard("bot@mailinator.com", "token")

    @pytest.mark.asyncio
    async def test_an_ordinary_signup_passes_untouched_with_nothing_configured(self):
        await botcheck.guard("person@gmail.com", None)  # must not raise


# ── The endpoints ────────────────────────────────────────────────────────
class TestTheEndpointsAreGuarded:
    @pytest.mark.asyncio
    async def test_signup_refuses_a_throwaway_address(self, client):
        r = await client.post(
            "/auth/signup",
            json={"email": f"bot-{uuid.uuid4().hex[:8]}@mailinator.com",
                  "password": "password123", "name": "Bot"},
        )
        assert r.status_code == 400, r.text
        assert r.json()["detail"]["code"] == botcheck.THROWAWAY_EMAIL_CODE

    @pytest.mark.asyncio
    async def test_signup_still_works_for_everybody_else(self, client):
        r = await client.post(
            "/auth/signup",
            json={"email": f"person-{uuid.uuid4().hex[:8]}@test.app",
                  "password": "password123", "name": "Person"},
        )
        assert r.status_code == 201, r.text

    @pytest.mark.asyncio
    async def test_a_refused_challenge_does_not_reveal_whether_the_address_exists(
        self, client, monkeypatch
    ):
        """The guard must run BEFORE the existence check, or signup leaks.

        With a challenge configured, a bot that sends a deliberately bad token
        learns from 409-vs-400 whether an address is registered — the same
        membership oracle `/auth/otp/request` and `/auth/password/forgot` go out
        of their way to avoid, on the endpoint that is easiest to script.

        Written this way after the first version of this test proved vacuous: it
        used a throwaway address, and a throwaway address can never BE
        registered, so both orderings answered 400 and a mutation that moved the
        guard after the existence check survived. The leak needs a real address
        and a live challenge.
        """
        address = f"taken-{uuid.uuid4().hex[:8]}@test.app"
        created = await client.post(
            "/auth/signup",
            json={"email": address, "password": "password123", "name": "A"},
        )
        assert created.status_code == 201, created.text

        _configured(monkeypatch)
        _provider_answers(monkeypatch, {"success": False})

        registered = await client.post(
            "/auth/signup",
            json={"email": address, "password": "password123", "name": "A",
                  "challenge_token": "bad"},
        )
        fresh = await client.post(
            "/auth/signup",
            json={"email": f"new-{uuid.uuid4().hex[:8]}@test.app",
                  "password": "password123", "name": "A", "challenge_token": "bad"},
        )

        assert registered.status_code == fresh.status_code == 400, (
            f"registered={registered.status_code} fresh={fresh.status_code} — "
            "a 409 here tells a bot the address is taken"
        )
        assert registered.json() == fresh.json()

    @pytest.mark.asyncio
    async def test_the_waitlist_is_guarded_too(self, client):
        r = await client.post(
            "/waitlist",
            json={"email": f"bot-{uuid.uuid4().hex[:8]}@guerrillamail.com", "source": "landing"},
        )
        assert r.status_code == 400, r.text
        assert r.json()["detail"]["code"] == botcheck.THROWAWAY_EMAIL_CODE

    @pytest.mark.asyncio
    async def test_an_older_client_that_sends_no_token_still_works(self, client):
        """The field is optional on purpose: it ships ahead of the vendor
        account, so every already-installed app keeps working until the day a
        secret is configured."""
        r = await client.post(
            "/waitlist", json={"email": f"ok-{uuid.uuid4().hex[:8]}@test.app"}
        )
        assert r.status_code == 201, r.text
