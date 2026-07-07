"use client";

import { useEffect, useRef, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { Icon } from "@/components/icons";

// Box breathing: four equal counts — in, hold, out, hold — the same pattern the
// iOS/Android apps ship. Reuses the onboarding breathing-orb classes.
const PHASES = [
  { label: "Breathe in", ms: 4000, state: "in" },
  { label: "Hold", ms: 4000, state: "hold" },
  { label: "Breathe out", ms: 4000, state: "out" },
  { label: "Hold", ms: 4000, state: "hold" },
] as const;

function BoxBreather() {
  const [running, setRunning] = useState(false);
  const [phase, setPhase] = useState(0);
  const [rounds, setRounds] = useState(0);
  const timer = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    if (!running) return;
    timer.current = setTimeout(() => {
      setPhase((p) => {
        const next = (p + 1) % PHASES.length;
        if (next === 0) setRounds((r) => r + 1);
        return next;
      });
    }, PHASES[phase].ms);
    return () => clearTimeout(timer.current);
  }, [running, phase]);

  function toggle() {
    if (running) {
      setRunning(false);
      clearTimeout(timer.current);
    } else {
      setPhase(0);
      setRunning(true);
    }
  }

  const state = running ? PHASES[phase].state : "";
  return (
    <div className="onb-breathe">
      <p className="onb-breathe-label">{running ? PHASES[phase].label : "Box breathing · 4·4·4·4"}</p>
      <div className={`onb-breathe-orb ${state}`} aria-hidden="true" />
      <button className="pill-btn tinted" onClick={toggle} aria-pressed={running}>
        <Icon.play size={14} /> {running ? "Stop" : "Start"}
      </button>
      {rounds > 0 && (
        <p className="meta">{rounds} {rounds === 1 ? "round" : "rounds"} complete</p>
      )}
    </div>
  );
}

export default function Games() {
  return (
    <>
      <AppHeader eyebrow="Calm play" title="Games to settle the mind" />
      <div className="page-body">
        <section
          className="media-hero"
          style={{
            minHeight: 160,
            background:
              "linear-gradient(120deg, rgba(60,90,90,0.5), rgba(20,16,44,0.3)), radial-gradient(circle at 88% 30%, rgba(143,230,238,0.28), transparent 42%), var(--night)",
          }}
        >
          <p className="eyebrow">Featured · playable now</p>
          <h2>Box breathing</h2>
          <p>
            Four slow counts in, hold, out, hold — a simple way to steady a racing nervous
            system. Follow the orb for a few rounds.
          </p>
        </section>

        <section className="card">
          <BoxBreather />
        </section>

        <p className="footnote">
          More calm play — bubble-pop and 5·4·3·2·1 grounding — lives in the iOS &amp; Android apps.
        </p>
      </div>
    </>
  );
}
