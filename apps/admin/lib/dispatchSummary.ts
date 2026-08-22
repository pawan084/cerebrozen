/**
 * Turn a dispatch pass into the one line an operator reads.
 *
 * Extracted from the button (2026-08-22) because choosing WHICH endings to name
 * is a decision, not rendering — and a decision belongs somewhere it can be
 * tested. The dispatcher produces five outcomes and they are never summed:
 * each answers a different question, and three of them are not problems with
 * the user at all.
 */

export type DispatchOutcome = {
  sent: number;
  skipped: number;
  failed: number;
  /** Optional so an older backend, mid-deploy, still renders. */
  expired?: number;
  deferred?: number;
};

export function dispatchSummary(outcome: DispatchOutcome): string {
  const expired = outcome.expired ?? 0;
  const deferred = outcome.deferred ?? 0;
  const total = outcome.sent + outcome.skipped + outcome.failed + expired + deferred;
  if (total === 0) return "Nothing was due.";

  // `sent` is always named, including when it is zero: "0 sent · 4 expired" is
  // the sentence an operator needs on the morning after an outage, and hiding
  // the zero would make that pass read as though something went out.
  const parts = [
    `${outcome.sent} sent`,
    // The rest appear only when they happened. A line that always reads
    // "0 expired · 0 deferred" trains someone to stop reading it, which is
    // exactly when the one that matters shows up.
    outcome.skipped ? `${outcome.skipped} skipped (nobody reachable)` : "",
    outcome.failed ? `${outcome.failed} failed` : "",
    // Not a user problem: it means WE were down long enough that these stopped
    // being true, so the wording points at the nudge rather than the person.
    expired ? `${expired} expired (too late to send)` : "",
    // Not an ending at all.
    deferred ? `${deferred} deferred (will retry)` : "",
  ].filter(Boolean);

  return parts.join(" · ");
}
