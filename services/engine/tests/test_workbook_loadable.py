"""Workbook-loadability gate + eval-harness smoke — merge-path guards.

The real ``agent_prompts.xlsx`` *is* the behaviour of the product. A commit that
breaks its structure — a missing stage sheet, an enabled agent with an empty
prompt, an orphaned continuation row that would silently concatenate into the
next read — must fail CI here, not surface as a degraded ``/health`` in
production. Quality warnings (oversize prompts, unresolved placeholders) are the
separately-tracked prompt-shrink work and are tolerated; nothing structural is.

The routing-contract golden cases run nightly against a real model
(docs/ENGINEERING.md), off the merge path. What runs on *every* merge is the
guard that the harness executes and the cases are well-formed.
"""

import pytest

from app.graph.runtime import get_registry
from app.llm.prompts import ALWAYS_ENABLED, STAGE_SHEET

# Categories that mean the workbook is broken, not merely unpolished.
STRUCTURAL = [
    "missing_sheets", "not_in_catalog", "enabled_no_prompt",
    "enabled_no_model", "orphaned_continuation",
]


@pytest.fixture(scope="module")
def reg():
    return get_registry()


def test_the_real_workbook_loads_without_degrading(reg):
    assert reg.degraded is False, reg.degraded_reason


def test_there_are_no_structural_validation_issues(reg):
    problems = {k: reg.validation.get(k) for k in STRUCTURAL if reg.validation.get(k)}
    assert not problems, f"structural workbook issues (a broken commit): {problems}"


def test_every_reported_issue_is_a_known_quality_warning(reg):
    # issue_count must be explained entirely by oversize + unknown_placeholders
    # (the tracked prompt-shrink work). Anything else is a structural regression,
    # and this equality catches it without pinning brittle exact counts — as the
    # oversize prompts are shrunk, both sides fall together.
    v = reg.validation
    tolerated = len(v.get("oversize", {})) + len(v.get("unknown_placeholders", {}))
    assert v.get("issue_count", 0) == tolerated, v


def test_always_on_agents_are_present_and_enabled(reg):
    for stage in ALWAYS_ENABLED:
        assert reg.is_enabled(stage), f"{stage} must be always-on"
        assert reg.get(stage).strip(), f"{stage} loaded with no prompt"


def test_every_enabled_stage_sheet_resolves_to_text(reg):
    for stage in STAGE_SHEET:
        if reg.is_enabled(stage):
            assert reg.get(stage).strip(), f"enabled stage {stage} loaded empty"


# ── eval harness (runs nightly with a real model; smoked here on the mock) ────

def test_golden_cases_are_well_formed():
    from evals.cases import CASES

    assert len(CASES) >= 10, "the routing-contract suite should not silently shrink"
    ids = set()
    for c in CASES:
        assert c.get("stage"), c
        assert c.get("message"), c
        assert isinstance(c.get("expect"), dict) and c["expect"], c
        assert c["id"] not in ids, f"duplicate case id: {c['id']}"
        ids.add(c["id"])


def test_the_eval_harness_executes_on_the_mock_provider():
    # Guards the harness plumbing every merge: build_system_prompt + the provider
    # call + parse_control run for every case without raising, and a score comes
    # back. It does NOT assert routing accuracy — that needs a real model, nightly.
    from scripts.eval import run

    out = run("mock", 1)
    assert "score" in out and isinstance(out["results"], list) and out["results"]
