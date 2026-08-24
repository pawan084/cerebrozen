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
from langchain_core.runnables import RunnableConfig
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import START, StateGraph, MessagesState
from langgraph.prebuilt import ToolNode, tools_condition

from app.agent.tools import TOOLS
from app.core.config import settings
from app.services import prompts

logger = logging.getLogger("cerebro.oracle")

# Registered code default — an active `prompt_templates` row overrides it live.
SYSTEM_PROMPT = prompts.register(
    "oracle_system",
    "You are CereBro, a warm, calm wellness companion. Reflect the user's feelings. "
    "You are NOT a therapist and never diagnose or prescribe."
    + prompts.RESPONSE_STYLE + "\n\n"
    "ACT WITH TOOLS — don't just talk about them:\n"
    "- The moment the user expresses anxiety, stress, panic, overwhelm, racing thoughts, "
    "rumination, sadness, or trouble sleeping, CALL suggest_activity in the SAME turn with "
    "the best-fit kind (breathing | grounding | mood_check | mini_journal | sleep_checkin), "
    "then add one short caring sentence. Do NOT merely ask 'would you like to try…'.\n"
    "- When the user asks to log or record how they feel, CALL log_mood right away (it "
    "confirms with them). When they want to write, vent, or journal, CALL save_journal. "
    "When they describe how last night went, CALL log_sleep. "
    "Pass your best guess for the arguments — do not ask clarifying questions first.\n"
    "- When the user asks how they've been doing — or how their week, sleep, or mood "
    "has looked — or ANY question about their own history, data, patterns or trends — "
    "CALL get_weekly_insights. NEVER say you lack access to their sleep, "
    "mood, or journal data: your tools are that access. Unsure whether the data "
    "exists? CALL the tool and find out — never assert absence (a live turn was observed "
    "answering 'what did my sleep look like this week' with 'I don't have direct "
    "access to your sleep data', which is false and erodes exactly the trust the "
    "tool exists to build).\n"
    "  Example — user: 'how has my mood been lately?' -> you call "
    "get_weekly_insights first, then answer from what it returns. "
    "Example — user: 'am I sleeping better?' -> you call get_weekly_insights "
    "first. Answering these from memory or with a disclaimer instead of the "
    "tool is a wrong answer.\n"
    "If the user mentions self-harm or suicide, respond with warmth and take it "
    "seriously, but do NOT name hotline numbers or crisis services — the platform "
    "attaches the correct local resources itself (the model's own numbers default "
    "to one country and were shown to users in another)."
)

def wants_history_tool(text: str) -> bool:
    """A user turn asking about their OWN history — the shape that forces
    get_weekly_insights via tool_choice rather than trusting the model.

    Deliberately tight: possessive-plus-history phrases only, so "I feel low
    lately" still routes freely to suggest_activity per the prompt. Pure, and
    pinned by tests/test_history_intent.py — extending it is a golden-table
    edit, not a prompt tweak.
    """
    low = text.lower()
    return any(
        k in low
        for k in (
            "how has my", "how have i been", "how am i doing", "how've i been",
            "my mood been", "my sleep look", "my week been", "my week look",
            "am i sleeping", "sleeping better", "been sleeping",
            "my patterns", "my trends", "my progress", "my history",
        )
    )


_graph = None
_checkpointer = None
# Which checkpointer actually initialised: "postgres" | "memory" | "none" (graph
# never built). Until now the Postgres→MemorySaver fallback was visible ONLY in a
# boot log line, so a production worker silently running in-process — paused
# confirmations dying on restart and not crossing workers — looked identical to a
# healthy one. The admin Oracle tab reads this.
_checkpointer_kind = "none"


def checkpointer_kind() -> str:
    """What the compiled graph is checkpointing to, for operator visibility."""
    return _checkpointer_kind


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
    global _checkpointer_kind
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
        _checkpointer_kind = "postgres"
        return saver
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "Postgres checkpointer unavailable (%s) — using in-process MemorySaver; "
            "paused confirmations won't survive a restart or cross workers.", exc
        )
        _checkpointer_kind = "memory"
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

    async def agent(state: MessagesState, config: RunnableConfig | None = None):
        # Live registry lookup per turn (own short session; falls back to the
        # registered default) — prompt edits apply without rebuilding the graph.
        system = await prompts.get("oracle_system")
        # The graph is compiled once and shared, so the user's language cannot
        # live in the closure — it rides the per-turn config alongside
        # thread_id. Empty for English, which is what the prompt already is.
        directive = ((config or {}).get("configurable") or {}).get("language_directive", "")
        messages = [SystemMessage(content=system + directive), *state["messages"]]
        # Forced tool_choice for the one turn shape the model keeps fumbling.
        # The eval suite measured "how has my mood been lately?" answered from
        # vibes — or with a false "I don't have access" — on roughly half of
        # sampled turns, and a prompt exemplar moved that to green-in-isolation
        # but red-in-suite. Prompts ask; tool_choice makes it not a choice.
        # Only on a fresh HUMAN turn (a resumed/tool turn must stay free to
        # answer), and only for the history-question shape, matched by the
        # same kind of deterministic classifier the /chat router trusts.
        last = state["messages"][-1] if state["messages"] else None
        force = (
            getattr(last, "type", "") == "human"
            and wants_history_tool(str(getattr(last, "content", "")))
        )
        turn_llm = (
            model.bind_tools(TOOLS, tool_choice="get_weekly_insights") if force else llm
        )
        return {"messages": [await turn_llm.ainvoke(messages)]}

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
