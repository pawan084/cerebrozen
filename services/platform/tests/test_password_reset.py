"""Password reset: /auth/password/forgot (non-enumerating, best-effort email) and
/auth/password/reset (single-use, expiring, revokes sessions). These endpoints are
what the Android app's "Forgot password" calls — until now they 404'd."""

import pytest

_PW = "hunter2hunter2"


async def _signup(client, email):
    r = await client.post(
        "/auth/signup", json={"email": email, "password": _PW, "name": "R"}
    )
    assert r.status_code == 201, r.text


def _capture_link(monkeypatch):
    """Intercept the reset email and hand back the link it would have sent."""
    box = {}
    monkeypatch.setattr(
        "app.routers.auth.emailer.send_password_reset",
        lambda to, link: box.update(to=to, link=link) or True,
    )
    return box


def _token(link: str) -> str:
    return link.split("token=", 1)[1]


async def test_forgot_then_reset_lets_the_user_sign_in_with_the_new_password(client, monkeypatch):
    box = _capture_link(monkeypatch)
    await _signup(client, "reset@example.com")
    r = await client.post("/auth/password/forgot", json={"email": "reset@example.com"})
    assert r.status_code == 200 and r.json() == {"ok": True}
    assert box["to"] == "reset@example.com"

    r2 = await client.post(
        "/auth/password/reset", json={"token": _token(box["link"]), "password": "newpass1234"}
    )
    assert r2.status_code == 200, r2.text
    # new password works, old one doesn't
    assert (await client.post("/auth/login", data={"username": "reset@example.com", "password": "newpass1234"})).status_code == 200
    assert (await client.post("/auth/login", data={"username": "reset@example.com", "password": _PW})).status_code == 401


async def test_forgot_never_reveals_whether_an_account_exists(client, monkeypatch):
    sent = []
    monkeypatch.setattr("app.routers.auth.emailer.send_password_reset", lambda to, link: sent.append(to))
    r = await client.post("/auth/password/forgot", json={"email": "nobody@example.com"})
    assert r.status_code == 200 and r.json() == {"ok": True}
    assert sent == [], "no reset email should be minted for an address with no account"


async def test_reset_rejects_a_forged_token(client):
    r = await client.post("/auth/password/reset", json={"token": "forged", "password": "newpass1234"})
    assert r.status_code == 400


async def test_a_reset_token_is_single_use(client, monkeypatch):
    box = _capture_link(monkeypatch)
    await _signup(client, "once@example.com")
    await client.post("/auth/password/forgot", json={"email": "once@example.com"})
    tok = _token(box["link"])
    assert (await client.post("/auth/password/reset", json={"token": tok, "password": "newpass1234"})).status_code == 200
    assert (await client.post("/auth/password/reset", json={"token": tok, "password": "another1234"})).status_code == 400


async def test_reset_rejects_an_expired_token(client, monkeypatch):
    monkeypatch.setattr("app.config.RESET_TTL_HOURS", -1)  # mint it already-expired
    box = _capture_link(monkeypatch)
    await _signup(client, "expired@example.com")
    await client.post("/auth/password/forgot", json={"email": "expired@example.com"})
    r = await client.post("/auth/password/reset", json={"token": _token(box["link"]), "password": "newpass1234"})
    assert r.status_code == 400


async def test_reset_rejects_a_short_password(client, monkeypatch):
    box = _capture_link(monkeypatch)
    await _signup(client, "shortpw@example.com")
    await client.post("/auth/password/forgot", json={"email": "shortpw@example.com"})
    r = await client.post("/auth/password/reset", json={"token": _token(box["link"]), "password": "short"})
    assert r.status_code == 400


async def test_reset_email_carries_the_reset_link(client, monkeypatch):
    from app import config as cfg
    from app import emailer

    sent = []
    monkeypatch.setattr(cfg, "SMTP_HOST", "smtp.example")
    monkeypatch.setattr(cfg, "SMTP_USER", "hello@cerebrozen.in")
    monkeypatch.setattr(cfg, "SMTP_PASS", "x")
    monkeypatch.setattr(emailer, "deliver", lambda msg: sent.append(msg))
    await _signup(client, "mailer@example.com")
    r = await client.post("/auth/password/forgot", json={"email": "mailer@example.com"})
    assert r.status_code == 200
    (msg,) = sent
    assert msg["To"] == "mailer@example.com"
    assert "/reset?token=" in msg.get_content()


def test_reset_email_skips_when_smtp_unconfigured():
    from app import emailer

    # No SMTP configured in the test env → returns False without attempting delivery.
    assert emailer.send_password_reset("x@example.com", "http://x/reset?token=t") is False


def test_reset_email_delivery_failure_returns_false(monkeypatch):
    from app import config as cfg
    from app import emailer

    monkeypatch.setattr(cfg, "SMTP_HOST", "smtp.example")
    monkeypatch.setattr(cfg, "SMTP_USER", "hello@cerebrozen.in")
    monkeypatch.setattr(cfg, "SMTP_PASS", "x")

    def boom(_msg):
        raise OSError("smtp down")

    monkeypatch.setattr(emailer, "deliver", boom)
    assert emailer.send_password_reset("x@example.com", "http://x/reset?token=t") is False


async def test_reset_fails_for_a_deactivated_account(client, monkeypatch):
    box = _capture_link(monkeypatch)
    await _signup(client, "gone@example.com")
    await client.post("/auth/password/forgot", json={"email": "gone@example.com"})
    tok = _token(box["link"])
    # Deactivate the account after the token was minted.
    from sqlalchemy import select, update
    from app.db import SessionLocal
    from app.models import User
    async with SessionLocal() as s:
        await s.execute(update(User).where(User.email == "gone@example.com").values(is_active=False))
        await s.commit()
    r = await client.post("/auth/password/reset", json={"token": tok, "password": "newpass1234"})
    assert r.status_code == 400
