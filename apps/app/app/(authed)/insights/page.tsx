"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { HeroCard, PageHeader, SectionTitle } from "@/components/ui";

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
      <PageHeader eyebrow="Computed from your week — never invented" title="Insights" />
      {error && <p className="error">{error}</p>}

      {insight && (
        <>
          <HeroCard tag={insight.period || "This week"} title={insight.headline} subtitle={insight.summary} />
          <SectionTitle title="How your week moved" />
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
        <>
          <SectionTitle title="Coming up" />
          <section className="card" aria-label="Upcoming nudges">
          {nudges.map((n) => (
            <div className="entry" key={n.id}>
              <strong>{n.title}</strong> <span className="tag">{n.kind}</span>
              <div className="meta">{n.body}</div>
              <div className="meta">{new Date(n.scheduled_for).toLocaleString()}</div>
            </div>
          ))}
          </section>
        </>
      )}
    </>
  );
}
