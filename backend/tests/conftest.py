import os
import tempfile

os.environ["TESTING"] = "1"  # must be set before importing the app/engine
# Isolated media dir — app.main mounts StaticFiles(MEDIA_ROOT) at import time,
# and narration tests write real files there.
os.environ.setdefault("MEDIA_ROOT", tempfile.mkdtemp(prefix="cerebro-media-"))

# Tests run in their OWN database (`<name>_test`, created on demand) so
# create_all never races the dev database's Alembic state or pollutes dev
# data. Active whenever DATABASE_URL is set in the environment (the container
# and CI paths); a bare local run keeps the legacy shared-DB behavior.
_BASE_DB_URL = os.environ.get("DATABASE_URL", "")
if _BASE_DB_URL and not _BASE_DB_URL.rsplit("/", 1)[-1].endswith("_test"):
    os.environ["DATABASE_URL"] = _BASE_DB_URL + "_test"

import uuid  # noqa: E402

import asyncpg  # noqa: E402
import pytest_asyncio  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402

from app.core.database import init_db  # noqa: E402
from app.main import app  # noqa: E402

_test_db_ready = False


async def _ensure_test_db():
    """Recreate the dedicated test database once per run — a fresh schema every
    time, so model changes never leave a stale-column database behind (needs
    CREATEDB on the base connection — true for the compose superuser and CI's
    service user)."""
    global _test_db_ready
    if _test_db_ready or not _BASE_DB_URL:
        _test_db_ready = True
        return
    name = os.environ["DATABASE_URL"].rsplit("/", 1)[-1]
    conn = await asyncpg.connect(dsn=_BASE_DB_URL.replace("postgresql+asyncpg://", "postgresql://"))
    try:
        await conn.execute(f'DROP DATABASE IF EXISTS "{name}" WITH (FORCE)')
        await conn.execute(f'CREATE DATABASE "{name}"')
    finally:
        await conn.close()
    _test_db_ready = True


@pytest_asyncio.fixture(autouse=True)
async def _schema():
    """Ensure tables exist (idempotent; requires Postgres to be reachable)."""
    await _ensure_test_db()
    await init_db()


@pytest_asyncio.fixture
async def client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c


@pytest_asyncio.fixture
async def auth_client(client):
    """A client with a freshly-registered user's bearer token applied."""
    email = f"user-{uuid.uuid4().hex[:10]}@test.app"
    resp = await client.post("/auth/signup", json={"email": email, "password": "password123", "name": "Test"})
    assert resp.status_code == 201, resp.text
    token = resp.json()["access_token"]
    client.headers["Authorization"] = f"Bearer {token}"
    return client


@pytest_asyncio.fixture
async def admin_client(client):
    """A client whose user has been promoted to admin (for /admin routes)."""
    from sqlalchemy import update  # noqa: E402

    from app.core.database import SessionLocal  # noqa: E402
    from app.models.user import User  # noqa: E402

    email = f"admin-{uuid.uuid4().hex[:10]}@test.app"
    resp = await client.post("/auth/signup", json={"email": email, "password": "password123", "name": "Admin"})
    assert resp.status_code == 201, resp.text
    token = resp.json()["access_token"]
    async with SessionLocal() as s:
        await s.execute(update(User).where(User.email == email).values(is_admin=True))
        await s.commit()
    client.headers["Authorization"] = f"Bearer {token}"
    return client
