"""Extraction dispatch — `extract(extract_id, params)` → structured payload or NULL.

This is the in-process tool the coaching nodes call (via the placeholder resolver).
One turn may run several extractions in parallel; each:

  1. resolves its definition from the registry,
  2. checks its trigger condition (e.g. CSKB only if the org has content),
  3. builds the query from turn state + the metadata filters,
  4. retrieves from the right LanceDB table,
  5. either runs the cheap extraction LLM (needs_llm) or formats deterministically,
  6. returns an ExtractionResult — resolved (with a text block to substitute) or null,
  7. logs EVERYTHING to `cerebrozen.rag`: when, which RAG, query params, filters,
     candidate ids+scores+sources, llm/tokens, the payload, status, latency.

Everything degrades to NULL on any failure (no store, no rows, LLM error) so a
retrieval problem never breaks a coaching turn.
"""

from __future__ import annotations

import contextvars
import hashlib
import json
import logging
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

from app import config, trace
from app.rag import store
from app.rag.prompt import build_retrieval_prompt
from app.rag.registry import (
    COND_ORG_AVAILABLE,
    Extraction,
    SKIP_PHASE3,
    get_registry,
)
from app.request_context import request_id as _request_id_ctx

logger = logging.getLogger("cerebrozen.rag")


@dataclass
class ExtractionResult:
    extract_id: str
    kb: str
    status: str = "null"  # "resolved" | "null" | "error"
    fields: Dict[str, Any] = field(default_factory=dict)
    formatted: str = ""    # text substituted into the prompt placeholder
    source_link: str = ""
    used_llm: bool = False
    latency_ms: float = 0.0

    @property
    def is_resolved(self) -> bool:
        return self.status == "resolved"


# --- query + filter assembly -------------------------------------------------

# Aliases so registry query_params can use friendly names regardless of the exact
# state key the resolver passes in.
_PARAM_ALIASES = {
    "user_goal_challenge": ("user_goal_challenge", "user_challenge", "user_message"),
    "user_challenge": ("user_challenge", "user_goal_challenge", "user_message"),
    # session_goal covers both the raw goal/challenge (CIM/CBT) and the CH
    # "user stated outcome" captured by challenge_context_agent (context_update →
    # coaching_progress). Falls back to the raw challenge/message early in a turn
    # (before challenge_context_agent has run and set it).
    "session_goal": ("session_goal", "user_goal_challenge", "user_challenge", "user_message"),
    "conversation": ("conversation", "history_text"),
    # conversation_history is the canonical name (matches builders.py's naming);
    # falls back to the older "conversation"/"history_text" keys.
    "conversation_history": ("conversation_history", "conversation", "history_text"),
    "user_role": ("user_role", "userPosition", "role"),
    # userRoleContext: intake/profile field (Mongo alias user_role_context /
    # userRoleContext), falling back to the plain role if intake hasn't set it.
    "userRoleContext": ("userRoleContext", "user_role_context", "user_role", "userPosition", "role"),
    "user_level": ("user_level", "level"),
    "skill_to_develop": ("skill_to_develop", "competency", "skill"),
    # confirmed_competency: set by CH_coaching_agent once competency mapping is
    # done; falls back to skill_to_develop until then.
    "confirmed_competency": ("confirmed_competency", "skill_to_develop", "competency", "skill"),
    # coaching_shift_summary: no producer yet — no fallback. Drops silently from
    # the query (same graceful-degradation pattern as any other unset param) until
    # a future agent populates coaching_progress.coaching_shift_summary.
    "coaching_shift_summary": ("coaching_shift_summary",),
    "org_id": ("org_id", "orgId"),
}


def _param(params: Dict[str, Any], name: str) -> str:
    for key in _PARAM_ALIASES.get(name, (name,)):
        val = params.get(key)
        if val:
            return val if isinstance(val, str) else str(val)
    return ""


def _build_query(ex: Extraction, params: Dict[str, Any]) -> str:
    parts = [_param(params, p) for p in ex.query_params]
    return "\n".join(p for p in parts if p).strip()


