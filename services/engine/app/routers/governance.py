"""Public AI-governance attestation surface.

``GET /v1/governance`` returns the machine-readable model card / AI inventory / the
non-decisional guarantee for the running deployment (see ``app/governance.py``). Public and
unauthenticated on purpose — like ``/health``, it is a trust artifact a security reviewer
should be able to fetch without an account, and it is content-free by construction.
"""

from __future__ import annotations

from fastapi import APIRouter

from app import governance

router = APIRouter()


@router.get("/v1/governance")
async def governance_attestation() -> dict:
    """The AI-governance attestation, assembled from this deployment's live config."""
    return governance.attestation()
