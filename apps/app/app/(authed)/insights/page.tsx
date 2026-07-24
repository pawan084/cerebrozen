"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";

type Mood = { mood: string; created_at: string };
// Real learned patterns from the transparent-memory engine (same source as
// /patterns) — statements carry their supporting basis counts; never invented.
type Pattern = { statement: string; basis: string };
const SCORE: Record<string, number> = { Great: 5, Good: 4, Okay: 3, Low: 2, Anxious: 1 };

// Honest week-over-week delta: only when both halves have enough check-ins.
function weekDelta(moods: Mood[]): string | null {
  const now = Date.now();
  const week = 7 * 24 * 3600 * 1000;
  const recent = moods.filter((m) => now - new Date(m.created_at).getTime() < week);
  const prior = moods.filter((m) => {
    const age = now - new Date(m.created_at).getTime();
    return age >= week && age < 2 * week;
  });
  if (recent.length < 2 || prior.length < 2) return null;
  const avg = (xs: Mood[]) => xs.reduce((a, m) => a + (SCORE[m.mood] ?? 3), 0) / xs.length;
  const diff = avg(recent) - avg(prior);
  if (diff > 0.3) return "▲ gentler than last week";
  if (diff < -0.3) return "▼ heavier than last week";
  return "≈ steady with last week";
}

// Most common check-in window, only once there's enough data to mean anything.
function bestTime(moods: Mood[]): { label: string; note: string } | null {
  if (moods.length < 3) return null;
  const buckets: Record<string, number> = { Morning: 0, Afternoon: 0, Evening: 0, Night: 0 };
  for (const m of moods) {
    const h = new Date(m.created_at).getHours();
    const b = h < 5 ? "Night" : h < 12 ? "Morning" : h < 18 ? "Afternoon" : "Evening";
    buckets[b]++;
  }
  const [label, n] = Object.entries(buckets).sort((a, b) => b[1] - a[1])[0];
  return { label, note: `${n} of your last ${moods.length} check-ins` };
}

export default function Insights() {
  const [moods, setMoods] = useState<Mood[]>([]);
  const [patterns, setPatterns] = useState<Pattern[] | null>(null);
  const [calmSessions, setCalmSessions] = useState<number | null>(null);

  useEffect(() => {
    api<Mood[]>("/moods?limit=14").then(setMoods).catch(() => {});
    api<{ patterns: Pattern[] }>("/insights/patterns")
      .then((r) => setPatterns(r.patterns))
      .catch(() => setPatterns([]));
    api<any>("/insights/weekly").then((w) => {
      const m = (w.metrics || []).find((x: any) => /calm|session/i.test(x.label));
      if (m) setCalmSessions(parseInt(m.value) || (w.metrics?.length ?? 0));
    }).catch(() => {});
  }, []);

  const scores = moods.map((m) => SCORE[m.mood] ?? 3).reverse();
  const avg = scores.length ? scores.reduce((a, b) => a + b, 0) / scores.length : 0;
  const avgLabel = avg >= 4.2 ? "Bright" : avg >= 3 ? "Steady" : avg > 0 ? "Tender" : "—";
  const avgEmoji = avg >= 4.2 ? "😊" : avg >= 3 ? "🙂" : avg > 0 ? "😔" : "";
  const delta = weekDelta(moods);
  const best = bestTime(moods);
  const hasLine = scores.length >= 2;

  return (
    <>
      <AppHeader eyebrow="Insights" title="How you've been" />
      <div className="page-body">
        <div className="stat-tiles">
          <div className="stat-tile">
            <div className="lbl">Average mood</div>
            <div className="val">{avgLabel} {avgEmoji}</div>
            {delta && <div className="delta">{delta}</div>}
          </div>
          <div className="stat-tile">
            <div className="lbl">Calm sessions</div>
            <div className="val">{calmSessions ?? 0}</div>
            <div className="lbl" style={{ fontSize: 13 }}>this week</div>
          </div>
          <div className="stat-tile">
            <div className="lbl">Best time of day</div>
            <div className="val">{best?.label ?? "—"}</div>
            <div className="lbl" style={{ fontSize: 13 }}>
              {best?.note ?? "shows after a few check-ins"}
            </div>
          </div>
        </div>

        <div className="dash-grid" style={{ marginTop: 20, gridTemplateColumns: "minmax(0,1fr) 380px" }}>
          <div className="chart-card">
            <h3>Mood, last 14 days</h3>
            <p className="sub">A gentle line, not a scoreboard.</p>
            {hasLine ? (
              <>
                <svg viewBox="0 0 560 210" style={{ width: "100%", height: 210 }} aria-hidden="true">
                  <defs><linearGradient id="ig" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="rgba(138,123,240,0.35)" /><stop offset="1" stopColor="rgba(138,123,240,0)" /></linearGradient></defs>
                  {(() => {
                    const P = scores.map((s, i) => [(i / (scores.length - 1)) * 540 + 10, 180 - ((s - 1) / 4) * 150]);
                    const line = P.map((p) => p.join(",")).join(" ");
                    const area = `10,180 ${line} 550,180`;
                    return (<>
                      <polygon points={area} fill="url(#ig)" />
                      <polyline points={line} fill="none" stroke="#a99cf0" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                      {P.map((p, i) => <circle key={i} cx={p[0]} cy={p[1]} r="4" fill="#cbb6ff" />)}
                    </>);
                  })()}
                </svg>
                <div className="chart-x"><span>2 weeks ago</span><span>1 week</span><span>Today</span></div>
              </>
            ) : (
              <p className="sub" style={{ padding: "48px 0", textAlign: "center" }}>
                Your line starts after two check-ins — it only ever draws your real days.
              </p>
            )}
          </div>

          <div className="rail-card">
            <span className="serif-h" style={{ fontSize: 20 }}>Gentle patterns</span>
            <div className="plist" style={{ marginTop: 10 }}>
              {patterns === null ? (
                <p className="sub">Looking at your data…</p>
              ) : patterns.length === 0 ? (
                <p className="sub">
                  Nothing yet. Patterns appear once a few weeks of real check-ins support
                  them — no guesses, ever.
                </p>
              ) : (
                patterns.map((p) => (
                  <div key={p.statement} className="pattern-row">
                    <div>
                      <strong>{p.statement}</strong>
                      <p style={{ color: "var(--cyan)" }}>{p.basis}</p>
                    </div>
                  </div>
                ))
              )}
            </div>
            <Link href="/patterns" className="link" style={{ display: "inline-block", marginTop: 10 }}>
              See everything CereBro remembers →
            </Link>
          </div>
        </div>
      </div>
    </>
  );
}
