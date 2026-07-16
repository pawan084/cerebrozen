"""The streaming turn must stay inside its tenant.

SECURITY.md calls tenancy "the sharpest inherited edge" and commits to `org_id` being "a
first-class column/key on every engine store... Enforced in code, tested with cross-tenant
access tests". Those tests exercised the STORES. They did not exercise the SSE path, and
that is where it broke:

`_sse_response` runs the graph in a bare worker thread and re-stamps the correlation ids
into that thread's context — request_id, user_id, session_id. It did not re-stamp
`ctx_org_id`, which is not a correlation id but the tenancy key every store writes with. So
`current_org()` fell back to DEFAULT_ORG inside the worker and EVERY streamed turn recorded
its conversation under org "default" instead of the caller's.

Nothing surfaced. The writes succeeded, and the reads — which run in the request context,
with the real org — simply never found them: /v1/sessions, /v1/sessions/resumable and the
history came back empty for every real tenant, so the Resume pill could not appear and no
one could reopen a thread.
"""

from __future__ import annotations

import threading

import pytest

from app.tenancy import DEFAULT_ORG, ctx_org_id, current_org


@pytest.fixture
def scoped_to_acme():
    """Set the tenant, and PUT IT BACK.

    ContextVars outlive a test function — they are not fixtures. Leaving this set made
    every later test in the session run scoped to "acme", so store tests whose fixtures
    write under the default org silently found nothing and asserted "fresh" where they
    meant "repeat". Eighteen unrelated failures, none of them reproducible alone.
    """
    token = ctx_org_id.set("acme")
    try:
        yield "acme"
    finally:
        ctx_org_id.reset(token)


def test_a_bare_worker_thread_does_not_inherit_the_org(scoped_to_acme):
    """The mechanism, stated plainly: contextvars do not cross a raw Thread. This is why
    the snapshot-and-restamp in _sse_response exists at all."""
    seen = {}
    t = threading.Thread(target=lambda: seen.setdefault("org", current_org()))
    t.start()
    t.join()
    assert seen["org"] == DEFAULT_ORG, "if this ever passes, the restamp is no longer needed"


def test_the_sse_worker_restamps_the_tenancy_key():
    """Pinned by source, because the bug was an ABSENCE — a missing line in the snapshot
    list, which no behavioural assertion on the router would have noticed."""
    import inspect

    from app.routers import sessions

    src = inspect.getsource(sessions._sse_response)
    assert "ctx_org_id" in src, "the SSE worker lost the tenancy key; every streamed turn writes to DEFAULT_ORG"
    assert "_snap_org" in src, "the org must be captured in the request context and restamped in the worker"


def test_every_context_var_the_worker_needs_is_snapshotted():
    """A guard against the next one. If a new ContextVar becomes load-bearing inside the
    graph, it has to be added here too — the failure mode is silent by construction."""
    import inspect

    from app.routers import sessions

    src = inspect.getsource(sessions._sse_response)
    for var in ("request_id", "ctx_user_id", "ctx_session_id", "ctx_org_id"):
        assert var in src, f"{var} is not carried into the SSE worker"


def test_the_restamp_happens_inside_the_worker_not_only_at_capture():
    import inspect

    from app.routers import sessions

    src = inspect.getsource(sessions._sse_response)
    worker = src[src.index("def _worker"):]
    assert "_co.set(_snap_org)" in worker or "_snap_org" in worker, \
        "the org is captured but never applied in the worker thread"
