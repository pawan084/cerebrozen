"""Coverage for the production config guard, auth deps, and security helpers."""
import uuid

import pytest

from app.core.config import Settings
from app.core.security import create_access_token, create_reset_token, verify_password


# ── Production guard (_guard_production) ─────────────────────────────────
def test_production_guard_rejects_insecure():
    with pytest.raises(ValueError) as exc:
        Settings(
            _env_file=None, env="production", secret_key="dev-secret",
            admin_password="admin12345", seed_demo_data=True,
            cors_origins_raw="https://a.com,*",
        )
    assert "Insecure production config" in str(exc.value)


def test_production_guard_accepts_secure():
    s = Settings(
        _env_file=None, env="production", secret_key="k" * 40,
        admin_password="A-strong-pass-123", seed_demo_data=False,
    )
    assert s.is_production   # secure config boots without raising


def test_development_skips_guard():
    s = Settings(_env_file=None, env="development", secret_key="dev-secret")
    assert not s.is_production


# ── get_current_user error branches ─────────────────────────────────────
async def test_current_user_rejects_malformed_token(client):
    client.headers["Authorization"] = "Bearer not-a-jwt"
    assert (await client.get("/users/me")).status_code == 401


async def test_current_user_rejects_nonuuid_sub(client):
    client.headers["Authorization"] = f"Bearer {create_access_token('not-a-uuid')}"
    assert (await client.get("/users/me")).status_code == 401


async def test_current_user_rejects_unknown_user(client):
    client.headers["Authorization"] = f"Bearer {create_access_token(str(uuid.uuid4()))}"
    assert (await client.get("/users/me")).status_code == 401


# ── security helpers ────────────────────────────────────────────────────
def test_verify_password_bad_hash_is_false():
    assert verify_password("whatever", "not-a-bcrypt-hash") is False


def test_reset_token_is_created():
    assert isinstance(create_reset_token("some-subject"), str)
