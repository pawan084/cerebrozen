"""Per-request context variables — stamped by middleware, read by the log filter.

All three vars default to "" so the filter silently skips them on non-user
requests (startup, health checks) where no middleware fires or the path is
excluded.
"""

import contextvars
from concurrent.futures import ThreadPoolExecutor
from contextvars import ContextVar

request_id:     ContextVar[str] = ContextVar("request_id",     default="")
ctx_user_id:    ContextVar[str] = ContextVar("ctx_user_id",    default="")
ctx_session_id: ContextVar[str] = ContextVar("ctx_session_id", default="")


class ContextThreadPoolExecutor(ThreadPoolExecutor):
    """A ThreadPoolExecutor whose workers inherit the submitting thread's ContextVars.

    A bare executor does NOT copy ContextVars, and background work here WRITES to
    the stores — so a worker would run with ``ctx_org_id`` unset and fall back to
    the DEFAULT org. That is not merely a logging blemish: the tenancy filters are
    built from ``current_org()``, so a background write lands in the wrong tenant,
    and the store's own "does this row match the whole filter" guard then silently
    drops the foreground write to that same document. The symptom is invisible —
    no exception, no error log, just a transcript that is never persisted.

    A fresh copy PER TASK, never one shared Context: ``Context.run()`` is not
    reentrant and raises RuntimeError if the same Context is entered concurrently
    from two threads (same rule as rag/placeholders.py).
    """

    def submit(self, fn, /, *args, **kwargs):  # type: ignore[override]
        return super().submit(contextvars.copy_context().run, fn, *args, **kwargs)
