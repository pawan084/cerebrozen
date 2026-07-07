"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { Icon } from "@/components/icons";
import { API_URL } from "@/lib/api";

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

export default function Programs() {
  const [programs, setPrograms] = useState<Item[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
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
          className="media-hero"
          style={{
            background:
              "linear-gradient(120deg, rgba(90,82,201,0.5), rgba(20,16,44,0.3)), radial-gradient(circle at 85% 30%, rgba(166,139,255,0.3), transparent 45%), var(--night)",
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

        <div className="sec-head"><h2 className="serif-h">All programs</h2></div>
        <div className="program-grid">
          {programs.map((p, i) => (
            <div key={p.id} className="program-card">
              <div className="program-thumb" style={{ background: THUMBS[i % THUMBS.length] }} />
              <div className="program-body">
                <div className="meta">
                  {p.subtitle}
                  {p.premium && (
                    <span style={{ float: "right", color: "var(--warm)" }}>Premium</span>
                  )}
                </div>
                <h3>{p.title}</h3>
              </div>
            </div>
          ))}
        </div>

        <p className="footnote">
          Programs are curated from the same catalogue the iOS &amp; Android apps read. Your{" "}
          <Link href="/plan">daily plan</Link> adapts to them as you check in.
        </p>
      </div>
    </>
  );
}
