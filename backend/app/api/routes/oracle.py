"""Oracle — streaming agentic chat (LangGraph).

SSE frames (each `data:` line is a JSON object with a `type`):
  token          {"type":"token","text":"…"}          incremental assistant text
  crisis         {"type":"crisis","resources":{…}}     region-aware crisis hotlines
  widget         {"type":"widget","widget":{…}}        inline activity to render
  tool           {"type":"tool","tool":"…"}            the agent is running this tool
  tool_confirm   {"type":"tool_confirm","tool":…,"summary":…,"thread_id":…}
  awaiting_confirm                                       stream paused for approval
  done           {"type":"done","text":"…"}            final assistant text
  error          {"type":"error","detail":"…"}

The `tool` frame carries the tool NAME and nothing else — arguments routinely
quote the user's own words (a journal title, a mood note), and progress display
does not need them. Write tools follow with `tool_confirm`, which is the frame
that carries a human summary; `tool` exists so a read tool's latency reads as
"the agent is checking your week" rather than as a stall (WC-138/139).

Approve/decline a paused write tool via POST /oracle/confirm, which resumes the
same thread. Falls back with 503 when the agent is disabled; clients then use the
deterministic /chat route.
"""
import json
import logging
import uuid

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse
from langchain_core.messages import HumanMessage
from langgraph.types import Command
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.agent.context import current_db, current_risk, current_thread_id, current_user_id, emitted_widgets
from app.agent.graph import get_graph
from app.core.config import settings
from app.core.database import SessionLocal, get_db, utcnow
from app.core.deps import get_current_user
from app.core.ratelimit import account_limit, limiter
from app.models.chat import ChatMessage
from app.models.user import User
from app.models.agent_action import AgentAction
from app.schemas.content_data import AgentActionOut
from app.services import crisis, language, safety, usage, verification

logger = logging.getLogger("cerebro.oracle")

router = APIRouter(prefix="/oracle", tags=["oracle"])


class OracleSend(BaseModel):
    # Same bound as ChatSend (register C17): the text feeds a streaming agent
    # run, so an uncapped body is uncapped provider cost.
    text: str = Field(min_length=1, max_length=4000)
    thread_id: str | None = None


class OracleConfirm(BaseModel):
    thread_id: str
    approved: bool


def _sse(obj: dict) -> str:
    return f"data: {json.dumps(obj)}\n\n"


@router.get("/status")
async def status(user: User = Depends(get_current_user)):
    return {"available": settings.oracle_available}


async def _run(graph_input, thread_id: str, user_id: uuid.UUID, persist_user: str | None,
               region: str = "", language_directive: str = ""):
    """Stream the graph run as SSE, managing request-scoped context + persistence."""
    graph = await get_graph()
    # The graph is compiled once and shared across users, so per-user context
    # travels in `configurable` rather than the closure.
    config = {"configurable": {"thread_id": thread_id,
                               "language_directive": language_directive}}

    async with SessionLocal() as db:
        t_db = current_db.set(db)
        t_uid = current_user_id.set(user_id)
        t_w = emitted_widgets.set([])
        t_tid = current_thread_id.set(thread_id)
        # Read-only-on-risk gate: default to "none" for turns that carry no
        # user text (a /confirm resume approves a PREVIOUS calm turn's write).
        t_risk = current_risk.set("none")
        try:
            if persist_user is not None:
                # Flushed first so the safety event can point at the message
                # (register C71) — mirrors /chat.
                user_msg = ChatMessage(user_id=user_id, role="user", text=persist_user)
                db.add(user_msg)
                await db.flush()
                risk = await safety.scan_and_record(
                    db, user_id=user_id, source="chat", source_id=user_msg.id, text=persist_user)
                current_risk.set(risk)
                user_msg.risk_level = risk
                await db.commit()
                # Surface crisis resources for elevated distress too (not only
                # explicit crisis) so the client raises its CrisisBanner — matches
                # the /chat path (activities.route surfaces the crisis suggestion
                # for {"elevated", "crisis"}). Escalation stays crisis-only inside
                # scan_and_record; this only shows the user a helpline.
                if risk in {"elevated", "crisis"}:
                    yield _sse({"type": "crisis", "resources": crisis.resources_for(region)})

            parts: list[str] = []
            async for mode, chunk in graph.astream(graph_input, config, stream_mode=["messages", "updates"]):
                if mode == "messages":
                    msg, _meta = chunk
                    content = getattr(msg, "content", "")
                    if content and getattr(msg, "type", "") == "AIMessageChunk":
                        parts.append(content)
                        yield _sse({"type": "token", "text": content})
                elif mode == "updates" and "agent" in chunk:
                    # The agent node just decided to call tools. Announce each by
                    # NAME before the ToolNode runs, so its latency renders as
                    # "checking your week" rather than a dead stream. Names only:
                    # tool arguments routinely quote the user's own words.
                    for m in (chunk["agent"] or {}).get("messages", []):
                        for call in getattr(m, "tool_calls", None) or []:
                            name = str(call.get("name", "")) if isinstance(call, dict) else str(getattr(call, "name", ""))
                            if name:
                                yield _sse({"type": "tool", "tool": name[:64]})
                elif mode == "updates" and "__interrupt__" in chunk:
                    intr = chunk["__interrupt__"][0]
                    payload = dict(intr.value) if isinstance(intr.value, dict) else {"summary": str(intr.value)}
                    payload.update({"type": "tool_confirm", "thread_id": thread_id})
                    # Audit: the proposal is durable from the moment it is
                    # shown, not from the moment it is approved. A user who is
                    # repeatedly asked to approve something they never want is
                    # exactly what this table has to be able to reveal.
                    db.add(AgentAction(
                        user_id=user_id,
                        thread_id=thread_id,
                        tool=str(payload.get("tool", ""))[:64],
                        summary=str(payload.get("summary", ""))[:2000],
                        status="proposed",
                    ))
                    await db.commit()
                    yield _sse(payload)

            for widget in emitted_widgets.get():
                yield _sse({"type": "widget", "widget": widget})

            state = await graph.aget_state(config)
            if state.next:                       # paused on an interrupt
                yield _sse({"type": "awaiting_confirm", "thread_id": thread_id})
            else:
                final = "".join(parts).strip()
                if final:
                    db.add(ChatMessage(user_id=user_id, role="assistant", text=final))
                    await db.commit()
                yield _sse({"type": "done", "text": final})
        except Exception:  # pragma: no cover - surfaced to the client
            # Log the real error; never stream internals (paths, SQL, provider
            # messages) to the client.
            logger.exception("Oracle stream failed (thread %s)", thread_id)
            yield _sse({"type": "error", "detail": "Something went wrong — please try again."})
        finally:
            current_db.reset(t_db)
            current_user_id.reset(t_uid)
            emitted_widgets.reset(t_w)
            current_thread_id.reset(t_tid)
            current_risk.reset(t_risk)


