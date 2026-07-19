"use client";

import Link from "next/link";
import { useState } from "react";
import { celebrate } from "@/lib/celebrate";

const TIPP = [
  { t: "Temperature", body: "Cool your face — splash cold water, or hold a cold pack over your eyes and cheeks for ~30 seconds. It triggers the dive reflex and calms your body fast." },
  { t: "Intense exercise", body: "Move hard for a few minutes — jog in place, push-ups, star jumps. Burn off the surge of stress energy your body is holding." },
  { t: "Paced breathing", body: "Slow your out-breath so it's longer than your in-breath. Try 4 counts in, 6 out, for a minute." },
  { t: "Paired muscle relaxation", body: "Tense a muscle group as you breathe in, release as you breathe out. Work slowly up the body." },
];

export default function Tipp() {
  const [i, setI] = useState(0);
  const [done, setDone] = useState(false);
  const step = TIPP[i];
  function next() { if (i < TIPP.length - 1) setI(i + 1); else { setDone(true); celebrate("Steadier"); } }

  return (
    <div className="page tool-page">
      <div className="page-head">
        <div>
          <div className="eyebrow"><Link href="/tools" className="link-accent">Tools</Link> · TIPP</div>
          <h1>TIPP — for a spike</h1>
        </div>
      </div>
      {done ? (
        <div className="ground-card">
          <div className="g-big" aria-hidden="true">✓</div>
          <h2>Nicely done.</h2>
          <p className="placeholder">TIPP is for the sharpest moments — when feelings are too big to think through. You just brought the intensity down a notch.</p>
          <button className="primary" onClick={() => { setI(0); setDone(false); }}>Again</button>
        </div>
      ) : (
        <div className="ground-card">
          <div className="g-step" aria-hidden="true">{step.t[0]}</div>
          <h2>{step.t}</h2>
          <p className="placeholder">{step.body}</p>
          <div className="g-dots" aria-hidden="true">{TIPP.map((_, k) => <span key={k} className={`dot ${k <= i ? "on" : ""}`} />)}</div>
          <button className="primary" onClick={next}>{i < TIPP.length - 1 ? "Next" : "Done"}</button>
        </div>
      )}
    </div>
  );
}
