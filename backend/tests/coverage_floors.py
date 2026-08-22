#!/usr/bin/env python
"""Per-module coverage floors (WC-285).

    pytest -q --cov=app --cov-report=json:coverage.json ...
    python tests/coverage_floors.py

The global gate (`--cov-fail-under=95`) protects the AVERAGE, and an average is
exactly the wrong instrument for a critical path. This codebase is ~6,100
statements; `services/safety.py` is 63 of them. Deleting every test for the
keyword floor would move the global number by one percentage point — through a
gate set five points lower — while removing the thing that decides whether an
explicit self-harm phrase is seen.

That is what "diluted" means, and it gets easier every time the codebase grows.
So the modules where a silent regression costs most carry their own floor,
independent of everything around them.

**Floors are a ratchet, not an aspiration.** Each one is set at or just below
what the module actually had when the floor was written, so it can only ever be
lowered deliberately — in a diff, with a reason, which is a conversation. An
aspirational floor that has never been met is just a broken build people learn
to ignore.

Pairs with `tests/mutation/` (WC-277): coverage says the lines RAN, mutation
says the tests would NOTICE. Neither is sufficient and both are cheap.
"""

from __future__ import annotations

import io
import json
import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1]

# The container is UTF-8; this repo is developed on Windows, where stdout is
# cp1252 and a ✓ raises UnicodeEncodeError — turning "your floors held" into a
# traceback. Found by running the checker on the host after it passed in CI.
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

#: module → (floor %, why this module has a floor at all).
#:
#: Not "every module": a floor on a module nobody would notice regressing is
#: noise that trains people to edit this file. These are the ones where the
#: failure is silent — where wrong output looks exactly like right output.
FLOORS: dict[str, tuple[float, str]] = {
    "app/services/safety.py": (
        100.0,
        "Decides whether an explicit self-harm phrase is seen at all.",
    ),
    "app/services/crisis.py": (
        100.0,
        "Every line here ends with somebody dialling something.",
    ),
    "app/models/consent.py": (
        100.0,
        "A consent check that fails open is a DPDP breach that produces "
        "output indistinguishable from correct output.",
    ),
    "app/services/organizations.py": (
        95.0,
        "Small-cell suppression — the mechanism behind 'managers cannot see "
        "who used CereBro'.",
    ),
    "app/services/entitlements.py": (
        100.0,
        "Who has paid for what, and who is sponsored.",
    ),
    "app/services/playstore.py": (
        95.0,
        "Server-side receipt verification: the only thing between a forged "
        "purchase and a free premium account on Android.",
    ),
    "app/services/appstore.py": (
        90.0,
        "The same, for iOS. Lower because the certificate-chain paths need "
        "Apple's real roots to exercise fully.",
    ),
    "app/services/nudges.py": (
        95.0,
        "Delivery, retry and lateness — the difference between a nudge sent "
        "twice, sent late, or lost.",
    ),
    "app/services/errors.py": (
        100.0,
        "Decides what may leave the process about a failure. A gap here is a "
        "privacy leak nobody sees.",
    ),
}


def main() -> int:
    report = BACKEND / "coverage.json"
    if not report.exists():
        print(
            f"✗ {report} is missing. Run pytest with "
            "--cov-report=json:coverage.json first.",
            file=sys.stderr,
        )
        return 2

    data = json.loads(io.open(report, encoding="utf-8").read())
    files = data.get("files", {})
    problems: list[str] = []
    missing: list[str] = []
    ok: list[str] = []

    for module, (floor, why) in sorted(FLOORS.items()):
        entry = files.get(module) or files.get(module.replace("/", "\\"))
        if entry is None:
            # A floor naming a file that no longer exists is not a pass: it is a
            # floor protecting nothing, and it will sit there looking like cover.
            missing.append(module)
            continue
        percent = float(entry["summary"]["percent_covered"])
        if percent + 1e-9 < floor:
            problems.append(
                f"{module}: {percent:.1f}% is below its floor of {floor:.0f}%\n"
                f"    Why it has one: {why}"
            )
        else:
            ok.append(f"{module}: {percent:.1f}% (floor {floor:.0f}%)")

    if missing:
        print("✗ Coverage floors naming files that do not exist:\n", file=sys.stderr)
        for module in missing:
            print(f"    {module}", file=sys.stderr)
        print(
            "\n  Either the module moved and this list did not follow, or the floor\n"
            "  outlived its subject. Fix it in the same commit as the move.",
            file=sys.stderr,
        )
        return 1

    if problems:
        print("✗ Per-module coverage floors breached:\n", file=sys.stderr)
        for problem in problems:
            print("  " + problem + "\n", file=sys.stderr)
        print(
            "  The global gate protects an average, which is the wrong instrument\n"
            "  for a critical path: this module is a rounding error inside it.\n"
            "  Add the tests, or lower the floor deliberately — in this diff, with\n"
            "  a reason someone can disagree with.",
            file=sys.stderr,
        )
        return 1

    print(f"✓ All {len(ok)} module floors held.")
    for line in ok:
        print("  · " + line)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
