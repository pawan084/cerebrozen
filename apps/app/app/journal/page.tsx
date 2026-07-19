"use client";

import { useCallback, useEffect, useState } from "react";
import { addJournal, deleteEntry, listJournal, Unavailable, type JournalEntry } from "@/lib/wellness";
import { celebrate } from "@/lib/celebrate";

function when(e: JournalEntry) {
  const raw = e.created_at || e.at;
  return raw ? new Date(raw).toLocaleString() : "";
}

export default function JournalPage() {
  const [entries, setEntries] = useState<JournalEntry[] | null>(null);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [blocked, setBlocked] = useState<Unavailable | null>(null);
  const [loadErr, setLoadErr] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(() => {
    setLoadErr(false);
    listJournal()
      .then((rows) => { setEntries(rows ?? []); setBlocked(null); })
      .catch((e) => {
        // A real load failure must not masquerade as the "nothing yet" empty state.
        if (e instanceof Unavailable && e.reason !== "offline") setBlocked(e);
        else setLoadErr(true);
        setEntries([]);
      });
  }, []);
  useEffect(() => { load(); }, [load]);

  async function remove(id: string) {
    const prev = entries;
    setEntries((es) => (es ?? []).filter((e) => (e.id || e.entry_id) !== id)); // optimistic
    try {
      await deleteEntry("journal", id);
    } catch {
      setEntries(prev); // restore on failure
      setError("Couldn't delete that entry — try again.");
    }
  }

  async function save() {
    const body = draft.trim();
    if (!body || busy) return;
    setBusy(true); setError("");
    try {
      await addJournal(body);
      celebrate("Saved");
      setDraft("");
      load();
    } catch (e) {
      if (e instanceof Unavailable) setBlocked(e);
      else setError(e instanceof Error ? e.message : "Couldn't save that.");
    } finally { setBusy(false); }
  }

  return (
    <div className="page">
      <div className="page-head"><div><div className="eyebrow">Journal</div><h1>Your journal</h1></div></div>

      <div className="dash">
        <div className="col">
          <div className="card">
            <h3>Write something</h3>
            <p className="placeholder" style={{ margin: "6px 0 12px" }}>
              Private by design — <strong>counts, never content</strong>. Nothing here is ever
              exposed to your employer or any admin surface.
            </p>
            <textarea rows={6} value={draft} onChange={(e) => setDraft(e.target.value)}
              placeholder="What's worth remembering from today?" aria-label="Journal entry"
              disabled={!!blocked} />
            <div style={{ marginTop: 12 }}>
              <button className="primary" onClick={save} disabled={busy || !draft.trim() || !!blocked}>
                {busy ? "Saving…" : "Save entry"}
              </button>
            </div>
            {blocked && (
              <p className="placeholder" style={{ marginTop: 12 }}>
                {blocked.reason === "consent"
                  ? <>Journaling is off until you turn on <strong>Journal</strong> in Settings.</>
                  : blocked.reason === "disabled"
                    ? <>Self-report wellness isn&rsquo;t enabled for your workspace yet.</>
                    : <>Your journal is unreachable right now.</>}
              </p>
            )}
            {error && <p className="error">{error}</p>}
          </div>
        </div>

        <div className="col">
          <div className="card">
            <div className="sec-title" style={{ margin: "0 0 8px" }}><h3>Recent entries</h3></div>
            {entries === null ? <p className="placeholder">Loading…</p>
              : loadErr ? <p className="placeholder">Couldn&rsquo;t load your entries just now — they&rsquo;re not lost. <button className="tool" onClick={load}>Retry</button></p>
              : entries.length === 0 ? <p className="placeholder">Nothing yet — your entries appear here.</p>
                : entries.map((e, i) => {
                    const id = e.id || e.entry_id;
                    return (
                      <div key={id || i} className="j-entry">
                        <div className="j-entry-head">
                          <div className="j-when">{when(e)}</div>
                          {id && (
                            <button className="j-del" aria-label="Delete this entry"
                              onClick={() => { if (window.confirm("Delete this journal entry? This can't be undone.")) remove(id); }}>
                              Delete
                            </button>
                          )}
                        </div>
                        <div className="j-body">{e.body}</div>
                      </div>
                    );
                  })}
          </div>
        </div>
      </div>
    </div>
  );
}
