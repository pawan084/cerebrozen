"""Per-org knowledge base management — the "Tuned to Your Culture" mechanism.

Two properties, and the second is the one that matters:

1. An operator can SEE whether a tenant's coach has an evidence base, and fix it. Without
   this the failure mode is silent: no `values` document → no `{CSKB_Values}` → the
   prompt's field-presence gate takes the absent branch → the coaching runs ungrounded and
   nothing errors.
2. One tenant's knowledge base is unreachable from another's. Every test here that could
   be satisfied by "it works for one org" is written with TWO orgs, because that is the
   only way the isolation is actually exercised.

Driven against real pgvector where isolation is concerned: `meta @> filters` is a Postgres
pre-filter, and mongomock has no opinion about it. That gap has already produced four bugs
in this repo (see tests/conftest.py).
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from tests.conftest import PG_URL, requires_pg


@pytest.fixture
def client():
    from app.main import create_app

    return TestClient(create_app(), raise_server_exceptions=False)


@pytest.fixture
def kb(pgdb, monkeypatch):
    """A real pgvector-backed CSKB with a deterministic embedder.

    The fake embedder is hash-based, so it needs no model and no key — this suite is about
    isolation and bookkeeping, not relevance.
    """
    import app.rag.store as store_facade
    from app.rag import embedder, pgvector_store

    monkeypatch.setenv("POSTGRES_URL", PG_URL)
    monkeypatch.setenv("CEREBROZEN_RAG_BACKEND", "pgvector")
    monkeypatch.setattr(pgvector_store, "_pool_obj", None, raising=False)
    monkeypatch.setattr(pgvector_store, "_ready", set())

    _DIM = 16

    def _vec(text: str):
        import hashlib

        h = hashlib.sha256(text.encode()).digest()
        return [b / 255 for b in h[:_DIM]]

    monkeypatch.setattr(embedder, "embed", lambda texts: [_vec(t) for t in texts])
    monkeypatch.setattr(embedder, "embed_one", _vec)
    monkeypatch.setattr(embedder, "embedding_dim", lambda: _DIM)
    monkeypatch.setattr(store_facade, "writable", lambda: True)

    yield pgvector_store

    with pgdb.pool.connection() as conn:
        conn.execute("DROP TABLE IF EXISTS rag_cskb")
    pgvector_store._ready.discard("rag_cskb")


def _upload(client, org, title, doc_type="values", text="We value candour over comfort."):
    return client.post(f"/v1/cskb/{org}", json={"title": title, "doc_type": doc_type,
                                                "text": text})


# ── the operator can see, and fix, an ungrounded tenant ───────────────────────


@requires_pg
def test_an_empty_tenant_names_what_is_missing(client, kb):
    """"No documents" is not the useful answer — "no VALUES document, so {CSKB_Values}
    never resolves" is. The gap is what the operator acts on."""
    body = client.get("/v1/cskb/acme").json()

    assert body["docs"] == []
    assert set(body["missing"]) == {"frameworks", "values", "competencies", "learning_aids"}
    assert "general" not in body["missing"], "nothing retrieves 'general' — it is not a gap"


@requires_pg
def test_uploading_makes_a_type_retrievable(client, kb):
    r = _upload(client, "acme", "Acme Values", "values")
    assert r.status_code == 200, r.text
    assert r.json()["chunks"] >= 1

    body = client.get("/v1/cskb/acme").json()
    assert body["retrievable"] == ["values"]
    assert "values" not in body["missing"]
    assert body["docs"][0]["chunks"] >= 1
    assert body["docs"][0]["doc_type"] == "values"


@requires_pg
def test_re_uploading_a_title_replaces_it_rather_than_duplicating(client, kb):
    """Fixing a bad document must not mean delete-then-add, which leaves the coach
    ungrounded in between — and must not silently double the index either."""
    _upload(client, "acme", "Acme Values", "values", text="First draft.")
    _upload(client, "acme", "Acme Values", "values", text="Second draft, corrected.")

    docs = client.get("/v1/cskb/acme").json()["docs"]
    assert len(docs) == 1, f"the same title indexed twice: {docs}"


@requires_pg
def test_a_doc_type_nothing_retrieves_is_refused(client, kb):
    """An upload typed `culture` is indexed and never read — it looks exactly like a
    working upload. The extractors filter on an exact doc_type."""
    r = _upload(client, "acme", "Culture Deck", "culture")
    assert r.status_code == 400
    assert "doc_type" in r.text


@requires_pg
def test_a_document_can_be_removed(client, kb):
    _upload(client, "acme", "Acme Values", "values")
    key = client.get("/v1/cskb/acme").json()["docs"][0]["doc_key"]

    r = client.request("DELETE", f"/v1/cskb/acme/docs", params={"doc_key": key})
    assert r.status_code == 200
    assert r.json()["chunks_removed"] >= 1
    assert client.get("/v1/cskb/acme").json()["docs"] == []


# ── isolation: two orgs, always ──────────────────────────────────────────────


