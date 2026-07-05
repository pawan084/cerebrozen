"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { HeroCard, PageHeader, SectionTitle } from "@/components/ui";

// Mirrors the iOS morning check-in (docs/SLEEP_TRACKING.md): felt quality +
// wall-clock times, one entry per morning (server upserts by date). Awareness
// framing only — never a measurement claim.
const QUALITY_WORDS = ["Rough", "Poor", "Okay", "Good", "Rested"];

type SleepLog = {
  id: string;
  date: string;
  bedtime: string;
  wake_time: string;
  quality: number;
  awakenings: number;
  duration_min: number;
};

type Summary = {
  nights: number;
  enough_data: boolean;
  avg_duration_min: number;
  avg_quality: number;
  bedtime_consistency_min: number;
  trend: string;
};

function todayLocalISO(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function fmt(min: number): string {
  return `${Math.floor(min / 60)}h ${String(min % 60).padStart(2, "0")}m`;
}

export default function Sleep() {
  const [quality, setQuality] = useState(0);
  const [bedtime, setBedtime] = useState("23:00");
  const [wakeTime, setWakeTime] = useState("07:00");
  const [awakenings, setAwakenings] = useState(0);
  const [logs, setLogs] = useState<SleepLog[]>([]);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void reload();
  }, []);

  async function reload() {
    try {
      const [list, sum] = await Promise.all([
        api<SleepLog[]>("/sleep?limit=14"),
        api<Summary>("/sleep/summary"),
      ]);
      setLogs(list);
      setSummary(sum);
      const today = list.find((l) => l.date === todayLocalISO());
      if (today) {
        setQuality(today.quality);
        setBedtime(today.bedtime.slice(0, 5));
        setWakeTime(today.wake_time.slice(0, 5));
        setAwakenings(today.awakenings);
      } else if (list[0]) {
        setBedtime(list[0].bedtime.slice(0, 5));
        setWakeTime(list[0].wake_time.slice(0, 5));
      }
    } catch {}
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (quality === 0 || busy) return;
    setBusy(true);
    try {
      await api("/sleep", {
        method: "POST",
        body: JSON.stringify({
          date: todayLocalISO(),
          bedtime: `${bedtime}:00`,
          wake_time: `${wakeTime}:00`,
          quality,
          awakenings,
        }),
      });
      setSaved(true);
      await reload();
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader eyebrow="Premium sleep hub" title="Sleep diary" />
      <HeroCard
        accent="sleep"
        tag="This morning"
        title="How did you sleep?"
        subtitle="A 20-second morning check-in — how you slept, not a measurement. The full sleep hub (soundscapes, stories, playback) lives in the iOS app."
      />

      <form className="card" onSubmit={save} aria-label="Morning check-in">
        <h2>Morning check-in</h2>
        <p className="sub">How rested do you feel?</p>
        <div className="quality-row" role="radiogroup" aria-label="Sleep quality">
          {QUALITY_WORDS.map((word, i) => (
            <button
              key={word}
              type="button"
              className={`pick${quality === i + 1 ? " selected" : ""}`}
              role="radio"
              aria-checked={quality === i + 1}
              onClick={() => setQuality(i + 1)}
            >
              {word}
            </button>
          ))}
        </div>
        <div className="row">
          <label className="field grow">
            <span>In bed around</span>
            <input type="time" value={bedtime} onChange={(e) => setBedtime(e.target.value)} required />
          </label>
          <label className="field grow">
            <span>Woke up around</span>
            <input type="time" value={wakeTime} onChange={(e) => setWakeTime(e.target.value)} required />
          </label>
          <label className="field grow">
            <span>Woke during night</span>
            <input
              type="number"
              min={0}
              max={20}
              value={awakenings}
              onChange={(e) => setAwakenings(Number(e.target.value))}
            />
          </label>
        </div>
        {saved && <p className="success" role="status">Saved — one entry per morning, edits welcome.</p>}
        <button className="btn" disabled={quality === 0 || busy}>
          {busy ? "Saving…" : "Save check-in"}
        </button>
        <p className="footnote">Roughly is perfect — this is awareness, not tracking accuracy.</p>
      </form>

      <SectionTitle title="Last 7 nights" trailing={summary && summary.enough_data ? `avg ${fmt(summary.avg_duration_min)}` : undefined} />
      <section className="card" aria-label="Weekly summary">
        {summary && summary.enough_data ? (
          <p className="sub">
            {summary.nights} nights logged · avg {fmt(summary.avg_duration_min)} in bed · felt{" "}
            {summary.avg_quality}/5 · trend {summary.trend.replace("_", " ")}
          </p>
        ) : (
          <p className="sub">
            {summary && summary.nights > 0
              ? `Log ${3 - summary.nights} more morning${summary.nights === 2 ? "" : "s"} to unlock your weekly view.`
              : "Log your first morning to start seeing your nights here."}
          </p>
        )}
      </section>

      {logs.length > 0 && (
        <><SectionTitle title="History" />
        <section className="card" aria-label="Diary history">
          {logs.map((l) => (
            <div className="entry" key={l.id}>
              <strong>{l.date}</strong>{" "}
              <span className="meta">
                · {l.bedtime.slice(0, 5)} → {l.wake_time.slice(0, 5)} · {fmt(l.duration_min)} · felt {l.quality}/5
                {l.awakenings > 0 ? ` · woke ${l.awakenings}×` : ""}
              </span>
            </div>
          ))}
        </section>
        </>
      )}
    </>
  );
}
