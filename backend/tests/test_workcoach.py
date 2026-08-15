"""/work — corporate coaching: talk, then get a plan (services/workcoach.py).

What must hold, in order of consequence: the gate (sponsored members only, with
an honest refusal for everyone else), the safety contract (a crisis turn gets
resources and is never blocked), the honesty contract (keyless extraction says
`source="rule"` and does not pretend it read the conversation), and the
separation contracts (work turns write NO chat rows; making a work plan never
deactivates a wellness plan).
"""
import uuid

from sqlalchemy import select

from app.core.database import SessionLocal
from app.models.chat import ChatMessage
from app.models.organization import STATUS_ACTIVE, Organization, OrgMembership
from app.models.plan import Plan
from app.models.safety import SafetyEvent
from app.models.user import User


async def _signup(client):
    email = f"work-{uuid.uuid4().hex[:10]}@test.app"
    r = await client.post("/auth/signup", json={"email": email, "password": "password123", "name": "W"})
    assert r.status_code == 201, r.text
    client.headers["Authorization"] = f"Bearer {r.json()['access_token']}"
    return email


async def _sponsor(email: str):
    async with SessionLocal() as db:
        org = Organization(name=f"Sponsor {uuid.uuid4().hex[:6]}", seats_licensed=10, grants_premium=True)
        db.add(org)
        await db.flush()
        user_id = await db.scalar(select(User.id).where(User.email == email))
        db.add(OrgMembership(org_id=org.id, user_id=user_id, status=STATUS_ACTIVE))
        await db.commit()
        return user_id


async def test_work_chat_is_sponsored_only_and_says_why(client):
    await _signup(client)
    r = await client.post("/work/chat", json={"message": "My week is chaos"})
    assert r.status_code == 403
    # An honest refusal, not a bare 403 — the client can render the reason.
    assert "organisation" in r.json()["detail"].lower()


async def test_personal_premium_is_not_enough(client):
    """The surface exists because an employer sponsors it; a personally-paid
    account keeps every consumer feature and does not gain this one."""
    email = await _signup(client)
    async with SessionLocal() as db:
        user = await db.scalar(select(User).where(User.email == email))
        user.subscription_tier = "premium"
        await db.commit()
    assert (await client.post("/work/chat", json={"message": "hi"})).status_code == 403


async def test_sponsored_member_chats_and_no_chat_rows_are_written(client):
    email = await _signup(client)
    user_id = await _sponsor(email)

    r = await client.post("/work/chat", json={"message": "I can't focus with all these meetings"})
    assert r.status_code == 200, r.text
    body = r.json()
    # Keyless CI: the deterministic coaching fallback, never an empty reply.
    assert body["reply"].strip()
    assert body["risk_level"] in {"none", "low", "elevated", "crisis"}

    # Separation contract: work turns are stateless — nothing may land in the
    # wellness chat history, where memory/insights/export would read it.
    async with SessionLocal() as db:
        rows = (await db.scalars(select(ChatMessage).where(ChatMessage.user_id == user_id))).all()
    assert rows == [], "a work turn leaked into the wellness chat history"


async def test_crisis_turn_gets_resources_and_is_never_blocked(client):
    email = await _signup(client)
    user_id = await _sponsor(email)
    # Region set explicitly: a fresh account has none, and reply_suffix then
    # falls back to the generic international lines — which is correct, but the
    # promise this test pins is REGION-correct resources, so it needs a region.
    assert (await client.patch("/users/me", json={"region": "IN"})).status_code == 200

    r = await client.post("/work/chat", json={"message": "work is so bad I want to kill myself"})
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["risk_level"] == "crisis"
    # The platform appends region-correct lines — Tele-MANAS for IN.
    assert "14416" in body["reply"]

    # And the event was recorded for the safety queue, source-tagged.
    async with SessionLocal() as db:
        ev = (await db.scalars(select(SafetyEvent).where(SafetyEvent.user_id == user_id))).all()
    assert any(e.source == "work" for e in ev)


async def test_plan_is_created_with_honest_rule_source_when_keyless(client):
    email = await _signup(client)
    await _sponsor(email)

    history = [
        {"role": "user", "text": "I want fewer chaotic mornings"},
        {"role": "assistant", "text": "What would a calmer morning look like?"},
        {"role": "user", "text": "Starting with one clear priority instead of email"},
    ]
    r = await client.post("/work/plan", json={"history": history})
    assert r.status_code == 201, r.text
    plan = r.json()
    # Keyless: the fallback plan, and it says so — source "rule", and a
    # rationale that does NOT pretend the conversation was read.
    assert plan["source"] == "rule"
    assert "not drawn from your conversation" in plan["rationale"]
    assert 1 <= len(plan["steps"]) <= 6
    assert plan["focus"] == "workload"
    # The steps are the task list, rendered by the existing plan screen.
    assert all(s["title"] for s in plan["steps"])


async def test_a_new_work_plan_retires_the_old_one_but_never_a_wellness_plan(client):
    email = await _signup(client)
    user_id = await _sponsor(email)

    # A wellness plan (the daily plan's shape — focus outside the work set).
    async with SessionLocal() as db:
        db.add(Plan(user_id=user_id, title="Evening wind-down", focus="sleep", active=True, source="rule"))
        await db.commit()

    assert (await client.post("/work/plan", json={"history": []})).status_code == 201
    second = await client.post("/work/plan", json={"history": []})
    assert second.status_code == 201

    async with SessionLocal() as db:
        plans = (await db.scalars(select(Plan).where(Plan.user_id == user_id))).all()
    work = [p for p in plans if p.focus == "workload"]
    wellness = [p for p in plans if p.focus == "sleep"]
    assert sum(1 for p in work if p.active) == 1, "exactly one active work plan"
    assert wellness and all(p.active for p in wellness), "a work plan retired a wellness plan"


