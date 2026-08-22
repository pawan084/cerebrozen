import { describe, expect, it } from "vitest";

import { dispatchSummary } from "../../apps/admin/lib/dispatchSummary";

/**
 * What an operator is told after a dispatch pass.
 *
 * The dispatcher gained two endings on 2026-08-22 (`expired`, `deferred`) and
 * they are the reason this is tested at all: both used to read as `failed`,
 * which is the one an operator is supposed to chase. Getting this line wrong
 * sends somebody hunting a delivery problem that is really an outage — or, far
 * worse, makes an outage look like a quiet night.
 */

const none = { sent: 0, skipped: 0, failed: 0, expired: 0, deferred: 0 };

describe("a pass that did nothing", () => {
  it("says so, rather than printing a row of zeroes", () => {
    expect(dispatchSummary(none)).toBe("Nothing was due.");
  });
});

describe("naming only what happened", () => {
  it("keeps the line short when everything simply sent", () => {
    expect(dispatchSummary({ ...none, sent: 12 })).toBe("12 sent");
  });

  it("never prints an ending that did not occur", () => {
    // A line that always reads "0 expired · 0 deferred" trains an operator to
    // stop reading it, which is exactly when the one that matters shows up.
    const line = dispatchSummary({ ...none, sent: 3, failed: 1 });
    expect(line).toBe("3 sent · 1 failed");
    expect(line).not.toContain("expired");
    expect(line).not.toContain("skipped");
  });

  it("names every ending that did occur, in a fixed order", () => {
    expect(
      dispatchSummary({ sent: 5, skipped: 2, failed: 1, expired: 3, deferred: 4 }),
    ).toBe(
      "5 sent · 2 skipped (nobody reachable) · 1 failed · 3 expired (too late to send) · 4 deferred (will retry)",
    );
  });
});

describe("the morning after an outage", () => {
  it("keeps a zero `sent` beside what expired", () => {
    // "0 sent · 4 expired" is the sentence somebody needs here. Hiding the
    // zero would let this pass read as though something went out.
    expect(dispatchSummary({ ...none, expired: 4 })).toBe(
      "0 sent · 4 expired (too late to send)",
    );
  });

  it("does not call an outage a failure", () => {
    // `expired` means WE were down; `failed` means a device refused. An
    // operator chases those in completely different directions.
    const line = dispatchSummary({ ...none, expired: 9 });
    expect(line).toContain("expired");
    expect(line).not.toContain("failed");
  });

  it("distinguishes a retry from an ending", () => {
    const line = dispatchSummary({ ...none, deferred: 2 });
    expect(line).toContain("will retry");
    expect(line).not.toContain("failed");
  });
});

describe("mid-deploy, against an older backend", () => {
  it("renders without the two newer fields", () => {
    // The admin app and the API deploy separately; a response missing
    // `expired`/`deferred` must not produce "undefined" in the UI.
    expect(dispatchSummary({ sent: 2, skipped: 1, failed: 0 })).toBe(
      "2 sent · 1 skipped (nobody reachable)",
    );
  });

  it("still recognises an empty pass", () => {
    expect(dispatchSummary({ sent: 0, skipped: 0, failed: 0 })).toBe("Nothing was due.");
  });
});
