"""#32: a GOLDEN-FILE snapshot of the full crisis flow — detect → takeover reply →
helplines → escalation signal — for a fixed input. The whole safety path is deterministic
and load-bearing (CLAUDE.md rule 4/5): a person in crisis must get the same detection, the
same scripted reply, the same dialable numbers, and a signal-only escalation, every time.

This pins all four stages at once. If it fails, a change touched the crisis path — that is
allowed, but it must be a DELIBERATE act: review the diff, and if correct, regenerate the
golden (see _regenerate below) so the change is recorded, not slipped in.
"""

from __future__ import annotations

import json
from pathlib import Path

import httpx

from app.graph.crisis import full_screen, safe_response
from app.safety import escalation
from app.safety.helplines import for_region

_GOLDEN = Path(__file__).parent / "golden" / "crisis_flow_en_IN.json"


def _capture_escalation_signal(detected_by: str) -> dict:
    """Fire an escalation with a captured webhook and return the delivered payload's shape —
    the sorted key set plus the two invariant fields — never the varying ids/timestamp."""
    sent: dict = {}

    class _Resp:
        status_code = 200

        def raise_for_status(self):
            pass

    def _fake_post(url, json=None, **kw):  # noqa: A002 — mirrors httpx.post's kwarg
        sent["body"] = json
        return _Resp()

    original = httpx.post
    httpx.post = _fake_post
    try:
        import os

        os.environ["CEREBROZEN_CRISIS_ESCALATION_URL"] = "https://contact.example/hook"
        escalation.escalate(user_id="u-1", session_id="s-1", detected_by=detected_by)
    finally:
        httpx.post = original
        os.environ.pop("CEREBROZEN_CRISIS_ESCALATION_URL", None)
    body = sent["body"]
    return {"keys": sorted(body.keys()), "event": body["event"], "detected_by": body["detected_by"]}


def _run_flow(text: str, region: str) -> dict:
    flag, lang, by = full_screen(text)
    return {
        "input": {"text": text, "region": region},
        "detect": {"flag": flag, "lang": lang, "detected_by": by},
        "reply": safe_response(lang),
        "helplines": for_region(region),
        "escalation_signal": _capture_escalation_signal(by),
    }


def test_the_full_crisis_flow_matches_the_golden_snapshot(mongo):
    golden = json.loads(_GOLDEN.read_text())
    actual = _run_flow(golden["input"]["text"], golden["input"]["region"])
    assert actual == golden, (
        "the crisis flow drifted from tests/golden/crisis_flow_en_IN.json. This is the "
        "safety path — if the change is intended and reviewed, regenerate the golden; do "
        "not loosen this test."
    )


def test_the_golden_is_a_real_crisis_takeover_not_an_empty_shell():
    """Guard the guard: the golden itself must describe an actual takeover — a crisis flag,
    a non-empty reply carrying a helpline, dialable numbers, and a signal-only escalation."""
    golden = json.loads(_GOLDEN.read_text())
    assert golden["detect"]["flag"] == "crisis"
    assert golden["reply"].strip() and "findahelpline.com" in golden["reply"]
    assert golden["helplines"], "the crisis screen must never show an empty helpline list"
    assert all(h["target"] for h in golden["helplines"])
    # Signal-only: the escalation carries ids/layer/time, never a content field (rule 5).
    assert golden["escalation_signal"]["keys"] == [
        "at", "detected_by", "event", "org_id", "session_id", "user_id"
    ]
