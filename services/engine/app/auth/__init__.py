"""JWT auth (HS512 shared secret) — same scheme as the other CereBroZen services,
so tokens validate across services.

Use ``require_auth`` as a FastAPI route dependency to protect an endpoint.
"""

from app.auth.dependencies import auth_enabled, require_auth, require_internal_admin
from app.auth.errors import AuthorizationError, ForbiddenError

__all__ = [
    "require_auth",
    "require_internal_admin",
    "auth_enabled",
    "AuthorizationError",
    "ForbiddenError",
]
