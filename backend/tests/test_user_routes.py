"""Route-level coverage for /users/me (subscription, consent, export, delete, push)."""


async def test_subscription_verify_rejects_bad_receipt(auth_client):
    r = await auth_client.post("/users/me/subscription/verify", json={"signed_transaction": "not.a.jws"})
    assert r.status_code == 400
    assert "receipt" in r.json()["detail"].lower()


async def test_consent_get_default_and_update(auth_client):
    # Default consent when none set yet.
    r = await auth_client.get("/users/me/consent")
    assert r.status_code == 200
    r = await auth_client.patch("/users/me/consent", json={"voice_storage": True, "model_training": True})
    assert r.status_code == 200
    body = r.json()
    assert body["voice_storage"] is True and body["model_training"] is True


async def test_update_profile_fields(auth_client):
    r = await auth_client.patch("/users/me", json={"goals": ["Sleep better"], "motivations": ["Calm"], "language": "Hindi"})
    assert r.status_code == 200
    me = r.json()
    assert me["goals"] == ["Sleep better"] and me["language"] == "Hindi"


async def test_push_token(auth_client):
    r = await auth_client.put("/users/me/push-token", json={"push_token": "abc123devicetoken"})
    assert r.status_code == 200


async def test_export_returns_all_sections(auth_client):
    # Create a little data first.
    await auth_client.post("/moods", json={"mood": "Calm", "note": "ok", "intensity": 3})
    await auth_client.post("/journal", json={"title": "Note", "body": "hi", "tags": ["Work"]})
    r = await auth_client.get("/users/me/export")
    assert r.status_code == 200
    data = r.json()
    for key in ("profile", "moods", "journal", "chat", "plans", "nudges", "insights", "exported_at"):
        assert key in data


async def test_delete_account_cascades(client):
    import uuid
    e = f"del-{uuid.uuid4().hex[:10]}@test.app"
    tok = (await client.post("/auth/signup", json={"email": e, "password": "password123", "name": "D"})).json()["access_token"]
    client.headers["Authorization"] = f"Bearer {tok}"
    assert (await client.get("/users/me")).status_code == 200
    assert (await client.delete("/users/me")).status_code == 204
    # Token now points at a deleted user.
    assert (await client.get("/users/me")).status_code == 401
