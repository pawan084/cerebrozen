"""Free-tier (B2C) coaching cap: the engine enforces the platform's signed `plan`
claim on the turn endpoint, so the paywall can't be bypassed by calling the API
directly. Only the FREE plan is capped; plus/enterprise and auth-off dev pass.

Unit-level against app.ratelimit's seams (no HTTP/auth harness needed) so every
branch of the new logic is exercised under the 96%-branch gate."""

import pytest

from app import ratelimit
from app.auth.dependencies import require_plus


def test_premium_route_blocks_a_free_token_end_to_end(monkeypatch):
    """The actual fix: a valid FREE-plan token is rejected at a Plus-only route (402) —
    server-side, not just in the app UI — while a plus token gets past the gate."""
    import jwt
    from fastapi.testclient import TestClient

    from app import config
    from app.main import create_app

    monkeypatch.setenv("AUTH_DEV_BYPASS", "")
    monkeypatch.setattr(config, "ENV", "production")
    monkeypatch.setattr(config, "JWT_SECRET", "s3cret")
    client = TestClient(create_app(), raise_server_exceptions=False)

    def tok(plan):
        raw = jwt.encode(
            {"user": {"username": "u1"}, "org_id": "default", "plan": plan},
            "s3cret", algorithm=config.JWT_ALGORITHM,
        )
        return {"Authorization": f"Bearer {raw}"}

    for path in ("/v1/wellness/patterns", "/v1/wellness/insights/weekly", "/v1/wellness/sleep"):
        assert client.get(path, headers=tok("free")).status_code == 402, path
        assert client.get(path, headers=tok("plus")).status_code != 402, path


def test_require_plus_blocks_the_free_plan():
    with pytest.raises(Exception) as ei:
        require_plus({"plan": "free"})
    assert getattr(ei.value, "status_code", None) == 402


def test_require_plus_allows_paid_plans_and_absent_claims():
    # plus / enterprise pass; an absent plan (auth off / non-consumer token) also passes.
    assert require_plus({"plan": "plus"}) == {"plan": "plus"}
    assert require_plus({"plan": "enterprise"}) == {"plan": "enterprise"}
    assert require_plus({}) == {}
    assert require_plus({"sub": "u1"}) == {"sub": "u1"}


class _Req:
    def __init__(self, auth="Bearer x"):
        self.headers = {"Authorization": auth} if auth else {}
        self.client = None


def test_plan_from_request_is_none_when_auth_is_off(monkeypatch):
    monkeypatch.setattr("app.auth.dependencies.auth_enabled", lambda: False)
    assert ratelimit._plan_from_request(_Req()) is None


def test_plan_from_request_reads_the_claim_when_auth_is_on(monkeypatch):
    monkeypatch.setattr("app.auth.dependencies.auth_enabled", lambda: True)
    monkeypatch.setattr("app.auth.jwt_validator.extract_bearer", lambda h: "tok")
    monkeypatch.setattr("app.auth.jwt_validator.decode_token", lambda t: {"plan": "free"})
    assert ratelimit._plan_from_request(_Req()) == "free"


def test_plan_from_request_returns_none_for_a_missing_claim(monkeypatch):
    monkeypatch.setattr("app.auth.dependencies.auth_enabled", lambda: True)
    monkeypatch.setattr("app.auth.jwt_validator.extract_bearer", lambda h: "tok")
    monkeypatch.setattr("app.auth.jwt_validator.decode_token", lambda t: {"sub": "u1"})
    assert ratelimit._plan_from_request(_Req()) is None


def test_plan_from_request_swallows_a_bad_token(monkeypatch):
    monkeypatch.setattr("app.auth.dependencies.auth_enabled", lambda: True)
    monkeypatch.setattr("app.auth.jwt_validator.extract_bearer", lambda h: "tok")

    def boom(_):
        raise ValueError("bad token")

    monkeypatch.setattr("app.auth.jwt_validator.decode_token", boom)
    assert ratelimit._plan_from_request(_Req()) is None


async def test_free_plan_allows_under_the_cap(monkeypatch):
    monkeypatch.setattr(ratelimit, "_enabled", lambda: True)
    monkeypatch.setattr(ratelimit, "_caller_id", lambda r: "sub:u1")
    monkeypatch.setattr(ratelimit, "_plan_from_request", lambda r: "free")
    monkeypatch.setattr(ratelimit, "_hit", lambda *a, **k: None)  # under the cap
    await ratelimit.limit_free_daily_turns(_Req())  # must not raise


async def test_free_plan_is_blocked_over_the_cap(monkeypatch):
    monkeypatch.setattr(ratelimit, "_enabled", lambda: True)
    monkeypatch.setattr(ratelimit, "_caller_id", lambda r: "sub:u1")
    monkeypatch.setattr(ratelimit, "_plan_from_request", lambda r: "free")
    monkeypatch.setattr(ratelimit, "_hit", lambda *a, **k: 3600)  # over the cap
    with pytest.raises(Exception) as ei:
        await ratelimit.limit_free_daily_turns(_Req())
    assert getattr(ei.value, "status_code", None) == 429
    assert ei.value.headers.get("Retry-After") == "3600"


async def test_paid_plans_are_never_counted(monkeypatch):
    monkeypatch.setattr(ratelimit, "_enabled", lambda: True)
    monkeypatch.setattr(ratelimit, "_plan_from_request", lambda r: "plus")
    seen = {"hit": False}

    def _hit(*a, **k):
        seen["hit"] = True
        return 3600

    monkeypatch.setattr(ratelimit, "_hit", _hit)
    await ratelimit.limit_free_daily_turns(_Req())  # returns before touching the counter
    assert not seen["hit"], "a paid plan must never hit the daily counter"


async def test_cap_is_a_no_op_when_disabled(monkeypatch):
    monkeypatch.setattr(ratelimit, "_enabled", lambda: False)
    monkeypatch.setattr(ratelimit, "_plan_from_request", lambda r: "free")
    await ratelimit.limit_free_daily_turns(_Req())  # disabled → no raise even for free


async def test_cap_is_a_no_op_when_limit_is_zero(monkeypatch):
    monkeypatch.setattr(ratelimit, "_enabled", lambda: True)
    monkeypatch.setattr(ratelimit, "FREE_TURN_DAILY_LIMIT", 0)
    monkeypatch.setattr(ratelimit, "_plan_from_request", lambda r: "free")
    await ratelimit.limit_free_daily_turns(_Req())  # limit<=0 means "no cap"
