"""Account lifecycle: data export, deletion (App Store 5.1.1(v)), Sign in with Apple."""
import uuid

import pytest

from app.services import apple


async def test_export_returns_all_sections(auth_client):
    # Seed a little data so the export isn't trivially empty.
    await auth_client.post("/moods", json={"mood": "calm", "note": "ok", "symbol": "sparkles", "intensity": 3})
    await auth_client.post("/journal", json={"title": "A note", "body": "hello", "tags": ["x"]})

    r = await auth_client.get("/users/me/export")
    assert r.status_code == 200
    body = r.json()
    for key in ("exported_at", "profile", "moods", "journal", "chat", "plans", "nudges", "insights"):
        assert key in body
    assert body["profile"]["email"]
    assert any(m["mood"] == "calm" for m in body["moods"])
    assert any(j["title"] == "A note" for j in body["journal"])


async def test_delete_account_then_unauthorized(auth_client):
    # Confirm we're authenticated, then delete and confirm the token is dead.
    assert (await auth_client.get("/auth/me")).status_code == 200
    r = await auth_client.delete("/users/me")
    assert r.status_code == 204
    # The user row is gone → the same bearer token no longer resolves a user.
    assert (await auth_client.get("/auth/me")).status_code == 401


async def test_apple_sign_in_creates_then_reuses(client, monkeypatch):
    email = f"apple-{uuid.uuid4().hex[:8]}@privaterelay.appleid.com"
    # Unique per run: the Apple `sub` now keys the account, and the dev test DB
    # persists across runs — a fixed sub would collide with older rows.
    sub = f"apple-sub-{uuid.uuid4().hex}"

    async def fake_verify(_token):
        return {"sub": sub, "email": email}

    monkeypatch.setattr(apple, "verify_identity_token", fake_verify)

    r1 = await client.post("/auth/apple", json={"identity_token": "x", "name": "Apple User"})
    assert r1.status_code == 200, r1.text
    token1 = r1.json()["access_token"]
    # The issued token works.
    me = await client.get("/auth/me", headers={"Authorization": f"Bearer {token1}"})
    assert me.status_code == 200 and me.json()["email"] == email

    # Signing in again with the same Apple email reuses the same account.
    r2 = await client.post("/auth/apple", json={"identity_token": "x"})
    assert r2.status_code == 200
    me2 = await client.get("/auth/me", headers={"Authorization": f"Bearer {r2.json()['access_token']}"})
    assert me2.json()["id"] == me.json()["id"]


async def test_apple_sign_in_rejects_invalid_token(client, monkeypatch):
    async def fake_verify(_token):
        return None

    monkeypatch.setattr(apple, "verify_identity_token", fake_verify)
    r = await client.post("/auth/apple", json={"identity_token": "bad"})
    assert r.status_code == 401
