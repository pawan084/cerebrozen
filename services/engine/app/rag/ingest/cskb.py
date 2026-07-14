"""CSKB ingestion — per-client knowledge base.

S3 layout: s3://<bucket>/cskb/<orgId>/<group>/<file>, where <group> is one of a
fixed set of typed subfolders per org (cskb_org_framework/, cskb_values/,
cskb_competency/, cskb_learning_aid/). The directory IS the taxonomy — same
directory-first contract as sskb.py — so `doc_type` is read straight off the
`<group>` path segment, never guessed from the filename. This is what lets
Extract2/4/5/values scope to their own subfolder without bleeding across an
org's other docs.

Client docs are real documents (PDF/DOCX), so they ARE chunked (unlike atomic SSKB
items). Each chunk keeps the doc's metadata + source_link.
"""

from __future__ import annotations

import logging
from pathlib import PurePosixPath
from typing import List

from app import config
from app.rag import store
from app.rag.ingest import chunk_doc, embed_and_upsert, iter_s3_objects

logger = logging.getLogger("cerebrozen.rag")


#  Exact per-org subfolder name -> doc_type. The directory is the authority for
# what a doc is (mirrors sskb.py's _SSKB_DIR/_subdir) — NEVER inferred from the
# filename. Filename sniffing let a file like "CSKB_LearningAid1.pdf" get
# misclassified as "frameworks" whenever its name/path happened to contain the
# substring "framework" (or miss the "learning aid"/"tool" patterns), which
# leaked the wrong document's source_link into another extraction's candidate
# pool with correct-looking text alongside a mismatched link. Any subfolder not
# listed here classifies as "general" and is skipped by all doc_type filters
# rather than being guessed at.
_CSKB_DIR = {
    "cskb_org_framework": "frameworks",
    "cskb_values": "values",
    "cskb_competency": "competencies",
    "cskb_learning_aid": "learning_aids",
}


def classify_doc_type(doc_group: str) -> str:
    """Map a CSKB doc's exact per-org subfolder name (the `<group>` segment in
    cskb/<orgId>/<group>/<file>) to its doc_type. Directory is the authority, not
    filename sniffing — an unrecognized subfolder is "general", not guessed at."""
    return _CSKB_DIR.get(doc_group.strip().lower(), "general")


def _records_for_key(key: str) -> List[dict]:
    """Build chunk records for one client object. Path: cskb/<orgId>/<group>/<file>.

    `org_id` is read from the key's own path (not hardcoded), so any number of org
    folders under cskb/ are picked up automatically as they're added. `doc_type`
    (from the exact per-org subfolder name) scopes Extract2/4/5/values to their own
    doc via the registry's `doc_type` filter."""
    rel = key[len(config.RAG_CSKB_PREFIX) + 1:] if key.startswith(config.RAG_CSKB_PREFIX) else key
    parts = [p for p in rel.split("/") if p]
    if not parts:
        return []
    org_id = parts[0]
    doc_group = parts[1] if len(parts) > 2 else ""
    doc_type = classify_doc_type(doc_group)

    records = chunk_doc(
        key,
        id_prefix=f"cskb:{org_id}",
        extra_fields={
            "kb": "cskb",
            "org_id": org_id,
            "doc_type": doc_type,
            "source": "client",
            "doc_group": doc_group,
        },
    )
    logger.info("rag.cskb_classified", extra={"key": key, "doc_type": doc_type, "chunks": len(records)})
    return records


def ingest_cskb(org_id: str = "") -> dict:
    """Ingest all client docs (optionally for a single org). Re-runnable: records
    upsert by id, so re-ingesting an org refreshes its docs. Also prunes: any
    doc_key that was previously indexed under this scope but no longer shows up
    in S3 (deleted upstream) has its chunks removed too."""
    prefix = config.RAG_CSKB_PREFIX + "/"
    if org_id:
        prefix += org_id.rstrip("/") + "/"

    indexed = store.indexed_docs("cskb")  # {doc_key: etag} — every org
    seen: set = set()
    written = files = skipped_existing = skipped_type = 0
    by_file: dict = {}  # filename -> chunks ingested (diagnostic: spot empty/image-only docs)
    for key, etag in iter_s3_objects(prefix):
        seen.add(key)
        if key.lower().rsplit(".", 1)[-1] not in ("pdf", "docx", "pptx", "txt", "md"):
            skipped_type += 1
            logger.info("rag.cskb_skip", extra={"key": key})
            continue
        if etag and indexed.get(key) == etag:  # already indexed, unchanged
            skipped_existing += 1
            continue
        if key in indexed:  # changed doc → drop stale chunks before re-embed
            store.delete_by_doc_key("cskb", key)
        try:
            recs = _records_for_key(key)
            for r in recs:
                r["doc_key"] = key
                r["s3_etag"] = etag
            n = embed_and_upsert("cskb", recs)
            by_file[PurePosixPath(key).name] = n
            written += n
            files += 1
        except Exception:  # noqa: BLE001
            logger.exception("rag.cskb_ingest_failed", extra={"key": key})

    # Prune docs deleted upstream: indexed under this scope, but never seen in
    # this run's listing. Guarded on RAG_S3_BUCKET being set — iter_s3_objects
    # yields nothing (without erroring) when the bucket is unconfigured, and that
    # must never be mistaken for "everything was deleted" and wipe the index.
    pruned = 0
    if config.RAG_S3_BUCKET:
        stale = [k for k in indexed if k.startswith(prefix) and k not in seen]
        for key in stale:
            store.delete_by_doc_key("cskb", key)
            logger.info("rag.cskb_pruned", extra={"key": key})
        pruned = len(stale)

    result = {"files": files, "chunks": written, "skipped_existing": skipped_existing,
              "skipped_type": skipped_type, "pruned": pruned, "by_file": by_file,
              "org_id": org_id or "ALL"}
    logger.info("rag.cskb_ingested", extra=result)
    return result
