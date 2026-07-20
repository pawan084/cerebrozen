"""Async engine/session. SQLite (aiosqlite) for dev/tests, Postgres in deploys."""

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy.pool import StaticPool

from app import config


class Base(DeclarativeBase):
    pass


_engine_kwargs = {}
if config.DATABASE_URL.startswith("sqlite") and ":memory:" in config.DATABASE_URL:
    # In-memory SQLite: one shared connection or every session sees a different db.
    _engine_kwargs = {"poolclass": StaticPool, "connect_args": {"check_same_thread": False}}

engine = create_async_engine(config.DATABASE_URL, **_engine_kwargs)
SessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


async def create_all() -> None:
    # Alembic comes in before the first post-launch schema change (tracked in
    # docs/TODO.md); create_all is sufficient while the schema is greenfield.
    from sqlalchemy import text

    from app import models  # noqa: F401 — register tables

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        # One paid purchase can't be replayed to Plus-ify multiple accounts: the provider
        # reference (Stripe sub id / Play token hash) is globally unique, DB-enforced — so a
        # concurrent double-submit that slips past the app's SELECT check fails at COMMIT
        # instead of granting two Plus subs. PARTIAL so the many free orgs (empty ref) don't
        # collide. Idempotent DDL (not a model Index) so it also lands on already-created
        # tables without Alembic; SQLite + Postgres both support `IF NOT EXISTS` + `WHERE`.
        await conn.execute(
            text(
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_provider_ref "
                "ON subscriptions (provider_ref) WHERE provider_ref <> ''"
            )
        )


async def get_session():
    async with SessionLocal() as session:
        yield session
