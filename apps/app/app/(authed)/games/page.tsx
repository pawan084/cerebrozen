"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { WhyThisWorks } from "@/components/WhyThisWorks";
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

// 5-4-3-2-1 grounding — the sensory anchor exercise the iOS/Android Toolkit
// ships; copy hand-synced with Android strings.xml ground_step*.
const GROUND_STEPS = [
  { title: "5 things you can see", hint: "Look around — name five, slowly." },
  { title: "4 things you can feel", hint: "Textures, temperature, your feet on the floor." },
  { title: "3 things you can hear", hint: "Near, then far." },
  { title: "2 things you can smell", hint: "Or two scents you like." },
  { title: "1 thing you can taste", hint: "Or one slow, full breath." },
];

function Grounding() {
  const [step, setStep] = useState(0);
  const last = step === GROUND_STEPS.length - 1;
  const s = GROUND_STEPS[step];
  return (
    <div>
      <p className="eyebrow">5 · 4 · 3 · 2 · 1</p>
      <h3 style={{ margin: "4px 0 4px" }}>{s.title}</h3>
      <p className="sub">{s.hint}</p>
      <button
        className="pill-btn tinted"
        onClick={() => setStep(last ? 0 : step + 1)}
        style={{ marginTop: 10 }}
      >
        {last ? "Start over" : "Next"}
      </button>
      {step > 0 && (
        <button
          onClick={() => setStep(step - 1)}
          style={{ background: "none", border: "none", cursor: "pointer", font: "inherit", color: "var(--muted)", marginLeft: 12 }}
        >
          Back
        </button>
      )}
    </div>
  );
}

export default function Toolkit() {
  return (
    <>
      <AppHeader eyebrow="Toolkit" title="Small ways to steady" />
      <div className="page-body">
        <section
          className="media-hero"
          style={{
            minHeight: 160,
            background:
              "linear-gradient(120deg, rgba(60,90,90,0.5), rgba(20,16,44,0.3)), radial-gradient(circle at 88% 30%, rgba(143,230,238,0.28), transparent 42%), var(--night)",
          }}
        >
          <p className="eyebrow">Breathe</p>
          <h2>Box breathing</h2>
          <p>
            Four slow counts in, hold, out, hold — a simple way to steady a racing nervous
            system. Follow the orb for a few rounds.
          </p>
        </section>

        <section className="card">
          <BoxBreather />
          <WhyThisWorks text="Paced breathing is used in clinical distress-tolerance and relaxation protocols. Slowing the breath activates the body's calming response." />
        </section>

        <div className="sec-head"><h2 className="serif-h">Ground</h2></div>
        <section className="card">
          <Grounding />
          <WhyThisWorks text="Sensory grounding redirects attention from spiralling thoughts to the here-and-now — a widely taught anxiety-management skill." />
        </section>

        <p className="footnote">
          More of the Toolkit — reframing, TIPP and calm play — lives in the iOS &amp; Android apps.
        </p>
        {/* Crisis stays ≤2 clicks from every tool surface (REDESIGN §2.3). */}
        <p className="footnote">
          In a heavier moment, tools aren&apos;t enough —{" "}
          <Link href="/crisis" style={{ color: "var(--lav)", fontWeight: 700 }}>urgent support</Link> is right here.
        </p>
      </div>
    </>
  );
}
