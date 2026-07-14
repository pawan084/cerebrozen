"""Platform API: auth rotation, org/seat lifecycle, roles, privacy, demo
pipeline, boot-guard, and the engine JWT contract."""

import jwt as pyjwt
import pytest

from app import config
from app.security import decode_access_token, hash_password, verify_password

# ── health & auth basics ─────────────────────────────────────────────────────


async def test_health(client):
    r = await client.get("/health")
    assert r.status_code == 200 and r.json()["status"] == "ok"


async def test_wrong_password_and_unknown_user_are_the_same_401(client, internal):
    r1 = await client.post(
        "/auth/login", data={"username": config.DEV_ADMIN_EMAIL, "password": "nope"}
    )
    r2 = await client.post(
        "/auth/login", data={"username": "ghost@nowhere.example", "password": "nope"}
    )
    assert r1.status_code == r2.status_code == 401
    assert r1.json()["detail"] == r2.json()["detail"], "no account enumeration"


async def test_me_requires_a_token(client):
    assert (await client.get("/users/me")).status_code == 401
    assert (
        await client.get("/users/me", headers={"Authorization": "Bearer garbage"})
    ).status_code == 401


# ── the engine JWT contract ──────────────────────────────────────────────────


async def test_access_tokens_carry_the_engine_claim_contract(client, internal):
    """The engine 401s tokens without org_id and reads user.username as the
    user id. Break this shape and every coaching session dies at auth."""
    _, tokens = internal
    claims = decode_access_token(tokens["access_token"])
    assert claims["org_id"], "engine rejects org-less tokens"
    assert claims["user"]["username"] == claims["sub"]
    assert claims["role"] == "internal_admin"
    # HS512 with the same secret bytes — one shared secret serves both services.
    header = pyjwt.get_unverified_header(tokens["access_token"])
    assert header["alg"] == "HS512"


async def test_org_member_tokens_carry_their_org_id(client, org_with_admin):
    claims = decode_access_token(org_with_admin["admin_tokens"]["access_token"])
    assert claims["org_id"] == org_with_admin["org"]["id"]


# ── refresh rotation ─────────────────────────────────────────────────────────


async def test_refresh_rotates_and_old_tokens_die(client, internal):
    _, tokens = internal
    r = await client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 200
    new = r.json()
    assert new["refresh_token"] != tokens["refresh_token"]
    # The old token is single-use: replaying it is treated as theft…
    r2 = await client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r2.status_code == 401 and "reuse" in r2.json()["detail"]
    # …and reuse revokes EVERYTHING, including the newly-issued token.
    r3 = await client.post("/auth/refresh", json={"refresh_token": new["refresh_token"]})
    assert r3.status_code == 401


async def test_unknown_refresh_token_is_401(client):
    r = await client.post("/auth/refresh", json={"refresh_token": "never-issued"})
    assert r.status_code == 401


async def test_logout_revokes_the_refresh_token(client, internal):
    _, tokens = internal
    assert (
        await client.post("/auth/logout", json={"refresh_token": tokens["refresh_token"]})
    ).status_code == 204
    r = await client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 401


# ── org lifecycle & roles ────────────────────────────────────────────────────


async def test_creating_an_org_requires_internal_admin(client, org_with_admin):
    r = await client.post(
        "/orgs",
        json={"name": "Sneaky", "slug": "sneaky"},
        headers=org_with_admin["admin_headers"],
    )
    assert r.status_code == 403, "an org admin must not create tenants"


async def test_duplicate_slug_is_409(client, internal, org_with_admin):
    headers, _ = internal
    r = await client.post("/orgs", json={"name": "Acme 2", "slug": "acme"}, headers=headers)
    assert r.status_code == 409


async def test_org_admin_sees_their_org_and_seat_usage(client, org_with_admin):
    r = await client.get("/orgs/me", headers=org_with_admin["admin_headers"])
    assert r.status_code == 200
    body = r.json()
    assert body["slug"] == "acme"
    assert body["seats_used"] == 1  # the accepted org admin
    assert body["regulated_mode"] is True, "regulated is the default posture"


async def test_the_people_roster_shows_identity_never_content(client, org_with_admin):
    r = await client.get("/orgs/me/people", headers=org_with_admin["admin_headers"])
    assert r.status_code == 200
    (person,) = r.json()
    assert set(person) == {"id", "email", "name", "role", "is_active"}