def scoped_thread_id(user_id, requested: str | None) -> str:
    """Namespace a caller-supplied thread id inside the caller's own threads.

    The register's C1: both streaming endpoints accepted any ``thread_id``
    verbatim, and thread ids default to ``str(user.id)`` — so another user's
    UUID resumed *their* LangGraph checkpoint, and a paused ``save_journal``
    could be approved by an attacker. A supplied id now lands at
    ``"<caller>:<id>"``: the caller's own namespace, where a foreign UUID
    resumes nothing. The bare default (no id, or the caller's own id) stays
    ``str(user.id)`` so every existing conversation is preserved.
    """
    own = str(user_id)
    requested = (requested or "").strip()
    if not requested or requested == own:
        return own
    return f"{own}:{requested}"


@router.post("/messages")
@limiter.limit("30/minute")
@account_limit("30/minute")   # …and the same ceiling per account (LLM agent turn)
async def messages(
    request: Request,
    payload: OracleSend,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    await verification.require_verified_email(db, user, feature='oracle')
    await usage.consume(db, user, "oracle_turn")
    if not settings.oracle_available or await get_graph() is None:
        raise HTTPException(status_code=503, detail="Oracle is not enabled")
    await usage.enforce_quota(db, user)   # free-tier daily cap (429 when exceeded)
    thread_id = scoped_thread_id(user.id, payload.thread_id)
    gen = _run({"messages": [HumanMessage(content=payload.text)]}, thread_id, user.id,
               persist_user=payload.text, region=user.region,
               language_directive=language.for_user(user))
    return StreamingResponse(gen, media_type="text/event-stream")


@router.get("/actions", response_model=list[AgentActionOut])
async def my_agent_actions(
    user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)
):
    """What the assistant has proposed writing on your behalf, and what you said.

    The user's own audit trail, not just the admin's — "did the assistant write
    that, or did I?" is a question they should be able to answer themselves.
    """
    return (
        await db.scalars(
            select(AgentAction)
            .where(AgentAction.user_id == user.id)
            .order_by(AgentAction.created_at.desc())
            .limit(100)
        )
    ).all()


async def _record_decision(db: AsyncSession, user: User, thread_id: str, approved: bool) -> None:
    """Stamp the outcome on the newest still-undecided proposal for this thread.

    Scoped to the caller's own rows, so a guessed thread_id cannot mark someone
    else's proposal as approved. A decision with no matching proposal is a no-op
    rather than an error — the stream is the source of truth for what runs, and
    the audit trail must never be able to block it.
    """
    row = await db.scalar(
        select(AgentAction)
        .where(
            AgentAction.user_id == user.id,
            AgentAction.thread_id == thread_id,
            AgentAction.status == "proposed",
        )
        .order_by(AgentAction.created_at.desc())
    )
    if row is None:
        return
    row.status = "approved" if approved else "declined"
    row.decided_at = utcnow()
    await db.commit()


@router.post("/confirm")
@limiter.limit("30/minute")
@account_limit("30/minute")   # …and the same ceiling per account (LLM agent turn)
async def confirm(request: Request, payload: OracleConfirm, user: User = Depends(get_current_user),
                  db: AsyncSession = Depends(get_db)):
    await verification.require_verified_email(db, user, feature='oracle')
    await usage.consume(db, user, "oracle_turn")
    if not settings.oracle_available or await get_graph() is None:
        raise HTTPException(status_code=503, detail="Oracle is not enabled")
    # A resume is a full agent turn (LLM + tools), so it draws on the same
    # free-tier quota as the send that paused it (register C78) — otherwise
    # every paused confirmation is an unmetered turn on top of the metered one.
    await usage.enforce_quota(db, user)
    thread_id = scoped_thread_id(user.id, payload.thread_id)
    await _record_decision(db, user, thread_id, payload.approved)
    gen = _run(Command(resume={"approved": payload.approved}), thread_id, user.id,
               persist_user=None, language_directive=language.for_user(user))
    return StreamingResponse(gen, media_type="text/event-stream")
