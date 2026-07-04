"""Coverage for the Oracle's read tools that don't need a live LLM/graph.

(The write tools — log_mood / save_journal — call LangGraph's ``interrupt()`` and
are exercised through the streaming integration, not unit tests.)
"""
import uuid

from app.agent import tools
from app.agent.context import current_db, current_user_id, emitted_widgets
from app.core.database import SessionLocal
from app.core.security import hash_password
from app.models.consent import Consent
from app.models.user import User


async def test_suggest_activity_emits_widget():
    emitted_widgets.set([])
    out = await tools.suggest_activity.ainvoke({"kind": "breathing"})
    assert "breathing" in out.lower() or "2-minute" in out.lower()
    assert len(emitted_widgets.get()) == 1
    assert emitted_widgets.get()[0]["widget_kind"] == "breathing"


async def test_suggest_activity_unknown_kind():
    emitted_widgets.set([])
    out = await tools.suggest_activity.ainvoke({"kind": "nonsense"})
    assert "unknown" in out.lower()
    assert emitted_widgets.get() == []


async def test_get_weekly_insights_tool():
    async with SessionLocal() as s:
        user = User(email=f"tool-{uuid.uuid4().hex[:8]}@test.app",
                    hashed_password=hash_password("x"), name="T")
        user.consent = Consent()   # mirror signup; consent gates read it
        s.add(user)
        await s.flush()
        current_db.set(s)
        current_user_id.set(user.id)
        out = await tools.get_weekly_insights.ainvoke({})
        assert isinstance(out, str) and len(out) > 0
