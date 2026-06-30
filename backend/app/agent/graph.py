"""The Oracle graph — a tool-calling companion built on LangGraph.

agent → (tools_condition) → tools → agent, looping until the model answers with
no tool calls. Write tools pause via ``interrupt()`` for confirmation. State is
checkpointed (MemorySaver) so a conversation — and a paused confirmation — can be
resumed by ``thread_id``.

Note: MemorySaver is in-process. For multi-worker production, swap in
``langgraph.checkpoint.postgres.aio.AsyncPostgresSaver`` (same interface).
"""
from __future__ import annotations

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
    "the best-fit kind (breathing | grounding | mood_check | mini_journal), then add one "
    "short caring sentence. Do NOT merely ask 'would you like to try…'.\n"
    "- When the user asks to log or record how they feel, CALL log_mood right away (it "
    "confirms with them). When they want to write, vent, or journal, CALL save_journal. "
    "Pass your best guess for the arguments — do not ask clarifying questions first.\n"
    "- When the user asks how they've been doing, CALL get_weekly_insights.\n"
    "If the user is in crisis, gently surface emergency resources."
)

_graph = None


def _chat_model():
    if settings.ai_provider == "openai":
        from langchain_openai import ChatOpenAI
        return ChatOpenAI(model=settings.openai_model, api_key=settings.openai_api_key,
                          temperature=0.4, streaming=True)
    if settings.ai_provider == "anthropic":
        from langchain_anthropic import ChatAnthropic
        return ChatAnthropic(model=settings.ai_model, api_key=settings.anthropic_api_key, temperature=0.4)
    return None


def get_graph():
    """Build (once) and return the compiled Oracle graph, or None if no LLM."""
    global _graph
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

    _graph = builder.compile(checkpointer=MemorySaver())
    return _graph