def _build_filters(ex: Extraction, params: Dict[str, Any]) -> Dict[str, Any]:
    """Resolve the registry filter spec ({col: state-field | "_const:value"})."""
    out: Dict[str, Any] = {}
    for col, spec in ex.filters.items():
        if isinstance(spec, str) and spec.startswith("_const:"):
            out[col] = spec.split(":", 1)[1]
        else:
            val = _param(params, spec)
            if val:
                out[col] = val
    return out


def _cache_key(ex: Extraction, params: Dict[str, Any]) -> str:
    """Stable cache key for an extraction's RESULT — its resolved query + filters +
    org. Identical inputs ⇒ identical retrieval ⇒ same key (cache hit); a different
    query (e.g. a query built from the current message) ⇒ different key (re-fetch).
    Org-scoped so one tenant's cache can never serve another's (Art. 12.3)."""
    query = _build_query(ex, params)
    filters = _build_filters(ex, params)
    org = _param(params, "org_id")
    raw = f"{ex.extract_id}|{org}|{query}|{json.dumps(filters, sort_keys=True, default=str)}"
    return "cerebrozen:rag:" + hashlib.sha1(raw.encode("utf-8")).hexdigest()


# --- cheap extraction LLM (isolated from the coaching breaker) ---------------


def _llm_extract(ex: Extraction, query: str, candidates: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    """Run the cheap RAG-tier model to select/extract structured fields.

    Returns the parsed fields dict, {} for an explicit null, or None on error.
    Uses the OpenAI client directly (NOT the coaching resilience layer) so a RAG
    failure can never trip the coaching circuit breaker."""
    from app.graph.tools import _safe_json  # tolerant JSON parse (shared)
    from app.rag.embedder import _client  # reuse the cached OpenAI client

    prompt = build_retrieval_prompt(ex, query, candidates)
    try:
        resp = _client().responses.create(
            model=config.RAG_LLM_MODEL,
            input=[{"role": "user", "content": prompt}],
        )
        obj = _safe_json(resp.output_text)
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.llm_extract_failed", extra={"extract_id": ex.extract_id, "error": str(exc)})
        return None
    if not obj:
        return None
    if str(obj.get("status", "")).lower() == "null":
        return {}
    fields = {f: obj.get(f, "") for f in ex.output_fields if f != "source_link"}
    if "source_link" in ex.output_fields:
        # Attribute the link to the passage the LLM actually drew the content
        # from (from_passage), never a string it copied itself — this is the
        # only way to guarantee the link can't mismatch the extracted text when
        # several candidate passages (different docs) are shown in one call.
        # Mirrors _extract_values' from_passage attribution below.
        try:
            fp = int(obj.get("from_passage"))
        except (TypeError, ValueError):
            fp = None
        src_hit = candidates[fp - 1] if fp and 1 <= fp <= len(candidates) else candidates[0]
        fields["source_link"] = src_hit.get("source_link", "")
    return fields


def _llm_select_index(ex: Extraction, query: str, candidates: List[Dict[str, Any]]) -> Optional[int]:
    """Ask the cheap model to pick the SINGLE best-fit candidate (by number) for the
    user's situation. Returns a 0-based index, or None for 'no fit'/error.

    Used where the output fields are stored metadata (concepts, learning aids): the
    LLM only chooses *which* item; the fields are then mapped verbatim from that
    item — so item_type/links/wording are always correct (no freehand hallucination).
    """
    from app.graph.tools import _safe_json
    from app.rag.embedder import _client

    lines = []
    for i, c in enumerate(candidates, 1):
        meta = c.get("meta") or {}
        label = c.get("title") or meta.get("concept_name") or ""
        snippet = (c.get("text") or "")[:240].replace("\n", " ")
        lines.append(f"{i}. {label} — {snippet}")
    prompt = (
        "Select the SINGLE most relevant item for the user's situation. Choose the "
        "one that best fits what they actually need help with.\n\n"
        f"USER SITUATION:\n{query.strip() or '(none)'}\n\n"
        f"ITEMS:\n" + "\n".join(lines) + "\n\n"
        'Reply with STRICT JSON only: {"choice": <item number>} — or {"status": "null"} '
        "if none are relevant."
    )
    try:
        resp = _client().responses.create(
            model=config.RAG_LLM_MODEL, input=[{"role": "user", "content": prompt}]
        )
        obj = _safe_json(resp.output_text)
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.llm_select_failed", extra={"extract_id": ex.extract_id, "error": str(exc)})
        return None
    if not obj or str(obj.get("status", "")).lower() == "null":
        return None
    try:
        idx = int(obj.get("choice")) - 1
    except (TypeError, ValueError):
        return None
    return idx if 0 <= idx < len(candidates) else None


# --- formatting --------------------------------------------------------------


def _format_fields(ex: Extraction, fields: Dict[str, Any]) -> str:
    """Render structured fields into the text block substituted for the placeholder.

    Markdown-ish and readable since it lands inside a coaching prompt. Empty/blank
    fields are dropped; the source line only appears when a link is present."""
    lines: List[str] = []
    for key in ex.output_fields:
        val = fields.get(key)
        if val in (None, "", [], {}):
            continue
        label = key.replace("_", " ").title()
        if isinstance(val, list):
            rendered = "; ".join(
                f"{v.get('name')}: {v.get('description')}".strip(": ")
                if isinstance(v, dict) else str(v)
                for v in val
            )
        else:
            rendered = str(val)
        lines.append(f"{label}: {rendered}")
    return "\n".join(lines)


def _result(
    ex: Extraction,
    *,
    status: str,
    fields: Optional[Dict[str, Any]] = None,
    used_llm: bool = False,
    started: float,
) -> ExtractionResult:
    fields = fields or {}
    formatted = _format_fields(ex, fields) if status == "resolved" else ex.null_text
    return ExtractionResult(
        extract_id=ex.extract_id,
        kb=ex.kb,
        status=status,
        fields=fields,
        formatted=formatted,
        source_link=str(fields.get("source_link", "") or ""),
        used_llm=used_llm,
        latency_ms=round((time.perf_counter() - started) * 1000, 1),
    )


# --- per-extraction handlers -------------------------------------------------


# Fixed semantic query for finding a values document in CSKB (Extract3 has no
# user-driven query — values are an org property, not a per-turn search).
_VALUES_QUERY = "organization core values, guiding principles and their descriptions"


def _llm_extract_values(hits: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    """Extract the FULL list of org values (name + description) from CSKB passages,
    verbatim, plus WHICH passage they came from (so the caller can attribute the
    source link correctly). Returns {"values": [...], "from_passage": int|None} or
    None for none/error."""
    from app.graph.tools import _safe_json
    from app.rag.embedder import _client

    passages = [
        f"[{i}] (doc: {h.get('title') or '?'})\n{(h.get('text') or '').strip()}"
        for i, h in enumerate(hits, 1)
    ]
    prompt = (
        "Extract the organization's VALUES from the passages below. List EVERY value "
        "with its description, using ONLY the passages (verbatim wording). Do not invent.\n\n"
        "PASSAGES:\n" + "\n\n".join(passages) + "\n\n"
        'Reply with STRICT JSON only: '
        '{"status":"ok","values":[{"name":"","description":""}],"from_passage":<the passage '
        'number you drew the values from>} — or {"status":"null"} if the passages contain '
        "no organizational values."
    )
    try:
        resp = _client().responses.create(model=config.RAG_LLM_MODEL, input=[{"role": "user", "content": prompt}])
        obj = _safe_json(resp.output_text)
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.values_llm_failed", extra={"error": str(exc)})
        return None
    if not obj or str(obj.get("status", "")).lower() == "null":
        return None
    values = obj.get("values") or []
    if not values:
        return None
    try:
        from_passage = int(obj.get("from_passage"))
    except (TypeError, ValueError):
        from_passage = None
    return {"values": values, "from_passage": from_passage}


def _extract_values(ex: Extraction, params: Dict[str, Any], started: float) -> ExtractionResult:
    """Extract3 — client values, in the order requested: CSKB docs → Mongo `org` → null.

    1. CSKB first (framework: Extract3 is a CSKB-RAG extraction): search the org's
       CSKB docs for a values doc and LLM-extract the full list.
    2. Fallback: the Mongo `org` collection (deterministic read).
    3. Neither has values → null (the prompt's skip behaviour)."""
    org_id = _param(params, "org_id")
    if not org_id:
        return _result(ex, status="null", started=started)

    # 1) CSKB docs (org-scoped; the values query naturally ranks the values doc top).
    hits: List[Dict[str, Any]] = []
    try:
        from app.rag.embedder import embed_one

        vec = embed_one(_VALUES_QUERY)
        if vec:
            hits = store.search(
                "cskb", vec, filters={"org_id": org_id, "doc_type": "values"}, top_k=ex.top_k
            )
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.values_cskb_failed", extra={"org_id": org_id, "error": str(exc)})
    _log_candidates(ex, _VALUES_QUERY, hits, params)
    if hits:
        extracted = _llm_extract_values(hits)
        if extracted:
            # Attribute the source link to the passage the values were actually
            # drawn from (set from the doc, never the LLM). Falls back to the top hit.
            fp = extracted.get("from_passage")
            src_hit = hits[fp - 1] if isinstance(fp, int) and 1 <= fp <= len(hits) else hits[0]
            fields = {"values": extracted["values"], "source_link": src_hit.get("source_link", "")}
            return _result(ex, status="resolved", fields=fields, used_llm=True, started=started)

    # 2) Mongo `org` fallback.
    from app.stores.org import read_org_values

    data = read_org_values(org_id)
    if data.get("values"):
        return _result(
            ex, status="resolved",
            fields={"values": data["values"], "source_link": data.get("source_link", "")},
            started=started,
        )

    # 3) Nothing anywhere.
    return _result(ex, status="null", started=started)


def _retrieve(ex: Extraction, params: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Embed the query and vector-search the kb table with the extraction's filters.

    Embedding failures (no API key, network) degrade to [] → the extraction
    resolves to NULL and the turn proceeds, rather than erroring."""
    from app.rag.embedder import embed_one

    query = _build_query(ex, params)
    if not query:
        return []
    try:
        vector = embed_one(query)
    except Exception as exc:  # noqa: BLE001
        logger.warning("rag.embed_failed", extra={"extract_id": ex.extract_id, "error": str(exc)})
        return []
    if not vector:
        return []
    return store.search(ex.kb, vector, filters=_build_filters(ex, params), top_k=ex.top_k)


def _extract_vector(ex: Extraction, params: Dict[str, Any], started: float) -> ExtractionResult:
    """Generic retrieval. Three modes:
      - needs_llm + output is stored metadata (has a deterministic map): the LLM
        SELECTS the best candidate, fields are mapped verbatim from it (no
        freehand hallucination of item_type/links/wording).
      - needs_llm + no map: the LLM extracts the fields from the chunk text
        (Extract2/5 — knowledge snippets that aren't pre-stored columns).
      - deterministic: top-1 by vector score, fields mapped from columns/meta.
    """
    query = _build_query(ex, params)
    hits = _retrieve(ex, params)
    _log_candidates(ex, query, hits, params)
    if not hits:
        return _result(ex, status="null", started=started)

    has_map = ex.extract_id in _DETERMINISTIC_MAP
    if ex.needs_llm and has_map:
        idx = _llm_select_index(ex, query, hits)
        if idx is None:
            return _result(ex, status="null", started=started)
        return _result(ex, status="resolved", fields=_map_top_hit(ex, hits[idx]), used_llm=True, started=started)
    if ex.needs_llm:
        fields = _llm_extract(ex, query, hits)
        if not fields:  # None (error) or {} (explicit null)
            return _result(ex, status="null", started=started)
        return _result(ex, status="resolved", fields=fields, used_llm=True, started=started)

    # Deterministic: map the top hit's columns/meta onto the output fields.
    return _result(ex, status="resolved", fields=_map_top_hit(ex, hits[0]), started=started)


# Deterministic field -> hit-source mapping for non-LLM extractions. "meta:" reads
# the JSON meta blob; otherwise a scalar column; a fixed default closes the rest.
# Extract1 (concepts) is NOT here: sskb_concept/ is a chunked PDF now, not atomic
# concept rows with concept_name/concept_description metadata, so it always goes
# through the needs_llm branch (_llm_extract), same as Extract2.
_DETERMINISTIC_MAP: Dict[str, Dict[str, str]] = {
    "Extract7": {  # curated content (content-library tab, when it exists)
        "heading": "title",
        "sub_heading": "meta:synopsis",
        "author": "author",
        "source_link": "source_link",
        "content_format": "content_format",
    },
}


def _map_top_hit(ex: Extraction, hit: Dict[str, Any]) -> Dict[str, Any]:
    spec = _DETERMINISTIC_MAP.get(ex.extract_id)
    if not spec:
        # Fallback: pass through any output field that matches a hit column.
        return {f: hit.get(f, "") for f in ex.output_fields}
    out: Dict[str, Any] = {}
    for field_name, source in spec.items():
        if source.startswith("meta:"):
            out[field_name] = (hit.get("meta") or {}).get(source.split(":", 1)[1], "")
        else:
            out[field_name] = hit.get(source, "")
    return out


def _extract_learning_aid(ex: Extraction, params: Dict[str, Any], started: float) -> ExtractionResult:
    """LearningAidSelect — gather candidates from micro (6) + curated (7) [+ client
    aids (5) if the org has CSKB] and select ONE for the learning_aid_agent.

    This is the single point where "which aid" is decided; the learning_aid_agent
    then honours this selection and never re-queries."""
    reg = get_registry()
    # Eligible sub-extractions (skip disabled / org-gated when no org).
    subs = []
    for sub_id in ("Extract6", "Extract7", "Extract5"):
        sub = reg.by_id(sub_id)
        if not sub or not sub.enabled:
            continue
        if sub.condition == COND_ORG_AVAILABLE and not _param(params, "org_id"):
            continue
        subs.append((sub_id, sub))

    # Retrieve the sources CONCURRENTLY (each is an embedding + vector search) instead
    # of sequentially — the heaviest RAG op on a turn, so this slims its first-fetch
    # latency ~Nx. (Repeat fetches are already free via the extract() result cache.)
    pool: List[Dict[str, Any]] = []
    if subs:
        # copy_context()/ctx.run() so worker threads see the same ContextVars
        # (request_id etc.) as the caller — see placeholders.py's _resolve_all
        # for the same pattern and why it's needed. A FRESH copy per task, not one
        # shared Context — Context.run() is not reentrant and raises RuntimeError
        # if the same Context object is entered concurrently from >1 thread, which
        # silently drops a sub-source here (caught by the except below as hits=[]).
        with ThreadPoolExecutor(max_workers=len(subs)) as pool_ex:
            futs = {
                pool_ex.submit(contextvars.copy_context().run, _retrieve, sub, params): sid
                for sid, sub in subs
            }
            for fut, sid in futs.items():
                try:
                    hits = fut.result()
                except Exception:  # noqa: BLE001 — one source failing shouldn't sink the rest
                    hits = []
                for hit in hits:
                    hit["_origin"] = sid
                    pool.append(hit)
    query = _build_query(ex, params)
    _log_candidates(ex, query, pool, params)
    if not pool:
        return _result(ex, status="null", started=started)

    # The LLM only SELECTS which item; fields are mapped verbatim from it — so
    # item_type is correct ({micro_learning|curated_content|client_learning_aid}),
    # never the freehand "article" the model previously emitted.
    idx = _llm_select_index(ex, query, pool)
    if idx is None:  # fallback: best vector score across the pool
        idx = min(range(len(pool)), key=lambda i: pool[i].get("_score") if pool[i].get("_score") is not None else 1e9)
    chosen = pool[idx]
    fields = {
        "item_type": _aid_item_type(chosen),
        "item_title": chosen.get("title", ""),
        "retrieved_item": chosen.get("text", ""),
        "source_link": chosen.get("source_link", ""),
        "content_format": chosen.get("content_format", ""),
    }
    return _result(ex, status="resolved", fields=fields, used_llm=True, started=started)


def _aid_item_type(hit: Dict[str, Any]) -> str:
    """Canonical learning-aid item_type from the chosen candidate's origin/metadata."""
    origin = hit.get("_origin")
    if origin == "Extract6":
        return "micro_learning"
    if origin == "Extract7":
        return "curated_content"
    if origin == "Extract5":
        return "client_learning_aid"
    return hit.get("item_type") or "curated_content"


# --- logging -----------------------------------------------------------------


def _log_candidates(
    ex: Extraction, query: str, hits: List[Dict[str, Any]], params: Dict[str, Any]
) -> None:
    """One structured line capturing what the vector search returned (ids, scores,
    sources) — attributed to the invoking agent."""
    logger.info(
        "rag.retrieved",
        extra={
            "invoking_agent": params.get("invoking_agent", ""),
            "extract_id": ex.extract_id,
            "kb": ex.kb,
            "query_chars": len(query),
            "user_id": params.get("user_id", ""),
            "session_id": params.get("session_id", ""),
            "request_id": _request_id_ctx.get(""),
            "candidates": [
                {"id": h.get("id"), "title": h.get("title", ""), "score": h.get("_score"),
                 "source_link": h.get("source_link", "")}
                for h in hits
            ],
        },
    )
    trace.io("rag.candidates", extract_id=ex.extract_id, query=query, hits=hits)


# --- public entrypoint -------------------------------------------------------

_HANDLERS = {
    "Extract3": _extract_values,
    "LearningAidSelect": _extract_learning_aid,
    # Extract4 (competencies) routes through the generic CSKB path (_extract_vector):
    # best-effort LLM extraction, org-scoped; null until a competency doc exists.
}


def build_rag_params(
    user_id: str = "",
    user_message: str = "",
    *,
    conversation: str = "",
    org_id: str = "",
    extra: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    """Assemble the flat params dict an extraction reads, resolving the user's
    orgId/level/role from `profile_read` (the `users` collection) when not given.

    Reusable by the graph and the test harness: pass a user_id and the orgId is
    discovered automatically (org-scoping for CSKB), with profile fields mapped to
    the names the registry queries use (orgId→org_id, level→user_level,
    userPosition→user_role)."""
    from app.graph.tools import profile_read

    ctx: Dict[str, Any] = profile_read(user_id) if user_id else {}
    params: Dict[str, Any] = dict(ctx)
    params["user_id"] = user_id
    params["user_message"] = user_message
    params["conversation"] = conversation or params.get("conversation", "")
    params["org_id"] = str(org_id or ctx.get("orgId") or ctx.get("org_id") or "")
    params["user_level"] = ctx.get("level") or ctx.get("user_level") or ""
    params["user_role"] = ctx.get("userPosition") or ctx.get("user_role") or ""
    params.setdefault("user_goal_challenge", user_message)
    params.setdefault("user_challenge", user_message)
    if extra:
        params.update({k: v for k, v in extra.items() if v not in (None, "")})
    return params


def extract(extract_id: str, params: Dict[str, Any]) -> ExtractionResult:
    """Run one extraction by id. `params` is a flat dict of turn-state fields
    (org_id, user_level, user_role, user_message/challenge, conversation,
    skill_to_develop, …). Never raises — returns a null/error result instead."""
    started = time.perf_counter()
    reg = get_registry()
    ex = reg.by_id(extract_id)
    if ex is None or not ex.enabled:
        logger.info(
            "rag.extract_unknown",
            extra={
                "extract_id": extract_id,
                "user_id": params.get("user_id", ""),
                "session_id": params.get("session_id", ""),
                "request_id": _request_id_ctx.get(""),
            },
        )
        return ExtractionResult(extract_id=extract_id, kb="", status="null")

    # Cache lookup (Phase 9/voice latency): a hit skips embedding + vector search
    # (+ the cheap RAG LLM) entirely. Keyed by org + extraction + resolved query, so
    # repeated retrievals for a user/org (values, competencies, learning aids) load
    # from cache; query-varying retrievals re-fetch correctly. Off when TTL=0.
    cache_key = ""
    if config.RAG_CACHE_TTL_S:
        try:
            from app.stores.redis_state import cache_get_json

            cache_key = _cache_key(ex, params)
            hit = cache_get_json(cache_key)
            if hit is not None:
                _cache_latency = round((time.perf_counter() - started) * 1000, 1)
                logger.info(
                    "rag.extract_cache_hit",
                    extra={"extract_id": extract_id, "kb": ex.kb,
                           "status": hit.get("status"),
                           "invoking_agent": params.get("invoking_agent", ""),
                           "user_id": params.get("user_id", ""),
                           "session_id": params.get("session_id", ""),
                           # request_id is NEVER a key in `params` (it only ever lives in
                           # the request-scoped ContextVar) — params.get() here was
                           # always "". Read it the same way the main rag.extract log does.
                           "request_id": _request_id_ctx.get(""),
                           "org_id": _param(params, "org_id"),
                           "query": _build_query(ex, params)[:500],
                           "token": f"{{{ex.placeholder}}}",
                           # OUTPUT: the cached fields, so a cache hit is just as
                           # traceable as a live extraction.
                           "output_fields": hit.get("fields", {}),
                           "source_link": hit.get("source_link", ""),
                           "latency_ms": _cache_latency},
                )
                return ExtractionResult(
                    extract_id=extract_id, kb=hit.get("kb", ex.kb),
                    status=hit.get("status", "null"), fields=hit.get("fields", {}),
                    formatted=hit.get("formatted", ""),
                    source_link=hit.get("source_link", ""),
                    latency_ms=_cache_latency,
                )
        except Exception:  # noqa: BLE001 — cache must never break a retrieval
            cache_key = ""

    invoked_at = time.time()
    try:
        # Condition gate: CSKB extractions only run when the org is known.
        if ex.condition == COND_ORG_AVAILABLE and not _param(params, "org_id"):
            result = _result(ex, status="null", started=started)
        else:
            handler = _HANDLERS.get(extract_id, _extract_vector)
            result = handler(ex, params, started)
    except Exception as exc:  # noqa: BLE001 — retrieval must never break a turn
        logger.exception("rag.extract_error", extra={"extract_id": extract_id})
        result = ExtractionResult(
            extract_id=extract_id, kb=ex.kb, status="error",
            latency_ms=round((time.perf_counter() - started) * 1000, 1),
        )

    logger.info(
        "rag.extract",
        extra={
            # WHO invoked it + WHEN.
            "invoking_agent": params.get("invoking_agent", ""),
            "extract_id": extract_id,
            "kb": ex.kb,
            "invoked_at": invoked_at,
            "latency_ms": result.latency_ms,
            "status": result.status,
            "used_llm": result.used_llm,
            "has_source": bool(result.source_link),
            # INPUT: the params that drove the query + the resolved query/filters.
            "query": _build_query(ex, params)[:500],
            "query_params": ex.query_params,
            "filters": _build_filters(ex, params),
            "org_id": _param(params, "org_id"),
            "user_id": params.get("user_id", ""),
            "session_id": params.get("session_id", ""),
            "request_id": _request_id_ctx.get(""),
            # OUTPUT: the structured fields returned + the source link.
            "output_fields": result.fields,
            "source_link": result.source_link,
        },
    )
    trace.io(
        "rag.result",
        invoking_agent=params.get("invoking_agent", ""),
        extract_id=extract_id,
        status=result.status,
        query=_build_query(ex, params),
        fields=result.fields,
        formatted=result.formatted,
    )

    # Cache the result (not transient errors) so the next identical retrieval is free.
    if cache_key and result.status != "error":
        try:
            from app.stores.redis_state import cache_set_json

            cache_set_json(
                cache_key,
                {"kb": result.kb, "status": result.status, "fields": result.fields,
                 "formatted": result.formatted, "source_link": result.source_link},
                config.RAG_CACHE_TTL_S,
            )
        except Exception:  # noqa: BLE001 — caching is best-effort
            pass
    return result
