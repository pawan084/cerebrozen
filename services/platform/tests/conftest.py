"""Fixtures: in-memory SQLite, seeded internal admin, an org with an admin,
and authenticated clients per role. Runs with zero external services."""

import os

os.environ["ENV"] = "test"
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///:memory:"
# Fast hashes in tests: PBKDF2 at 600k iterations is ~0.3s per hash — right in
# production, wasteful across hundreds of test logins.
os.environ["CEREBROZEN_PBKDF2_ITERATIONS"] = "1000"

import httpx
import pytest

from app import config
from app.main import create_app


@pytest.fixture
async def client():
    app = create_app()
    async with httpx.ASGITransport(app=app) as transport:
        # ASGITransport doesn't run lifespan; drive it via httpx's context.
        async with httpx.AsyncClient(
            transport=transport, base_url="http://test"
        ) as c:
            # Trigger lifespan manually: create tables + seed.
            from app.db import create_all
            from app.main import _seed_dev_admin

            await create_all()
            await _seed_dev_admin()
            yield c
    # Dispose the engine so the next test's :memory: db starts clean.
    from app.db import engine
    from app.db import Base

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


async def _login(client, email, password):
    r = await client.post("/auth/login", data={"username": email, "password": password})
    assert r.status_code == 200, r.text
    return r.json()


def _auth(tokens) -> dict:
    return {"Authorization": f"Bearer {tokens['access_token']}"}


@pytest.fixture
async def internal(client):
    """(headers, tokens) for the seeded internal admin."""
    tokens = await _login(client, config.DEV_ADMIN_EMAIL, config.DEV_ADMIN_PASSWORD)
    return _auth(tokens), tokens


@pytest.fixture
async def org_with_admin(client, internal):
    """An org 'acme' with an accepted org_admin; returns ids, headers, tokens."""
    headers, _ = internal
    r = await client.post(
        "/orgs",
        json={"name": "Acme Corp", "slug": "acme", "seats_total": 3},
        headers=headers,
    )
    assert r.status_code == 201, r.text
    org = r.json()

    # Internal admins have no org; provision the first org_admin by writing an
    # invitation through an org-scoped path requires an org admin — so seed the
    # first one directly through the invitation acceptance flow with a token
    # minted by the internal admin via a direct DB write.
    from datetime import datetime, timedelta, timezone

    from app.db import SessionLocal
    from app.models import Invitation
    from app.security import new_opaque_token

    raw, token_hash = new_opaque_token()
    async with SessionLocal() as session:
        session.add(
            Invitation(
                org_id=org["id"],
                email="hr@acme.example",
                role="org_admin",
                token_hash=token_hash,
                expires_at=datetime.now(timezone.utc) + timedelta(days=1),
            )
        )
        await session.commit()
    r = await client.post(
        "/auth/accept-invitation",
        json={"token": raw, "name": "HR Lead", "password": "hunter2hunter2"},
    )
    assert r.status_code == 201, r.text
    admin_tokens = r.json()
    return {
        "org": org,
        "admin_headers": _auth(admin_tokens),
        "admin_tokens": admin_tokens,
    }
