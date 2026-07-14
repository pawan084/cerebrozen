"""The pluggable backends: Postgres checkpointer and the Ollama provider.

Both exist so the product can be sold to a client who runs neither Mongo nor OpenAI.
Both have a failure mode that is SILENT — they fall back to something that still works,
so a broken port looks healthy. These tests make the failure loud.
"""
import os

import pytest

from app.llm.providers.ollama import control_schema


# ── Ollama: the routing contract ─────────────────────────────────────────────

def test_routing_fields_are_forced_by_the_grammar():
    """Any field the GRAPH ROUTES ON must be `required` in the schema.

    Measured against a live 8B: with coaching_path OPTIONAL the model simply omitted it
    (3/3 CH-shaped goals) → the router saw no path → silent CIM fallback → the CH path
    became unreachable. Made REQUIRED, the model committed and chose correctly 5/5.
    Grammar-forcing is what turns "might mention a path" into "must pick one".
    """
    challenge = control_schema("challenge_context_agent")
    assert "coaching_path" in challenge["required"], (
        "coaching_path must be REQUIRED for challenge_context — it is the only thing "
        "that agent exists to decide, and an omitted path routes every session to CIM."
    )
    # And it must be constrained to the paths the router actually knows.
    assert challenge["properties"]["coaching_path"]["enum"] == ["CIM", "CBT", "CH"]

    # Every stage must at minimum be forced to produce user text + a handoff decision.
    for stage in ("core_coaching_agent", "coaching_intake_agent", ""):
        req = control_schema(stage)["required"]
        assert "response_to_user" in req and "handoff_ready" in req


# ── Postgres: the checkpointer ───────────────────────────────────────────────

def test_postgres_checkpointer_is_pinned_to_a_compatible_version():
    """langgraph-checkpoint-postgres 3.x requires langgraph-checkpoint>=4, which is
    incompatible with the langgraph 0.2.x core this app is built on — installing it
    breaks the graph. This asserts the environment is actually consistent."""
    from importlib.metadata import version

    major = int(version("langgraph-checkpoint-postgres").split(".")[0])
    assert major == 2, (
        "langgraph-checkpoint-postgres must stay on the 2.0.x line; the 3.x line pulls "
        "langgraph-checkpoint>=4 and silently breaks the graph."
    )


@pytest.mark.skipif(not os.environ.get("POSTGRES_URL"), reason="no POSTGRES_URL")
def test_postgres_checkpointer_actually_connects():
    """Guards the `from_conn_string()` trap: it returns a context manager whose connection
    closes on collection, so the saver dies with 'the connection is closed' and the app
    silently falls back to SQLite. get_checkpointer() must return a LIVE Postgres saver."""
    from langgraph.checkpoint.postgres import PostgresSaver

    from app.graph.build_graph import get_checkpointer

    cp = get_checkpointer()
    assert isinstance(cp, PostgresSaver), (
        f"POSTGRES_URL is set but the checkpointer is {type(cp).__name__} — it fell back. "
        "The app would run, and silently lose every session on restart."
    )
    # A live connection: this raises if the pool is dead.
    cp.setup()


# ── Postgres store shim: the Mongo operators it emulates ─────────────────────
# Every bug found while porting was in this emulation, and each one degraded SILENTLY
# (a repeat user read back as "fresh"; a check-in never became due). Pin them.

def test_pg_shim_emulates_the_mongo_operators_the_stores_use():
    from app.stores.pg import _apply_update

    # $push with $each + $slice — how transcripts keep only the last N messages
    doc = _apply_update({}, {"$push": {"messages": {"$each": [1, 2, 3]}}}, inserted=True)
    assert doc["messages"] == [1, 2, 3]
    doc = _apply_update(doc, {"$push": {"messages": {"$each": [4, 5], "$slice": -3}}}, inserted=False)
    assert doc["messages"] == [3, 4, 5], "$slice -3 must keep the LAST three"

    # $addToSet — how checked-in sessions are marked complete (must be idempotent)
    doc = _apply_update({}, {"$addToSet": {"done": {"$each": ["a", "b"]}}}, inserted=True)
    doc = _apply_update(doc, {"$addToSet": {"done": {"$each": ["b", "c"]}}}, inserted=False)
    assert doc["done"] == ["a", "b", "c"], "$addToSet must not duplicate"

    # $setOnInsert fires ONLY on insert; $set always; $inc accumulates
    doc = _apply_update({}, {"$setOnInsert": {"created": "t0"}, "$set": {"x": 1}}, inserted=True)
    doc = _apply_update(doc, {"$setOnInsert": {"created": "t9"}, "$inc": {"n": 2}}, inserted=False)
    assert doc["created"] == "t0", "$setOnInsert must not overwrite on update"
    assert doc["n"] == 2

    # dotted paths — the stores write nested fields like intake_vars.userRoleContext
    doc = _apply_update({}, {"$set": {"a.b.c": 7}}, inserted=True)
    assert doc["a"]["b"]["c"] == 7


