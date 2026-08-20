"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { WhyThisWorks } from "@/components/WhyThisWorks";

// Body scan — the web half of Android's `bodyscan` route. Same eight parts,
// same instructions, same 15 seconds a part when auto timing is on.
//
// Two behaviours are ported deliberately rather than reinvented:
//
//  * **Pause holds the REMAINING seconds, not the step.** Android register B34
//    was exactly this: pausing at second 14 of 15 restarted the wait from 15,
//    so pausing to answer the door cost you the part you had nearly finished.
//  * **Auto timing is off by default.** A scan that moves on without you is
//    the wrong default for a practice whose whole point is going at the pace
//    your attention actually has.

const STEPS = [
  { part: "Feet", body: "Notice pressure, temperature, or contact in both feet." },
  { part: "Lower legs", body: "Move attention through the ankles, calves, and knees." },
  { part: "Hips and pelvis", body: "Notice where the body is supported by the chair, bed, or floor." },
  { part: "Belly", body: "Feel the natural movement of breathing without forcing its pace." },
  { part: "Chest and back", body: "Notice expansion, release, and any areas that feel neutral." },
  { part: "Hands and arms", body: "Notice the fingers, palms, wrists, and arms." },
  { part: "Shoulders and neck", body: "Observe sensations here. Softening is optional, not a task." },
  { part: "Face and head", body: "Notice the jaw, eyes, forehead, and the whole head together." },
];
const SECONDS = 15;

export default function BodyScan() {
  const [started, setStarted] = useState(false);
  const [complete, setComplete] = useState(false);
  const [index, setIndex] = useState(0);
  const [auto, setAuto] = useState(false);
  const [paused, setPaused] = useState(false);
  const [remaining, setRemaining] = useState(SECONDS);
  const tick = useRef<ReturnType<typeof setInterval>>();

  // One interval, decrementing the seconds LEFT in this step — so a pause
  // resumes where it stopped instead of restarting the part.
  useEffect(() => {
    if (!started || complete || !auto || paused) return;
    tick.current = setInterval(() => setRemaining((r) => r - 1), 1000);
    return () => clearInterval(tick.current);
  }, [started, complete, auto, paused]);

  useEffect(() => {
    if (remaining > 0) return;
    if (index === STEPS.length - 1) setComplete(true);
    else setIndex((i) => i + 1);
  }, [remaining, index]);

  // A new step is a fresh 15 seconds — including when you skipped to it.
  useEffect(() => setRemaining(SECONDS), [index]);

  const step = STEPS[index];

  if (complete) {
    return (
      <>
        <AppHeader eyebrow="Offline practice" title="Body Scan" />
        <div className="today-wrap">
          <section className="ds-card" aria-live="polite">
            <h2 className="serif-h">Body scan complete</h2>
            <p className="sub">
              Move attention through the body without trying to change anything. Nothing was
              measured here and nothing was scored.
            </p>
            <div className="ds-actions">
              <button
                type="button"
                className="ds-cta"
                onClick={() => {
                  setComplete(false);
                  setStarted(false);
                  setIndex(0);
                  setPaused(false);
                }}
              >
                Again
              </button>
              <Link href="/games" className="text-btn">
                Back to the toolkit
              </Link>
            </div>
          </section>
        </div>
      </>
    );
  }

  if (!started) {
    return (
      <>
        <AppHeader eyebrow="Offline practice" title="Body Scan" />
        <div className="today-wrap">
          <p className="sub today-lede">
            Move attention through the body without trying to change anything. It works with
            no connection at all — nothing here is sent anywhere.
          </p>

          <ol className="offline-modules">
            {STEPS.map((s) => (
              <li key={s.part} className="ds-card">
                <h2 className="serif-h">{s.part}</h2>
                <p className="sub">{s.body}</p>
              </li>
            ))}
          </ol>

          <section className="ds-card">
            <div className="ds-head">
              <h2 className="serif-h">Auto timing</h2>
              <button
                type="button"
                className="chip"
                aria-pressed={auto}
                onClick={() => setAuto((a) => !a)}
              >
                {auto ? `On · ${SECONDS}s a part` : "Off · you move it"}
              </button>
            </div>
            <div className="ds-actions">
              <button type="button" className="ds-cta" onClick={() => setStarted(true)}>
                Begin
              </button>
            </div>
          </section>

          <WhyThisWorks text="Attention gets somewhere to be that is not the thought you were having. Noticing a sensation without trying to change it is the practice — there is no relaxed enough to reach." />
        </div>
      </>
    );
  }

  return (
    <>
      <AppHeader eyebrow="Offline practice" title="Body Scan" />
      <div className="today-wrap">
        <p className="eyebrow" role="status">
          {index + 1} of {STEPS.length}
          {auto && !paused ? ` · ${remaining}s` : auto ? " · paused" : ""}
        </p>
        <section className="ds-card" aria-live="polite">
          <h2 className="serif-h">{step.part}</h2>
          <p className="sub">{step.body}</p>
          <div className="ds-actions">
            <button
              type="button"
              className="text-btn"
              onClick={() => setIndex((i) => Math.max(0, i - 1))}
              disabled={index === 0}
            >
              Back
            </button>
            {auto && (
              <button type="button" className="text-btn" onClick={() => setPaused((p) => !p)}>
                {paused ? "Resume" : "Pause"}
              </button>
            )}
            <button
              type="button"
              className="ds-cta"
              onClick={() =>
                index === STEPS.length - 1 ? setComplete(true) : setIndex((i) => i + 1)
              }
            >
              {index === STEPS.length - 1 ? "Finish" : "Next"}
            </button>
          </div>
          <button
            type="button"
            className="text-btn"
            onClick={() => {
              setStarted(false);
              setIndex(0);
              setPaused(false);
            }}
          >
            Exit
          </button>
        </section>
      </div>
    </>
  );
}
