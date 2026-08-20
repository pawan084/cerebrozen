"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";

// Sleep insights, the web half of Android's `sleepinsights` route.
//
// The distinction from /sleep's own "Your rhythm" card matters: that card does
// its arithmetic in the browser over the last seven rows. This page asks the
// SERVER for the window, which is the only place `trend` and `enough_data` are
// computed (services/sleep.py) — and `enough_data` is the whole point. Under
// MIN_NIGHTS the API returns zeros, so every derived number here is gated on
// it rather than printed as "0h 0m", which would read as a measurement.

type Night = { date: string; bedtime: string; wake_time: string; quality: number; duration_min?: number };
type Summary = {
  nights: number;
  enough_data: boolean;
  avg_duration_min: number;
  avg_quality: number;
  bedtime_consistency_min: number;
  trend: "improving" | "steady" | "declining" | "not_enough_data";
};

const WINDOWS = [
  { id: "week", label: "Week", days: 7 },
  { id: "month", label: "Month", days: 30 },
  { id: "3m", label: "3 months", days: 90 },
] as const;

// Android's note applies verbatim: /sleep takes a ROW LIMIT and /sleep/summary
// takes a DAY COUNT, so passing the same number to both let the chart and the
// tiles describe different windows — seven nights spread over two months drawn
// under "Week". The rows are filtered by date here for the same reason.
function withinWindow(nights: Night[], days: number): Night[] {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - (days - 1));
  const iso = cutoff.toISOString().slice(0, 10);
  // A row whose date will not parse is KEPT rather than dropped — it is the
  // person's own history, and hiding it would be the worse error.
  return nights.filter((n) => !n.date || n.date >= iso);
}

const hhmm = (min: number) => `${Math.floor(min / 60)}h ${min % 60}m`;

// The direction sentence. `trend` is the server's word; these are the only
// four it emits, and none of them is a diagnosis or a promise.
const TREND_LINE: Record<Summary["trend"], string> = {
  improving: "Your rest quality has been rising across this window.",
  steady: "Your rest quality has held steady across this window.",
  declining: "Your rest quality has been drifting down across this window.",
  not_enough_data: "",
};

export default function SleepInsights() {
  const [win, setWin] = useState<(typeof WINDOWS)[number]["id"]>("week");
  const [nights, setNights] = useState<Night[] | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [error, setError] = useState<string | null>(null);

  const days = WINDOWS.find((w) => w.id === win)!.days;

  useEffect(() => {
    let live = true;
    setError(null);
    setNights(null);
    setSummary(null);
    Promise.all([
      api<Night[]>(`/sleep?limit=${days}`),
      api<Summary>(`/sleep/summary?days=${days}`),
    ])
      .then(([rows, s]) => {
        if (!live) return;
        setNights(withinWindow(rows, days));
        setSummary(s);
      })
      // Said, not swallowed: an empty chart and a blank sentence look exactly
      // like "you have not slept enough to say", which would be a lie.
      .catch(() => live && setError("We couldn't load your nights just now. Try again in a moment."));
    return () => {
      live = false;
    };
  }, [days]);

  const enough = summary?.enough_data === true;
  const chart = (nights ?? []).slice(0, 7).reverse();
  const bars = chart.map((n) => {
    const mins = n.duration_min ?? 0;
    // 10h is the top of the scale, and the floor keeps a logged night visible
    // rather than reading as a night that never happened.
    return Math.max(8, Math.min(88, Math.round((mins / 600) * 88)));
  });

  return (
    <>
      <AppHeader eyebrow="Trends without diagnosis" title="Sleep insights" />
      <div className="today-wrap">
        <p className="eyebrow">Sleep insights</p>
        <h1 className="today-greeting">Look for the pattern, not the score</h1>
        <p className="sub today-lede">
          Support tonight&apos;s sleep. There is no diagnosis here, no sleep score, and no
          promised outcome — only what your own logged nights say.
        </p>

        <div className="row" style={{ gap: 7, flexWrap: "wrap", margin: "14px 0" }} role="group" aria-label="Window">
          {WINDOWS.map((w) => (
            <button
              key={w.id}
              type="button"
              className="chip"
              aria-pressed={win === w.id}
              onClick={() => setWin(w.id)}
            >
              {w.label}
            </button>
          ))}
        </div>

        <section className="ds-card" aria-labelledby="si-stats">
          <h2 id="si-stats" className="sr-only">
            This window in numbers
          </h2>
          <div className="stat-tiles">
            {[
              { value: enough ? hhmm(summary!.avg_duration_min) : "—", label: "average" },
              { value: enough ? `${summary!.bedtime_consistency_min}m` : "—", label: "bedtime range" },
              { value: enough ? `${summary!.avg_quality.toFixed(1)}/5` : "—", label: "rest quality" },
            ].map((s) => (
              <div key={s.label} className="stat-tile">
                <div className="val">{s.value}</div>
                <div className="lbl">{s.label}</div>
              </div>
            ))}
          </div>

          {chart.length > 0 && (
            <div className="sleep-bars" aria-hidden="true">
              {bars.map((h, i) => (
                <div key={chart[i].date || i} className="sleep-bar-col">
                  <div className="sleep-bar" style={{ height: `${h}px` }} />
                  <small>{chart[i].date?.slice(-2)}</small>
                </div>
              ))}
            </div>
          )}
          {/* The chart is decorative; this is the same data as text, so a
              screen reader gets the nights rather than a shape. */}
          {chart.length > 0 && (
            <ul className="sr-only">
              {chart.map((n, i) => (
                <li key={`sr-${n.date || i}`}>
                  {n.date}: {n.duration_min ? hhmm(n.duration_min) : "duration not recorded"}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="ds-card" aria-labelledby="si-noticed" aria-live="polite">
          <h2 id="si-noticed" className="serif-h">
            What CereBro noticed
          </h2>
          <p className="sub">
            {error
              ? error
              : nights === null
                ? "Reading your nights…"
                : !enough
                  ? `Log at least three nights before CereBro describes a direction — there ${
                      (summary?.nights ?? 0) === 1 ? "is 1 night" : `are ${summary?.nights ?? 0} nights`
                    } in this window.`
                  : `${TREND_LINE[summary!.trend]} Drawn from ${summary!.nights} logged ${
                      summary!.nights === 1 ? "night" : "nights"
                    } — nothing here is a sleep score, and none of it is a diagnosis.`}
          </p>
          {enough && summary!.bedtime_consistency_min > 90 && (
            <p className="sub">
              A steadier bedtime — even an imperfect one — does more for sleep than extra
              hours. That is the one CBT-I principle this data supports.
            </p>
          )}
          <div className="ds-actions">
            <Link href="/sleep" className="text-btn">
              Log tonight
            </Link>
            <Link href="/account" className="text-btn">
              Reminders
            </Link>
          </div>
        </section>
      </div>
    </>
  );
}
