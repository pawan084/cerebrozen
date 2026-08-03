"use client";

import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
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

// Deep-link filters (?kind=…, e.g. Home's Sounds tile). "sounds" is a composite —
// no single catalogue kind covers it — and any single kind passes through as-is.
const COMPOSITE_KINDS: Record<string, string[]> = {
  sounds: ["sleep", "soundscape"],
};

function LibraryCatalogue() {
  const [items, setItems] = useState<Item[]>([]);
  const [error, setError] = useState("");
  const kindParam = useSearchParams().get("kind");
  // Object.hasOwn: a crafted ?kind=constructor would otherwise resolve
  // prototype members and crash the render on filter.includes().
  const filter = kindParam
    ? Object.hasOwn(COMPOSITE_KINDS, kindParam)
      ? COMPOSITE_KINDS[kindParam]
      : Object.hasOwn(KIND_LABELS, kindParam)
        ? [kindParam]
        : undefined
    : undefined;

  useEffect(() => {
    fetch(`${API_URL}/content`)
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then(setItems)
      .catch(() => setError("Couldn't load the library."));
  }, []);

  const kinds = Object.keys(KIND_LABELS).filter((k) => (!filter || filter.includes(k)) && items.some((i) => i.kind === k));

  return (
    <>
      <div className="page-body">
      <p className="eyebrow">The living catalogue — served, not hardcoded</p>
      <h1>Library</h1>
      {error && <p className="error">{error}</p>}
      {filter && (
        <p className="footnote">
          A filtered view · <Link href="/library" className="link">browse the full library</Link>
        </p>
      )}
      {kinds.map((kind, i) => (
        <section className={`card cz-in cz-d${Math.min(i + 1, 6)}`} key={kind} aria-label={KIND_LABELS[kind]}>
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
        Items with narration play right here — the full soundscape mixer lives in the iOS &amp; Android apps.
      </p>
      </div>
    </>
  );
}

// useSearchParams needs a Suspense boundary for Next 14's static build.
export default function Library() {
  return (
    <Suspense>
      <LibraryCatalogue />
    </Suspense>
  );
}
