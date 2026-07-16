"""The RAG demo corpus is real and retrievable.

Proves the whole no-S3 seed path end-to-end: the bundled rag_seed/ corpus ingests
via the same chunk_doc + embed_and_upsert primitives as the S3 path, lands in the
right kb/source/doc_type buckets, and comes back out of vector search — including
CSKB org-isolation. Retrieval *quality* isn't the point (a deterministic hash
embedder stands in for the real one); the plumbing is.
"""

import hashlib
import math
import re

import pytest

_DIM = 16


def _fake_vector(text):
    vec = [0.0] * _DIM
    for word in re.findall(r"[a-z0-9]+", (text or "").lower()):
        vec[int(hashlib.sha1(word.encode()).hexdigest(), 16) % _DIM] += 1.0
    if not any(vec):
        vec[0] = 1.0
    norm = math.sqrt(sum(v * v for v in vec))
    return [v / norm for v in vec]


@pytest.fixture
def seeded(tmp_path, monkeypatch):
    """A real, empty LanceDB in tmp_path + a deterministic embedder, with the
    bundled demo corpus seeded into it."""
    from app import config
    from app.rag import embedder, store
    from app.rag.seed_demo import seed_from_dir

    monkeypatch.delenv("CEREBROZEN_RAG_BACKEND", raising=False)
    monkeypatch.setattr(config, "RAG_LANCEDB_URI", str(tmp_path / "lancedb"))
    monkeypatch.setattr(config, "RAG_CACHE_TTL_S", 0)
    monkeypatch.setattr(embedder, "embed", lambda texts: [_fake_vector(t) for t in texts])
    monkeypatch.setattr(embedder, "embed_one", lambda text: _fake_vector(text))
    monkeypatch.setattr(embedder, "embedding_dim", lambda: _DIM)
    store._connect.cache_clear()
    summary = seed_from_dir()  # bundled rag_seed/
    yield store, summary
    store._connect.cache_clear()


def test_the_demo_corpus_ingests_into_both_knowledge_bases(seeded):
    _, summary = seeded
    assert summary["seeded"] is True
    assert summary["sskb"] > 0, "SSKB concepts/micro-learning should have indexed"
    assert summary["cskb"] > 0, "CSKB (demo-org) docs should have indexed"


def test_sskb_concepts_are_retrievable_and_source_tagged(seeded):
    store, _ = seeded
    hits = store.search("sskb", _fake_vector("psychological safety on a team"),
                        filters={"source": "concept"}, top_k=10)
    assert hits, "seeded concept chunks must come back out of search"
    assert all(h["source"] == "concept" for h in hits)  # the source filter holds
    assert any("psychological safety" in (h["text"] or "").lower() for h in hits)


def test_cskb_is_scoped_to_the_owning_org(seeded):
    store, _ = seeded
    q = _fake_vector("company values ownership")
    mine = store.search("cskb", q, filters={"org_id": "demo-org", "doc_type": "values"}, top_k=10)
    assert mine and any("ownership" in (h["text"] or "").lower() for h in mine)
    # A different tenant sees nothing of demo-org's culture.
    other = store.search("cskb", q, filters={"org_id": "someone-else", "doc_type": "values"}, top_k=10)
    assert other == []


def test_cskb_framework_lands_under_the_right_doc_type(seeded):
    store, _ = seeded
    hits = store.search("cskb", _fake_vector("leadership develop deliver shape"),
                        filters={"org_id": "demo-org", "doc_type": "frameworks"}, top_k=10)
    assert hits and all(h["doc_type"] == "frameworks" for h in hits)


def test_seeding_is_idempotent(seeded, monkeypatch):
    # Re-seeding upserts by stable id — the row count must not grow.
    from app.rag import store
    from app.rag.seed_demo import seed_from_dir

    before = store.search("sskb", _fake_vector("psychological safety"),
                          filters={"source": "concept"}, top_k=50)
    seed_from_dir()
    after = store.search("sskb", _fake_vector("psychological safety"),
                         filters={"source": "concept"}, top_k=50)
    assert len(after) == len(before)


def test_missing_seed_dir_is_a_clean_noop(monkeypatch, tmp_path):
    from app.rag.seed_demo import seed_from_dir

    out = seed_from_dir(str(tmp_path / "does-not-exist"))
    assert out == {"seeded": False, "reason": "no seed dir",
                   "root": str(tmp_path / "does-not-exist"), "sskb": 0, "cskb": 0}


def test_one_unreadable_file_is_skipped_not_fatal(tmp_path, monkeypatch):
    # A stray/unparseable doc in either KB must be logged and skipped, never abort
    # the whole seed run.
    from app.rag.seed_demo import seed_from_dir

    (tmp_path / "sskb" / "sskb_concept").mkdir(parents=True)
    (tmp_path / "sskb" / "sskb_concept" / "x.md").write_text("hi")
    (tmp_path / "cskb" / "org1" / "cskb_values").mkdir(parents=True)
    (tmp_path / "cskb" / "org1" / "cskb_values" / "y.md").write_text("hi")

    def _boom(*a, **k):
        raise RuntimeError("cannot chunk")

    monkeypatch.setattr("app.rag.ingest.chunk_doc", _boom)
    out = seed_from_dir(str(tmp_path))
    assert out == {"seeded": True, "root": str(tmp_path), "sskb": 0, "cskb": 0}