def test_pg_shim_filter_matching():
    from app.stores.pg import _matches

    doc = {"user_id": "u1", "session_id": "s1", "ended": True}
    assert _matches(doc, {"user_id": "u1"})
    assert not _matches(doc, {"user_id": "u2"})
    # $ne — how "prior sessions EXCLUDING this one" is expressed
    assert _matches(doc, {"user_id": "u1", "session_id": {"$ne": "s2"}})
    assert not _matches(doc, {"session_id": {"$ne": "s1"}})
    assert _matches(doc, {"ended": {"$exists": True}})
    # $and/$or — the shapes tenancy's scoped() emits for the default org
    assert _matches(
        doc,
        {"user_id": "u1", "$and": [{"$or": [{"org_id": "default"}, {"org_id": {"$exists": False}}]}]},
    )
    assert not _matches(
        {"user_id": "u1", "org_id": "acme"},
        {"user_id": "u1", "$and": [{"$or": [{"org_id": "default"}, {"org_id": {"$exists": False}}]}]},
    )
    assert _matches({"org_id": "acme"}, {"$or": [{"org_id": "acme"}, {"org_id": "globex"}]})


def test_pg_cursor_is_chainable():
    """find() must return a CURSOR, not a list — the stores chain .sort().limit().
    Returning a list raised TypeError, the store swallowed it, and repeat users
    silently read back as fresh."""
    from app.stores.pg import PgCursor

    cur = PgCursor([{"t": "a"}, {"t": "c"}, {"t": "b"}])
    assert [d["t"] for d in cur.sort("t", -1).limit(2)] == ["c", "b"]

    # pymongo list-form sort (the sessions list tiebreaks on _id) and .skip()
    cur = PgCursor([
        {"t": "x", "i": "1"}, {"t": "x", "i": "2"}, {"t": "a", "i": "9"},
    ])
    out = [d["i"] for d in cur.sort([("t", -1), ("i", -1)]).skip(1).limit(2)]
    assert out == ["1", "9"], "list-form sort + skip must behave like pymongo"


# ── pgvector RAG store ───────────────────────────────────────────────────────

def test_pgvector_follows_postgres_first_policy(monkeypatch):
    """Postgres-first: pgvector is the default whenever POSTGRES_URL is set;
    without Postgres it stays off; an explicit lancedb opts out either way."""
    from app.rag import pgvector_store as pg

    monkeypatch.delenv("CEREBROZEN_RAG_BACKEND", raising=False)
    monkeypatch.delenv("POSTGRES_URL", raising=False)
    assert pg.enabled() is False

    monkeypatch.setenv("POSTGRES_URL", "postgresql://x")
    assert pg.enabled() is True

    monkeypatch.setenv("CEREBROZEN_RAG_BACKEND", "lancedb")
    assert pg.enabled() is False

    monkeypatch.delenv("POSTGRES_URL", raising=False)
    monkeypatch.setenv("CEREBROZEN_RAG_BACKEND", "pgvector")
    assert pg.enabled() is True


def test_embeddings_are_not_portable_across_models():
    """1536-dim (OpenAI) vectors cannot be searched in a 768-dim (nomic) index, and vice
    versa. Postgres rejects it — but with `expected 8 dimensions, not 1536`, which tells
    you nothing. The guard must say what to DO: re-ingest.

    This is a real trap on the offline port: swapping the embedder silently invalidates
    the entire knowledge base.
    """
    import inspect

    from app.rag import pgvector_store as pg

    src = inspect.getsource(pg._ensure)
    assert "re-index" in src or "re-ingest" in src, (
        "the dimension-mismatch guard must tell the operator to re-ingest"
    )


# ── KV-cache prewarm (offline latency) ───────────────────────────────────────

def test_prewarm_only_fires_on_a_stage_change():
    """The local model caches ONE prompt prefix at a time (measured: calling a second
    agent evicts the first). So prewarming the next agent MID-STAGE would evict the
    prefix the user is about to reuse and make their next turn SLOWER, not faster.

    Prewarm is correct only when the stage actually changed — which the deterministic
    graph tells us for free.
    """
    import inspect

    from app.graph import engine

    src = inspect.getsource(engine.CereBroZenEngine.run_turn_stream)
    assert "entry_stage" in src and "dispatch_prewarm" in src
    assert '_next_stage != (entry_stage or "")' in src, (
        "prewarm must be guarded on a stage CHANGE — prewarming mid-stage evicts the "
        "prefix the user is about to reuse."
    )


def test_prewarm_is_a_noop_without_the_provider_hook():
    """OpenAI caches server-side; only the Ollama provider exposes .prewarm(). The
    dispatcher must not blow up on a provider that has no such method."""
    import inspect

    from app.graph.builders import _run_prewarm

    assert 'hasattr(provider, "prewarm")' in inspect.getsource(_run_prewarm)


# ── OpenAI prompt caching (cost) ─────────────────────────────────────────────

def test_cache_key_is_shared_across_users_not_per_user():
    """The cache key must group requests that SHARE a system prompt.

    Every user of the same agent gets the same system prompt, so they should share one
    cache entry. A per-user or per-session key would fragment the cache and throw the
    cross-user reuse away — the first turn of every new session would pay full price.

    Measured on the live stack: 0% cached (no key) -> 83% (stage:version key), 48% cheaper
    per turn. The workbook version in the key busts it automatically on a prompt edit, so a
    stale prefix can never be served after a reload.
    """
    from app.llm.responses_client import _cache_key

    k1 = _cache_key("core_coaching_agent")
    k2 = _cache_key("core_coaching_agent")
    assert k1 == k2, "the same agent must produce a stable key across users/sessions"
    assert _cache_key("challenge_context_agent") != k1, "different agents must not share a key"

    # the workbook version is in the key → a prompt edit invalidates it
    from app.llm.prompts import current_workbook_version

    v = current_workbook_version()
    if v:
        assert v in k1