async def test_prompts_are_registered_for_admin_editing():
    """The two prompts live in the same registry the Oracle uses, so the admin
    console can override them — cerebroSG's version of HeyCere's workbook."""
    from app.services import prompts
    import app.services.workcoach  # noqa: F401  (registers on import)

    names = prompts.registered()
    assert "workcoach_system" in names
    assert "workcoach_extract" in names


async def test_a_fresh_conversation_checks_in_on_the_active_work_plan(client, monkeypatch):
    """Audit J#2: the plan becomes a loop. A fresh /work/chat with an active
    work plan must put that plan in front of the model; a mid-conversation
    turn must not (the coach follows the conversation, it doesn't reset it).

    Pinned at the prompt boundary — `ai.complete` is captured — because
    keylessly the reply itself is the deterministic fallback either way.
    """
    email = await _signup(client)
    await _sponsor(email)
    await client.post("/work/plan", json={"history": []})   # creates the fallback work plan

    seen: list[str] = []

    async def _capture(system, prompt, max_tokens=1024):
        seen.append(system)
        return None   # stay on the keyless path

    from app.services import ai as ai_service
    monkeypatch.setattr(ai_service, "complete", _capture)

    # Fresh conversation → the system prompt carries the plan.
    r = await client.post("/work/chat", json={"message": "hello again"})
    assert r.status_code == 200
    assert "CHECK-IN FIRST" in seen[-1]
    assert "A steadier work week" in seen[-1]        # the plan's own title
    assert "[open]" in seen[-1]

    # Mid-conversation → no re-injection.
    r = await client.post("/work/chat", json={
        "message": "it went okay",
        "history": [
            {"role": "user", "text": "hello again"},
            {"role": "assistant", "text": "How did naming a priority go?"},
        ],
    })
    assert r.status_code == 200
    assert "CHECK-IN FIRST" not in seen[-1]


async def test_a_fresh_conversation_without_a_plan_gets_no_checkin_section(client, monkeypatch):
    email = await _signup(client)
    await _sponsor(email)
    seen: list[str] = []

    async def _capture(system, prompt, max_tokens=1024):
        seen.append(system)
        return None

    from app.services import ai as ai_service
    monkeypatch.setattr(ai_service, "complete", _capture)

    assert (await client.post("/work/chat", json={"message": "hi"})).status_code == 200
    assert "CHECK-IN FIRST" not in seen[-1]


def test_structured_output_is_extraction_only():
    """Audit J#5, the sibling's measured lesson (2026-07-18): JSON mode biases
    models toward decisive form-filling — their routing gate silently
    skip-routed eligible sessions under it. So in this module, complete_json
    may appear ONLY on the extraction path; every conversational turn runs
    free-form. A structural pin, so the next feature doesn't reach for
    structured output on a coaching turn because it looks tidier.
    """
    import inspect
    from app.services import workcoach

    assert "complete_json" not in inspect.getsource(workcoach.reply)
    assert "complete_json" in inspect.getsource(workcoach.propose_plan)
    # And nowhere else in the module.
    module_src = inspect.getsource(workcoach)
    assert module_src.count("ai.complete_json") == 1


def test_rehearsal_instruction_ships_in_the_registered_prompt():
    """J#6-lite: the rehearsal behaviour (offer once, stay in character, ~8
    exchanges, debrief) rides in the DEFAULT prompt, so an admin edit that
    drops it is a visible diff against the code default, not silent."""
    from app.services.workcoach import WORKCOACH_SYSTEM

    assert "REHEARSAL" in WORKCOACH_SYSTEM
    assert "debrief" in WORKCOACH_SYSTEM
    assert "never restart an offer they declined" in WORKCOACH_SYSTEM


async def test_ai_extraction_branch_shapes_and_clips(client, monkeypatch):
    """The 'ai' branch, exercised with a mocked model: shaping must clip long
    strings to the column limits, reject junk focus values, and cap steps at
    six — the LLM's enthusiasm must never become a 500 or a 12-step backlog."""
    email = await _signup(client)
    await _sponsor(email)

    async def _fake_json(system, prompt, max_tokens=1024):
        return {
            "title": "T" * 400,                       # over the 160 column
            "focus": "world domination",              # not in the vocabulary
            "rationale": "R" * 900,                   # over the 500 clip
            "steps": [{"title": f"Step {i}", "detail": "D" * 400} for i in range(12)],
        }

    from app.services import ai as ai_service
    monkeypatch.setattr(ai_service, "complete_json", _fake_json)

    r = await client.post("/work/plan", json={"history": [{"role": "user", "text": "help me focus"}]})
    assert r.status_code == 201, r.text
    plan = r.json()
    assert plan["source"] == "ai"
    assert len(plan["title"]) <= 160
    assert plan["focus"] == "workload"          # junk focus falls back safely
    assert len(plan["rationale"]) <= 500
    assert len(plan["steps"]) == 6              # capped, not 12
    assert all(len(s["detail"]) <= 255 for s in plan["steps"])