async def test_internal_admin_patches_org_config(client, internal, org_with_admin):
    headers, _ = internal
    org_id = org_with_admin["org"]["id"]
    r = await client.patch(
        f"/orgs/{org_id}", json={"seats_total": 10, "regulated_mode": False}, headers=headers
    )
    assert r.status_code == 200
    assert r.json()["seats_total"] == 10 and r.json()["regulated_mode"] is False
    assert (
        await client.patch("/orgs/nope", json={"seats_total": 1}, headers=headers)
    ).status_code == 404


# ── invitations & seats ──────────────────────────────────────────────────────


async def _invite_and_accept(client, admin_headers, email, password="hunter2hunter2"):
    r = await client.post(
        "/orgs/me/invitations", json={"email": email}, headers=admin_headers
    )
    assert r.status_code == 201, r.text
    token = r.json()["invitation_token"]
    r = await client.post(
        "/auth/accept-invitation",
        json={"token": token, "name": "Member", "password": password},
    )
    assert r.status_code == 201, r.text
    return r.json()


async def test_invitation_flow_creates_a_member_who_can_log_in(client, org_with_admin):
    tokens = await _invite_and_accept(
        client, org_with_admin["admin_headers"], "worker@acme.example"
    )
    claims = decode_access_token(tokens["access_token"])
    assert claims["org_id"] == org_with_admin["org"]["id"]
    assert claims["role"] == "user"
    r = await client.post(
        "/auth/login",
        data={"username": "worker@acme.example", "password": "hunter2hunter2"},
    )
    assert r.status_code == 200


async def test_an_invitation_is_single_use(client, org_with_admin):
    r = await client.post(
        "/orgs/me/invitations",
        json={"email": "once@acme.example"},
        headers=org_with_admin["admin_headers"],
    )
    token = r.json()["invitation_token"]
    body = {"token": token, "name": "A", "password": "hunter2hunter2"}
    assert (await client.post("/auth/accept-invitation", json=body)).status_code == 201
    r2 = await client.post(
        "/auth/accept-invitation",
        json={"token": token, "name": "B", "password": "hunter2hunter2"},
    )
    assert r2.status_code == 400


async def test_a_bad_invitation_token_is_400(client):
    r = await client.post(
        "/auth/accept-invitation",
        json={"token": "forged", "name": "X", "password": "hunter2hunter2"},
    )
    assert r.status_code == 400


async def test_short_passwords_are_rejected(client, org_with_admin):
    r = await client.post(
        "/orgs/me/invitations",
        json={"email": "short@acme.example"},
        headers=org_with_admin["admin_headers"],
    )
    token = r.json()["invitation_token"]
    r = await client.post(
        "/auth/accept-invitation", json={"token": token, "name": "S", "password": "short"}
    )
    assert r.status_code == 400


async def test_seats_are_a_hard_limit_and_invitations_count(client, org_with_admin):
    """seats_total=3: admin (1) + two more; the fourth commitment must 409 —
    and a PENDING invitation already holds a seat."""
    headers = org_with_admin["admin_headers"]
    await _invite_and_accept(client, headers, "w1@acme.example")
    r = await client.post(
        "/orgs/me/invitations", json={"email": "w2@acme.example"}, headers=headers
    )
    assert r.status_code == 201  # pending — seat 3 committed
    r = await client.post(
        "/orgs/me/invitations", json={"email": "w3@acme.example"}, headers=headers
    )
    assert r.status_code == 409 and "seats" in r.json()["detail"]


async def test_inviting_an_existing_email_is_409(client, org_with_admin):
    r = await client.post(
        "/orgs/me/invitations",
        json={"email": "hr@acme.example"},
        headers=org_with_admin["admin_headers"],
    )
    assert r.status_code == 409


async def test_a_plain_user_cannot_administer_the_org(client, org_with_admin):
    tokens = await _invite_and_accept(
        client, org_with_admin["admin_headers"], "plain@acme.example"
    )
    headers = {"Authorization": f"Bearer {tokens['access_token']}"}
    assert (await client.get("/orgs/me/people", headers=headers)).status_code == 403
    assert (
        await client.post(
            "/orgs/me/invitations", json={"email": "x@acme.example"}, headers=headers
        )
    ).status_code == 403
    assert (await client.get("/orgs", headers=headers)).status_code == 403


