"""Unit coverage for Google ID-token verification (RS256 against a JWK).

We mint our own RSA key, expose it as Google's certs via monkeypatch, and sign
tokens locally — so the real verification path runs without hitting Google.
"""
import time

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from jose import jwk as jose_jwk
from jose import jwt

from app.services import google

_KID = "g-test-kid-1"


@pytest.fixture
def rsa_pair():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    priv_pem = key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    ).decode()
    pub_pem = key.public_key().public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    ).decode()
    public_jwk = jose_jwk.construct(pub_pem, "RS256").to_dict()
    public_jwk.update({"kid": _KID, "alg": "RS256", "use": "sig"})
    return priv_pem, public_jwk


def _make_token(priv_pem, *, iss="https://accounts.google.com", aud=None,
                kid=_KID, email="user@gmail.com", email_verified=True):
    claims = {
        "iss": iss,
        "aud": aud or "cerebro.apps.googleusercontent.com",
        "sub": "google-sub-xyz",
        "email": email,
        "email_verified": email_verified,
        "name": "Test User",
        "iat": int(time.time()),
        "exp": int(time.time()) + 3600,
    }
    return jwt.encode(claims, priv_pem, algorithm="RS256", headers={"kid": kid})


async def test_valid_token(monkeypatch, rsa_pair):
    priv, pub = rsa_pair

    async def fake_keys():
        return [pub]

    monkeypatch.setattr(google, "_google_keys", fake_keys)
    monkeypatch.setattr(google.settings, "google_client_id", "cerebro.apps.googleusercontent.com")
    claims = await google.verify_id_token(_make_token(priv))
    assert claims and claims["email"] == "user@gmail.com"


async def test_wrong_audience_rejected(monkeypatch, rsa_pair):
    priv, pub = rsa_pair

    async def fake_keys():
        return [pub]

    monkeypatch.setattr(google, "_google_keys", fake_keys)
    monkeypatch.setattr(google.settings, "google_client_id", "cerebro.apps.googleusercontent.com")
    assert await google.verify_id_token(_make_token(priv, aud="someone.else")) is None


async def test_foreign_issuer_rejected(monkeypatch, rsa_pair):
    priv, pub = rsa_pair

    async def fake_keys():
        return [pub]

    monkeypatch.setattr(google, "_google_keys", fake_keys)
    monkeypatch.setattr(google.settings, "google_client_id", "")   # aud check skipped
    assert await google.verify_id_token(_make_token(priv, iss="https://evil.example")) is None


async def test_unverified_email_rejected(monkeypatch, rsa_pair):
    priv, pub = rsa_pair

    async def fake_keys():
        return [pub]

    monkeypatch.setattr(google, "_google_keys", fake_keys)
    monkeypatch.setattr(google.settings, "google_client_id", "")
    assert await google.verify_id_token(_make_token(priv, email_verified=False)) is None


async def test_malformed_token_rejected():
    assert await google.verify_id_token("not-a-jwt") is None
