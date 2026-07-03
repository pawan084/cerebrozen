"""StoreKit 2 signed-transaction verification (services/appstore)."""
import base64
import json
from datetime import datetime, timedelta, timezone

import pytest
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature
from cryptography.x509.oid import NameOID

_DER = serialization.Encoding.DER
_PEM = serialization.Encoding.PEM

from app.services import appstore


def _name(cn):
    return x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, cn)])


def _cert(subject, issuer_name, subject_key, issuer_key, ca=False):
    now = datetime.now(timezone.utc)
    builder = (
        x509.CertificateBuilder()
        .subject_name(_name(subject))
        .issuer_name(issuer_name)
        .public_key(subject_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(days=1))
        .not_valid_after(now + timedelta(days=3650))
    )
    if ca:
        builder = builder.add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
    return builder.sign(issuer_key, hashes.SHA256())


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _build_jws(payload: dict):
    """Build a self-signed StoreKit-style JWS + return (jws, root_pem)."""
    root_key = ec.generate_private_key(ec.SECP256R1())
    leaf_key = ec.generate_private_key(ec.SECP256R1())
    root = _cert("Test Apple Root", _name("Test Apple Root"), root_key, root_key, ca=True)
    leaf = _cert("Test Leaf", root.subject, leaf_key, root_key)

    x5c = [
        base64.b64encode(leaf.public_bytes(_DER)).decode(),
        base64.b64encode(root.public_bytes(_DER)).decode(),
    ]
    header = {"alg": "ES256", "x5c": x5c}
    signing_input = f"{_b64url(json.dumps(header).encode())}.{_b64url(json.dumps(payload).encode())}"
    der_sig = leaf_key.sign(signing_input.encode(), ec.ECDSA(hashes.SHA256()))
    r, s = decode_dss_signature(der_sig)
    raw = r.to_bytes(32, "big") + s.to_bytes(32, "big")
    jws = f"{signing_input}.{_b64url(raw)}"
    return jws, root.public_bytes(_PEM)


def test_verify_and_tier_for_active_premium(tmp_path, monkeypatch):
    future = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp() * 1000)
    payload = {"productId": "com.cerebrozen.premium.monthly", "expiresDate": future}
    jws, root_pem = _build_jws(payload)

    root_file = tmp_path / "root.pem"
    root_file.write_bytes(root_pem)
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", str(root_file))

    data = appstore.verify_transaction(jws)
    assert data["productId"] == "com.cerebrozen.premium.monthly"
    tier, expires = appstore.tier_for(data)
    assert tier == "premium"
    assert expires is not None


def test_expired_subscription_is_free():
    past = int((datetime.now(timezone.utc) - timedelta(days=1)).timestamp() * 1000)
    tier, _ = appstore.tier_for({"productId": "com.cerebrozen.premium.monthly", "expiresDate": past})
    assert tier == "free"


def test_revoked_subscription_is_free():
    future = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp() * 1000)
    tier, _ = appstore.tier_for(
        {"productId": "com.cerebrozen.premium.monthly", "expiresDate": future, "revocationDate": 123}
    )
    assert tier == "free"


def test_tampered_signature_rejected(tmp_path, monkeypatch):
    future = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp() * 1000)
    jws, root_pem = _build_jws({"productId": "com.cerebrozen.premium.monthly", "expiresDate": future})
    # Flip the payload but keep the old signature.
    header_b64, _payload_b64, sig_b64 = jws.split(".")
    forged_payload = _b64url(json.dumps({"productId": "com.cerebrozen.premiumhuman.monthly"}).encode())
    tampered = f"{header_b64}.{forged_payload}.{sig_b64}"
    with pytest.raises(appstore.ReceiptError):
        appstore.verify_transaction(tampered)


def test_wrong_bundle_id_rejected(tmp_path, monkeypatch):
    """A validly signed transaction for ANOTHER app must not grant tier here."""
    future = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp() * 1000)
    payload = {"productId": "com.cerebrozen.premium.monthly", "expiresDate": future,
               "bundleId": "com.somebody.else"}
    jws, root_pem = _build_jws(payload)
    root_file = tmp_path / "root.pem"
    root_file.write_bytes(root_pem)
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", str(root_file))
    with pytest.raises(appstore.ReceiptError):
        appstore.verify_transaction(jws)


def test_matching_bundle_id_accepted(tmp_path, monkeypatch):
    future = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp() * 1000)
    payload = {"productId": "com.cerebrozen.premium.monthly", "expiresDate": future,
               "bundleId": appstore.settings.appstore_bundle_id}
    jws, root_pem = _build_jws(payload)
    root_file = tmp_path / "root.pem"
    root_file.write_bytes(root_pem)
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", str(root_file))
    assert appstore.verify_transaction(jws)["bundleId"] == appstore.settings.appstore_bundle_id


def test_wrong_root_pin_rejected(tmp_path, monkeypatch):
    future = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp() * 1000)
    jws, _root_pem = _build_jws({"productId": "com.cerebrozen.premium.monthly", "expiresDate": future})
    # Pin to a DIFFERENT root than the one that signed the chain.
    _other_jws, other_root_pem = _build_jws({"productId": "x"})
    root_file = tmp_path / "other_root.pem"
    root_file.write_bytes(other_root_pem)
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", str(root_file))
    with pytest.raises(appstore.ReceiptError):
        appstore.verify_transaction(jws)


def test_annual_products_map_to_the_same_tiers():
    future = int((datetime.now(timezone.utc) + timedelta(days=365)).timestamp() * 1000)
    tier, expires = appstore.tier_for(
        {"productId": "com.cerebrozen.premium.annual", "expiresDate": future}
    )
    assert tier == "premium" and expires is not None
    tier, _ = appstore.tier_for(
        {"productId": "com.cerebrozen.premiumhuman.annual", "expiresDate": future}
    )
    assert tier == "premium_human"
