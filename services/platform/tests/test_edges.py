"""Edge paths: lifespan, expired/disabled credentials, crafted invitations,
and config parsing — the branches the happy-path suite doesn't reach."""

from datetime import datetime, timedelta, timezone

from sqlalchemy import select, update

from app import config
from app.db import SessionLocal
from app.models import Invitation, Org, RefreshToken, User
from app.security import new_opaque_token


async def test_the_lifespan_boots_creates_tables_and_seeds(client):
    from app.main import _lifespan, _seed_dev_admin, create_app

    app = create_app()
    async with _lifespan(app):
        pass  # guard + create_all + seed (idempotent: admin already exists)
    # And the no-seed branch:
    import app.config as cfg

    old = cfg.SEED_DEV_ADMIN
    cfg.SEED_DEV_ADMIN = False
    try:
        await _seed_dev_admin()
    finally:
        cfg.SEED_DEV_ADMIN = old


async def test_me_returns_profile_with_org_name(client, org_with_admin):
    r = await client.get("/users/me", headers=org_with_admin["admin_headers"])
    assert r.status_code == 200
    body = r.json()
    assert body["email"] == "hr@acme.example"
    assert body["org_name"] == "Acme Corp"


async def test_internal_me_has_no_org(client, internal):
    headers, _ = internal
    body = (await client.get("/users/me", headers=headers)).json()
    assert body["org_id"] is None and body["org_name"] is None
    # ...and /orgs/me is meaningless for internal staff:
    assert (await client.get("/orgs/me", headers=headers)).status_code == 400


async def test_internal_admin_lists_orgs(client, internal, org_with_admin):
    headers, _ = internal
    rows = (await client.get("/orgs", headers=headers)).json()
    assert [o["slug"] for o in rows] == ["acme"]
    assert rows[0]["seats_used"] == 1


async def test_an_expired_refresh_token_is_401(client, internal):
    _, tokens = internal
    from app.security import hash_opaque

    async with SessionLocal() as session:
        await session.execute(
            update(RefreshToken)
            .where(RefreshToken.token_hash == hash_opaque(tokens["refresh_token"]))
            .values(expires_at=datetime.now(timezone.utc) - timedelta(days=1))
        )
        await session.commit()
    r = await client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 401 and "expired" in r.json()["detail"]


async def test_a_disabled_users_refresh_and_access_both_die(client, org_with_admin, internal):
    admin_headers, admin_tokens = org_with_admin["admin_headers"], org_with_admin["admin_tokens"]
    async with SessionLocal() as session:
        await session.execute(
            update(User).where(User.email == "hr@acme.example").values(is_active=False)
        )
        await session.commit()
    r = await client.post(
        "/auth/refresh", json={"refresh_token": admin_tokens["refresh_token"]}
    )
    assert r.status_code == 401
    assert (await client.get("/users/me", headers=admin_headers)).status_code == 401


async def _craft_invitation(org_id, email, role, expires_delta):
    raw, token_hash = new_opaque_token()
    async with SessionLocal() as session:
        session.add(
            Invitation(
                org_id=org_id,
                email=email,
                role=role,
                token_hash=token_hash,
                expires_at=datetime.now(timezone.utc) + expires_delta,
            )
        )
        await session.commit()
    return raw


async def test_an_expired_invitation_is_400_and_frees_its_seat(client, org_with_admin):
    org_id = org_with_admin["org"]["id"]
    raw = await _craft_invitation(org_id, "late@acme.example", "user", timedelta(days=-1))
    r = await client.post(
        "/auth/accept-invitation",
        json={"token": raw, "name": "Late", "password": "hunter2hunter2"},
    )
    assert r.status_code == 400 and "expired" in r.json()["detail"]
    # An expired invitation no longer counts against the seat total.
    body = (await client.get("/orgs/me", headers=org_with_admin["admin_headers"])).json()
    assert body["seats_used"] == 1


async def test_an_invitation_to_a_deactivated_org_is_refused(client, internal, org_with_admin):
    headers, _ = internal
    org_id = org_with_admin["org"]["id"]
    raw = await _craft_invitation(org_id, "tolate@acme.example", "user", timedelta(days=1))
    await client.patch(f"/orgs/{org_id}", json={"is_active": False}, headers=headers)
    r = await client.post(
        "/auth/accept-invitation",
        json={"token": raw, "name": "X", "password": "hunter2hunter2"},
    )
    assert r.status_code == 400 and "not active" in r.json()["detail"]


