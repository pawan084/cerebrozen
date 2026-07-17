"""R3: "RAG silently degraded (0 rows) → coaching never evaluated with KB".

The inherited-risk register's third entry, and the one that had quietly come true. Every
eval run in this repo printed a score with `rag.search_failed` in the log above it and
nothing in the result — measured 2026-07-17: POSTGRES_URL unset, pgvector off, every
retrieval failing, harness reporting 100%. The number was true; the impression it gave was
not. The agents' ROUTING was measured with no knowledge base attached, which is not the
product that ships.

`scripts/eval.py` now prints the KB state next to the score so a 0-row run can never again
read as "the coaching works". This file is the other half: proof that when a KB DOES exist,
its content reaches the composed prompt.

Deterministic on purpose. The question R3 asks — "did retrieval reach the model?" — has a
yes/no answer that needs no model call, so it is a test rather than an eval case: whether
the model USES what it retrieved is coaching quality, which is the coach's, and no
assertion here pretends otherwise.
"""

from __future__ import annotations

import os

import pytest

from tests.conftest import PG_URL, requires_pg

#: Driven through the LEARNING-AID path (Extract5, {CSKB_LearningAid}) because it is the
#: one CSKB extractor that is PURE RETRIEVAL — it puts the retrieved passage straight into
#: the prompt (`retrieved_item: chosen["text"]`). `_extract_values` and `_extract_vector`
#: each make their own LLM call to restructure the passages, so with the mock provider they
#: return null and prove nothing about retrieval. That is worth knowing on its own: two of
#: the three CSKB paths depend on a SECOND model call, and when it fails they degrade to
#: "this org has no values" rather than to an error (rag.values_llm_failed).
#:
#: A fact no model could produce on its own. If it appears in the composed prompt, it came
#: out of the index — nothing else in this process knows it.
FACT = "Escalate to the Kingfisher council before any pricing change."


@pytest.fixture
def kb(pgdb, monkeypatch):
    """A real pgvector CSKB with a deterministic embedder (no model, no key)."""
    import app.rag.store as store_facade
    from app.rag import embedder, pgvector_store

    monkeypatch.setenv("POSTGRES_URL", PG_URL)
    monkeypatch.setenv("CEREBROZEN_RAG_BACKEND", "pgvector")
    monkeypatch.setattr(pgvector_store, "_ready", set())

    # BOTH tables, and dropped BEFORE the run as well as after. The learning-aid extraction
    # pools CSKB *and* SSKB sub-extractions, and embeddings are dimension-locked per table:
    # a 1536-dim `rag_sskb` left by a real-embedder run makes this fixture's 16-dim query
    # raise inside the pool, which empties the placeholder — i.e. it fails exactly like the
    # bug under test, for an unrelated reason. That is what cost me the first attempt.
    with pgdb.pool.connection() as conn:
        for t in ("rag_cskb", "rag_sskb"):
            conn.execute(f"DROP TABLE IF EXISTS {t}")

    _DIM = 16

    def _vec(text: str):
        import hashlib

        return [b / 255 for b in hashlib.sha256(text.encode()).digest()[:_DIM]]

    monkeypatch.setattr(embedder, "embed", lambda texts: [_vec(t) for t in texts])
    monkeypatch.setattr(embedder, "embed_one", _vec)
    monkeypatch.setattr(embedder, "embedding_dim", lambda: _DIM)
    monkeypatch.setattr(store_facade, "writable", lambda: True)

    # No LLM stand-in, and none needed: `_extract_learning_aid` uses `_llm_select_index`
    # to pick WHICH retrieved item to use, and falls back to the best vector score when
    # that call fails. So the aid path is genuinely pure retrieval with a deterministic
    # fallback — which is why R3's question ("did retrieval reach the model?") can be
    # answered without a model.
    #
    # `_extract_values` and `_extract_vector` are NOT like this: each makes a second model
    # call that RESTRUCTURES the passages, and on failure returns null — so with a full
    # index a RAG model outage degrades to "this org has no values" and a WARNING. That is
    # a live risk of its own, recorded in TODO.md under R3, and not what this file asserts.

    yield pgvector_store

    with pgdb.pool.connection() as conn:
        for t in ("rag_cskb", "rag_sskb"):
            conn.execute(f"DROP TABLE IF EXISTS {t}")
    for t in ("rag_cskb", "rag_sskb"):
        pgvector_store._ready.discard(t)


