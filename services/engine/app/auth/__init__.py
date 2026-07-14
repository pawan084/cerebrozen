"""JWT auth (HS512 shared secret) — same scheme as the other CereBroZen services,
so tokens validate across services.

Use ``require_auth`` as a FastAPI route dependency to protect an endpoint.
"""

from app.auth.dependencies import auth_enabled, require_auth
from app.auth.errors import AuthorizationError

__all__ = ["require_auth", "auth_enabled", "AuthorizationError"]
