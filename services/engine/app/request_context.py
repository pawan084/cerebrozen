"""Per-request context variables — stamped by middleware, read by the log filter.

All three vars default to "" so the filter silently skips them on non-user
requests (startup, health checks) where no middleware fires or the path is
excluded.
"""

from contextvars import ContextVar

request_id:     ContextVar[str] = ContextVar("request_id",     default="")
ctx_user_id:    ContextVar[str] = ContextVar("ctx_user_id",    default="")
ctx_session_id: ContextVar[str] = ContextVar("ctx_session_id", default="")
