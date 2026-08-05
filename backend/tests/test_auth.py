import uuid


async def test_health(client):
    r = await client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"
    assert r.headers["x-request-id"]
    assert r.headers["server-timing"].startswith("app;dur=")


async def test_versioned_health_alias_preserves_payload(client):
    legacy = await client.get("/health")
    versioned = await client.get("/api/v1/health")
    assert versioned.status_code == 200
    assert versioned.json() == legacy.json()


async def test_ready_checks_database(client):
    for path in ("/ready", "/api/v1/ready"):
        response = await client.get(path)
        assert response.status_code == 200
        assert response.json() == {"status": "ready", "database": "ok"}


async def test_request_id_is_echoed(client):
    response = await client.get(
        "/health", headers={"X-Request-ID": "test-correlation-123"}
    )
    assert response.headers["x-request-id"] == "test-correlation-123"


async def test_signup_login_me(client):
    email = f"u-{uuid.uuid4().hex[:8]}@test.app"

    r = await client.post(
        "/auth/signup", json={"email": email, "password": "password123", "name": "Ada"}
    )
    assert r.status_code == 201
    tokens = r.json()
    assert tokens["access_token"] and tokens["refresh_token"]

    # Duplicate signup is rejected.
    r = await client.post(
        "/auth/signup", json={"email": email, "password": "password123"}
    )
    assert r.status_code == 409

    # Login via OAuth2 form.
    r = await client.post(
        "/auth/login", data={"username": email, "password": "password123"}
    )
    assert r.status_code == 200
    access = r.json()["access_token"]

    # /auth/me requires the token.
    r = await client.get("/auth/me", headers={"Authorization": f"Bearer {access}"})
    assert r.status_code == 200
    body = r.json()
    assert body["email"] == email
    assert body["consent"] is not None

    # Wrong password fails.
    r = await client.post("/auth/login", data={"username": email, "password": "nope"})
    assert r.status_code == 401


async def test_me_requires_auth(client):
    r = await client.get("/users/me")
    assert r.status_code == 401
