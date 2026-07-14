"""Compile the CIM-skeleton StateGraph + its checkpointer.

Deterministic edges only — every branch is a code predicate over typed state;
there is no routing-only LLM node. The checkpointer persists state per
`session_id` (Mongo when available, in-memory otherwise so the dev box runs
without Mongo).
"""

from __future__ import annotations

import logging
from typing import Dict, Optional

from langgraph.graph import END, START, StateGraph

from app import config
from app.graph.runtime import get_registry
from app.graph.nodes import (
    action_checkin_node,
    capability_coaching_node,
    challenge_node,
    checkin_node,
    core_coaching_node,
    dynamic_actions_insights_node,
    feedback_node,
    final_action_check_node,
    intake_node,
    learning_aid_node,
    pattern_node,
    profile_read_node,
    role_play_node,
    safe_response_node,
    safety_node,
    session_complete_node,
    simulation_decision_node,
    sjt_simulation_node,
)
from app.graph.state import (
    PATH_TO_NODE,
    STAGE_ACTION_CHECKIN,
    STAGE_CH,
    STAGE_CHALLENGE,
    STAGE_CHECKIN,
    STAGE_CLOSE,
    STAGE_CORE,
    STAGE_DYNAMIC_ACTIONS,
    STAGE_FEEDBACK,
    STAGE_FINAL_ACTION_CHECK,
    STAGE_INTAKE,
    STAGE_LEARNING_AID,
    STAGE_PATTERN,
    STAGE_ROLEPLAY,
    STAGE_SIMULATION_DECISION,
    STAGE_SJT,
    CereBroZenState,
)

logger = logging.getLogger("cerebrozen.graph")


def _after_safety(state: CereBroZenState) -> str:
    return "crisis" if state.get("safety_flag") == "crisis" else "ok"


def _coaching_route(state: CereBroZenState) -> str:
    """Pick the coaching node from the path challenge_context scored — a pure state
    predicate, no LLM hop (constitution: deterministic routing). CIM→core, CBT→core
    (unified), CH→capability. The agent's decision is authoritative; CIM (core) is only a
    LOGGED last-resort when the agent emitted no usable path, never a silent default.

    Gated by config.ENABLE_MULTIPATH: while it's off, every turn routes to CIM
    regardless of coaching_path (CIM-only validation mode)."""
    if not config.ENABLE_MULTIPATH:
        return "core"
    path = (state.get("coaching_path") or "").strip().upper()
    node = PATH_TO_NODE.get(path)
    if node is None:
        # No usable decision from challenge_context — fall back to CIM, but make it
        # visible so a missing/garbled path isn't silently masked as a real choice.
        logger.warning(
            "route.coaching_path_unset_fallback_cim",
            extra={"coaching_path": state.get("coaching_path"), "fallback": "core"},
        )
        return "core"
    return node


# stage → graph node key. ONE table, read by both the turn-entry dispatch edge
# and every stage's in-turn chain edge, so the two can't drift apart (they used
# to be hand-maintained twins — and STAGE_CH was missing from the chain half).
# Stages whose node depends on runtime state resolve through _node_for_stage below.
_COACHING_SLOT = "__coaching_slot__"  # → core | capability, per coaching_path
STAGE_NODE: Dict[str, str] = {
    STAGE_ACTION_CHECKIN: "action_checkin",  # standalone sticky mini-session, seeded at entry
    STAGE_CHECKIN: "checkin",                # gated: Catalog
    STAGE_INTAKE: "intake",
    STAGE_CHALLENGE: "challenge",
    STAGE_CORE: _COACHING_SLOT,
    STAGE_CH: _COACHING_SLOT,
    STAGE_DYNAMIC_ACTIONS: "dynamic_actions",
    STAGE_SIMULATION_DECISION: "simulation_decision",  # sticky 2-turn: offer → route
    STAGE_ROLEPLAY: "role_play",
    STAGE_SJT: "sjt",
    STAGE_PATTERN: "pattern",                # post-simulation reflect beat
    STAGE_LEARNING_AID: "learning_aid",      # gated: Catalog
    STAGE_FINAL_ACTION_CHECK: "final_action_check",
    STAGE_FEEDBACK: "feedback",              # gated: final action check must pass first
    STAGE_CLOSE: "session_complete",         # terminal
}


