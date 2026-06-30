"""Coverage for the CRUD/edge routes: users, content, waitlist, plans, chat,
oracle status, and auth/deps error paths."""
import uuid

from app.core.security import create_access_token


# ── Users ───────────────────────────────────────────────────────────────
async def test_update_profile(auth_client):
    r = await auth_client.patch("/users/me", json={"name": "Renamed", "goals": ["Sleep better"], "motivations": ["Calm"]})
    assert r.status_code == 200
    body = r.json()
    assert body["name"] == "Renamed"
    assert body["goals"] == ["Sleep better"]
    assert body["motivations"] == ["Calm"]


async def test_consent_get_and_update(auth_client):
    r = await auth_client.get("/users/me/consent")
    assert r.status_code == 200
    r = await auth_client.patch("/users/me/consent", json={"ai_memory": False, "voice_storage": True})
    assert r.status_code == 200
    assert r.json()["ai_memory"] is False
    assert r.json()["voice_storage"] is True


async def test_push_token(auth_client):
    r = await auth_client.put("/users/me/push-token", json={"push_token": "abc123token"})
    assert r.status_code == 200


# ── Content (public catalogue) ──────────────────────────────────────────
async def test_content_list_and_filters(auth_client):
    assert (await auth_client.get("/content")).status_code == 200
    assert (await auth_client.get("/content", params={"kind": "meditation"})).status_code == 200
    r = await auth_client.get("/content", params={"q": "breath"})
    assert r.status_code == 200 and isinstance(r.json(), list)


# ── Waitlist ────────────────────────────────────────────────────────────
async def test_waitlist_join_and_dedupe(client):
    email = f"wl-{uuid.uuid4().hex[:8]}@test.app"
    r1 = await client.post("/waitlist", json={"email": email, "source": "test"})
    assert r1.status_code == 201 and r1.json()["status"] == "joined"
    r2 = await client.post("/waitlist", json={"email": email})
    assert r2.status_code == 201 and r2.json()["status"] == "already_joined"


# ── Plans ───────────────────────────────────────────────────────────────
async def test_regenerate_plan(auth_client):
    r = await auth_client.post("/plans/generate")
    assert r.status_code == 201
    assert r.json()["steps"]


async def test_toggle_unknown_step_404(auth_client):
    r = await auth_client.patch(f"/plans/steps/{uuid.uuid4()}", json={"done": True})
    assert r.status_code == 404


# ── Chat history ────────────────────────────────────────────────────────
async def test_chat_history(auth_client):
    await auth_client.post("/chat/messages", json={"text": "hello there"})
    r = await auth_client.get("/chat")
    assert r.status_code == 200
    assert any(m["role"] == "user" for m in r.json())


# ── Oracle status / disabled path ───────────────────────────────────────
async def test_oracle_status_requires_auth(client):
    assert (await client.get("/oracle/status")).status_code == 401


async def test_oracle_status_shape(auth_client):
    r = await auth_client.get("/oracle/status")
    assert r.status_code == 200 and isinstance(r.json()["available"], bool)


async def test_oracle_messages_503_when_disabled(auth_client, monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "oracle_enabled", False)
    r = await auth_client.post("/oracle/messages", json={"text": "hi", "thread_id": "t1"})
    assert r.status_code == 503
    r2 = await auth_client.post("/oracle/confirm", json={"thread_id": "t1", "approved": True})
    assert r2.status_code == 503


# ── Auth / deps error paths ─────────────────────────────────────────────
async def test_bad_bearer_rejected(client):
    r = await client.get("/auth/me", headers={"Authorization": "Bearer garbage.token.value"})
    assert r.status_code == 401


async def test_token_with_non_uuid_subject_rejected(client):
    token = create_access_token("not-a-uuid")
    r = await client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 401


async def test_token_for_missing_user_rejected(client):
    token = create_access_token(str(uuid.uuid4()))
    r = await client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert r.status_code == 401


async def test_refresh_token_flow(client):
    email = f"refresh-{uuid.uuid4().hex[:8]}@test.app"
    signup = await client.post("/auth/signup", json={"email": email, "password": "password123"})
    refresh = signup.json()["refresh_token"]
    r = await client.post("/auth/refresh", json={"refresh_token": refresh})
    assert r.status_code == 200 and r.json()["access_token"]


async def test_refresh_rejects_garbage(client):
    r = await client.post("/auth/refresh", json={"refresh_token": "nope"})
    assert r.status_code == 401
