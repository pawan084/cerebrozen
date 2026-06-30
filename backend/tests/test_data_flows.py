async def test_mood_create_and_list(auth_client):
    r = await auth_client.post("/moods", json={"mood": "Anxious", "note": "Loud thoughts", "intensity": 4})
    assert r.status_code == 201
    assert r.json()["mood"] == "Anxious"

    r = await auth_client.get("/moods")
    assert r.status_code == 200
    assert any(m["mood"] == "Anxious" for m in r.json())


async def test_journal_create_lists_and_scans(auth_client):
    r = await auth_client.post("/journal", json={"title": "Meeting pressure", "body": "A bit stressed", "tags": ["Work"]})
    assert r.status_code == 201
    assert r.json()["risk_level"] in {"none", "low", "elevated", "crisis"}

    r = await auth_client.get("/journal")
    assert r.status_code == 200
    assert r.json()[0]["title"] == "Meeting pressure"


async def test_active_plan_generates(auth_client):
    r = await auth_client.get("/plans/active")
    assert r.status_code == 200
    plan = r.json()
    assert len(plan["steps"]) >= 1

    # Toggle the first step.
    step_id = plan["steps"][0]["id"]
    r = await auth_client.patch(f"/plans/steps/{step_id}", json={"done": True})
    assert r.status_code == 200
    assert any(s["id"] == step_id and s["done"] for s in r.json()["steps"])


async def test_chat_reply(auth_client):
    r = await auth_client.post("/chat/messages", json={"text": "I keep overthinking tomorrow's meeting."})
    assert r.status_code == 201
    body = r.json()
    assert body["user_message"]["role"] == "user"
    assert body["reply"]["role"] == "assistant"
    assert body["reply"]["text"]


async def test_weekly_insights(auth_client):
    r = await auth_client.get("/insights/weekly")
    assert r.status_code == 200
    assert len(r.json()["metrics"]) == 4
