"""Workbook source resolver + S3 upload-with-backup.

`PROMPT_SOURCE` decides where the prompt workbook comes from:
  - "codebase" -> the bundled local agent_prompts.xlsx (config.PROMPT_WORKBOOK).
  - "s3" (default) -> download agent_prompts.xlsx from the system-configuration
    bucket (config.PROMPT_S3_BUCKET — distinct from RAG_S3_BUCKET), cached to a
    temp file. Falls back to the bundled local file if the S3 fetch fails, so a
    misconfigured bucket never takes prompts fully down.

Upload backs up the current S3 object to a timestamped key, then replaces it —
so a bad edit is always reversible.
"""

from __future__ import annotations

import hashlib
import logging
import tempfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Dict, Optional

from app import config

logger = logging.getLogger("cerebrozen.prompt_store")

# Server-side workbook cache: the file the registry loads from (and what the
# download endpoint serves).  Public so the router can reference the same path
# without re-downloading from S3 on every request.
WORKBOOK_CACHE_PATH = Path(tempfile.gettempdir()) / "agent_prompts.xlsx"


def _file_md5(path: Path) -> str:
    """MD5 hex digest of a local file (streaming, 64 KB chunks)."""
    h = hashlib.md5()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def _s3_etag_to_md5(etag: str) -> Optional[str]:
    """Strip quotes and return the MD5 portion of an S3 ETag.

    For single-part uploads (< 5 GB, which our xlsx always is) the ETag is the
    plain MD5.  For multipart uploads it's ``<md5>-<parts>`` — we return None in
    that case so callers know the comparison is not directly valid."""
    etag = etag.strip('"')
    if "-" in etag:
        return None  # multipart — cannot compare directly
    return etag


def workbook_checksum() -> Dict[str, Any]:
    """Compare the server cache with the live S3 object via MD5.

    Returns a dict with:
      local_md5   — MD5 of WORKBOOK_CACHE_PATH (what the registry has loaded)
      s3_etag     — raw ETag from S3 head_object
      s3_md5      — ETag with quotes/multipart-suffix stripped (None if multipart)
      match       — True when the bytes are identical (local_md5 == s3_md5)
      error       — set when S3 is unreachable or codebase-mode is active
    """
    if config.PROMPT_SOURCE == "codebase":
        return {"error": "codebase mode — S3 is not used; no comparison possible"}

    if not WORKBOOK_CACHE_PATH.is_file():
        return {"error": "server cache not populated; call POST /v1/prompts/reload first"}

    local_md5 = _file_md5(WORKBOOK_CACHE_PATH)
    try:
        s3 = _s3_client()
        head = s3.head_object(Bucket=config.PROMPT_S3_BUCKET, Key=config.PROMPT_S3_KEY)
        raw_etag: str = head.get("ETag", "")
        s3_md5 = _s3_etag_to_md5(raw_etag)
        match = (s3_md5 is not None) and (local_md5 == s3_md5)
        return {
            "local_md5": local_md5,
            "s3_etag": raw_etag,
            "s3_md5": s3_md5,
            "match": match,
            "note": None if s3_md5 else "S3 object was multipart-uploaded; ETag is not a plain MD5",
        }
    except Exception as exc:  # noqa: BLE001
        return {"local_md5": local_md5, "error": str(exc)}


def _s3_client():
    import boto3  # imported lazily so codebase-mode never needs boto3/AWS.

    # Pass the region explicitly rather than relying on the ambient AWS_REGION env:
    # the RAG store (app.rag.store) pins AWS_REGION/AWS_DEFAULT_REGION to the RAG
    # bucket's region, which may differ from this (prompt) bucket's. config.AWS_REGION
    # is the app/prompt-bucket region, captured at import before any such pinning.
    return boto3.client("s3", region_name=config.AWS_REGION)


