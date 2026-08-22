"""Google Play purchase verification (WC-15, the half that was missing).

Apple's side has been verified server-side since `services/appstore.py`: a
StoreKit 2 JWS, signature checked, chain pinned to Apple's root. Android had
**nothing** — `POST /users/me/subscription/verify` only understood Apple, so an
Android build's entitlement was whatever the client said it was. On a product
with a paid tier that is a forged-premium hole, and it is the reason WC-10's
missing Play Billing client is not the only thing standing between here and
taking money on Android.

## What this proves, and what it does not

Play hands the app a purchase as **two strings**: a JSON payload and a
base64 RSA signature over it, made with the developer key from Play Console.
Verifying that signature proves the purchase was issued **by Play, for this
app, and has not been edited since**. That is exactly the forgery this closes,
and it needs no network call and no service account — which is why it can run
in CI and on a laptop with no credentials.

It is deliberately NOT the whole story, and the docstring says so rather than
letting a later reader assume more:

* A signature is **not a live state check.** A refund, a chargeback or a
  cancellation after purchase does not change the signed payload. The Play
  Developer API (`purchases.subscriptionsv2.get`) is authoritative for current
  state and needs a service account; when one is configured that call belongs
  here, alongside this check rather than instead of it.
* A signature is **replayable** on its own — the same valid pair verifies
  forever, on any account. That hole is closed one level up, the same way
  Apple's is: the purchase token is stored UNIQUE on the user, so the first
  account to verify a purchase owns it and every later account is refused.

## Why SHA-1

`SHA1withRSA` is not a choice made here — it is the algorithm Play signs with,
and the verifier has to match the signer. Its weakness is collision resistance,
which would matter if an attacker could get us to sign something; here we only
verify, against a fixed public key we configure, so a second-preimage attack on
SHA-1 would be required — and that is not a break anyone has. Payloads that
arrive signed with SHA-256 verify too, so the day Google moves, this does not
need editing.
"""

from __future__ import annotations

import base64
import binascii
import json
import logging
from datetime import datetime, timezone

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

from app.core.config import settings

logger = logging.getLogger("cerebro.playstore")

#: Product id → tier. Hand-duplicated with `appstore._PRODUCT_TIERS` and the two
#: clients (ARCHITECTURE's cross-stack contract table) — the SAME ids on both
#: stores, so a subscriber who switches phones keeps the tier they paid for.
_PRODUCT_TIERS = {
    "com.cerebrozen.premium.monthly": "premium",
    "com.cerebrozen.premium.annual": "premium",
    "com.cerebrozen.premiumhuman.monthly": "premium_human",
    "com.cerebrozen.premiumhuman.annual": "premium_human",
}

#: Play's `purchaseState`: 0 purchased, 1 cancelled, 2 pending. Only 0 is a sale.
_PURCHASED = 0


class ReceiptError(Exception):
    """Any failure while verifying a Play purchase. Mirrors appstore's."""


def configured() -> bool:
    """Whether a Play public key is available to verify against.

    Without it nothing can be verified, so `/subscription/verify` must refuse
    rather than fall through to a default — the degrade-without-keys rule says
    an integration no-ops cleanly, and for a paywall the clean no-op is
    "unverified, therefore not premium".
    """
    return bool(settings.play_license_key.strip())


def _public_key() -> rsa.RSAPublicKey:
    """Play Console gives the key as base64 DER (SubjectPublicKeyInfo)."""
    raw = settings.play_license_key.strip()
    try:
        der = base64.b64decode(raw, validate=True)
        key = serialization.load_der_public_key(der)
    except (binascii.Error, ValueError) as exc:
        raise ReceiptError("play_license_key is not a valid base64 DER public key") from exc
    if not isinstance(key, rsa.RSAPublicKey):
        raise ReceiptError("play_license_key is not an RSA public key")
    return key


def verify_purchase(payload_json: str, signature_b64: str) -> dict:
    """Verify Play's signature over the purchase JSON and return the payload.

    Raises :class:`ReceiptError` for every failure mode — unconfigured, badly
    encoded, wrong key, tampered payload, or a payload that is not the shape
    Play sends. The caller turns that into a 400; nothing here decides
    entitlement.
    """
    if not configured():
        raise ReceiptError("Play verification is not configured on this server")

    key = _public_key()
    try:
        signature = base64.b64decode(signature_b64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ReceiptError("signature is not valid base64") from exc

    message = payload_json.encode()
    for algorithm in (hashes.SHA1(), hashes.SHA256()):
        try:
            key.verify(signature, message, padding.PKCS1v15(), algorithm)
            break
        except InvalidSignature:
            continue
    else:
        # Deliberately one message for every signature failure: distinguishing
        # "wrong key" from "edited payload" tells a forger which half to work on.
        raise ReceiptError("signature does not match the purchase payload")

    try:
        payload = json.loads(payload_json)
    except json.JSONDecodeError as exc:
        raise ReceiptError("purchase payload is not JSON") from exc
    if not isinstance(payload, dict):
        raise ReceiptError("purchase payload is not an object")

    # A signature over the wrong app's purchase is still a valid signature, so
    # the package is checked rather than assumed. Only when we know our own.
    expected = settings.play_package_name.strip()
    got = str(payload.get("packageName") or "").strip()
    if expected and got and got != expected:
        raise ReceiptError("purchase is for a different application")

    if not str(payload.get("purchaseToken") or "").strip():
        raise ReceiptError("purchase payload has no purchaseToken")
    return payload


def tier_for(payload: dict) -> tuple[str, datetime | None]:
    """Map a verified purchase to (tier, expiry), mirroring `appstore.tier_for`.

    Anything that is not a completed purchase of a known product resolves to
    `free`. A pending purchase (state 2) is someone mid-payment at a kiosk —
    not yet a subscriber, and not an error either.
    """
    if int(payload.get("purchaseState", _PURCHASED)) != _PURCHASED:
        return "free", None

    expires = None
    # Local purchase data carries an expiry only for subscriptions bought
    # through the older flow; when it is absent the Developer API is the only
    # source, and until that is wired the tier is granted without a local
    # expiry rather than guessed at.
    raw_expiry = payload.get("expiryTimeMillis")
    if raw_expiry is not None:
        try:
            expires = datetime.fromtimestamp(int(raw_expiry) / 1000, tz=timezone.utc)
        except (TypeError, ValueError, OSError, OverflowError):
            expires = None

    if expires is not None and expires < datetime.now(timezone.utc):
        return "free", expires
    return _PRODUCT_TIERS.get(str(payload.get("productId", "")), "free"), expires
