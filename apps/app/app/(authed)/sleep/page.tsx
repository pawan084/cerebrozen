"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";

const SOUNDSCAPES = [
  { name: "Forest rain", len: "45 min", bg: "linear-gradient(160deg,#2f5a3a,#12241a)" },
  { name: "Warm rain", len: "60 min", bg: "linear-gradient(160deg,#8a5a2f,#3a2416)" },
  { name: "Deep space", len: "8 hr", bg: "linear-gradient(160deg,#3a3a7a,#161240)" },
  { name: "Ocean tide", len: "90 min", bg: "linear-gradient(160deg,#2f6a6a,#12302f)" },
];
const STORIES = [
  { title: "The lighthouse keeper", sub: "A slow tale of tides and quiet · 24 min", bg: "linear-gradient(135deg,#8a7bf0,#5b52c9)" },
  { title: "Night train to nowhere", sub: "Rhythmic and drowsy · 31 min", bg: "linear-gradient(135deg,#e08a9a,#8a5a6a)" },
];
const QUALITY = ["Rough", "Poor", "Okay", "Good", "Rested"];

export default function Sleep() {
  const [quality, setQuality] = useState(0);
  const [bedtime, setBedtime] = useState("23:00");
  const [wake, setWake] = useState("07:00");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api<any[]>("/sleep?limit=1").then((l) => { if (l[0]) { setBedtime(l[0].bedtime.slice(0, 5)); setWake(l[0].wake_time.slice(0, 5)); } }).catch(() => {});
  }, []);

  function todayISO() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; }
  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!quality || busy) return; setBusy(true);
    try {
      await api("/sleep", { method: "POST", body: JSON.stringify({ date: todayISO(), bedtime: `${bedtime}:00`, wake_time: `${wake}:00`, quality, awakenings: 0 }) });
      setSaved(true);
    } finally { setBusy(false); }
  }

  return (
    <>
      <AppHeader eyebrow="Sleep" title="Ease toward rest" />
      <div className="page-body">
        <section className="media-hero" style={{ background: "linear-gradient(120deg,rgba(90,40,80,0.6),rgba(20,16,44,0.4)), radial-gradient(circle at 82% 20%, rgba(143,230,238,0.25), transparent 40%), var(--night)" }}>
          <div className="hero-orb" aria-hidden="true" />
          <p className="eyebrow">Tonight's wind-down</p>
          <h2>Drift off to a quieter mind</h2>
          <p>Sleep stories, soundscapes, and slow breathing to ease you into rest.</p>
        </section>

        <div className="sec-head"><h2 className="serif-h">Soundscapes</h2></div>
        <div className="media-grid">
          {SOUNDSCAPES.map((s) => (
            <div key={s.name} className="media-card" style={{ background: s.bg }}>
              <span />
              <span className="cap"><strong>{s.name}</strong><small>{s.len}</small></span>
            </div>
          ))}
        </div>

        <div className="sec-head"><h2 className="serif-h">Sleep stories</h2></div>
        {STORIES.map((s) => (
          <div key={s.title} className="story-row">
            <span className="story-thumb" style={{ background: s.bg }} />
            <span className="body"><strong>{s.title}</strong><small>{s.sub}</small></span>
            <button className="play">PLAY</button>
          </div>
        ))}
        <p className="footnote">Audio playback lives in the iOS app — here you can plan your wind-down and log your mornings.</p>

        <div className="sec-head"><h2 className="serif-h">Morning check-in</h2></div>
        <form className="card-dark" style={{ padding: 22 }} onSubmit={save} aria-label="Morning check-in">
          <p className="sub" style={{ color: "var(--muted)", marginBottom: 12 }}>How rested do you feel?</p>
          <div className="quality-row" role="radiogroup" aria-label="Sleep quality">
            {QUALITY.map((w, i) => (
              <button key={w} type="button" role="radio" aria-checked={quality === i + 1}
                className={`pick${quality === i + 1 ? " selected" : ""}`} onClick={() => setQuality(i + 1)}>{w}</button>
            ))}
          </div>
          <div className="row" style={{ gap: 12, marginTop: 4 }}>
            <label className="field grow"><span>In bed around</span><input type="time" value={bedtime} onChange={(e) => setBedtime(e.target.value)} /></label>
            <label className="field grow"><span>Woke up around</span><input type="time" value={wake} onChange={(e) => setWake(e.target.value)} /></label>
          </div>
          {saved && <p className="success" role="status">Saved — one entry per morning, edits welcome.</p>}
          <button className="btn" disabled={!quality || busy}>{busy ? "Saving…" : "Save check-in"}</button>
        </form>
      </div>
    </>
  );
}
