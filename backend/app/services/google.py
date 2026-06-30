"""Sign in with Google — verify the ID token the client obtains from Google.

Mirrors ``app/services/apple.py``: the iOS app runs Google's OAuth flow and POSTs
the resulting ID token (a signed JWT) to ``/auth/google``. We verify that JWT
against Google's public keys (RS256), checking the issuer and (when configured)
the audience, then trust the ``sub`` / ``email`` claims to find-or-create the user.

No new dependency: ``python-jose[cryptography]`` verifies RS256 from a JWK and
``httpx`` fetches Google's certs. The key set is cached in-process.
"""
from __future__ import annotations

import logging

import httpx
from jose import jwt
from jose.exceptions import JWTError

from app.core.config import settings

logger = logging.getLogger("cerebro.google")

_GOOGLE_ISSUERS = {"accounts.google.com", "https://accounts.google.com"}
_GOOGLE_KEYS_URL = "https://www.googleapis.com/oauth2/v3/certs"

_keys_cache: list[dict] | None = None


async def _google_keys() -> list[dict]:
    """Fetch (and cache) Google's public signing keys."""
    global _keys_cache
    if _keys_cache is None:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(_GOOGLE_KEYS_URL)
            resp.raise_for_status()
            _keys_cache = resp.json().get("keys", [])
    return _keys_cache


async def verify_id_token(id_token: str) -> dict | None:
    """Return the verified claims (``sub``, ``email``, …) or ``None`` if invalid.

    Never raises — a malformed/foreign/expired token just yields ``None`` so the
    route can answer 401 cleanly.
    """
    try:
        header = jwt.get_unverified_header(id_token)
    except JWTError:
        return None
    kid = header.get("kid")

    try:
        keys = await _google_keys()
    except Exception as exc:  # pragma: no cover - network guard
        logger.warning("Could not fetch Google keys: %s", exc)
        return None

    key = next((k for k in keys if k.get("kid") == kid), None)
    if key is None:
        # Google rotated keys — drop the cache and retry once.
        global _keys_cache
        _keys_cache = None
        try:
            keys = await _google_keys()
        except Exception:  # pragma: no cover
            return None
        key = next((k for k in keys if k.get("kid") == kid), None)
        if key is None:
            return None

    try:
        # Google publishes two valid issuers, so we check `iss` ourselves below
        # rather than via jose's single-issuer option. Audience is only enforced
        # when a client id is configured.
        claims = jwt.decode(
            id_token,
            key,
            algorithms=["RS256"],
            audience=settings.google_client_id or None,
            options={"verify_aud": bool(settings.google_client_id)},
        )
    except JWTError as exc:
        logger.info("Google token rejected: %s", exc)
        return None

    if claims.get("iss") not in _GOOGLE_ISSUERS:
        return None
    if claims.get("email") and claims.get("email_verified") is False:
        return None
    return claims