def resolve_workbook() -> Dict[str, Any]:
    """Resolve the workbook the registry should load, with provenance.

    Returns {path, source, fallback, error}:
      path     — local filesystem path to load
      source   — "codebase" | "s3" | "s3-fallback"
      fallback — True when S3 was configured but the bundled file is being
                 served instead (the registry marks itself degraded so the
                 condition is visible in /v1/prompts and reload responses,
                 not just a log line)
      error    — the S3 failure, when fallback is True
    """
    if config.PROMPT_SOURCE == "codebase":
        return {"path": config.PROMPT_WORKBOOK, "source": "codebase",
                "fallback": False, "error": None}
    try:
        return {"path": _download_from_s3(), "source": "s3",
                "fallback": False, "error": None}
    except Exception as exc:  # noqa: BLE001 — never fail prompts over S3 trouble.
        # error-level, not warning: serving stale bundled prompts in an S3-configured
        # environment is an operational incident (config drift), not routine noise.
        logger.error(
            "prompt_store.s3_fallback",
            extra={"error": str(exc), "fallback": config.PROMPT_WORKBOOK},
        )
        return {"path": config.PROMPT_WORKBOOK, "source": "s3-fallback",
                "fallback": True, "error": str(exc)}


def resolve_workbook_path() -> str:
    """Back-compat wrapper over resolve_workbook() for callers that only need the path."""
    return resolve_workbook()["path"]


def _download_from_s3() -> str:
    if not config.PROMPT_S3_BUCKET:
        raise RuntimeError(
            "PROMPT_S3_BUCKET is not set — the prompt workbook lives in the "
            "system-configuration bucket and must be configured per environment "
            "(e.g. dev-system-configuration)."
        )
    extra: Dict[str, Any] = {}
    if config.PROMPT_S3_VERSION:
        extra["VersionId"] = config.PROMPT_S3_VERSION
    s3 = _s3_client()
    s3.download_file(
        config.PROMPT_S3_BUCKET, config.PROMPT_S3_KEY, str(WORKBOOK_CACHE_PATH),
        ExtraArgs=extra or None,
    )
    local_md5 = _file_md5(WORKBOOK_CACHE_PATH)
    logger.info(
        "prompt_store.s3_download",
        extra={
            "bucket": config.PROMPT_S3_BUCKET,
            "key": config.PROMPT_S3_KEY,
            "local_md5": local_md5,
        },
    )
    return str(WORKBOOK_CACHE_PATH)


def _backup_key(key: str) -> str:
    """prompts/agent_prompts.xlsx -> prompts/agent_prompts_20260611T103045Z.xlsx"""
    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    p = PurePosixPath(key)
    return str(p.with_name(f"{p.stem}_{ts}{p.suffix}"))


def upload_workbook_to_s3(data: bytes) -> Dict[str, Any]:
    """Replace the canonical S3 workbook with ``data``, backing up the current
    object first to a timestamped key. Returns bucket/key/backup_key."""
    if not config.PROMPT_S3_BUCKET:
        raise RuntimeError(
            "PROMPT_S3_BUCKET is not set — the prompt workbook lives in the "
            "system-configuration bucket and must be configured per environment "
            "(e.g. dev-system-configuration)."
        )
    s3 = _s3_client()
    bucket, key = config.PROMPT_S3_BUCKET, config.PROMPT_S3_KEY
    backup_key = _backup_key(key)

    # 1. Back up the existing object (copy -> timestamped key). Skip if absent.
    backed_up = None
    try:
        s3.head_object(Bucket=bucket, Key=key)
        s3.copy_object(
            Bucket=bucket, Key=backup_key,
            CopySource={"Bucket": bucket, "Key": key},
        )
        backed_up = backup_key
    except s3.exceptions.ClientError as exc:
        code = exc.response.get("Error", {}).get("Code")
        if code not in ("404", "NoSuchKey", "NotFound"):
            raise  # a real error (perms, etc.) — don't silently overwrite.

    # 2. Replace the canonical object with the new workbook.
    s3.put_object(
        Bucket=bucket, Key=key, Body=data,
        ContentType=(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ),
    )
    # MD5 of what was just written — used by the checksum endpoint to confirm
    # the upload is consistent with the server cache after the subsequent reload.
    uploaded_md5 = hashlib.md5(data).hexdigest()
    logger.info(
        "prompt_store.s3_upload",
        extra={"bucket": bucket, "key": key, "backup_key": backed_up, "md5": uploaded_md5},
    )
    return {"bucket": bucket, "key": key, "backup_key": backed_up, "md5": uploaded_md5}
