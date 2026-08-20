"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";

type Mood = { mood: string; created_at: string };
// The honest pattern engine: server statements, each with the count that
// supports it. Nothing here is written on the client (/patterns is the same
// feed) — a claim about someone's life has to come from their own data.
type Pattern = { statement: string; basis: string };

const SCORE: Record<string, number> = { Great: 5, Good: 4, Okay: 3, Low: 2, Anxious: 1 };
const PARTS = ["at night", "in the morning", "in the afternoon", "in the evening"];

export default function Insights() {
  const [moods, setMoods] = useState<Mood[]>([]);
  const [calmSessions, setCalmSessions] = useState<number | null>(null);
  const [patterns, setPatterns] = useState<Pattern[] | null>(null);

  useEffect(() => {
    api<Mood[]>("/moods?limit=14").then(setMoods).catch(() => {});
    api<any>("/insights/weekly").then((w) => {
      const m = (w.metrics || []).find((x: any) => x.label === "Calm sessions");
      const n = parseInt(m?.value, 10);
      if (Number.isFinite(n)) setCalmSessions(n);
    }).catch(() => {});
    api<{ patterns: Pattern[] }>("/insights/patterns")
      .then((r) => setPatterns(r.patterns ?? []))
      .catch(() => setPatterns([]));
  }, []);

  const scores = moods.map((m) => SCORE[m.mood] ?? 3).reverse();
  const avg = scores.length ? scores.reduce((a, b) => a + b, 0) / scores.length : 0;
  const avgLabel = avg >= 4.2 ? "Bright" : avg >= 3 ? "Steady" : avg > 0 ? "Tender" : "—";
  const avgEmoji = avg >= 4.2 ? "😊" : avg >= 3 ? "🙂" : avg > 0 ? "😔" : "";

  // Drift is only stated when there are enough check-ins on both sides of the
  // window to compare — no "▲ gentler than last week" out of thin air.
  const drift = (() => {
    if (scores.length < 6) return "";
    const half = Math.floor(scores.length / 2);
    const mean = (xs: number[]) => xs.reduce((a, b) => a + b, 0) / xs.length;
    const gap = mean(scores.slice(half)) - mean(scores.slice(0, half));
    if (gap >= 0.4) return "a little brighter than the days before";
    if (gap <= -0.4) return "a little heavier than the days before";
    return "steady across these days";
  })();

  // When you check in, counted from your own check-ins (needs a real plurality).
  const timeOfDay = (() => {
    if (moods.length < 5) return "";
    const tally = [0, 0, 0, 0];
    for (const m of moods) {
      const h = new Date(m.created_at).getHours();
      tally[h < 5 || h >= 22 ? 0 : h < 12 ? 1 : h < 17 ? 2 : 3] += 1;
    }
    const top = tally.indexOf(Math.max(...tally));
    return tally[top] * 2 > moods.length ? PARTS[top] : "";
  })();

  const enoughForChart = scores.length >= 2;

  return (
    <>
      <AppHeader eyebrow="Insights" title="How you've been" />
      <div className="page-body">
        <div className="stat-tiles">
          <div className="stat-tile cz-in">
            <div className="lbl">Average mood</div>
            <div className="val">{avgLabel} {avgEmoji}</div>
            <div className="lbl" style={{ fontSize: 13 }}>
              {scores.length === 0
                ? "after your first check-in"
                : drift || `across ${scores.length} check-in${scores.length === 1 ? "" : "s"}`}
            </div>
          </div>
          <div className="stat-tile cz-in cz-d1">
            <div className="lbl">Calm sessions</div>
            <div className="val">{calmSessions ?? "—"}</div>
            <div className="lbl" style={{ fontSize: 13 }}>
              {calmSessions === null ? "not counted yet" : "this week"}
            </div>
          </div>
          <div className="stat-tile cz-in cz-d2">
            <div className="lbl">When you check in</div>
            <div className="val">{timeOfDay ? timeOfDay.replace(/^(at|in the) /, "") : "—"}</div>
            <div className="lbl" style={{ fontSize: 13 }}>
              {timeOfDay ? `most of your check-ins land ${timeOfDay}` : "a few more check-ins and this fills in"}
            </div>
          </div>
        </div>

        {/* rail-380 instead of an inline column template: an inline style beats
            the responsive media query and left this grid two-up on phones. */}
        <div className="dash-grid rail-380" style={{ marginTop: 20 }}>
          <div className="chart-card cz-in cz-d3">
            <h3>Mood, last 14 days</h3>
            <p className="sub">A gentle line, not a scoreboard.</p>
            {enoughForChart ? (
              <>
                <svg viewBox="0 0 560 210" style={{ width: "100%", height: 210 }} aria-hidden="true">
                  <defs><linearGradient id="ig" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="rgba(138,123,240,0.35)" /><stop offset="1" stopColor="rgba(138,123,240,0)" /></linearGradient></defs>
                  {(() => {
                    const P = scores.map((s, i) => [(i / (scores.length - 1)) * 540 + 10, 180 - ((s - 1) / 4) * 150]);
                    const line = P.map((p) => p.join(",")).join(" ");
                    const area = `10,180 ${line} 550,180`;
                    return (<>
                      <polygon points={area} fill="url(#ig)" />
                      <polyline points={line} fill="none" stroke="var(--lav-2)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                      {P.map((p, i) => <circle key={i} cx={p[0]} cy={p[1]} r="4" fill="var(--soft)" />)}
                    </>);
                  })()}
                </svg>
                <div className="chart-x"><span>2 weeks ago</span><span>1 week</span><span>Today</span></div>
              </>
            ) : (
              // No invented line: an empty chart says so, and offers the step.
              <div className="empty-note">
                <p>Your line starts after two check-ins — there {scores.length === 1 ? "is one" : "are none"} so far.</p>
                <Link href="/home" className="link">Check in on Home →</Link>
              </div>
            )}
          </div>

          <div className="rail-card cz-in cz-d4">
            <span className="serif-h" style={{ fontSize: 20 }}>Gentle patterns</span>
            <div className="plist" style={{ marginTop: 10 }}>
              {patterns === null ? (
                <p className="sub">Looking at your data…</p>
              ) : patterns.length === 0 ? (
                <p className="sub">
                  Nothing to say yet. Patterns appear only once enough of your own check-ins
                  support them — CereBro doesn&apos;t guess about your life.
                </p>
              ) : (
                patterns.map((p) => (
                  <div key={p.statement} className="pattern-row">
                    <div>
                      <strong>{p.statement}</strong>
                      <p>{p.basis}</p>
                    </div>
                  </div>
                ))
              )}
            </div>
            <Link href="/patterns" className="link" style={{ display: "inline-block", marginTop: 12 }}>
              See everything CereBro remembers →
            </Link>
            {/* This page recomputes a 14-day view in the browser; Trends asks
                the server for the series, which is where the mood-and-sleep
                link lives — and where it is withheld until it means
                something. */}
            <Link href="/insights/trends" className="link" style={{ display: "block", marginTop: 8 }}>
              Trends over a month or three, and how your nights sit against your days →
            </Link>
          </div>
        </div>
      </div>
    </>
  );
}
