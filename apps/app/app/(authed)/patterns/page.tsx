"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { api } from "@/lib/api";

// Ref PATTERN DASHBOARD: "everything CereBro has learned about you — visible,
// honest, and yours to delete."
//
// Two different things live on this screen, and the distinction is the whole
// privacy argument, so the UI keeps them apart:
//   • Patterns — computed on every request from the user's own rows. Never
//     stored, so they can only be hidden, not edited.
//   • Memories — things the user wrote or explicitly approved. Real rows, so
//     they can be edited and deleted individually.
type Pattern = { statement: string; basis: string };
type Memory = {
  id: string;
  body: string;
  source: string;
  created_at: string;
};
// A suggested practice + the pattern that prompted it. `reason` is never
// omitted — a suggestion with no visible basis is what this screen exists to
// avoid showing.
type Rec = {
  id: string;
  slug: string;
  title: string;
  body: string;
  reason: string;
};

const SOURCE_LABEL: Record<string, string> = {
  manual: "You added this",
  confirmed: "You approved this",
  onboarding: "From onboarding",
};

export default function Patterns() {
  const [patterns, setPatterns] = useState<Pattern[] | null>(null);
  const [suppressed, setSuppressed] = useState(0);
  const [memories, setMemories] = useState<Memory[] | null>(null);
  const [recs, setRecs] = useState<Rec[] | null>(null);
  const [draft, setDraft] = useState("");
  const [editing, setEditing] = useState<string | null>(null);
  const [editText, setEditText] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const [memoryOff, setMemoryOff] = useState(false);

  const loadPatterns = useCallback(() => {
    api<{ patterns: Pattern[]; suppressed?: number }>("/insights/patterns")
      .then((r) => {
        setPatterns(r.patterns);
        setSuppressed(r.suppressed ?? 0);
      })
      .catch(() => setPatterns([]));
  }, []);

  const loadMemories = useCallback(() => {
    api<Memory[]>("/users/me/memory")
      .then((rows) => {
        setMemories(rows);
        setMemoryOff(false);
      })
      // Defensive: the READ is not consent-gated today (only POST/PATCH/
      // DELETE call `_memory_allowed`), so this branch is a failed load
      // rather than a refusal. It is kept because the register's D1 fix means
      // a 403 now reaches the caller instead of destroying the session — if
      // the GET is ever gated too, this explains itself rather than showing
      // "Nothing saved yet" about data the user does have.
      .catch(() => {
        setMemories([]);
        setMemoryOff(true);
      });
  }, []);

  const loadRecs = useCallback(() => {
    api<Rec[]>("/recommendations/mine")
      .then(setRecs)
      .catch(() => setRecs([]));
  }, []);

  useEffect(() => {
    loadPatterns();
    loadMemories();
    loadRecs();
  }, [loadPatterns, loadMemories, loadRecs]);

  async function resolveRec(id: string, how: "accept" | "dismiss") {
    try {
      await api(`/recommendations/${id}/${how}`, { method: "POST" });
      loadRecs();
    } catch {
      setError("Couldn't record that — try again.");
    }
  }

  async function addMemory() {
    const body = draft.trim();
    if (!body) return;
    try {
      await api("/users/me/memory", { method: "POST", body: JSON.stringify({ body }) });
      setDraft("");
      setError("");
      loadMemories();
    } catch {
      setError("Couldn't save that — is AI memory switched on in your privacy settings?");
    }
  }

  async function saveEdit(id: string) {
    const body = editText.trim();
    if (!body) return;
    try {
      await api(`/users/me/memory/${id}`, { method: "PATCH", body: JSON.stringify({ body }) });
      setEditing(null);
      loadMemories();
    } catch {
      setError("Couldn't save that change.");
    }
  }

  async function removeMemory(id: string) {
    try {
      await api(`/users/me/memory/${id}`, { method: "DELETE" });
      loadMemories();
    } catch {
      setError("Couldn't delete that.");
    }
  }

  // Hiding a pattern is not deletion — it stops being shown and stops being
  // recalled, but nothing about the underlying check-ins changes.
  async function hidePattern(statement: string) {
    try {
      await api("/users/me/memory/suppress-pattern", {
        method: "POST",
        body: JSON.stringify({ statement }),
      });
      loadPatterns();
    } catch {
      setError("Couldn't hide that.");
    }
  }

  async function wipe() {
    if (!confirming) {
      setConfirming(true);
      return;
    }
    try {
      const r = await api<{ chat_messages: number; insights: number; memories: number }>(
        "/users/me/memory",
        { method: "DELETE" },
      );
      setConfirming(false);
      setPatterns([]);
      setMemories([]);
      setStatus(
        `Memory cleared — ${r.chat_messages} messages, ${r.insights} insights and ${r.memories} saved notes forgotten.`,
      );
    } catch {
      setStatus("Couldn't delete — try again.");
    }
  }

  return (
    <>
      <AppHeader eyebrow="Transparent AI memory" title="Pattern dashboard" />
      <div className="page-body">
        <p style={{ color: "var(--muted)", maxWidth: 560 }}>
          Everything CereBro has learned about you — visible, honest, and yours to delete.
        </p>
        {error && <p className="error">{error}</p>}

        <section className="card cz-in" style={{ marginTop: 14 }}>
          <h2>Patterns it noticed</h2>
          <p className="sub" style={{ maxWidth: 560 }}>
            Worked out from your own check-ins each time you open this page — never stored.
            Hide one and it stops being shown or used.
          </p>
          {patterns === null ? (
            <p className="sub">Looking at your data…</p>
          ) : patterns.length === 0 ? (
            <p className="sub">
              Nothing yet. Patterns only appear once a few weeks of real check-ins support them —
              no guesses, ever.
            </p>
          ) : (
            <ul style={{ margin: "8px 0 0", padding: 0, listStyle: "none" }}>
              {patterns.map((p) => (
                <li
                  key={p.statement}
                  style={{
                    margin: "6px 0", display: "flex", gap: 12,
                    alignItems: "baseline", justifyContent: "space-between",
                  }}
                >
                  <span>
                    {p.statement}{" "}
                    <span style={{ color: "var(--cyan)", fontSize: 12.5 }}>{p.basis}</span>
                  </span>
                  <button
                    onClick={() => hidePattern(p.statement)}
                    aria-label={`Hide: ${p.statement}`}
                    style={{
                      background: "none", border: "none", cursor: "pointer", font: "inherit",
                      color: "var(--muted)", padding: "8px 0", flex: "0 0 auto",
                    }}
                  >
                    Hide
                  </button>
                </li>
              ))}
            </ul>
          )}
          {suppressed > 0 && (
            <p className="sub" style={{ marginTop: 10 }}>
              {suppressed} hidden. They stay hidden until you clear your memory.
            </p>
          )}
        </section>

        {recs !== null && recs.length > 0 && (
          <section className="card cz-in cz-d1" style={{ marginTop: 14 }}>
            <h2>Something you could try</h2>
            <p className="sub" style={{ maxWidth: 560 }}>
              Suggested because of a pattern above — never a general prescription. Dismissing
              one means it won&apos;t come back.
            </p>
            {recs.map((r) => (
              <div key={r.id} style={{ borderTop: "1px solid var(--line)", padding: "12px 0" }}>
                <strong>{r.title}</strong>
                <p className="sub" style={{ margin: "4px 0 0" }}>{r.body}</p>
                <p className="meta" style={{ marginTop: 6 }}>Because: {r.reason}</p>
                <div style={{ display: "flex", gap: 14, marginTop: 8 }}>
                  <button
                    className="btn"
                    onClick={() => resolveRec(r.id, "accept")}
                  >
                    I&apos;ll try this
                  </button>
                  <button
                    onClick={() => resolveRec(r.id, "dismiss")}
                    style={{
                      background: "none", border: "none", cursor: "pointer",
                      font: "inherit", color: "var(--muted)",
                    }}
                  >
                    Not for me
                  </button>
                </div>
              </div>
            ))}
          </section>
        )}

        <section className="card cz-in cz-d1" style={{ marginTop: 14 }}>
          <h2>What you asked it to remember</h2>
          <p className="sub" style={{ maxWidth: 560 }}>
            Your words, not its guesses. Edit or delete any of them.
          </p>

          {memoryOff ? (
            <p className="sub">
              We couldn&apos;t load your saved notes just now. If AI memory is switched off in{" "}
              <Link href="/account">privacy settings</Link>, nothing is being remembered —
              otherwise this is a connection problem, not an empty list.
            </p>
          ) : memories === null ? (
            <p className="sub">Loading…</p>
          ) : memories.length === 0 ? (
            <p className="sub">Nothing saved yet.</p>
          ) : (
            <ul style={{ margin: "10px 0 0", padding: 0, listStyle: "none" }}>
              {memories.map((m) => (
                <li key={m.id} style={{ borderTop: "1px solid var(--line)", padding: "12px 0" }}>
                  {editing === m.id ? (
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      <input
                        value={editText}
                        onChange={(e) => setEditText(e.target.value)}
                        aria-label="Edit this memory"
                        style={{ flex: "1 1 240px", minWidth: 0 }}
                      />
                      <button className="btn" onClick={() => saveEdit(m.id)}>Save</button>
                      <button
                        onClick={() => setEditing(null)}
                        style={{
                          background: "none", border: "none", cursor: "pointer",
                          font: "inherit", color: "var(--muted)",
                        }}
                      >
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <div style={{ display: "flex", gap: 12, justifyContent: "space-between" }}>
                      <div style={{ minWidth: 0 }}>
                        <div>{m.body}</div>
                        <div className="meta">{SOURCE_LABEL[m.source] ?? m.source}</div>
                      </div>
                      <div style={{ display: "flex", gap: 10, flex: "0 0 auto" }}>
                        <button
                          onClick={() => { setEditing(m.id); setEditText(m.body); }}
                          aria-label={`Edit: ${m.body}`}
                          style={{
                            background: "none", border: "none", cursor: "pointer",
                            font: "inherit", color: "var(--lav-2)", padding: "8px 0",
                          }}
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => removeMemory(m.id)}
                          aria-label={`Delete: ${m.body}`}
                          style={{
                            background: "none", border: "none", cursor: "pointer",
                            font: "inherit", color: "var(--danger)", padding: "8px 0",
                          }}
                        >
                          Delete
                        </button>
                      </div>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}

          <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") addMemory(); }}
              placeholder="Something worth remembering…"
              aria-label="Add a memory"
              style={{ flex: "1 1 240px", minWidth: 0 }}
            />
            <button className="btn" onClick={addMemory} disabled={!draft.trim()}>
              Remember this
            </button>
          </div>
        </section>

        <section className="card cz-in cz-d3" style={{ marginTop: 14 }}>
          <h2 style={{ color: "var(--danger)" }}>Delete all memory</h2>
          <p className="sub" style={{ maxWidth: 560 }}>
            Removes chat history, computed insights, saved notes and the companion&apos;s thread
            memory — it starts fresh. Your journal, check-ins and sleep diary stay: they&apos;re
            your content, with their own controls.
          </p>
          <button
            onClick={wipe}
            style={{
              background: "none", border: "1px solid var(--danger)", borderRadius: 999,
              cursor: "pointer", font: "inherit", fontWeight: 700, color: "var(--danger)",
              padding: "10px 20px", marginTop: 12,
            }}
          >
            {confirming ? "Click again to confirm" : "Delete all memory"}
          </button>
          {status && <p className="sub" style={{ marginTop: 10 }}>{status}</p>}
        </section>
      </div>
    </>
  );
}
