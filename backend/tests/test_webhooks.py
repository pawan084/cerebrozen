"""App Store Server Notifications V2 webhook."""
import base64
import json
import uuid
from datetime import datetime, timedelta, timezone

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature
from cryptography.x509.oid import NameOID

from app.services import appstore

_DER = serialization.Encoding.DER
_PEM = serialization.Encoding.PEM


def _name(cn):
    return x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, cn)])


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _chain():
    root_key = ec.generate_private_key(ec.SECP256R1())
    leaf_key = ec.generate_private_key(ec.SECP256R1())
    now = datetime.now(timezone.utc)

    def cert(subject, issuer, sub_key, iss_key, ca=False):
        b = (x509.CertificateBuilder().subject_name(_name(subject)).issuer_name(issuer)
             .public_key(sub_key.public_key()).serial_number(x509.random_serial_number())
             .not_valid_before(now - timedelta(days=1)).not_valid_after(now + timedelta(days=3650)))
        if ca:
            b = b.add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
        return b.sign(iss_key, hashes.SHA256())

    root = cert("Test Root", _name("Test Root"), root_key, root_key, ca=True)
    leaf = cert("Test Leaf", root.subject, leaf_key, root_key)
    x5c = [base64.b64encode(leaf.public_bytes(_DER)).decode(),
           base64.b64encode(root.public_bytes(_DER)).decode()]
    return leaf_key, x5c, root.public_bytes(_PEM)


def _jws(payload: dict, leaf_key, x5c) -> str:
    header = {"alg": "ES256", "x5c": x5c}
    signing_input = f"{_b64url(json.dumps(header).encode())}.{_b64url(json.dumps(payload).encode())}"
    der = leaf_key.sign(signing_input.encode(), ec.ECDSA(hashes.SHA256()))
    r, s = decode_dss_signature(der)
    raw = r.to_bytes(32, "big") + s.to_bytes(32, "big")
    return f"{signing_input}.{_b64url(raw)}"


async def test_webhook_renew_sets_premium(client, auth_client, tmp_path, monkeypatch):
    # A real user to target via appAccountToken.
    me = (await auth_client.get("/users/me")).json()
    assert me["subscription_tier"] == "free"

    leaf_key, x5c, root_pem = _chain()
    root_file = tmp_path / "root.pem"
    root_file.write_bytes(root_pem)
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", str(root_file))

    future = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp() * 1000)
    txn = _jws({"productId": "com.cerebro.premium.monthly", "expiresDate": future,
                "appAccountToken": me["id"]}, leaf_key, x5c)
    outer = _jws({"notificationType": "DID_RENEW", "data": {"signedTransactionInfo": txn}},
                 leaf_key, x5c)

    r = await client.post("/webhooks/appstore", json={"signedPayload": outer})
    assert r.status_code == 200 and r.json()["handled"] is True and r.json()["tier"] == "premium"
    assert (await auth_client.get("/users/me")).json()["subscription_tier"] == "premium"


async def test_webhook_expire_reverts_to_free(client, auth_client, tmp_path, monkeypatch):
    me = (await auth_client.get("/users/me")).json()
    leaf_key, x5c, root_pem = _chain()
    root_file = tmp_path / "root.pem"
    root_file.write_bytes(root_pem)
    monkeypatch.setattr(appstore.settings, "appstore_root_cert_path", str(root_file))

    future = int((datetime.now(timezone.utc) + timedelta(days=30)).timestamp() * 1000)
    txn = _jws({"productId": "com.cerebro.premium.monthly", "expiresDate": future,
                "appAccountToken": me["id"]}, leaf_key, x5c)
    outer = _jws({"notificationType": "EXPIRED", "data": {"signedTransactionInfo": txn}},
                 leaf_key, x5c)

    r = await client.post("/webhooks/appstore", json={"signedPayload": outer})
    assert r.json()["tier"] == "free"


async def test_webhook_rejects_forged_payload(client):
    r = await client.post("/webhooks/appstore", json={"signedPayload": "not.a.jws"})
    assert r.status_code == 200 and r.json()["handled"] is False
