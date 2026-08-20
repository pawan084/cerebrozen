"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { WhyThisWorks } from "@/components/WhyThisWorks";
import { ThoughtSort } from "@/components/ThoughtSort";
import { SensorySteps } from "@/components/RitualSteps";
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

// 5-4-3-2-1 grounding lives in components/RitualSteps (shared with the ritual
// builder) — its copy is hand-synced with Android strings.xml ground_step*.

export default function Toolkit() {
  return (
    <>
      <AppHeader eyebrow="Toolkit" title="Small ways to steady" />
      <div className="page-body">
        <section
          className="media-hero cz-in"
          style={{
            minHeight: 160,
            background:
              "linear-gradient(120deg, rgba(60,90,90,0.5), rgba(20,16,44,0.3)), radial-gradient(circle at 88% 30%, rgba(143,230,238,0.28), transparent 42%), #0e0c22",
          }}
        >
          <p className="eyebrow">Breathe</p>
          <h2 id="breathe">Box breathing</h2>
          <p>
            Four slow counts in, hold, out, hold — a simple way to steady a racing nervous
            system. Follow the orb for a few rounds.
          </p>
        </section>

        <section className="card cz-in cz-d1">
          <BoxBreather />
          <WhyThisWorks text="Paced breathing is used in clinical distress-tolerance and relaxation protocols. Slowing the breath activates the body's calming response." />
        </section>

        <div className="sec-head" id="ground"><h2 className="serif-h">Ground</h2></div>
        <section className="card">
          <SensorySteps />
          <WhyThisWorks text="Sensory grounding redirects attention from spiralling thoughts to the here-and-now — a widely taught anxiety-management skill." />
        </section>

        <div className="sec-head" id="reframe"><h2 className="serif-h">Reframe</h2></div>
        <section className="card">
          <ThoughtSort />
          <WhyThisWorks text="Learning to spot named thinking traps — all-or-nothing, catastrophising, 'should' statements — is the first step of cognitive restructuring, the core skill in CBT (Beck, Cognitive Therapy and the Emotional Disorders)." />
        </section>

        <div className="sec-head" id="settle"><h2 className="serif-h">Settle</h2></div>
        <section className="card">
          <h3 style={{ margin: "0 0 4px" }}>A place you can go</h3>
          <p className="sub">
            Two minutes building somewhere calm in detail — the light, the sounds, the ground under
            you. Eight lines, fifteen seconds each.
          </p>
          <Link href="/games/imagery" className="pill-btn tinted" style={{ marginTop: 12 }}>
            Start the visualization
          </Link>
          <WhyThisWorks text="Guided imagery is a standard relaxation component in stress and anxiety protocols. The detail is what does the work — which is why the prompts ask about sound and temperature rather than just telling you to picture somewhere nice." />
        </section>

        <section className="card">
          <h3 style={{ margin: "0 0 4px" }}>Body scan</h3>
          <p className="sub">
            Eight parts of the body, in order, noticing what is there without trying to change
            it. Move at your own pace or let it time each part for you.
          </p>
          <Link href="/games/bodyscan" className="pill-btn tinted" style={{ marginTop: 12 }}>
            Start the scan
          </Link>
          <WhyThisWorks text="The body scan is the first formal practice in MBSR and MBCT, and the instruction that does the work is the one people skip: notice, do not fix. Nothing here is scored and there is no relaxed enough to reach." />
        </section>

        {/* The builder is a door, not a section: it only sequences the tools
            above, and putting it first would suggest setup comes before use. */}
        <section className="card">
          <p className="eyebrow">Make it a routine</p>
          <h3 style={{ margin: "4px 0 4px" }}>Your ritual</h3>
          <p className="sub">
            String a few of these together and attach them to something you already do — after the
            morning coffee, before the laptop closes. The cue is what makes it happen.
          </p>
          <Link href="/games/ritual" className="pill-btn" style={{ marginTop: 12 }}>
            Build yours
          </Link>
        </section>

        <p className="footnote">
          More of the Toolkit — TIPP and calm play — arrives with the mobile apps.
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
