"""Typed ingestion — turn each source item into one or more records with rich metadata.

Structured sources (the curated-content xlsx) still parse to ONE record per row.
Real documents (client PDFs/DOCX/PPTX, and — now that the SSKB sources are PDFs
too — the SSKB concept/micro-learning/competency docs) are chunked via
`chunk_doc()`. Every record carries the metadata the extractions filter on
(org_id, doc_type, source, content_format, source_link, …). Records are embedded
and upserted into the two LanceDB tables (sskb / cskb).

Shared helpers live here; `sskb.py` and `cskb.py` hold the per-source dispatch.
"""

from __future__ import annotations

import hashlib
import html
import logging
import re
import zipfile
from pathlib import PurePosixPath
from typing import Any, Dict, Iterator, List, Optional
from urllib.parse import quote

from app import config

logger = logging.getLogger("cerebrozen.rag")


def make_id(*parts: str) -> str:
    """Stable id from source parts (so re-ingestion upserts, not duplicates)."""
    raw = "|".join(p for p in parts if p)
    return hashlib.sha1(raw.encode("utf-8", "ignore")).hexdigest()[:24]


def detect_content_format(link: str) -> str:
    """Infer curated-content type from its URL (the content-library has no explicit
    type column). Video / audio / pdf / article."""
    low = (link or "").lower()
    if not low:
        return ""
    if "youtube.com" in low or "youtu.be" in low or "vimeo.com" in low:
        return "video"
    if "spotify.com" in low or "soundcloud.com" in low or "podcast" in low or "anchor.fm" in low:
        return "audio"
    if low.endswith(".pdf"):
        return "pdf"
    return "article"


def chunk_text(text: str, size: int = 1200, overlap: int = 150) -> List[str]:
    """Char-window chunker for long client docs (CSKB). SSKB items are not chunked
    — they're already atomic (one row / one bite)."""
    text = (text or "").strip()
    if not text:
        return []
    if len(text) <= size:
        return [text]
    chunks, start = [], 0
    while start < len(text):
        end = min(start + size, len(text))
        chunks.append(text[start:end])
        if end == len(text):
            break
        start = end - overlap
    return chunks


def extract_text(path: str, key: str) -> str:
    """Format-specific text extraction by the S3 key's extension. Shared by CSKB
    (client docs) and SSKB (concept/micro-learning/competency docs) — both are now
    real documents (PDF/DOCX/PPTX/TXT/MD), not structured spreadsheets."""
    low = key.lower()
    try:
        if low.endswith(".pdf"):
            from pypdf import PdfReader

            reader = PdfReader(path)
            return "\n".join((page.extract_text() or "") for page in reader.pages)
        if low.endswith(".docx"):
            z = zipfile.ZipFile(path)
            names = [n for n in z.namelist() if re.match(r"word/document\d*\.xml", n)]
            xml = z.read(names[0]).decode("utf-8", "ignore")
            xml = re.sub(r"</w:p>", "\n", xml)
            return html.unescape(re.sub(r"<[^>]+>", "", xml))
        if low.endswith(".pptx"):
            z = zipfile.ZipFile(path)
            # Slides in order (slide1, slide2, …); pull every text run <a:t>.
            slides = sorted(
                (n for n in z.namelist() if re.match(r"ppt/slides/slide\d+\.xml", n)),
                key=lambda n: int(re.search(r"slide(\d+)\.xml", n).group(1)),
            )
            out = []
            for n in slides:
                xml = z.read(n).decode("utf-8", "ignore")
                xml = re.sub(r"</a:p>", "\n", xml)  # paragraph breaks
                runs = re.findall(r"<a:t>(.*?)</a:t>", xml, re.S)
                if runs:
                    out.append(html.unescape(" ".join(runs)))
            return "\n".join(out)
        if low.endswith(".txt") or low.endswith(".md"):
            with open(path, "r", encoding="utf-8", errors="ignore") as fh:
                return fh.read()
    except Exception:  # noqa: BLE001
        logger.exception("rag.extract_text_failed", extra={"key": key})
    return ""


