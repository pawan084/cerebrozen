"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api, API_URL, authedFetch } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";
import { WhyThisWorks } from "@/components/WhyThisWorks";

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
type Night = { date: string; bedtime: string; wake_time: string; quality: number };

// --- "Your rhythm" math — the unit-tested Android SleepScreen helpers, ported.
// Server times are "HH:MM[:SS]" strings → minutes since midnight first.
const toMin = (t: string) => {
  const [h, m] = t.split(":").map(Number);
  return (h % 24) * 60 + (m || 0);
};
// Mean nightly duration, wrapping past midnight (23:30→07:00 = 450m).
function avgSleepMinutes(nights: Night[]): number | null {
  const durations = nights
    .filter((n) => n.bedtime && n.wake_time)
    .map((n) => ((toMin(n.wake_time) - toMin(n.bedtime)) % 1440 + 1440) % 1440);
  if (!durations.length) return null;
  return Math.round(durations.reduce((a, b) => a + b, 0) / durations.length);
}
// Bedtime spread (max − min), anchored at noon so bedtimes either side of
// midnight stay close (23:30 vs 00:30 → 60 minutes, not 23 hours).
function bedtimeSpreadMinutes(nights: Night[]): number | null {
  const anchored = nights
    .filter((n) => n.bedtime)
    .map((n) => ((toMin(n.bedtime) - 720) % 1440 + 1440) % 1440);
  if (!anchored.length) return null;
  return Math.max(...anchored) - Math.min(...anchored);
}
// The one gentle CBT-I principle the data supports — consistency over duration.
const rhythmPrinciple = (spreadMin: number) =>
  spreadMin > 90
    ? "A steadier bedtime — even an imperfect one — does more for sleep than extra hours."
    : "Your bedtime is steady — that consistency is the strongest thing you're doing for your sleep.";
const spreadLabel = (min: number) =>
  min < 60 ? `${min}m` : `${Math.floor(min / 60)}h${min % 60 ? ` ${min % 60}m` : ""}`;

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
  const [nights, setNights] = useState<Night[]>([]);

  useEffect(() => {
    api<Night[]>("/sleep?limit=7").then((l) => {
      setNights(l);
      if (l[0]) { setBedtime(l[0].bedtime.slice(0, 5)); setWake(l[0].wake_time.slice(0, 5)); }
    }).catch(() => {});
    // Authenticated so premium narration actually carries its audio_url
    // (register D — the anonymous read stripped it for paying users).
    authedFetch(`/content?kind=soundscape`).then((r) => (r.ok ? r.json() : [])).then(setSoundscapes).catch(() => {});
    authedFetch(`/content?kind=sleep`).then((r) => (r.ok ? r.json() : [])).then(setStories).catch(() => {});
  }, []);

  function todayISO() { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; }
  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!quality || busy) return; setBusy(true);
    try {
      await api("/sleep", { method: "POST", body: JSON.stringify({ date: todayISO(), bedtime: `${bedtime}:00`, wake_time: `${wake}:00`, quality, awakenings: 0 }) });
      setSaved(true);
      api<Night[]>("/sleep?limit=7").then(setNights).catch(() => {});
    } finally { setBusy(false); }
  }

  return (
    <>
      {/* Sleep follows the selected appearance (owner decision 2026-08-04,
          docs/TODO.md — appearance is global on every client; Android dropped
          its forceNight in the same commit). Night stays one tap away in the
          theme picker for anyone winding down with the lights off. */}
      <div style={{ flex: 1 }}>
      <AppHeader eyebrow="Improve your sleep, night by night" title="Sleep" />
      <div className="page-body">
        <section className="media-hero cz-in" style={{ background: "linear-gradient(120deg,rgba(90,40,80,0.6),rgba(20,16,44,0.4)), radial-gradient(circle at 82% 20%, rgba(143,230,238,0.25), transparent 40%), var(--night)" }}>
          <div className="hero-orb" aria-hidden="true" />
          <p className="eyebrow">Wind down</p>
          <h2>A calmer night</h2>
          <p>A slower evening makes for a softer morning.</p>
        </section>

        {/* Consistency insight (CBT-I Phase 1) — client-side only, ≥3 real
            nights; the same noon-anchored math Android unit-tests. */}
        {(() => {
          const avg = avgSleepMinutes(nights);
          const spread = bedtimeSpreadMinutes(nights);
          if (nights.length < 3 || avg === null || spread === null) return null;
          return (
            <section className="card" style={{ marginTop: 14 }}>
              <h3 style={{ margin: "0 0 6px" }}>Your rhythm</h3>
              <p className="sub">
                You averaged {spreadLabel(avg)}. Your bedtime varied by about {spreadLabel(spread)} this week.
              </p>
              <p style={{ color: "var(--lav)", fontSize: 14, margin: "8px 0 0" }}>{rhythmPrinciple(spread)}</p>
            </section>
          );
        })()}

        {/* Stimulus-control micro-education — non-diagnostic, hardcoded
            (SLEEP_TRACKING framing; copy synced with Android sleep_bed_* /
            sleep_waketime_*). */}
        <div className="sec-head"><h2 className="serif-h">Better nights, gently</h2></div>
        <div className="dash-grid" style={{ gridTemplateColumns: "minmax(0,1fr) minmax(0,1fr)" }}>
          <section className="card">
            <h3 style={{ margin: "0 0 6px" }}>Bed is for sleep</h3>
            <p className="sub">
              If you&apos;re wide awake for 20+ minutes, get up, do something quiet and dim,
              come back sleepy.
            </p>
          </section>
          <section className="card">
            <h3 style={{ margin: "0 0 6px" }}>Same wake time, even after a short night</h3>
            <p className="sub">Your body clock anchors on when you get up.</p>
          </section>
        </div>
        <WhyThisWorks text="From CBT-I (cognitive behavioural therapy for insomnia) — the best-evidenced approach in sleep apps (Lancet Digital Health, 2025)." />

        {/* The guided version of the two cards above — a routine rather than
            advice to remember at 1am. */}
        <Link href="/sleep/ritual" className="card" style={{ display: "block", marginTop: 14 }}>
          <h3 style={{ margin: "0 0 6px" }}>Tonight&apos;s wind-down ritual →</h3>
          <p className="sub">
            Four quiet steps, about ten minutes: empty your head, name what went right,
            let the body go, then settle the breath.
          </p>
        </Link>

        <div className="sec-head"><h2 className="serif-h">Soundscapes</h2></div>
        <div className="media-grid cz-in cz-d1">
          {soundscapes.map((s, i) => (
            <div key={s.id} className="media-card" style={{ background: SOUND_BG[i % SOUND_BG.length] }}>
              <span />
              <span className="cap"><strong>{s.title}</strong><small>{s.duration_min > 0 ? `${s.duration_min} min` : s.subtitle}</small></span>
            </div>
          ))}
        </div>

        <div className="sec-head"><h2 className="serif-h">Sleep stories</h2></div>
        {stories.map((s, i) => (
          <div key={s.id} className={`cz-in cz-d${Math.min(i + 2, 6)}`}>
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
        <p className="footnote">
          Comfort content, not therapy — stories and sounds help you settle, while the cards
          above carry the evidence. Stories with narration play right here; the full
          soundscape mixer arrives with the mobile apps.
        </p>

        <div className="sec-head"><h2 className="serif-h">Morning check-in</h2></div>
        <form className="card-dark cz-in cz-d3" style={{ padding: 22 }} onSubmit={save} aria-label="Morning check-in">
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
      </div>
    </>
  );
}
