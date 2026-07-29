"use client";

// Guided imagery — building a calm place in detail, one sense at a time.
//
// Sixth adoption from the `workspace/cerebro` sibling build (its
// `GuidedImageryPage`): eight prompts, fifteen seconds each, two minutes
// total. The structure survived intact. The words did not, in one specific
// place, and it matters enough to write down:
//
//   The reference's sixth slide reads **"You are safe here. Nothing can harm
//   you."** An app cannot promise that, and safe-place imagery is exactly the
//   exercise where the promise can break — for someone carrying trauma, going
//   looking for a calm interior place is a well-known route to intrusive
//   material surfacing instead. Being told "nothing can harm you" at that
//   moment reads as the app being wrong about you, which is worse than the
//   app saying nothing. So the absolute reassurance is gone, the mechanism it
//   was reaching for is kept ("nothing here needs anything from you"), and the
//   screen says plainly, *before* you start, that stopping is a normal outcome
//   and where to go instead.
//
// Also changed: the reference tells you to close your eyes, then shows you
// eight screens of text. This one says what it actually expects — read a line,
// let your eyes rest, glance back when the next one lands.

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { WhyThisWorks } from "@/components/WhyThisWorks";
import { Icon } from "@/components/icons";

const LINES = [
  "Let your shoulders drop. Three slow breaths — out for longer than in.",
  "Bring to mind a place where you feel at ease. Real or imagined: a room, a shore, a path you know.",
  "Look around it. What's the light like? What colours are there?",
  "Listen from where you're standing. What's close by, and what's further off?",
  "Feel the ground holding you up. The air on your skin, and its temperature.",
  "Nothing here needs anything from you. You can put things down for a minute.",
  "When you're ready, let the place stay as it is. You know the way back to it.",
  "Come back to the room. One more slow breath, then open your eyes.",
];
const LINE_SECONDS = 15;

export default function GuidedImagery() {
  const [started, setStarted] = useState(false);
  const [done, setDone] = useState(false);
  const [i, setI] = useState(0);
  const [left, setLeft] = useState(LINE_SECONDS);
  const [paused, setPaused] = useState(false);

  // The countdown and the advance are two effects on purpose: advancing from
  // inside the setLeft updater would make it impure, and React double-invokes
  // updaters in StrictMode — which skips a line in dev.
  useEffect(() => {
    if (!started || done || paused) return;
    const t = setInterval(() => setLeft((s) => (s > 0 ? s - 1 : 0)), 1000);
    return () => clearInterval(t);
  }, [started, done, paused]);

  useEffect(() => {
    if (!started || done || left > 0) return;
    if (i >= LINES.length - 1) { setDone(true); return; }
    setI(i + 1);
    setLeft(LINE_SECONDS);
  }, [started, done, left, i]);

  function skip() {
    setLeft(LINE_SECONDS);
    if (i >= LINES.length - 1) return setDone(true);
    setI(i + 1);
  }

  if (!started) {
    return (
      <>
        <AppHeader eyebrow="Settle" title="A place you can go" />
        <div className="page-body">
          <section className="card">
            <p className="sub">
              Two minutes of building somewhere calm in detail — what it looks like, what you can
              hear from there, how the ground feels. Eight lines, fifteen seconds each. Read one,
              let your eyes rest, and glance back when the next lands.
            </p>
            {/* Said before starting, not after something goes wrong. */}
            <div className="crisis" style={{ marginTop: 14 }}>
              <strong>If it stops feeling calm, stop.</strong> Going looking for a quiet place can
              sometimes turn up the opposite. That&apos;s a normal thing for this exercise to do and
              not a failure of yours —{" "}
              <Link href="/games" style={{ color: "var(--lav)", fontWeight: 700 }}>
                5-4-3-2-1 grounding
              </Link>{" "}
              works in the other direction and is right here.
            </div>
            <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
              <button className="btn" onClick={() => setStarted(true)}>Begin</button>
              <Link href="/games" className="btn ghost">Back to the Toolkit</Link>
            </div>
            <WhyThisWorks text="Guided imagery is a standard relaxation component in stress and anxiety protocols. The detail is what does the work — which is why these prompts ask about sound, light and temperature rather than just telling you to picture somewhere nice." />
          </section>
        </div>
      </>
    );
  }

  if (done) {
    return (
      <>
        <AppHeader eyebrow="Settle" title="A place you can go" />
        <div className="page-body">
          <section className="card" style={{ textAlign: "center" }}>
            <h2 className="serif-h" style={{ fontSize: 22 }}>The place stays where it is.</h2>
            <p className="sub">You built it, so you can get back to it — it takes less time each go.</p>
            <div style={{ display: "flex", gap: 8, justifyContent: "center", marginTop: 14, flexWrap: "wrap" }}>
              <button
                className="btn ghost"
                onClick={() => { setI(0); setLeft(LINE_SECONDS); setDone(false); setPaused(false); }}
              >
                Again
              </button>
              <Link href="/games" className="btn ghost">Back to the Toolkit</Link>
            </div>
          </section>
        </div>
      </>
    );
  }

  return (
    <>
      <AppHeader eyebrow="Settle" title="A place you can go" />
      <div className="page-body">
        <section className="imagery-stage">
          <div className="imagery-glow" aria-hidden="true" />
          <div className="imagery-glow two" aria-hidden="true" />
          <p key={i} className="imagery-line" aria-live="polite">{LINES[i]}</p>
        </section>

        <div className="card">
          <div style={{ display: "flex", gap: 6 }} aria-hidden="true">
            {LINES.map((_, n) => (
              <div
                key={n}
                style={{
                  flex: 1, height: 4, borderRadius: 999,
                  background: n <= i ? "var(--lav)" : "var(--line)",
                }}
              />
            ))}
          </div>
          <div className="row" style={{ marginTop: 12, flexWrap: "wrap" }}>
            <button
              className="pill-btn tinted"
              onClick={() => setPaused((p) => !p)}
              aria-pressed={paused}
            >
              <Icon.play size={14} /> {paused ? "Resume" : "Pause"}
            </button>
            <button className="linklike" onClick={skip}>Skip ahead</button>
            <span className="grow" />
            <span className="meta">{paused ? "Paused" : `${left}s`}</span>
          </div>
          {/* Stopping is one click, always — the same posture as the caution
              on the way in. */}
          <p className="footnote">
            <button className="linklike" onClick={() => { setStarted(false); setI(0); setLeft(LINE_SECONDS); setPaused(false); }}>
              Stop here
            </button>
          </p>
        </div>
      </div>
    </>
  );
}
