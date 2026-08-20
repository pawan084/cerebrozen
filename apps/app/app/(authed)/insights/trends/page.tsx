"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { AppHeader } from "@/components/AppHeader";

// Trends — the web half of Android's `trends` route, over /insights/trends.
//
// Everything honest about this screen is decided by the server and simply
// obeyed here (services/trends.py): days with no data are ABSENT rather than
// zero, `enough_data` gates every summary number, and the mood↔sleep link is
// withheld with a machine-readable reason until enough overlapping days exist.
// Two points always correlate perfectly, which is exactly why two points must
// never be drawn as a finding — so this page renders the reason, not a number.

type MoodPoint = { date: string; ease: number; checkins: number; mood: string };
type SleepPoint = { date: string; duration_min: number; quality: number };
type Trends = {
  days: number;
  start: string;
  end: string;
  mood: { points: MoodPoint[]; days_logged: number; enough_data: boolean; average_ease: number | null };
  sleep: { points: SleepPoint[]; nights: number; enough_data: boolean; avg_duration_min: number | null };
  correlation: {
    available: boolean;
    pairs: number;
    coefficient: number | null;
    direction: "better_sleep_easier_days" | "better_sleep_harder_days" | "no_clear_link" | null;
    reason: "needs_more_days" | "no_variation" | null;
  };
};

const RANGES = [
  { days: 14, label: "2 weeks" },
  { days: 30, label: "Month" },
  { days: 90, label: "3 months" },
];

// The server's four machine-readable answers, in words. None of them is a
// causal claim — a correlation over your own logs is not a mechanism.
const DIRECTION: Record<NonNullable<Trends["correlation"]["direction"]>, string> = {
  better_sleep_easier_days: "On the days after your longer nights, the days tended to read easier.",
  better_sleep_harder_days: "On the days after your longer nights, the days tended to read harder.",
  no_clear_link: "There is no clear link between your nights and your days in this window.",
};
const WITHHELD: Record<NonNullable<Trends["correlation"]["reason"]>, string> = {
  needs_more_days:
    "Not enough days where both a night and a check-in exist. Two points always line up perfectly, which is why this stays blank rather than guessing.",
  no_variation:
    "Your logs barely vary across this window, so there is nothing for a link to be made of. That is a real answer, not a missing one.",
};

