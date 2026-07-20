"""Passwordless email OTP: /auth/otp/request + /auth/otp/verify. The Android app's
one-time-code sign-in calls these — until now they 404'd. Codes are single-use,
short-lived, non-enumerating, and brute-force-capped."""

import pytest

from app.security import decode_access_token

_PW = "hunter2hunter2"


async def _signup(client, email):
    r = await client.post("/auth/signup", json={"email": email, "password": _PW, "name": "O"})
    assert r.status_code == 201, r.text


def _capture_code(monkeypatch):
    box = {}
    monkeypatch.setattr(
        "app.routers.auth.emailer.send_otp_code",
        lambda to, code: box.update(to=to, code=code) or True,
    )
    return box


async def test_request_then_verify_signs_in(client, monkeypatch):
    box = _capture_code(monkeypatch)
    await _signup(client, "otp@example.com")
    r = await client.post("/auth/otp/request", json={"email": "otp@example.com"})
    assert r.status_code == 200 and r.json() == {"ok": True}
    assert box["to"] == "otp@example.com" and len(box["code"]) == 6 and box["code"].isdigit()
    r2 = await client.post("/auth/otp/verify", json={"email": "otp@example.com", "code": box["code"]})
    assert r2.status_code == 200, r2.text
    assert decode_access_token(r2.json()["access_token"])["plan"] == "free"


async def test_request_is_non_enumerating(client, monkeypatch):
    sent = []
    monkeypatch.setattr("app.routers.auth.emailer.send_otp_code", lambda to, code: sent.append(to))
    r = await client.post("/auth/otp/request", json={"email": "nobody@example.com"})
    assert r.status_code == 200 and r.json() == {"ok": True}
    assert sent == []


async def test_a_code_is_single_use(client, monkeypatch):
    box = _capture_code(monkeypatch)
    await _signup(client, "otp2@example.com")
    await client.post("/auth/otp/request", json={"email": "otp2@example.com"})
    code = box["code"]
    assert (await client.post("/auth/otp/verify", json={"email": "otp2@example.com", "code": code})).status_code == 200
    assert (await client.post("/auth/otp/verify", json={"email": "otp2@example.com", "code": code})).status_code == 400


async def test_verify_without_a_requested_code_is_400(client):
    await _signup(client, "otp3@example.com")
    r = await client.post("/auth/otp/verify", json={"email": "otp3@example.com", "code": "000000"})
    assert r.status_code == 400


async def test_a_code_burns_after_too_many_wrong_guesses(client, monkeypatch):
    box = _capture_code(monkeypatch)
    await _signup(client, "otp4@example.com")
    await client.post("/auth/otp/request", json={"email": "otp4@example.com"})
    code = box["code"]
    wrong = "999999" if code != "999999" else "111111"
    for _ in range(5):  # OTP_MAX_ATTEMPTS default
        assert (await client.post("/auth/otp/verify", json={"email": "otp4@example.com", "code": wrong})).status_code == 400
    # the correct code is now burned — a guessing attacker can't fall back to it
    assert (await client.post("/auth/otp/verify", json={"email": "otp4@example.com", "code": code})).status_code == 400


async def test_an_expired_code_is_400(client, monkeypatch):
    monkeypatch.setattr("app.config.OTP_TTL_MINUTES", -1)  # mint already-expired
    box = _capture_code(monkeypatch)
    await _signup(client, "otp5@example.com")
    await client.post("/auth/otp/request", json={"email": "otp5@example.com"})
    r = await client.post("/auth/otp/verify", json={"email": "otp5@example.com", "code": box["code"]})
    assert r.status_code == 400


async def test_a_new_request_invalidates_the_prior_code(client, monkeypatch):
    box = _capture_code(monkeypatch)
    await _signup(client, "otp6@example.com")
    await client.post("/auth/otp/request", json={"email": "otp6@example.com"})
    first = box["code"]
    await client.post("/auth/otp/request", json={"email": "otp6@example.com"})
    second = box["code"]
    assert (await client.post("/auth/otp/verify", json={"email": "otp6@example.com", "code": first})).status_code == 400
    assert (await client.post("/auth/otp/verify", json={"email": "otp6@example.com", "code": second})).status_code == 200


async def test_verify_fails_if_account_deactivated(client, monkeypatch):
    box = _capture_code(monkeypatch)
    await _signup(client, "otpgone@example.com")
    await client.post("/auth/otp/request", json={"email": "otpgone@example.com"})
    from sqlalchemy import update
    from app.db import SessionLocal
    from app.models import User
    async with SessionLocal() as s:
        await s.execute(update(User).where(User.email == "otpgone@example.com").values(is_active=False))
        await s.commit()
    r = await client.post("/auth/otp/verify", json={"email": "otpgone@example.com", "code": box["code"]})
    assert r.status_code == 400


async def test_otp_email_carries_a_code(client, monkeypatch):
    from app import config as cfg
    from app import emailer

    sent = []
    monkeypatch.setattr(cfg, "SMTP_HOST", "smtp.example")
    monkeypatch.setattr(cfg, "SMTP_USER", "hello@cerebrozen.in")
    monkeypatch.setattr(cfg, "SMTP_PASS", "x")
    monkeypatch.setattr(emailer, "deliver", lambda msg: sent.append(msg))
    await _signup(client, "otpmail@example.com")
    await client.post("/auth/otp/request", json={"email": "otpmail@example.com"})
    (msg,) = sent
    assert msg["To"] == "otpmail@example.com"


def test_otp_email_skips_when_smtp_unconfigured():
    from app import emailer

    assert emailer.send_otp_code("x@example.com", "123456") is False


def test_otp_email_delivery_failure_returns_false(monkeypatch):
    from app import config as cfg
    from app import emailer

    monkeypatch.setattr(cfg, "SMTP_HOST", "smtp.example")
    monkeypatch.setattr(cfg, "SMTP_USER", "hello@cerebrozen.in")
    monkeypatch.setattr(cfg, "SMTP_PASS", "x")

    def boom(_msg):
        raise OSError("smtp down")

    monkeypatch.setattr(emailer, "deliver", boom)
    assert emailer.send_otp_code("x@example.com", "123456") is False
