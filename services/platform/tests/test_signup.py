"""Consumer self-serve signup (B2C): POST /auth/signup mints a personal
org-of-one and its sole member. These lock the contract the Android/iOS clients
already expect (a synchronous access/refresh token pair) and the invariants that
keep a solo account safe and distinguishable from a real customer tenant."""

from sqlalchemy import select

from app.db import SessionLocal
from app.models import Org, User, is_personal_org
from app.security import decode_access_token

_PW = "hunter2hunter2"


async def test_signup_creates_an_account_that_can_log_in(client):
    r = await client.post(
        "/auth/signup",
        json={"email": "solo@example.com", "password": _PW, "name": "Solo"},
    )
    assert r.status_code == 201, r.text
    tokens = r.json()
    assert tokens["access_token"] and tokens["refresh_token"]
    claims = decode_access_token(tokens["access_token"])
    assert claims["role"] == "user"
    assert claims["plan"] == "free"  # a fresh personal account is free until it buys Plus
    # A real, engine-usable tenancy — never the internal-staff sentinel.
    assert claims["org_id"] and claims["org_id"] != "internal"
    # And the credentials actually work against /login.
    r2 = await client.post(
        "/auth/login", data={"username": "solo@example.com", "password": _PW}
    )
    assert r2.status_code == 200, r2.text


async def test_signup_mints_a_personal_org_of_one_with_safety_defaults(client):
    r = await client.post(
        "/auth/signup",
        json={"email": "owner@example.com", "password": _PW, "name": "Owner"},
    )
    assert r.status_code == 201, r.text
    org_id = decode_access_token(r.json()["access_token"])["org_id"]
    async with SessionLocal() as s:
        org = (await s.execute(select(Org).where(Org.id == org_id))).scalar_one()
        assert org.seats_total == 1
        assert is_personal_org(org)  # marked personal via slug prefix, no schema change
        assert org.regulated_mode is True  # safety-as-code default carries over
        members = (
            await s.execute(select(User).where(User.org_id == org_id))
        ).scalars().all()
        assert len(members) == 1 and members[0].email == "owner@example.com"


async def test_signup_names_the_workspace_for_a_human_not_the_email(client):
    """#42: a personal workspace gets a name the person recognises — '<Name>'s space' with
    a name, and a warm generic (never '<email-local>'s space') without one."""
    async def _org_name(email, name):
        r = await client.post(
            "/auth/signup", json={"email": email, "password": _PW, "name": name}
        )
        assert r.status_code == 201, r.text
        org_id = decode_access_token(r.json()["access_token"])["org_id"]
        async with SessionLocal() as s:
            return (await s.execute(select(Org).where(Org.id == org_id))).scalar_one().name

    assert await _org_name("nova@example.com", "Nova") == "Nova's space"
    generic = await _org_name("free1784@example.com", "")
    assert "free1784" not in generic and generic == "My CereBro space"


async def test_signup_starts_with_no_consent(client):
    """Consent is an act, not an inheritance — a fresh account has all six off."""
    r = await client.post(
        "/auth/signup",
        json={"email": "fresh@example.com", "password": _PW, "name": "F"},
    )
    claims = decode_access_token(r.json()["access_token"])
    assert claims["consent"] == {
        "mood_history": False,
        "ai_memory": False,
        "journal_memory": False,
        "sleep_history": False,
        "voice_storage": False,
        "model_training": False,
    }


async def test_signup_normalizes_email(client):
    r = await client.post(
        "/auth/signup",
        json={"email": "  MixedCase@Example.COM ", "password": _PW, "name": "M"},
    )
    assert r.status_code == 201, r.text
    r2 = await client.post(
        "/auth/login", data={"username": "mixedcase@example.com", "password": _PW}
    )
    assert r2.status_code == 200, r2.text


async def test_signup_rejects_a_duplicate_email(client):
    body = {"email": "dupe@example.com", "password": _PW, "name": "D"}
    assert (await client.post("/auth/signup", json=body)).status_code == 201
    assert (await client.post("/auth/signup", json=body)).status_code == 409


async def test_signup_rejects_a_short_password(client):
    r = await client.post(
        "/auth/signup",
        json={"email": "x@example.com", "password": "short", "name": "X"},
    )
    assert r.status_code == 400


async def test_signup_rejects_a_malformed_email(client):
    for bad in ("notanemail", "no@domain", "@example.com", "spaces in@x.com"):
        r = await client.post(
            "/auth/signup", json={"email": bad, "password": _PW, "name": "X"}
        )
        assert r.status_code == 400, f"{bad!r} should be rejected, got {r.status_code}"


async def test_two_signups_get_separate_orgs(client):
    a = await client.post(
        "/auth/signup", json={"email": "a@example.com", "password": _PW, "name": "A"}
    )
    b = await client.post(
        "/auth/signup", json={"email": "b@example.com", "password": _PW, "name": "B"}
    )
    org_a = decode_access_token(a.json()["access_token"])["org_id"]
    org_b = decode_access_token(b.json()["access_token"])["org_id"]
    assert org_a != org_b  # no shared tenancy between strangers
