"""Every org write route refuses a read-only role — not just the one that was tested.

The enforcement already exists and works: `_require_write` guards all six write
routes in `api/routes/organizations.py`, and `ROLES_CAN_WRITE` is
`{benefits_owner, programme_admin}`, so `analyst` and `privacy_reviewer` are
read-only. `test_org.py::test_analyst_can_read_but_not_write` pins that — for
`POST /org/groups`.

One of six. The other five are guarded by a hand-written `_require_write(admin)`
call at the top of each function, which is exactly the kind of guard a new route
is added without: nothing fails, the route simply works for everyone. This closes
that by asserting the property across the whole surface, so a seventh write route
added without the call has a failing test rather than a silent authorization
hole.

Written as a matrix rather than five more copies of one test: the interesting
thing is the *product* of routes and roles, and enumerating it makes the next
route a one-line addition.
"""
import pytest

from app.core.database import SessionLocal
from app.models.organization import (
    ROLE_ANALYST,
    ROLE_BENEFITS_OWNER,
    ROLE_PRIVACY_REVIEWER,
    ROLES_CAN_WRITE,
)

from .test_org import _make_org, _signup

#: Every mutating route on /org, as (method, path, body). Keep in step with
#: `api/routes/organizations.py` — a new write route belongs here the day it is
#: written, which is the point of the test below that counts them.
WRITE_ROUTES = [
    ("patch", "/org", {"retention_months": 12}),
    ("post", "/org/groups", {"name": "Everyone"}),
    # A *valid* body on purpose. FastAPI validates the request body before the
    # route function runs, so an invalid one returns 422 without ever reaching
    # `_require_write` — the test would then pass or fail for reasons that have
    # nothing to do with the role. `MembershipCreate` forbids extra keys and
    # requires `email`, so this is the smallest body that gets as far as the
    # authorization check.
    ("post", "/org/members", {"email": "seat-role-probe@example.com"}),
    ("post", "/org/members/import", {"csv": "external_ref\ne-2\n"}),
    ("post", "/org/programmes", {"programme_slug": "sleep-reset"}),
]

READ_ONLY_ROLES = [ROLE_ANALYST, ROLE_PRIVACY_REVIEWER]


@pytest.mark.parametrize("role", READ_ONLY_ROLES)
@pytest.mark.parametrize("method,path,body", WRITE_ROUTES)
async def test_a_read_only_role_is_refused_by_every_write_route(client, role, method, path, body):
    email, _ = await _signup(client, f"ro-{role[:6]}")
    async with SessionLocal() as db:
        await _make_org(db, name=f"RO {role} {path}", admin_email=email, role=role)

    r = await getattr(client, method)(path, json=body)

    # 403 specifically — not 404, not 422. A route that rejects a read-only role
    # by accident (a missing record, a schema quibble) would pass a looser
    # assertion while leaving the authorization hole open.
    assert r.status_code == 403, f"{method.upper()} {path} as {role} returned {r.status_code}"
    assert "may read reports but not change" in r.json()["detail"]


@pytest.mark.parametrize("method,path,body", WRITE_ROUTES)
async def test_the_owner_is_not_refused_by_the_same_guard(client, method, path, body):
    """The mirror image: the guard must not be refusing everyone.

    A `_require_write` that raised unconditionally would pass every assertion
    above and break the product completely, so the matrix is only meaningful
    with this alongside it. Asserting "not 403" rather than a success code —
    these routes have their own validation, and this test is about the role
    check, not about whether the body is well-formed.
    """
    email, _ = await _signup(client, "owner-w")
    async with SessionLocal() as db:
        await _make_org(db, name=f"Owner {path}", admin_email=email, role=ROLE_BENEFITS_OWNER)

    r = await getattr(client, method)(path, json=body)

    assert r.status_code != 403, f"{method.upper()} {path} refused a benefits_owner"


def test_the_write_roles_are_the_two_we_think_they_are():
    """Pins the set itself, so widening it becomes a deliberate, reviewed act.

    Adding a role to ROLES_CAN_WRITE is a one-word change with the blast radius
    of every route above; it should not be possible to do quietly.
    """
    assert ROLES_CAN_WRITE == {ROLE_BENEFITS_OWNER, "programme_admin"}
    assert ROLE_ANALYST not in ROLES_CAN_WRITE
    assert ROLE_PRIVACY_REVIEWER not in ROLES_CAN_WRITE
