"""pgvector RAG store — the S3-free replacement for LanceDB.

Same surface as `app/rag/store.py` (`search` / `upsert` / `count` / `indexed_docs` /
`delete_by_doc_key`), so `app/rag/extractors.py` and everything above it is unchanged.
Selected with `CEREBROZEN_RAG_BACKEND=pgvector`.

Why this maps cleanly: the retrieval the extractors actually do is
`cosine similarity + metadata pre-filter + top-k`. That is one SQL statement in pgvector
(`ORDER BY embedding <=> query LIMIT k WHERE org_id = … AND doc_type = …`) — there is no
LanceDB-specific behaviour to emulate.

Two tables mirror the two knowledge bases (`sskb` global, `cskb` per-org), because the
extraction registry addresses them by name and their metadata filters differ.

DIMENSIONS ARE NOT PORTABLE. text-embedding-3-small is 1536-dim; nomic-embed-text is 768.
The table is created at whatever dimension the *configured embedder* reports, and a query
vector from a different model cannot search it. Changing embedder = re-ingest, not migrate.
"""

from __future__ import annotations

import logging
import os
from typing import Any, Dict, List, Optional

logger = logging.getLogger("cerebrozen.rag")

_ready: set = set()


def enabled() -> bool:
    """Postgres-first: pgvector is the default RAG backend whenever Postgres is
    configured. Set CEREBROZEN_RAG_BACKEND=lancedb to opt back into LanceDB/S3."""
    explicit = os.environ.get("CEREBROZEN_RAG_BACKEND", "").strip().lower()
    if explicit:
        return explicit == "pgvector"
    return bool(os.environ.get("POSTGRES_URL", "").strip())


def _pool():
    from app.stores.pg import get_pool

    return get_pool()


def _table(kb: str) -> str:
    kb = (kb or "sskb").strip().lower()
    return "rag_sskb" if kb == "sskb" else "rag_cskb"


def _existing_dim(conn, t: str) -> Optional[int]:
    row = conn.execute(
        "SELECT a.atttypmod FROM pg_attribute a "
        "JOIN pg_class c ON c.oid = a.attrelid "
        "WHERE c.relname = %s AND a.attname = 'embedding'", (t,)
    ).fetchone()
    return row[0] if row and row[0] and row[0] > 0 else None


def _ensure(kb: str, dim: int) -> None:
    """Create the table + indexes for this kb. Idempotent.

    Also guards the dimension lock: an index built with one embedding model CANNOT be
    queried with another (1536-dim OpenAI vs 768-dim nomic-embed-text). Postgres already
    rejects the mismatch, but with `expected 8 dimensions, not 1536` — which tells you
    nothing about what to DO. Raise something actionable instead."""
    t = _table(kb)
    if t in _ready:
        return
    pool = _pool()
    if pool is None:
        return
    with pool.connection() as conn:
        # The `vector` type comes from an extension that is NOT enabled by default. Nothing
        # in this repo ever created it, so on a clean database every statement below died
        # with `type "vector" does not exist` — which reaches the user as RAG returning
        # null for everything, i.e. as a coach with no evidence base and no error. A new
        # client following our own setup guide would have hit this on day one.
        # Needs a superuser or an image that ships the extension (pgvector/pgvector).
        try:
            conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
        except Exception as exc:  # noqa: BLE001
            raise RuntimeError(
                "the pgvector extension is not installed and could not be enabled "
                f"({exc}). RAG cannot run on this database. Use an image that ships it "
                "(pgvector/pgvector), or have a superuser run: CREATE EXTENSION vector;"
            ) from exc
    with pool.connection() as conn:
        have = _existing_dim(conn, t)
        if have and have != dim:
            raise RuntimeError(
                f"{t} was built for {have}-dim vectors, but the configured embedder "
                f"produces {dim}-dim. Embeddings are NOT portable across models — there is "
                f"no migration, only a re-index. Drop the table and re-ingest the knowledge "
                f"base with the new embedder:  DROP TABLE {t};"
            )
    with pool.connection() as conn:
        conn.execute(
            f"""CREATE TABLE IF NOT EXISTS {t} (
                    id        TEXT PRIMARY KEY,
                    doc_key   TEXT,
                    text      TEXT NOT NULL,
                    meta      JSONB NOT NULL DEFAULT '{{}}'::jsonb,
                    embedding vector({dim}) NOT NULL
                )"""
        )
        # HNSW over cosine: better recall/latency than IVFFlat at this scale, and it needs
        # no training step (IVFFlat's lists must be built AFTER the data is loaded — a
        # classic footgun when you index an empty table).
        conn.execute(
            f"CREATE INDEX IF NOT EXISTS {t}_emb_hnsw ON {t} "
            f"USING hnsw (embedding vector_cosine_ops)"
        )
        conn.execute(f"CREATE INDEX IF NOT EXISTS {t}_meta_gin ON {t} USING GIN (meta)")
        conn.execute(f"CREATE INDEX IF NOT EXISTS {t}_doc_key ON {t} (doc_key)")
    _ready.add(t)