def _node_for_stage(state: CereBroZenState, stage: str) -> Optional[str]:
    """The node that serves `stage` right now, or None when the stage is unknown.

    Three stages resolve dynamically:
      - the coaching slot → core / capability, from `coaching_path`;
      - Catalog-gated nodes (checkin, learning_aid) → the next stage forward when
        the agent is switched off, so a stale/gated checkpoint advances rather
        than re-running a completed stage or dead-ending;
      - feedback → the mandatory Final action check first, so no session closes
        with zero saved actions.
    """
    node = STAGE_NODE.get(stage)
    if node is None:
        return None
    if node == _COACHING_SLOT:
        return _coaching_route(state)
    if stage == STAGE_CHECKIN and not get_registry().is_enabled(STAGE_CHECKIN):
        return "challenge"  # check-in sits after intake → advance, don't re-run intake
    if stage == STAGE_LEARNING_AID and not get_registry().is_enabled(STAGE_LEARNING_AID):
        # Skip to the closing layer — but resolve it through the SAME rules, so a
        # disabled learning aid can't smuggle a session past the mandatory Final
        # action check (routing straight to the `feedback` node used to do exactly
        # that, letting a session close with zero saved actions).
        return _node_for_stage(state, STAGE_FEEDBACK)
    if stage == STAGE_FEEDBACK and not (
        state.get("final_action_check_done") or state.get("ch_early_exit")
    ):
        return "final_action_check"
    return node


def _dispatch_stage(state: CereBroZenState) -> str:
    """Turn-entry routing: resume the checkpointed stage."""
    stage = state.get("stage") or STAGE_INTAKE
    node = _node_for_stage(state, stage)
    if node is None:
        # Unknown/garbled stage — recover into the coaching slot rather than
        # dead-ending the turn, but make the recovery visible.
        logger.warning("route.unknown_stage_fallback_coaching", extra={"stage": stage})
        return _coaching_route(state)
    return node


def _after_stage(state: CereBroZenState) -> str:
    """Chain to the next stage within the SAME turn when a stage handed off with
    NO user-facing text (a pure control envelope) — so the user gets a real reply
    from the next stage instead of a dead turn. Otherwise end the turn."""
    if not state.get("handoff_ready"):
        return "end"
    if (state.get("reply_text") or "").strip():
        return "end"  # the user already has a reply this turn
    node = _node_for_stage(state, state.get("stage"))
    return node or "end"  # unknown → stop (never invent a destination mid-turn)


def get_checkpointer():
    """Resolve the durable store for graph state: Postgres → Mongo → SQLite → memory.

    Each fall-back is a REAL loss of durability (a MemorySaver drops every session
    on restart), so a configured-but-unreachable store logs at error level rather
    than degrading quietly — the silent version of this hid a misconfigured Mongo
    behind a working-looking app whose sessions vanished on redeploy."""
    import os

    # Postgres first when configured — the backend for clients who don't run Mongo.
    #
    # PIN: langgraph-checkpoint-postgres==2.0.x. The 3.x line requires
    # langgraph-checkpoint>=4, which is incompatible with the langgraph 0.2.x core this
    # app is built on — installing it silently breaks the graph (3 tests fail). Upgrading
    # to the 3.x line means upgrading LangGraph itself, which is a separate project.
    pg_url = os.environ.get("POSTGRES_URL", "") or getattr(config, "POSTGRES_URL", "")
    if pg_url:
        try:
            from langgraph.checkpoint.postgres import PostgresSaver
            from psycopg_pool import ConnectionPool

            # A POOL, not from_conn_string(). `from_conn_string()` is a context manager:
            # the connection it yields is closed as soon as the generator is collected,
            # so the saver dies on its first real query with "the connection is closed"
            # (measured — it fell straight through to the SQLite fallback).
            # autocommit=True is required: the saver issues DDL/UPSERTs outside an
            # explicit transaction.
            pool = ConnectionPool(
                conninfo=pg_url,
                max_size=int(os.environ.get("CEREBROZEN_PG_POOL_MAX", "10")),
                kwargs={"autocommit": True, "prepare_threshold": 0},
                open=True,
            )
            saver = PostgresSaver(pool)
            saver.setup()          # idempotent: creates the checkpoint tables if absent
            logger.info("checkpointer.postgres", extra={"pool_max": pool.max_size})
            return saver
        except Exception as exc:  # noqa: BLE001
            logger.error(
                "checkpointer.postgres_unavailable_falling_back",
                extra={"error": str(exc), "fallback": "mongo/sqlite"},
            )

    mongo_url = getattr(config, "MONGO_DB_URL", "") or ""
    if mongo_url:
        try:
            from langgraph.checkpoint.mongodb import MongoDBSaver
            from pymongo import MongoClient
            client = MongoClient(mongo_url, serverSelectionTimeoutMS=getattr(config, "MONGO_TIMEOUT_MS", 3000))
            client.admin.command("ping")
            logger.info("checkpointer.mongo", extra={"db": config.MONGO_CHECKPOINT_DB})
            return MongoDBSaver(client, db_name=config.MONGO_CHECKPOINT_DB)
        except Exception as exc:  # noqa: BLE001
            # MONGO_DB_URL was set, so Mongo was EXPECTED to serve checkpoints.
            logger.error(
                "checkpointer.mongo_unavailable_falling_back",
                extra={"error": str(exc), "fallback": "sqlite"},
            )
    # SQLite (durable, works with async graph via to_thread)
    try:
        import asyncio, sqlite3
        from langgraph.checkpoint.sqlite import SqliteSaver
        class AsyncWrappedSqliteSaver(SqliteSaver):
            async def aget_tuple(self, cfg): return await asyncio.to_thread(self.get_tuple, cfg)
            async def aput(self, cfg, cp, md, nv): return await asyncio.to_thread(self.put, cfg, cp, md, nv)
            async def aput_writes(self, cfg, w, tid, tp=""): return await asyncio.to_thread(self.put_writes, cfg, w, tid, tp)
            async def alist(self, cfg, **kw):
                for it in await asyncio.to_thread(lambda: list(self.list(cfg, **kw))): yield it
        path = os.environ.get("SQLITE_CHECKPOINT_PATH", "./cerebrozen.db")
        logger.info("checkpointer.sqlite", extra={"path": path})
        return AsyncWrappedSqliteSaver(sqlite3.connect(path, check_same_thread=False))
    except Exception as exc:  # noqa: BLE001
        from langgraph.checkpoint.memory import MemorySaver
        # Last resort: sessions do NOT survive a restart. Never acceptable in
        # prod, so say so loudly; tests/dev boxes reach here intentionally.
        logger.error(
            "checkpointer.memory_no_durability",
            extra={"error": str(exc), "impact": "sessions are lost on restart"},
        )
        return MemorySaver()