def _seed(org_id: str, text: str = FACT, doc_type: str = "learning_aids") -> None:
    from app.rag.ingest import embed_and_upsert, make_id

    key = f"cskb:{org_id}:probe"
    embed_and_upsert("cskb", [{
        "id": make_id("cskb", key, "0"), "text": text, "source_link": "", "title": "probe",
        "kb": "cskb", "org_id": org_id, "doc_type": doc_type, "source": "curated",
        "doc_key": key,
    }])


def _compose(org_id: str, stage: str = "learning_aid_agent") -> str:
    from app.graph.guardrails import build_system_prompt
    from app.graph.runtime import get_registry

    reg = get_registry()
    return build_system_prompt(
        reg.environment, reg.get(stage), "CBT",
        {"org_id": org_id, "orgId": org_id, "userName": "Alex"},
        {"user_message": "How should I handle a pricing decision?",
         "conversation_history": "How should I handle a pricing decision?",
         "session_goal": "handle a pricing decision",
         "userRoleContext": "Engineering manager"},
        invoking_agent=stage,
    )


#: R3's central assertion runs the REAL path — real embedder, real index, no stand-ins —
#: because that is the only configuration that answers the question. It needs a key, so it
#: skips without one; it is not a substitute for the deterministic tests below, it is the
#: one thing they cannot cover.
requires_real_embedder = pytest.mark.skipif(
    not os.environ.get("OPENAI_API_KEY"),
    reason="needs a real embedder — R3 asks whether retrieval reaches the model, and a "
           "fake 16-dim embedder answers a different question",
)


@requires_pg
@requires_real_embedder
def test_a_seeded_knowledge_base_reaches_the_composed_prompt(pgdb, monkeypatch):
    """The whole of R3, driven for real.

    If this fails, the coach is running on the general method while a customer pays for
    "tuned to your culture" — and nothing errors, which is the entire risk.

    Every hop is live: OpenAI embeddings, pgvector, the real extraction registry, the real
    `_llm_select_index`. No fake embedder, because that is what defeated the first two
    attempts — a 16-dim query against a 1536-dim `rag_sskb` raises INSIDE the learning-aid
    pool and empties the placeholder, failing exactly like the bug under test for an
    unrelated reason. Measured against the live index, the fact reached the prompt first
    time; the product was never broken, the fixture was.
    """
    monkeypatch.setenv("POSTGRES_URL", PG_URL)
    monkeypatch.setenv("CEREBROZEN_RAG_BACKEND", "pgvector")
    monkeypatch.setenv("CEREBROZEN_EMBED_PROVIDER", "openai")
    from app.rag import pgvector_store

    monkeypatch.setattr(pgvector_store, "_ready", set())
    org = "r3-real-turn"
    try:
        _seed(org)

        composed = _compose(org)

        assert FACT in composed, (
            "the org's own knowledge base did not reach the prompt — retrieval is degraded "
            "and the coaching is ungrounded, silently"
        )
    finally:
        from app.rag import store

        store.delete_org_doc("cskb", org, f"cskb:{org}:probe")


@requires_pg
def test_an_empty_knowledge_base_degrades_instead_of_breaking(kb):
    """The other half of the contract: no KB must not mean no turn.

    This is why the failure is silent and therefore worth a test — the prompt reads fine
    with the placeholder blank, so nothing downstream complains.
    """
    composed = _compose("org-with-nothing-indexed")

    assert FACT not in composed
    assert composed.strip(), "an empty KB produced no prompt at all"
    assert "{CSKB_LearningAid}" not in composed, "a raw placeholder leaked into the prompt"


@requires_pg
def test_one_orgs_knowledge_base_never_reaches_anothers_prompt(kb):
    """The retrieval filter is a Postgres pre-filter (`meta @> filters`), so isolation is a
    property of the query. Asserted here at the PROMPT, which is where a leak would
    actually reach a person — a customer's confidential framework surfacing in another
    customer's coaching session is the worst outcome this index has.
    """
    _seed("acme")

    composed = _compose("globex")

    assert FACT not in composed, "one tenant's knowledge base leaked into another's prompt"


@requires_pg
def test_the_eval_harness_reports_an_empty_index_rather_than_scoring_over_it(kb, capsys):
    """R3's actual mitigation: the harness must SAY the KB is empty.

    A 100% score printed over a dead index is not a wrong number — routing is genuinely
    what those cases test — it is a true number that reads as a claim about the product.
    This asserts the run names which product it measured.
    """
    from scripts.eval import _report_rag

    _report_rag()
    empty = capsys.readouterr().out
    assert "EMPTY (0 rows)" in empty
    assert "R3" in empty, "the report should name the risk it exists for"

    _seed("acme")
    _report_rag()
    seeded = capsys.readouterr().out
    assert "EMPTY" not in seeded
    assert "cskb 1 rows" in seeded