@requires_pg
def test_one_tenants_knowledge_base_is_invisible_to_another(client, kb):
    _upload(client, "acme", "Acme Values", "values", text="Acme candour.")
    _upload(client, "globex", "Globex Values", "values", text="Globex consensus.")

    acme = client.get("/v1/cskb/acme").json()["docs"]
    globex = client.get("/v1/cskb/globex").json()["docs"]

    assert [d["doc_key"] for d in acme] == ["cskb:acme:Acme Values"]
    assert [d["doc_key"] for d in globex] == ["cskb:globex:Globex Values"]
    assert "Globex" not in str(acme), f"another tenant's document is in this list: {acme}"


@requires_pg
def test_deleting_cannot_cross_a_tenant(client, kb):
    """The whole reason `delete_org_doc` exists rather than `delete_by_doc_key`: the org
    filter is in the DELETE's own WHERE, so a key from another tenant matches nothing —
    there is no check-then-delete window and no guard for a refactor to drop."""
    _upload(client, "globex", "Globex Values", "values")
    victim = client.get("/v1/cskb/globex").json()["docs"][0]["doc_key"]

    r = client.request("DELETE", "/v1/cskb/acme/docs", params={"doc_key": victim})

    assert r.status_code == 404, "one tenant deleted another tenant's knowledge base"
    assert len(client.get("/v1/cskb/globex").json()["docs"]) == 1, "it was deleted anyway"


@requires_pg
def test_two_tenants_can_hold_a_document_of_the_same_name(client, kb):
    """The org is in the key. Two customers both having a "Values" doc is the normal case,
    not a collision."""
    _upload(client, "acme", "Values", "values", text="Acme.")
    _upload(client, "globex", "Values", "values", text="Globex.")

    assert len(client.get("/v1/cskb/acme").json()["docs"]) == 1
    assert len(client.get("/v1/cskb/globex").json()["docs"]) == 1


# ── the gate: curated, not self-serve ────────────────────────────────────────


def test_only_an_operator_can_touch_a_knowledge_base(client, monkeypatch):
    """PRODUCT.md ships this CURATED; self-serve is v2 and SECURITY.md gates it on prompt
    injection — everything indexed here is retrieved into the coach's context on a later
    turn, so an upload box is an instruction channel into every session that tenant runs.
    An org_admin reaching it is that gate falling over.
    """
    from app.auth import dependencies as deps

    monkeypatch.setattr(deps, "auth_enabled", lambda: True)
    for path, method in (("/v1/cskb/acme", "GET"), ("/v1/cskb/acme", "POST"),
                         ("/v1/cskb/acme/docs", "DELETE")):
        r = client.request(method, path, json={"title": "x", "doc_type": "values", "text": "x"})
        assert r.status_code in (401, 403), f"{method} {path} was reachable without a token"


@requires_pg
def test_an_unconfigured_index_says_so_instead_of_lying(client, kb, monkeypatch):
    """A disabled index and an empty one look identical from a count. Reporting "no
    documents" would tell an operator their tenant simply has no content, when in fact
    nothing they upload can be written."""
    import app.rag.store as store_facade

    monkeypatch.setattr(store_facade, "writable", lambda: False)

    body = client.get("/v1/cskb/acme").json()
    assert body["enabled"] is False

    r = _upload(client, "acme", "Acme Values", "values")
    assert r.status_code == 503, "an upload silently succeeded with no index behind it"


@requires_pg
def test_an_embedding_failure_says_so_instead_of_a_bare_500(client, kb, monkeypatch):
    """Measured on the live stack: the configured embedding model was not installed on the
    box, `/api/embed` returned 404, and this route raised an unhandled HTTPStatusError —
    a 500 with an empty body. The operator learns nothing, and "did my document land?" is
    exactly the question this surface exists to answer.

    Embedding is a call to a MODEL. It fails for ordinary reasons (provider down, key
    missing, model absent). Say which half broke: the document was fine, the index could
    not take it, nothing was written.
    """
    import app.routers.cskb as cskb_mod

    def boom(*_a, **_k):
        raise RuntimeError("Client error '404 Not Found' for url '.../api/embed'")

    monkeypatch.setattr("app.rag.ingest.embed_and_upsert", boom)

    r = _upload(client, "acme", "Acme Values", "values")

    assert r.status_code == 503, f"an embed failure surfaced as {r.status_code}"
    assert "embedding" in r.text.lower()
    assert "not written" in r.text.lower(), "the operator must know nothing landed"
    assert client.get("/v1/cskb/acme").json()["docs"] == []


@requires_pg
def test_an_upload_that_indexes_nothing_is_not_reported_as_success(client, kb, monkeypatch):
    """embed_and_upsert returns 0 when every record is dropped. A 200 there would tell the
    operator the tenant is tuned while retrieval comes back empty forever."""
    monkeypatch.setattr("app.rag.ingest.embed_and_upsert", lambda *_a, **_k: 0)

    r = _upload(client, "acme", "Acme Values", "values")

    assert r.status_code == 503
    assert "nothing was indexed" in r.text.lower()
