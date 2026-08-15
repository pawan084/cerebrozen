"""Work coaching for sponsored (organisation) members — talk, then get a plan.

The pattern is HeyCere's (`CereBroZen`) one transferable idea, grafted onto this
codebase's existing models: a coaching *conversation* whose output is
**structured committed actions**, not just talk. HeyCere runs a staged LangGraph
with a `dynamic_actions_insights_agent` extracting actions from the transcript;
here the same shape is two prompts and the tables that already exist —
`Plan`/`PlanStep` (which the Today hero and the plan screen already render) and
the honest `source` column ("ai" | "rule") the rest of the product already
respects.

Boundaries, stated because they are the point:

* **Only sponsored members.** The gate is `entitlements.resolve(...).sponsored`
  — the same resolution every other gate uses. Nothing about the consumer
  product changes for anyone else.
* **The organisation never sees any of this.** Work-chat turns are STATELESS —
  the client holds the transcript and sends it per turn; nothing is written to
  `chat_messages`, so work conversations never enter the wellness memory,
  insights, or export surfaces, and there is no row an org report could ever
  aggregate. The only persisted artefact is the user's own `Plan`, exactly as
  private as every other plan.
* **Safety never blocks** (hard rule). Every turn is scanned; a crisis-level
  message gets the region-correct resources appended to the reply, same as the
  wellness chat.
* **Everything degrades without keys** (hard rule). Keyless, the reply is a
  deterministic coaching prompt and the plan is an honest `source="rule"`
  fallback that says it is not personalised.

Prompts are registered in the same live registry the Oracle and the chat
personas use (`services/prompts`), so an admin can edit them from the console
like every other prompt — cerebroSG's equivalent of HeyCere's editable
workbook.
"""
from __future__ import annotations

import logging

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.plan import Plan, PlanStep
from app.models.user import User
from app.services import ai, prompts

logger = logging.getLogger("cerebro.workcoach")

#: The transcript cap per turn. The client holds history; the server refuses to
#: burn tokens on more than this many turns of it.
MAX_TURNS = 20
#: Per-message length cap, mirroring the chat route's input bound.
MAX_MESSAGE_CHARS = 2000
#: A plan is a next step, not a backlog. HeyCere's action extraction dedupes and
#: caps for the same reason: ten "committed actions" is a to-do list nobody does.
MAX_STEPS = 6

WORKCOACH_SYSTEM = prompts.register(
    "workcoach_system",
    "You are CereBro's work coach for employees whose organisation sponsors their "
    "access. You help with workload, focus, difficult conversations, boundaries and "
    "professional growth. Warm, practical, 2-4 short sentences. Ask at most one "
    "question per reply, and steer toward something the person could actually DO "
    "this week. You are NOT a therapist, never diagnose, and never give legal or HR "
    "rulings — for those, suggest the person's own HR or a qualified professional. "
    "Never claim their employer can or cannot see this conversation beyond: it is "
    "private to them. If they mention self-harm or suicide, respond with warmth and "
    "take it seriously, but do NOT name hotline numbers — the platform attaches the "
    "correct local resources itself.\n\n"
    # Rehearsal-lite (audit J#6): the sibling runs role-play as a dedicated
    # graph stage with measured turn budgets (minimum 4 so the model cannot
    # bail after persona setup; ceiling 16 for two rounds plus debrief). We do
    # not have stages; the same *behavioural* rules ride in the prompt, and the
    # numbers are theirs — measured, not invented here.
    "REHEARSAL: when the topic is a difficult conversation and the person seems "
    "willing, offer once — no pressure — to practise it: you play the other "
    "person, they play themselves. If they accept: stay in character for the "
    "exchange, never bail out after one line, and keep the whole rehearsal "
    "under about eight exchanges before stepping out of character to debrief "
    "— two things they did well, one thing to try. Decline gracefully if they "
    "would rather not, and never restart an offer they declined.",
)

