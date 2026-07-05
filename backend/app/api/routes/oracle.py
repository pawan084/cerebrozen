"""Oracle — streaming agentic chat (LangGraph).

SSE frames (each `data:` line is a JSON object with a `type`):
  token          {"type":"token","text":"…"}          incremental assistant text
  crisis         {"type":"crisis","resources":{…}}     region-aware crisis hotlines
  widget         {"type":"widget","widget":{…}}        inline activity to render
  tool_confirm   {"type":"tool_confirm","tool":…,"summary":…,"thread_id":…}
  awaiting_confirm                                       stream paused for approval
  done           {"type":"done","text":"…"}            final assistant text
  error          {"type":"error","detail":"…"}

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
from sqlalchemy.ext.asyncio import AsyncSession

from app.agent.context import current_db, current_user_id, emitted_widgets
from app.agent.graph import get_graph
from app.core.config import settings
from app.core.database import SessionLocal, get_db
from app.core.deps import get_current_user
from app.core.ratelimit import limiter
from app.models.chat import ChatMessage
from app.models.user import User
from app.services import crisis, safety, usage

logger = logging.getLogger("cerebro.oracle")

router = APIRouter(prefix="/oracle", tags=["oracle"])


class OracleSend(BaseModel):
    text: str = Field(min_length=1)
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
               region: str = ""):
    """Stream the graph run as SSE, managing request-scoped context + persistence."""
    graph = await get_graph()
    config = {"configurable": {"thread_id": thread_id}}

    async with SessionLocal() as db:
        t_db = current_db.set(db)
        t_uid = current_user_id.set(user_id)
        t_w = emitted_widgets.set([])
        try:
            if persist_user is not None:
                risk = await safety.scan_and_record(
                    db, user_id=user_id, source="chat", source_id=None, text=persist_user)
                db.add(ChatMessage(user_id=user_id, role="user", text=persist_user, risk_level=risk))
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
                elif mode == "updates" and "__interrupt__" in chunk:
                    intr = chunk["__interrupt__"][0]
                    payload = dict(intr.value) if isinstance(intr.value, dict) else {"summary": str(intr.value)}
                    payload.update({"type": "tool_confirm", "thread_id": thread_id})
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


@router.post("/messages")
@limiter.limit("30/minute")
async def messages(
    request: Request,
    payload: OracleSend,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if not settings.oracle_available or await get_graph() is None:
        raise HTTPException(status_code=503, detail="Oracle is not enabled")
    await usage.enforce_quota(db, user)   # free-tier daily cap (429 when exceeded)
    thread_id = payload.thread_id or str(user.id)
    gen = _run({"messages": [HumanMessage(content=payload.text)]}, thread_id, user.id,
               persist_user=payload.text, region=user.region)
    return StreamingResponse(gen, media_type="text/event-stream")


@router.post("/confirm")
@limiter.limit("30/minute")
async def confirm(request: Request, payload: OracleConfirm, user: User = Depends(get_current_user)):
    if not settings.oracle_available or await get_graph() is None:
        raise HTTPException(status_code=503, detail="Oracle is not enabled")
    gen = _run(Command(resume={"approved": payload.approved}), payload.thread_id, user.id, persist_user=None)
    return StreamingResponse(gen, media_type="text/event-stream")
