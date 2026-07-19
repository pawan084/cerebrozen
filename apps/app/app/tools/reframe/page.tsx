"use client";

import Link from "next/link";
import { useState } from "react";
import { addJournal, Unavailable } from "@/lib/wellness";
import { celebrate } from "@/lib/celebrate";

const FIELDS = [
  { key: "situation", label: "What happened?", hint: "Just the facts — what triggered the thought." },
  { key: "thought", label: "What went through your mind?", hint: "The automatic thought, in your own words." },
  { key: "evidence", label: "What supports it — and what doesn't?", hint: "Weigh both sides honestly." },
  { key: "reframe", label: "A more balanced way to see it?", hint: "Not forced positivity — just fairer." },
];

export default function Reframe() {
  const [vals, setVals] = useState<Record<string, string>>({});
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const set = (k: string, v: string) => setVals((p) => ({ ...p, [k]: v }));
  const filled = FIELDS.every((f) => (vals[f.key] || "").trim());

  async function save() {
    if (!filled || saving) return;
    setSaving(true); setNote("");
    const body = `Thought reframe\n\nSituation: ${vals.situation}\n\nThought: ${vals.thought}\n\nEvidence: ${vals.evidence}\n\nBalanced view: ${vals.reframe}`;
    try {
      await addJournal(body, "Thought reframe");
      celebrate("Reframed");
      setNote("Saved to your journal.");
      setVals({});
    } catch (e) {
      setNote(
        e instanceof Unavailable
          ? e.reason === "consent"
            ? "Turn on Journal in Settings to save this."
            : "Couldn't save — check your connection and try again."
          : "Couldn't save — try again in a moment."
      );
    } finally { setSaving(false); }
  }

  return (
    <div className="page tool-page">
      <div className="page-head">
        <div>
          <div className="eyebrow"><Link href="/tools" className="link-accent">Tools</Link> · Reframe</div>
          <h1>Thought reframe</h1>
        </div>
      </div>
      <p className="placeholder" style={{ maxWidth: 520, marginBottom: 20 }}>
        A CBT thought record — untangle a sticky thought in four steps. It saves to your private journal.
      </p>
      <div className="reframe-form">
        {FIELDS.map((f) => (
          <label key={f.key} className="rf-field">
            <span className="rf-label">{f.label}</span>
            <span className="rf-hint">{f.hint}</span>
            <textarea rows={2} value={vals[f.key] || ""} onChange={(e) => set(f.key, e.target.value)} />
          </label>
        ))}
        <button className="primary" disabled={!filled || saving} onClick={save}>{saving ? "Saving…" : "Save to journal"}</button>
        {note && <p className="placeholder" aria-live="polite">{note}</p>}
      </div>
    </div>
  );
}
