"""LanceDB vector store — two structurally-isolated tables.

`sskb` (CereBroZen-global) and `cskb` (per-client) live in separate LanceDB tables
so isolation is structural, not filter-dependent: an SSKB query physically cannot
return CSKB rows, and a CSKB query is always org-scoped. LanceDB is embedded (no
server), so this is a true in-process tool.

Schema is a fixed, generous column set shared by both tables (unused columns are
empty strings) plus a JSON `meta` blob for anything extra. Scalar columns are
filterable via a SQL-ish WHERE clause; `vector` carries the embedding.

Defensive by design (mirrors stores/mongo.py): if `lancedb` is not installed or
the URI is unreachable, search returns [] and the extraction degrades to NULL —
the graph keeps running.
"""

from __future__ import annotations

import json
import logging
import os
from functools import lru_cache
from typing import Any, Dict, List, Optional

from app import config

logger = logging.getLogger("cerebrozen.rag")

# Filterable scalar columns shared by both tables. Anything not in here rides in
# the JSON `meta` blob. Keep names stable — ingestion + extractors both rely on them.
SCALAR_COLUMNS = (
    "id",
    "text",
    "kb",            # "sskb" | "cskb"
    "org_id",        # "" for sskb; the org for cskb
    "doc_type",      # cskb only: "frameworks" | "competencies" | "learning_aids" | "values" | "general"
    "source",        # logical source: "curated" | "micro_learning" | "concept" | "client"
    "item_type",     # "micro_learning" | "curated_content" | "" (for learning-aid selection)
    "source_link",   # URL/S3 link, or "" when the extraction carries no link
    "title",
    "author",
    "content_format",  # "video" | "audio" | "pdf" | "article" | ""
    "topic",
    "skill",
    "level",
    "cluster",
    "doc_group",     # cskb wrapper-dir name / sskb file name
    "doc_key",       # source S3 key — identifies the doc for idempotent ingestion
    "s3_etag",       # source object ETag — re-embed only when the doc content changes
)


def _table_for(kb: str) -> str:
    return config.RAG_CSKB_TABLE if kb == "cskb" else config.RAG_SSKB_TABLE


def _resolve_bucket_region(bucket: str) -> Optional[str]:
    """Discover a bucket's real region via boto3 — the SAME IAM-role/credential
    chain prompt-fetch uses — so we never depend on a configured AWS_REGION that
    may not match the bucket. (LanceDB's object store, unlike boto3, won't follow
    S3's region redirect, so it must be handed the correct region up front.)

    Returns None if detection fails; the caller then falls back to config.AWS_REGION.
    """
    try:
        import boto3

        loc = boto3.client("s3").get_bucket_location(Bucket=bucket).get("LocationConstraint")
    except Exception as exc:  # noqa: BLE001 — degrade to the configured region.
        logger.warning("rag.region_detect_failed", extra={"bucket": bucket, "error": str(exc)})
        return None
    # us-east-1 historically returns null; "EU" is the legacy eu-west-1 alias.
    if not loc:
        return "us-east-1"
    return "eu-west-1" if loc == "EU" else loc


