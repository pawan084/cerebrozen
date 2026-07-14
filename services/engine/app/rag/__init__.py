"""In-process RAG layer (CSKB + SSKB as tools, not agents).

This package is the LangGraph rebuild's retrieval layer. It replaces the old
standalone Agno/AgentOS service: we keep the *library* pieces (a LanceDB vector
store + OpenAI embeddings + S3 ingestion) and drop the agent framework + its
HTTP `/ask` endpoint. Coaching nodes call `extract(...)` directly, exactly like
`profile_read()` — no LLM round-trip to route, no separate deploy.

Public surface:
  - `extract(extract_id, params)`  → one extraction's structured payload or NULL.
  - `PlaceholderResolver`          → unified, parallel find-&-replace of prompt
                                     placeholders ({userName} … and RAG tokens).
  - `get_registry()`               → the editable extraction registry (workbook).

Everything degrades gracefully: with no LanceDB/S3/Mongo configured (the dev
box), extractions return NULL and the graph runs unchanged.
"""

from __future__ import annotations

from app.rag.extractors import ExtractionResult, extract
from app.rag.placeholders import PlaceholderResolver
from app.rag.registry import get_registry

__all__ = [
    "extract",
    "ExtractionResult",
    "PlaceholderResolver",
    "get_registry",
]
