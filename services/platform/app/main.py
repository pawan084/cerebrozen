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
from app.models import ROLE_INTERNAL_ADMIN, User
from app.routers import auth, demo, orgs, users
from app.security import hash_password

logger = logging.getLogger("cerebrozen.platform")

_DEV_ENVS = {"local", "dev", "development", "test", "ci"}


async def _seed_dev_admin() -> None:
    if not (config.SEED_DEV_ADMIN and config.ENV in _DEV_ENVS):
        return
    async with SessionLocal() as session:
        existing = (
            await session.execute(
                select(User).where(User.email == config.DEV_ADMIN_EMAIL)
            )
        ).scalar_one_or_none()
        if existing is None:
            session.add(
                User(
                    email=config.DEV_ADMIN_EMAIL,
                    name="Dev Admin",
                    password_hash=hash_password(config.DEV_ADMIN_PASSWORD),
                    role=ROLE_INTERNAL_ADMIN,
                )
            )
            await session.commit()
            logger.info("seed.dev_admin_created email=%s", config.DEV_ADMIN_EMAIL)


@asynccontextmanager
async def _lifespan(app: FastAPI):
    config.guard_production()
    await create_all()
    await _seed_dev_admin()
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

    @app.get("/health")
    async def health() -> dict:
        return {"status": "ok", "service": "platform", "env": config.ENV}

    return app


app = create_app()
