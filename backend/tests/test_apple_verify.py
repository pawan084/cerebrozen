"""Unit coverage for Apple identity-token verification (RS256 against a JWK).

We mint our own RSA key, expose it as Apple's JWKS via monkeypatch, and sign
tokens locally — so the real verification path runs without hitting Apple.
"""
import time

import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from jose import jwk as jose_jwk
from jose import jwt

from app.core.config import settings
from app.services import apple

_KID = "test-kid-1"


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


def _make_token(priv_pem, *, aud=None, kid=_KID, email="apple@privaterelay.appleid.com"):
    claims = {
        "iss": "https://appleid.apple.com",
        "aud": aud or settings.apple_audience,
        "sub": "apple-sub-xyz",
        "email": email,
        "iat": int(time.time()),
        "exp": int(time.time()) + 3600,
    }
    return jwt.encode(claims, priv_pem, algorithm="RS256", headers={"kid": kid})


async def test_valid_token(monkeypatch, rsa_pair):
    priv, pub = rsa_pair

    async def fake_keys():
        return [pub]

    monkeypatch.setattr(apple, "_apple_keys", fake_keys)
    claims = await apple.verify_identity_token(_make_token(priv))
    assert claims and claims["email"] == "apple@privaterelay.appleid.com"


async def test_wrong_audience_rejected(monkeypatch, rsa_pair):
    priv, pub = rsa_pair

    async def fake_keys():
        return [pub]

    monkeypatch.setattr(apple, "_apple_keys", fake_keys)
    assert await apple.verify_identity_token(_make_token(priv, aud="com.someone.else")) is None


async def test_malformed_token_rejected():
    assert await apple.verify_identity_token("not-a-jwt") is None


async def test_unknown_kid_rejected(monkeypatch, rsa_pair):
    priv, pub = rsa_pair

    async def fake_keys():
        return [pub]   # only _KID present

    monkeypatch.setattr(apple, "_apple_keys", fake_keys)
    # Token signed with a header kid Apple's set doesn't contain → no match (covers retry).
    assert await apple.verify_identity_token(_make_token(priv, kid="other-kid")) is None
