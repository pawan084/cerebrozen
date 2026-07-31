"""Weekly insights must not report a reading it has no data for.

`stability` used to default to 0.7 when there was no mood data — above the
"Steady" threshold — so the weekly insight told a user who had logged nothing
that their mood was Steady, with a 70% bar beneath it, and said the same to a
user who had switched mood history OFF. The product tells people on the Pattern
Dashboard that "patterns only appear once real check-ins support them — no
guesses, ever". This is that rule, applied where it was being broken.
"""
import uuid



async def _signup(client):
    addr = f"insights-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "I"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return addr


def _metric(payload: dict, label: str) -> dict:
    return next(m for m in payload["metrics"] if m["label"] == label)


async def test_no_check_ins_reports_no_check_ins_not_steady(client):
    await _signup(client)
    payload = (await client.get("/insights/weekly")).json()
    mood = _metric(payload, "Mood stability")
    assert mood["value"] == "No check-ins yet"
    assert mood["progress"] == 0.0, "an empty week must not draw a 70% bar"


async def test_switching_mood_history_off_also_reports_no_reading(client):
    """A user who withheld the data must not be shown a conclusion drawn from it."""
    await _signup(client)
    await client.post("/moods", json={"mood": "Anxious", "note": "", "intensity": 4})
    r = await client.patch("/users/me/consent", json={"mood_history": False})
    assert r.status_code == 200

    payload = (await client.get("/insights/weekly")).json()
    assert _metric(payload, "Mood stability")["value"] == "No check-ins yet"


async def test_real_check_ins_do_produce_a_reading(client):
    await _signup(client)
    for _ in range(3):
        await client.post("/moods", json={"mood": "Good", "note": "", "intensity": 1})
    payload = (await client.get("/insights/weekly")).json()
    mood = _metric(payload, "Mood stability")
    assert mood["value"] in {"Steady", "Variable"}
    assert mood["progress"] > 0.0


async def test_one_session_is_not_described_as_a_few(client):
    await _signup(client)
    await client.post("/moods", json={"mood": "Good", "note": "", "intensity": 2})
    payload = (await client.get("/insights/weekly")).json()
    assert "a few" not in payload["summary"].lower()