@lru_cache(maxsize=1)
def _connect():
    """Cached LanceDB connection, or None when unavailable (lib missing / bad URI).

    Supports both a local dir and an S3 URI (`s3://bucket/prefix`) — the latter
    keeps the index durable + shared on ephemeral EC2. S3 credentials AND region
    are resolved from the instance IAM role / standard AWS chain (via boto3), so no
    AWS_REGION env needs to match the bucket — it's auto-detected from the bucket."""
    try:
        import lancedb  # lazy: importing app.rag never requires lancedb installed
    except Exception as exc:  # noqa: BLE001
        logger.info("rag.store_disabled", extra={"reason": f"lancedb import: {exc}"})
        return None
    uri = config.RAG_LANCEDB_URI
    kwargs: Dict[str, Any] = {}
    if uri.startswith("s3://"):
        # Auto-detect the bucket's region (boto3 / IAM role) instead of trusting a
        # configured AWS_REGION — the bucket may live in a different region than the
        # app. Fall back to config.AWS_REGION only if detection fails.
        bucket = uri[len("s3://"):].split("/", 1)[0]
        region = _resolve_bucket_region(bucket) or config.AWS_REGION
        # LanceDB's object store takes the SigV4 *signing* region from the
        # AWS_REGION / AWS_DEFAULT_REGION env vars and ignores storage_options
        # ["region"] whenever those env vars are set (only "endpoint" is honored).
        # When the RAG bucket's region differs from the app's region (e.g. RAG in
        # us-east-1, app/EC2 in ap-south-1) the env-derived signing region is wrong
        # and S3 rejects every request (AuthorizationHeaderMalformed). Pin the env
        # to the bucket's region so the signature matches. This is safe because every
        # other AWS client passes its region explicitly (prompt_store, rag.ingest),
        # so the pin is scoped to LanceDB in practice. endpoint+region in
        # storage_options stay as belt-and-suspenders for the request host.
        os.environ["AWS_REGION"] = region
        os.environ["AWS_DEFAULT_REGION"] = region
        storage_options: Dict[str, str] = {
            "region": region,
            "endpoint": f"https://s3.{region}.amazonaws.com",
        }
        # Also pass the creds boto3 resolves so LanceDB's object store authenticates
        # the SAME way as the rest of the service (local ~/.aws, env, or EC2 role).
        try:
            import boto3

            creds = boto3.Session().get_credentials()
            if creds:
                frozen = creds.get_frozen_credentials()
                storage_options["aws_access_key_id"] = frozen.access_key
                storage_options["aws_secret_access_key"] = frozen.secret_key
                if frozen.token:
                    storage_options["aws_session_token"] = frozen.token
        except Exception:  # noqa: BLE001 — fall back to object-store's own resolution
            pass
        kwargs["storage_options"] = storage_options
        logger.info("rag.store_connect", extra={"uri": uri, "region": region})
    try:
        return lancedb.connect(uri, **kwargs)
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.store_unavailable", extra={"uri": uri, "error": str(exc)})
        return None


def _norm_record(rec: Dict[str, Any]) -> Dict[str, Any]:
    """Coerce an arbitrary ingestion dict into the fixed table schema. Extra keys
    are JSON-packed into `meta`; missing scalar columns default to ""."""
    row: Dict[str, Any] = {col: rec.get(col, "") for col in SCALAR_COLUMNS}
    row["vector"] = rec["vector"]
    extra = {k: v for k, v in rec.items() if k not in SCALAR_COLUMNS and k != "vector"}
    row["meta"] = json.dumps(extra, ensure_ascii=False, default=str) if extra else ""
    # Scalars must be strings for the shared schema / WHERE filters.
    for col in SCALAR_COLUMNS:
        if row[col] is None:
            row[col] = ""
        elif not isinstance(row[col], str):
            row[col] = str(row[col])
    return row


def upsert(kb: str, records: List[Dict[str, Any]]) -> int:
    """Insert records (each already carrying a `vector`) into the kb's table.

    Replaces rows with matching `id` (delete-then-add) so re-ingestion is
    idempotent. Returns the number of rows written; 0 when the store is disabled."""
    # pgvector backend (no S3). Same contract; see rag/pgvector_store.py.
    from app.rag import pgvector_store as _pg
    if _pg.enabled():
        return _pg.upsert(kb, records)
    db = _connect()
    if db is None or not records:
        return 0
    table_name = _table_for(kb)
    rows = [_norm_record(r) for r in records]
    try:
        if table_name in db.table_names():
            tbl = db.open_table(table_name)
            ids = [r["id"] for r in rows if r.get("id")]
            if ids:
                quoted = ",".join("'" + i.replace("'", "''") + "'" for i in ids)
                tbl.delete(f"id IN ({quoted})")
            tbl.add(rows)
        else:
            db.create_table(table_name, data=rows)
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.upsert_failed", extra={"kb": kb, "error": str(exc)})
        return 0
    logger.info("rag.upserted", extra={"kb": kb, "table": table_name, "rows": len(rows)})
    return len(rows)


