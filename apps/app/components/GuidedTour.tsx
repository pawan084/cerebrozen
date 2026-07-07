"use client";

import { useEffect, useState } from "react";

// First-run guided tour (ref GUIDED TOUR OVERLAY): four gentle stops over
// Home, shown once per browser (localStorage). Same copy as iOS/Android.
const STOPS: { label: string; caption: string }[] = [
  {
    label: "Check in daily",
    caption:
      "One tap tells CereBro how you're arriving — plans, insights and starters all personalize from it.",
  },
  {
    label: "Your plan adapts",
    caption:
      "Three small steps a day, rebuilt from your check-ins and sleep diary. Open Plan any time.",
  },
  {
    label: "Talk it through",
    caption:
      "A voice companion that listens first. It's AI — never a therapist, and always honest about that.",
  },
  {
    label: "Private by default",
    caption:
      "Nothing is remembered without your say-so. Change anything under Account → Privacy.",
  },
];

const KEY = "cb_tour_done";

export function GuidedTour() {
  const [idx, setIdx] = useState(0);
  const [show, setShow] = useState(false);

  useEffect(() => {
    try {
      if (!localStorage.getItem(KEY)) setShow(true);
    } catch {}
  }, []);

  function finish() {
    try {
      localStorage.setItem(KEY, "1");
    } catch {}
    setShow(false);
  }

  if (!show) return null;
  const stop = STOPS[idx];
  const last = idx === STOPS.length - 1;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Guided tour"
      style={{
        position: "fixed", inset: 0, zIndex: 80,
        background: "rgba(14,12,34,0.82)",
        display: "flex", alignItems: "flex-end", justifyContent: "center",
      }}
    >
      <div
        style={{
          margin: 16, width: "100%", maxWidth: 560,
          background: "var(--night-top)", border: "1px solid var(--line)",
          borderRadius: 22, padding: "20px 22px",
        }}
      >
        <p className="eyebrow" style={{ color: "var(--cyan)", marginBottom: 6 }}>
          Guided tour · {idx + 1} of {STOPS.length}
        </p>
        <h3 style={{ margin: "0 0 6px" }}>{stop.label}</h3>
        <p style={{ color: "var(--muted)", margin: 0 }}>{stop.caption}</p>
        <div style={{ display: "flex", alignItems: "center", marginTop: 14 }}>
          <div style={{ display: "flex", gap: 6, flex: 1 }} aria-hidden="true">
            {STOPS.map((_, i) => (
              <span
                key={i}
                style={{
                  width: 7, height: 7, borderRadius: "50%",
                  background: i === idx ? "var(--lav)" : "var(--line)",
                }}
              />
            ))}
          </div>
          <button style={{ background: "none", border: "none", cursor: "pointer", font: "inherit", fontWeight: 600, color: "var(--muted)" }} onClick={finish}>
            Skip
          </button>
          <button
            style={{ background: "none", border: "none", cursor: "pointer", font: "inherit", color: "var(--lav)", fontWeight: 700, marginLeft: 14 }}
            onClick={() => (last ? finish() : setIdx(idx + 1))}
          >
            {last ? "Let's begin" : "Next"}
          </button>
        </div>
      </div>
    </div>
  );
}
