"""Auth hardening: lockout, token revocation, email verification, password reset."""
import uuid

from app.core.security import create_reset_token, create_verify_token
from app.services import email as email_service


async def _signup(client, password="password123"):
    e = f"user-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": e, "password": password, "name": "T"})
    assert r.status_code == 201, r.text
    return e, r.json()["access_token"]


async def test_account_lockout_after_repeated_failures(client):
    email, _ = await _signup(client)
    for _ in range(5):
        r = await client.post("/auth/login", data={"username": email, "password": "wrong"})
        assert r.status_code == 401
    # Now locked — even the correct password is refused.
    r = await client.post("/auth/login", data={"username": email, "password": "password123"})
    assert r.status_code == 403
    assert "locked" in r.json()["detail"].lower()


async def test_logout_revokes_token(client):
    _, token = await _signup(client)
    client.headers["Authorization"] = f"Bearer {token}"
    assert (await client.get("/users/me")).status_code == 200
    assert (await client.post("/auth/logout")).status_code == 204
    # Same token is now revoked.
    assert (await client.get("/users/me")).status_code == 401


async def test_password_reset_flow_and_revocation(client):
    email, token = await _signup(client)
    client.headers["Authorization"] = f"Bearer {token}"
    me = (await client.get("/users/me")).json()

    reset_token = create_reset_token(me["id"])
    r = await client.post("/auth/password/reset",
                          json={"token": reset_token, "new_password": "newpassword456"})
    assert r.status_code == 200

    # Old access token is revoked (token_version bumped).
    assert (await client.get("/users/me")).status_code == 401
    # Old password no longer works; the new one does.
    del client.headers["Authorization"]
    assert (await client.post("/auth/login", data={"username": email, "password": "password123"})).status_code == 401
    assert (await client.post("/auth/login", data={"username": email, "password": "newpassword456"})).status_code == 200


async def test_email_verification_flow(client):
    _, token = await _signup(client)
    client.headers["Authorization"] = f"Bearer {token}"
    me = (await client.get("/users/me")).json()

    # Request (captured in the test outbox), then confirm with a minted token.
    email_service.sent_outbox.clear()
    assert (await client.post("/auth/verify/request")).json()["sent"] is True
    assert email_service.sent_outbox and "verify" in email_service.sent_outbox[-1]["subject"].lower()

    verify_token = create_verify_token(me["id"])
    assert (await client.post("/auth/verify", json={"token": verify_token})).json()["verified"] is True


async def test_forgot_password_no_enumeration(client):
    # Nonexistent email still returns 200 (no account enumeration).
    r = await client.post("/auth/password/forgot", json={"email": "ghost-nobody@absent.app"})
    assert r.status_code == 200 and r.json()["sent"] is True

    email, _ = await _signup(client)
    email_service.sent_outbox.clear()
    r = await client.post("/auth/password/forgot", json={"email": email})
    assert r.status_code == 200
    assert email_service.sent_outbox and email_service.sent_outbox[-1]["to"] == email


async def test_reset_rejects_bad_token(client):
    r = await client.post("/auth/password/reset", json={"token": "not-a-token", "new_password": "whatever12"})
    assert r.status_code == 400
