"use client";

import { useEffect, useState } from "react";
import { listMoods, weeklyInsights, Unavailable, type MoodEntry } from "@/lib/wellness";

/* A sparkline over the person's own check-ins. Intensity when we have it, else a
   flat mid-line — we never invent a trend we don't have data for. */
function Spark({ moods }: { moods: MoodEntry[] }) {
  const pts = moods.slice().reverse().map((m) => (typeof m.intensity === "number" && m.intensity > 0 ? m.intensity : 3));
  if (pts.length < 2) return null;
  const max = Math.max(...pts, 5);
  const step = 300 / (pts.length - 1);
  const d = pts.map((p, i) => `${(i * step).toFixed(1)},${(74 - (p / max) * 64).toFixed(1)}`).join(" ");
  return (
    <svg className="spark" viewBox="0 0 300 74" preserveAspectRatio="none" aria-label="Your mood trend">
      <polyline fill="none" stroke="url(#gi)" strokeWidth="2.5" points={d} />
      <defs><linearGradient id="gi" x1="0" x2="1"><stop offset="0" stopColor="#56c8c0" /><stop offset="1" stopColor="#f56b6b" /></linearGradient></defs>
    </svg>
  );
}

export default function InsightsPage() {
  const [moods, setMoods] = useState<MoodEntry[] | null>(null);
  const [weekly, setWeekly] = useState<Record<string, unknown> | null>(null);
  const [blocked, setBlocked] = useState<Unavailable | null>(null);

  useEffect(() => {
    listMoods().then((m) => { setMoods(m ?? []); setBlocked(null); })
      .catch((e) => { if (e instanceof Unavailable) setBlocked(e); setMoods([]); });
    weeklyInsights().then(setWeekly).catch(() => setWeekly(null));
  }, []);

  const hasWeekly = weekly && Object.keys(weekly).length > 0;

  return (
    <div className="page">
      <div className="page-head"><div><div className="eyebrow">Insights</div><h1>Your patterns</h1></div></div>

      <div className="dash">
        <div className="col">
          <div className="card">
            <div className="sec-title" style={{ margin: "0 0 8px" }}><h3>Mood trend</h3></div>
            {moods === null ? <p className="placeholder">Loading…</p>
              : moods.length >= 2 ? <Spark moods={moods} />
                : (
                  <p className="placeholder">
                    {blocked?.reason === "consent"
                      ? <>Turn on <strong>Mood history</strong> in Settings and your trend builds here.</>
                      : blocked?.reason === "disabled"
                        ? <>Self-report wellness isn&rsquo;t enabled for your workspace yet.</>
                        : <>Check in a couple of times and your trend appears here.</>}
                  </p>
                )}
            {moods && moods.length > 0 && (
              <p className="placeholder" style={{ marginTop: 10 }}>{moods.length} check-in{moods.length === 1 ? "" : "s"} recorded.</p>
            )}
          </div>

          <div className="card">
            <div className="sec-title" style={{ margin: "0 0 8px" }}><h3>Your week</h3></div>
            {hasWeekly
              ? <pre className="weekly">{JSON.stringify(weekly, null, 2)}</pre>
              : <p className="placeholder">Your weekly summary appears once you&rsquo;ve had a session or two.</p>}
          </div>
        </div>

        <div className="col">
          <div className="card">
            <h3>Yours, and only yours</h3>
            <p className="placeholder">
              Personal insight for you — never a report about you. Your employer only ever sees
              anonymised, cohort-floored counts; no transcript, journal or commitment body ever
              reaches an admin surface.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
