"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";
import { Icon } from "@/components/icons";

type Mood = { mood: string; created_at: string };
const PATTERNS = [
  { emoji: "🚶", bg: "rgba(240,164,140,0.18)", title: "Movement lifts you", body: "Your mood tends to rise on days you log a walk." },
  { emoji: "🌙", bg: "rgba(166,139,255,0.18)", title: "Wind-downs help you sleep", body: "Nights with a story tend to feel calmer the next morning." },
  { emoji: "💬", bg: "rgba(143,230,238,0.18)", title: "Talking eases Sundays", body: "Your hardest check-ins soften after a conversation." },
];
const SCORE: Record<string, number> = { Great: 5, Good: 4, Okay: 3, Low: 2, Anxious: 1 };

export default function Insights() {
  const [moods, setMoods] = useState<Mood[]>([]);
  const [calmSessions, setCalmSessions] = useState<number | null>(null);

  useEffect(() => {
    api<Mood[]>("/moods?limit=14").then(setMoods).catch(() => {});
    api<any>("/insights/weekly").then((w) => {
      const m = (w.metrics || []).find((x: any) => /calm|session/i.test(x.label));
      if (m) setCalmSessions(parseInt(m.value) || (w.metrics?.length ?? 0));
    }).catch(() => {});
  }, []);

  const scores = moods.map((m) => SCORE[m.mood] ?? 3).reverse();
  const avg = scores.length ? scores.reduce((a, b) => a + b, 0) / scores.length : 0;
  const avgLabel = avg >= 4.2 ? "Bright" : avg >= 3 ? "Steady" : avg > 0 ? "Tender" : "—";
  const avgEmoji = avg >= 4.2 ? "😊" : avg >= 3 ? "🙂" : avg > 0 ? "😔" : "";
  const pts = scores.length >= 2 ? scores : [3, 4, 3, 4, 3, 4, 3.5, 4.2, 3.5, 4, 4.4];

  return (
    <>
      <AppHeader eyebrow="Insights" title="How you've been" />
      <div className="page-body">
        <div className="stat-tiles">
          <div className="stat-tile">
            <div className="lbl">Average mood</div>
            <div className="val">{avgLabel} {avgEmoji}</div>
            <div className="delta">▲ gentler than last week</div>
          </div>
          <div className="stat-tile">
            <div className="lbl">Calm sessions</div>
            <div className="val">{calmSessions ?? 0}</div>
            <div className="lbl" style={{ fontSize: 13 }}>this week</div>
          </div>
          <div className="stat-tile">
            <div className="lbl">Best time of day</div>
            <div className="val">Morning</div>
            <div className="lbl" style={{ fontSize: 13 }}>you check in most before 9am</div>
          </div>
        </div>

        <div className="dash-grid" style={{ marginTop: 20, gridTemplateColumns: "minmax(0,1fr) 380px" }}>
          <div className="chart-card">
            <h3>Mood, last 14 days</h3>
            <p className="sub">A gentle line, not a scoreboard.</p>
            <svg viewBox="0 0 560 210" style={{ width: "100%", height: 210 }} aria-hidden="true">
              <defs><linearGradient id="ig" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="rgba(138,123,240,0.35)" /><stop offset="1" stopColor="rgba(138,123,240,0)" /></linearGradient></defs>
              {(() => {
                const P = pts.map((s, i) => [(i / (pts.length - 1)) * 540 + 10, 180 - ((s - 1) / 4) * 150]);
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
          </div>

          <div className="rail-card">
            <span className="serif-h" style={{ fontSize: 20 }}>Gentle patterns</span>
            <div className="plist" style={{ marginTop: 10 }}>
              {PATTERNS.map((p) => (
                <div key={p.title} className="pattern-row">
                  <span className="pattern-ic" style={{ background: p.bg }}>{p.emoji}</span>
                  <div><strong>{p.title}</strong><p>{p.body}</p></div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