# ── privacy: export & deletion ───────────────────────────────────────────────


async def test_export_returns_the_platform_record(client, org_with_admin):
    r = await client.get("/users/me/export", headers=org_with_admin["admin_headers"])
    assert r.status_code == 200
    body = r.json()
    assert body["profile"]["email"] == "hr@acme.example"
    assert "engine" in body["note"], "export must point at the coaching-content export"


async def test_deletion_scrubs_pii_revokes_sessions_and_writes_the_ledger(
    client, org_with_admin
):
    tokens = await _invite_and_accept(client, org_with_admin["admin_headers"], "gone@acme.example")
    headers = {"Authorization": f"Bearer {tokens['access_token']}"}

    assert (await client.delete("/users/me", headers=headers)).status_code == 400, (
        "deletion must require explicit confirmation"
    )
    assert (
        await client.delete("/users/me?confirm=true", headers=headers)
    ).status_code == 204

    # Sessions are dead: refresh fails and the account cannot log in.
    r = await client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 401
    r = await client.post(
        "/auth/login", data={"username": "gone@acme.example", "password": "hunter2hunter2"}
    )
    assert r.status_code == 401

    # The ledger row exists and holds no raw PII.
    from sqlalchemy import select

    from app.db import SessionLocal
    from app.models import DeletionLedger, User

    async with SessionLocal() as session:
        ledger = (await session.execute(select(DeletionLedger))).scalars().all()
        assert len(ledger) == 1
        assert "@" not in ledger[0].email_hash
        scrubbed = (
            await session.execute(select(User).where(User.id == ledger[0].user_id))
        ).scalar_one()
        assert "gone@acme.example" not in scrubbed.email
        assert scrubbed.is_active is False


# ── demo pipeline ────────────────────────────────────────────────────────────


async def test_demo_requests_are_stored_and_listed_for_ops(client, internal):
    r = await client.post(
        "/demo-requests",
        json={"name": "Priya", "email": "priya@corp.example", "company": "Corp"},
    )
    assert r.status_code == 201
    headers, _ = internal
    rows = (await client.get("/admin/demo-requests", headers=headers)).json()
    assert rows[0]["company"] == "Corp" and rows[0]["status"] == "new"
    rid = rows[0]["id"]
    r = await client.patch(
        f"/admin/demo-requests/{rid}", json={"status": "contacted"}, headers=headers
    )
    assert r.status_code == 200 and r.json()["status"] == "contacted"
    assert (
        await client.patch(
            f"/admin/demo-requests/{rid}", json={"status": "bogus"}, headers=headers
        )
    ).status_code == 400


async def test_the_honeypot_swallows_bots_silently(client, internal):
    r = await client.post(
        "/demo-requests",
        json={
            "name": "Bot",
            "email": "bot@spam.example",
            "company": "Spam",
            "website": "http://spam",
        },
    )
    assert r.status_code == 201 and r.json() == {"ok": True}
    headers, _ = internal
    rows = (await client.get("/admin/demo-requests", headers=headers)).json()
    assert all(row["email"] != "bot@spam.example" for row in rows)


async def test_the_pipeline_view_is_internal_only(client, org_with_admin):
    r = await client.get("/admin/demo-requests", headers=org_with_admin["admin_headers"])
    assert r.status_code == 403


# ── passwords & boot-guard ───────────────────────────────────────────────────


def test_password_hashing_round_trips_and_rejects_wrong_input():
    stored = hash_password("correct horse battery")
    assert verify_password("correct horse battery", stored)
    assert not verify_password("wrong", stored)
    assert not verify_password("anything", "not-a-valid-hash")
    assert not verify_password("anything", "bcrypt$12$abc$def")


def test_the_boot_guard_refuses_insecure_production(monkeypatch):
    from app import config as cfg

    monkeypatch.setattr(cfg, "ENV", "production")
    monkeypatch.setattr(cfg, "_JWT_SECRET_B64", "")
    with pytest.raises(RuntimeError) as err:
        cfg.guard_production()
    msg = str(err.value)
    assert "JWT_SECRET" in msg and "sqlite" in msg.lower()


def test_the_boot_guard_is_quiet_in_dev(monkeypatch):
    from app import config as cfg

    monkeypatch.setattr(cfg, "ENV", "local")
    cfg.guard_production()  # must not raise
