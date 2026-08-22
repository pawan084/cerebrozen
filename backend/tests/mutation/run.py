#!/usr/bin/env python
"""Apply each mutant, run the tests that should catch it, restore, report (WC-277).

    docker compose run --rm api python tests/mutation/run.py
    docker compose run --rm api python tests/mutation/run.py S1 E4   # a subset

Exit 1 if any mutant SURVIVES — a survivor means the suite would not have
noticed the wrong behaviour described in the catalogue.

Runs inside one container on purpose: the cost of this is `docker compose run`,
not pytest, so N mutants in one invocation is a minute and N invocations is
twenty.

**Restoration is the one thing that must never fail.** A crashed run that leaves
a mutated `safety.py` on disk is strictly worse than no mutation testing at all,
so every edit is written back in a `finally`, and the file's original bytes are
held in memory rather than re-read from anywhere that could also be mutated.
"""

from __future__ import annotations

import io
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tests.mutation.catalogue import CATALOGUE, Mutant  # noqa: E402

BACKEND = Path(__file__).resolve().parents[2]


def apply(mutant: Mutant) -> str | None:
    """Write the mutated file, returning the original text (None if impossible)."""
    path = BACKEND / mutant.path
    original = io.open(path, encoding="utf-8").read()
    if mutant.old not in original:
        return None
    io.open(path, "w", encoding="utf-8", newline="").write(
        original.replace(mutant.old, mutant.new, 1)
    )
    return original


def restore(mutant: Mutant, original: str) -> None:
    io.open(BACKEND / mutant.path, "w", encoding="utf-8", newline="").write(original)


def run_tests(paths: tuple[str, ...]) -> bool:
    """True when the tests PASS — which for a mutated tree means it survived."""
    result = subprocess.run(
        [sys.executable, "-m", "pytest", "-q", "-x", "--no-header", *paths],
        cwd=BACKEND,
        capture_output=True,
        text=True,
    )
    return result.returncode == 0


def main(argv: list[str]) -> int:
    wanted = [a for a in argv[1:] if not a.startswith("-")]
    mutants = [m for m in CATALOGUE if not wanted or any(w in m.id for w in wanted)]
    if not mutants:
        print("No mutants matched.", file=sys.stderr)
        return 2

    survivors: list[Mutant] = []
    missing: list[Mutant] = []

    print(f"Mutation run — {len(mutants)} mutant(s)\n")
    for mutant in mutants:
        original = apply(mutant)
        if original is None:
            # The code moved and the catalogue did not follow. Not a pass:
            # a mutant that cannot be applied is silently testing nothing.
            missing.append(mutant)
            print(f"  ?  {mutant.id}: PATTERN NOT FOUND in {mutant.path}")
            continue
        try:
            survived = run_tests(mutant.caught_by)
        finally:
            restore(mutant, original)
        if survived:
            survivors.append(mutant)
            print(f"  ✗  {mutant.id}: SURVIVED")
        else:
            print(f"  ✓  {mutant.id}: caught")

    print()
    if missing:
        print("Catalogue entries that no longer match the code:")
        for m in missing:
            print(f"  · {m.id} — {m.path}")
        print("  Update the catalogue in the same commit as the code it describes.\n")

    if survivors:
        print("SURVIVORS — the suite would not have noticed:\n")
        for m in survivors:
            print(f"  {m.id}")
            print(f"    Breaks: {m.breaks}")
            print(f"    Looked at: {', '.join(m.caught_by)}\n")
        print(
            "Either the tests are thinner than they look, or the mutant is\n"
            "equivalent — and equivalence is a claim to PROVE, not to assume."
        )
        return 1

    if missing:
        return 1
    print(f"All {len(mutants)} mutant(s) caught.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
