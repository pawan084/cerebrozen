"""Read-only Mongo access for org-level data (Extract3: client values).

Client *values* live in the `org` collection (NOT the vector store / S3), so this
is a deterministic DB read — a data tool, no LLM. Mirrors stores/mongo.py: it
reuses the same pooled client and never raises (returns an empty result when
Mongo is unset/unreachable or the org has no values), so the graph degrades.

NOTE: the exact field mapping is provisional until a sample `org` document is
supplied. `read_org_values` probes the common shapes and is the single place to
pin once the real schema is known — search for FIELD-MAPPING-TODO.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, List, Optional

from app import config
from app.stores import mongo as _mongo_seam  # late-bound: get_client is THE
# patchable Mongo/Postgres seam; binding the function at import time would
# freeze whatever stood there when THIS module first loaded (see conftest).

logger = logging.getLogger("cerebrozen.stores")


def _to_object_id(value: str) -> Optional[Any]:
    try:
        from bson import ObjectId

        return ObjectId(value)
    except Exception:  # noqa: BLE001
        return None


def _find_org_doc(db, org_id: str) -> Optional[Dict[str, Any]]:
    """Locate the org document by the most likely keys (string orgId or _id)."""
    coll = db[config.MONGO_ORG_COLLECTION]
    # Most schemas key on a string org id field; fall back to _id ObjectId.
    for query in ({"orgId": org_id}, {"org_id": org_id}, {"_id": org_id}):
        doc = coll.find_one(query)
        if doc:
            return doc
    oid = _to_object_id(org_id)
    if oid is not None:
        return coll.find_one({"_id": oid})
    return None


def _extract_values(doc: Dict[str, Any]) -> List[Dict[str, str]]:
    """Pull a list of {name, description} from whatever shape the org doc uses.

    FIELD-MAPPING-TODO: replace this probing with the exact field once a sample
    `org` document is provided. Handles the common shapes defensively for now:
      - doc["values"] = [{"name","description"}, ...]
      - doc["values"] = ["Integrity", "Ownership", ...]
      - doc["coreValues"] / doc["organizationValues"] = same
    """
    raw = (
        doc.get("values")
        or doc.get("coreValues")
        or doc.get("organizationValues")
        or doc.get("orgValues")
        or []
    )
    out: List[Dict[str, str]] = []
    if isinstance(raw, dict):
        raw = [{"name": k, "description": v} for k, v in raw.items()]
    for item in raw or []:
        if isinstance(item, str):
            out.append({"name": item.strip(), "description": ""})
        elif isinstance(item, dict):
            name = str(item.get("name") or item.get("value") or item.get("title") or "").strip()
            desc = str(item.get("description") or item.get("desc") or item.get("detail") or "").strip()
            if name:
                out.append({"name": name, "description": desc})
    return out


def read_org_values(org_id: str) -> Dict[str, Any]:
    """Return {"values": [{name, description}...], "source_link": str} for an org.

    Empty `values` => Extract3 resolves to NULL. Never raises."""
    client = _mongo_seam.get_client()
    if client is None or not org_id:
        return {"values": [], "source_link": ""}
    try:
        db = client[config.MONGO_BACKEND_DB]
        doc = _find_org_doc(db, org_id)
        if not doc:
            logger.info("org.values_not_found", extra={"org_id": org_id})
            return {"values": [], "source_link": ""}
        values = _extract_values(doc)
        source_link = str(
            doc.get("valuesSourceLink") or doc.get("source_link") or ""
        ).strip()
        logger.info(
            "org.values_read", extra={"org_id": org_id, "count": len(values)}
        )
        return {"values": values, "source_link": source_link}
    except Exception as exc:  # noqa: BLE001
        logger.warning("org.values_read_failed", extra={"org_id": org_id, "error": str(exc)})
        return {"values": [], "source_link": ""}
