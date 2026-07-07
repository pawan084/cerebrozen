"""Prompt registry: code-default fallback, versioning, activation, wiring."""
import uuid

from sqlalchemy import delete

from app.core.database import SessionLocal
from app.models.prompt import PromptTemplate
from app.services import ai, assessment, prompts


async def _clean(name: str):
    async with SessionLocal() as s:
        await s.execute(delete(PromptTemplate).where(PromptTemplate.name == name))
        await s.commit()


async def test_get_falls_back_to_registered_default(client):
    name = f"test_prompt_{uuid.uuid4().hex[:8]}"
    prompts.register(name, "the code default")
    assert await prompts.get(name) == "the code default"
    # With an explicit session too.
    async with SessionLocal() as s:
        assert await prompts.get(name, s) == "the code default"


async def test_save_activates_and_overrides(admin_client):
    await _clean("agentic_plan")
    r = await admin_client.post("/admin/prompts/agentic_plan", json={"template": "OVERRIDE v1", "notes": "test"})
    assert r.status_code == 201 and r.json()["version"] == 1 and r.json()["active"] is True
    assert await prompts.get("agentic_plan") == "OVERRIDE v1"

    # A second save becomes v2 and takes over.
    r = await admin_client.post("/admin/prompts/agentic_plan", json={"template": "OVERRIDE v2"})
    assert r.status_code == 201 and r.json()["version"] == 2
    assert await prompts.get("agentic_plan") == "OVERRIDE v2"

    # Rollback: re-activate v1.
    r = await admin_client.post("/admin/prompts/agentic_plan/versions/1/activate")
    assert r.status_code == 200 and r.json()["version"] == 1
    assert await prompts.get("agentic_plan") == "OVERRIDE v1"

    # Revert: the code default serves again; history survives.
    r = await admin_client.post("/admin/prompts/agentic_plan/revert")
    assert r.status_code == 200 and r.json()["source"] == "code_default"
    assert await prompts.get("agentic_plan") == prompts.default_for("agentic_plan")
    listing = (await admin_client.get("/admin/prompts")).json()
    entry = next(p for p in listing if p["name"] == "agentic_plan")
    assert entry["source"] == "code_default" and len(entry["versions"]) == 2
    await _clean("agentic_plan")


async def test_list_includes_all_registered_defaults(admin_client):
    r = await admin_client.get("/admin/prompts")
    assert r.status_code == 200
    names = {p["name"] for p in r.json()}
    # The four production prompts register at import time.
    assert {"agentic_plan", "safety_classifier", "assessment_topics", "oracle_system"} <= names
    for p in r.json():
        if p["name"] == "safety_classifier":
            assert p["source"] == "code_default" and "safety classifier" in p["template"]


async def test_unknown_prompt_rejected(admin_client):
    r = await admin_client.post("/admin/prompts/not_a_prompt", json={"template": "x"})
    assert r.status_code == 404
    r = await admin_client.post("/admin/prompts/not_a_prompt/revert")
    assert r.status_code == 404
    r = await admin_client.post("/admin/prompts/agentic_plan/versions/99/activate")
    assert r.status_code == 404


async def test_prompts_require_admin(client):
    email = f"plain-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": email, "password": "password123", "name": "P"})
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    r = await client.get("/admin/prompts")
    assert r.status_code == 403


async def test_wired_caller_uses_override(admin_client, monkeypatch):
    """assessment.generate_topics must send the ACTIVE registry template as the
    system prompt — proving call sites read the registry, not the constant."""
    await _clean("assessment_topics")
    r = await admin_client.post(
        "/admin/prompts/assessment_topics", json={"template": "REGISTRY SYSTEM PROMPT"}
    )
    assert r.status_code == 201

    captured: dict = {}

    async def fake_complete_json(system, prompt, **kwargs):
        captured["system"] = system
        return None  # deterministic fallback topics take over

    monkeypatch.setattr(ai, "complete_json", fake_complete_json)
    topics, source = await assessment.generate_topics(["Calm"], ["Reduce stress"])
    assert captured["system"] == "REGISTRY SYSTEM PROMPT"
    assert source == "rule" and topics  # fallback still delivers
    await _clean("assessment_topics")