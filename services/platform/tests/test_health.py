"""Health + the deployment self-check that backs the sovereignty story: an operator
can see, with no credentials, exactly which external dependencies a running instance
reaches for (email, database kind, billing provider) — posture, never data."""


async def test_health_is_ok(client):
    r = await client.get("/health")
    assert r.status_code == 200
    assert r.json()["service"] == "platform"


async def test_health_status_reports_deployment_posture(client):
    r = await client.get("/health/status")  # public, no auth header
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["service"] == "platform"
    assert body["database"] in ("sqlite", "postgres", "other")
    assert isinstance(body["email_delivery"], bool)
    assert body["billing_provider"] in ("mock", "live")
    assert isinstance(body["dev_seed_enabled"], bool)
    # The test DB is in-memory sqlite → a fully keyless, sovereign-capable posture.
    assert body["database"] == "sqlite"
    assert body["sovereign_ready"] is True
    assert body["email_delivery"] is False  # no SMTP in tests → manual sharing
