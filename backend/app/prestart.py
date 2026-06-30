"""Run before the server boots (from the Docker CMD).

Waits for the database, creates tables, and seeds demo data.
"""
import asyncio
import logging
import subprocess

from sqlalchemy import text

from app.core.database import SessionLocal, engine, init_db
from app.seed import seed

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("cerebro.prestart")


async def _wait_for_db(retries: int = 30, delay: float = 1.5) -> None:
    for attempt in range(1, retries + 1):
        try:
            async with engine.connect() as conn:
                await conn.execute(text("SELECT 1"))
            return
        except Exception as exc:  # noqa: BLE001
            logger.info("DB not ready (%d/%d): %s", attempt, retries, exc)
            await asyncio.sleep(delay)
    raise RuntimeError("Database did not become ready in time")


def _migrate() -> None:
    """Apply Alembic migrations. Falls back to create_all if none exist yet."""
    try:
        subprocess.run(["alembic", "upgrade", "head"], check=True)
        logger.info("Alembic migrations applied.")
    except Exception as exc:  # noqa: BLE001
        logger.warning("Alembic upgrade failed (%s); falling back to create_all.", exc)
        raise


async def main() -> None:
    await _wait_for_db()
    try:
        _migrate()
    except Exception:
        await init_db()   # dev fallback so the app still boots
    async with SessionLocal() as db:
        await seed(db)
    logger.info("Prestart complete.")


if __name__ == "__main__":
    asyncio.run(main())
