"""Second wave of service coverage: safety fallback heuristics, the Oracle graph
build, the real APNs send path (mocked transport), and AI provider SDK dispatch."""
import uuid

import pytest

from app.services import ai, safety


# ── Safety classifier fallback (LLM off → keyword heuristic) ─────────────
async def test_safety_classify_branches(monkeypatch):
    async def no_ai(system, prompt, max_tokens=1024):
        return None

    monkeypatch.setattr(ai, "complete_json", no_ai)

    assert (await safety.classify(""))[0] == "none"                       # empty
    assert (await safety.classify("I want to end my life"))[0] == "crisis"  # crisis term
    assert (await safety.classify("I feel hopeless and worthless"))[0] == "elevated"  # elevated term
    assert (await safety.classify("had a nice cup of tea"))[0] == "none"   # nothing flagged


async def test_safety_uses_llm_result(monkeypatch):
    async def fake(system, prompt, max_tokens=1024):
        return {"risk_level": "low", "reason": "mild"}

    monkeypatch.setattr(ai, "complete_json", fake)
    level, reason = await safety.classify("a bit stressed")
    assert level == "low" and reason == "mild"


# ── Oracle graph build (no model call — just construction) ───────────────
async def test_graph_build_matches_llm_availability():
    from app.agent.graph import get_graph
    from app.core.config import settings

    # With a key the graph compiles (covers _chat_model + StateGraph wiring,
    # without calling the model); hermetically it returns None (the guard path).
    graph = await get_graph()
    if settings.ai_enabled:
        assert graph is not None
    else:
        assert graph is None


# ── Notifications: real APNs path with a mocked HTTP/2 transport ─────────
class _Resp:
    def __init__(self, status=200):
        self.status_code = status
        self.text = ""


class _Client:
    def __init__(self, *a, **k):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *a):
        return False

    async def post(self, *a, **k):
        return _Resp(200)


async def test_apns_send_path(monkeypatch, tmp_path):
    from app.core.config import settings
    from app.core.database import SessionLocal
    from app.core.security import hash_password
    from app.models.nudge import Nudge
    from app.models.user import User
    from app.services import notifications

    # Enable APNs with a dummy key file; stub token signing + HTTP transport.
    key_file = tmp_path / "AuthKey.p8"
    key_file.write_text("-----BEGIN PRIVATE KEY-----\nx\n-----END PRIVATE KEY-----\n")
    monkeypatch.setattr(settings, "apns_key_path", str(key_file))
    monkeypatch.setattr(settings, "apns_key_id", "KEY123")
    monkeypatch.setattr(settings, "apns_team_id", "TEAM123")
    monkeypatch.setattr(notifications.jwt, "encode", lambda *a, **k: "signed.jwt.token")
    monkeypatch.setattr(notifications.httpx, "AsyncClient", _Client)
    notifications._token_cache.clear()

    async with SessionLocal() as s:
        user = User(email=f"apns-{uuid.uuid4().hex[:8]}@test.app",
                    hashed_password=hash_password("x"), name="A", push_token="device-tok")
        s.add(user)
        await s.flush()
        nudge = Nudge(user_id=user.id, kind="checkin", title="t", body="b",
                      deeplink="cerebro://x", scheduled_for=None)
        assert await notifications.send_push(user, nudge) is True


# ── AI provider dispatch (mocked OpenAI + Anthropic SDK clients) ─────────
class _FakeOpenAIMessage:
    content = "openai reply"


class _FakeOpenAIChoice:
    message = _FakeOpenAIMessage()


class _FakeOpenAIResp:
    choices = [_FakeOpenAIChoice()]


class _FakeOpenAIClient:
    class chat:  # noqa: N801
        class completions:  # noqa: N801
            @staticmethod
            async def create(**kwargs):
                return _FakeOpenAIResp()


async def test_ai_openai_path(monkeypatch):
    from app.core.config import settings

    monkeypatch.setattr(settings, "openai_api_key", "sk-test")
    monkeypatch.setattr(ai, "_get_openai", lambda: _FakeOpenAIClient())
    out = await ai.complete("system", "prompt")
    assert out == "openai reply"


class _FakeBlock:
    type = "text"
    text = "anthropic reply"


class _FakeAnthropicMsg:
    content = [_FakeBlock()]


class _FakeAnthropicClient:
    class messages:  # noqa: N801
        @staticmethod
        async def create(**kwargs):
            return _FakeAnthropicMsg()


async def test_ai_anthropic_path(monkeypatch):
    from app.core.config import settings

    # Force the anthropic branch: no OpenAI key, anthropic key present.
    monkeypatch.setattr(settings, "openai_api_key", "")
    monkeypatch.setattr(settings, "anthropic_api_key", "ak-test")
    monkeypatch.setattr(ai, "_get_anthropic", lambda: _FakeAnthropicClient())
    out = await ai.complete("system", "prompt")
    assert out == "anthropic reply"