WORKCOACH_EXTRACT = prompts.register(
    "workcoach_extract",
    "From the coaching conversation, extract the plan the person actually moved "
    "toward — commitments they made or agreed with, not things merely mentioned. "
    'Return JSON: {"title": str (<=60 chars, their goal in their words), '
    '"focus": str (one of: workload, focus, conversations, boundaries, growth), '
    '"rationale": str (<=200 chars, why these steps, referencing what they said), '
    '"steps": [{"title": str (<=60 chars, starts with a verb), "detail": str '
    "(<=140 chars, concrete enough to start)}]}. 3 to 6 steps. If the "
    "conversation contains no real commitments, return steps you would propose "
    "from what they described — but keep the rationale honest that they are "
    "proposals.",
)

#: Keyless / LLM-down replies — a deterministic coaching move, not an apology.
#: One per conversational beat so a short exchange doesn't repeat itself.
_FALLBACK_REPLIES = [
    "Let's make this concrete: what's the one thing about work that, if it were "
    "10% lighter this week, would matter most?",
    "That sounds worth working on. What have you already tried, and where did it "
    "get stuck?",
    "If you imagine next Friday and this went well — what's visibly different? "
    "Name one thing.",
    "What's the smallest version of that you could do before the end of the day "
    "tomorrow?",
]


def fallback_reply(turn_index: int) -> str:
    """A deterministic coaching prompt for the keyless path, varied by turn."""
    return _FALLBACK_REPLIES[turn_index % len(_FALLBACK_REPLIES)]


def _clip(text: str, limit: int) -> str:
    text = " ".join(str(text).split())
    return text if len(text) <= limit else text[: limit - 1].rstrip() + "…"


def transcript_text(turns: list[dict]) -> str:
    """Flatten client-held turns into the prompt transcript, capped and clipped."""
    lines = []
    for t in turns[-MAX_TURNS:]:
        role = "user" if t.get("role") != "assistant" else "assistant"
        text = _clip(t.get("text", ""), MAX_MESSAGE_CHARS)
        if text:
            lines.append(f"{role}: {text}")
    return "\n".join(lines)


async def _active_work_plan(db: AsyncSession, user: User) -> Plan | None:
    from sqlalchemy import select

    return await db.scalar(
        select(Plan)
        .where(Plan.user_id == user.id, Plan.active.is_(True), Plan.focus.in_(_WORK_FOCUSES))
        .order_by(Plan.created_at.desc())
        .limit(1)
    )


def _checkin_context(plan: Plan) -> str:
    """A system-prompt section describing the member's open plan.

    Audit J#2: both reference engines (the sibling's `coach_action_checkin`
    stage, HeyCere's `repeat_user_checkin_agent`) open a session by closing
    the last one's loop — a plan nobody asks about again is a list, not a
    loop. This stays inside /work's statelessness: the plan is data the user
    already owns in `plans`, read fresh per turn, injected into the PROMPT and
    stored nowhere new.
    """
    lines = [f"- [{'done' if s.done else 'open'}] {s.title}" for s in plan.steps]
    return (
        "\n\nCHECK-IN FIRST: the member has an active work plan, "
        f'"{plan.title}":\n' + "\n".join(lines) +
        "\nOpen this conversation by warmly asking how ONE open step went — "
        "before anything else. If every step is done, congratulate them and ask "
        "what they want to build on next. Never scold about open steps."
    )


async def reply(db: AsyncSession, user: User, turns: list[dict]) -> str:
    """One coaching reply. Keyless → a deterministic coaching move."""
    transcript = transcript_text(turns)
    # The live registry (admin-editable override) — same call the Oracle makes.
    system = await prompts.get("workcoach_system", db)
    # A FRESH conversation (no assistant turn yet) opens with a check-in on the
    # member's active work plan, when one exists. Mid-conversation turns never
    # re-inject it — the coach should follow the conversation, not reset it.
    if not any(t.get("role") == "assistant" for t in turns):
        plan = await _active_work_plan(db, user)
        if plan is not None:
            system += _checkin_context(plan)
    text = await ai.complete(system, transcript, max_tokens=260)
    if text:
        return text
    user_turns = sum(1 for t in turns if t.get("role") != "assistant")
    return fallback_reply(max(user_turns - 1, 0))


