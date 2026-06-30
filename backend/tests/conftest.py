import os

os.environ["TESTING"] = "1"  # must be set before importing the app/engine

import uuid  # noqa: E402

import pytest_asyncio  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402

from app.core.database import init_db  # noqa: E402
from app.main import app  # noqa: E402


@pytest_asyncio.fixture(autouse=True)
async def _schema():
    """Ensure tables exist (idempotent; requires Postgres to be reachable)."""
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
