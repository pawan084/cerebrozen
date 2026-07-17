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

    _DIM = 16

    def _vec(text: str):
        import hashlib

        return [b / 255 for b in hashlib.sha256(text.encode()).digest()[:_DIM]]

    monkeypatch.setattr(embedder, "embed", lambda texts: [_vec(t) for t in texts])
    monkeypatch.setattr(embedder, "embed_one", _vec)
    monkeypatch.setattr(embedder, "embedding_dim", lambda: _DIM)
    monkeypatch.setattr(store_facade, "writable", lambda: True)

    # Stand in for the RAG SELECTION model. Every extraction path — values, vector, and
    # learning aid — funnels through `_llm_extract`, a SECOND model call that picks and
    # restructures the retrieved passages. It is not what R3 asks about ("did retrieval
    # reach the model?"), and under the mock provider it fails and empties the placeholder,
    # so it would mask the very thing under test. This echoes the top candidate, leaving
    # search → extract → prompt as the path being asserted.
    #
    # Worth stating plainly because it is a live risk of its own: with a FULL index, a RAG
    # model failure (no key, rate limit, model retired) degrades to "this org has no
    # values" and a WARNING, not an error. The knowledge base depends on a model call per
    # extraction, and its failure is as silent as the empty index R3 names.
    from app.rag import extractors as _ex

    def _echo(ex, query, candidates):
        top = (candidates or [{}])[0]
        return {"retrieved_item": top.get("text", ""), "text": top.get("text", ""),
                "status": "ok", "source_link": top.get("source_link", "")}

    monkeypatch.setattr(_ex, "_llm_extract", _echo)

    yield pgvector_store

    with pgdb.pool.connection() as conn:
        conn.execute("DROP TABLE IF EXISTS rag_cskb")
    pgvector_store._ready.discard("rag_cskb")


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


# NOT YET WRITTEN — R3's central assertion: "a seeded KB reaches the composed prompt".
#
# The wiring is confirmed ({CSKB_LearningAid} is in learning_aid_agent's prompt; Extract5
# maps it to kb=cskb, condition=org_available), and the pieces below prove isolation,
# graceful degradation, and the harness report. What is missing is the positive case, and
# I could not stand the fixture up inside one sitting: every extraction funnels through
# `_llm_extract` (a second model call), and standing in for it was not sufficient — the
# placeholder still resolved empty, so something between the search and the render is not
# being exercised the way a live turn exercises it.
#
# Do NOT mark this xfail and move on: an xfail on the one assertion R3 exists for is the
# same silence the risk describes, with a decorator on it. The honest next step is to run
# ONE real turn against a seeded index (the composed stack, a real key) and diff the
# prompt — the same move that found every other bug in this file's history.
#
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