def upsert(kb: str, records: List[Dict[str, Any]]) -> int:
    """records: [{id, doc_key, text, vector|embedding, **metadata}]"""
    pool = _pool()
    if pool is None or not records:
        return 0

    def _vec(r):
        # The ingest pipeline emits "vector"; this module was written expecting
        # "embedding". They never met, so every upsert raised KeyError, ingest swallowed
        # it per-document, and the KB stayed empty with no error raised anywhere.
        v = r.get("vector")
        if v is None:
            v = r.get("embedding")
        if v is None:
            raise KeyError(
                "rag record has neither 'vector' nor 'embedding' — nothing to index"
            )
        return v

    dim = len(_vec(records[0]))
    _ensure(kb, dim)
    t = _table(kb)

    from psycopg.types.json import Jsonb

    rows = []
    for r in records:
        meta = {k: v for k, v in r.items()
                if k not in ("id", "doc_key", "text", "embedding", "vector")}
        rows.append((
            str(r.get("id") or r.get("doc_key")),
            r.get("doc_key", ""),
            r.get("text", ""),
            Jsonb(meta),
            str(list(_vec(r))),          # pgvector accepts the '[1,2,3]' text form
        ))
    with pool.connection() as conn:
        with conn.cursor() as cur:
            cur.executemany(
                f"INSERT INTO {t} (id, doc_key, text, meta, embedding) "
                f"VALUES (%s,%s,%s,%s,%s) "
                f"ON CONFLICT (id) DO UPDATE SET "
                f"  text=EXCLUDED.text, meta=EXCLUDED.meta, embedding=EXCLUDED.embedding",
                rows,
            )
    logger.info("rag.pgvector_upsert", extra={"kb": kb, "rows": len(rows)})
    return len(rows)


def search(kb: str, query_vector: List[float], *,
           filters: Optional[Dict[str, Any]] = None,
           top_k: int = 5) -> List[Dict[str, Any]]:
    """Cosine top-k with a metadata PRE-filter (the WHERE runs before the ranking, so a
    per-org filter can never leak another org's documents into the results)."""
    pool = _pool()
    if pool is None:
        return []
    t = _table(kb)
    if t not in _ready:
        _ensure(kb, len(query_vector))

    from psycopg.types.json import Jsonb

    where, params = "", []
    if filters:
        where = "WHERE meta @> %s"
        params.append(Jsonb(filters))
    params += [str(list(query_vector)), top_k]

    with pool.connection() as conn:
        # `_score` is a DISTANCE (lower = closer), NOT a similarity — because that is the
        # contract LanceDB set, and every consumer was written against it. This returned
        # `1 - (embedding <=> q)`, a similarity, so the two backends disagreed about which
        # direction "better" points. `extractors._extract_learning_aid` picks its fallback
        # with `min(_score)`: on LanceDB that is the most relevant aid, and on pgvector it
        # was the LEAST relevant one. The coach would have confidently handed an offline
        # user the worst match in the knowledge base, and nothing would have looked wrong.
        # One backend swap, silently inverted relevance. Both now speak distance.
        rows = conn.execute(
            f"SELECT text, meta, (embedding <=> %s) AS score "
            f"FROM {t} {where} ORDER BY embedding <=> %s LIMIT %s",
            [str(list(query_vector))] + params,
        ).fetchall()

    out = []
    for text, meta, score in rows:
        rec = dict(meta or {})
        rec["text"] = text
        rec["_score"] = float(score)
        out.append(rec)
    logger.info("rag.pgvector_search",
                extra={"kb": kb, "hits": len(out), "filters": filters or {}})
    return out


def count(kb: str) -> int:
    pool = _pool()
    if pool is None:
        return 0
    t = _table(kb)
    try:
        with pool.connection() as conn:
            return conn.execute(f"SELECT count(*) FROM {t}").fetchone()[0]
    except Exception:  # noqa: BLE001 — table not created yet
        return 0


def indexed_docs(kb: str) -> Dict[str, str]:
    pool = _pool()
    if pool is None:
        return {}
    t = _table(kb)
    try:
        with pool.connection() as conn:
            rows = conn.execute(
                f"SELECT DISTINCT doc_key, meta->>'s3_etag' FROM {t} "
                f"WHERE doc_key <> ''"
            ).fetchall()
        # {doc_key: s3_etag} — the ETag is the whole point: it is what lets ingestion skip
        # an unchanged document instead of re-embedding the corpus on every boot.
        return {r[0]: (r[1] or "") for r in rows}
    except Exception:  # noqa: BLE001
        return {}


def delete_by_doc_key(kb: str, doc_key: str) -> None:
    pool = _pool()
    if pool is None or not doc_key:
        return
    with pool.connection() as conn:
        conn.execute(f"DELETE FROM {_table(kb)} WHERE doc_key = %s", (doc_key,))
