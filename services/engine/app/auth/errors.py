"""Auth errors. ``AuthorizationError`` is mapped to HTTP 401 by the app handler
(registered in app.main), returning ``{"message": ...}`` to match the other
CereBroZen services."""

from __future__ import annotations


class AuthorizationError(Exception):
    """Raised on any JWT auth failure (missing / invalid / expired token)."""

    def __init__(self, message: str = "Unauthorized") -> None:
        self.message = message
        super().__init__(message)