def build_graph(checkpointer=None):
    g = StateGraph(CereBroZenState)

    g.add_node("safety", safety_node)
    g.add_node("safe_response", safe_response_node)
    g.add_node("profile_read", profile_read_node)
    g.add_node("action_checkin", action_checkin_node)  # standalone per-action check-in
    g.add_node("checkin", checkin_node)  # Phase 4 pre-session (gated)
    g.add_node("intake", intake_node)
    g.add_node("challenge", challenge_node)
    g.add_node("core", core_coaching_node)  # CIM + CBT (unified — CBT is the core method)
    g.add_node("capability", capability_coaching_node)
    g.add_node("dynamic_actions", dynamic_actions_insights_node)  # Phase 5 actions/insights gate
    g.add_node("simulation_decision", simulation_decision_node)  # post-coaching simulation gate (gated)
    g.add_node("role_play", role_play_node)  # simulation (CIM/CBT, gated)
    g.add_node("sjt", sjt_simulation_node)
    g.add_node("pattern", pattern_node)  # post-simulation reflect beat (pattern mirror)
    g.add_node("learning_aid", learning_aid_node)  # support node (CIM/CH, gated)
    g.add_node("final_action_check", final_action_check_node)  # mandatory pre-feedback gate
    g.add_node("feedback", feedback_node)  # closing layer (sole path to terminal close)
    g.add_node("session_complete", session_complete_node)  # terminal close

    g.add_edge(START, "safety")
    g.add_conditional_edges(
        "safety", _after_safety, {"crisis": "safe_response", "ok": "profile_read"}
    )
    g.add_edge("safe_response", END)

    # Every routable destination, derived from STAGE_NODE + the coaching paths so a
    # new stage can't be added to the table and silently lack an edge. Shared by the
    # dispatch edge and every stage's chain edge, so a control-only handoff lands on
    # the right node instead of dead-ending.
    routes = {n: n for n in STAGE_NODE.values() if n != _COACHING_SLOT}
    routes.update({n: n for n in PATH_TO_NODE.values()})  # core, capability
    g.add_conditional_edges("profile_read", _dispatch_stage, routes)

    # Chain edge on every stage node (i.e. all routable nodes except the terminal).
    stage_routes = {**routes, "end": END}
    for node in routes:
        if node != "session_complete":  # terminal: goes straight to END, never chains
            g.add_conditional_edges(node, _after_stage, stage_routes)
    g.add_edge("session_complete", END)  # terminal — a finished session stops here

    return g.compile(checkpointer=checkpointer or get_checkpointer())
