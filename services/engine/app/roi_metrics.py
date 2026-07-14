"""ROI-metric ("Development Area") catalogue — sourced from the editable prompt.

The ``dynamic_actions_insights_agent`` prompt owns the canonical ROI list (its
"ROI Metric Mapping" JSON block); the agent assigns each action exactly one metric
from it. We parse that SAME list at runtime so the UI picker (turn payload +
actions-insights) always matches what the agent emits, and non-technical editors
keep a single source of truth in the workbook (constitution: prompts editable by
non-technical users; graph structure is code, prompt content is not).

Falls back to a bundled default (``config.ROI_METRICS``) when the prompt is missing
or unparseable, so a prompt edit can never take the picker down. Not cached — the
prompt registry can hot-reload from S3, and the parse is a cheap regex over a
string we already hold in memory.
"""

from __future__ import annotations

import json
import logging
import re
from typing import Dict, List, Optional

from app import config

logger = logging.getLogger("cerebrozen.roi")

ACTIONS_INSIGHTS_AGENT = "dynamic_actions_insights_agent"

# The list of ROI metrics, as it appears after the "ROI Metric Mapping" heading. TWO shapes
# are accepted, and the second one is the reason this module worked at all:
#
#   1. a JSON array          ["Delegation", "Resilience", ...]
#   2. a pipe-separated run  "Delegation" | "Resilience" | ...
#
# Only (1) was ever implemented. The LIVE prompt writes (2). So the regex never matched, the
# parse always returned [], and `get_roi_metrics()` silently fell back to the hardcoded
# `config.ROI_METRICS` — meaning this module's entire premise, that the workbook is the
# single source of truth and a non-engineer can edit the list, has been dead since it was
# written. Nothing LOOKED broken, because the hardcoded list happens to be identical to the
# prompt's today. The day an author edits the workbook, the agent would have started
# assigning metrics from the new list while the UI kept offering the old one — a mismatch
# with no error and no obvious cause.
_ROI_JSON_RE = re.compile(r'\[\s*"[^\]]*"\s*\]', re.DOTALL)
_ROI_PIPE_RE = re.compile(r'"[^"\n]+"(?:\s*\|\s*"[^"\n]+")+')


def _parse_from_prompt(text: str) -> List[str]:
    if not text:
        return []
    idx = text.find("ROI Metric Mapping")
    scope = text[idx:] if idx != -1 else text

    m = _ROI_JSON_RE.search(scope)
    if m:
        try:
            arr = json.loads(m.group(0))
            return [str(x).strip() for x in arr if str(x).strip()]
        except Exception:  # noqa: BLE001 — malformed block → try the pipe form
            pass

    m = _ROI_PIPE_RE.search(scope)
    if not m:
        return []
    return [s.strip() for s in re.findall(r'"([^"\n]+)"', m.group(0)) if s.strip()]


def get_roi_metrics() -> List[str]:
    """The canonical Development-Area list from the prompt; the bundled default
    (``config.ROI_METRICS``) when the prompt is missing/unparseable."""
    metrics: List[str] = []
    try:
        from app.graph.runtime import get_registry

        metrics = _parse_from_prompt(get_registry().get(ACTIONS_INSIGHTS_AGENT) or "")
    except Exception as exc:  # noqa: BLE001 — registry hiccup → fall back
        logger.warning("roi.parse_failed", extra={"error": str(exc)})
    if not metrics:
        return list(config.ROI_METRICS)
    return metrics


def canonical_roi_metric(value: object) -> Optional[str]:
    """Resolve a UI-supplied roi_metric to the catalogue's casing. None/empty →
    None; a known metric (any casing) → canonical; an unknown non-empty value
    passes through unchanged (lenient — never blocks a save)."""
    if value is None:
        return None
    v = str(value).strip()
    if not v:
        return None
    by_lower: Dict[str, str] = {m.lower(): m for m in get_roi_metrics()}
    return by_lower.get(v.lower(), v)


def canonical_roi_metrics(values: object) -> Optional[List[str]]:
    """Resolve a UI-supplied list of roi_metrics to canonical catalogue casing.

    Accepts None, a single string (coerced to a one-item list), or a list of
    strings. Empty/None entries are dropped. Returns None when nothing remains
    (lenient — never blocks a save)."""
    if values is None:
        return None
    if isinstance(values, str):
        values = [values]
    if not isinstance(values, list):
        return None
    result = [canonical_roi_metric(v) for v in values]
    result = [r for r in result if r is not None]
    return result if result else None
