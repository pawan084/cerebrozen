"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

type Phase = { name: string; secs: number; scale: number };
const PRESETS: Record<string, { label: string; sub: string; phases: Phase[] }> = {
  box: {
    label: "Box",
    sub: "4 · 4 · 4 · 4 — steady and grounding",
    phases: [
      { name: "Breathe in", secs: 4, scale: 1 },
      { name: "Hold", secs: 4, scale: 1 },
      { name: "Breathe out", secs: 4, scale: 0.5 },
      { name: "Hold", secs: 4, scale: 0.5 },
    ],
  },
  "478": {
    label: "4-7-8",
    sub: "in 4 · hold 7 · out 8 — for winding down",
    phases: [
      { name: "Breathe in", secs: 4, scale: 1 },
      { name: "Hold", secs: 7, scale: 1 },
      { name: "Breathe out", secs: 8, scale: 0.5 },
    ],
  },
  coherent: {
    label: "Coherent",
    sub: "5 · 5 — even and calming",
    phases: [
      { name: "Breathe in", secs: 5, scale: 1 },
      { name: "Breathe out", secs: 5, scale: 0.5 },
    ],
  },
};

export default function Breathe() {
  const [key, setKey] = useState<string>("box");
  const [running, setRunning] = useState(false);
  const [idx, setIdx] = useState(0);
  const [cycles, setCycles] = useState(0);
  const [remain, setRemain] = useState(0);
  const preset = PRESETS[key];
  const phase = preset.phases[idx];

  useEffect(() => {
    if (!running) return;
    setRemain(phase.secs);
    const tick = setInterval(() => setRemain((r) => Math.max(0, r - 1)), 1000);
    const next = setTimeout(() => {
      const ni = (idx + 1) % preset.phases.length;
      if (ni === 0) setCycles((c) => c + 1);
      setIdx(ni);
    }, phase.secs * 1000);
    return () => { clearInterval(tick); clearTimeout(next); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [running, idx, key]);

  function start() { setIdx(0); setCycles(0); setRunning(true); }
  function stop() { setRunning(false); setIdx(0); setRemain(0); }

  const scale = running ? phase.scale : 0.5;
  const dur = running ? phase.secs : 0.6;

  return (
    <div className="page tool-page">
      <div className="page-head">
        <div>
          <div className="eyebrow"><Link href="/tools" className="link-accent">Tools</Link> · Breathe</div>
          <h1>Breathing</h1>
        </div>
      </div>

      <div className="breathe">
        <div className="orb-wrap">
          <div className="b-orb" style={{ transform: `scale(${scale})`, transitionDuration: `${dur}s` }} aria-hidden="true" />
          <div className="b-label" aria-live="polite">
            <span className="b-phase">{running ? phase.name : "Ready"}</span>
            {running && <span className="b-count">{remain}</span>}
          </div>
        </div>

        <p className="b-sub">{preset.sub}</p>

        <div className="seg" role="radiogroup" aria-label="Breathing pattern">
          {Object.entries(PRESETS).map(([k, p]) => (
            <button key={k} type="button" role="radio" aria-checked={key === k}
              className={`seg-btn ${key === k ? "on" : ""}`} disabled={running} onClick={() => setKey(k)}>
              {p.label}
            </button>
          ))}
        </div>

        <div className="b-actions">
          {running
            ? <button className="primary" onClick={stop}>Stop</button>
            : <button className="primary" onClick={start}>Start</button>}
          {cycles > 0 && <span className="placeholder">{cycles} cycle{cycles === 1 ? "" : "s"} · nicely done</span>}
        </div>

        <p className="placeholder b-why">
          Slower exhales gently tell your nervous system it&rsquo;s safe to settle. A few rounds is enough.
        </p>
      </div>
    </div>
  );
}
