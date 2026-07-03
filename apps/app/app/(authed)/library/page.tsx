"use client";

import { useEffect, useState } from "react";
import { API_URL } from "@/lib/api";

// Public catalogue (no auth required) — the same /content the iOS rails read.
type Item = {
  id: string;
  title: string;
  subtitle: string;
  kind: string;
  duration_min: number;
  premium: boolean;
};

const KIND_LABELS: Record<string, string> = {
  wind_down: "Wind down tonight",
  sleep: "Sleep stories & sounds",
  soundscape: "Soundscapes",
  meditation: "Meditations",
  breath: "Breathwork",
  program: "Programs",
};

export default function Library() {
  const [items, setItems] = useState<Item[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    fetch(`${API_URL}/content`)
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then(setItems)
      .catch(() => setError("Couldn't load the library."));
  }, []);

  const kinds = Object.keys(KIND_LABELS).filter((k) => items.some((i) => i.kind === k));

  return (
    <>
      <p className="eyebrow">The living catalogue — served, not hardcoded</p>
      <h1>Library</h1>
      {error && <p className="error">{error}</p>}
      {kinds.map((kind) => (
        <section className="card" key={kind} aria-label={KIND_LABELS[kind]}>
          <h2>{KIND_LABELS[kind]}</h2>
          {items
            .filter((i) => i.kind === kind)
            .map((i) => (
              <div className="entry row" key={i.id}>
                <div className="grow">
                  <strong>{i.title}</strong>
                  <div className="meta">{i.subtitle}</div>
                </div>
                {i.duration_min > 0 && <span className="meta">{i.duration_min} min</span>}
                {i.premium && <span className="tag">premium</span>}
              </div>
            ))}
        </section>
      ))}
      <p className="footnote">
        Audio playback (soundscapes, stories, the mixer) lives in the iOS app for now.
      </p>
    </>
  );
}
