"""Auth errors, mapped to HTTP by handlers registered in app.main, returning
``{"message": ...}`` to match the other CereBroZen services.

``AuthorizationError`` -> 401 (we don't know who you are).
``ForbiddenError``     -> 403 (we do, and you may not).

Kept distinct on purpose: a client that gets a 401 retries with a fresh token, and
the Android session burns a refresh doing so. A role refusal is a real, final answer
— rotating the token would not change it, so it must not look like a stale one."""

from __future__ import annotations


class AuthorizationError(Exception):
    """Raised on any JWT auth failure (missing / invalid / expired token)."""

    def __init__(self, message: str = "Unauthorized") -> None:
        self.message = message
        super().__init__(message)


class ForbiddenError(Exception):
    """Raised when a valid token's role is not permitted on this route (-> HTTP 403)."""

    def __init__(self, message: str = "Forbidden") -> None:
        self.message = message
        super().__init__(message)