def _fallback_plan(user: User) -> tuple[str, str, str, list[dict]]:
    """The honest keyless plan: steady, generic, and it says so.

    Mirrors `agentic._fallback_plan`'s honesty contract (and the Today hero's):
    when nothing personalised the output, the rationale must not imply the
    conversation was read.
    """
    return (
        "A steadier work week",
        "workload",
        "A starting structure — not drawn from your conversation. Edit anything.",
        [
            {"title": "Name tomorrow's one priority", "detail": "Write it down before closing the laptop today."},
            {"title": "Block 45 focused minutes", "detail": "One block, notifications off, on the priority."},
            {"title": "Take one real break", "detail": "Away from the screen, before mid-afternoon."},
        ],
    )


async def propose_plan(db: AsyncSession, user: User, turns: list[dict]) -> Plan:
    """Materialise the conversation into the user's existing Plan model.

    Deactivates the user's previous work-focus plans (one active work plan, the
    same rule the daily plan follows) but NEVER touches wellness plans — the two
    products share a table, not a lifecycle.
    """
    extracted = None
    transcript = transcript_text(turns)
    if transcript:
        system = await prompts.get("workcoach_extract", db)
        # Structured output is used for EXTRACTION only, never for a routing or
        # coaching turn (audit J#5, the sibling's measured 2026-07-18 finding:
        # JSON mode biases models toward decisive form-filling — their exercise
        # gate silently skip-routed three eligible sessions under it). The
        # conversation itself always runs free-form through `reply`; a test
        # pins that complete_json appears only on this path.
        extracted = await ai.complete_json(system, transcript, max_tokens=600)

    source = "ai"
    if not isinstance(extracted, dict) or not isinstance(extracted.get("steps"), list) or not extracted["steps"]:
        title, focus, rationale, steps = _fallback_plan(user)
        source = "rule"
    else:
        title = _clip(extracted.get("title") or "Your work plan", 160)
        raw_focus = str(extracted.get("focus") or "workload").lower()
        focus = raw_focus if raw_focus in {"workload", "focus", "conversations", "boundaries", "growth"} else "workload"
        rationale = _clip(extracted.get("rationale") or "", 500)
        steps = extracted["steps"][:MAX_STEPS]

    # One active WORK plan; the scope of the deactivation is the point — a
    # `.where(active)` alone would silently retire the member's wellness plan.
    from sqlalchemy import select, update

    await db.execute(
        update(Plan)
        .where(Plan.user_id == user.id, Plan.active.is_(True), Plan.focus.in_(_WORK_FOCUSES))
        .values(active=False)
    )
    plan = Plan(
        user_id=user.id,
        title=_clip(title, 160),
        focus=focus,
        rationale=rationale,
        active=True,
        source=source,
    )
    db.add(plan)
    await db.flush()
    for i, s in enumerate(steps):
        db.add(
            PlanStep(
                plan_id=plan.id,
                title=_clip((s.get("title") if isinstance(s, dict) else str(s)) or "Step", 160),
                detail=_clip((s.get("detail") if isinstance(s, dict) else "") or "", 255),
                symbol="briefcase",
                order=i,
            )
        )
    await db.commit()
    # selectin relationship: refresh after commit so `steps` is loaded.
    await db.refresh(plan)
    return plan


#: The focus values this feature owns. A frozen set rather than `focus != ""`,
#: so the one-active-plan rule above can never reach into wellness plans.
_WORK_FOCUSES = frozenset({"workload", "focus", "conversations", "boundaries", "growth"})
