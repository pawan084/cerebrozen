"""Wave 17 pins: 500s become 4xx, bounds mirror columns, searches stay text.

Register C19-C21, C26-C28, C30-C32, C53, C86-C88 — every case here used to be
a Postgres DataError, an uncaught ValueError or a wildcard leak.
"""
import uuid
from datetime import timedelta

from tests.dates import account_day, account_iso

import pytest

from app.schemas.user import KNOWN_REGIONS
from app.services.crisis import _REGIONS


async def _signup(client, prefix="bounds"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post(
        "/auth/signup", json={"email": addr, "password": "password123", "name": "B"}
    )
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


# ── Profile bounds (C19-C21) ────────────────────────────────────────────

def test_schema_region_list_mirrors_the_crisis_directory():
    # KNOWN_REGIONS is a hand mirror of services/crisis._REGIONS (plus "");
    # this is the pin that keeps the two from drifting.
    assert KNOWN_REGIONS == set(_REGIONS.keys()) | {""}


async def test_overlong_profile_fields_are_422_not_500(auth_client):
    for body in (
        {"language": "x" * 121},
        {"companion": "x" * 61},
        {"timezone": "x" * 61},
        {"region": "x" * 9},
        {"name": "x" * 121},
    ):
        r = await auth_client.patch("/users/me", json=body)
        assert r.status_code == 422, body


async def test_unknown_timezone_is_refused_with_words(auth_client):
    r = await auth_client.patch("/users/me", json={"timezone": "Mars/Olympus_Mons"})
    assert r.status_code == 422
    assert "IANA" in r.text
    ok = await auth_client.patch("/users/me", json={"timezone": "Asia/Kolkata"})
    assert ok.status_code == 200


async def test_unknown_region_is_refused_and_case_is_canonical(auth_client):
    assert (await auth_client.patch("/users/me", json={"region": "ZZ"})).status_code == 422
    r = await auth_client.patch("/users/me", json={"region": "in"})
    assert r.status_code == 200 and r.json()["region"] == "IN"
    # "" = automatic stays allowed.
    assert (await auth_client.patch("/users/me", json={"region": ""})).status_code == 200


# ── Auth bounds (C28, C31) ──────────────────────────────────────────────

async def test_long_passphrase_is_422_not_500(client):
    # bcrypt 4.x raises above 72 bytes; 100 ASCII chars used to 500 on signup.
    r = await client.post(
        "/auth/signup",
        json={"email": f"b-{uuid.uuid4().hex[:8]}@test.app", "password": "x" * 100, "name": "B"},
    )
    assert r.status_code == 422


async def test_link_token_with_garbage_subject_is_400(client):
    from app.core.security import RESET, VERIFY, _create_token
    from datetime import timedelta as td

    bad_verify = _create_token("not-a-uuid", VERIFY, td(hours=1))
    assert (await client.post("/auth/verify", json={"token": bad_verify})).status_code == 400
    bad_reset = _create_token("not-a-uuid", RESET, td(hours=1), version=0)
    r = await client.post("/auth/password/reset", json={"token": bad_reset, "new_password": "newpassword1"})
    assert r.status_code == 400


# ── Query-param bounds (C30, C32) ───────────────────────────────────────

async def test_negative_limit_is_clamped_not_500(auth_client):
    for path in ("/moods?limit=-1", "/journal?limit=-1", "/sleep?limit=-1"):
        assert (await auth_client.get(path)).status_code == 200, path


async def test_devices_platform_is_a_closed_set(auth_client):
    assert (await auth_client.get("/users/me/devices?platform=windows")).status_code == 422
    assert (await auth_client.get("/users/me/devices?platform=ios")).status_code == 200


# ── Sleep diary bounds (C26, C27) ───────────────────────────────────────

async def test_sleep_rejects_implausible_dates(auth_client):
    base = {"bedtime": "23:00:00", "wake_time": "07:00:00", "quality": 3}
    future = (account_day(-30)).isoformat()
    ancient = (account_day(1000)).isoformat()
    assert (await auth_client.post("/sleep", json={"date": future, **base})).status_code == 422
    assert (await auth_client.post("/sleep", json={"date": ancient, **base})).status_code == 422
    # Tomorrow is allowed (client clock skew).
    tomorrow = (account_day(-1)).isoformat()
    assert (await auth_client.post("/sleep", json={"date": tomorrow, **base})).status_code in (200, 201)


async def test_sleep_rejects_a_zero_minute_night(auth_client):
    r = await auth_client.post(
        "/sleep",
        json={"date": account_iso(), "bedtime": "23:00:00", "wake_time": "23:00:00"},
    )
    assert r.status_code == 422


# ── Search stays text (C87) ─────────────────────────────────────────────

async def test_content_search_treats_wildcards_as_text(client):
    r = await client.get("/content", params={"q": "100%"})
    assert r.status_code == 200
    # The catalogue is seeded; a literal "100%" matches nothing rather than
    # everything.
    assert r.json() == []


# ── Stripe signature parsing (C86) ──────────────────────────────────────

def test_stripe_malformed_timestamp_is_a_stripe_error(monkeypatch):
    import hashlib
    import hmac as hmac_mod

    from app.core.config import settings
    from app.services import stripe_billing

    monkeypatch.setattr(settings, "stripe_webhook_secret", "whsec_test")
    payload = b"{}"
    # A valid signature over a non-numeric timestamp reaches the float() that
    # used to raise ValueError straight into a 500.
    sig = hmac_mod.new(b"whsec_test", b"abc." + payload, hashlib.sha256).hexdigest()
    with pytest.raises(stripe_billing.StripeError):
        stripe_billing.verify_webhook(payload, f"t=abc,v1={sig}")
