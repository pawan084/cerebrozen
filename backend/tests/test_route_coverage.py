"""Route coverage for plans, journal, moods, voice, and waitlist."""
import uuid


# ── plans ───────────────────────────────────────────────────────────────
async def test_active_plan_generates_then_toggle(auth_client):
    r = await auth_client.get("/plans/active")   # generated on first access
    assert r.status_code == 200
    steps = r.json()["steps"]
    assert steps
    sid = steps[0]["id"]
    r2 = await auth_client.patch(f"/plans/steps/{sid}", json={"done": True})
    assert r2.status_code == 200 and any(s["id"] == sid and s["done"] for s in r2.json()["steps"])
    assert (await auth_client.patch(f"/plans/steps/{sid}", json={"done": False})).status_code == 200


async def test_regenerate_plan(auth_client):
    assert (await auth_client.post("/plans/generate")).status_code == 201


async def test_toggle_unknown_step_404(auth_client):
    assert (await auth_client.patch(f"/plans/steps/{uuid.uuid4()}", json={"done": True})).status_code == 404


async def test_toggle_other_users_step_404(auth_client, client):
    sid = (await auth_client.get("/plans/active")).json()["steps"][0]["id"]
    e = f"other-{uuid.uuid4().hex[:8]}@test.app"
    tok = (await client.post("/auth/signup", json={"email": e, "password": "password123", "name": "O"})).json()["access_token"]
    client.headers["Authorization"] = f"Bearer {tok}"
    assert (await client.patch(f"/plans/steps/{sid}", json={"done": True})).status_code == 404


# ── journal ─────────────────────────────────────────────────────────────
async def test_journal_create_then_delete(auth_client):
    r = await auth_client.post("/journal", json={"title": "T", "body": "a calm day", "tags": ["Work"]})
    assert r.status_code == 201
    eid = r.json()["id"]
    assert (await auth_client.delete(f"/journal/{eid}")).status_code == 204
    assert (await auth_client.delete(f"/journal/{eid}")).status_code == 404


async def test_journal_delete_unknown_404(auth_client):
    assert (await auth_client.delete(f"/journal/{uuid.uuid4()}")).status_code == 404


# ── moods ───────────────────────────────────────────────────────────────
async def test_mood_create_and_list(auth_client):
    assert (await auth_client.post("/moods", json={"mood": "Low", "note": "tough", "intensity": 2})).status_code == 201
    r = await auth_client.get("/moods")
    assert r.status_code == 200 and isinstance(r.json(), list)


# ── voice ───────────────────────────────────────────────────────────────
async def test_voice_status(auth_client):
    r = await auth_client.get("/voice/status")
    assert r.status_code == 200 and set(r.json()) == {"stt", "tts"}


async def test_voice_stt_disabled_503(auth_client, monkeypatch):
    from app.api.routes import voice as vr
    monkeypatch.setattr(vr.settings, "deepgram_api_key", "")
    r = await auth_client.post("/voice/stt", files={"audio": ("a.mp3", b"x", "audio/mpeg")})
    assert r.status_code == 503


async def test_voice_tts_disabled_503(auth_client, monkeypatch):
    from app.api.routes import voice as vr
    monkeypatch.setattr(vr.settings, "elevenlabs_api_key", "")
    r = await auth_client.post("/voice/tts", json={"text": "hi"})
    assert r.status_code == 503


# ── waitlist ────────────────────────────────────────────────────────────
async def test_waitlist_join_then_duplicate(client):
    e = f"wl-{uuid.uuid4().hex[:8]}@test.app"
    assert (await client.post("/waitlist", json={"email": e})).json()["status"] == "joined"
    assert (await client.post("/waitlist", json={"email": e})).json()["status"] == "already_joined"
