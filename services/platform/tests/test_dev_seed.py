"""The dev-only demo personas — one per surface.

They exist so a fresh checkout is usable immediately (and so the clients can offer a
one-click fill). They must NEVER exist in production: the seed itself checks the ENV,
and guard_production() refuses to boot a deployed environment with the flag on. Both
halves are pinned here — a demo account that survived into production would be a
publicly-known credential.
"""

import pytest
from sqlalchemy import func, select

from app import config
from app.models import ROLE_INTERNAL_ADMIN, ROLE_ORG_ADMIN, ROLE_USER, User


@pytest.fixture
async def demo(client):
    """The pristine baseline (internal admin) + the demo tenant seeded on top.

    The suite's `client` deliberately does NOT create the demo Org — a seeded org would
    appear in every org-listing/seat-count assertion (it broke test_edges once). So the
    tenant is seeded only where it's under test.
    """
    from app.main import _seed_demo_tenant

    await _seed_demo_tenant()
    return client


async def _login(client, email, password):
    return await client.post("/auth/login", data={"username": email, "password": password})


async def _me(client, email, password):
    tokens = (await _login(client, email, password)).json()
    r = await client.get("/users/me", headers={"Authorization": f"Bearer {tokens['access_token']}"})
    assert r.status_code == 200, r.text
    return r.json()


async def test_every_demo_persona_can_sign_in(demo):
    for email, password in (
        (config.DEV_ADMIN_EMAIL, config.DEV_ADMIN_PASSWORD),
        (config.DEMO_HR_EMAIL, config.DEMO_PASSWORD),
        (config.DEMO_MEMBER_EMAIL, config.DEMO_PASSWORD),
    ):
        r = await _login(demo, email, password)
        assert r.status_code == 200, f"{email} cannot sign in — the client offers it as one-click fill: {r.text}"


async def test_the_member_is_a_user_inside_the_demo_org(demo):
    # An org-less member would make the engine's org-scoped tenancy meaningless — the
    # employee app would be signing in as somebody who belongs to no tenant.
    me = await _me(demo, config.DEMO_MEMBER_EMAIL, config.DEMO_PASSWORD)
    assert me["role"] == ROLE_USER
    assert me["org_id"], "the demo member must belong to an org"


async def test_the_hr_persona_is_an_org_admin_of_the_same_org(demo):
    hr = await _me(demo, config.DEMO_HR_EMAIL, config.DEMO_PASSWORD)
    member = await _me(demo, config.DEMO_MEMBER_EMAIL, config.DEMO_PASSWORD)
    assert hr["role"] == ROLE_ORG_ADMIN
    assert hr["org_id"] == member["org_id"], "HR must administer the org the member is in"


async def test_the_internal_admin_has_no_org(client):
    # internal_admin is cross-tenant by definition; giving it an org would scope it.
    me = await _me(client, config.DEV_ADMIN_EMAIL, config.DEV_ADMIN_PASSWORD)
    assert me["role"] == ROLE_INTERNAL_ADMIN
    assert not me["org_id"]


async def test_seeding_twice_does_not_duplicate(demo):
    # The seed runs on every boot; a dev restart must not accumulate users/orgs.
    from app.db import SessionLocal
    from app.main import _seed_demo_tenant

    await _seed_demo_tenant()
    await _seed_demo_tenant()
    async with SessionLocal() as session:
        for email in (config.DEV_ADMIN_EMAIL, config.DEMO_HR_EMAIL, config.DEMO_MEMBER_EMAIL):
            n = (await session.execute(
                select(func.count()).select_from(User).where(User.email == email)
            )).scalar_one()
            assert n == 1, f"{email} seeded {n} times"


async def test_the_seed_is_a_noop_outside_a_dev_env(client, monkeypatch):
    from app.db import SessionLocal
    from app.main import _seed_demo_tenant

    async with SessionLocal() as session:
        before = (await session.execute(select(func.count()).select_from(User))).scalar_one()
    monkeypatch.setattr(config, "ENV", "production")
    await _seed_demo_tenant()  # must add nobody
    async with SessionLocal() as session:
        after = (await session.execute(select(func.count()).select_from(User))).scalar_one()
    assert after == before


async def test_the_seed_is_a_noop_when_the_flag_is_off(client, monkeypatch):
    from app.db import SessionLocal
    from app.main import _seed_demo_tenant

    monkeypatch.setattr(config, "SEED_DEV_ADMIN", False)
    await _seed_demo_tenant()
    async with SessionLocal() as session:
        n = (await session.execute(
            select(func.count()).select_from(User).where(User.email == "nobody@new.example")
        )).scalar_one()
    assert n == 0


def test_production_refuses_to_boot_with_the_demo_seed_enabled(monkeypatch):
    # The load-bearing guarantee: these accounts cannot reach production.
    monkeypatch.setattr(config, "ENV", "production")
    monkeypatch.setattr(config, "SEED_DEV_ADMIN", True)
    monkeypatch.setattr(config, "_JWT_SECRET_B64", "x" * 8)
    monkeypatch.setattr(config, "DATABASE_URL", "postgresql+asyncpg://x/y")
    with pytest.raises(RuntimeError, match="SEED_DEV_ADMIN"):
        config.guard_production()