export default function TrendsPage() {
  const [days, setDays] = useState(30);
  const [data, setData] = useState<Trends | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    setData(null);
    setError(null);
    api<Trends>(`/insights/trends?days=${days}`)
      .then((t) => live && setData(t))
      .catch((e) =>
        live &&
        setError(
          e?.message === "unauthorized"
            ? "Your session expired — please sign in again."
            : "We couldn't load your trends just now. Try again in a moment.",
        ),
      );
    return () => {
      live = false;
    };
  }, [days]);

  const mood = data?.mood;
  const sleep = data?.sleep;
  // Ease runs 1–5 the same way the check-in intensity does.
  const moodBar = (ease: number) => Math.max(6, Math.round((ease / 5) * 78));
  const sleepBar = (min: number) => Math.max(6, Math.min(78, Math.round((min / 600) * 78)));

  return (
    <>
      <AppHeader eyebrow="Your own logs, over time" title="Trends" />
      <div className="today-wrap">
        <p className="sub today-lede">
          What you have logged, drawn as it is. Days you did not log are missing rather than
          drawn as zero — an unlogged day is not a bad day.
        </p>

        <div className="row" style={{ gap: 7, flexWrap: "wrap", margin: "14px 0" }} role="group" aria-label="Range">
          {RANGES.map((r) => (
            <button
              key={r.days}
              type="button"
              className="chip"
              aria-pressed={days === r.days}
              onClick={() => setDays(r.days)}
            >
              {r.label}
            </button>
          ))}
        </div>

        {error && <p className="error" role="alert">{error}</p>}
        {!data && !error && <p className="sub">Reading your logs…</p>}

        {data && (
          <>
            <section className="ds-card" aria-labelledby="tr-mood">
              <div className="ds-head">
                <h2 id="tr-mood" className="serif-h">
                  How the days read
                </h2>
                <span className="ds-badge">
                  {mood!.days_logged} {mood!.days_logged === 1 ? "day" : "days"} logged
                </span>
              </div>
              {mood!.points.length === 0 ? (
                <p className="sub">
                  Nothing logged in this window yet.{" "}
                  <Link href="/checkin" className="link">
                    Check in
                  </Link>{" "}
                  and this fills itself in.
                </p>
              ) : (
                <>
                  <div className="trend-bars" aria-hidden="true">
                    {mood!.points.map((p) => (
                      <div key={p.date} className="trend-col" title={`${p.date}: ${p.mood}`}>
                        <div className="trend-bar mood" style={{ height: `${moodBar(p.ease)}px` }} />
                      </div>
                    ))}
                  </div>
                  <ul className="sr-only">
                    {mood!.points.map((p) => (
                      <li key={`sr-${p.date}`}>
                        {p.date}: {p.mood}, {p.checkins} {p.checkins === 1 ? "check-in" : "check-ins"}
                      </li>
                    ))}
                  </ul>
                  <p className="sub">
                    {mood!.enough_data && mood!.average_ease !== null
                      ? `Across this window your days averaged ${mood!.average_ease} out of 5 for ease. That is an average of what you said, not a score of how you are.`
                      : "Not enough logged days yet to average — the bars above are still every day you did log."}
                  </p>
                </>
              )}
            </section>

            <section className="ds-card" aria-labelledby="tr-sleep">
              <div className="ds-head">
                <h2 id="tr-sleep" className="serif-h">
                  How the nights went
                </h2>
                <span className="ds-badge">
                  {sleep!.nights} {sleep!.nights === 1 ? "night" : "nights"}
                </span>
              </div>
              {sleep!.points.length === 0 ? (
                <p className="sub">
                  No nights logged in this window.{" "}
                  <Link href="/sleep" className="link">
                    Log tonight
                  </Link>
                  .
                </p>
              ) : (
                <>
                  <div className="trend-bars" aria-hidden="true">
                    {sleep!.points.map((p) => (
                      <div key={p.date} className="trend-col">
                        <div className="trend-bar sleep" style={{ height: `${sleepBar(p.duration_min)}px` }} />
                      </div>
                    ))}
                  </div>
                  <ul className="sr-only">
                    {sleep!.points.map((p) => (
                      <li key={`sr-${p.date}`}>
                        {p.date}: {Math.floor(p.duration_min / 60)}h {p.duration_min % 60}m, quality{" "}
                        {p.quality} of 5
                      </li>
                    ))}
                  </ul>
                  <p className="sub">
                    {sleep!.enough_data && sleep!.avg_duration_min !== null
                      ? `Averaging ${Math.floor(sleep!.avg_duration_min / 60)}h ${sleep!.avg_duration_min % 60}m a night.`
                      : "Not enough nights yet to average."}
                  </p>
                </>
              )}
            </section>

            <section className="ds-card" aria-labelledby="tr-link">
              <h2 id="tr-link" className="serif-h">
                Nights and days together
              </h2>
              <p className="sub">
                {data.correlation.available
                  ? `${DIRECTION[data.correlation.direction!]} Drawn from ${data.correlation.pairs} days where both exist. This is a pattern in your own logs — it is not a cause, and it is not advice.`
                  : WITHHELD[data.correlation.reason ?? "needs_more_days"]}
              </p>
              <div className="ds-actions">
                <Link href="/patterns" className="text-btn">
                  What CereBro has learned
                </Link>
                <Link href="/sleep/insights" className="text-btn">
                  Sleep insights
                </Link>
              </div>
            </section>
          </>
        )}
      </div>
    </>
  );
}
