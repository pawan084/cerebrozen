"""Platform API — orgs, seats, auth, privacy, demo pipeline.

Issues the JWTs the coaching engine validates (shared HS512 secret; claims
carry org_id + user identity). Boots on SQLite with zero configuration;
production runs Postgres and the boot-guard refuses insecure defaults."""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import select

from app import config
from app.db import SessionLocal, create_all
from app.models import ROLE_INTERNAL_ADMIN, ROLE_ORG_ADMIN, ROLE_USER, Org, User
from app.routers import analytics, auth, content, demo, orgs, users
from app.security import hash_password

logger = logging.getLogger("cerebrozen.platform")

_DEV_ENVS = {"local", "dev", "development", "test", "ci"}


async def _add_user(session, email: str, name: str, role: str, password: str,
                    org_id: str | None = None) -> None:
    """Add one seed user if absent. Idempotent — the seed runs on every boot."""
    existing = (await session.execute(select(User).where(User.email == email))).scalar_one_or_none()
    if existing is not None:
        return
    session.add(User(email=email, name=name, role=role, org_id=org_id,
                     password_hash=hash_password(password)))
    logger.info("seed.dev_user_created email=%s role=%s", email, role)


async def _seed_dev_admin() -> None:
    """The internal admin, so a fresh checkout can reach the ops console.

    Kept deliberately MINIMAL — org-less, one user. The test suite seeds exactly this as
    its baseline, so anything added here shows up in every org-listing and seat-count
    assertion in the suite. The demo tenant lives in `_seed_demo_tenant` for that reason.
    """
    if not (config.SEED_DEV_ADMIN and config.ENV in _DEV_ENVS):
        return
    async with SessionLocal() as session:
        await _add_user(session, config.DEV_ADMIN_EMAIL, "Dev Admin",
                        ROLE_INTERNAL_ADMIN, config.DEV_ADMIN_PASSWORD)
        await session.commit()


async def _seed_demo_tenant() -> None:
    """A demo tenant + a persona for each remaining surface, so a fresh checkout is
    usable immediately (and the clients can offer one-click sign-in):

      hr@   → org_admin of "Demo Co"  the HR surface (overview, analytics, people, invite)
      demo@ → user of "Demo Co"       the employee app — a member who actually HAS an org,
                                      so the engine's org-scoped tenancy is real

    Separate from `_seed_dev_admin` on purpose: this creates an Org, and the test suite
    must start from a pristine, org-less database. Gated twice (SEED_DEV_ADMIN + a dev
    ENV), and guard_production() refuses to boot a deployed env with the flag on — so
    these accounts cannot exist in production. Idempotent.
    """
    if not (config.SEED_DEV_ADMIN and config.ENV in _DEV_ENVS):
        return
    async with SessionLocal() as session:
        org = (
            await session.execute(select(Org).where(Org.slug == config.DEMO_ORG_SLUG))
        ).scalar_one_or_none()
        if org is None:
            org = Org(name=config.DEMO_ORG_NAME, slug=config.DEMO_ORG_SLUG, seats_total=50)
            session.add(org)
            await session.flush()  # need org.id for the two members below
            logger.info("seed.demo_org_created slug=%s", config.DEMO_ORG_SLUG)
        await _add_user(session, config.DEMO_HR_EMAIL, "Dana Okafor", ROLE_ORG_ADMIN,
                        config.DEMO_PASSWORD, org_id=org.id)
        await _add_user(session, config.DEMO_MEMBER_EMAIL, "Alex Rivera", ROLE_USER,
                        config.DEMO_PASSWORD, org_id=org.id)
        await session.commit()


@asynccontextmanager
async def _lifespan(app: FastAPI):
    config.guard_production()
    await create_all()
    await _seed_dev_admin()
    await _seed_demo_tenant()
    yield


def create_app() -> FastAPI:
    app = FastAPI(title="CereBroZen Platform", lifespan=_lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=config.CORS_ORIGINS,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(auth.router)
    app.include_router(orgs.router)
    app.include_router(users.router)
    app.include_router(demo.router)
    app.include_router(analytics.router)
    app.include_router(content.router)

    @app.get("/health")
    async def health() -> dict:
        return {"status": "ok", "service": "platform", "env": config.ENV}

    return app


app = create_app()
