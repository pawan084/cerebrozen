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
  audio_url: string;
};

// Relative "/media/…" (backend-minted narration) resolves against the API base;
// admin-pasted absolute URLs pass through.
function mediaSrc(url: string): string {
  if (!url) return "";
  return url.startsWith("/") ? `${API_URL}${url}` : url;
}

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
      <div className="page-body">
      <p className="eyebrow">The living catalogue — served, not hardcoded</p>
      <h1>Library</h1>
      {error && <p className="error">{error}</p>}
      {kinds.map((kind) => (
        <section className="card" key={kind} aria-label={KIND_LABELS[kind]}>
          <h2>{KIND_LABELS[kind]}</h2>
          {items
            .filter((i) => i.kind === kind)
            .map((i) => (
              <div className="entry" key={i.id}>
                <div className="row">
                  <div className="grow">
                    <strong>{i.title}</strong>
                    <div className="meta">{i.subtitle}</div>
                  </div>
                  {i.duration_min > 0 && <span className="meta">{i.duration_min} min</span>}
                  {i.premium && <span className="tag">premium</span>}
                </div>
                {i.audio_url && (
                  <audio
                    controls
                    preload="none"
                    src={mediaSrc(i.audio_url)}
                    aria-label={`Play ${i.title}`}
                    style={{ width: "100%", marginTop: 8 }}
                  />
                )}
              </div>
            ))}
        </section>
      ))}
      <p className="footnote">
        Items with narration play right here — the full mixer and offline playback live in the iOS &amp; Android apps.
      </p>
      </div>
    </>
  );
}
