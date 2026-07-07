"""Pattern dashboard (transparent AI memory) + the delete-memory endpoint."""
import uuid
from datetime import timedelta

from sqlalchemy import select

from app.core.database import SessionLocal, utcnow
from app.models.chat import ChatMessage
from app.models.insight import Insight
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.user import User


async def _signup(client, prefix="patterns"):
    addr = f"{prefix}-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": addr, "password": "password123", "name": "P"})
    assert r.status_code == 201
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    # Patterns read mood/journal history — flip the consents on (private-by-default).
    await client.patch("/users/me/consent", json={"mood_history": True, "journal_memory": True})
    return addr


async def _user_id(addr: str) -> uuid.UUID:
    async with SessionLocal() as s:
        return (await s.scalar(select(User).where(User.email == addr))).id


async def test_patterns_empty_without_data(client):
    await _signup(client)
    r = await client.get("/insights/patterns")
    assert r.status_code == 200
    body = r.json()
    assert body["patterns"] == [] and body["enough_data"] is False


async def test_hardest_time_pattern_needs_a_dominant_bucket(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    async with SessionLocal() as s:
        # 8 difficult check-ins, 6 in the evening (UTC user timezone default is
        # Asia/Kolkata — use hours that stay in the evening bucket there: 15 UTC = 20:30 IST).
        for i in range(6):
            s.add(MoodLog(user_id=uid, mood="Anxious", note="", symbol="wind", intensity=4,
                          created_at=utcnow().replace(hour=15) - timedelta(days=i)))
        for i in range(2):
            s.add(MoodLog(user_id=uid, mood="Low", note="", symbol="moon", intensity=4,
                          created_at=utcnow().replace(hour=3) - timedelta(days=10 + i)))
        await s.commit()
    body = (await client.get("/insights/patterns")).json()
    statements = [p["statement"] for p in body["patterns"]]
    assert any("hardest time of day" in st for st in statements), statements
    assert all("basis" in p and p["basis"] for p in body["patterns"])


async def test_consent_off_silences_the_source(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    async with SessionLocal() as s:
        for i in range(8):
            s.add(MoodLog(user_id=uid, mood="Anxious", note="", symbol="wind", intensity=5,
                          created_at=utcnow().replace(hour=15) - timedelta(days=i)))
        await s.commit()
    await client.patch("/users/me/consent", json={"mood_history": False})
    body = (await client.get("/insights/patterns")).json()
    assert body["patterns"] == []
    assert body["sources"]["mood_history"] is False


async def test_delete_memory_wipes_chat_and_insights_only(client):
    addr = await _signup(client)
    uid = await _user_id(addr)
    async with SessionLocal() as s:
        s.add(ChatMessage(user_id=uid, role="user", text="hello"))
        s.add(ChatMessage(user_id=uid, role="assistant", text="hi"))
        s.add(Insight(user_id=uid, period="weekly", headline="h", summary="s", metrics=[]))
        s.add(JournalEntry(user_id=uid, title="Keep me", body="user content"))
        s.add(MoodLog(user_id=uid, mood="Good", note="", symbol="sun", intensity=2))
        await s.commit()

    r = await client.delete("/users/me/memory")
    assert r.status_code == 200
    body = r.json()
    assert body["chat_messages"] == 2 and body["insights"] == 1

    async with SessionLocal() as s:
        assert (await s.scalar(select(ChatMessage).where(ChatMessage.user_id == uid))) is None
        assert (await s.scalar(select(Insight).where(Insight.user_id == uid))) is None
        # User content is deliberately untouched.
        assert (await s.scalar(select(JournalEntry).where(JournalEntry.user_id == uid))) is not None
        assert (await s.scalar(select(MoodLog).where(MoodLog.user_id == uid))) is not None
