"""Seed the RAG index from a local directory — the no-S3 path, plus a demo corpus.

Production ingests SSKB/CSKB from S3 (``ingest/sskb.py``, ``ingest/cskb.py``). But a
dev box, an air-gapped deploy, or a first-run demo has no bucket — and an empty index
means the coach improvises instead of grounding in evidence. This walks a local
directory that mirrors the S3 taxonomy and ingests it with the *same* primitives
(``chunk_doc`` + ``embed_and_upsert``), so retrieval behaves identically:

    <root>/sskb/<subdir>/<file>            subdir → (source, item_type)   [app/rag/ingest/sskb._SSKB_DIR]
    <root>/cskb/<orgId>/<group>/<file>     group  → doc_type              [app/rag/ingest/cskb.classify_doc_type]

``RAG_SEED_DIR`` overrides the root (default: the bundled ``rag_seed/`` demo corpus).
Drop your real content in the same layout and it loads the same way.

Embedding still needs a provider (OpenAI or Ollama); with none, ``embed_and_upsert``
degrades and the index stays empty — the same honest failure as the S3 path.
"""

from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any, Dict, Optional

logger = logging.getLogger("cerebrozen.rag")

_ALLOWED = {"pdf", "docx", "pptx", "txt", "md", "xlsx"}


def default_seed_dir() -> Path:
    """The bundled demo corpus at services/engine/rag_seed."""
    return Path(__file__).resolve().parents[2] / "rag_seed"


def _resolve_root(root: Optional[str]) -> Path:
    return Path(root or os.environ.get("RAG_SEED_DIR") or default_seed_dir())


def _ext_ok(path: Path) -> bool:
    return path.suffix.lower().lstrip(".") in _ALLOWED


def seed_from_dir(root: Optional[str] = None) -> Dict[str, Any]:
    """Ingest a local corpus directory into the sskb/cskb tables. Returns a summary.

    Best-effort per file: one unreadable doc is logged and skipped, never fatal —
    a first-run demo must not die on a stray file.
    """
    from app.rag.ingest import chunk_doc, embed_and_upsert
    from app.rag.ingest.cskb import classify_doc_type
    from app.rag.ingest.sskb import _SSKB_DIR

    base = _resolve_root(root)
    if not base.is_dir():
        logger.info("rag.seed_skipped", extra={"reason": "no seed dir", "root": str(base)})
        return {"seeded": False, "reason": "no seed dir", "root": str(base), "sskb": 0, "cskb": 0}

    written = {"sskb": 0, "cskb": 0}

    # ── SSKB: <root>/sskb/<subdir>/<file> ────────────────────────────────────
    sskb_root = base / "sskb"
    for subdir, (source, item_type) in _SSKB_DIR.items():
        d = sskb_root / subdir
        if not d.is_dir() or subdir == "sskb_curated":
            continue  # curated is a structured .xlsx parse; out of scope for the demo seeder
        for f in sorted(p for p in d.iterdir() if p.is_file() and _ext_ok(p)):
            try:
                recs = chunk_doc(
                    str(f), id_prefix=f"sskb:{source}", local=True,
                    extra_fields={"kb": "sskb", "source": source, "item_type": item_type,
                                  "doc_key": str(f)},
                )
                written["sskb"] += embed_and_upsert("sskb", recs)
            except Exception:  # noqa: BLE001
                logger.exception("rag.seed_file_failed", extra={"path": str(f)})

    # ── CSKB: <root>/cskb/<orgId>/<group>/<file> ─────────────────────────────
    cskb_root = base / "cskb"
    if cskb_root.is_dir():
        for org_dir in sorted(p for p in cskb_root.iterdir() if p.is_dir()):
            org_id = org_dir.name
            for group_dir in sorted(p for p in org_dir.iterdir() if p.is_dir()):
                doc_type = classify_doc_type(group_dir.name)
                for f in sorted(p for p in group_dir.iterdir() if p.is_file() and _ext_ok(p)):
                    try:
                        recs = chunk_doc(
                            str(f), id_prefix=f"cskb:{org_id}", local=True,
                            extra_fields={"kb": "cskb", "org_id": org_id, "doc_type": doc_type,
                                          "source": group_dir.name, "doc_key": str(f)},
                        )
                        written["cskb"] += embed_and_upsert("cskb", recs)
                    except Exception:  # noqa: BLE001
                        logger.exception("rag.seed_file_failed", extra={"path": str(f)})

    logger.info("rag.seeded", extra={"root": str(base), **written})
    return {"seeded": True, "root": str(base), **written}
