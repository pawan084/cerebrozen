"use client";

/* "Your data" — export and delete, as product functions rather than a support ticket.
 *
 * SECURITY.md: "Deletion & export | Product functions in the app". The marketing site sells
 * it. Both servers implemented it and were tested. This is the caller that was missing.
 *
 * The delete flow is deliberately awkward: a typed confirmation, not a `confirm()` dialog.
 * Everything else on this page is reversible — a consent toggle can be flicked back — and
 * this cannot be. The friction is the feature. (`lib/privacy.ts` carries the reasoning for
 * the two-service ordering, which is the part that actually matters.)
 */

import { useState } from "react";
import { useMe } from "@/components/shell";
import { NotSignedIn, deleteEverything, downloadBundle, exportEverything } from "@/lib/privacy";

const CONFIRM_WORD = "DELETE";

export function YourData() {
  const me = useMe();
  const [busy, setBusy] = useState<"export" | "delete" | null>(null);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [arming, setArming] = useState(false);
  const [typed, setTyped] = useState("");

  async function onExport() {
    setBusy("export"); setNote(""); setError("");
    try {
      const bundle = await exportEverything();
      downloadBundle(bundle, me?.email ?? "");
      // Never claim a complete export when a half failed — this is a statutory answer.
      setNote(
        bundle.incomplete
          ? `Downloaded, but incomplete — ${bundle.incomplete.join(", ")} couldn't be read. Try again in a moment for the full file.`
          : "Downloaded. That file is everything both services hold about you.",
      );
    } catch (e) {
      setError(e instanceof NotSignedIn ? "Please sign in again." : "Couldn't build your export. Nothing was changed.");
    } finally {
      setBusy(null);
    }
  }

  async function onDelete() {
    setBusy("delete"); setNote(""); setError("");
    try {
      const r = await deleteEverything();
      if (r.ok) {
        // The account is gone; there is nothing to return to.
        window.location.href = "/";
        return;
      }
      setError(r.detail);
      // A coaching-stage failure means the account is untouched and still usable, so
      // leave them armed to retry. An account-stage failure is not theirs to retry.
      setArming(r.stage === "coaching");
    } catch (e) {
      setError(e instanceof NotSignedIn ? "Please sign in again." : "Couldn't reach the servers. Nothing was deleted.");
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="card" style={{ marginTop: 16 }}>
      <h3>Your data</h3>
      <p className="placeholder" style={{ marginBottom: 14 }}>
        Yours to take and yours to destroy — both are buttons here, not a request you have to
        make to anyone. Your employer is never involved in either.
      </p>

      <div className="data-actions">
        <button className="primary" onClick={onExport} disabled={busy !== null}>
          {busy === "export" ? "Building your file…" : "Download my data"}
        </button>
        <span className="placeholder">
          One JSON file: your account and consents, plus every conversation, journal entry and
          check-in the coach holds.
        </span>
      </div>

      {!arming ? (
        <div className="data-actions danger-row">
          <button className="danger" onClick={() => { setArming(true); setTyped(""); setError(""); setNote(""); }}
            disabled={busy !== null}>
            Delete my account
          </button>
          <span className="placeholder">
            Erases your coaching history and your account. This cannot be undone.
          </span>
        </div>
      ) : (
        <div className="danger-zone">
          <p className="dz-lede">
            This erases every conversation, journal entry and check-in, then removes your
            account. It cannot be undone, and nobody — including us — can bring it back.
          </p>
          <label className="dz-confirm">
            Type <b>{CONFIRM_WORD}</b> to confirm
            <input value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off"
              aria-label={`Type ${CONFIRM_WORD} to confirm deletion`} />
          </label>
          <div className="data-actions">
            <button className="danger" onClick={onDelete} disabled={typed !== CONFIRM_WORD || busy !== null}>
              {busy === "delete" ? "Erasing…" : "Permanently delete everything"}
            </button>
            <button className="tool" onClick={() => { setArming(false); setTyped(""); setError(""); }} disabled={busy !== null}>
              Cancel
            </button>
          </div>
        </div>
      )}

      {note && <p className="placeholder" style={{ marginTop: 12 }}>{note}</p>}
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}
