#!/usr/bin/env python3
"""Fail when an instrumented test was SKIPPED rather than run.

Fourteen of the twenty-two Android instrumented tests are write flows, and they
need a real account against a real backend — a journal entry that is not stored
anywhere proves nothing. `BackendFixture.signInOrSkip` calls `assumeTrue`, so
without a reachable API they skip instead of failing, and the job goes green.

That was the right trade while CI had no backend beside the emulator. It is the
wrong one now that it does, because the failure is silent: the suite reports
success, the summary says "22 tests", and fourteen of them did nothing.

`assumeTrue` is the only skip mechanism in the suite — there is no `@Ignore`
anywhere — so ANY skip means the backend was unreachable. Hence a flat zero.

Reads the JUnit XML the Gradle connected-test task writes. Missing results are
also a failure: "no XML" and "everything passed" must never look the same.
"""

import glob
import sys
import xml.etree.ElementTree as ET

RESULTS = "apps/android/app/build/outputs/androidTest-results/connected/**/*.xml"


def main() -> int:
    # An explicit path is accepted so this script can be exercised against
    # fixtures; CI passes nothing and gets the real results directory.
    pattern = sys.argv[1] if len(sys.argv) > 1 else RESULTS
    files = glob.glob(pattern, recursive=True)
    if not files:
        print(f"FAIL: no instrumented results under {pattern}")
        print("      The emulator step did not produce XML — treat this as a failure,")
        print("      not as 'nothing to check'.")
        return 1

    total = skipped = failed = 0
    skipped_names: list[str] = []

    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as e:
            print(f"FAIL: could not parse {path}: {e}")
            return 1
        for suite in root.iter("testsuite"):
            total += int(suite.get("tests", 0))
            failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        for case in root.iter("testcase"):
            if case.find("skipped") is not None:
                skipped += 1
                skipped_names.append(f"{case.get('classname')}.{case.get('name')}")

    print(f"instrumented: {total} tests, {failed} failed, {skipped} skipped")

    if skipped:
        print("\nFAIL: these tests SKIPPED, which means the backend was unreachable:")
        for name in sorted(skipped_names):
            print(f"  - {name}")
        print(
            "\n`assumeTrue` in BackendFixture is the only skip in this suite. A skip "
            "here is not a passing test — it is a write flow that did not run.\n"
            "Check the 'Start the backend the write flows need' step and the API logs."
        )
        return 1

    if total == 0:
        print("FAIL: results were found but contained no tests at all.")
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
