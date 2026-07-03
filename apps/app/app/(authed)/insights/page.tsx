"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";

type Metric = { label: string; value: string; progress: number };
type Insight = { period: string; headline: string; summary: string; metrics: Metric[] };
type Nudge = { id: string; kind: string; title: string; body: string; scheduled_for: string; status: string };

export default function Insights() {
  const [insight, setInsight] = useState<Insight | null>(null);
  const [nudges, setNudges] = useState<Nudge[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api<Insight>("/insights/weekly").then(setInsight).catch(() => setError("Couldn't load insights."));
    api<Nudge[]>("/nudges").then(setNudges).catch(() => {});
  }, []);

  return (
    <>
      <p className="eyebrow">Computed from your week — never invented</p>
      <h1>Insights</h1>
      {error && <p className="error">{error}</p>}

      {insight && (
        <>
          <section className="card">
            <h2>{insight.headline}</h2>
            <p className="sub">{insight.summary}</p>
          </section>
          <section className="card" aria-label="Weekly metrics">
            {insight.metrics.map((m) => (
              <div className="entry" key={m.label}>
                <div className="row">
                  <strong className="grow">{m.label}</strong>
                  <span className="meta">{m.value}</span>
                </div>
                <div className="bar" role="progressbar" aria-valuenow={Math.round(m.progress * 100)} aria-valuemin={0} aria-valuemax={100} aria-label={m.label}>
                  <div className="bar-fill" style={{ width: `${Math.round(m.progress * 100)}%` }} />
                </div>
              </div>
            ))}
          </section>
        </>
      )}

      {nudges.length > 0 && (
        <section className="card" aria-label="Upcoming nudges">
          <h2>Coming up</h2>
          {nudges.map((n) => (
            <div className="entry" key={n.id}>
              <strong>{n.title}</strong> <span className="tag">{n.kind}</span>
              <div className="meta">{n.body}</div>
              <div className="meta">{new Date(n.scheduled_for).toLocaleString()}</div>
            </div>
          ))}
        </section>
      )}
    </>
  );
}
