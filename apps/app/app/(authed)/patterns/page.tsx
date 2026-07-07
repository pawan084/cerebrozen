"use client";

import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { api } from "@/lib/api";

// Ref PATTERN DASHBOARD: "everything CereBro has learned about you — visible,
// honest, and yours to delete." Statements come from /insights/patterns with
// their supporting counts; deletion is real (DELETE /users/me/memory).
type Pattern = { statement: string; basis: string };

export default function Patterns() {
  const [patterns, setPatterns] = useState<Pattern[] | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [status, setStatus] = useState("");

  useEffect(() => {
    api<{ patterns: Pattern[] }>("/insights/patterns")
      .then((r) => setPatterns(r.patterns))
      .catch(() => setPatterns([]));
  }, []);

  async function wipe() {
    if (!confirming) {
      setConfirming(true);
      return;
    }
    try {
      const r = await api<{ chat_messages: number; insights: number }>("/users/me/memory", {
        method: "DELETE",
      });
      setConfirming(false);
      setPatterns([]);
      setStatus(`Memory cleared — ${r.chat_messages} messages and ${r.insights} insights forgotten.`);
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

        <section className="card" style={{ marginTop: 14 }}>
          <h2>What CereBro remembers</h2>
          {patterns === null ? (
            <p className="sub">Looking at your data…</p>
          ) : patterns.length === 0 ? (
            <p className="sub">
              Nothing yet. Patterns only appear once a few weeks of real check-ins support them —
              no guesses, ever.
            </p>
          ) : (
            <ul style={{ margin: "8px 0 0", paddingLeft: 18 }}>
              {patterns.map((p) => (
                <li key={p.statement} style={{ margin: "6px 0" }}>
                  {p.statement}{" "}
                  <span style={{ color: "var(--cyan)", fontSize: 12.5 }}>{p.basis}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card" style={{ marginTop: 14 }}>
          <h2 style={{ color: "var(--danger)" }}>Delete all memory</h2>
          <p className="sub" style={{ maxWidth: 560 }}>
            Removes chat history, computed insights and the companion&apos;s thread memory — it
            starts fresh. Your journal, check-ins and sleep diary stay: they&apos;re your content,
            with their own controls.
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