def _where_clause(filters: Optional[Dict[str, Any]]) -> Optional[str]:
    """Build a conjunctive SQL WHERE from {col: value | [values]}. Values are
    string-quoted (schema is all-string scalars). Unknown columns are ignored."""
    if not filters:
        return None
    parts: List[str] = []
    for col, val in filters.items():
        if col not in SCALAR_COLUMNS or val in (None, "", []):
            continue
        if isinstance(val, (list, tuple, set)):
            quoted = ",".join("'" + str(v).replace("'", "''") + "'" for v in val if v)
            if quoted:
                parts.append(f"{col} IN ({quoted})")
        else:
            parts.append(f"{col} = '" + str(val).replace("'", "''") + "'")
    return " AND ".join(parts) if parts else None


def search(
    kb: str,
    query_vector: List[float],
    *,
    filters: Optional[Dict[str, Any]] = None,
    top_k: int = 5,
) -> List[Dict[str, Any]]:
    """Vector search within one kb table, optionally metadata-filtered.

    Returns a list of hit dicts: the scalar columns + parsed `meta` + `_score`
    (LanceDB distance, lower = closer). Empty list when the store is disabled, the
    table is missing, or the query vector is empty — callers treat that as NULL.
    """
    # pgvector backend (no S3). Same contract; see rag/pgvector_store.py.
    from app.rag import pgvector_store as _pg
    if _pg.enabled():
        return _pg.search(kb, query_vector, filters=filters, top_k=top_k)
    db = _connect()
    if db is None or not query_vector:
        return []
    table_name = _table_for(kb)
    try:
        if table_name not in db.table_names():
            logger.info("rag.table_missing", extra={"kb": kb, "table": table_name})
            return []
        tbl = db.open_table(table_name)
        q = tbl.search(query_vector).metric("cosine")
        where = _where_clause(filters)
        if where:
            q = q.where(where, prefilter=True)
        rows = q.limit(top_k).to_list()
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.search_failed", extra={"kb": kb, "error": str(exc)})
        return []

    hits: List[Dict[str, Any]] = []
    for row in rows:
        hit = {col: row.get(col, "") for col in SCALAR_COLUMNS}
        meta_raw = row.get("meta") or ""
        if meta_raw:
            try:
                hit["meta"] = json.loads(meta_raw)
            except Exception:  # noqa: BLE001
                hit["meta"] = {}
        else:
            hit["meta"] = {}
        hit["_score"] = row.get("_distance")
        hits.append(hit)
    return hits


def drop_tables() -> None:
    """Delete both vector tables — used by RAG_REINDEX for a clean rebuild."""
    db = _connect()
    if db is None:
        return
    for table_name in (config.RAG_SSKB_TABLE, config.RAG_CSKB_TABLE):
        try:
            if table_name in db.table_names():
                db.drop_table(table_name)
                logger.info("rag.dropped_table", extra={"table": table_name})
        except Exception as exc:  # noqa: BLE001
            logger.warning("rag.drop_failed", extra={"table": table_name, "error": str(exc)})


