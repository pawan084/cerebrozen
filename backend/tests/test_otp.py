"""Email one-time-code (passwordless) sign-in: /auth/otp/request + /auth/otp/verify."""
import re
import uuid
from datetime import timedelta

from sqlalchemy import select, update

from app.core.database import SessionLocal, utcnow
from app.models.login_code import LoginCode
from app.models.user import User
from app.services import email as email_service


def _addr() -> str:
    return f"otp-{uuid.uuid4().hex[:10]}@test.app"


async def _request_code(client, addr: str) -> str:
    """Request a code and pull it out of the captured email."""
    email_service.sent_outbox.clear()
    r = await client.post("/auth/otp/request", json={"email": addr})
    assert r.status_code == 200 and r.json()["sent"] is True
    sent = email_service.sent_outbox[-1]
    assert sent["to"] == addr
    return re.search(r"\b(\d{6})\b", sent["body"]).group(1)


async def test_otp_creates_account_and_marks_verified(client):
    addr = _addr()
    code = await _request_code(client, addr)
    r = await client.post("/auth/otp/verify", json={"email": addr, "code": code})
    assert r.status_code == 200, r.text
    tokens = r.json()
    assert tokens["access_token"] and tokens["refresh_token"]

    client.headers["Authorization"] = f"Bearer {tokens['access_token']}"
    me = (await client.get("/users/me")).json()
    assert me["email"] == addr

    async with SessionLocal() as s:
        user = await s.scalar(select(User).where(User.email == addr))
        assert user.email_verified is True


async def test_otp_signs_in_existing_account(client):
    addr = _addr()
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "T"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    original_id = (await client.get("/users/me")).json()["id"]

    code = await _request_code(client, addr)
    r = await client.post("/auth/otp/verify", json={"email": addr, "code": code})
    assert r.status_code == 200
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    assert (await client.get("/users/me")).json()["id"] == original_id


async def test_otp_is_single_use(client):
    addr = _addr()
    code = await _request_code(client, addr)
    assert (await client.post("/auth/otp/verify", json={"email": addr, "code": code})).status_code == 200
    assert (await client.post("/auth/otp/verify", json={"email": addr, "code": code})).status_code == 401


async def test_otp_burns_after_max_wrong_attempts(client):
    addr = _addr()
    code = await _request_code(client, addr)
    wrong = "000000" if code != "000000" else "111111"
    for _ in range(5):
        assert (await client.post("/auth/otp/verify", json={"email": addr, "code": wrong})).status_code == 401
    # Burned — even the real code is refused now.
    assert (await client.post("/auth/otp/verify", json={"email": addr, "code": code})).status_code == 401


async def test_otp_expired_code_refused(client):
    addr = _addr()
    code = await _request_code(client, addr)
    async with SessionLocal() as s:
        await s.execute(update(LoginCode).where(LoginCode.email == addr)
                        .values(expires_at=utcnow() - timedelta(minutes=1)))
        await s.commit()
    assert (await client.post("/auth/otp/verify", json={"email": addr, "code": code})).status_code == 401


async def test_otp_rerequest_replaces_earlier_code(client):
    addr = _addr()
    first = await _request_code(client, addr)
    second = await _request_code(client, addr)
    if first != second:   # 1-in-a-million collision would make the old code "work"
        assert (await client.post("/auth/otp/verify", json={"email": addr, "code": first})).status_code == 401
    assert (await client.post("/auth/otp/verify", json={"email": addr, "code": second})).status_code == 200


async def test_otp_verify_without_request_refused(client):
    r = await client.post("/auth/otp/verify", json={"email": _addr(), "code": "123456"})
    assert r.status_code == 401


async def test_otp_rejects_malformed_code(client):
    r = await client.post("/auth/otp/verify", json={"email": _addr(), "code": "abc123"})
    assert r.status_code == 422


async def test_otp_disabled_account_refused(client):
    addr = _addr()
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "T"})
    assert r.status_code == 201
    async with SessionLocal() as s:
        await s.execute(update(User).where(User.email == addr).values(is_active=False))
        await s.commit()
    code = await _request_code(client, addr)
    assert (await client.post("/auth/otp/verify", json={"email": addr, "code": code})).status_code == 403
    # The code burned on the attempt.
    assert (await client.post("/auth/otp/verify", json={"email": addr, "code": code})).status_code == 401


async def test_otp_clears_password_lockout(client):
    addr = _addr()
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "T"})
    assert r.status_code == 201
    for _ in range(5):
        assert (await client.post("/auth/login", data={"username": addr, "password": "wrong"})).status_code == 401
    assert (await client.post("/auth/login", data={"username": addr, "password": "password123"})).status_code == 403

    # Proving the inbox unlocks the account again.
    code = await _request_code(client, addr)
    assert (await client.post("/auth/otp/verify", json={"email": addr, "code": code})).status_code == 200
    assert (await client.post("/auth/login", data={"username": addr, "password": "password123"})).status_code == 200
