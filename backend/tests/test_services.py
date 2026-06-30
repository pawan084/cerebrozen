"""Unit coverage for the service layer: insights, nudges, notifications,
voice (mocked HTTP), ai provider dispatch, and agentic plan fallback."""
import uuid
from datetime import timedelta

import pytest

from app.core.database import SessionLocal, utcnow
from app.core.security import hash_password
from app.models.journal import JournalEntry
from app.models.mood import MoodLog
from app.models.nudge import Nudge
from app.models.user import User
from app.services import agentic, ai, insights, notifications, nudges, voice


async def _make_user(session, *, push_token=None):
    user = User(
        email=f"svc-{uuid.uuid4().hex[:10]}@test.app",
        hashed_password=hash_password("x"),
        name="Svc",
        push_token=push_token,
    )
    session.add(user)
    await session.flush()
    return user


# ── Insights ────────────────────────────────────────────────────────────
async def test_insights_fresh_user_branch():
    async with SessionLocal() as s:
        user = await _make_user(s)
        data = await insights.compute_weekly(s, user)
        assert data["headline"] == "A fresh start"
        assert len(data["metrics"]) == 4


async def test_insights_with_activity_branches():
    async with SessionLocal() as s:
        user = await _make_user(s)
        s.add(MoodLog(user_id=user.id, mood="calm", note="", symbol="x", intensity=2))
        await s.flush()
        data = await insights.compute_weekly(s, user)
        assert data["headline"] == "Building a rhythm"   # sessions but no journal

        s.add(JournalEntry(user_id=user.id, title="note", body="", tags=[], symbol="book", risk_level="none"))
        await s.flush()
        data2 = await insights.compute_weekly(s, user)
        assert data2["headline"] == "Calmer evenings"     # journal + sessions


# ── Nudges ──────────────────────────────────────────────────────────────
async def test_schedule_default_is_idempotent():
    async with SessionLocal() as s:
        user = await _make_user(s)
        first = await nudges.schedule_default_nudges(s, user)
        assert len(first) == 2
        again = await nudges.schedule_default_nudges(s, user)
        assert again == []   # already pending → none added


async def test_schedule_contextual():
    async with SessionLocal() as s:
        user = await _make_user(s)
        assert await nudges.schedule_contextual(s, user) is None   # no rough mood
        s.add(MoodLog(user_id=user.id, mood="anxious", note="", symbol="x", intensity=4))
        await s.flush()
        nudge = await nudges.schedule_contextual(s, user)
        assert nudge is not None and nudge.kind == "reset"


async def test_dispatch_due_sends_past_nudges():
    async with SessionLocal() as s:
        user = await _make_user(s, push_token="tok-123")
        s.add(Nudge(user_id=user.id, kind="checkin", title="t", body="b",
                    deeplink="cerebro://x", scheduled_for=utcnow() - timedelta(minutes=5)))
        await s.commit()
        sent = await nudges.dispatch_due(s)
        assert sent >= 1


# ── Notifications (APNs disabled → log fallback) ────────────────────────
async def test_send_push_paths():
    async with SessionLocal() as s:
        no_token = await _make_user(s)
        with_token = await _make_user(s, push_token="device-token")
        await s.flush()
        nudge = Nudge(user_id=with_token.id, kind="reminder", title="t", body="b", deeplink="d",
                      scheduled_for=utcnow())
        assert await notifications.send_push(no_token, nudge) is False        # no token branch
        assert await notifications.send_push(with_token, nudge) is True       # log fallback (apns off)


# ── Voice (mocked HTTP) ─────────────────────────────────────────────────
class _FakeResp:
    def __init__(self, json_data=None, content=b"", status=200):
        self._json = json_data
        self.content = content
        self.status_code = status

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError("http error")

    def json(self):
        return self._json


class _FakeClient:
    def __init__(self, resp):
        self._resp = resp

    async def __aenter__(self):
        return self

    async def __aexit__(self, *a):
        return False

    async def post(self, *a, **k):
        return self._resp


async def test_transcribe_disabled_and_enabled(monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "deepgram_api_key", "")
    assert await voice.transcribe(b"audio") is None              # disabled

    monkeypatch.setattr(settings, "deepgram_api_key", "key")
    resp = _FakeResp(json_data={"results": {"channels": [{"alternatives": [{"transcript": "hello world"}]}]}})
    monkeypatch.setattr(voice.httpx, "AsyncClient", lambda *a, **k: _FakeClient(resp))
    assert await voice.transcribe(b"audio") == "hello world"


async def test_synthesize_disabled_and_enabled(monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "elevenlabs_api_key", "")
    assert await voice.synthesize("hi") is None                  # disabled

    monkeypatch.setattr(settings, "elevenlabs_api_key", "key")
    monkeypatch.setattr(voice.httpx, "AsyncClient", lambda *a, **k: _FakeClient(_FakeResp(content=b"MP3")))
    assert await voice.synthesize("calm down") == b"MP3"


# ── AI provider dispatch ────────────────────────────────────────────────
async def test_ai_disabled_returns_none(monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "openai_api_key", "")
    monkeypatch.setattr(settings, "anthropic_api_key", "")
    assert await ai.complete("sys", "prompt") is None
    assert await ai.complete_json("sys", "prompt") is None


async def test_complete_json_parsing(monkeypatch):
    async def fake_complete(system, prompt, max_tokens=1024):
        return "```json\n{\"a\": 1, \"b\": [2, 3]}\n```"

    monkeypatch.setattr(ai, "complete", fake_complete)
    assert await ai.complete_json("s", "p") == {"a": 1, "b": [2, 3]}

    async def fake_no_json(system, prompt, max_tokens=1024):
        return "sorry, no json here"

    monkeypatch.setattr(ai, "complete", fake_no_json)
    assert await ai.complete_json("s", "p") is None


# ── Agentic plan (deterministic fallback) ───────────────────────────────
async def test_generate_plan_fallback(monkeypatch):
    async def no_ai(system, prompt, max_tokens=1024):
        return None

    monkeypatch.setattr(ai, "complete_json", no_ai)
    async with SessionLocal() as s:
        user = await _make_user(s)
        user.goals = ["Sleep better"]
        await s.flush()
        plan = await agentic.generate_plan(s, user)
        assert plan.source == "rule"
        assert len(plan.steps) >= 1
