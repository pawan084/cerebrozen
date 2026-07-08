"use client";

import { useEffect, useState } from "react";
import { api, API_URL } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";

// Warm thumbnail gradients for the served soundscapes / stories.
const SOUND_BG = [
  "linear-gradient(160deg,#2f5a3a,#12241a)",
  "linear-gradient(160deg,#8a5a2f,#3a2416)",
  "linear-gradient(160deg,#3a3a7a,#161240)",
  "linear-gradient(160deg,#2f6a6a,#12302f)",
];
const STORY_BG = [
  "linear-gradient(135deg,#8a7bf0,#5b52c9)",
  "linear-gradient(135deg,#e08a9a,#8a5a6a)",
];
const QUALITY = ["Rough", "Poor", "Okay", "Good", "Rested"];

type Content = { id: string; title: string; subtitle: string; duration_min: number; audio_url: string };

// Relative "/media/…" narration resolves against the API base; absolute passes through.
function mediaSrc(url: string): string {
  if (!url) return "";
  return url.startsWith("/") ? `${API_URL}${url}` : url;
}

export default function Sleep() {
  const [quality, setQuality] = useState(0);
  const [bedtime, setBedtime] = useState("23:00");
  const [wake, setWake] = useState("07:00");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [soundscapes, setSoundscapes] = useState<Content[]>([]);
  const [stories, setStories] = useState<Content[]>([]);

  useEffect(() => {
    api<any[]>("/sleep?limit=1").then((l) => { if (l[0]) { setBedtime(l[0].bedtime.slice(0, 5)); setWake(l[0].wake_time.slice(0, 5)); } }).catch(() => {});
    fetch(`${API_URL}/content?kind=soundscape`).then((r) => (r.ok ? r.json() : [])).then(setSoundscapes).catch(() => {});
    fetch(`${API_URL}/content?kind=sleep`).then((r) => (r.ok ? r.json() : [])).then(setStories).catch(() => {});
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
          {soundscapes.map((s, i) => (
            <div key={s.id} className="media-card" style={{ background: SOUND_BG[i % SOUND_BG.length] }}>
              <span />
              <span className="cap"><strong>{s.title}</strong><small>{s.duration_min > 0 ? `${s.duration_min} min` : s.subtitle}</small></span>
            </div>
          ))}
        </div>

        <div className="sec-head"><h2 className="serif-h">Sleep stories</h2></div>
        {stories.map((s, i) => (
          <div key={s.id}>
            <div className="story-row">
              <span className="story-thumb" style={{ background: STORY_BG[i % STORY_BG.length] }} />
              <span className="body"><strong>{s.title}</strong><small>{s.subtitle}</small></span>
              {s.duration_min > 0 && <span className="meta">{s.duration_min} min</span>}
            </div>
            {s.audio_url && (
              <audio
                controls
                preload="none"
                src={mediaSrc(s.audio_url)}
                aria-label={`Play ${s.title}`}
                style={{ width: "100%", marginTop: 6, marginBottom: 10 }}
              />
            )}
          </div>
        ))}
        <p className="footnote">Served from the same catalogue as the apps. Stories with narration play right here; the soundscape mixer lives in the iOS &amp; Android apps.</p>

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