async def test_a_crafted_bogus_role_falls_back_to_user(client, org_with_admin):
    """Defense in depth: the API validates roles, but a row written by any
    other path must still never mint an internal_admin."""
    org_id = org_with_admin["org"]["id"]
    raw = await _craft_invitation(
        org_id, "sneaky@acme.example", "internal_admin_wannabe", timedelta(days=1)
    )
    r = await client.post(
        "/auth/accept-invitation",
        json={"token": raw, "name": "S", "password": "hunter2hunter2"},
    )
    assert r.status_code == 201
    async with SessionLocal() as session:
        user = (
            await session.execute(select(User).where(User.email == "sneaky@acme.example"))
        ).scalar_one()
        assert user.role == "user"


async def test_inviting_with_a_bogus_role_is_400(client, org_with_admin):
    r = await client.post(
        "/orgs/me/invitations",
        json={"email": "r@acme.example", "role": "internal_admin"},
        headers=org_with_admin["admin_headers"],
    )
    assert r.status_code == 400


async def test_patching_an_unknown_demo_request_is_404(client, internal):
    headers, _ = internal
    r = await client.patch(
        "/admin/demo-requests/nope", json={"status": "contacted"}, headers=headers
    )
    assert r.status_code == 404


def test_secret_decoding_treats_garbage_as_unset():
    assert config.decode_secret("") == b""
    assert config.decode_secret("!!! not base64 !!!") == b""
    assert config.decode_secret("c2VjcmV0") == b"secret"


def test_the_boot_guard_lists_only_real_problems(monkeypatch):
    from app import config as cfg

    monkeypatch.setattr(cfg, "ENV", "production")
    monkeypatch.setattr(cfg, "_JWT_SECRET_B64", "c2VjcmV0")
    monkeypatch.setattr(cfg, "DATABASE_URL", "postgresql+asyncpg://x/y")
    monkeypatch.setattr(cfg, "SEED_DEV_ADMIN", True)
    try:
        cfg.guard_production()
        raise AssertionError("seed-on must fail production")
    except RuntimeError as err:
        assert "SEED_DEV_ADMIN" in str(err)
        assert "JWT_SECRET" not in str(err)
    monkeypatch.setattr(cfg, "SEED_DEV_ADMIN", False)
    monkeypatch.setattr(cfg, "BILLING_MOCK", False)
    cfg.guard_production()  # fully configured production boots


async def test_invitation_email_carries_the_accept_link(client, org_with_admin, monkeypatch):
    from app import config as cfg
    from app import emailer

    sent = []
    monkeypatch.setattr(cfg, "SMTP_HOST", "smtp.example")
    monkeypatch.setattr(cfg, "SMTP_USER", "hello@cerebrozen.in")
    monkeypatch.setattr(cfg, "SMTP_PASS", "x")
    monkeypatch.setattr(emailer, "deliver", lambda msg: sent.append(msg))

    r = await client.post(
        "/orgs/me/invitations",
        json={"email": "mail@acme.example"},
        headers=org_with_admin["admin_headers"],
    )
    body = r.json()
    assert body["emailed"] is True
    (msg,) = sent
    assert msg["To"] == "mail@acme.example"
    assert body["invitation_token"] in msg.get_content()
    assert "/accept?token=" in body["invite_link"]


async def test_unconfigured_smtp_still_creates_the_invitation(client, org_with_admin):
    r = await client.post(
        "/orgs/me/invitations",
        json={"email": "manual@acme.example"},
        headers=org_with_admin["admin_headers"],
    )
    assert r.status_code == 201
    assert r.json()["emailed"] is False, "no SMTP -> manual sharing, never an error"


async def test_a_delivery_failure_never_breaks_creation(client, org_with_admin, monkeypatch):
    from app import config as cfg
    from app import emailer

    monkeypatch.setattr(cfg, "SMTP_HOST", "smtp.example")
    monkeypatch.setattr(cfg, "SMTP_USER", "hello@cerebrozen.in")
    monkeypatch.setattr(cfg, "SMTP_PASS", "x")
    def _boom(msg):
        raise OSError("mailbox on fire")
    monkeypatch.setattr(emailer, "deliver", _boom)

    r = await client.post(
        "/orgs/me/invitations",
        json={"email": "unlucky@acme.example"},
        headers=org_with_admin["admin_headers"],
    )
    assert r.status_code == 201 and r.json()["emailed"] is False
