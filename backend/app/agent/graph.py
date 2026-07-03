"""The Oracle graph — a tool-calling companion built on LangGraph.

agent → (tools_condition) → tools → agent, looping until the model answers with
no tool calls. Write tools pause via ``interrupt()`` for confirmation. State is
checkpointed so a conversation — and a paused confirmation — can be resumed by
``thread_id``.

Checkpointing is durable: ``AsyncPostgresSaver`` on the app database, so a
paused confirmation can be resumed by ANY gunicorn worker (prod runs several).
If Postgres checkpointing can't initialize, we fall back to the in-process
``MemorySaver`` (single-worker dev) with a loud warning.
"""
from __future__ import annotations

import asyncio
import logging

from langchain_core.messages import SystemMessage
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import START, StateGraph, MessagesState
from langgraph.prebuilt import ToolNode, tools_condition

from app.agent.tools import TOOLS
from app.core.config import settings

logger = logging.getLogger("cerebro.oracle")

SYSTEM_PROMPT = (
    "You are CereBro, a warm, calm wellness companion. Reflect the user's feelings and "
    "keep replies to 1–2 short sentences. You are NOT a therapist and never diagnose or "
    "prescribe.\n\n"
    "ACT WITH TOOLS — don't just talk about them:\n"
    "- The moment the user expresses anxiety, stress, panic, overwhelm, racing thoughts, "
    "rumination, sadness, or trouble sleeping, CALL suggest_activity in the SAME turn with "
    "the best-fit kind (breathing | grounding | mood_check | mini_journal | sleep_checkin), "
    "then add one short caring sentence. Do NOT merely ask 'would you like to try…'.\n"
    "- When the user asks to log or record how they feel, CALL log_mood right away (it "
    "confirms with them). When they want to write, vent, or journal, CALL save_journal. "
    "When they describe how last night went, CALL log_sleep. "
    "Pass your best guess for the arguments — do not ask clarifying questions first.\n"
    "- When the user asks how they've been doing, CALL get_weekly_insights.\n"
    "If the user is in crisis, gently surface emergency resources."
)

_graph = None
_checkpointer = None


def _chat_model():
    if settings.ai_provider == "openai":
        from langchain_openai import ChatOpenAI
        return ChatOpenAI(model=settings.openai_model, api_key=settings.openai_api_key,
                          temperature=0.4, streaming=True)
    if settings.ai_provider == "anthropic":
        from langchain_anthropic import ChatAnthropic
        return ChatAnthropic(model=settings.ai_model, api_key=settings.anthropic_api_key, temperature=0.4)
    return None


async def _make_checkpointer():
    """Durable Postgres checkpointer (multi-worker safe), MemorySaver fallback."""
    try:
        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        from psycopg.rows import dict_row
        from psycopg_pool import AsyncConnectionPool

        # psycopg wants a plain postgresql:// DSN, not SQLAlchemy's +asyncpg URL.
        conninfo = settings.database_url.replace("+asyncpg", "")
        pool = AsyncConnectionPool(
            conninfo,
            min_size=1,
            max_size=4,
            open=False,
            kwargs={"autocommit": True, "row_factory": dict_row},
        )
        await pool.open()
        saver = AsyncPostgresSaver(pool)
        # Idempotent table/index creation. Bounded because setup() uses
        # CREATE INDEX CONCURRENTLY, which waits on ALL open transactions —
        # an idle-in-transaction app connection would otherwise hang this
        # (and every /oracle request behind it) forever. The lifespan warms
        # this pre-traffic; the timeout is the belt-and-braces fallback.
        await asyncio.wait_for(saver.setup(), timeout=30)
        logger.info("Oracle checkpointer: Postgres (durable, multi-worker safe)")
        return saver
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "Postgres checkpointer unavailable (%s) — using in-process MemorySaver; "
            "paused confirmations won't survive a restart or cross workers.", exc
        )
        return MemorySaver()


async def get_graph():
    """Build (once) and return the compiled Oracle graph, or None if no LLM."""
    global _graph, _checkpointer
    if _graph is not None:
        return _graph
    model = _chat_model()
    if model is None:
        return None
    llm = model.bind_tools(TOOLS)

    async def agent(state: MessagesState):
        messages = [SystemMessage(content=SYSTEM_PROMPT), *state["messages"]]
        return {"messages": [await llm.ainvoke(messages)]}

    builder = StateGraph(MessagesState)
    builder.add_node("agent", agent)
    builder.add_node("tools", ToolNode(TOOLS))
    builder.add_edge(START, "agent")
    builder.add_conditional_edges("agent", tools_condition)
    builder.add_edge("tools", "agent")

    if _checkpointer is None:
        _checkpointer = await _make_checkpointer()
    _graph = builder.compile(checkpointer=_checkpointer)
    return _graph
