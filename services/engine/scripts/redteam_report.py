"""Crisis red-team report — the reproducible, buyer-facing scorecard.

    python -m scripts.redteam_report               # human-readable, to stdout
    python -m scripts.redteam_report --json         # machine-readable, to stdout
    python -m scripts.redteam_report --json > redteam.json

Why this exists. The Evidence page publishes "1 / 22" — the crisis lexicon's recall on
implicit disclosures. A number a buyer cannot reproduce is a claim, not evidence; the whole
CereBroZen posture is that every claim comes with a way to check it. This is that way: it
runs the SAME scenarios the CI gate runs (app/safety/redteam_scenarios.py) through the real
screen and prints the scorecard, every scenario marked caught or missed.

By default it scores the LEXICON alone — deterministic, offline, no model, no keys — which
is exactly the floor an air-gapped deployment runs on, and reproduces the 1/22. With
--full and a configured provider it also runs the shipped two-layer screen (lexicon +
classifier); that number depends on the live model, so it is reported as measured-now, not
pinned.

This is a report, not a test. The build-gating assertions (the pinned floor, the precision
side, the guard against claiming the red team passes) live in tests/test_crisis_redteam.py.
"""

from __future__ import annotations

import argparse
import json
import sys
from typing import Any, Dict, List

from app.graph.crisis import crisis_screen
from app.safety.redteam_scenarios import EXPLICIT, IMPLICIT, NOT_A_CRISIS


def _score(screen, cases: List) -> List[Dict[str, Any]]:
    """Run each case through ``screen`` and record caught/verdict. Cases are (tag, text)."""
    rows = []
    for tag, text in cases:
        verdict = screen(text)
        rows.append({"tag": tag, "text": text, "verdict": verdict,
                     "caught": verdict == "crisis"})
    return rows


def build_report(full: bool = False) -> Dict[str, Any]:
    """Assemble the scorecard. ``full`` additionally runs the two-layer shipped screen,
    which requires a configured provider."""
    explicit = _score(crisis_screen, EXPLICIT)
    implicit = _score(crisis_screen, IMPLICIT)
    precision = _score(crisis_screen, [("idiom", t) for t in NOT_A_CRISIS])

    report: Dict[str, Any] = {
        "spec": "cerebrozen.redteam/v1",
        "layer": "lexicon",  # what the numbers below reflect
        "explicit": {
            "caught": sum(r["caught"] for r in explicit),
            "total": len(explicit),
            "scenarios": explicit,
        },
        "implicit": {
            "caught": sum(r["caught"] for r in implicit),
            "total": len(implicit),
            "scenarios": implicit,
        },
        "precision": {
            # A false positive here is firing on ordinary workplace idiom.
            "false_positives": sum(r["caught"] for r in precision),
            "total": len(precision),
            "scenarios": precision,
        },
        "note": (
            "Lexicon alone. This is the offline floor an air-gapped deployment runs on. "
            "The shipped screen adds a classifier layer (see --full); its wiring, fail-safe "
            "and escalation are tested in tests/test_crisis_classifier.py."
        ),
    }

    if full:
        try:
            from app.graph.crisis import full_screen
            def _two_layer(text: str) -> str:
                return full_screen(text)[0]
            f_implicit = _score(_two_layer, IMPLICIT)
            report["full_screen"] = {
                "layer": "lexicon+classifier",
                "implicit_caught": sum(r["caught"] for r in f_implicit),
                "total": len(f_implicit),
                "measured_now": True,  # depends on the live model, not pinned
                "scenarios": f_implicit,
            }
        except Exception as exc:  # noqa: BLE001 — a missing provider must not crash the report
            report["full_screen"] = {"error": f"two-layer screen unavailable: {exc}"}

    return report


def _render_text(report: Dict[str, Any]) -> str:
    out: List[str] = []
    out.append("\n  CRISIS RED-TEAM SCORECARD")
    out.append("  " + "─" * 58)
    e, i, p = report["explicit"], report["implicit"], report["precision"]
    out.append(f"  Explicit disclosures ....... {e['caught']}/{e['total']} caught   (floor: must be 100%)")
    out.append(f"  Implicit disclosures ....... {i['caught']}/{i['total']} caught   (lexicon alone)")
    out.append(f"  Ordinary idiom (precision) . {p['false_positives']}/{p['total']} false positives")
    out.append("")
    for r in i["scenarios"]:
        mark = "caught " if r["caught"] else "MISSED "
        out.append(f"    {mark} [{r['tag']:<12}] {r['text']}")
    if "full_screen" in report:
        fs = report["full_screen"]
        out.append("")
        if "error" in fs:
            out.append(f"  Two-layer screen: {fs['error']}")
        else:
            out.append(f"  Two-layer (lexicon+classifier): {fs['implicit_caught']}/{fs['total']} "
                       "implicit caught (measured on the live model, not pinned)")
    out.append("")
    out.append("  " + report["note"])
    out.append("")
    return "\n".join(out)


def main(argv: List[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Crisis red-team reproducible scorecard.")
    ap.add_argument("--json", action="store_true", help="emit machine-readable JSON")
    ap.add_argument("--full", action="store_true",
                    help="also run the two-layer shipped screen (needs a provider)")
    args = ap.parse_args(argv)

    report = build_report(full=args.full)
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print(_render_text(report))
    return 0


if __name__ == "__main__":
    sys.exit(main())
