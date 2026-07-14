"""Startup ingestion for the RAG vector store (ephemeral-EC2 friendly).

On an instance with no persistent disk, the LanceDB index lives in S3
(`RAG_LANCEDB_URI=s3://<bucket>/lancedb`). This runs once at app startup:

  - Default (RAG_INGEST_ON_STARTUP=true): connect to the existing S3 index and
    INCREMENTALLY ingest — embed only docs whose S3 key+ETag aren't already
    indexed; re-embed a doc whose ETag changed; skip everything else. So a fresh
    instance reuses the index as-is, and a newly-uploaded doc is picked up without
    re-embedding the whole corpus. A doc deleted from S3 has its chunks pruned
    from the index too (see ingest/cskb.py and ingest/sskb.py).
  - RAG_REINDEX=true: drop the vector store first, then re-embed everything.

Best-effort and fully guarded: any failure (no S3, no OpenAI key, lancedb absent)
is logged and swallowed so it never blocks the service from starting.
"""

from __future__ import annotations

import importlib.util
import logging
import time
from typing import Any, Dict, List

from app import config
from app.rag import store

logger = logging.getLogger("cerebrozen.rag")

# Optional deps the S3-backed RAG ingest needs. Absent on a lean local install,
# so we preflight them and skip cleanly (with an install hint) rather than letting
# a raw ModuleNotFoundError surface as a full traceback at boot.
_REQUIRED_INGEST_MODULES = ("lancedb", "boto3")


def _missing_ingest_deps() -> List[str]:
    """Names of the RAG-ingest optional deps that aren't importable."""
    return [m for m in _REQUIRED_INGEST_MODULES if importlib.util.find_spec(m) is None]


def _has_aws_credentials() -> bool:
    """True if boto3 can resolve credentials (env / shared config / instance role).
    Checks the credential chain WITHOUT making an S3 call — so a creds-less local
    box skips ingest cleanly instead of raising NoCredentialsError mid-traceback."""
    try:
        import boto3

        return boto3.Session().get_credentials() is not None
    except Exception:  # noqa: BLE001 — any boto3 hiccup → treat as no creds, skip.
        return False


def _is_s3_access_error(exc: BaseException) -> bool:
    """True for the expected S3-access failures — expired/invalid token, access
    denied, missing bucket, endpoint/connection errors. These mean "can't ingest
    in this environment", not "the code is broken", so they degrade to a clean
    one-line skip rather than a boot-time traceback. (NoCredentialsError and the
    connection errors are BotoCoreError subclasses; API errors are ClientError.)"""
    try:
        from botocore.exceptions import BotoCoreError, ClientError

        return isinstance(exc, (BotoCoreError, ClientError))
    except Exception:  # noqa: BLE001 — botocore absent → not an s3 access error.
        return False


def run_startup(reindex: bool = False) -> Dict[str, Any]:
    """Connect to the index and ingest per the policy above. Returns a summary."""
    if not (reindex or config.RAG_INGEST_ON_STARTUP):
        logger.info("rag.startup_skipped", extra={"reason": "RAG_INGEST_ON_STARTUP=false"})
        return {"ingested": False}

    # Preflight (before any drop/connect): degrade cleanly when the environment
    # can't actually ingest, so boot logs carry a one-line hint, not a traceback.
    missing = _missing_ingest_deps()
    if missing:
        logger.warning(
            "rag.startup_skipped",
            extra={
                "reason": f"RAG deps not installed: {', '.join(missing)}",
                "hint": "pip install -r requirements.txt to enable RAG (installs "
                + ", ".join(_REQUIRED_INGEST_MODULES) + ")",
            },
        )
        return {"ingested": False, "reason": "missing_deps", "missing": missing}

    if config.RAG_S3_BUCKET and not _has_aws_credentials():
        logger.warning(
            "rag.startup_skipped",
            extra={
                "reason": "no AWS credentials resolved",
                "hint": f"set AWS creds (or an instance role) to ingest s3://"
                f"{config.RAG_S3_BUCKET}; RAG stays empty until then",
            },
        )
        return {"ingested": False, "reason": "no_aws_credentials"}

    t0 = time.perf_counter()
    if reindex:
        logger.info("rag.reindex_start", extra={"uri": config.RAG_LANCEDB_URI})
        store.drop_tables()

    # Imported here so a degraded environment (no boto3/lancedb) can't break import.
    from app.rag.ingest.cskb import ingest_cskb
    from app.rag.ingest.sskb import ingest_sskb

    try:
        sskb_summary = ingest_sskb()
        cskb_summary = ingest_cskb()
    except Exception as exc:  # noqa: BLE001
        # Creds resolved but S3 is unusable (expired token, access denied, bad
        # bucket, offline). Degrade to a clean skip — RAG stays empty, boot
        # continues. Anything that ISN'T an S3-access error is a real bug → re-raise.
        if _is_s3_access_error(exc):
            logger.warning(
                "rag.startup_skipped",
                extra={
                    "reason": f"S3 ingest unavailable: {type(exc).__name__}: {exc}",
                    "hint": f"check AWS credentials/permissions for s3://"
                    f"{config.RAG_S3_BUCKET} (e.g. expired token); RAG stays empty until fixed",
                },
            )
            return {"ingested": False, "reason": "s3_access_error", "error": str(exc)}
        raise

    summary: Dict[str, Any] = {
        "ingested": True,
        "reindex": reindex,
        "uri": config.RAG_LANCEDB_URI,
        "sskb": sskb_summary,
        "cskb": cskb_summary,
    }
    summary["sskb_rows"] = store.count("sskb")
    summary["cskb_rows"] = store.count("cskb")
    summary["elapsed_s"] = round(time.perf_counter() - t0, 1)
    logger.info("rag.startup_done", extra=summary)
    return summary
