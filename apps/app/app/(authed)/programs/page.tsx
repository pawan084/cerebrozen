"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { WhyThisWorks } from "@/components/WhyThisWorks";
import { Icon } from "@/components/icons";
import { JourneyPath, type DayGuide } from "@/components/JourneyPath";
import { API_URL, api } from "@/lib/api";

// Same served catalogue the iOS/Android rails read — no hardcoded programs.
type Item = {
  id: string;
  title: string;
  subtitle: string;
  kind: string;
  duration_min: number;
  premium: boolean;
};

// Deterministic warm thumbnails so each program reads distinctly.
const THUMBS = [
  "linear-gradient(160deg,#8a7bf0,#5b52c9)",
  "linear-gradient(160deg,#8a5a6a,#3a2430)",
  "linear-gradient(160deg,#2f6a6a,#12302f)",
  "linear-gradient(160deg,#7a4a7a,#301640)",
];

// `today_guide` and `guides` are both additive — omitted for programs with no
// day guides, and by servers older than the migrations that added them. `guides`
// is the whole ordered week; `today_guide` stays for older clients and as the
// fallback when a server sends only that. Tolerate absence, never require it.
type Active = {
  content_id: string;
  title: string;
  day: number;
  days: number;
  completed: boolean;
  today_guide?: DayGuide | null;
  guides?: DayGuide[] | null;
};

export default function Programs() {
  const [programs, setPrograms] = useState<Item[]>([]);
  const [active, setActive] = useState<Active | null>(null);
  const [error, setError] = useState("");

  function refreshActive() {
    api<{ program: Active | null }>("/programs/active").then((r) => setActive(r.program)).catch(() => {});
  }

  async function enroll(id: string) {
    try {
      const r = await api<{ program: Active }>("/programs/enroll", {
        method: "POST",
        body: JSON.stringify({ content_id: id }),
      });
      setActive(r.program);
    } catch {
      setError("Couldn't enroll — try again.");
    }
  }

  async function leave() {
    try {
      await api("/programs/active", { method: "DELETE" });
      setActive(null);
    } catch {}
  }

  useEffect(() => {
    refreshActive();
    fetch(`${API_URL}/content?kind=program`)
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then(setPrograms)
      .catch(() => setError("Couldn't load programs."));
  }, []);

  const featured = programs[0];

  return (
    <>
      <AppHeader eyebrow="Programs" title="Guided journeys" />
      <div className="page-body">
        <section
          className="media-hero cz-in"
          style={{
            background:
              "linear-gradient(120deg, rgba(90,82,201,0.5), rgba(20,16,44,0.3)), radial-gradient(circle at 85% 30%, rgba(166,139,255,0.3), transparent 45%), #0e0c22",
          }}
        >
          <p className="eyebrow">Multi-day paths to a calmer baseline</p>
          <h2>{featured ? featured.title : "Guided programs"}</h2>
          <p>{featured ? featured.subtitle : "Start any time; go at your own pace."}</p>
          <Link
            href="/plan"
            className="pill-btn"
            style={{ marginTop: 18, alignSelf: "flex-start" }}
          >
            <Icon.play size={14} /> Begin with today's plan
          </Link>
          <span
            style={{
              position: "absolute", top: 30, right: 40, width: 96, height: 96, borderRadius: 22,
              display: "grid", placeItems: "center",
              background: "linear-gradient(160deg,#8a7bf0,#5b52c9)",
              boxShadow: "0 0 40px rgba(138,123,240,.4)",
            }}
            aria-hidden="true"
          />
        </section>

        {error && <p className="error">{error}</p>}

        {/* Active journey (ref "PROGRAM · DAY X OF Y") — one at a time. */}
        {active && (
          <section className="card cz-in cz-d1" style={{ marginTop: 6 }}>
            <p className="eyebrow" style={{ color: "var(--cyan)", marginBottom: 4 }}>
              Program · day {active.day} of {active.days}
            </p>
            <h3 style={{ margin: "0 0 6px" }}>{active.title}</h3>
            <p style={{ color: "var(--muted)", fontSize: 13, margin: 0 }}>
              {active.completed
                ? "Complete — beautifully done. Start another whenever you like."
                : "The day counts itself from when you started — showing up is the whole assignment."}
            </p>
            {/* The whole week, or — on a server that sends only `today_guide` —
                just today's. Blank titles AND bodies count as no guide, matching
                the mobile clients. */}
            {(() => {
              const week = (active.guides ?? []).filter(
                (g) => g.title.trim() || g.body.trim(),
              );
              if (week.length > 0) {
                return (
                  <div style={{ marginTop: 14, paddingTop: 12, borderTop: "1px solid var(--line)" }}>
                    <p className="eyebrow" style={{ marginBottom: 2 }}>The week ahead</p>
                    <p style={{ color: "var(--muted)", fontSize: 13, margin: "0 0 6px" }}>
                      Every day is open — read ahead, or go back. Nothing here locks.
                    </p>
                    <JourneyPath guides={week} currentDay={active.day} />
                  </div>
                );
              }
              const today = active.today_guide;
              if (!today || !(today.title.trim() || today.body.trim())) return null;
              return (
                <div style={{ marginTop: 14, paddingTop: 12, borderTop: "1px solid var(--line)" }}>
                  <p className="eyebrow" style={{ marginBottom: 4 }}>Today&apos;s guide</p>
                  {today.title.trim() && (
                    <h4 style={{ margin: "0 0 4px", fontSize: 14 }}>{today.title}</h4>
                  )}
                  {today.body.trim() && (
                    <p style={{ color: "var(--muted)", fontSize: 13, margin: 0 }}>{today.body}</p>
                  )}
                </div>
              );
            })()}
            <button
              onClick={leave}
              style={{ background: "none", border: "none", cursor: "pointer", font: "inherit", color: "var(--muted)", padding: 0, marginTop: 10 }}
            >
              Leave this journey
            </button>
          </section>
        )}

        <div className="sec-head"><h2 className="serif-h">All programs</h2></div>
        <div className="program-grid">
          {programs.map((p, i) => (
            <div key={p.id} className={`program-card cz-in cz-d${Math.min(i + 1, 6)}`}>
              <div className="program-thumb" style={{ background: THUMBS[i % THUMBS.length] }} />
              <div className="program-body">
                <div className="meta">
                  {p.subtitle}
                  {p.premium && (
                    <span style={{ float: "right", color: "var(--warm)" }}>Premium</span>
                  )}
                </div>
                <h3>{p.title}</h3>
                {active?.title !== p.title && (
                  <button
                    onClick={() => enroll(p.id)}
                    style={{ background: "none", border: "none", cursor: "pointer", font: "inherit", color: "var(--lav)", fontWeight: 700, padding: 0, marginTop: 8 }}
                  >
                    Start this journey
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>

        <WhyThisWorks text="Programs are evidence-informed — built on CBT and sleep-science techniques." />
        <p className="footnote">
          Programs are curated from the same catalogue the iOS &amp; Android apps read. Your{" "}
          <Link href="/plan">daily plan</Link> adapts to them as you check in.
        </p>
      </div>
    </>
  );
}
