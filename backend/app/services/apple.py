"""Sign in with Apple — verify the identity token Apple returns to the client.

The iOS app performs the Apple authorization and POSTs the resulting identity
token (a signed JWT) to ``/auth/apple``. We verify that JWT against Apple's
public keys (RS256), checking the issuer and audience, then trust the ``sub``
(stable Apple user id) and ``email`` claims to find-or-create the user.

No new dependency: ``python-jose[cryptography]`` verifies RS256 from a JWK, and
``httpx`` fetches Apple's JWKS. The key set is cached in-process.
"""
from __future__ import annotations

import logging
import time

import httpx
from jose import jwt
from jose.exceptions import JWTError

from app.core.config import settings

logger = logging.getLogger("cerebro.apple")

_APPLE_ISSUER = "https://appleid.apple.com"
_APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys"

_keys_cache: list[dict] | None = None
_keys_fetched_at: float = 0.0
_KEYS_TTL_SECONDS = 6 * 3600   # refetch periodically so key rotation propagates


async def _apple_keys() -> list[dict]:
    """Fetch (and cache, with a TTL) Apple's public signing keys."""
    global _keys_cache, _keys_fetched_at
    if _keys_cache is None or time.monotonic() - _keys_fetched_at > _KEYS_TTL_SECONDS:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(_APPLE_KEYS_URL)
            resp.raise_for_status()
            _keys_cache = resp.json().get("keys", [])
            _keys_fetched_at = time.monotonic()
    return _keys_cache


async def verify_identity_token(identity_token: str) -> dict | None:
    """Return the verified claims (``sub``, ``email``, …) or ``None`` if invalid.

    Never raises — a malformed/foreign/expired token just yields ``None`` so the
    route can answer 401 cleanly.
    """
    try:
        header = jwt.get_unverified_header(identity_token)
    except JWTError:
        return None
    kid = header.get("kid")

    try:
        keys = await _apple_keys()
    except Exception as exc:  # pragma: no cover - network guard
        logger.warning("Could not fetch Apple keys: %s", exc)
        return None

    key = next((k for k in keys if k.get("kid") == kid), None)
    if key is None:
        # Apple rotated keys — drop the cache and retry once.
        global _keys_cache
        _keys_cache = None
        try:
            keys = await _apple_keys()
        except Exception:  # pragma: no cover
            return None
        key = next((k for k in keys if k.get("kid") == kid), None)
        if key is None:
            return None

    try:
        return jwt.decode(
            identity_token,
            key,
            algorithms=["RS256"],
            audience=settings.apple_audience,
            issuer=_APPLE_ISSUER,
        )
    except JWTError as exc:
        logger.info("Apple token rejected: %s", exc)
        return None