def indexed_docs(kb: str) -> Dict[str, str]:
    """Return {doc_key: s3_etag} for everything already in the kb table, so
    ingestion can skip unchanged docs (idempotency) and re-embed changed ones.

    Projects only the two scalar columns (not the vectors) when the backend
    supports it; falls back to a full scan. Empty when the store/table is absent."""
    # pgvector backend (no S3). Same contract; see rag/pgvector_store.py.
    from app.rag import pgvector_store as _pg
    if _pg.enabled():
        return _pg.indexed_docs(kb)
    db = _connect()
    if db is None:
        return {}
    table_name = _table_for(kb)
    try:
        if table_name not in db.table_names():
            return {}
        tbl = db.open_table(table_name)
        try:
            # Project without loading vectors (cheap even for large tables).
            arrow = tbl.to_lance().to_table(columns=["doc_key", "s3_etag"])
        except Exception:  # noqa: BLE001 — fall back to a full materialise.
            arrow = tbl.to_arrow().select(["doc_key", "s3_etag"])
        keys = arrow.column("doc_key").to_pylist()
        etags = arrow.column("s3_etag").to_pylist()
        return {k: (e or "") for k, e in zip(keys, etags) if k}
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.indexed_docs_failed", extra={"kb": kb, "error": str(exc)})
        return {}


def delete_by_doc_key(kb: str, doc_key: str) -> None:
    """Remove all chunks belonging to one source doc (re-ingest of a changed doc)."""
    # pgvector backend (no S3). Same contract; see rag/pgvector_store.py.
    from app.rag import pgvector_store as _pg
    if _pg.enabled():
        return _pg.delete_by_doc_key(kb, doc_key)
    db = _connect()
    if db is None or not doc_key:
        return
    table_name = _table_for(kb)
    try:
        if table_name in db.table_names():
            db.open_table(table_name).delete(f"doc_key = '{doc_key.replace(chr(39), chr(39) * 2)}'")
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.delete_doc_failed", extra={"kb": kb, "doc_key": doc_key, "error": str(exc)})


def count(kb: str) -> int:
    """Row count for a kb table (0 when disabled/missing) — used by /rag health."""
    # pgvector backend (no S3). Same contract; see rag/pgvector_store.py.
    from app.rag import pgvector_store as _pg
    if _pg.enabled():
        return _pg.count(kb)
    db = _connect()
    if db is None:
        return 0
    table_name = _table_for(kb)
    try:
        if table_name not in db.table_names():
            return 0
        return db.open_table(table_name).count_rows()
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.count_failed", extra={"kb": kb, "error": str(exc)})
        return 0


# ── per-org knowledge-base management (routers/cskb.py) ──────────────────────
#
# pgvector only, and that is stated rather than papered over. The management surface needs
# a metadata GROUP BY and an org-filtered DELETE; LanceDB is the legacy path
# (ARCHITECTURE.md §"Storage consolidation: Postgres-first" drops it from the critical
# path, and even the offline profile pins CEREBROZEN_RAG_BACKEND=pgvector). Returning an
# empty list on LanceDB would tell an operator their tenant has no documents when the
# truth is that this backend cannot answer — the exact silent lie `writable()` exists to
# prevent.


def writable() -> bool:
    """Can a curated document be indexed AND listed back right now?

    Not "is some vector store reachable" — is the one that supports management. The
    routes report this rather than assuming, because an upload that returns 200 into a
    backend that never wrote it leaves the operator believing a tenant is tuned.
    """
    from app.rag import pgvector_store as _pg

    return bool(_pg.enabled())


def org_docs(kb: str, org_id: str) -> List[Dict[str, Any]]:
    """One row per document in an org's KB. Empty when the backend cannot answer — the
    caller must check `writable()` first to tell that from a genuinely empty index."""
    from app.rag import pgvector_store as _pg

    if not _pg.enabled():
        return []
    return _pg.org_docs(kb, org_id)


def delete_org_doc(kb: str, org_id: str, doc_key: str) -> int:
    """Delete one document from ONE org's KB; returns chunks removed. The org filter is in
    the DELETE's own WHERE — see pgvector_store.delete_org_doc for why that matters."""
    from app.rag import pgvector_store as _pg

    if not _pg.enabled():
        return 0
    return _pg.delete_org_doc(kb, org_id, doc_key)
