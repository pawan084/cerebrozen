"""The administrator account has to survive the production seed guard.

``is_admin`` is written in exactly one place in the whole backend —
``seed._ensure_user`` — and no route grants or revokes it. Production always runs
with ``SEED_DEMO_DATA=false`` (``Settings._guard_production`` refuses to boot
otherwise), so while that call sat *below* the demo-data guard, every real deploy
came up with no administrator at all: the console was reachable only by an UPDATE
against Postgres, and ADMIN_EMAIL/ADMIN_PASSWORD sat in the environment looking
like they had provisioned something.

Nothing caught it because nothing ran the production path. These tests do.
"""
import uuid

from sqlalchemy import func, select

from app.core.database import SessionLocal
from app.models.user import User


async def test_a_production_boot_still_creates_the_administrator(monkeypatch):
    """The one that matters: seed with demo data OFF, as production does."""
    from app import seed as seed_mod

    email = f"admin-seed-{uuid.uuid4().hex[:8]}@cerebro.app"
    monkeypatch.setattr(seed_mod.settings, "seed_demo_data", False)
    monkeypatch.setattr(seed_mod.settings, "admin_email", email)
    monkeypatch.setattr(seed_mod.settings, "admin_password", "a-real-admin-password")

    async with SessionLocal() as s:
        await seed_mod.seed(s)

    async with SessionLocal() as s:
        admin = await s.scalar(select(User).where(User.email == email))

    assert admin is not None, "a production boot left nobody who can sign in to /admin"
    assert admin.is_admin is True


async def test_the_demo_account_did_not_follow_the_admin_above_the_guard(monkeypatch):
    """Moving the admin up must not drag demo data into production with it.

    Counted rather than asserted absent: other tests in this suite seed *with*
    demo data, and the account may already exist by the time this runs. What has
    to hold is that a demo-data-off boot adds nothing.
    """
    from app import seed as seed_mod

    monkeypatch.setattr(seed_mod.settings, "seed_demo_data", False)
    monkeypatch.setattr(seed_mod.settings, "admin_email", f"admin-{uuid.uuid4().hex[:8]}@cerebro.app")
    monkeypatch.setattr(seed_mod.settings, "admin_password", "a-real-admin-password")

    async def _demo_users() -> int:
        async with SessionLocal() as s:
            return await s.scalar(
                select(func.count()).select_from(User).where(User.email == "pawan@cerebro.app")
            )

    before = await _demo_users()
    async with SessionLocal() as s:
        await seed_mod.seed(s)
    assert await _demo_users() == before


async def test_rotating_the_env_password_does_not_reset_a_live_admin(monkeypatch):
    """`seed` runs on every container start, so it must not be a password reset.

    An administrator who changes their password in the product would otherwise
    have it silently reverted to whatever ADMIN_PASSWORD still said at the next
    reboot — and the old value stays in the deployment environment long after.
    """
    from app import seed as seed_mod

    email = f"admin-rotate-{uuid.uuid4().hex[:8]}@cerebro.app"
    monkeypatch.setattr(seed_mod.settings, "seed_demo_data", False)
    monkeypatch.setattr(seed_mod.settings, "admin_email", email)

    monkeypatch.setattr(seed_mod.settings, "admin_password", "the-original-password")
    async with SessionLocal() as s:
        await seed_mod.seed(s)
    async with SessionLocal() as s:
        first = (await s.scalar(select(User).where(User.email == email))).hashed_password

    monkeypatch.setattr(seed_mod.settings, "admin_password", "a-different-password")
    async with SessionLocal() as s:
        await seed_mod.seed(s)
    async with SessionLocal() as s:
        second = (await s.scalar(select(User).where(User.email == email))).hashed_password

    assert first == second, "a reboot rewrote the administrator's password from the environment"
