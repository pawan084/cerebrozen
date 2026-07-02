"""StoreKit 2 signed-transaction (JWS) verification.

Verifies the JWS Apple issues for a purchase: the ES256 signature by the leaf
certificate, the certificate chain up to Apple's root (pinned when
``appstore_root_cert_path`` is configured), and extracts the product + expiry.
Maps the product to our subscription tier so the *server* — not the client — is
the source of truth for entitlement.
"""
from __future__ import annotations

import base64
import json
import logging
from datetime import datetime, timezone

from cryptography import x509
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import encode_dss_signature

from app.core.config import settings

logger = logging.getLogger("cerebro.appstore")

_PRODUCT_TIERS = {
    "com.cerebrozen.premium.monthly": "premium",
    "com.cerebrozen.premiumhuman.monthly": "premium_human",
}


class ReceiptError(Exception):
    """Any failure while verifying a signed transaction."""


def _b64url(segment: str) -> bytes:
    return base64.urlsafe_b64decode(segment + "=" * (-len(segment) % 4))


def _load_root() -> x509.Certificate | None:
    path = settings.appstore_root_cert_path
    if not path:
        return None
    try:
        with open(path, "rb") as fh:
            return x509.load_pem_x509_certificate(fh.read())
    except Exception as exc:  # pragma: no cover - config error path
        logger.warning("Could not load App Store root cert (%s): %s", path, exc)
        return None


def _cert_signed_by(child: x509.Certificate, parent: x509.Certificate) -> bool:
    try:
        parent.public_key().verify(
            child.signature,
            child.tbs_certificate_bytes,
            ec.ECDSA(child.signature_hash_algorithm),
        )
        return True
    except (InvalidSignature, Exception):
        return False


def verify_transaction(jws: str) -> dict:
    """Verify a StoreKit 2 signed transaction JWS and return its payload.

    Raises :class:`ReceiptError` on any verification failure.
    """
    try:
        header_b64, payload_b64, sig_b64 = jws.split(".")
        header = json.loads(_b64url(header_b64))
    except Exception as exc:
        raise ReceiptError("Malformed JWS") from exc

    x5c = header.get("x5c") or []
    if len(x5c) < 2:
        raise ReceiptError("Missing certificate chain")

    try:
        certs = [x509.load_der_x509_certificate(base64.b64decode(c)) for c in x5c]
    except Exception as exc:
        raise ReceiptError("Bad certificate encoding") from exc
    leaf = certs[0]

    # 1) JWS signature by the leaf cert. ES256 signatures are raw r||s; the
    #    cryptography API wants DER, so convert.
    signing_input = f"{header_b64}.{payload_b64}".encode()
    raw = _b64url(sig_b64)
    if len(raw) != 64:
        raise ReceiptError("Bad signature length")
    r = int.from_bytes(raw[:32], "big")
    s = int.from_bytes(raw[32:], "big")
    try:
        leaf.public_key().verify(encode_dss_signature(r, s), signing_input, ec.ECDSA(hashes.SHA256()))
    except InvalidSignature as exc:
        raise ReceiptError("Signature verification failed") from exc

    # 2) Chain: each cert signed by the next one up.
    for child, parent in zip(certs, certs[1:]):
        if not _cert_signed_by(child, parent):
            raise ReceiptError("Broken certificate chain")

    # 3) Pin the chain root to Apple's when configured.
    root = _load_root()
    if root is not None:
        if certs[-1].fingerprint(hashes.SHA256()) != root.fingerprint(hashes.SHA256()):
            raise ReceiptError("Root certificate not trusted")
    else:
        logger.warning(
            "appstore_root_cert_path not set — chain verified but NOT pinned to Apple's root"
        )

    payload = json.loads(_b64url(payload_b64))

    # 4) A valid Apple signature isn't enough — the transaction must be for OUR
    #    app, not any app's receipt replayed at us. (Notification outer payloads
    #    carry no top-level bundleId; their inner transaction does.)
    bundle = payload.get("bundleId")
    if bundle and settings.appstore_bundle_id and bundle != settings.appstore_bundle_id:
        raise ReceiptError(f"Transaction is for a different app ({bundle})")

    return payload


# Notification types that end entitlement regardless of the dates in the payload.
_ENDING_TYPES = {"EXPIRED", "REFUND", "REVOKE", "GRACE_PERIOD_EXPIRED"}


def verify_notification(signed_payload: str) -> dict:
    """Verify an App Store Server Notification V2 and return a normalized dict:

    ``{"notification_type", "subtype", "transaction"}`` — where ``transaction`` is
    the verified ``signedTransactionInfo`` payload (or None). Raises
    :class:`ReceiptError` on any signature/chain failure.
    """
    outer = verify_transaction(signed_payload)          # same JWS format, Apple-signed
    data = outer.get("data") or {}
    signed_txn = data.get("signedTransactionInfo")
    transaction = verify_transaction(signed_txn) if signed_txn else None
    return {
        "notification_type": outer.get("notificationType", ""),
        "subtype": outer.get("subtype", ""),
        "transaction": transaction,
    }


def tier_from_notification(note: dict) -> tuple[str, datetime | None]:
    """Resolve (tier, expiry) for a verified notification. Ending events force free."""
    txn = note.get("transaction") or {}
    if note.get("notification_type") in _ENDING_TYPES:
        _tier, expires = tier_for(txn)
        return "free", expires
    return tier_for(txn)


def tier_for(payload: dict) -> tuple[str, datetime | None]:
    """Map a verified transaction payload to (tier, expiry).

    Expired or revoked subscriptions resolve to the free tier.
    """
    product = payload.get("productId", "")
    expires_ms = payload.get("expiresDate")
    revoked = payload.get("revocationDate") is not None
    expires = (
        datetime.fromtimestamp(expires_ms / 1000, tz=timezone.utc) if expires_ms else None
    )
    now = datetime.now(timezone.utc)
    if revoked or (expires is not None and expires < now):
        return "free", expires
    return _PRODUCT_TIERS.get(product, "free"), expires
