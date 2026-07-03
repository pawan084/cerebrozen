"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";

// Mirrors the iOS one-tap moods (Dummy.moods) so check-ins read the same
// across clients; `symbol` carries the SF Symbol name the iOS app renders.
const MOODS = [
  { name: "Good", note: "Clear", symbol: "sparkles" },
  { name: "Anxious", note: "Loud thoughts", symbol: "exclamationmark.triangle" },
  { name: "Low", note: "Heavy", symbol: "moon" },
  { name: "Tired", note: "Need rest", symbol: "drop" },
];

type Mood = { id: string; mood: string; note: string; created_at: string };

export default function Home() {
  const [name, setName] = useState("");
  const [selected, setSelected] = useState<string | null>(null);
  const [moods, setMoods] = useState<Mood[]>([]);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api("/auth/me").then((me) => setName(me.name)).catch(() => {});
    void reload();
  }, []);

  async function reload() {
    try {
      setMoods(await api<Mood[]>("/moods?limit=5"));
    } catch {}
  }

  async function checkIn() {
    const mood = MOODS.find((m) => m.name === selected);
    if (!mood || busy) return;
    setBusy(true);
    try {
      await api("/moods", {
        method: "POST",
        body: JSON.stringify({ mood: mood.name, note: mood.note, symbol: mood.symbol, intensity: 3 }),
      });
      setSaved(true);
      setSelected(null);
      await reload();
    } finally {
      setBusy(false);
    }
  }

  const hour = new Date().getHours();
  const greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";

  return (
    <>
      <p className="eyebrow">Today</p>
      <h1>{greeting}{name ? `, ${name}` : ""}</h1>

      <section className="card" aria-label="Mood check-in">
        <h2>How are you arriving?</h2>
        <p className="sub">A 20-second check-in shapes your next best step.</p>
        <div className="pick-grid">
          {MOODS.map((m) => (
            <button
              key={m.name}
              className={`pick${selected === m.name ? " selected" : ""}`}
              aria-pressed={selected === m.name}
              onClick={() => setSelected(m.name)}
            >
              {m.name}
              <span className="note">{m.note}</span>
            </button>
          ))}
        </div>
        {saved && <p className="success" role="status">Checked in — noted gently.</p>}
        <button className="btn" onClick={checkIn} disabled={!selected || busy}>
          {busy ? "Saving…" : "Check in"}
        </button>
      </section>

      {moods.length > 0 && (
        <section className="card" aria-label="Recent check-ins">
          <h2>Recent check-ins</h2>
          {moods.map((m) => (
            <div className="entry" key={m.id}>
              <strong>{m.mood}</strong> <span className="meta">· {m.note}</span>
              <div className="meta">{new Date(m.created_at).toLocaleString()}</div>
            </div>
          ))}
        </section>
      )}

      <section className="card">
        <div className="row">
          <div className="grow">
            <h2>Journal</h2>
            <p className="sub">Get it out of your head and onto the page — private by default.</p>
          </div>
          <Link className="btn ghost" href="/journal">Open</Link>
        </div>
      </section>
      <section className="card">
        <div className="row">
          <div className="grow">
            <h2>Sleep diary</h2>
            <p className="sub">Log last night in 20 seconds; watch your week take shape.</p>
          </div>
          <Link className="btn ghost" href="/sleep">Open</Link>
        </div>
      </section>
      <p className="footnote">
        CereBro is not a therapist or crisis service. The full experience — voice companion,
        soundscapes, daily plans — lives in the iOS app.
      </p>
    </>
  );
}