def chunk_doc(key: str, *, id_prefix: str, extra_fields: Dict[str, Any],
              local: bool = False) -> List[dict]:
    """Download `key`, extract its text, chunk it, and build one record per chunk:
    `id` (stable per key+chunk-index, so re-ingestion upserts), `text`,
    `source_link`, `title` (filename stem), plus whatever `extra_fields` the
    caller supplies (kb, org_id, doc_type, source, item_type, …).

    `local=True` means `key` is a path on THIS disk, not an S3 key — skip the download.
    Without it, `ingest_sskb_local` — the function whose entire purpose is "ingest from
    local files (no S3)" — handed local paths to `download_to_temp`, which called
    `s3.download_file(bucket, "/tmp/my.pdf")`. The one code path that exists so you can
    stand RAG up WITHOUT AWS credentials could not run without AWS credentials, which is a
    fair part of why nobody has ever seen this coach with its evidence base attached.
    """
    src = key if local else download_to_temp(key)
    text = extract_text(src, key)
    chunks = chunk_text(text)
    link = "" if local else public_url(key)
    title = PurePosixPath(PurePosixPath(key).name).stem
    records: List[dict] = []
    for idx, chunk in enumerate(chunks):
        rec = {
            "id": make_id(id_prefix, key, str(idx)),
            "text": chunk,
            "source_link": link,
            "title": title,
        }
        rec.update(extra_fields)
        records.append(rec)
    return records


# --- S3 helpers (shared with the prompt-store credential chain) --------------


def s3_client():
    import boto3

    return boto3.client("s3", region_name=config.AWS_REGION)


def iter_s3_objects(prefix: str) -> Iterator[tuple[str, str]]:
    """Yield (key, etag) for every object under s3://<RAG_S3_BUCKET>/<prefix>/.

    Skips folder markers AND anything under the LanceDB embeddings prefix, so the
    vector store's own files are never ingested as source documents. The ETag is
    the object's content hash — used to re-embed a doc only when it changes."""
    if not config.RAG_S3_BUCKET:
        logger.warning("rag.ingest_no_bucket")
        return
    embeddings_prefix = config.RAG_LANCEDB_PREFIX + "/"
    paginator = s3_client().get_paginator("list_objects_v2")
    for page in paginator.paginate(Bucket=config.RAG_S3_BUCKET, Prefix=prefix):
        for obj in page.get("Contents", []):
            key = obj["Key"]
            if key.endswith("/"):  # folder marker
                continue
            if key.startswith(embeddings_prefix):  # never ingest the vector store itself
                continue
            yield key, (obj.get("ETag") or "").strip('"')


def iter_s3_keys(prefix: str) -> Iterator[str]:
    """Keys only (back-compat wrapper over iter_s3_objects)."""
    for key, _etag in iter_s3_objects(prefix):
        yield key


def public_url(key: str) -> str:
    """Virtual-hosted public S3 URL for a key (CSKB source_link). Mirrors the old
    service's URL scheme so links match what was previously stored."""
    return f"https://{config.RAG_S3_BUCKET}.s3.{config.AWS_REGION}.amazonaws.com/{quote(key)}"


def download_to_temp(key: str) -> str:
    """Download an S3 object to a temp file; returns the local path."""
    import tempfile
    from pathlib import Path

    suffix = Path(key).suffix
    fd = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
    fd.close()
    s3_client().download_file(config.RAG_S3_BUCKET, key, fd.name)
    return fd.name


def embed_and_upsert(kb: str, records: List[dict]) -> int:
    """Embed each record's `text` and upsert into the kb table. Records missing
    text are skipped. Returns rows written."""
    from app.rag.embedder import embed
    from app.rag import store

    records = [r for r in records if (r.get("text") or "").strip()]
    if not records:
        return 0
    vectors = embed([r["text"] for r in records])
    for rec, vec in zip(records, vectors):
        rec["vector"] = vec
    return store.upsert(kb, records)
