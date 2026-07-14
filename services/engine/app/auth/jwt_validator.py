"""JWT validation — HS512 shared-secret, ported from the reference auth service
(ai-specialized-generative-bot) so tokens validate identically across services.

The secret is base64-decoded in app.config; tokens arrive as
``Authorization: Bearer <jwt>``.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, Optional

import jwt

from app import config
from app.auth.errors import AuthorizationError

logger = logging.getLogger("cerebrozen.auth")

_BEARER_PREFIX = "Bearer "


def extract_bearer(authorization: Optional[str]) -> str:
    """Pull the raw JWT out of an Authorization header value."""
    if not authorization:
        raise AuthorizationError("Missing Authentication Token")
    return authorization.replace(_BEARER_PREFIX, "")


def decode_token(token: str) -> Dict[str, Any]:
    """Verify signature + expiry and return the decoded claims.

    PyJWT enforces ``exp`` by default, so an expired token raises
    ExpiredSignatureError (a subclass of InvalidTokenError) — mapped to 401.
    """
    try:
        return jwt.decode(token, config.JWT_SECRET, algorithms=[config.JWT_ALGORITHM])
    except jwt.InvalidTokenError as exc:  # expired / bad signature / malformed
        logger.warning("auth.invalid_token", extra={"error": str(exc)})
        raise AuthorizationError("Invalid JWT Token")
