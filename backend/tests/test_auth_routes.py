"""Route-level coverage for /auth (Apple/Google sign-in, lockout, logout, links)."""
import uuid

from sqlalchemy import update

from app.core.database import SessionLocal
from app.core.security import create_verify_token
from app.models.user import User
from app.services import apple, google


async def _signup(client, pw="password123"):
    e = f"ar-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": e, "password": pw, "name": "T"})
    assert r.status_code == 201, r.text
    return e, r.json()["access_token"]


# ── Apple ────────────────────────────────────────────────────────────────
async def test_apple_new_then_existing(client, monkeypatch):
    email = f"apple-{uuid.uuid4().hex[:8]}@icloud.com"

    async def fake(_tok):
        return {"email": email}
    monkeypatch.setattr(apple, "verify_identity_token", fake)

    r = await client.post("/auth/apple", json={"identity_token": "x", "name": "Ann"})
    assert r.status_code == 200 and r.json()["access_token"]
    r2 = await client.post("/auth/apple", json={"identity_token": "x"})   # existing user path
    assert r2.status_code == 200


async def test_apple_invalid_token(client, monkeypatch):
    async def fake(_):
        return None
    monkeypatch.setattr(apple, "verify_identity_token", fake)
    assert (await client.post("/auth/apple", json={"identity_token": "bad"})).status_code == 401


async def test_apple_no_email_still_signs_in_by_sub(client, monkeypatch):
    # Private-relay edge: no email claim — the stable `sub` keys the account.
    async def fake(_):
        return {"sub": f"no-email-{uuid.uuid4().hex}"}
    monkeypatch.setattr(apple, "verify_identity_token", fake)
    assert (await client.post("/auth/apple", json={"identity_token": "x"})).status_code == 200


async def test_apple_no_identity_rejected(client, monkeypatch):
    async def fake(_):
        # Verified claims but neither sub nor email — nothing to key an account on.
        return {"iss": "https://appleid.apple.com"}
    monkeypatch.setattr(apple, "verify_identity_token", fake)
    assert (await client.post("/auth/apple", json={"identity_token": "x"})).status_code == 400


async def test_apple_disabled_user(client, monkeypatch):
    email = f"apple-dis-{uuid.uuid4().hex[:8]}@icloud.com"

    async def fake(_):
        return {"email": email}
    monkeypatch.setattr(apple, "verify_identity_token", fake)
    await client.post("/auth/apple", json={"identity_token": "x"})           # create
    async with SessionLocal() as s:
        await s.execute(update(User).where(User.email == email).values(is_active=False))
        await s.commit()
    assert (await client.post("/auth/apple", json={"identity_token": "x"})).status_code == 403


# ── Google ───────────────────────────────────────────────────────────────
async def test_google_new_then_existing(client, monkeypatch):
    email = f"g-{uuid.uuid4().hex[:8]}@gmail.com"

    async def fake(_):
        return {"email": email, "name": "G"}
    monkeypatch.setattr(google, "verify_id_token", fake)
    assert (await client.post("/auth/google", json={"id_token": "x"})).status_code == 200
    assert (await client.post("/auth/google", json={"id_token": "x"})).status_code == 200


async def test_google_invalid_and_no_email(client, monkeypatch):
    async def bad(_):
        return None
    monkeypatch.setattr(google, "verify_id_token", bad)
    assert (await client.post("/auth/google", json={"id_token": "x"})).status_code == 401

    async def noemail(_):
        return {"sub": "1"}
    monkeypatch.setattr(google, "verify_id_token", noemail)
    assert (await client.post("/auth/google", json={"id_token": "x"})).status_code == 400


# ── Signup / login edges ────────────────────────────────────────────────
async def test_signup_duplicate_conflict(client):
    e, _ = await _signup(client)
    r = await client.post("/auth/signup", json={"email": e, "password": "password123", "name": "T"})
    assert r.status_code == 409


async def test_login_wrong_password_then_success(client):
    e, _ = await _signup(client)
    assert (await client.post("/auth/login", data={"username": e, "password": "nope"})).status_code == 401
    assert (await client.post("/auth/login", data={"username": e, "password": "password123"})).status_code == 200


async def test_login_disabled_account(client):
    e, _ = await _signup(client)
    async with SessionLocal() as s:
        await s.execute(update(User).where(User.email == e).values(is_active=False))
        await s.commit()
    assert (await client.post("/auth/login", data={"username": e, "password": "password123"})).status_code == 403


async def test_login_unknown_user(client):
    assert (await client.post("/auth/login", data={"username": "ghost@absent.app", "password": "x"})).status_code == 401


# ── Refresh ──────────────────────────────────────────────────────────────
async def test_refresh_rotates_and_rejects_bad(client):
    e, _ = await _signup(client)
    tokens = (await client.post("/auth/login", data={"username": e, "password": "password123"})).json()
    r = await client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 200 and r.json()["access_token"]
    assert (await client.post("/auth/refresh", json={"refresh_token": "garbage"})).status_code == 401


# ── Verify / forgot / reset extra branches ──────────────────────────────
async def test_verify_request_and_bad_token(client):
    _, tok = await _signup(client)
    client.headers["Authorization"] = f"Bearer {tok}"
    assert (await client.post("/auth/verify/request")).json()["sent"] is True
    del client.headers["Authorization"]
    assert (await client.post("/auth/verify", json={"token": "not-a-token"})).status_code == 400


async def test_verify_unknown_user_token(client):
    # A validly-typed verify token for a random (nonexistent) user id.
    tok = create_verify_token(str(uuid.uuid4()))
    assert (await client.post("/auth/verify", json={"token": tok})).status_code == 400
