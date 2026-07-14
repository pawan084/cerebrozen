"""OpenAI text embeddings for the RAG store.

A thin, lazily-initialised wrapper around the OpenAI embeddings endpoint. Kept
separate from `responses_client` because embeddings are a different endpoint and
must never share the coaching model's resilience/breaker state — a RAG hiccup
must not trip the coaching circuit breaker.

Same model id as the old Agno service (`text-embedding-3-small`) so vectors are
comparable across the migration.
"""

from __future__ import annotations

import logging
import os
from functools import lru_cache
from typing import List

from app import config

logger = logging.getLogger("cerebrozen.rag")


@lru_cache(maxsize=1)
def _client():
    """Cached OpenAI client. Imported lazily so importing the rag package never
    requires the OpenAI SDK / an API key (e.g. AST-only tooling, tests)."""
    from openai import OpenAI

    # Short timeout, no SDK-level retries: retrieval runs in the pre-step and must
    # fail fast rather than stall a turn. The caller treats failure as NULL.
    return OpenAI(timeout=config.OPENAI_TIMEOUT, max_retries=1)


def _ollama_embed(texts: List[str]) -> List[List[float]]:
    """Offline embeddings via Ollama (`CEREBROZEN_EMBED_PROVIDER=ollama`).

    Two things to know before you turn this on:

    1. **The Ollama server must be started with embeddings enabled.** A default server
       answers `/api/embed` with "This server does not support embeddings. Start it with
       `--embeddings`" — for EVERY model, including chat models. This is a server flag,
       not a model choice.
    2. **Vectors are NOT comparable across models.** text-embedding-3-small is 1536-dim;
       nomic-embed-text is 768. Switching the embedder means RE-INGESTING the whole
       knowledge base — a query vector from one model cannot search an index built with
       another. There is no migration; there is only a re-index.
    """
    import httpx

    host = os.environ.get("OLLAMA_HOST", "http://localhost:11434").rstrip("/")
    model = os.environ.get("CEREBROZEN_EMBED_MODEL", "nomic-embed-text")
    r = httpx.post(f"{host}/api/embed", json={"model": model, "input": texts},
                   timeout=float(os.environ.get("CEREBROZEN_EMBED_TIMEOUT", "60")))
    r.raise_for_status()
    data = r.json()
    if "embeddings" not in data:
        raise RuntimeError(f"ollama embed failed: {str(data)[:160]}")
    return data["embeddings"]


def embed(texts: List[str]) -> List[List[float]]:
    """Embed a batch of texts. Returns one vector per input (same order).

    Raises on failure — callers (ingestion / search) decide how to degrade. Empty
    input returns []."""
    cleaned = [(t or " ").replace("\n", " ").strip() or " " for t in texts]
    if not cleaned:
        return []
    if os.environ.get("CEREBROZEN_EMBED_PROVIDER", "openai").strip().lower() == "ollama":
        return _ollama_embed(cleaned)
    resp = _client().embeddings.create(model=config.RAG_EMBED_MODEL, input=cleaned)
    return [d.embedding for d in resp.data]


def embed_one(text: str) -> List[float]:
    """Embed a single query string."""
    vectors = embed([text])
    return vectors[0] if vectors else []


@lru_cache(maxsize=1)
def embedding_dim() -> int:
    if os.environ.get("CEREBROZEN_EMBED_PROVIDER", "openai").strip().lower() == "ollama":
        # Dimensions differ per model (nomic-embed-text 768, mxbai-embed-large 1024,
        # bge-m3 1024). Probe once rather than hardcode a table that will rot.
        return int(os.environ.get("CEREBROZEN_EMBED_DIM", "0")) or len(_ollama_embed(["x"])[0])
    """Dimensionality of the configured embedding model (table schema needs it).

    Known dims are hard-coded so table creation never costs an API call; an
    unknown model falls back to a probe embedding."""
    known = {
        "text-embedding-3-small": 1536,
        "text-embedding-3-large": 3072,
        "text-embedding-ada-002": 1536,
    }
    if config.RAG_EMBED_MODEL in known:
        return known[config.RAG_EMBED_MODEL]
    vec = embed_one("dimension probe")
    return len(vec) or 1536
