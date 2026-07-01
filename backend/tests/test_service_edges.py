"""Coverage for provider-integration branches: email SMTP, SMS Twilio, escalation
ops-alert, AI fallback, and App Store receipt edge cases."""
import base64
import json

import pytest

from app.services import ai, appstore
from app.services import email as em
from app.services import sms


# ── email: log path + SMTP send path + failure ─────────────────────────
async def test_email_logs_when_no_smtp(monkeypatch):
    monkeypatch.delenv("TESTING", raising=False)   # run the real send path
    monkeypatch.setattr(em.settings, "smtp_host", "")
    await em.send_email("a@b.com", "hi", "body")    # no host → logs, returns


async def test_email_smtp_send_and_failure(monkeypatch):
    monkeypatch.delenv("TESTING", raising=False)
    sent = {}

    class FakeSMTP:
        def __init__(self, *a, **k): pass
        def __enter__(self): return self
        def __exit__(self, *a): return False
        def starttls(self): sent["tls"] = True
        def login(self, u, p): sent["login"] = (u, p)
        def send_message(self, m): sent["msg"] = m

    monkeypatch.setattr(em.smtplib, "SMTP", FakeSMTP)
    monkeypatch.setattr(em.settings, "smtp_host", "smtp.example.com")
    monkeypatch.setattr(em.settings, "smtp_user", "u")
    monkeypatch.setattr(em.settings, "smtp_password", "p")
    monkeypatch.setattr(em.settings, "smtp_tls", True)
    await em.send_email("a@b.com", "hi", "body")
    assert sent.get("tls") and "msg" in sent

    # A raising SMTP must be swallowed (never 500s the caller).
    class BoomSMTP(FakeSMTP):
        def __enter__(self): raise RuntimeError("smtp down")
    monkeypatch.setattr(em.smtplib, "SMTP", BoomSMTP)
    await em.send_email("a@b.com", "hi", "body")


# ── sms: log path + Twilio path ─────────────────────────────────────────
async def test_sms_logs_when_unconfigured(monkeypatch):
    monkeypatch.delenv("TESTING", raising=False)
    monkeypatch.setattr(sms.settings, "twilio_account_sid", "")
    await sms.send_sms("+15550000000", "hi")


async def test_sms_twilio_send(monkeypatch):
    monkeypatch.delenv("TESTING", raising=False)
    calls = {}

    class FakeResp:
        status_code = 200
        text = ""

    class FakeClient:
        def __init__(self, *a, **k): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, url, **k): calls["url"] = url; return FakeResp()

    monkeypatch.setattr(sms.httpx, "AsyncClient", FakeClient)
    monkeypatch.setattr(sms.settings, "twilio_account_sid", "AC1")
    monkeypatch.setattr(sms.settings, "twilio_auth_token", "tok")
    monkeypatch.setattr(sms.settings, "twilio_from", "+15551231234")
    await sms.send_sms("+15550000000", "hi")
    assert "AC1" in calls["url"]


# ── escalation: ops alert email on crisis ───────────────────────────────
async def test_escalation_sends_ops_alert(auth_client, monkeypatch):
    from app.services import escalation
    monkeypatch.setattr(escalation.settings, "ops_alert_email", "ops@cerebrozen.in")
    em.sent_outbox.clear()
    r = await auth_client.post("/chat/messages", json={"text": "I want to kill myself tonight."})
    assert r.status_code == 201
    assert any(m["to"] == "ops@cerebrozen.in" for m in em.sent_outbox)


# ── ai: no-provider fallback ────────────────────────────────────────────
async def test_ai_no_provider_returns_none(monkeypatch):
    monkeypatch.setattr(ai.settings, "openai_api_key", "")
    monkeypatch.setattr(ai.settings, "anthropic_api_key", "")
    assert await ai.complete("system", "user") is None
    assert await ai.complete_json("system", "user") is None


# ── appstore: malformed / missing-chain / root-load edges ───────────────
def test_appstore_malformed_jws():
    with pytest.raises(appstore.ReceiptError):
        appstore.verify_transaction("only-one-part")
    with pytest.raises(appstore.ReceiptError):
        appstore.verify_transaction("bad.bad.bad")   # header not valid b64 json


def test_appstore_missing_chain():
    h = base64.urlsafe_b64encode(json.dumps({"alg": "ES256"}).encode()).rstrip(b"=").decode()
    with pytest.raises(appstore.ReceiptError):
        appstore.verify_transaction(f"{h}.{h}.{h}")   # no x5c → missing chain


def test_appstore_load_root_missing(monkeypatch):
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", "/does/not/exist.pem")
    assert appstore._load_root() is None
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", "")
    assert appstore._load_root() is None
