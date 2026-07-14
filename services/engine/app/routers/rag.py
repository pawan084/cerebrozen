"""RAG admin/test HTTP surface (resource-named — there is no `/ask`).

In production the coaching nodes call the extraction layer in-process; these
endpoints exist only to exercise/inspect it:
  - POST /rag/extract  → run one extraction with explicit params (testing).
  - POST /rag/reload   → hot-reload the extraction registry after a workbook edit.
  - GET  /rag/health   → table row counts + bound placeholder tokens.

Behind /v1-style auth like the prompt admin router.
"""

from __future__ import annotations

import logging
from dataclasses import asdict
from typing import Any, Dict, Optional

from fastapi import APIRouter, Depends
from fastapi.concurrency import run_in_threadpool
from pydantic import BaseModel, Field

from app.auth import require_auth
from app.rag import store
from app.rag.extractors import extract
from app.rag.registry import get_registry, reload_registry

logger = logging.getLogger("cerebrozen.rag")
router = APIRouter()


class ExtractRequest(BaseModel):
    extract_id: str = Field(..., description="e.g. Extract6, Extract7, LearningAidSelect")
    params: Dict[str, Any] = Field(
        default_factory=dict,
        description="Turn-state fields: org_id, user_level, user_role, user_message, "
        "conversation, skill_to_develop, user_id, session_id …",
    )


@router.post("/rag/extract")
async def extract_endpoint(
    payload: ExtractRequest, _claims: dict = Depends(require_auth)
) -> dict:
    """Run one extraction and return its full result (status/fields/formatted/source)."""
    result = await run_in_threadpool(extract, payload.extract_id, payload.params)
    return asdict(result)


@router.post("/rag/reload")
async def reload_endpoint(_claims: dict = Depends(require_auth)) -> dict:
    """Re-read the `extractions` sheet from the workbook into the registry."""
    reg = await run_in_threadpool(reload_registry)
    return {"status": "reloaded", "tokens": reg.binding_tokens(), "count": len(reg.all())}


@router.get("/rag/health")
async def health_endpoint() -> dict:
    """Vector-table row counts + the currently-bound placeholder tokens."""
    reg = get_registry()
    return {
        "sskb_rows": await run_in_threadpool(store.count, "sskb"),
        "cskb_rows": await run_in_threadpool(store.count, "cskb"),
        "tokens": reg.binding_tokens(),
        "extractions": [e.extract_id for e in reg.all()],
    }
