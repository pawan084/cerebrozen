"use client";

import { useEffect, useState } from "react";
import { forgetMemory, getPatterns, listMoods, weeklyInsights, Unavailable, type MoodEntry, type Patterns } from "@/lib/wellness";

/* A sparkline over the person's own check-ins. Intensity when we have it, else a
   flat mid-line — we never invent a trend we don't have data for. */
function Spark({ moods }: { moods: MoodEntry[] }) {
  // Only real intensities — never substitute a mid-value for a missing one, which
  // would draw a trend the data doesn't support.
  const pts = moods.slice().reverse()
    .map((m) => m.intensity)
    .filter((v): v is number => typeof v === "number" && v > 0);
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

function humanize(k: string): string {
  return k.replace(/[_-]+/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

/* Render the weekly read as a human summary — a headline plus labelled metrics —
   instead of dumping raw JSON. Nested/object fields are skipped, not stringified. */
function WeeklySummary({ data }: { data: Record<string, unknown> }) {
  const headline =
    typeof data.headline === "string" ? data.headline
    : typeof data.summary === "string" ? data.summary
    : null;
  const scalars = Object.entries(data).filter(
    ([k, v]) => k !== "headline" && k !== "summary" &&
      (typeof v === "string" || typeof v === "number" || typeof v === "boolean")
  );
  if (!headline && scalars.length === 0) {
    return <p className="placeholder">Your weekly summary appears once you&rsquo;ve had a session or two.</p>;
  }
  return (
    <div className="weekly-summary">
      {headline && <p className="w-headline">{headline}</p>}
      {scalars.length > 0 && (
        <ul className="w-metrics">
          {scalars.map(([k, v]) => (
            <li key={k}>
              <span className="w-k">{humanize(k)}</span>
              <span className="w-v">{typeof v === "boolean" ? (v ? "Yes" : "No") : String(v)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default function InsightsPage() {
  const [moods, setMoods] = useState<MoodEntry[] | null>(null);
  const [weekly, setWeekly] = useState<Record<string, unknown> | null>(null);
  const [weeklyLoading, setWeeklyLoading] = useState(true);
  const [blocked, setBlocked] = useState<Unavailable | null>(null);
  const [patterns, setPatterns] = useState<Patterns | null>(null);
  const [patternsLoading, setPatternsLoading] = useState(true);
  const [forgetting, setForgetting] = useState(false);

  useEffect(() => {
    listMoods().then((m) => { setMoods(m ?? []); setBlocked(null); })
      .catch((e) => { if (e instanceof Unavailable) setBlocked(e); setMoods([]); });
    weeklyInsights().then(setWeekly).catch(() => setWeekly(null)).finally(() => setWeeklyLoading(false));
    getPatterns().then(setPatterns).catch(() => setPatterns(null)).finally(() => setPatternsLoading(false));
  }, []);

  const hasWeekly = weekly && Object.keys(weekly).length > 0;
  const statements = patterns?.statements ?? [];

  async function forget() {
    if (forgetting) return;
    if (!window.confirm("Forget everything the coach has learned about you? Your journal, mood and sleep logs are kept — only what the coach inferred is cleared. This can't be undone.")) return;
    setForgetting(true);
    try {
      await forgetMemory();
      setPatterns({ enough_data: false, statements: [] });
    } catch { /* leave as-is; the user can retry */ }
    finally { setForgetting(false); }
  }

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
            {weeklyLoading
              ? <p className="placeholder">Loading your week…</p>
              : hasWeekly
                ? <WeeklySummary data={weekly!} />
                : <p className="placeholder">Your weekly summary appears once you&rsquo;ve had a session or two.</p>}
          </div>

          <div className="card">
            <div className="sec-title" style={{ margin: "0 0 8px" }}>
              <h3>What your coach has learned</h3>
              {statements.length > 0 && (
                <button className="tool" onClick={forget} disabled={forgetting}>
                  {forgetting ? "Forgetting…" : "Forget everything"}
                </button>
              )}
            </div>
            {patternsLoading ? <p className="placeholder">Loading…</p>
              : statements.length === 0 ? (
                <p className="placeholder">
                  {patterns?.enough_data === false
                    ? "Nothing yet — the coach only forms a picture once it has enough of your own check-ins and sessions to stand behind it."
                    : "Nothing here yet."}
                </p>
              ) : (
                <ul className="mem-list">
                  {statements.map((s, i) => (
                    <li key={i} className="mem-item">
                      <span className="mem-text">{s.text || s.statement}</span>
                      {(s.basis || s.count != null) && (
                        <span className="mem-basis">{s.basis || `${s.count} check-in${s.count === 1 ? "" : "s"}`}</span>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            <p className="placeholder" style={{ marginTop: 12, fontSize: 12 }}>
              Everything the coach knows, with the counts behind it — and a button to erase it. A claim you can&rsquo;t audit is a horoscope.
            </p>
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
